package com.modnmetl.modnvote.service.canonical;

import com.modnmetl.modnvote.api.PollStatus;
import com.modnmetl.modnvote.api.PollType;
import com.modnmetl.modnvote.domain.Poll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Stability and golden-output tests for {@link BallotCanonicalizer}.
 *
 * These tests lock the canonical anonymous-ballot payload format byte-for-byte
 * so the 2.2.0 canonicalizer extraction cannot silently change hashes for
 * existing YES_NO and RANKED_SINGLE_WINNER ballots.
 *
 * The golden strings below were derived from the pre-extraction inline payload
 * builders in BallotService / IntegrityVerificationService and must not change
 * without an intentional rule-snapshot version bump.
 */
class BallotCanonicalizerTest {

    private static final Instant FIXED_SUBMITTED_AT = Instant.ofEpochMilli(1000L);

    private final BallotCanonicalizer canonicalizer = new BallotCanonicalizer();

    private static Poll poll(PollType type, int maxRankings, boolean allowPartialRanking, long pollId) {
        return new Poll(
                pollId,
                "slug-" + pollId,
                "Title " + pollId,
                "Description",
                type,
                PollStatus.OPEN,
                null,
                null,
                maxRankings,
                1,
                allowPartialRanking,
                true,
                "participation-secret"
        );
    }

    @Test
    void yesNoCanonicalPayloadMatchesGoldenString() {
        Poll yesNoPoll = poll(PollType.YES_NO, 1, true, 42L);

        String payload = canonicalizer.canonicalAnonymousBallotPayload(
                yesNoPoll,
                List.of(7L),
                FIXED_SUBMITTED_AT
        );

        String expected =
                "poll_id=42\n"
                        + "poll_type=YES_NO\n"
                        + "submitted_at=1000\n"
                        + "rule_snapshot_version=v2\n"
                        + "max_rankings=1\n"
                        + "allow_partial_ranking=true\n"
                        + "ordered_option_ids=7";

        assertEquals(expected, payload);
    }

    @Test
    void rankedCanonicalPayloadMatchesGoldenString() {
        Poll rankedPoll = poll(PollType.RANKED_SINGLE_WINNER, 6, false, 7L);

        String payload = canonicalizer.canonicalAnonymousBallotPayload(
                rankedPoll,
                List.of(3L, 1L, 2L),
                FIXED_SUBMITTED_AT
        );

        String expected =
                "poll_id=7\n"
                        + "poll_type=RANKED_SINGLE_WINNER\n"
                        + "submitted_at=1000\n"
                        + "rule_snapshot_version=v2\n"
                        + "max_rankings=6\n"
                        + "allow_partial_ranking=false\n"
                        + "ordered_option_ids=3,1,2";

        assertEquals(expected, payload);
    }

    @Test
    void rankedPreferenceOrderAffectsCanonicalPayload() {
        Poll rankedPoll = poll(PollType.RANKED_SINGLE_WINNER, 6, false, 7L);

        String first = canonicalizer.canonicalAnonymousBallotPayload(
                rankedPoll, List.of(1L, 2L, 3L), FIXED_SUBMITTED_AT);
        String second = canonicalizer.canonicalAnonymousBallotPayload(
                rankedPoll, List.of(3L, 2L, 1L), FIXED_SUBMITTED_AT);

        assertNotEquals(first, second);
    }

    @Test
    void differentSelectedOptionsProduceDifferentCanonicalPayloads() {
        Poll yesNoPoll = poll(PollType.YES_NO, 1, true, 42L);

        String yes = canonicalizer.canonicalAnonymousBallotPayload(
                yesNoPoll, List.of(7L), FIXED_SUBMITTED_AT);
        String no = canonicalizer.canonicalAnonymousBallotPayload(
                yesNoPoll, List.of(8L), FIXED_SUBMITTED_AT);

        assertNotEquals(yes, no);
    }

    @Test
    void canonicalPayloadIsIdenticalRegardlessOfCallerInstance() {
        // BallotService and IntegrityVerificationService each construct their own
        // BallotCanonicalizer instance; both must produce identical output so the
        // submission and verification layers can never drift apart.
        BallotCanonicalizer submissionLayerCanonicalizer = new BallotCanonicalizer();
        BallotCanonicalizer integrityLayerCanonicalizer = new BallotCanonicalizer();

        Poll rankedPoll = poll(PollType.RANKED_SINGLE_WINNER, 6, false, 7L);

        String fromSubmission = submissionLayerCanonicalizer.canonicalAnonymousBallotPayload(
                rankedPoll, List.of(3L, 1L, 2L), FIXED_SUBMITTED_AT);
        String fromIntegrity = integrityLayerCanonicalizer.canonicalAnonymousBallotPayload(
                rankedPoll, List.of(3L, 1L, 2L), FIXED_SUBMITTED_AT);

        assertEquals(fromSubmission, fromIntegrity);
    }

    @Test
    void canonicalPayloadIsDeterministicAcrossRepeatedCalls() {
        Poll rankedPoll = poll(PollType.RANKED_SINGLE_WINNER, 6, false, 7L);

        String first = canonicalizer.canonicalAnonymousBallotPayload(
                rankedPoll, List.of(3L, 1L, 2L), FIXED_SUBMITTED_AT);
        String second = canonicalizer.canonicalAnonymousBallotPayload(
                rankedPoll, List.of(3L, 1L, 2L), FIXED_SUBMITTED_AT);

        assertEquals(first, second);
    }

    @Test
    void sha256OfCanonicalPayloadIsDeterministicAndContentSensitive() {
        // Mirrors how BallotService derives the anonymous ballot hash: SHA-256 over
        // the canonical payload. This proves hashing behaviour is deterministic and
        // changes when ballot content changes, without re-implementing production code.
        Poll rankedPoll = poll(PollType.RANKED_SINGLE_WINNER, 6, false, 7L);

        String hashA1 = sha256(canonicalizer.canonicalAnonymousBallotPayload(
                rankedPoll, List.of(1L, 2L, 3L), FIXED_SUBMITTED_AT));
        String hashA2 = sha256(canonicalizer.canonicalAnonymousBallotPayload(
                rankedPoll, List.of(1L, 2L, 3L), FIXED_SUBMITTED_AT));
        String hashB = sha256(canonicalizer.canonicalAnonymousBallotPayload(
                rankedPoll, List.of(3L, 2L, 1L), FIXED_SUBMITTED_AT));

        assertEquals(hashA1, hashA2);
        assertNotEquals(hashA1, hashB);
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
