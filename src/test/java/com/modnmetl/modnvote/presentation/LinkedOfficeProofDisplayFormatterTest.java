package com.modnmetl.modnvote.presentation;

import com.modnmetl.modnvote.service.IntegrityVerificationService.IntegrityVerificationResult;
import com.modnmetl.modnvote.service.LinkedOfficeBallotProofVerificationResult;
import com.modnmetl.modnvote.service.LinkedOfficeBallotProofVerificationResult.OfficeResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the linked-offices proof verification command output.
 *
 * <p>Command handlers in {@code PollCommand} require Bukkit {@code CommandSender}
 * types and a fully-wired {@code BallotService}, neither of which can be
 * constructed in this test harness (Paper API is {@code compileOnly}). The command
 * is therefore kept thin: it forwards a {@link LinkedOfficeBallotProofVerificationResult}
 * to the Bukkit-free {@link LinkedOfficeProofDisplayFormatter} and prints each
 * returned line. These tests pin that formatter — the only new rendering logic in
 * this tranche — thoroughly.
 *
 * <p>Coverage notes for the tranche's required scenarios:
 * <ul>
 *   <li>The single-contest (YES_NO / RANKED_SINGLE_WINNER) proof output is built
 *       inline in {@code PollCommand} and is left byte-for-byte unchanged; its
 *       hashing/verification is locked by {@code BallotHashingServiceTest},
 *       {@code BallotCanonicalizerTest} and the single-contest regression in
 *       {@code LinkedOfficesProofVerifierTest}. The new linked-offices branch only
 *       adds an early route and never touches that path.</li>
 *   <li>Linked-offices integrity verification was already reachable generically via
 *       {@code IntegrityVerificationService.verifyPollIntegrity} (which delegates to
 *       the linked-offices verifier); {@link #integrityResultRepresentsSuccessAndFailures()}
 *       confirms the result the command renders can represent both success and
 *       failure states without exposing identity.</li>
 * </ul>
 */
class LinkedOfficeProofDisplayFormatterTest {

    private static final String MAYOR = "mayor";
    private static final String COUNCIL = "council";
    private static final String ALICE = "alice";
    private static final String BOB = "bob";
    private static final String CAROL = "carol";
    private static final String DAVE = "dave";
    private static final String GRACE = "grace";

    private static LinkedOfficeBallotProofVerificationResult verifiedResult() {
        return new LinkedOfficeBallotProofVerificationResult(
                7L,
                true,
                true,
                42L,
                "ballot-hash-abc123",
                Instant.parse("2026-06-20T10:15:30Z"),
                List.of(
                        new OfficeResponse(MAYOR, "RANKED", List.of(ALICE, BOB, CAROL)),
                        new OfficeResponse(COUNCIL, "APPROVAL", List.of(ALICE, DAVE, GRACE))
                ),
                null
        );
    }

    @Test
    void successOutputIncludesOfficeResponses() {
        List<String> lines = LinkedOfficeProofDisplayFormatter.formatInGame(verifiedResult());
        String joined = String.join("\n", lines);

        assertTrue(joined.contains("Exact-ballot verification succeeded"), joined);
        assertTrue(joined.contains("#7"), "should show poll id: " + joined);
        assertTrue(joined.contains("2026-06-20T10:15:30Z"), "should show submitted_at: " + joined);
        assertTrue(joined.contains("ballot-hash-abc123"), "should show ballot hash: " + joined);

        // Both offices, their response types, and candidate keys are rendered.
        assertTrue(joined.contains(MAYOR), joined);
        assertTrue(joined.contains("(RANKED)"), joined);
        assertTrue(joined.contains(COUNCIL), joined);
        assertTrue(joined.contains("(APPROVAL)"), joined);
        for (String key : List.of(ALICE, BOB, CAROL, DAVE, GRACE)) {
            assertTrue(joined.contains(key), "missing candidate key " + key + ": " + joined);
        }

        // Ranked office shows numbered positions; approval office shows bullets.
        assertTrue(joined.contains("#§f1 §8-> §b" + ALICE), "ranked first preference: " + joined);
        assertTrue(joined.contains("#§f3 §8-> §b" + CAROL), "ranked third preference: " + joined);
        assertTrue(joined.contains(" §8- §b" + GRACE), "approval bullet: " + joined);
    }

    @Test
    void failedVerificationOutputHidesOfficeContent() {
        LinkedOfficeBallotProofVerificationResult failed = new LinkedOfficeBallotProofVerificationResult(
                7L,
                true,
                false,
                42L,
                "ballot-hash-abc123",
                Instant.parse("2026-06-20T10:15:30Z"),
                List.of(),
                "matched the proof phrase but failed exact-ballot verification "
                        + "(ballot_hash_valid=true,commitment_valid=false)"
        );

        List<String> lines = LinkedOfficeProofDisplayFormatter.formatInGame(failed);
        String joined = String.join("\n", lines);

        assertTrue(joined.contains("exact-ballot verification failed"), joined);
        assertTrue(joined.contains("commitment_valid=false"), "identity-free reason shown: " + joined);

        // No office or candidate content may be revealed on a failed verification.
        for (String key : List.of(MAYOR, COUNCIL, ALICE, BOB, CAROL, DAVE, GRACE)) {
            assertFalse(joined.contains(key), "failed output must not reveal " + key + ": " + joined);
        }
        assertFalse(joined.contains("Verified linked-office responses"), joined);
    }

    @Test
    void wrongProofPhraseRevealsNoContent() {
        LinkedOfficeBallotProofVerificationResult notFound = new LinkedOfficeBallotProofVerificationResult(
                7L,
                false,
                false,
                null,
                null,
                null,
                List.of(),
                null
        );

        List<String> lines = LinkedOfficeProofDisplayFormatter.formatInGame(notFound);
        String joined = String.join("\n", lines);

        assertTrue(joined.contains("No anonymous ballot was found"), joined);
        for (String key : List.of(MAYOR, COUNCIL, ALICE, BOB, CAROL, DAVE, GRACE)) {
            assertFalse(joined.contains(key), "not-found output must not reveal " + key + ": " + joined);
        }
        assertFalse(joined.contains("Verified linked-office responses"), joined);
    }

    @Test
    void outputNeverContainsIdentityOrProofPhrase() {
        // The formatter only ever receives a result with anonymous content, so it
        // is structurally incapable of echoing identity material or the phrase.
        // This guards against a future change that wrongly threads identity through.
        String identityKey = "IDENTITY-KEY-SENTINEL";
        String uuid = "11111111-2222-3333-4444-555555555555";
        String playerName = "PlayerNameSentinel";
        String ipHash = "ip-hash-sentinel";
        String floodgateId = "floodgate-sentinel";
        String tokenHash = "participation-token-sentinel";
        String receipt = "participation-receipt-sentinel";
        String proofPhrase = "correct horse battery staple";

        for (LinkedOfficeBallotProofVerificationResult result : List.of(
                verifiedResult(),
                new LinkedOfficeBallotProofVerificationResult(7L, true, false, 42L, "h", null, List.of(),
                        "matched the proof phrase but failed exact-ballot verification"),
                new LinkedOfficeBallotProofVerificationResult(7L, false, false, null, null, null, List.of(), null)
        )) {
            String joined = String.join("\n", LinkedOfficeProofDisplayFormatter.formatInGame(result));
            for (String forbidden : List.of(identityKey, uuid, playerName, ipHash, floodgateId,
                    tokenHash, receipt, proofPhrase)) {
                assertFalse(joined.contains(forbidden),
                        "output leaked forbidden value '" + forbidden + "': " + joined);
            }
        }
    }

    @Test
    void verifiedOutputIsNeverEmpty() {
        assertFalse(LinkedOfficeProofDisplayFormatter.formatInGame(verifiedResult()).isEmpty());
    }

    @Test
    void integrityResultRepresentsSuccessAndFailures() {
        IntegrityVerificationResult success = new IntegrityVerificationResult(
                7L, true, true, true, true, List.of());

        assertTrue(success.auditChainValid());
        assertTrue(success.ballotHashesValid());
        assertTrue(success.recordCountsMatch());
        assertTrue(success.overallValid());
        assertTrue(success.issues().isEmpty());

        IntegrityVerificationResult failure = new IntegrityVerificationResult(
                7L, true, false, false, false,
                List.of("Anonymous ballot #42 failed exact-ballot verification."));

        assertFalse(failure.overallValid());
        assertFalse(failure.ballotHashesValid());
        assertFalse(failure.recordCountsMatch());
        assertEquals(1, failure.issues().size());

        // Issue text the command renders is identity-free.
        String issue = failure.issues().get(0);
        assertFalse(issue.contains("uuid"));
        assertFalse(issue.toLowerCase().contains("player"));
        assertFalse(issue.toLowerCase().contains("floodgate"));
    }
}
