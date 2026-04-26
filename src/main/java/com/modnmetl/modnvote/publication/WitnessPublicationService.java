package com.modnmetl.modnvote.publication;

import com.modnmetl.modnvote.domain.Poll;
import com.modnmetl.modnvote.domain.PollOption;
import com.modnmetl.modnvote.service.IntegrityVerificationService;
import com.modnmetl.modnvote.service.PollService;
import com.modnmetl.modnvote.service.PollServiceException;
import com.modnmetl.modnvote.service.ResultService;
import com.modnmetl.modnvote.storage.AnonymousBallotDao;
import com.modnmetl.modnvote.storage.DatabaseManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Best-effort publication of public poll witness events.
 *
 * Publication is deliberately non-authoritative:
 * - poll lifecycle and ballot persistence must complete before this service is called
 * - webhook delivery is asynchronous
 * - failed publication is logged but never rolls back gameplay state
 * - no player names, UUIDs, IPs, proof phrases, or participation receipts are published
 *
 * The current implementation publishes Discord-compatible webhook payloads from
 * publication.discord_webhooks. The service name remains generic so other witness
 * targets can be added later without changing lifecycle/voting code.
 */
public final class WitnessPublicationService {

    private static final int DISCORD_GREEN = 0x57F287;
    private static final int DISCORD_ORANGE = 0xFEE75C;
    private static final int DISCORD_BLUE = 0x5865F2;
    private static final int MAX_FIELD_VALUE_LENGTH = 1024;
    private static final int MAX_EMBED_DESCRIPTION_LENGTH = 4096;

    private final JavaPlugin plugin;
    private final PollService pollService;
    private final IntegrityVerificationService integrityVerificationService;
    private final AnonymousBallotDao anonymousBallotDao;
    private final HttpClient httpClient;
    private final Logger logger;

    public WitnessPublicationService(JavaPlugin plugin,
                                     PollService pollService,
                                     IntegrityVerificationService integrityVerificationService,
                                     DatabaseManager databaseManager,
                                     Logger logger) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.pollService = Objects.requireNonNull(pollService, "pollService");
        this.integrityVerificationService = Objects.requireNonNull(integrityVerificationService, "integrityVerificationService");
        this.anonymousBallotDao = new AnonymousBallotDao(Objects.requireNonNull(databaseManager, "databaseManager"));
        this.logger = Objects.requireNonNull(logger, "logger");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public void publishPollOpened(Poll poll, List<PollOption> options) {
        Objects.requireNonNull(poll, "poll");
        Objects.requireNonNull(options, "options");

        if (!plugin.getConfig().getBoolean("publication.publish_poll_opened", true)) {
            return;
        }

        DiscordEmbed embed = new DiscordEmbed(
                "Poll Opened",
                truncate(poll.title() + " is now open for voting.", MAX_EMBED_DESCRIPTION_LENGTH),
                DISCORD_GREEN,
                List.of(
                        new DiscordField("Poll ID", "#" + poll.pollId(), true),
                        new DiscordField("Type", poll.pollType().name(), true),
                        new DiscordField("Status", poll.status().name(), true),
                        new DiscordField("Options", String.valueOf(options.size()), true),
                        new DiscordField("Max Rankings", formatMaxRankings(poll, options), true),
                        new DiscordField("Partial Rankings", poll.allowPartialRanking() ? "Yes" : "No", true)
                )
        );

        publishEmbed(embed);
    }

    public void publishPollClosed(Poll poll,
                                  List<PollOption> options,
                                  ResultService.PollResult result) {
        Objects.requireNonNull(poll, "poll");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(result, "result");

        if (!plugin.getConfig().getBoolean("publication.publish_poll_closed", true)) {
            return;
        }

        List<DiscordField> fields = new ArrayList<>();
        fields.add(new DiscordField("Poll ID", "#" + poll.pollId(), true));
        fields.add(new DiscordField("Type", poll.pollType().name(), true));
        fields.add(new DiscordField("Status", poll.status().name(), true));
        fields.add(new DiscordField("Ballots", String.valueOf(result.totalVotes()), true));
        fields.add(new DiscordField("Options", String.valueOf(options.size()), true));

        if (result.winnerName() != null && !result.winnerName().isBlank()) {
            fields.add(new DiscordField("Winner", result.winnerName(), false));
        }

        fields.add(new DiscordField("Result Summary", formatResultSummary(result), false));

        DiscordEmbed embed = new DiscordEmbed(
                "Poll Closed",
                truncate(poll.title() + " has closed.", MAX_EMBED_DESCRIPTION_LENGTH),
                DISCORD_ORANGE,
                List.copyOf(fields)
        );

        publishEmbed(embed);
    }

    public void maybePublishCheckpoint(long pollId) {
        if (!plugin.getConfig().getBoolean("publication.publish_checkpoints", true)) {
            return;
        }

        int interval = plugin.getConfig().getInt("integrity.checkpoint_interval_ballots", 25);
        if (interval <= 0) {
            return;
        }

        try {
            int ballotCount = anonymousBallotDao.findAnonymousBallotsByPollId(pollId).size();
            if (ballotCount <= 0 || ballotCount % interval != 0) {
                return;
            }

            Poll poll = pollService.findPollById(pollId);
            if (poll == null) {
                return;
            }

            IntegrityVerificationService.IntegrityVerificationResult integrityResult =
                    integrityVerificationService.verifyPollIntegrity(pollId);

            publishCheckpoint(poll, ballotCount, interval, integrityResult);
        } catch (PollServiceException e) {
            logger.warning("Failed to prepare witness checkpoint for poll #" + pollId + ": " + e.getMessage());
        } catch (Exception e) {
            logger.warning("Unexpected failure while preparing witness checkpoint for poll #" + pollId + ": " + e.getMessage());
        }
    }

    private void publishCheckpoint(Poll poll,
                                   int ballotCount,
                                   int interval,
                                   IntegrityVerificationService.IntegrityVerificationResult integrityResult) {
        List<DiscordField> fields = new ArrayList<>();
        fields.add(new DiscordField("Poll ID", "#" + poll.pollId(), true));
        fields.add(new DiscordField("Status", poll.status().name(), true));
        fields.add(new DiscordField("Ballots", String.valueOf(ballotCount), true));
        fields.add(new DiscordField("Interval", String.valueOf(interval), true));
        fields.add(new DiscordField("Audit Chain", integrityResult.auditChainValid() ? "Valid" : "Invalid", true));
        fields.add(new DiscordField("Ballot Hashes", integrityResult.ballotHashesValid() ? "Valid" : "Invalid", true));
        fields.add(new DiscordField("Record Counts", integrityResult.recordCountsMatch() ? "Valid" : "Invalid", true));
        fields.add(new DiscordField("Overall Integrity", integrityResult.overallValid() ? "Valid" : "Needs review", true));

        if (!integrityResult.issues().isEmpty()) {
            fields.add(new DiscordField("Integrity Notes", truncate(String.join("\n", integrityResult.issues()), MAX_FIELD_VALUE_LENGTH), false));
        }

        DiscordEmbed embed = new DiscordEmbed(
                "Integrity Checkpoint",
                truncate("Public witness checkpoint for " + poll.title() + ".", MAX_EMBED_DESCRIPTION_LENGTH),
                DISCORD_BLUE,
                List.copyOf(fields)
        );

        publishEmbed(embed);
    }

    private void publishEmbed(DiscordEmbed embed) {
        List<String> webhookUrls = configuredWebhookUrls();
        if (webhookUrls.isEmpty()) {
            return;
        }

        String payload = buildDiscordPayload(embed);
        for (String webhookUrl : webhookUrls) {
            sendWebhook(webhookUrl, payload);
        }
    }

    private List<String> configuredWebhookUrls() {
        FileConfiguration config = plugin.getConfig();
        List<String> urls = config.getStringList("publication.discord_webhooks");
        if (urls.isEmpty()) {
            return List.of();
        }

        List<String> out = new ArrayList<>();
        for (String url : urls) {
            if (url == null || url.isBlank()) {
                continue;
            }
            out.add(url.trim());
        }
        return List.copyOf(out);
    }

    private void sendWebhook(String webhookUrl, String payload) {
        URI uri;
        try {
            uri = URI.create(webhookUrl);
        } catch (IllegalArgumentException e) {
            logger.warning("Skipping invalid witness publication webhook URL: " + redactWebhookUrl(webhookUrl));
            return;
        }

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    int statusCode = response.statusCode();
                    if (statusCode < 200 || statusCode >= 300) {
                        logger.warning("Witness publication webhook returned HTTP " + statusCode
                                + " for " + redactWebhookUrl(webhookUrl));
                    }
                })
                .exceptionally(error -> {
                    logger.warning("Witness publication webhook failed for " + redactWebhookUrl(webhookUrl)
                            + ": " + error.getMessage());
                    return null;
                });
    }

    private String buildDiscordPayload(DiscordEmbed embed) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        sb.append("\"username\":\"ModNVote Witness\",");
        sb.append("\"embeds\":[{");
        sb.append("\"title\":\"").append(jsonEscape(embed.title())).append("\",");
        sb.append("\"description\":\"").append(jsonEscape(embed.description())).append("\",");
        sb.append("\"color\":").append(embed.color()).append(',');
        sb.append("\"timestamp\":\"").append(Instant.now()).append("\",");
        sb.append("\"fields\":[");
        for (int i = 0; i < embed.fields().size(); i++) {
            DiscordField field = embed.fields().get(i);
            if (i > 0) {
                sb.append(',');
            }
            sb.append('{');
            sb.append("\"name\":\"").append(jsonEscape(field.name())).append("\",");
            sb.append("\"value\":\"").append(jsonEscape(truncate(field.value(), MAX_FIELD_VALUE_LENGTH))).append("\",");
            sb.append("\"inline\":").append(field.inline());
            sb.append('}');
        }
        sb.append(']');
        sb.append("}]");
        sb.append('}');
        return sb.toString();
    }

    private String formatMaxRankings(Poll poll, List<PollOption> options) {
        if (poll.maxRankings() <= 0) {
            return "All " + options.size();
        }
        return String.valueOf(poll.maxRankings());
    }

    private String formatResultSummary(ResultService.PollResult result) {
        if (result.tallies().isEmpty()) {
            return "No recorded tallies.";
        }

        List<String> lines = new ArrayList<>();
        for (ResultService.OptionTally tally : result.tallies()) {
            lines.add(tally.optionName() + ": " + tally.votes());
            if (lines.size() >= 8) {
                break;
            }
        }

        if (result.tallies().size() > lines.size()) {
            lines.add("...and " + (result.tallies().size() - lines.size()) + " more options");
        }

        return truncate(String.join("\n", lines), MAX_FIELD_VALUE_LENGTH);
    }

    private String redactWebhookUrl(String webhookUrl) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            return "<blank>";
        }

        String trimmed = webhookUrl.trim();
        int slash = trimmed.lastIndexOf('/');
        if (slash < 0 || slash >= trimmed.length() - 1) {
            return "<redacted webhook>";
        }

        return trimmed.substring(0, slash + 1) + "...";
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        if (maxLength <= 1) {
            return value.substring(0, maxLength);
        }
        return value.substring(0, maxLength - 1) + "…";
    }

    private String jsonEscape(String value) {
        if (value == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    private record DiscordEmbed(
            String title,
            String description,
            int color,
            List<DiscordField> fields
    ) {
        private DiscordEmbed {
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(description, "description");
            Objects.requireNonNull(fields, "fields");
            fields = List.copyOf(fields);
        }
    }

    private record DiscordField(
            String name,
            String value,
            boolean inline
    ) {
        private DiscordField {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(value, "value");
        }
    }
}
