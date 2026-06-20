package com.modnmetl.modnvote.presentation;

import com.modnmetl.modnvote.service.LinkedOfficeBallotProofVerificationResult;
import com.modnmetl.modnvote.service.LinkedOfficeBallotProofVerificationResult.OfficeResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Presentation-only formatting for linked-offices ballot proof-phrase verification.
 *
 * <p>This class renders an already-computed
 * {@link LinkedOfficeBallotProofVerificationResult} into in-game chat lines. It
 * performs no verification, hashing, canonicalization, or database access, and it
 * is deliberately free of any Bukkit type so the command output can be unit-tested
 * without a server. {@code PollCommand} keeps its handler thin and simply forwards
 * each returned line to the sender.
 *
 * <p><strong>Privacy:</strong> proof verification is bearer-token based, so the
 * holder of the proof phrase may see anonymous ballot content. This formatter only
 * ever reads fields already present on the result (poll id, anonymous ballot hash,
 * submission timestamp, and per-office anonymous responses). It never has access to
 * and never emits voter identity (player UUID, name, IP hash, Floodgate id),
 * participation token/receipt material, or the proof phrase itself. On a failed
 * verification no office or candidate content is shown at all.
 */
public final class LinkedOfficeProofDisplayFormatter {

    /** Response type marker for ranked office contests. */
    static final String TYPE_RANKED = "RANKED";

    private LinkedOfficeProofDisplayFormatter() {
    }

    /**
     * Renders the verification result as ordered in-game chat lines.
     *
     * @param result the linked-offices proof verification result (must not be null)
     * @return immutable list of color-coded chat lines, never empty
     */
    public static List<String> formatInGame(LinkedOfficeBallotProofVerificationResult result) {
        Objects.requireNonNull(result, "result");

        if (!result.ballotFound()) {
            return List.of(
                    "§cNo anonymous ballot was found for that proof phrase.",
                    "§7Check the poll ID and try the proof phrase again using the same words."
            );
        }

        if (!result.verified()) {
            List<String> lines = new ArrayList<>();
            lines.add("§cA ballot matched the proof phrase, but exact-ballot verification failed.");
            String reason = result.failureReason();
            if (reason != null && !reason.isBlank()) {
                lines.add("§7Reason: §f" + reason);
            }
            lines.add("§7No ballot content is shown because the stored ballot did not verify.");
            return List.copyOf(lines);
        }

        List<String> lines = new ArrayList<>();
        lines.add("§aExact-ballot verification succeeded for this proof phrase.");
        lines.add("§7Poll: §f#" + result.pollId());

        if (result.submittedAt() != null) {
            lines.add("§7Submitted: §f" + result.submittedAt());
        }
        if (result.ballotHash() != null && !result.ballotHash().isBlank()) {
            lines.add("§7Verified ballot reference: §f" + result.ballotHash());
        }

        lines.add("§bVerified linked-office responses:");

        if (result.offices().isEmpty()) {
            lines.add("§eNo recorded office responses were found for this ballot.");
            return List.copyOf(lines);
        }

        for (OfficeResponse office : result.offices()) {
            lines.add("§7Office §f" + office.officeKey() + " §8(" + office.responseType() + ")");

            List<String> keys = office.orderedCandidateKeys();
            if (keys.isEmpty()) {
                lines.add(" §8- §7(no selection)");
                continue;
            }

            if (TYPE_RANKED.equals(office.responseType())) {
                for (int i = 0; i < keys.size(); i++) {
                    lines.add(" §8#§f" + (i + 1) + " §8-> §b" + keys.get(i));
                }
            } else {
                for (String key : keys) {
                    lines.add(" §8- §b" + key);
                }
            }
        }

        return List.copyOf(lines);
    }
}
