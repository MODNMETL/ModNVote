package com.modnmetl.modnvote.presentation;

import com.modnmetl.modnvote.api.PollType;
import com.modnmetl.modnvote.service.ResultService;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Presentation-only formatting helpers for public result displays.
 *
 * This class does not calculate results. It only renders the immutable result
 * snapshots produced by ResultService so in-game and witness-publication output
 * use consistent ranked-choice terminology.
 */
public final class ResultDisplayFormatter {

    private ResultDisplayFormatter() {
    }

    public static List<String> formatInGame(ResultService.PollResult result) {
        Objects.requireNonNull(result, "result");

        if (result.pollType() == PollType.RANKED_SINGLE_WINNER) {
            return formatRankedInGame(result);
        }

        return formatSimpleInGame(result);
    }

    public static String formatInGameText(ResultService.PollResult result) {
        return String.join("\n", formatInGame(result));
    }

    public static List<FieldBlock> formatDiscordFields(ResultService.PollResult result, int maxFieldValueLength) {
        Objects.requireNonNull(result, "result");

        if (result.pollType() == PollType.RANKED_SINGLE_WINNER) {
            return formatRankedDiscordFields(result, maxFieldValueLength);
        }

        return List.of(new FieldBlock("Result Summary", formatSimpleTallies(result.tallies())));
    }

    public static String formatFinalRoundSummary(ResultService.PollResult result) {
        Objects.requireNonNull(result, "result");

        if (result.pollType() != PollType.RANKED_SINGLE_WINNER || result.rankedChoiceRounds().isEmpty()) {
            return "Not applicable";
        }

        ResultService.RankedChoiceRound finalRound = result.rankedChoiceRounds()
                .get(result.rankedChoiceRounds().size() - 1);

        List<String> lines = new ArrayList<>();
        for (ResultService.OptionTally tally : finalRound.tallies()) {
            lines.add(tally.optionName() + ": " + tally.votes());
        }

        if (finalRound.exhaustedBallots() > 0) {
            lines.add("Exhausted ballots: " + finalRound.exhaustedBallots());
        }

        return lines.isEmpty() ? "No final-round tallies." : String.join("\n", lines);
    }

    private static List<String> formatSimpleInGame(ResultService.PollResult result) {
        List<String> lines = new ArrayList<>();
        lines.add("§6Results for poll #" + result.pollId() + ": §f" + result.pollTitle());
        lines.add("§7Ballots: §f" + result.totalVotes());

        if (result.winnerName() != null && !result.winnerName().isBlank()) {
            lines.add("§aWinner: §f" + result.winnerName());
        }

        lines.add("§eResult Summary:");
        for (ResultService.OptionTally tally : result.tallies()) {
            lines.add("§7- §f" + tally.optionName() + "§7: §f" + tally.votes());
        }

        return List.copyOf(lines);
    }

    private static List<String> formatRankedInGame(ResultService.PollResult result) {
        List<String> lines = new ArrayList<>();
        lines.add("§6Ranked-choice results for poll #" + result.pollId() + ": §f" + result.pollTitle());
        lines.add("§7Ballots: §f" + result.totalVotes());

        if (result.winnerName() != null && !result.winnerName().isBlank()) {
            lines.add("§aPoll winner: §f" + result.winnerName());
        }

        if (result.finalWinnerTally() != null) {
            lines.add("§7Final winner tally: §f" + result.finalWinnerTally().votes());
        }

        if (result.exhaustedBallots() > 0) {
            lines.add("§7Exhausted ballots in final round: §f" + result.exhaustedBallots());
        }

        if (result.rankedChoiceRounds().isEmpty()) {
            lines.add("§eFirst Preference Round:");
            for (ResultService.OptionTally tally : result.tallies()) {
                lines.add("§7- §f" + tally.optionName() + "§7: §f" + tally.votes());
            }
            return List.copyOf(lines);
        }

        lines.add("§eIRV Round Breakdown:");
        for (ResultService.RankedChoiceRound round : result.rankedChoiceRounds()) {
            String heading = round.roundNumber() == 1 ? "First Preference Round" : "Round " + round.roundNumber();
            if (round.finalRound()) {
                heading += " (Final)";
            }
            lines.add("§6" + heading + "§7 - active ballots: §f" + round.activeBallots());

            for (ResultService.OptionTally tally : round.tallies()) {
                lines.add("§7- §f" + tally.optionName() + "§7: §f" + tally.votes());
            }

            if (round.exhaustedBallots() > 0) {
                lines.add("§7Exhausted ballots: §f" + round.exhaustedBallots());
            }

            if (round.eliminatedTally() != null) {
                lines.add("§cEliminated: §f" + round.eliminatedTally().optionName()
                        + " §7(" + round.eliminatedTally().votes() + ")");
            }
        }

        return List.copyOf(lines);
    }

    private static List<FieldBlock> formatRankedDiscordFields(ResultService.PollResult result, int maxFieldValueLength) {
        List<FieldBlock> fields = new ArrayList<>();
        fields.add(new FieldBlock("Final IRV Round", formatFinalRoundSummary(result)));

        if (result.rankedChoiceRounds().isEmpty()) {
            fields.add(new FieldBlock("First Preference Round", formatSimpleTallies(result.tallies())));
            return List.copyOf(fields);
        }

        List<String> roundBlocks = new ArrayList<>();
        for (ResultService.RankedChoiceRound round : result.rankedChoiceRounds()) {
            roundBlocks.add(formatDiscordRound(round));
        }

        fields.addAll(chunkFieldBlocks("IRV Round Breakdown", roundBlocks, maxFieldValueLength));
        return List.copyOf(fields);
    }

    private static String formatDiscordRound(ResultService.RankedChoiceRound round) {
        List<String> lines = new ArrayList<>();
        String heading = round.roundNumber() == 1 ? "First Preference Round" : "Round " + round.roundNumber();
        if (round.finalRound()) {
            heading += " (Final)";
        }
        lines.add(heading + " - active ballots: " + round.activeBallots());

        for (ResultService.OptionTally tally : round.tallies()) {
            lines.add(tally.optionName() + ": " + tally.votes());
        }

        if (round.exhaustedBallots() > 0) {
            lines.add("Exhausted ballots: " + round.exhaustedBallots());
        }

        if (round.eliminatedTally() != null) {
            lines.add("Eliminated: " + round.eliminatedTally().optionName()
                    + " (" + round.eliminatedTally().votes() + ")");
        }

        return String.join("\n", lines);
    }

    private static List<FieldBlock> chunkFieldBlocks(String baseName, List<String> blocks, int maxFieldValueLength) {
        int safeLimit = Math.max(128, maxFieldValueLength);
        List<FieldBlock> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String block : blocks) {
            String separator = current.isEmpty() ? "" : "\n\n";
            if (!current.isEmpty() && current.length() + separator.length() + block.length() > safeLimit) {
                fields.add(new FieldBlock(fieldName(baseName, fields.size() + 1), current.toString()));
                current.setLength(0);
                separator = "";
            }

            if (block.length() > safeLimit) {
                fields.add(new FieldBlock(fieldName(baseName, fields.size() + 1), truncate(block, safeLimit)));
                continue;
            }

            current.append(separator).append(block);
        }

        if (!current.isEmpty()) {
            fields.add(new FieldBlock(fieldName(baseName, fields.size() + 1), current.toString()));
        }

        if (fields.isEmpty()) {
            fields.add(new FieldBlock(baseName, "No recorded rounds."));
        }

        return List.copyOf(fields);
    }

    private static String fieldName(String baseName, int index) {
        return index == 1 ? baseName : baseName + " " + index;
    }

    private static String formatSimpleTallies(List<ResultService.OptionTally> tallies) {
        if (tallies.isEmpty()) {
            return "No recorded tallies.";
        }

        List<String> lines = new ArrayList<>();
        for (ResultService.OptionTally tally : tallies) {
            lines.add(tally.optionName() + ": " + tally.votes());
        }
        return String.join("\n", lines);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        if (maxLength <= 1) {
            return value.substring(0, maxLength);
        }
        return value.substring(0, maxLength - 1) + "...";
    }

    public record FieldBlock(String name, String value) {
        public FieldBlock {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(value, "value");
        }
    }
}
