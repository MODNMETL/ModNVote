package com.modnmetl.modnvote.ui.builder;

import com.modnmetl.modnvote.domain.Poll;
import com.modnmetl.modnvote.domain.PollOption;

import java.util.List;
import java.util.UUID;

/**
 * Session state holder for the Admin Poll Builder flow.
 *
 * This class is intentionally lightweight and does NOT perform
 * any persistence or service-layer mutations.
 */
public class PollBuilderSession {

    private final UUID playerId;
    private final long pollId;
    private final Poll pollSnapshot;
    private final List<PollOption> optionsSnapshot;

    public PollBuilderSession(UUID playerId,
                              long pollId,
                              Poll pollSnapshot,
                              List<PollOption> optionsSnapshot) {
        this.playerId = playerId;
        this.pollId = pollId;
        this.pollSnapshot = pollSnapshot;
        this.optionsSnapshot = optionsSnapshot;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public long getPollId() {
        return pollId;
    }

    public Poll getPollSnapshot() {
        return pollSnapshot;
    }

    public List<PollOption> getOptionsSnapshot() {
        return optionsSnapshot;
    }
}
