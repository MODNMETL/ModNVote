package com.modnmetl.modnvote.service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Result of bearer-token proof verification for a LINKED_OFFICES anonymous ballot.
 *
 * <p>This is the linked-offices analogue of
 * {@link BallotService.BallotProofVerificationResult}. The single-contest result
 * exposes a flat list of ordered option ids, which cannot represent a
 * multi-contest ballot, so a dedicated result is used while the single-contest
 * shape is left unchanged.
 *
 * <p><strong>Privacy:</strong> proof verification is bearer-token based — whoever
 * holds the proof phrase may see the anonymous ballot content. This result
 * therefore exposes anonymous vote content only. It carries no player UUID, name,
 * IP hash, Floodgate id, participation token hash, participation receipt, or
 * participation row id, and it is never produced by joining
 * {@code participation_records} to anonymous content. {@code anonymousBallotId} and
 * {@code ballotHash} are anonymous content anchors, mirroring what the
 * single-contest result already exposes.
 *
 * @param pollId           the poll the proof was checked against
 * @param ballotFound      whether a stored anonymous ballot matched the proof hash
 * @param verified         whether the reconstructed ballot's hash and commitment
 *                         both still match the stored values
 * @param anonymousBallotId the matched anonymous ballot id, or {@code null} if none
 * @param ballotHash       the stored anonymous ballot hash, or {@code null} if none
 * @param submittedAt      the ballot submission timestamp, or {@code null} if none
 * @param offices          per-office anonymous response content, populated only on
 *                         success (empty otherwise)
 * @param failureReason    an admin/user-safe, identity-free explanation when
 *                         {@code verified} is false, otherwise {@code null}
 */
public record LinkedOfficeBallotProofVerificationResult(
        long pollId,
        boolean ballotFound,
        boolean verified,
        Long anonymousBallotId,
        String ballotHash,
        Instant submittedAt,
        List<OfficeResponse> offices,
        String failureReason
) {

    public LinkedOfficeBallotProofVerificationResult {
        offices = offices == null ? List.of() : List.copyOf(offices);
    }

    /**
     * One office's anonymous response content.
     *
     * @param officeKey            the contest/office key
     * @param responseType         {@code RANKED} or {@code APPROVAL}
     * @param orderedCandidateKeys the reconstructed candidate keys in canonical
     *                             order (ranked: by rank; approval: contest order)
     */
    public record OfficeResponse(
            String officeKey,
            String responseType,
            List<String> orderedCandidateKeys
    ) {
        public OfficeResponse {
            Objects.requireNonNull(officeKey, "officeKey");
            Objects.requireNonNull(responseType, "responseType");
            orderedCandidateKeys = orderedCandidateKeys == null ? List.of() : List.copyOf(orderedCandidateKeys);
        }
    }
}
