package com.modnmetl.modnvote.service;

import com.modnmetl.modnvote.domain.Poll;
import com.modnmetl.modnvote.domain.PollOption;
import com.modnmetl.modnvote.platform.PlatformAdapter;
import com.modnmetl.modnvote.storage.AnonymousBallotDao;
import com.modnmetl.modnvote.storage.AnonymousBallotPreferenceDao;
import com.modnmetl.modnvote.storage.AuditEventDao;
import com.modnmetl.modnvote.storage.DatabaseManager;
import com.modnmetl.modnvote.storage.ParticipationRecordDao;
import com.modnmetl.modnvote.storage.PollDao;
import com.modnmetl.modnvote.storage.PollOptionDao;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Performs poll integrity verification beyond basic inclusion checks.
 *
 * This service recomputes canonical ballot hashes from stored anonymous ballot
 * content and preferences so offline DB edits to ballot preferences cannot
 * silently pass as valid.
 *
 * Privacy-hardened model notes:
 * - participation and anonymous ballot records are intentionally no longer
 *   bridged via shared receipt linkage
 * - integrity therefore checks aggregate record consistency, not cross-table
 *   per-ballot linkage
 */
public final class IntegrityVerificationService {

    private final DatabaseManager databaseManager;
    private final PlatformAdapter platformAdapter;
    private final Logger logger;
    private final PollDao pollDao;
    private final PollOptionDao pollOptionDao;
    private final ParticipationRecordDao participationRecordDao;
    private final AnonymousBallotDao anonymousBallotDao;
    private final AnonymousBallotPreferenceDao anonymousBallotPreferenceDao;
    private final AuditEventDao auditEventDao;

    public IntegrityVerificationService(DatabaseManager databaseManager,
                                        PlatformAdapter platformAdapter,
                                        Logger logger) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager");
        this.platformAdapter = Objects.requireNonNull(platformAdapter, "platformAdapter");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.pollDao = new PollDao(databaseManager);
        this.pollOptionDao = new PollOptionDao(databaseManager);
        this.participationRecordDao = new ParticipationRecordDao(databaseManager);
        this.anonymousBallotDao = new AnonymousBallotDao(databaseManager);
        this.anonymousBallotPreferenceDao = new AnonymousBallotPreferenceDao(databaseManager);
        this.auditEventDao = new AuditEventDao(databaseManager);
    }

    public boolean isInitialized() {
        return true;
    }

    public String getStatusSummary() {
        return "IntegrityVerificationService ready";
    }

    public IntegrityVerificationResult verifyPollIntegrity(long pollId) throws PollServiceException {
        try {
            Poll poll = pollDao.findPollById(pollId);
            if (poll == null) {
                throw new PollServiceException("Poll #" + pollId + " does not exist.");
            }

            List<PollOption> pollOptions = pollOptionDao.findOptionsByPollId(pollId);
            Set<Long> validOptionIds = pollOptions.stream()
                    .map(PollOption::optionId)
                    .collect(Collectors.toSet());

            List<AnonymousBallotDao.StoredAnonymousBallot> ballots =
                    anonymousBallotDao.findAnonymousBallotsByPollId(pollId);

            int participationRecordCount =
                    participationRecordDao.findParticipationReceiptHashesByPollId(pollId).size();
            int anonymousBallotCount = ballots.size();

            boolean auditChainValid = auditEventDao.isPollAuditChainValid(pollId);
            boolean recordCountsMatch = participationRecordCount == anonymousBallotCount;

            List<String> issues = new ArrayList<>();
            boolean ballotHashesValid = true;

            Set<String> seenBallotProofHashes = new HashSet<>();

            for (AnonymousBallotDao.StoredAnonymousBallot ballot : ballots) {
                if (ballot.ballotProofHash() == null || ballot.ballotProofHash().isBlank()) {
                    ballotHashesValid = false;
                    issues.add("Anonymous ballot #" + ballot.anonymousBallotId() + " is missing ballot proof verifier material.");
                    continue;
                }

                if (!seenBallotProofHashes.add(ballot.ballotProofHash())) {
                    ballotHashesValid = false;
                    issues.add("Anonymous ballot proof hash collision detected in poll #" + pollId + ".");
                }

                if (ballot.ballotCommitmentHash() == null || ballot.ballotCommitmentHash().isBlank()) {
                    ballotHashesValid = false;
                    issues.add("Anonymous ballot #" + ballot.anonymousBallotId() + " is missing ballot commitment material.");
                }

                List<AnonymousBallotPreferenceDao.StoredAnonymousBallotPreference> preferences =
                        anonymousBallotPreferenceDao.findPreferencesByAnonymousBallotId(ballot.anonymousBallotId());

                preferences.sort(Comparator.comparingInt(
                        AnonymousBallotPreferenceDao.StoredAnonymousBallotPreference::rankPosition
                ));

                boolean currentBallotValid = true;

                if (preferences.isEmpty()) {
                    ballotHashesValid = false;
                    issues.add("Anonymous ballot #" + ballot.anonymousBallotId() + " has no stored preferences.");
                    continue;
                }

                List<Long> orderedOptionIds = new ArrayList<>();
                Set<Long> seenOptionIds = new HashSet<>();

                for (int i = 0; i < preferences.size(); i++) {
                    AnonymousBallotPreferenceDao.StoredAnonymousBallotPreference preference = preferences.get(i);
                    int expectedRank = i + 1;

                    if (preference.rankPosition() != expectedRank) {
                        ballotHashesValid = false;
                        currentBallotValid = false;
                        issues.add("Anonymous ballot #" + ballot.anonymousBallotId()
                                + " has non-contiguous ranks.");
                        break;
                    }

                    if (!validOptionIds.contains(preference.optionId())) {
                        ballotHashesValid = false;
                        currentBallotValid = false;
                        issues.add("Anonymous ballot #" + ballot.anonymousBallotId()
                                + " references option #" + preference.optionId()
                                + " which does not belong to poll #" + pollId + ".");
                        break;
                    }

                    if (!seenOptionIds.add(preference.optionId())) {
                        ballotHashesValid = false;
                        currentBallotValid = false;
                        issues.add("Anonymous ballot #" + ballot.anonymousBallotId()
                                + " contains duplicate option ids.");
                        break;
                    }

                    orderedOptionIds.add(preference.optionId());
                }

                if (!currentBallotValid) {
                    continue;
                }

                String recomputedHash = sha256(buildCanonicalAnonymousBallotPayload(
                        poll,
                        orderedOptionIds,
                        ballot.submittedAt()
                ));

                if (!recomputedHash.equals(ballot.ballotHash())) {
                    ballotHashesValid = false;
                    issues.add("Anonymous ballot #" + ballot.anonymousBallotId()
                            + " failed canonical ballot hash verification.");
                }
            }

            if (!recordCountsMatch) {
                issues.add("Participation record count and anonymous ballot count do not match.");
            }

            boolean overallValid = auditChainValid && ballotHashesValid && recordCountsMatch;

            return new IntegrityVerificationResult(
                    pollId,
                    auditChainValid,
                    ballotHashesValid,
                    recordCountsMatch,
                    overallValid,
                    List.copyOf(issues)
            );
        } catch (PollServiceException e) {
            throw e;
        } catch (Exception e) {
            logger.warning("Failed to verify poll integrity for poll #" + pollId + ": " + e.getMessage());
            throw new PollServiceException("Failed to verify poll integrity for poll #" + pollId, e);
        }
    }

    private String buildCanonicalAnonymousBallotPayload(Poll poll,
                                                        List<Long> rankedOptionIds,
                                                        java.time.Instant submittedAt) {
        StringBuilder sb = new StringBuilder();
        sb.append("poll_id=").append(poll.pollId()).append('\n');
        sb.append("poll_type=").append(poll.pollType().name()).append('\n');
        sb.append("submitted_at=").append(submittedAt.toEpochMilli()).append('\n');
        sb.append("rule_snapshot_version=v2").append('\n');
        sb.append("max_rankings=").append(poll.maxRankings()).append('\n');
        sb.append("allow_partial_ranking=").append(poll.allowPartialRanking()).append('\n');
        sb.append("ordered_option_ids=").append(joinOptionIds(rankedOptionIds));
        return sb.toString();
    }

    private String joinOptionIds(List<Long> rankedOptionIds) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < rankedOptionIds.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(rankedOptionIds.get(i));
        }
        return sb.toString();
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash integrity payload", e);
        }
    }

    public record IntegrityVerificationResult(
            long pollId,
            boolean auditChainValid,
            boolean ballotHashesValid,
            boolean recordCountsMatch,
            boolean overallValid,
            List<String> issues
    ) {
    }
}