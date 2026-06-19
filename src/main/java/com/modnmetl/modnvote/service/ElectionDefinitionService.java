package com.modnmetl.modnvote.service;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.modnmetl.modnvote.domain.Poll;
import com.modnmetl.modnvote.domain.election.ElectionDefinition;
import com.modnmetl.modnvote.domain.election.ElectionDefinitionException;
import com.modnmetl.modnvote.domain.election.ElectionDefinitionParser;
import com.modnmetl.modnvote.domain.election.ElectionDefinitionValidator;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Read-only service boundary around the linked-offices election definition
 * parser and validator.
 *
 * Responsibilities:
 * - parse a poll's {@code config_json} (or raw JSON) into an
 *   {@link ElectionDefinition}
 * - validate it
 * - return a structured {@link ElectionDefinitionValidationResult} instead of
 *   throwing, so admin-facing callers can present clear feedback
 *
 * Strict boundaries (Tranche 2B):
 * - this service performs no persistence writes
 * - it performs no poll lifecycle changes
 * - it never touches voter identity, participation records, or ballot content
 * - it does NOT enable linked-offices voting; it only validates definitions
 */
public final class ElectionDefinitionService {

    private final ElectionDefinitionParser parser;
    private final ElectionDefinitionValidator validator;

    public ElectionDefinitionService() {
        this(new ElectionDefinitionParser(), new ElectionDefinitionValidator());
    }

    public ElectionDefinitionService(ElectionDefinitionParser parser,
                                     ElectionDefinitionValidator validator) {
        this.parser = Objects.requireNonNull(parser, "parser");
        this.validator = Objects.requireNonNull(validator, "validator");
    }

    /**
     * Validates the election definition carried in the poll's {@code config_json}.
     */
    public ElectionDefinitionValidationResult validate(Poll poll) {
        Objects.requireNonNull(poll, "poll");
        return validate(poll.configJson());
    }

    /**
     * Validates a raw {@code config_json} payload as a linked-offices definition.
     *
     * Parser and validator failures are surfaced as issues rather than thrown.
     */
    public ElectionDefinitionValidationResult validate(String configJson) {
        String rawModel = peekModel(configJson);

        ElectionDefinition definition;
        try {
            definition = parser.parse(configJson == null ? "" : configJson);
        } catch (ElectionDefinitionException e) {
            return new ElectionDefinitionValidationResult(
                    false,
                    Optional.empty(),
                    List.of(e.getMessage()),
                    Optional.ofNullable(rawModel)
            );
        }

        List<String> issues = validator.findIssues(definition);
        return new ElectionDefinitionValidationResult(
                issues.isEmpty(),
                Optional.of(definition),
                issues,
                Optional.ofNullable(definition.model())
        );
    }

    /**
     * Returns true if the JSON declares {@code model == LINKED_OFFICES}, regardless
     * of whether the rest of the definition is valid. Used by vote/result guards to
     * recognise a linked-offices definition without enabling any voting path.
     */
    public boolean isLinkedOfficesModel(String configJson) {
        return ElectionDefinition.LINKED_OFFICES_MODEL.equals(peekModel(configJson));
    }

    private String peekModel(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            return null;
        }
        try {
            JsonElement element = JsonParser.parseString(configJson);
            if (!element.isJsonObject()) {
                return null;
            }
            JsonObject object = element.getAsJsonObject();
            if (!object.has("model") || object.get("model").isJsonNull()) {
                return null;
            }
            return object.get("model").getAsString();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Structured result of validating a linked-offices definition.
     *
     * @param valid      whether the definition parsed and passed all validation rules
     * @param definition the parsed definition when parsing succeeded, otherwise empty
     * @param issues     admin-facing issue messages (empty when valid)
     * @param rawModel   the declared model string, when it could be determined
     */
    public record ElectionDefinitionValidationResult(
            boolean valid,
            Optional<ElectionDefinition> definition,
            List<String> issues,
            Optional<String> rawModel
    ) {
        public ElectionDefinitionValidationResult {
            Objects.requireNonNull(definition, "definition");
            Objects.requireNonNull(rawModel, "rawModel");
            issues = List.copyOf(issues);
        }
    }
}
