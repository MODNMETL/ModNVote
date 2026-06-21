package com.modnmetl.modnvote.ui.render;

import com.modnmetl.modnvote.domain.election.CountingMethod;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bukkit-free tests for the linked-offices vote-screen method text/classification.
 *
 * <p>{@link LinkedOfficesVoteRenderer} imports Bukkit types and cannot be loaded in
 * the headless suite, so the ranked-vs-approval decisions it relies on were extracted
 * to {@link LinkedOfficesVoteMethodText} and are exercised here. These assertions lock
 * the live-server defect where STV Council contests rendered and behaved as approval.
 */
class LinkedOfficesVoteMethodTextTest {

    @Test
    void irvIsLabelledRanked() {
        assertEquals("Ranked (IRV)", LinkedOfficesVoteMethodText.methodLabel(CountingMethod.IRV));
    }

    @Test
    void stvIsLabelledRankedNotApproval() {
        assertEquals("Ranked (STV)", LinkedOfficesVoteMethodText.methodLabel(CountingMethod.STV));
    }

    @Test
    void approvalIsLabelledApproval() {
        assertEquals("Approval", LinkedOfficesVoteMethodText.methodLabel(CountingMethod.APPROVAL_TOP_N));
    }

    @Test
    void nullMethodFallsBackToUnknown() {
        assertEquals("Unknown", LinkedOfficesVoteMethodText.methodLabel(null));
    }

    @Test
    void stvIsClassifiedAsRanked() {
        assertTrue(LinkedOfficesVoteMethodText.isRankedMethod(CountingMethod.STV),
                "STV must render as a ranked contest, not approval");
    }

    @Test
    void irvIsClassifiedAsRanked() {
        assertTrue(LinkedOfficesVoteMethodText.isRankedMethod(CountingMethod.IRV));
    }

    @Test
    void approvalIsNotClassifiedAsRanked() {
        assertFalse(LinkedOfficesVoteMethodText.isRankedMethod(CountingMethod.APPROVAL_TOP_N));
    }

    @Test
    void nullMethodIsNotClassifiedAsRanked() {
        assertFalse(LinkedOfficesVoteMethodText.isRankedMethod(null));
    }
}
