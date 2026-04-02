package com.modnmetl.modnvote.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Canonical in-memory representation of a committed ballot.
 *
 * In 2.x, ballots are the source of truth. Aggregate tallies are derived from them.
 */
public final class Ballot {

    private final long ballotId;
    private final long pollId;
    private final UUID voterUuid;
    private final Instant submittedAt;
    private final List<BallotPreference> preferences;

    public Ballot(long ballotId,
                  long pollId,
                  UUID voterUuid,
                  Instant submittedAt,
                  List<BallotPreference> preferences) {
        this.ballotId = ballotId;
        this.pollId = pollId;
        this.voterUuid = Objects.requireNonNull(voterUuid, "voterUuid");
        this.submittedAt = Objects.requireNonNull(submittedAt, "submittedAt");
        this.preferences = validateAndCopy(preferences);
    }

    private List<BallotPreference> validateAndCopy(List<BallotPreference> input) {
        Objects.requireNonNull(input, "preferences");
        if (input.isEmpty()) {
            throw new IllegalArgumentException("preferences must not be empty");
        }

        List<BallotPreference> copy = new ArrayList<>(input);
        copy.sort(Comparator.comparingInt(BallotPreference::rankPosition));

        for (int i = 0; i < copy.size(); i++) {
            BallotPreference pref = copy.get(i);
            int expectedRank = i + 1;
            if (pref.rankPosition() != expectedRank) {
                throw new IllegalArgumentException("preferences must use contiguous ranks starting at 1");
            }
        }

        long distinctOptionCount = copy.stream()
                .map(BallotPreference::optionId)
                .distinct()
                .count();

        if (distinctOptionCount != copy.size()) {
            throw new IllegalArgumentException("preferences must not contain duplicate option ids");
        }

        return List.copyOf(copy);
    }

    public long ballotId() {
        return ballotId;
    }

    public long pollId() {
        return pollId;
    }

    public UUID voterUuid() {
        return voterUuid;
    }

    public Instant submittedAt() {
        return submittedAt;
    }

    public List<BallotPreference> preferences() {
        return preferences;
    }
}