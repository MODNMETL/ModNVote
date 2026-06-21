package com.modnmetl.modnvote.presentation;

import com.modnmetl.modnvote.domain.Poll;
import com.modnmetl.modnvote.domain.election.results.CandidateResult;
import com.modnmetl.modnvote.domain.election.results.CandidateTally;
import com.modnmetl.modnvote.domain.election.results.ContestResult;
import com.modnmetl.modnvote.domain.election.results.IrvRoundResult;
import com.modnmetl.modnvote.domain.election.results.LinkedElectionResult;
import com.modnmetl.modnvote.domain.election.results.StvCandidateTally;
import com.modnmetl.modnvote.domain.election.results.StvResultData;
import com.modnmetl.modnvote.domain.election.results.StvRoundResult;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Presentation-only builder for the witness publication payload of a
 * linked-offices election result.
 *
 * <p>This class performs no counting, touches no database, and references no
 * Bukkit type, so it is fully unit-testable. It transforms an already-computed
 * {@link LinkedElectionResult} into deterministic witness fields
 * ({@link WitnessField}) which {@code WitnessPublicationService} maps onto its
 * Discord embed fields.
 *
 * <p>The result it receives carries only anonymous office/candidate keys and
 * integer counts, so the formatter is structurally incapable of emitting voter
 * identity (UUID, name, IP hash, Floodgate id), participation tokens or
 * receipts, anonymous ballot ids, or proof phrases.
 *
 * <p>All ordering is taken directly from the result: offices in result order,
 * candidates in contest order, rounds in round order. No hashing or unordered
 * iteration is involved, so the same result always renders the same payload.
 */
public final class LinkedElectionWitnessPayloadFormatter {

    private LinkedElectionWitnessPayloadFormatter() {
    }

    /**
     * A single witness field: a name, a value, and whether it renders inline.
     */
    public record WitnessField(String name, String value, boolean inline) {
        public WitnessField {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(value, "value");
        }
    }

    /**
     * Builds the deterministic witness fields for a closed linked-offices poll.
     *
     * @param poll                 the poll being published (for id, title, type, status)
     * @param result               the computed linked-offices result
     * @param closedAt             the close timestamp, or {@code null} if unavailable
     * @param maxFieldValueLength  the maximum length of any field value; longer
     *                             values are truncated with an ellipsis
     * @return the witness fields in deterministic order
     */
    public static List<WitnessField> buildFields(Poll poll,
                                                 LinkedElectionResult result,
                                                 Instant closedAt,
                                                 int maxFieldValueLength) {
        Objects.requireNonNull(poll, "poll");
        Objects.requireNonNull(result, "result");

        List<WitnessField> fields = new ArrayList<>();
        fields.add(new WitnessField("Poll ID", "#" + poll.pollId(), true));
        fields.add(new WitnessField("Type", poll.pollType().name(), true));
        fields.add(new WitnessField("Status", poll.status().name(), true));
        if (closedAt != null) {
            fields.add(new WitnessField("Closed", closedAt.toString(), true));
        }
        fields.add(new WitnessField("Result", result.complete() ? "Complete" : "Incomplete", true));
        fields.add(new WitnessField("Counted Ballots", String.valueOf(result.countedBallots()), true));
        fields.add(new WitnessField("Skipped Ballots", String.valueOf(result.skippedBallots()), true));
        fields.add(new WitnessField("Offices", String.valueOf(result.contestResults().size()), true));

        for (ContestResult contest : result.contestResults()) {
            fields.add(new WitnessField(
                    truncate("Office: " + contest.displayName() + " (" + contest.officeKey() + ")", maxFieldValueLength),
                    truncate(formatContestValue(contest), maxFieldValueLength),
                    false));
        }

        if (!result.issues().isEmpty()) {
            fields.add(new WitnessField(
                    "Election Issues",
                    truncate(formatIssues(result.issues()), maxFieldValueLength),
                    false));
        }

        return List.copyOf(fields);
    }

    private static String formatContestValue(ContestResult contest) {
        StringBuilder sb = new StringBuilder();
        sb.append("Method: ").append(contest.method())
                .append(" | Seats: ").append(contest.seats()).append('\n');

        if (contest.stv() != null) {
            sb.append("Quota: ").append(contest.stv().quota()).append('\n');
        }

        if (contest.winners().isEmpty()) {
            sb.append("Winners: (none determined)").append('\n');
        } else {
            sb.append("Winners: ").append(String.join(", ", contest.winners())).append('\n');
        }

        if (contest.unresolvedSeatCount() > 0) {
            sb.append("Unresolved seats: ").append(contest.unresolvedSeatCount()).append('\n');
            sb.append("Tied candidates: ")
                    .append(String.join(", ", contest.unresolvedCandidateKeys())).append('\n');
            sb.append("Runoff/admin resolution required.").append('\n');
        }

        if (!contest.excludedCandidateKeys().isEmpty()) {
            sb.append("Excluded by dependency: ")
                    .append(String.join(", ", contest.excludedCandidateKeys())).append('\n');
        }

        sb.append("Tallies:").append('\n');
        for (CandidateResult candidate : contest.candidateResults()) {
            if (candidate.excluded()) {
                sb.append("- ").append(candidate.candidateKey()).append(" (excluded)").append('\n');
            } else {
                sb.append("- ").append(candidate.candidateKey()).append(": ").append(candidate.score());
                if (candidate.elected()) {
                    sb.append(" (elected)");
                } else if (candidate.unresolved()) {
                    sb.append(" (tied — unresolved)");
                }
                sb.append('\n');
            }
        }

        if (!contest.rounds().isEmpty()) {
            sb.append("IRV rounds:").append('\n');
            for (IrvRoundResult round : contest.rounds()) {
                appendRound(sb, round);
            }
        }

        if (contest.stv() != null) {
            appendStv(sb, contest.stv());
        }

        if (contest.exhaustedBallots() > 0) {
            sb.append("Exhausted ballots (final round): ").append(contest.exhaustedBallots()).append('\n');
        }

        for (String issue : contest.issues()) {
            sb.append("Issue: ").append(issue).append('\n');
        }

        return stripTrailingNewline(sb.toString());
    }

    private static void appendStv(StringBuilder sb, StvResultData stv) {
        if (!stv.rounds().isEmpty()) {
            sb.append("STV rounds:").append('\n');
            for (StvRoundResult round : stv.rounds()) {
                sb.append("Round ").append(round.roundNumber()).append(": ");
                List<String> parts = new ArrayList<>();
                for (StvCandidateTally tally : round.tallies()) {
                    parts.add(tally.candidateKey() + " " + tally.value());
                }
                sb.append(String.join(", ", parts)).append('\n');
                if (!round.electedThisRound().isEmpty()) {
                    sb.append("  Elected: ").append(String.join(", ", round.electedThisRound())).append('\n');
                }
                if (round.eliminatedCandidateKey() != null) {
                    sb.append("  Eliminated: ").append(round.eliminatedCandidateKey()).append('\n');
                }
            }
        }
        sb.append("Exhausted ballot value: ").append(stv.exhaustedValue()).append('\n');
    }

    private static void appendRound(StringBuilder sb, IrvRoundResult round) {
        sb.append("Round ").append(round.roundNumber()).append(": ");
        List<String> parts = new ArrayList<>();
        for (CandidateTally tally : round.tallies()) {
            parts.add(tally.candidateKey() + " " + tally.votes());
        }
        sb.append(String.join(", ", parts)).append('\n');
        if (round.exhaustedBallots() > 0) {
            sb.append("  Exhausted: ").append(round.exhaustedBallots()).append('\n');
        }
        if (round.eliminatedCandidateKey() != null) {
            sb.append("  Eliminated: ").append(round.eliminatedCandidateKey()).append('\n');
        }
        if (round.winnerCandidateKey() != null) {
            sb.append("  Elected: ").append(round.winnerCandidateKey()).append('\n');
        }
    }

    private static String formatIssues(List<String> issues) {
        StringBuilder sb = new StringBuilder();
        for (String issue : issues) {
            sb.append("- ").append(issue).append('\n');
        }
        return stripTrailingNewline(sb.toString());
    }

    private static String stripTrailingNewline(String value) {
        if (value.endsWith("\n")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        if (maxLength <= 0 || value.length() <= maxLength) {
            return value;
        }
        if (maxLength == 1) {
            return value.substring(0, 1);
        }
        return value.substring(0, maxLength - 1) + "…";
    }
}
