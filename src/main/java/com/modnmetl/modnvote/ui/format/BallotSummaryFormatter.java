package com.modnmetl.modnvote.ui.format;

import com.modnmetl.modnvote.ui.session.VoteSession;

import java.util.Objects;

/**
 * Produces stable, renderer-friendly ballot summary text.
 *
 * This formatter exists so that summary phrasing is not hardcoded into
 * renderers or mixed too deeply into session models.
 */
public final class BallotSummaryFormatter {

    public String formatSelectionSummary(VoteSession session) {
        Objects.requireNonNull(session, "session");

        if (!session.hasSelections()) {
            return "No selections made yet.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Your ranking:");

        for (VoteSession.RankedSelection selection : session.rankedSelections()) {
            sb.append('\n')
                    .append(selection.rank())
                    .append(". ")
                    .append(selection.displayName());
        }

        return sb.toString();
    }

    public String formatConfirmationSummary(VoteSession session) {
        Objects.requireNonNull(session, "session");

        if (!session.hasSelections()) {
            return "No selections made yet.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Please confirm this ranking:");

        for (VoteSession.RankedSelection selection : session.rankedSelections()) {
            sb.append('\n')
                    .append(selection.rank())
                    .append(". ")
                    .append(selection.displayName());
        }

        return sb.toString();
    }

    public String formatCastButtonSummary(VoteSession session) {
        Objects.requireNonNull(session, "session");

        if (!session.hasSelections()) {
            return "Choose at least one option to continue.";
        }

        if (!session.isValidSelection()) {
            return "Your current ranking is not valid yet.";
        }

        return formatSelectionSummary(session);
    }

    public String formatYesNoSelectionSummary(String selectedDisplayName) {
        if (selectedDisplayName == null || selectedDisplayName.isBlank()) {
            return "No choice selected yet.";
        }
        return "Your vote: " + selectedDisplayName;
    }

    public String formatYesNoConfirmationSummary(String selectedDisplayName) {
        if (selectedDisplayName == null || selectedDisplayName.isBlank()) {
            return "No choice selected yet.";
        }
        return "Please confirm your vote: " + selectedDisplayName;
    }

    public String formatYesNoCastButtonSummary(String selectedDisplayName) {
        if (selectedDisplayName == null || selectedDisplayName.isBlank()) {
            return "Choose Yes or No to continue.";
        }
        return formatYesNoSelectionSummary(selectedDisplayName);
    }
}