package com.modnmetl.modnvote.domain.election;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Serializes a generic {@link ElectionDefinition} back into a
 * {@code polls.config_json} payload.
 *
 * This is the inverse of {@link ElectionDefinitionParser}. It is deterministic
 * and order-stable: offices and candidates are emitted in their existing list
 * order, so {@code parse(serialize(x)).equals(x)} holds for any definition that
 * the parser could have produced (the parser groups
 * {@link OfficeDependencyType#EXCLUDE_WINNERS} dependencies by office in office
 * order, and this serializer reproduces exactly that grouping by emitting each
 * office's {@code excludeWinnersFrom} array).
 *
 * Like the parser/validator, this performs no voting, persistence, counting, or
 * GUI work. It only converts the definition data model to JSON text.
 */
public final class ElectionDefinitionSerializer {

    public String serialize(ElectionDefinition definition) {
        Objects.requireNonNull(definition, "definition");

        JsonObject root = new JsonObject();
        root.addProperty("model", definition.model());

        JsonObject offices = new JsonObject();
        for (ContestDefinition contest : definition.contests()) {
            offices.add(contest.officeKey(), serializeOffice(contest, definition));
        }
        root.add("offices", offices);

        JsonObject candidates = new JsonObject();
        for (CandidateDefinition candidate : definition.candidates()) {
            candidates.add(candidate.candidateKey(), serializeCandidate(candidate));
        }
        root.add("candidateDefinitions", candidates);

        return new GsonBuilder()
                .setPrettyPrinting()
                .disableHtmlEscaping()
                .create()
                .toJson(root);
    }

    private JsonObject serializeOffice(ContestDefinition contest, ElectionDefinition definition) {
        JsonObject office = new JsonObject();
        if (contest.displayName() != null) {
            office.addProperty("displayName", contest.displayName());
        }
        if (contest.method() != null) {
            office.addProperty("method", contest.method().name());
        }
        office.addProperty("seats", contest.seats());
        if (contest.maxSelections() != null) {
            office.addProperty("maxSelections", contest.maxSelections());
        }
        office.addProperty("allowAbstain", contest.allowAbstain());
        office.add("candidates", toJsonArray(contest.candidateKeys()));

        List<String> excludeWinnersFrom = excludeWinnersFrom(contest.officeKey(), definition);
        if (!excludeWinnersFrom.isEmpty()) {
            office.add("excludeWinnersFrom", toJsonArray(excludeWinnersFrom));
        }
        return office;
    }

    private JsonObject serializeCandidate(CandidateDefinition candidate) {
        JsonObject json = new JsonObject();
        if (candidate.displayName() != null) {
            json.addProperty("displayName", candidate.displayName());
        }
        json.add("eligibleFor", toJsonArray(candidate.eligibleOfficeKeys()));
        return json;
    }

    /**
     * Collects, in dependency-list order, the source offices of every
     * EXCLUDE_WINNERS dependency that applies to {@code officeKey}. Re-parsing the
     * resulting {@code excludeWinnersFrom} array reproduces the same dependency
     * sequence the parser originally emitted.
     */
    private List<String> excludeWinnersFrom(String officeKey, ElectionDefinition definition) {
        List<String> out = new ArrayList<>();
        for (OfficeDependencyRule rule : definition.dependencies()) {
            if (rule.type() == OfficeDependencyType.EXCLUDE_WINNERS
                    && rule.appliesToOfficeKey().equals(officeKey)) {
                out.add(rule.fromOfficeKey());
            }
        }
        return out;
    }

    private JsonArray toJsonArray(List<String> values) {
        JsonArray array = new JsonArray();
        for (String value : values) {
            array.add(value);
        }
        return array;
    }
}
