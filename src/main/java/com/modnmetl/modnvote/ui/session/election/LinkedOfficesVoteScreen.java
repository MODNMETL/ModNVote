package com.modnmetl.modnvote.ui.session.election;

/**
 * The current screen of an in-progress linked-offices vote session.
 *
 * <ul>
 *   <li>{@link #OVERVIEW} — list of offices with completion status and a submit guard.</li>
 *   <li>{@link #OFFICE} — ranking/approval screen for one selected office.</li>
 *   <li>{@link #REVIEW} — per-office summary with the final submit action.</li>
 * </ul>
 */
public enum LinkedOfficesVoteScreen {
    OVERVIEW,
    OFFICE,
    REVIEW
}
