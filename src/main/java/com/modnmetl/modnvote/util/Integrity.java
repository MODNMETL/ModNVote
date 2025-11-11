package com.modnmetl.modnvote.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class Integrity {
    private Integrity() {}

    public static String canonicalString(int roundId, int yes, int no, List<java.util.UUID> uuids) {
        List<String> s = new ArrayList<>(uuids.size());
        for (java.util.UUID u : uuids) s.add(u.toString());
        Collections.sort(s);
        String joined = String.join(",", s);
        return "modnvote|r=" + roundId + "|yes=" + yes + "|no=" + no + "|uuids=" + joined;
    }

    public static String hmacSha256Hex(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] out = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(out.length * 2);
            for (byte b : out) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("HMAC error: " + e.getMessage(), e);
        }
    }
}
