package com.modnmetl.modnvote.service.canonical;

import com.modnmetl.modnvote.api.PollStatus;
import com.modnmetl.modnvote.api.PollType;
import com.modnmetl.modnvote.domain.Poll;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Focused tests for {@link BallotHashingService}.
 *
 * <p>These lock the byte-level semantics that {@code BallotService} and
 * {@code LinkedBallotStorageService} both depend on. The expected values are
 * recomputed here independently (SHA-256 / HmacSHA256 over the exact prefixed
 * strings) so the test fails if the helper's encoding, prefixes, or field
 * ordering ever change.
 */
class BallotHashingServiceTest {

    private static Poll poll(long pollId, String participationSecret) {
        return new Poll(
                pollId,
                "slug-" + pollId,
                "Title",
                "Description",
                PollType.RANKED_SINGLE_WINNER,
                PollStatus.OPEN,
                null,
                null,
                3,
                1,
                false,
                true,
                participationSecret
        );
    }

    // --- 1-3: participation token hash ---------------------------------------

    @Test
    void participationTokenHashIsDeterministicForSamePollAndIdentity() {
        Poll p = poll(42L, "secret-a");
        assertEquals(
                BallotHashingService.deriveParticipationTokenHash(p, "player-1"),
                BallotHashingService.deriveParticipationTokenHash(p, "player-1"));
    }

    @Test
    void differentIdentityGivesDifferentParticipationTokenHash() {
        Poll p = poll(42L, "secret-a");
        assertNotEquals(
                BallotHashingService.deriveParticipationTokenHash(p, "player-1"),
                BallotHashingService.deriveParticipationTokenHash(p, "player-2"));
    }

    @Test
    void differentParticipationSecretGivesDifferentParticipationTokenHash() {
        assertNotEquals(
                BallotHashingService.deriveParticipationTokenHash(poll(42L, "secret-a"), "player-1"),
                BallotHashingService.deriveParticipationTokenHash(poll(42L, "secret-b"), "player-1"));
    }

    @Test
    void participationTokenHashMatchesIndependentHmacSha256() throws Exception {
        Poll p = poll(7L, "the-secret");
        String expected = hmacSha256("the-secret", 7L + "\n" + "identity-key");
        assertEquals(expected, BallotHashingService.deriveParticipationTokenHash(p, "identity-key"));
    }

    // --- 4-6: proof + commitment hashes --------------------------------------

    @Test
    void ballotProofHashIsDeterministicAndMatchesLiteralSemantics() {
        assertEquals(
                BallotHashingService.buildBallotProofHash(7L, "river-stone-maple-fox"),
                BallotHashingService.buildBallotProofHash(7L, "river-stone-maple-fox"));
        assertEquals(
                sha256("ballot_proof\n7\nriver-stone-maple-fox"),
                BallotHashingService.buildBallotProofHash(7L, "river-stone-maple-fox"));
    }

    @Test
    void ballotCommitmentHashIsDeterministicAndMatchesLiteralSemantics() {
        String phrase = "river-stone-maple-fox";
        String payload = "poll_id=7\nordered_option_ids=3,1,2";
        assertEquals(
                BallotHashingService.buildBallotCommitmentHash(phrase, payload),
                BallotHashingService.buildBallotCommitmentHash(phrase, payload));
        assertEquals(
                sha256("ballot_commitment\n" + phrase + "\n" + payload),
                BallotHashingService.buildBallotCommitmentHash(phrase, payload));
    }

    @Test
    void changingCanonicalPayloadChangesCommitmentHash() {
        String phrase = "river-stone-maple-fox";
        assertNotEquals(
                BallotHashingService.buildBallotCommitmentHash(phrase, "payload-A"),
                BallotHashingService.buildBallotCommitmentHash(phrase, "payload-B"));
    }

    // --- 7: participation receipt hash ---------------------------------------

    @Test
    void participationReceiptHashIsDeterministicAndMatchesLiteralSemantics() {
        assertEquals(
                BallotHashingService.buildParticipationReceiptHash(7L, "receipt-xyz"),
                BallotHashingService.buildParticipationReceiptHash(7L, "receipt-xyz"));
        assertEquals(
                sha256("participation_receipt\n7\nreceipt-xyz"),
                BallotHashingService.buildParticipationReceiptHash(7L, "receipt-xyz"));
    }

    // --- 8: sha256 surface ----------------------------------------------------

    @Test
    void sha256MatchesIndependentDigest() {
        assertEquals(sha256("hello"), BallotHashingService.sha256("hello"));
        assertNotEquals(BallotHashingService.sha256("hello"), BallotHashingService.sha256("world"));
    }

    // --- independent reference implementations --------------------------------

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hmacSha256(String key, String input) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(input.getBytes(StandardCharsets.UTF_8)));
    }
}
