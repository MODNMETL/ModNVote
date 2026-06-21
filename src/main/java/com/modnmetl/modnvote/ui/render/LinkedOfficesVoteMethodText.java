package com.modnmetl.modnvote.ui.render;

import com.modnmetl.modnvote.domain.election.CountingMethod;

/**
 * Bukkit-free presentation helper for linked-offices vote-screen method text.
 *
 * <p>{@link LinkedOfficesVoteRenderer} imports Bukkit types and therefore cannot be
 * loaded in the Bukkit-free unit suite (Paper API is {@code compileOnly}). The pure
 * decisions that drive whether an office renders as a ranked or an approval contest —
 * and the label shown for its counting method — live here so they can be unit-tested
 * directly. The renderer delegates to these methods; it does not duplicate the logic.
 *
 * <p>Ranked-vs-approval classification is driven by
 * {@link CountingMethod#usesRankedBallot()} (the single source of truth shared with
 * the vote state and ballot validator), so IRV and STV both render as ranked and STV
 * is never mislabelled or operated as Approval.
 */
final class LinkedOfficesVoteMethodText {

    private LinkedOfficesVoteMethodText() {
    }

    /**
     * Human-readable label for an office's counting method. Both ranked methods are
     * labelled "Ranked" (distinguished by the method name); approval is "Approval";
     * an unknown/null method falls back to "Unknown".
     */
    static String methodLabel(CountingMethod method) {
        if (method == null) {
            return "Unknown";
        }
        return switch (method) {
            case IRV -> "Ranked (IRV)";
            case STV -> "Ranked (STV)";
            case APPROVAL_TOP_N -> "Approval";
        };
    }

    /**
     * Whether an office is rendered and operated as a ranked contest (ordered
     * preferences) rather than an approval contest.
     */
    static boolean isRankedMethod(CountingMethod method) {
        return method != null && method.usesRankedBallot();
    }
}
