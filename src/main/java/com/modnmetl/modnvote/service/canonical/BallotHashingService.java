package com.modnmetl.modnvote.service.canonical;

import com.modnmetl.modnvote.domain.Poll;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Single shared source of truth for anonymous-ballot hashing, proof, and
 * commitment derivation.
 *
 * <p>Both the single-contest submission/verification path ({@code BallotService})
 * and the linked-offices storage path ({@code LinkedBallotStorageService}) route
 * through this helper, so the two layers can never silently drift apart on how a
 * ballot hash, proof hash, or commitment hash is derived.
 *
 * <p>The byte-level semantics are fixed and must not change without invalidating
 * every previously stored hash:
 * <ul>
 *   <li>simple hashes use SHA-256 over UTF-8 bytes, hex-encoded;</li>
 *   <li>the participation token hash uses HmacSHA256 keyed by the poll's
 *       participation secret over {@code pollId + "\n" + identityKey};</li>
 *   <li>the receipt / proof / commitment hashes use the fixed string prefixes
 *       {@code "participation_receipt\n"}, {@code "ballot_proof\n"}, and
 *       {@code "ballot_commitment\n"} with the same field ordering and newlines.</li>
 * </ul>
 *
 * <p>This component is stateless; all methods are static.
 */
public final class BallotHashingService {

    private static final String PARTICIPATION_TOKEN_ALGORITHM = "HmacSHA256";

    private BallotHashingService() {
    }

    /**
     * @return the lowercase hex SHA-256 of the UTF-8 bytes of {@code input}
     */
    public static String sha256(String input) {
        Objects.requireNonNull(input, "input");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash ballot payload", e);
        }
    }

    /**
     * Derives the one-way participation token hash that enforces one vote per
     * participant without storing identity alongside vote content. Keyed by the
     * poll's participation secret (HmacSHA256).
     */
    public static String deriveParticipationTokenHash(Poll poll, String identityKey) {
        Objects.requireNonNull(poll, "poll");
        Objects.requireNonNull(identityKey, "identityKey");
        try {
            String input = poll.pollId() + "\n" + identityKey;
            Mac mac = Mac.getInstance(PARTICIPATION_TOKEN_ALGORITHM);
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    poll.participationSecret().getBytes(StandardCharsets.UTF_8),
                    PARTICIPATION_TOKEN_ALGORITHM
            );
            mac.init(secretKeySpec);
            byte[] bytes = mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to derive participation token hash", e);
        }
    }

    /**
     * @return the one-way participation receipt hash (proves inclusion without
     * revealing vote content)
     */
    public static String buildParticipationReceiptHash(long pollId, String participationReceipt) {
        Objects.requireNonNull(participationReceipt, "participationReceipt");
        return sha256("participation_receipt\n" + pollId + "\n" + participationReceipt);
    }

    /**
     * @return the ballot proof lookup hash (one-way; the proof phrase is the
     * bearer token shown to the voter)
     */
    public static String buildBallotProofHash(long pollId, String ballotProofPhrase) {
        Objects.requireNonNull(ballotProofPhrase, "ballotProofPhrase");
        return sha256("ballot_proof\n" + pollId + "\n" + ballotProofPhrase);
    }

    /**
     * @return the ballot commitment hash binding the proof phrase to the exact
     * canonical ballot payload (enables identity-free exact-ballot verification)
     */
    public static String buildBallotCommitmentHash(String ballotProofPhrase, String canonicalPayload) {
        Objects.requireNonNull(ballotProofPhrase, "ballotProofPhrase");
        Objects.requireNonNull(canonicalPayload, "canonicalPayload");
        return sha256("ballot_commitment\n" + ballotProofPhrase + "\n" + canonicalPayload);
    }
}
