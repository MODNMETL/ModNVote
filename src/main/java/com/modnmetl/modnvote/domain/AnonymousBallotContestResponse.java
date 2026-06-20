package com.modnmetl.modnvote.domain;

import java.util.Objects;

/**
 * One stored row of anonymous, multi-contest vote content for a single
 * {@code anonymous_ballots} row (linked-offices ballots).
 *
 * <p>This is anonymous vote content only. It is linked solely to an
 * {@code anonymousBallotId} and carries no player UUID, name, IP, Floodgate id,
 * participation token, or receipt — exactly like {@link BallotPreference}, it
 * lives entirely on the content side of the privacy split.
 *
 * <p>Encoding (canonical, matching the Tranche 2F hash input):
 * <ul>
 *   <li>{@code responseType = "RANKED"} — one row per ranked candidate;
 *       {@code rankPosition} is the 1-based voter rank; {@code selectionOrder} is
 *       null.</li>
 *   <li>{@code responseType = "APPROVAL"} — one row per approved candidate;
 *       {@code rankPosition} is null; {@code selectionOrder} is the 1-based index
 *       in the contest's canonical candidate order (selection order is not
 *       significant, so the canonical order is stored to match the ballot hash).
 *       </li>
 * </ul>
 *
 * @param responseId        the row id (DB-assigned)
 * @param anonymousBallotId the owning anonymous ballot
 * @param officeKey         the contest/office key
 * @param responseType      {@code RANKED} or {@code APPROVAL}
 * @param candidateKey      the candidate key
 * @param rankPosition      1-based rank for ranked responses, else null
 * @param selectionOrder    1-based canonical index for approval responses, else null
 * @param createdAt         row creation timestamp text (DB default)
 */
public record AnonymousBallotContestResponse(
        long responseId,
        long anonymousBallotId,
        String officeKey,
        String responseType,
        String candidateKey,
        Integer rankPosition,
        Integer selectionOrder,
        String createdAt
) {
    public static final String TYPE_RANKED = "RANKED";
    public static final String TYPE_APPROVAL = "APPROVAL";

    public AnonymousBallotContestResponse {
        Objects.requireNonNull(officeKey, "officeKey");
        Objects.requireNonNull(responseType, "responseType");
        Objects.requireNonNull(candidateKey, "candidateKey");
    }
}
