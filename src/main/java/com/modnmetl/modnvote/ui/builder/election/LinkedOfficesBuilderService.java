package com.modnmetl.modnvote.ui.builder.election;

import com.modnmetl.modnvote.domain.election.ElectionDefinition;
import com.modnmetl.modnvote.domain.election.ElectionDefinitionException;
import com.modnmetl.modnvote.domain.election.ElectionDefinitionParser;
import com.modnmetl.modnvote.domain.election.ElectionDefinitionSerializer;
import com.modnmetl.modnvote.service.ElectionDefinitionService;
import com.modnmetl.modnvote.service.PollService;
import com.modnmetl.modnvote.service.PollServiceException;

import java.util.Objects;
import java.util.UUID;

/**
 * Bukkit-free coordinator behind the linked-offices builder GUI.
 *
 * It is the single bridge between the GUI's {@link LinkedOfficesBuilderState} and
 * the authoritative service layer:
 *
 * <pre>
 *   builder state -&gt; ElectionDefinition -&gt; serialize -&gt; PollService.updatePollConfigJson
 *   config_json   -&gt; parse -&gt; ElectionDefinition -&gt; builder state
 * </pre>
 *
 * Validation reuses {@link ElectionDefinitionService} (no duplicate validation
 * logic in the GUI). Saving goes exclusively through
 * {@link PollService#updatePollConfigJson}; the GUI never writes the DAO directly
 * and never becomes a second source of truth. This class implements no voting,
 * ballot storage, counting, or result logic.
 */
public final class LinkedOfficesBuilderService {

    private final PollService pollService;
    private final ElectionDefinitionService electionDefinitionService;
    private final ElectionDefinitionParser parser;
    private final ElectionDefinitionSerializer serializer;

    public LinkedOfficesBuilderService(PollService pollService) {
        this(pollService, new ElectionDefinitionService(),
                new ElectionDefinitionParser(), new ElectionDefinitionSerializer());
    }

    public LinkedOfficesBuilderService(PollService pollService,
                                       ElectionDefinitionService electionDefinitionService,
                                       ElectionDefinitionParser parser,
                                       ElectionDefinitionSerializer serializer) {
        this.pollService = Objects.requireNonNull(pollService, "pollService");
        this.electionDefinitionService = Objects.requireNonNull(electionDefinitionService, "electionDefinitionService");
        this.parser = Objects.requireNonNull(parser, "parser");
        this.serializer = Objects.requireNonNull(serializer, "serializer");
    }

    /**
     * Builds a builder session for a poll from its current {@code config_json}.
     * A valid or merely-invalid (parseable) definition is loaded for editing; a
     * blank or unparseable definition opens an empty buffer (repair mode) so the
     * GUI never crashes on bad data.
     */
    public LinkedOfficesBuilderSession openSession(UUID adminId, long pollId, String configJson) {
        return new LinkedOfficesBuilderSession(adminId, pollId, loadState(configJson));
    }

    /** Parses {@code config_json} into an edit buffer, falling back to empty on failure. */
    public LinkedOfficesBuilderState loadState(String configJson) {
        try {
            ElectionDefinition definition = parser.parse(configJson == null || configJson.isBlank() ? "" : configJson);
            return LinkedOfficesBuilderState.fromDefinition(definition);
        } catch (ElectionDefinitionException e) {
            return LinkedOfficesBuilderState.empty();
        }
    }

    /** Serializes the current buffer to a {@code config_json} payload. */
    public String serialize(LinkedOfficesBuilderState state) {
        return serializer.serialize(state.toDefinition());
    }

    /** Validates the current buffer without persisting anything. */
    public ElectionDefinitionService.ElectionDefinitionValidationResult validate(LinkedOfficesBuilderState state) {
        return electionDefinitionService.validate(serialize(state));
    }

    /**
     * Serializes the buffer and saves it through {@link PollService}. The service
     * re-validates and rejects invalid definitions, so an invalid buffer cannot be
     * persisted. The GUI never bypasses this path.
     */
    public void save(long pollId, LinkedOfficesBuilderState state, String actor) throws PollServiceException {
        pollService.updatePollConfigJson(pollId, serialize(state), actor);
    }
}
