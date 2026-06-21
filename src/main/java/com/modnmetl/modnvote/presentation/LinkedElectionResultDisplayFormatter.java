package com.modnmetl.modnvote.presentation;

import com.modnmetl.modnvote.domain.election.results.CandidateResult;
import com.modnmetl.modnvote.domain.election.results.CandidateTally;
import com.modnmetl.modnvote.domain.election.results.ContestResult;
import com.modnmetl.modnvote.domain.election.results.IrvRoundResult;
import com.modnmetl.modnvote.domain.election.results.LinkedElectionResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Presentation-only formatting for linked-offices election results.
 *
 * <p>This class performs no counting and touches no database. It renders an
 * already-computed {@link LinkedElectionResult} into in-game chat lines, mirroring
 * the Bukkit-free {@code List<String>} pattern of {@link ResultDisplayFormatter}
 * and {@link LinkedOfficeProofDisplayFormatter} so it is fully unit-testable.
 *
 * <p>The result it receives carries only anonymous office/candidate keys and
 * counts, so the formatter is structurally incapable of emitting voter identity
 * (UUID/name/IP hash/Floodgate id), participation tokens/receipts, or proof
 * phrases.
 */
public final class LinkedElectionResultDisplayFormatter {

    private LinkedElectionResultDisplayFormatter() {
    }

    public static List<String> formatInGame(LinkedElectionResult result) {
        Objects.requireNonNull(result, "result");

        List<String> lines = new ArrayList<>();
        lines.add("§6Linked-offices results for poll #" + result.pollId() + ": §f" + result.pollTitle());
        lines.add("§7Counted ballots: §f" + result.countedBallots());
        if (result.skippedBallots() > 0) {
            lines.add("§eSkipped ballots: §f" + result.skippedBallots());
        }
        if (!result.complete()) {
            lines.add("§cResult is incomplete; see issues below.");
        }

        if (result.contestResults().isEmpty()) {
            lines.add("§eNo contests were counted for this election.");
        }

        for (ContestResult contest : result.contestResults()) {
            appendContest(lines, contest);
        }

        if (!result.issues().isEmpty()) {
            lines.add("§cElection issues:");
            for (String issue : result.issues()) {
                lines.add(" §8- §7" + issue);
            }
        }

        return List.copyOf(lines);
    }

    private static void appendContest(List<String> lines, ContestResult contest) {
        lines.add("§6Office §f" + contest.displayName() + " §8(" + contest.officeKey() + ")");
        lines.add("§7Method: §f" + contest.method() + " §8| §7Seats: §f" + contest.seats());

        if (contest.winners().isEmpty()) {
            lines.add("§eNo winner could be determined.");
        } else {
            lines.add("§aWinners: §f" + String.join(", ", contest.winners()));
        }

        if (contest.unresolvedSeatCount() > 0) {
            lines.add("§c" + contest.displayName() + " unresolved: §f" + contest.unresolvedSeatCount()
                    + " §cseat(s) require runoff/admin resolution.");
            lines.add("§cTied candidates: §f" + String.join(", ", contest.unresolvedCandidateKeys()));
        }

        if (!contest.excludedCandidateKeys().isEmpty()) {
            lines.add("§7Excluded by dependency: §f" + String.join(", ", contest.excludedCandidateKeys()));
        }

        lines.add("§eCandidate tallies:");
        for (CandidateResult candidate : contest.candidateResults()) {
            if (candidate.excluded()) {
                lines.add(" §8- §7" + candidate.candidateKey() + " §8(excluded)");
            } else {
                String marker = candidate.elected()
                        ? " §a(elected)"
                        : candidate.unresolved() ? " §c(tied — unresolved)" : "";
                lines.add(" §8- §f" + candidate.candidateKey() + "§7: §f" + candidate.score() + marker);
            }
        }

        if (!contest.rounds().isEmpty()) {
            lines.add("§eIRV round breakdown:");
            for (IrvRoundResult round : contest.rounds()) {
                appendRound(lines, round);
            }
        }

        if (contest.exhaustedBallots() > 0) {
            lines.add("§7Exhausted ballots (final round): §f" + contest.exhaustedBallots());
        }

        for (String issue : contest.issues()) {
            lines.add("§cIssue: §7" + issue);
        }
    }

    private static void appendRound(List<String> lines, IrvRoundResult round) {
        lines.add("§6Round " + round.roundNumber() + "§7:");
        for (CandidateTally tally : round.tallies()) {
            lines.add("  §8- §f" + tally.candidateKey() + "§7: §f" + tally.votes());
        }
        if (round.exhaustedBallots() > 0) {
            lines.add("  §7Exhausted: §f" + round.exhaustedBallots());
        }
        if (round.eliminatedCandidateKey() != null) {
            lines.add("  §cEliminated: §f" + round.eliminatedCandidateKey());
        }
        if (round.winnerCandidateKey() != null) {
            lines.add("  §aElected: §f" + round.winnerCandidateKey());
        }
    }
}
