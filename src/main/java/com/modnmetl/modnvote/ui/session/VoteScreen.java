package com.modnmetl.modnvote.ui.session;

/**
 * The current screen/state of an in-progress vote session.
 *
 * This is intentionally minimal for the first GUI/session phase.
 * Additional states can be introduced later if the interaction flow expands.
 */
public enum VoteScreen {
    SELECTION,
    CONFIRMATION
}