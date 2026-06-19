package com.modnmetl.modnvote.domain.election;

import java.util.Objects;

/**
 * Generic dependency rule linking two offices/contests.
 *
 * For {@link OfficeDependencyType#EXCLUDE_WINNERS}, the winners of
 * {@code fromOfficeKey} are excluded from {@code appliesToOfficeKey} at tally
 * time, which also means the source contest must be counted first.
 *
 * No office name is hardcoded.
 *
 * @param type               the dependency type
 * @param fromOfficeKey      the source office whose result drives the dependency
 * @param appliesToOfficeKey the office affected by the dependency
 */
public record OfficeDependencyRule(
        OfficeDependencyType type,
        String fromOfficeKey,
        String appliesToOfficeKey
) {
    public OfficeDependencyRule {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(fromOfficeKey, "fromOfficeKey");
        Objects.requireNonNull(appliesToOfficeKey, "appliesToOfficeKey");
    }
}
