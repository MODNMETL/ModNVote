package com.modnmetl.modnvote.ui.render;

/**
 * Distinguishes which UI/session flow owns a managed ModNVote inventory.
 *
 * This prevents different poll-type listeners/renderers from accidentally
 * treating each other's inventories as their own.
 */
public enum VoteUiFlow {
    RANKED,
    YES_NO
}