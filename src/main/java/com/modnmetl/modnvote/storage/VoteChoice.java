package com.modnmetl.modnvote.storage;

public enum VoteChoice {
    YES, NO;
    public static VoteChoice fromString(String s) {
        return s == null ? null : (s.equalsIgnoreCase("yes") ? YES : s.equalsIgnoreCase("no") ? NO : null);
    }
}
