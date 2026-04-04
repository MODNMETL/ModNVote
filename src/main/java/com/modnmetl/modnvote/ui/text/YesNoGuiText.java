package com.modnmetl.modnvote.ui.text;

import com.modnmetl.modnvote.config.MessageService;
import com.modnmetl.modnvote.domain.PollOption;
import com.modnmetl.modnvote.ui.format.BallotSummaryFormatter;
import com.modnmetl.modnvote.ui.session.YesNoVoteSession;
import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * GUI-specific text composition layer for yes/no vote renderers.
 */
public final class YesNoGuiText {

    private final MessageService messages;
    private final BallotSummaryFormatter ballotSummaryFormatter;

    public YesNoGuiText(MessageService messages,
                        BallotSummaryFormatter ballotSummaryFormatter) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.ballotSummaryFormatter = Objects.requireNonNull(ballotSummaryFormatter, "ballotSummaryFormatter");
    }

    public String selectionTitle(YesNoVoteSession session) {
        return messages.formatRaw("gui_yes_no.titles.selection", Map.of(
                "poll_title", session.poll().title()
        ));
    }

    public String confirmationTitle(YesNoVoteSession session) {
        return messages.formatRaw("gui_yes_no.titles.confirmation", Map.of(
                "poll_title", session.poll().title()
        ));
    }

    public ItemText pollInfo(YesNoVoteSession session) {
        return new ItemText(
                messages.getRaw("gui_yes_no.selection.info.title"),
                messages.formatRawList("gui_yes_no.selection.info.lore", Map.of(
                        "poll_title", session.poll().title()
                ))
        );
    }

    public ItemText option(YesNoVoteSession session, PollOption option) {
        boolean selected = session.isSelected(option.optionId());

        return new ItemText(
                messages.formatRaw(
                        selected ? "gui_yes_no.selection.option.title.selected" : "gui_yes_no.selection.option.title.unselected",
                        Map.of("option_name", option.displayName())
                ),
                messages.formatRawList(
                        selected ? "gui_yes_no.selection.option.lore.selected" : "gui_yes_no.selection.option.lore.unselected",
                        Map.of("description", option.description())
                )
        );
    }

    public ItemText summary(YesNoVoteSession session) {
        List<String> lore = new ArrayList<>(messages.getRawList("gui_yes_no.selection.summary.lore_intro"));
        lore.addAll(summaryLines(ballotSummaryFormatter.formatYesNoSelectionSummary(session.selectedDisplayName())));

        return new ItemText(
                messages.getRaw("gui_yes_no.selection.summary.title"),
                lore
        );
    }

    public ItemText confirmationSummary(YesNoVoteSession session) {
        List<String> lore = new ArrayList<>(messages.getRawList("gui_yes_no.confirmation.summary.lore_intro"));
        lore.addAll(summaryLines(ballotSummaryFormatter.formatYesNoConfirmationSummary(session.selectedDisplayName())));

        return new ItemText(
                messages.getRaw("gui_yes_no.confirmation.summary.title"),
                lore
        );
    }

    public ItemText clearButton() {
        return new ItemText(
                messages.getRaw("gui_yes_no.selection.clear.title"),
                messages.getRawList("gui_yes_no.selection.clear.lore")
        );
    }

    public ItemText reviewButton(YesNoVoteSession session) {
        boolean valid = session.isValidSelection();

        List<String> lore = new ArrayList<>();
        lore.addAll(summaryLines(ballotSummaryFormatter.formatYesNoCastButtonSummary(session.selectedDisplayName())));
        lore.add(" ");

        if (valid) {
            lore.addAll(messages.getRawList("gui_yes_no.selection.cast.lore_valid"));
            return new ItemText(messages.getRaw("gui_yes_no.selection.cast.title_valid"), lore);
        }

        lore.addAll(messages.getRawList("gui_yes_no.selection.cast.lore_invalid"));
        return new ItemText(messages.getRaw("gui_yes_no.selection.cast.title_invalid"), lore);
    }

    public ItemText backButton() {
        return new ItemText(
                messages.getRaw("gui_yes_no.confirmation.back.title"),
                messages.getRawList("gui_yes_no.confirmation.back.lore")
        );
    }

    public ItemText commitButton() {
        return new ItemText(
                messages.getRaw("gui_yes_no.confirmation.commit.title"),
                messages.getRawList("gui_yes_no.confirmation.commit.lore")
        );
    }

    private List<String> summaryLines(String summaryText) {
        return Arrays.stream(summaryText.split("\\R"))
                .map(line -> ChatColor.WHITE + line)
                .toList();
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