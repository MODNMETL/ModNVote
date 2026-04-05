package com.modnmetl.modnvote.service;

import com.modnmetl.modnvote.api.PollStatus;
import com.modnmetl.modnvote.api.PollType;
import com.modnmetl.modnvote.domain.Poll;
import com.modnmetl.modnvote.domain.PollOption;
import com.modnmetl.modnvote.platform.PlatformAdapter;
import com.modnmetl.modnvote.storage.AuditEventDao;
import com.modnmetl.modnvote.storage.DatabaseManager;
import com.modnmetl.modnvote.storage.PollDao;
import com.modnmetl.modnvote.storage.PollOptionDao;

import java.security.SecureRandom;
import java.sql.Connection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Service layer for poll lifecycle and authoring operations.
 */
public final class PollService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int MAX_TITLE_LENGTH = 48;
    private static final int MAX_DESCRIPTION_LENGTH = 240;
    private final DatabaseManager databaseManager;
    private final PlatformAdapter platformAdapter;
    private final Logger logger;
    private final PollDao pollDao;
    private final PollOptionDao pollOptionDao;
    private final AuditEventDao auditEventDao;

    public PollService(DatabaseManager databaseManager,
                       PlatformAdapter platformAdapter,
                       Logger logger) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager");
        this.platformAdapter = Objects.requireNonNull(platformAdapter, "platformAdapter");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.pollDao = new PollDao(databaseManager);
        this.pollOptionDao = new PollOptionDao(databaseManager);
        this.auditEventDao = new AuditEventDao(databaseManager);
    }

    public boolean isInitialized() {
        return true;
    }

    public String getStatusSummary() {
        return "PollService ready";
    }

    public List<Poll> listPolls() throws PollServiceException {
        try {
            return pollDao.findAllPolls();
        } catch (Exception e) {
            throw new PollServiceException("Failed to load polls", e);
        }
    }

    public Poll findPollById(long pollId) throws PollServiceException {
        try {
            return pollDao.findPollById(pollId);
        } catch (Exception e) {
            throw new PollServiceException("Failed to load poll #" + pollId, e);
        }
    }

    public long createPoll(String createdBy,
                           PollType pollType) throws PollServiceException {
        requireNonBlank(createdBy, "createdBy");
        Objects.requireNonNull(pollType, "pollType");

        if (!isSupportedAuthoringType(pollType)) {
            throw new PollServiceException("Poll type " + pollType.name() + " is not yet supported for command authoring.");
        }

        try {
            String slug = generateDraftSlug(pollType);

            Poll poll = new Poll(
                    0L,
                    slug,
                    defaultTitleFor(pollType),
                    "",
                    pollType,
                    PollStatus.DRAFT,
                    null,
                    null,
                    pollType == PollType.YES_NO ? 1 : 0,
                    1,
                    true,
                    true,
                    generateParticipationSecret()
            );

            List<PollOption> initialOptions = pollType == PollType.YES_NO
                    ? List.of(
                    new PollOption(0L, 0L, "yes", "Yes", "Affirmative response for this poll.", 0),
                    new PollOption(0L, 0L, "no", "No", "Negative response for this poll.", 1)
            )
                    : List.of();

            try (Connection connection = databaseManager.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    long pollId = pollDao.insertPoll(connection, poll, createdBy, "UUID_AND_IP_HEURISTIC", "{}");

                    if (!initialOptions.isEmpty()) {
                        pollOptionDao.insertOptions(connection, pollId, initialOptions);
                    }

                    auditEventDao.insertPollEvent(
                            connection,
                            pollId,
                            "POLL_CREATED",
                            "actor=" + createdBy
                                    + ";slug=" + poll.slug()
                                    + ";type=" + poll.pollType().name()
                                    + ";status=" + poll.status().name()
                    );

                    connection.commit();
                    return pollId;
                } catch (Exception e) {
                    connection.rollback();
                    throw e;
                } finally {
                    connection.setAutoCommit(true);
                }
            }
        } catch (Exception e) {
            throw new PollServiceException("Failed to create poll", e);
        }
    }

    public long createSeedBreedPoll(String createdBy) throws PollServiceException {
        try {
            String slug = "breed-of-the-month-" + Instant.now().toEpochMilli();

            if (pollDao.pollExistsBySlug(slug)) {
                throw new IllegalStateException("Generated slug already exists: " + slug);
            }

            Poll poll = new Poll(
                    0L,
                    slug,
                    "Breed of the Month",
                    "Rank the nominated horse breeds in order of preference.",
                    PollType.RANKED_SINGLE_WINNER,
                    PollStatus.READY,
                    null,
                    null,
                    6,
                    1,
                    true,
                    true,
                    generateParticipationSecret()
            );

            List<PollOption> options = List.of(
                    new PollOption(0L, 0L, "arabian", "Arabian", "Elegant, fast, and refined.", 0),
                    new PollOption(0L, 0L, "shire", "Shire", "Large, powerful, and steady.", 1),
                    new PollOption(0L, 0L, "mustang", "Mustang", "Hardy, agile, and spirited.", 2),
                    new PollOption(0L, 0L, "friesian", "Friesian", "Striking black coat and noble bearing.", 3),
                    new PollOption(0L, 0L, "andalusian", "Andalusian", "Strong, responsive, and graceful.", 4),
                    new PollOption(0L, 0L, "clydesdale", "Clydesdale", "Heavy draft strength with calm temperament.", 5)
            );

            try (Connection connection = databaseManager.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    long pollId = pollDao.insertPoll(connection, poll, createdBy, "UUID_AND_IP_HEURISTIC", "{}");
                    pollOptionDao.insertOptions(connection, pollId, options);
                    auditEventDao.insertPollEvent(
                            connection,
                            pollId,
                            "POLL_CREATED",
                            "actor=" + createdBy
                                    + ";slug=" + poll.slug()
                                    + ";type=" + poll.pollType().name()
                                    + ";status=" + poll.status().name()
                    );
                    connection.commit();
                    return pollId;
                } catch (Exception e) {
                    connection.rollback();
                    throw e;
                } finally {
                    connection.setAutoCommit(true);
                }
            }
        } catch (Exception e) {
            throw new PollServiceException("Failed to create seed breed poll", e);
        }
    }

    public void updatePollTitle(long pollId, String title, String actor) throws PollServiceException {
        requireNonBlank(title, "title");
        requireNonBlank(actor, "actor");
        if (title.length() > MAX_TITLE_LENGTH) {
            throw new PollServiceException("title must not exceed " + MAX_TITLE_LENGTH + " characters.");
        }

        Poll poll = requireDraftPoll(pollId);

        try (Connection connection = databaseManager.getConnection()) {
            connection.setAutoCommit(false);
            try {
                pollDao.updatePollTitle(connection, pollId, title);
                auditEventDao.insertPollEvent(
                        connection,
                        pollId,
                        "POLL_TITLE_UPDATED",
                        "actor=" + actor + ";poll_id=" + pollId + ";old_title=" + poll.title() + ";new_title=" + title
                );
                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (Exception e) {
            throw new PollServiceException("Failed to update title for poll #" + pollId, e);
        }
    }

    public void updatePollDescription(long pollId, String description, String actor) throws PollServiceException {
        Objects.requireNonNull(description, "description");
        requireNonBlank(actor, "actor");
        if (description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new PollServiceException("description must not exceed " + MAX_DESCRIPTION_LENGTH + " characters.");
        }

        requireDraftPoll(pollId);

        try (Connection connection = databaseManager.getConnection()) {
            connection.setAutoCommit(false);
            try {
                pollDao.updatePollDescription(connection, pollId, description);
                auditEventDao.insertPollEvent(
                        connection,
                        pollId,
                        "POLL_DESCRIPTION_UPDATED",
                        "actor=" + actor + ";poll_id=" + pollId
                );
                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (Exception e) {
            throw new PollServiceException("Failed to update description for poll #" + pollId, e);
        }
    }

    public void updatePollMaxRankings(long pollId, int maxRankings, String actor) throws PollServiceException {
        requireNonBlank(actor, "actor");

        Poll poll = requireDraftPoll(pollId);
        requireRankedPoll(poll);

        if (maxRankings < 0) {
            throw new PollServiceException("maxRankings must not be negative.");
        }

        try (Connection connection = databaseManager.getConnection()) {
            connection.setAutoCommit(false);
            try {
                pollDao.updatePollMaxRankings(connection, pollId, maxRankings);
                auditEventDao.insertPollEvent(
                        connection,
                        pollId,
                        "POLL_MAX_RANKINGS_UPDATED",
                        "actor=" + actor + ";poll_id=" + pollId + ";max_rankings=" + maxRankings
                );
                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (Exception e) {
            throw new PollServiceException("Failed to update max rankings for poll #" + pollId, e);
        }
    }

    public void updatePollAllowPartialRanking(long pollId,
                                              boolean allowPartialRanking,
                                              String actor) throws PollServiceException {
        requireNonBlank(actor, "actor");

        Poll poll = requireDraftPoll(pollId);
        requireRankedPoll(poll);

        try (Connection connection = databaseManager.getConnection()) {
            connection.setAutoCommit(false);
            try {
                pollDao.updatePollAllowPartialRanking(connection, pollId, allowPartialRanking);
                auditEventDao.insertPollEvent(
                        connection,
                        pollId,
                        "POLL_ALLOW_PARTIAL_UPDATED",
                        "actor=" + actor + ";poll_id=" + pollId + ";allow_partial_ranking=" + allowPartialRanking
                );
                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (Exception e) {
            throw new PollServiceException("Failed to update partial-ranking setting for poll #" + pollId, e);
        }
    }

    public long addOption(long pollId,
                          String key,
                          String displayName,
                          String description,
                          String actor) throws PollServiceException {
        requireNonBlank(key, "key");
        requireNonBlank(displayName, "displayName");
        Objects.requireNonNull(description, "description");
        requireNonBlank(actor, "actor");

        Poll poll = requireDraftPoll(pollId);

        try {
            List<PollOption> existing = pollOptionDao.findOptionsByPollId(pollId);

            if (poll.pollType() == PollType.YES_NO) {
                throw new PollServiceException("YES_NO polls use the canonical 'yes' and 'no' options and do not allow additional options.");
            }

            for (PollOption option : existing) {
                if (option.key().equalsIgnoreCase(key)) {
                    throw new PollServiceException("An option with key '" + key + "' already exists in poll #" + pollId + ".");
                }
            }

            int nextDisplayOrder = existing.stream()
                    .mapToInt(PollOption::displayOrder)
                    .max()
                    .orElse(-1) + 1;

            PollOption option = new PollOption(
                    0L,
                    pollId,
                    key,
                    displayName,
                    description,
                    nextDisplayOrder
            );

            try (Connection connection = databaseManager.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    long optionId = pollOptionDao.insertOption(connection, pollId, option);
                    auditEventDao.insertPollEvent(
                            connection,
                            pollId,
                            "POLL_OPTION_ADDED",
                            "actor=" + actor + ";poll_id=" + pollId + ";option_id=" + optionId + ";option_key=" + key
                    );
                    connection.commit();
                    return optionId;
                } catch (Exception e) {
                    connection.rollback();
                    throw e;
                } finally {
                    connection.setAutoCommit(true);
                }
            }
        } catch (PollServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new PollServiceException("Failed to add option to poll #" + pollId, e);
        }
    }

    public void updateOptionName(long pollId,
                                 long optionId,
                                 String displayName,
                                 String actor) throws PollServiceException {
        requireNonBlank(displayName, "displayName");
        requireNonBlank(actor, "actor");

        requireDraftPoll(pollId);
        PollOption option = requireOptionInPoll(pollId, optionId);

        try (Connection connection = databaseManager.getConnection()) {
            connection.setAutoCommit(false);
            try {
                pollOptionDao.updateOptionDisplayName(connection, optionId, displayName);
                auditEventDao.insertPollEvent(
                        connection,
                        pollId,
                        "POLL_OPTION_NAME_UPDATED",
                        "actor=" + actor + ";poll_id=" + pollId + ";option_id=" + optionId
                                + ";old_name=" + option.displayName() + ";new_name=" + displayName
                );
                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (Exception e) {
            throw new PollServiceException("Failed to update option name for option #" + optionId, e);
        }
    }

    public void updateOptionDescription(long pollId,
                                        long optionId,
                                        String description,
                                        String actor) throws PollServiceException {
        Objects.requireNonNull(description, "description");
        requireNonBlank(actor, "actor");

        requireDraftPoll(pollId);
        requireOptionInPoll(pollId, optionId);

        try (Connection connection = databaseManager.getConnection()) {
            connection.setAutoCommit(false);
            try {
                pollOptionDao.updateOptionDescription(connection, optionId, description);
                auditEventDao.insertPollEvent(
                        connection,
                        pollId,
                        "POLL_OPTION_DESCRIPTION_UPDATED",
                        "actor=" + actor + ";poll_id=" + pollId + ";option_id=" + optionId
                );
                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (Exception e) {
            throw new PollServiceException("Failed to update option description for option #" + optionId, e);
        }
    }

    public void moveOption(long pollId,
                           long optionId,
                           int displayOrder,
                           String actor) throws PollServiceException {
        requireNonBlank(actor, "actor");

        if (displayOrder < 0) {
            throw new PollServiceException("displayOrder must not be negative.");
        }

        Poll poll = requireDraftPoll(pollId);
        requireOptionInPoll(pollId, optionId);

        if (poll.pollType() == PollType.YES_NO) {
            throw new PollServiceException("YES_NO polls use fixed option ordering and do not allow option reordering.");
        }

        try (Connection connection = databaseManager.getConnection()) {
            connection.setAutoCommit(false);
            try {
                pollOptionDao.updateOptionDisplayOrder(connection, optionId, displayOrder);
                auditEventDao.insertPollEvent(
                        connection,
                        pollId,
                        "POLL_OPTION_MOVED",
                        "actor=" + actor + ";poll_id=" + pollId + ";option_id=" + optionId + ";display_order=" + displayOrder
                );
                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (Exception e) {
            throw new PollServiceException("Failed to move option #" + optionId, e);
        }
    }

    public void removeOption(long pollId,
                             long optionId,
                             String actor) throws PollServiceException {
        requireNonBlank(actor, "actor");

        Poll poll = requireDraftPoll(pollId);
        requireOptionInPoll(pollId, optionId);

        if (poll.pollType() == PollType.YES_NO) {
            throw new PollServiceException("YES_NO polls require the canonical 'yes' and 'no' options and do not allow option removal.");
        }

        try (Connection connection = databaseManager.getConnection()) {
            connection.setAutoCommit(false);
            try {
                pollOptionDao.deleteOption(connection, optionId);
                auditEventDao.insertPollEvent(
                        connection,
                        pollId,
                        "POLL_OPTION_REMOVED",
                        "actor=" + actor + ";poll_id=" + pollId + ";option_id=" + optionId
                );
                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (Exception e) {
            throw new PollServiceException("Failed to remove option #" + optionId, e);
        }
    }

    public PollValidationResult validatePollDefinition(long pollId) throws PollServiceException {
        try {
            Poll poll = pollDao.findPollById(pollId);
            if (poll == null) {
                throw new PollServiceException("Poll #" + pollId + " does not exist.");
            }

            List<String> issues = new ArrayList<>();
            List<PollOption> options = pollOptionDao.findOptionsByPollId(pollId);

            if (poll.title().isBlank()) {
                issues.add("Poll title must not be blank.");
            }
            if (poll.description().isBlank()) {
                issues.add("Poll description must not be blank.");
            }

            for (PollOption option : options) {
                if (option.displayName().isBlank()) {
                    issues.add("Option #" + option.optionId() + " has a blank display name.");
                }
                if (option.description().isBlank()) {
                    issues.add("Option #" + option.optionId() + " has a blank description.");
                }
            }

            if (poll.pollType() == PollType.YES_NO) {
                if (options.size() != 2) {
                    issues.add("YES_NO polls must have exactly 2 options.");
                }

                boolean hasYesKey = false;
                boolean hasNoKey = false;

                for (PollOption option : options) {
                    String key = option.key().trim().toLowerCase(java.util.Locale.ROOT);

                    if ("yes".equals(key)) {
                        hasYesKey = true;
                    } else if ("no".equals(key)) {
                        hasNoKey = true;
                    } else {
                        issues.add("YES_NO polls may only use the canonical option keys 'yes' and 'no'.");
                    }
                }

                if (!hasYesKey) {
                    issues.add("YES_NO polls must contain an option with key 'yes'.");
                }
                if (!hasNoKey) {
                    issues.add("YES_NO polls must contain an option with key 'no'.");
                }

                if (poll.maxRankings() != 1) {
                    issues.add("YES_NO polls must use maxRankings = 1.");
                }
            } else if (poll.pollType() == PollType.RANKED_SINGLE_WINNER) {
                if (options.size() < 2) {
                    issues.add("Ranked single-winner polls must have at least 2 options.");
                }
                if (poll.maxRankings() < 0) {
                    issues.add("maxRankings must not be negative.");
                }
                if (!options.isEmpty() && poll.maxRankings() > options.size()) {
                    issues.add("maxRankings must not exceed the number of options.");
                }
            } else {
                issues.add("Poll type " + poll.pollType().name() + " is not yet supported by the authoring workflow.");
            }

            return new PollValidationResult(
                    poll.pollId(),
                    poll.title(),
                    issues.isEmpty(),
                    List.copyOf(issues)
            );
        } catch (PollServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new PollServiceException("Failed to validate poll #" + pollId, e);
        }
    }

    public void readyPoll(long pollId, String actor) throws PollServiceException {
        requireNonBlank(actor, "actor");

        Poll poll = requirePollExists(pollId);
        if (poll.status() != PollStatus.DRAFT) {
            throw new PollServiceException("Poll #" + pollId + " is not in DRAFT state.");
        }

        PollValidationResult validationResult = validatePollDefinition(pollId);
        if (!validationResult.valid()) {
            throw new PollServiceException("Poll #" + pollId + " is not ready: " + String.join(" ", validationResult.issues()));
        }

        try (Connection connection = databaseManager.getConnection()) {
            connection.setAutoCommit(false);
            try {
                pollDao.updatePollStatus(connection, pollId, PollStatus.READY);
                auditEventDao.insertPollEvent(
                        connection,
                        pollId,
                        "POLL_READY",
                        "actor=" + actor + ";poll_id=" + pollId + ";from=DRAFT;to=READY"
                );
                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (Exception e) {
            throw new PollServiceException("Failed to mark poll #" + pollId + " as READY.", e);
        }
    }

    public void openPoll(long pollId, String actor) throws PollServiceException {
        try {
            Poll poll = pollDao.findPollById(pollId);
            if (poll == null) {
                throw new PollServiceException("Poll #" + pollId + " does not exist.");
            }
            if (poll.status() != PollStatus.READY) {
                throw new PollServiceException("Poll #" + pollId + " is not in READY state.");
            }

            try (Connection connection = databaseManager.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    pollDao.updatePollStatus(connection, pollId, PollStatus.OPEN);
                    auditEventDao.insertPollEvent(
                            connection,
                            pollId,
                            "POLL_OPENED",
                            "actor=" + actor + ";poll_id=" + pollId + ";from=READY;to=OPEN"
                    );
                    connection.commit();
                } catch (Exception e) {
                    connection.rollback();
                    throw e;
                } finally {
                    connection.setAutoCommit(true);
                }
            }
        } catch (PollServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new PollServiceException("Failed to open poll #" + pollId, e);
        }
    }

    public void closePoll(long pollId, String actor) throws PollServiceException {
        try {
            Poll poll = pollDao.findPollById(pollId);
            if (poll == null) {
                throw new PollServiceException("Poll #" + pollId + " does not exist.");
            }
            if (poll.status() != PollStatus.OPEN) {
                throw new PollServiceException("Poll #" + pollId + " is not in OPEN state.");
            }

            try (Connection connection = databaseManager.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    pollDao.updatePollStatus(connection, pollId, PollStatus.CLOSED);
                    auditEventDao.insertPollEvent(
                            connection,
                            pollId,
                            "POLL_CLOSED",
                            "actor=" + actor + ";poll_id=" + pollId + ";from=OPEN;to=CLOSED"
                    );
                    connection.commit();
                } catch (Exception e) {
                    connection.rollback();
                    throw e;
                } finally {
                    connection.setAutoCommit(true);
                }
            }
        } catch (PollServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new PollServiceException("Failed to close poll #" + pollId, e);
        }
    }

    public void deletePoll(long pollId, String actor) throws PollServiceException {
        requireNonBlank(actor, "actor");

        Poll poll = requirePollExists(pollId);
        if (poll.status() != PollStatus.DRAFT && poll.status() != PollStatus.READY) {
            throw new PollServiceException("Poll #" + pollId + " can only be deleted while in DRAFT or READY state.");
        }

        try (Connection connection = databaseManager.getConnection()) {
            connection.setAutoCommit(false);
            try {
                auditEventDao.insertPollEvent(
                        connection,
                        pollId,
                        "POLL_DELETED",
                        "actor=" + actor + ";poll_id=" + pollId + ";from=" + poll.status().name()
                );

                pollDao.deletePoll(connection, pollId);
                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (Exception e) {
            throw new PollServiceException("Failed to delete poll #" + pollId, e);
        }
    }

    private Poll requirePollExists(long pollId) throws PollServiceException {
        try {
            Poll poll = pollDao.findPollById(pollId);
            if (poll == null) {
                throw new PollServiceException("Poll #" + pollId + " does not exist.");
            }
            return poll;
        } catch (PollServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new PollServiceException("Failed to load poll #" + pollId, e);
        }
    }

    private Poll requireDraftPoll(long pollId) throws PollServiceException {
        Poll poll = requirePollExists(pollId);
        if (poll.status() != PollStatus.DRAFT) {
            throw new PollServiceException("Poll #" + pollId + " can only be edited while in DRAFT state.");
        }
        return poll;
    }

    private PollOption requireOptionInPoll(long pollId, long optionId) throws PollServiceException {
        try {
            PollOption option = pollOptionDao.findOptionById(optionId);
            if (option == null) {
                throw new PollServiceException("Option #" + optionId + " does not exist.");
            }
            if (option.pollId() != pollId) {
                throw new PollServiceException("Option #" + optionId + " does not belong to poll #" + pollId + ".");
            }
            return option;
        } catch (PollServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new PollServiceException("Failed to load option #" + optionId, e);
        }
    }

    private void requireRankedPoll(Poll poll) throws PollServiceException {
        if (poll.pollType() != PollType.RANKED_SINGLE_WINNER) {
            throw new PollServiceException("This setting can only be changed for ranked single-winner polls.");
        }
    }

    private boolean isSupportedAuthoringType(PollType pollType) {
        return pollType == PollType.YES_NO || pollType == PollType.RANKED_SINGLE_WINNER;
    }

    private String defaultTitleFor(PollType pollType) {
        return switch (pollType) {
            case YES_NO -> "Untitled yes/no poll";
            case RANKED_SINGLE_WINNER -> "Untitled ranked poll";
            default -> "Untitled poll";
        };
    }

    private String generateDraftSlug(PollType pollType) {
        String prefix = switch (pollType) {
            case YES_NO -> "yes-no-draft";
            case RANKED_SINGLE_WINNER -> "ranked-draft";
            default -> "poll-draft";
        };

        return prefix + "-" + Instant.now().toEpochMilli();
    }

    private String generateParticipationSecret() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private String requireNonBlank(String value, String fieldName) throws PollServiceException {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new PollServiceException(fieldName + " must not be blank.");
        }
        return value;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public PlatformAdapter getPlatformAdapter() {
        return platformAdapter;
    }

    public Logger getLogger() {
        return logger;
    }

    public record PollValidationResult(
            long pollId,
            String pollTitle,
            boolean valid,
            List<String> issues
    ) {
        public PollValidationResult {
            Objects.requireNonNull(pollTitle, "pollTitle");
            Objects.requireNonNull(issues, "issues");
            issues = List.copyOf(issues);
        }
    }
}