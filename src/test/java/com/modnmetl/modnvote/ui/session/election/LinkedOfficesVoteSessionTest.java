package com.modnmetl.modnvote.ui.session.election;

import com.modnmetl.modnvote.api.PollStatus;
import com.modnmetl.modnvote.api.PollType;
import com.modnmetl.modnvote.domain.Poll;
import com.modnmetl.modnvote.domain.election.CandidateDefinition;
import com.modnmetl.modnvote.domain.election.ContestDefinition;
import com.modnmetl.modnvote.domain.election.CountingMethod;
import com.modnmetl.modnvote.domain.election.ElectionDefinition;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bukkit-free tests for linked-offices vote session navigation and the in-memory
 * session manager. The command/listener layers are kept thin and delegate all
 * state to these classes, so this is where the flow logic is verified.
 */
class LinkedOfficesVoteSessionTest {

    private static final String MAYOR = "mayor";
    private static final String COUNCIL = "council";

    private static ElectionDefinition definition() {
        ContestDefinition mayor = new ContestDefinition(
                MAYOR, "Mayor", CountingMethod.IRV, 1, null, false, List.of("alice", "bob"));
        ContestDefinition council = new ContestDefinition(
                COUNCIL, "Council", CountingMethod.APPROVAL_TOP_N, 2, 2, false, List.of("dave", "erin"));
        List<CandidateDefinition> candidates = List.of(
                new CandidateDefinition("alice", "Alice", List.of(MAYOR)),
                new CandidateDefinition("bob", "Bob", List.of(MAYOR)),
                new CandidateDefinition("dave", "Dave", List.of(COUNCIL)),
                new CandidateDefinition("erin", "Erin", List.of(COUNCIL)));
        return new ElectionDefinition(
                ElectionDefinition.LINKED_OFFICES_MODEL, List.of(mayor, council), candidates, List.of());
    }

    private static Poll linkedPoll(long pollId) {
        return new Poll(pollId, "linked-" + pollId, "Linked Poll", "Desc",
                PollType.LINKED_OFFICES, PollStatus.OPEN, null, null,
                1, 2, false, true, "secret-" + pollId);
    }

    @Test
    void sessionNavigatesAcrossScreens() {
        LinkedOfficesVoteSession session =
                new LinkedOfficesVoteSession(UUID.randomUUID(), linkedPoll(7), definition());

        assertTrue(session.isInOverviewScreen());

        session.openOffice(MAYOR);
        assertTrue(session.isInOfficeScreen());
        assertEquals(MAYOR, session.currentOfficeKey());

        session.returnToOverview();
        assertTrue(session.isInOverviewScreen());
        assertEquals(null, session.currentOfficeKey());

        session.moveToReview();
        assertTrue(session.isInReviewScreen());
    }

    @Test
    void managerKeepsOneSessionPerPlayerAndSupportsRemoval() {
        LinkedOfficesVoteSessionManager manager = new LinkedOfficesVoteSessionManager(Duration.ofMinutes(10));
        UUID player = UUID.randomUUID();

        LinkedOfficesVoteSession first = manager.createOrReplaceSession(player, linkedPoll(3), definition());
        assertSame(first, manager.getRequiredSession(player));
        assertTrue(manager.findSession(player, 3).isPresent());
        assertFalse(manager.findSession(player, 999).isPresent());

        LinkedOfficesVoteSession second = manager.createOrReplaceSession(player, linkedPoll(5), definition());
        assertSame(second, manager.getRequiredSession(player));

        assertTrue(manager.removeSession(player, 5));
        assertFalse(manager.findSession(player).isPresent());
    }
}
