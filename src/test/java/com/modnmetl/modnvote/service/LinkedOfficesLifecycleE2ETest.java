package com.modnmetl.modnvote.service;

import com.modnmetl.modnvote.api.PollStatus;
import com.modnmetl.modnvote.api.PollType;
import com.modnmetl.modnvote.domain.Poll;
import com.modnmetl.modnvote.domain.election.ElectionDefinition;
import com.modnmetl.modnvote.domain.election.ElectionDefinitionSerializer;
import com.modnmetl.modnvote.domain.election.execution.ApprovalContestVote;
import com.modnmetl.modnvote.domain.election.execution.ContestVote;
import com.modnmetl.modnvote.domain.election.execution.LinkedElectionBallot;
import com.modnmetl.modnvote.domain.election.execution.RankedContestVote;
import com.modnmetl.modnvote.domain.election.results.LinkedElectionResult;
import com.modnmetl.modnvote.presentation.LinkedElectionResultDisplayFormatter;
import com.modnmetl.modnvote.presentation.LinkedElectionWitnessPayloadFormatter;
import com.modnmetl.modnvote.storage.DatabaseManager;
import com.modnmetl.modnvote.storage.PollDao;
import com.modnmetl.modnvote.storage.SchemaInitializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static com.modnmetl.modnvote.service.LinkedStorageTestFixtures.ALICE;
import static com.modnmetl.modnvote.service.LinkedStorageTestFixtures.BOB;
import static com.modnmetl.modnvote.service.LinkedStorageTestFixtures.CAROL;
import static com.modnmetl.modnvote.service.LinkedStorageTestFixtures.COUNCIL;
import static com.modnmetl.modnvote.service.LinkedStorageTestFixtures.DAVE;
import static com.modnmetl.modnvote.service.LinkedStorageTestFixtures.ERIN;
import static com.modnmetl.modnvote.service.LinkedStorageTestFixtures.FRANK;
import static com.modnmetl.modnvote.service.LinkedStorageTestFixtures.GRACE;
import static com.modnmetl.modnvote.service.LinkedStorageTestFixtures.MAYOR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end, Bukkit-free regression test for the full LINKED_OFFICES lifecycle:
 * create → ready → open → submit (multiple voters) → duplicate rejected → close →
 * compute result → verify proof phrase → verify integrity → build witness payload,
 * then assert no voter identity leaks across the result, proof, integrity and
 * witness outputs.
 *
 * <p>The command layer ({@code PollCommand}) and {@code WitnessPublicationService}
 * require Bukkit (Paper API is {@code compileOnly}); this test drives the exact
 * Bukkit-free collaborators those thin layers delegate to —
 * {@link PollService#readyPoll}/{@link PollService#openPoll}/{@link PollService#closePoll},
 * {@link LinkedOfficesSubmissionService}, {@link ResultService#getLinkedElectionResult},
 * {@link LinkedOfficesProofVerifier}, {@link LinkedOfficesIntegrityVerifier} and
 * {@link LinkedElectionWitnessPayloadFormatter} — so the whole release path is
 * regression-locked here.
 */
class LinkedOfficesLifecycleE2ETest {

    private record Ctx(DatabaseManager dbm, LinkedOfficesSubmissionService submission,
                       PollService pollService, ResultService resultService,
                       LinkedOfficesProofVerifier proofVerifier,
                       LinkedOfficesIntegrityVerifier integrityVerifier,
                       ElectionDefinition definition, String configJson) {
    }

    private static Ctx setup(Path tempDir) throws Exception {
        DatabaseManager dbm = new DatabaseManager(tempDir.resolve("e2e.db"));
        new SchemaInitializer(dbm).initialize();
        ElectionDefinition def = LinkedStorageTestFixtures.mayorCouncil();
        String configJson = new ElectionDefinitionSerializer().serialize(def);
        Logger logger = Logger.getLogger("LinkedOfficesLifecycleE2ETest");
        return new Ctx(dbm, new LinkedOfficesSubmissionService(dbm),
                new PollService(dbm, logger), new ResultService(dbm, logger),
                new LinkedOfficesProofVerifier(dbm), new LinkedOfficesIntegrityVerifier(dbm),
                def, configJson);
    }

    private static long createDraft(Ctx ctx) throws Exception {
        PollDao pollDao = new PollDao(ctx.dbm);
        Poll draft = new Poll(0L, "linked-e2e", "Town Election", "Annual linked election",
                PollType.LINKED_OFFICES, PollStatus.DRAFT, null, null,
                1, 3, false, true, "participation-secret-e2e", ctx.configJson);
        try (Connection connection = ctx.dbm.getConnection()) {
            return pollDao.insertPoll(connection, draft, "admin", "DEFAULT", ctx.configJson);
        }
    }

    private static String submit(Ctx ctx, long pollId, String identity,
                                 List<String> mayorRanking, List<String> councilApprovals) throws Exception {
        LinkedElectionBallot ballot = new LinkedElectionBallot(ctx.definition, List.<ContestVote>of(
                new RankedContestVote(MAYOR, mayorRanking),
                new ApprovalContestVote(COUNCIL, councilApprovals)));
        return ctx.submission.submitLinkedOfficesBallot(pollId, identity, "JAVA_GUI_LINKED_OFFICES",
                ballot, null, null).proofPhrase();
    }

    @Test
    void fullLinkedOfficesLifecycleCreateToWitnessWithoutIdentityLeak(@TempDir Path tempDir) throws Exception {
        Ctx ctx = setup(tempDir);

        // 1-4. Create, ready, open.
        long pollId = createDraft(ctx);
        ctx.pollService.readyPoll(pollId, "admin");
        assertEquals(PollStatus.READY, new PollDao(ctx.dbm).findPollById(pollId).status());
        ctx.pollService.openPoll(pollId, "admin");
        assertEquals(PollStatus.OPEN, new PollDao(ctx.dbm).findPollById(pollId).status());

        // 5. Submit multiple linked-office ballots; keep one voter's proof phrase.
        String voterIdentity = "voter-uuid-1111-secret";
        String voterIp = "ip-hash-aaaa";
        String firstVoterProof = submit(ctx, pollId, voterIdentity, List.of(ALICE, BOB), List.of(ALICE, DAVE, GRACE));
        submit(ctx, pollId, "voter-2", List.of(ALICE, CAROL), List.of(DAVE, ERIN, GRACE));
        submit(ctx, pollId, "voter-3", List.of(ALICE), List.of(DAVE, GRACE, FRANK));
        submit(ctx, pollId, "voter-4", List.of(BOB, ALICE), List.of(ERIN, GRACE, DAVE));
        submit(ctx, pollId, "voter-5", List.of(CAROL), List.of(DAVE, GRACE, ERIN));

        // 6. Duplicate voter is rejected and writes no second ballot.
        assertThrows(PollServiceException.class, () ->
                submit(ctx, pollId, voterIdentity, List.of(BOB), List.of(DAVE, ERIN)));

        // 7. Close.
        ctx.pollService.closePoll(pollId, "admin");
        Poll closed = new PollDao(ctx.dbm).findPollById(pollId);
        assertEquals(PollStatus.CLOSED, closed.status());

        // 8. Compute result.
        LinkedElectionResult result = ctx.resultService.getLinkedElectionResult(pollId);
        assertEquals(5, result.countedBallots());
        assertTrue(result.complete(), () -> "issues: " + result.issues());
        assertEquals(List.of(ALICE), result.findContest(MAYOR).orElseThrow().winners());

        // 9. Verify the proof phrase reveals that voter's anonymous content.
        com.modnmetl.modnvote.service.LinkedOfficeBallotProofVerificationResult proof =
                ctx.proofVerifier.verify(closed, firstVoterProof);
        assertTrue(proof.verified(), () -> "proof failure: " + proof.failureReason());
        assertEquals(List.of(ALICE, BOB), proof.offices().get(0).orderedCandidateKeys());

        // 10. Verify integrity.
        IntegrityVerificationService.IntegrityVerificationResult integrity =
                ctx.integrityVerifier.verify(closed);
        assertTrue(integrity.overallValid(), () -> "integrity issues: " + integrity.issues());

        // 11. Build witness payload.
        List<LinkedElectionWitnessPayloadFormatter.WitnessField> witnessFields =
                LinkedElectionWitnessPayloadFormatter.buildFields(closed, result, closed.closesAt(), 1024);

        // 12. No identity material across result / proof / integrity / witness strings.
        List<String> surfaces = new ArrayList<>();
        surfaces.addAll(LinkedElectionResultDisplayFormatter.formatInGame(result));
        surfaces.add(proof.toString());
        surfaces.add(String.valueOf(integrity.issues()));
        for (LinkedElectionWitnessPayloadFormatter.WitnessField field : witnessFields) {
            surfaces.add(field.name() + "=" + field.value());
        }
        String combined = String.join("\n", surfaces);

        assertFalse(combined.contains(voterIdentity), () -> "leaked voter identity: " + combined);
        assertFalse(combined.contains(voterIp), () -> "leaked ip hash: " + combined);
        String lower = combined.toLowerCase();
        for (String forbidden : List.of("uuid", "floodgate", "participation", "receipt", "identity")) {
            assertFalse(lower.contains(forbidden), () -> "leaked '" + forbidden + "': " + combined);
        }
        // The voter's proof phrase must never appear in published or integrity surfaces.
        String witnessAndIntegrity = String.valueOf(integrity.issues()) + "\n"
                + witnessFields.stream().map(f -> f.name() + "=" + f.value()).reduce("", (a, b) -> a + "\n" + b);
        assertFalse(witnessAndIntegrity.toLowerCase().contains(firstVoterProof.toLowerCase()),
                () -> "leaked proof phrase: " + witnessAndIntegrity);
    }
}
