package com.modnmetl.modnvote.ui.text;

import com.modnmetl.modnvote.api.PollType;
import com.modnmetl.modnvote.config.MessageService;
import com.modnmetl.modnvote.domain.PollOption;
import com.modnmetl.modnvote.ui.format.BallotSummaryFormatter;
import com.modnmetl.modnvote.ui.session.VoteSession;
import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * GUI-specific text composition layer for vote renderers.
 *
 * This keeps renderer classes focused on layout and item construction while
 * allowing GUI wording to be externalised into messages.yml.
 */
public final class VoteGuiText {

    private final MessageService messages;
    private final BallotSummaryFormatter ballotSummaryFormatter;

    public VoteGuiText(MessageService messages,
                       BallotSummaryFormatter ballotSummaryFormatter) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.ballotSummaryFormatter = Objects.requireNonNull(ballotSummaryFormatter, "ballotSummaryFormatter");
    }

    public String selectionTitle(VoteSession session) {
        return messages.formatRaw("gui.titles.selection", Map.of(
                "poll_title", session.poll().title()
        ));
    }

    public String confirmationTitle(VoteSession session) {
        return messages.formatRaw("gui.titles.confirmation", Map.of(
                "poll_title", session.poll().title()
        ));
    }

    public ItemText pollInfo(VoteSession session) {
        List<String> lore = new ArrayList<>(messages.formatRawList("gui.selection.info.lore", Map.of(
                "poll_title", session.poll().title(),
                "poll_type", readablePollType(session.poll().pollType()),
                "max_rankings", String.valueOf(session.maxSelectableOptions()),
                "partial_ranking", session.poll().allowPartialRanking() ? "Allowed" : "Not allowed"
        )));

        String description = session.poll().description();
        if (!description.isBlank()) {
            lore.add(" ");
            lore.add(ChatColor.GRAY + description);
        }

        return new ItemText(
                messages.getRaw("gui.selection.info.title"),
                lore
        );
    }

    public ItemText option(VoteSession session, PollOption option) {
        Integer rank = session.assignedRank(option.optionId());

        if (rank != null) {
            return new ItemText(
                    messages.formatRaw("gui.selection.option.title.selected", Map.of(
                            "rank", String.valueOf(rank),
                            "option_name", option.displayName()
                    )),
                    messages.formatRawList("gui.selection.option.lore.selected", Map.of(
                            "description", option.description(),
                            "rank", String.valueOf(rank)
                    ))
            );
        }

        if (session.canAssignAnotherRank()) {
            return new ItemText(
                    messages.formatRaw("gui.selection.option.title.unselected", Map.of(
                            "option_name", option.displayName()
                    )),
                    messages.formatRawList("gui.selection.option.lore.unselected_available", Map.of(
                            "description", option.description(),
                            "next_rank", String.valueOf(session.selectedCount() + 1)
                    ))
            );
        }

        return new ItemText(
                messages.formatRaw("gui.selection.option.title.unselected", Map.of(
                        "option_name", option.displayName()
                )),
                messages.formatRawList("gui.selection.option.lore.unselected_full", Map.of(
                        "description", option.description()
                ))
        );
    }

    public ItemText selectionSummary(VoteSession session) {
        List<String> lore = new ArrayList<>(messages.getRawList("gui.selection.summary.lore_intro"));
        lore.addAll(summaryLines(ballotSummaryFormatter.formatSelectionSummary(session)));

        return new ItemText(
                messages.getRaw("gui.selection.summary.title"),
                lore
        );
    }

    public ItemText confirmationSummary(VoteSession session) {
        List<String> lore = new ArrayList<>(messages.getRawList("gui.confirmation.summary.lore_intro"));
        lore.addAll(summaryLines(ballotSummaryFormatter.formatConfirmationSummary(session)));

        return new ItemText(
                messages.getRaw("gui.confirmation.summary.title"),
                lore
        );
    }

    public ItemText resetButton() {
        return new ItemText(
                messages.getRaw("gui.selection.reset.title"),
                messages.getRawList("gui.selection.reset.lore")
        );
    }

    public ItemText reviewButton(VoteSession session) {
        boolean valid = session.isValidSelection();

        List<String> lore = new ArrayList<>();
        lore.addAll(summaryLines(ballotSummaryFormatter.formatCastButtonSummary(session)));
        lore.add(" ");

        if (valid) {
            lore.addAll(messages.getRawList("gui.selection.cast.lore_valid"));
            return new ItemText(messages.getRaw("gui.selection.cast.title_valid"), lore);
        }

        lore.addAll(messages.getRawList("gui.selection.cast.lore_invalid"));
        return new ItemText(messages.getRaw("gui.selection.cast.title_invalid"), lore);
    }

    public ItemText backButton() {
        return new ItemText(
                messages.getRaw("gui.confirmation.back.title"),
                messages.getRawList("gui.confirmation.back.lore")
        );
    }

    public ItemText commitButton() {
        return new ItemText(
                messages.getRaw("gui.confirmation.commit.title"),
                messages.getRawList("gui.confirmation.commit.lore")
        );
    }

    private List<String> summaryLines(String summaryText) {
        return Arrays.stream(summaryText.split("\\R"))
                .map(line -> ChatColor.WHITE + line)
                .toList();
    }

    private String readablePollType(PollType pollType) {
        return switch (pollType) {
            case RANKED_SINGLE_WINNER -> "Ranked Choice";
            case YES_NO -> "Yes / No";
            case SINGLE_CHOICE -> "Single Choice";
            case RANKED_MULTI_WINNER_STV -> "STV";
            case COMBINED_EXECUTIVE_AND_COUNCIL -> "Executive + Council";
            case LINKED_OFFICES -> "Linked Offices";
        };
    }

    public record ItemText(
            String title,
            List<String> lore
    ) {
        public ItemText {
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(lore, "lore");
            lore = List.copyOf(lore);
        }
    }
}