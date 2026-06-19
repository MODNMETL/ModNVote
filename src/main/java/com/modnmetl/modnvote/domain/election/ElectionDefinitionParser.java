package com.modnmetl.modnvote.domain.election;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Parses a {@code polls.config_json} payload into a generic
 * {@link ElectionDefinition}.
 *
 * Parsing is structural only and intentionally generic:
 * - office order and candidate order are preserved exactly as supplied
 * - {@code excludeWinnersFrom} entries are converted into generic
 *   {@link OfficeDependencyType#EXCLUDE_WINNERS} dependency rules
 * - missing {@code maxSelections} is left null
 * - missing {@code allowAbstain} defaults to false
 * - unknown model values and unknown counting methods are rejected
 *
 * Deeper semantic validation (uniqueness, eligibility, acyclic dependencies,
 * seat counts, etc.) is performed separately by
 * {@link ElectionDefinitionValidator}. This keeps parsing and validation
 * independently testable.
 *
 * This parser does not implement voting, persistence, counting, or GUI flow.
 */
public final class ElectionDefinitionParser {

    public ElectionDefinition parse(String configJson) {
        Objects.requireNonNull(configJson, "configJson");

        JsonObject root = readRootObject(configJson);

        String model = readString(root, "model");
        if (model == null) {
            throw new ElectionDefinitionException("Election definition is missing the required 'model' field.");
        }
        if (!ElectionDefinition.LINKED_OFFICES_MODEL.equals(model)) {
            throw new ElectionDefinitionException(
                    "Unknown election model '" + model + "'. Expected '" + ElectionDefinition.LINKED_OFFICES_MODEL + "'.");
        }

        List<ContestDefinition> contests = new ArrayList<>();
        List<OfficeDependencyRule> dependencies = new ArrayList<>();
        parseOffices(root, contests, dependencies);

        List<CandidateDefinition> candidates = parseCandidateDefinitions(root);

        return new ElectionDefinition(model, contests, candidates, dependencies);
    }

    private void parseOffices(JsonObject root,
                              List<ContestDefinition> contests,
                              List<OfficeDependencyRule> dependencies) {
        if (!root.has("offices") || root.get("offices").isJsonNull()) {
            return;
        }
        JsonObject offices = asObject(root.get("offices"), "offices");

        for (Map.Entry<String, JsonElement> entry : offices.entrySet()) {
            String officeKey = entry.getKey();
            JsonObject officeObj = asObject(entry.getValue(), "office '" + officeKey + "'");

            String displayName = readString(officeObj, "displayName");
            CountingMethod method = readMethod(officeKey, officeObj);
            int seats = readInt(officeObj, "seats", 0);
            Integer maxSelections = readNullableInt(officeObj, "maxSelections");
            boolean allowAbstain = readBoolean(officeObj, "allowAbstain", false);
            List<String> candidateKeys = readStringArray(officeObj, "candidates");

            contests.add(new ContestDefinition(
                    officeKey,
                    displayName,
                    method,
                    seats,
                    maxSelections,
                    allowAbstain,
                    candidateKeys
            ));

            for (String fromOfficeKey : readStringArray(officeObj, "excludeWinnersFrom")) {
                dependencies.add(new OfficeDependencyRule(
                        OfficeDependencyType.EXCLUDE_WINNERS,
                        fromOfficeKey,
                        officeKey
                ));
            }
        }
    }

    private List<CandidateDefinition> parseCandidateDefinitions(JsonObject root) {
        List<CandidateDefinition> candidates = new ArrayList<>();
        if (!root.has("candidateDefinitions") || root.get("candidateDefinitions").isJsonNull()) {
            return candidates;
        }
        JsonObject definitions = asObject(root.get("candidateDefinitions"), "candidateDefinitions");

        for (Map.Entry<String, JsonElement> entry : definitions.entrySet()) {
            String candidateKey = entry.getKey();
            JsonObject candidateObj = asObject(entry.getValue(), "candidate '" + candidateKey + "'");

            String displayName = readString(candidateObj, "displayName");
            List<String> eligibleFor = readStringArray(candidateObj, "eligibleFor");

            candidates.add(new CandidateDefinition(candidateKey, displayName, eligibleFor));
        }
        return candidates;
    }

    private CountingMethod readMethod(String officeKey, JsonObject officeObj) {
        String methodRaw = readString(officeObj, "method");
        if (methodRaw == null) {
            return null;
        }
        try {
            return CountingMethod.valueOf(methodRaw);
        } catch (IllegalArgumentException e) {
            throw new ElectionDefinitionException(
                    "Office '" + officeKey + "' has unknown counting method '" + methodRaw + "'.");
        }
    }

    private JsonObject readRootObject(String configJson) {
        try {
            JsonElement element = JsonParser.parseString(configJson);
            if (element == null || !element.isJsonObject()) {
                throw new ElectionDefinitionException("Election definition must be a JSON object.");
            }
            return element.getAsJsonObject();
        } catch (JsonSyntaxException e) {
            throw new ElectionDefinitionException("Election definition is not valid JSON: " + e.getMessage(), e);
        }
    }

    private JsonObject asObject(JsonElement element, String what) {
        if (element == null || !element.isJsonObject()) {
            throw new ElectionDefinitionException("Expected '" + what + "' to be a JSON object.");
        }
        return element.getAsJsonObject();
    }

    private String readString(JsonObject obj, String key) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) {
            return null;
        }
        return obj.get(key).getAsString();
    }

    private int readInt(JsonObject obj, String key, int defaultValue) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) {
            return defaultValue;
        }
        try {
            return obj.get(key).getAsInt();
        } catch (NumberFormatException | IllegalStateException e) {
            throw new ElectionDefinitionException("Field '" + key + "' must be an integer.");
        }
    }

    private Integer readNullableInt(JsonObject obj, String key) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) {
            return null;
        }
        try {
            return obj.get(key).getAsInt();
        } catch (NumberFormatException | IllegalStateException e) {
            throw new ElectionDefinitionException("Field '" + key + "' must be an integer.");
        }
    }

    private boolean readBoolean(JsonObject obj, String key, boolean defaultValue) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) {
            return defaultValue;
        }
        try {
            return obj.get(key).getAsBoolean();
        } catch (IllegalStateException e) {
            throw new ElectionDefinitionException("Field '" + key + "' must be a boolean.");
        }
    }

    private List<String> readStringArray(JsonObject obj, String key) {
        List<String> out = new ArrayList<>();
        if (!obj.has(key) || obj.get(key).isJsonNull()) {
            return out;
        }
        JsonElement element = obj.get(key);
        if (!element.isJsonArray()) {
            throw new ElectionDefinitionException("Field '" + key + "' must be a JSON array.");
        }
        JsonArray array = element.getAsJsonArray();
        for (JsonElement item : array) {
            out.add(item.getAsString());
        }
        return out;
    }
}
