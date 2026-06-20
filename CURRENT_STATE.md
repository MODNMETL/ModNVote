# CURRENT_STATE — ModNVote

This is the primary handoff document for new development sessions.

Read this after:

1. `README.md`
2. `ARCHITECTURE.md`
3. `CHANGELOG.md`
4. `Project-Context.txt` if present in the active context upload

If this file disagrees with code, verify against the code and resolve the disagreement before editing.

---

## Baseline

- Branch: `main`
- Current release: `v2.1.1`
- In development: `2.2.0` (Linked Offices development stretch; groundwork only so far — see CHANGELOG and the "2.2.0 groundwork" section below)
- Java target: 21
- Platform target: Paper 1.21.x
- Folia-aware scheduling: through `ModNScheduler`
- Build command, Unix/macOS: `./gradlew clean build`
- Build command, Windows: `gradlew.bat clean build`
- Release jar: produced by `shadowJar` as `build/libs/modnvote-<version>.jar`

`build.gradle.kts` is authoritative for the current plugin version.

---

## 2.2.0 groundwork (in progress)

The 2.2.0 stretch targets a generic Linked Offices election model (multiple
contests resolved from a single anonymous ballot). That feature is **not yet
implemented**.

Completed groundwork so far:

- Extracted a shared `BallotCanonicalizer` (`service.canonical`) as the single
  source of truth for anonymous-ballot canonical payload construction. Both
  `BallotService` (submission) and `IntegrityVerificationService`
  (recount/verification) now use it instead of duplicated private builders.
  Canonical output is byte-for-byte unchanged (`rule_snapshot_version=v2`), so
  existing stored ballot hashes and proof commitments still verify.
- Added a test foundation under `src/test`:
  - golden/stability tests locking the canonical payload format for existing
    `YES_NO` and `RANKED_SINGLE_WINNER` ballots
  - schema privacy/non-joinability regression tests over the live schema
- Bumped the project version to `2.2.0`.

Tranche 2A added the generic election-definition/config layer (definition only, no voting):

- Generic election-definition domain model under `domain.election`
  (`ElectionDefinition`, `ContestDefinition`, `CandidateDefinition`,
  `OfficeDependencyRule`, `CountingMethod`, `OfficeDependencyType`). Mayor/Council
  are examples only; nothing is hardcoded.
- `ElectionDefinitionParser` (parses `polls.config_json`; preserves office and
  candidate order; converts `excludeWinnersFrom` into `EXCLUDE_WINNERS`
  dependencies; rejects unknown models/methods) and `ElectionDefinitionValidator`
  (generic structural rules including an acyclic dependency check).
- Surfaced `config_json` on `Poll`/`PollDao` and `metadata_json` on
  `PollOption`/`PollOptionDao`, both defaulting to `"{}"` via backward-compatible
  constructors. No schema change.
- Gson is used for parsing as a `compileOnly` (Paper-provided) + test-only
  dependency; it is not shaded into the plugin jar.

Tranche 2B added read-only admin definition validation (still no voting):

- `ElectionDefinitionService` (`service`) parses+validates a poll's `config_json`
  and returns a structured `ElectionDefinitionValidationResult` (no throwing, no
  persistence, no identity/ballot involvement).
- `/modnvote validate-definition <pollId>` admin command (permission
  `modnvote.admin.poll.create`) reports valid/invalid + issues. Read-only: no
  status change, no DB write, no GUI.
- Reserved, NON-VOTABLE `PollType.LINKED_OFFICES`. It is guarded out of the vote
  command, the vote session layer, `ResultService`, and authoring/lifecycle
  (cannot be created or readied for voting). Existing types/data stay compatible.

Tranche 2C added linked-offices authoring + lifecycle readiness (still no voting):

- `/modnvote create linked_offices` creates a DRAFT, non-votable poll
  (`PollService.createPoll` now accepts `LINKED_OFFICES`; default `config_json`
  is `"{}"`, no default options).
- `/modnvote config <pollId> set <json>` and `/modnvote config <pollId> import
  <file>` store a definition via `PollService.updatePollConfigJson` /
  `PollDao.updatePollConfigJson` (writes only `config_json`). Definitions are
  parsed+validated first; invalid ones are rejected with no write. File import
  uses `LinkedOfficesDefinitionFileLoader`, reading UTF-8 from
  `plugins/ModNVote/definitions` with path-traversal rejection. Config writes are
  allowed only for `LINKED_OFFICES` polls and only while DRAFT.
- A `POLL_CONFIG_UPDATED` audit event stores poll id, actor, declared model, a
  SHA-256 hash of the definition, and its byte length — never the raw JSON.
- `validatePollDefinition`/`readyPoll` accept `LINKED_OFFICES` only with a valid
  definition; an explicit `openPoll` guard rejects it even when READY.
- Example: `docs/examples/linked-offices-mayor-council.json` (example only).

Tranche 2D added an in-game admin builder GUI for the definition (still no voting):

- `/modnvote edit-definition <pollId>` opens a GUI to edit a `LINKED_OFFICES`
  poll's `ElectionDefinition` (offices, candidates, EXCLUDE_WINNERS dependencies),
  with Validate and Save buttons. Code lives in `ui.builder.election`.
- The GUI is an editor for `ElectionDefinition`, **not** a source of truth. It
  loads by parsing `config_json` into a Bukkit-free `LinkedOfficesBuilderState`;
  Save serializes via `ElectionDefinitionSerializer` and writes **only** through
  `PollService.updatePollConfigJson` (no DAO bypass). Validate reuses
  `ElectionDefinitionService`. Invalid definitions cannot be saved.
- `ElectionDefinitionSerializer` is the deterministic, order-stable inverse of
  `ElectionDefinitionParser` (`parse(serialize(x))` equals `x`).
- The JSON `config set`/`import` paths from Tranche 2C remain supported.
- Hardening: the legacy `poll_options` authoring methods are rejected for
  `LINKED_OFFICES` polls, so the `ElectionDefinition` in `config_json` is the
  single source of truth for linked-offices candidates.

Tranche 2E added the in-memory execution model for linked-offices votes (still
no voting, storage, or counting):

- New `domain.election.execution` package, pure in-memory and Bukkit-free:
  sealed `ContestVote` (`RankedContestVote` / `ApprovalContestVote`) and
  `LinkedElectionBallot` (an `ElectionDefinition` plus per-contest responses).
- `LinkedElectionBallotValidator` validates a ballot against its definition and
  returns a structured `BallotValidationResult` (never throws for ordinary voter
  mistakes): office exists, vote shape matches the contest method, no duplicate
  response per office, candidates exist/unique/eligible, approval within
  `maxSelections`, and definition dependency references resolve.
- `ElectionDependencyEvaluator` interprets dependency rules **without applying
  outcomes** (`determineCandidatesEligibleForContest`, `evaluateDependencies` →
  deterministic `DependencyEvaluation` with counting order and cycle detection).
- `LinkedElectionCanonicalModel` fixes the deterministic ordering for future
  ballot hashing (contest order from the definition; ranked order preserved;
  approval normalised to contest order) and produces a `CanonicalBallot`. This
  is canonicalization planning, NOT the final hash implementation.

Explicitly NOT done in this groundwork:

- `PollType.LINKED_OFFICES` is authorable/readyable but remains non-votable;
  there is NO linked-offices voting, submission, counting, or result calculation,
  and it cannot be opened.
- No `anonymous_ballot_contest_responses` table or any schema change.
- No multi-contest ballot submission, counting pipeline, or IRV extraction.
- The linked-offices GUI edits definition data only; there is NO linked-offices
  voter GUI/session flow, and no proof-phrase or participation-token changes.
- The Tranche 2E execution model is pure in-memory types only: no ballot
  submission, no persistence, no counting/tallying, and no participation/privacy
  changes. Nothing constructs or stores a `LinkedElectionBallot` in production.

Tranche 2F added the linked-offices canonical payload (the deterministic hashing
input), still with no voting, storage, or counting:

- New `BallotCanonicalizer.canonicalLinkedOfficesBallotPayload(Poll,
  ElectionDefinition, LinkedElectionBallot, Instant)` builds a deterministic,
  versioned text payload for a multi-contest ballot — the exact bytes a later
  tranche will hash for ballot hash / commitment / proof-phrase / recount.
- It uses its own version `rule_snapshot_version=linked_offices_v1`, separate
  from the single-contest `v2`; the existing `YES_NO` / `RANKED_SINGLE_WINNER`
  canonical payloads are unchanged byte-for-byte.
- It validates the ballot via `LinkedElectionBallotValidator` first and throws
  `IllegalArgumentException` for an invalid ballot (and for a non-`LINKED_OFFICES`
  poll, or a definition that does not match the ballot's own); it never hashes an
  invalid ballot. Ordering comes from `LinkedElectionCanonicalModel` (contest
  order, ranked preserved, approval normalised). The payload carries no player
  UUID/name/IP, session state, or participation token.

Explicitly NOT done by Tranche 2F:

- Nothing in production calls `canonicalLinkedOfficesBallotPayload`; there is no
  submission, no multi-contest storage, no vote session, no counting, and no
  result calculation. No schema/`SchemaInitializer`/`anonymous_ballot*` changes,
  and no proof-phrase or participation-token changes.

Tranche 2G added anonymous multi-contest ballot **storage infrastructure** and
wired the 2F canonical payload into hash derivation, still with no voting,
counting, or results:

- New `anonymous_ballot_contest_responses` table (the first 2.2.0 schema
  addition): `response_id`, `anonymous_ballot_id`, `office_key`, `response_type`
  (`RANKED`/`APPROVAL`), `candidate_key`, `rank_position` (ranked only),
  `selection_order` (approval only, canonical order), `created_at`. Only FK is to
  `anonymous_ballots` (ON DELETE CASCADE); no identity columns, no
  `participation_records` link. UNIQUE `(anonymous_ballot_id, office_key,
  candidate_key)`.
- New `AnonymousBallotContestResponse` (domain) + `AnonymousBallotContestResponseDao`
  (insert canonical rows / read by ballot in deterministic order / delete by
  ballot); no identity-aware queries.
- New `LinkedBallotStorageService` (Bukkit-free, **not** a voting path, not
  reachable from any command/session/GUI): validates via
  `canonicalLinkedOfficesBallotPayload`, derives `ballot_hash =
  SHA-256(payload)` and `ballot_commitment_hash` (existing proof semantics), and
  transactionally writes one participation record + one anonymous ballot + the
  contest-response rows (all-or-nothing). Approval rows stored in canonical
  contest order. Requires a `LINKED_OFFICES` poll; deliberately does not require
  `OPEN` (storage primitive — a future real submission path must add it).
- New `LinkedBallotReconstructor` rebuilds a `LinkedElectionBallot` from stored
  rows; re-canonicalising reproduces the original payload/hash (recount
  foundation; no counting).

Explicitly NOT done by Tranche 2G:

- No voter GUI, vote session, player voting command, counting, IRV/approval
  tallying, dependency-outcome application, or result calculation; nothing in
  production calls `LinkedBallotStorageService`. Existing `YES_NO`/`RANKED`
  submission, `anonymous_ballot_preferences`, single-contest canonical payloads,
  participation-token hashing, and proof-phrase generation are all unchanged.

The canonicalizer is intentionally shaped so multi-contest canonicalization can
be added later without altering the existing single-contest format.

Tranche 2H wired linked-offices stored content into integrity verification, with
**no schema changes** (it consumes the 2G schema) and still no voting, counting,
or results:

- `IntegrityVerificationService.verifyPollIntegrity` now branches on poll type:
  `LINKED_OFFICES` polls are delegated to the new, Bukkit-free
  `LinkedOfficesIntegrityVerifier`; the single-contest (`YES_NO`/`RANKED`) path is
  unchanged. For a linked-offices poll the verifier parses + validates the
  definition from `config_json`, and for each anonymous ballot loads its
  `anonymous_ballot_contest_responses` rows, reconstructs the
  `LinkedElectionBallot`, rebuilds the canonical payload, recomputes
  `ballot_hash = SHA-256(payload)` via the shared `BallotHashingService`, and
  compares it to the stored hash. Failures: missing/invalid `config_json`, a
  ballot with no rows, rows that cannot reconstruct a valid ballot (unknown
  office/candidate, ineligible), and any recomputed-vs-stored hash mismatch —
  reported with deterministic, identity-free messages (poll id, anonymous ballot
  id, failure type, expected/actual hash).
- Only `ballot_hash` is recomputed. `ballot_commitment_hash` binds the voter's
  proof phrase, which integrity does not hold, so commitment recomputation stays
  in the bearer-token proof path; there is no linked-office proof-phrase bypass.
- `LinkedBallotReconstructor` is hardened: it now throws
  `LinkedBallotReconstructionException` for mixed response types in one office,
  missing/non-positive/duplicate `rank_position`/`selection_order`, duplicate
  candidate rows, and unknown response types — it no longer silently repairs
  malformed stored rows.

Explicitly NOT done by Tranche 2H:

- No voter GUI, vote session, player voting command, counting, IRV/approval
  tallying, dependency-outcome application, or result calculation. No schema
  changes. Existing `YES_NO`/`RANKED` integrity behaviour, single-contest
  canonical payloads, participation-token hashing, and proof-phrase generation
  are unchanged. **Linked-office bearer-token proof-phrase verification is
  deferred** to a later tranche (the existing single-contest proof path is
  untouched).

Tranche 2I completed the linked-offices proof loop the 2H commitment boundary
deferred, with **no schema changes** (it consumes the 2G schema) and still no
voting, counting, or results:

- New `BallotService.verifyLinkedOfficeBallotProof(pollId, ballotProofPhrase)`
  resolves and type-checks the poll, then delegates to the new, Bukkit-free
  `LinkedOfficesProofVerifier`. The verifier hashes the phrase to
  `ballot_proof_hash`, loads the matching anonymous ballot, reconstructs it from
  its `anonymous_ballot_contest_responses` rows, re-canonicalises it, and compares
  **both** the recomputed `ballot_hash` and the phrase-bound
  `ballot_commitment_hash` to the stored values — proving the held phrase commits
  to that exact stored ballot. The single-contest `verifyBallotProof` path and
  its `BallotProofVerificationResult` are unchanged.
- Result type `LinkedOfficeBallotProofVerificationResult` (nested `OfficeResponse`)
  exposes anonymous content only: poll id, anonymous ballot id, `submitted_at`, a
  `verified` flag, the anonymous `ballot_hash`, per-office response content (office
  key, response type, ordered candidate keys, populated only on success), and an
  identity-free `failureReason` on failure. Bearer-token semantics: success
  reveals the anonymous content to the phrase holder, but never voter identity.
- Failures reported (identity-free, no content): proof phrase not found,
  mismatched/non-linked poll, invalid `config_json`, missing contest rows,
  reconstruction/canonicalization failure, and `ballot_hash`/commitment mismatch.

Explicitly NOT done by Tranche 2I:

- No voter GUI, vote session, player voting command, counting, IRV/approval
  tallying, dependency-outcome application, or result calculation. No schema
  changes. No production command is wired to the new linked-offices proof method
  yet. Existing single-contest proof verification, canonical payloads,
  participation-token hashing, and proof-phrase generation are unchanged. The
  verifier reads only anonymous content keyed by `anonymous_ballot_id` and never
  joins `participation_records`, preserving identity↔content non-joinability.

Tranche 2J wired the already-built linked-offices verification into the existing
commands, with **no schema changes** and no new verification logic, and still no
voting, counting, or results:

- `/modnvote verify ballot <pollId> <proofPhrase>` now routes `LINKED_OFFICES`
  polls to `BallotService.verifyLinkedOfficeBallotProof` (Tranche 2I) and renders
  the result through the new, Bukkit-free `LinkedOfficeProofDisplayFormatter`. The
  `YES_NO`/`RANKED_SINGLE_WINNER` branch of `handleBallotVerification` is unchanged
  byte-for-byte; the linked-offices case is an early route added at the top.
- On success the command shows the poll id, `submitted_at`, the anonymous
  `ballot_hash`, and per-office responses (office key, response type, ordered
  candidate keys — ranked offices numbered, approval offices bulleted). On a
  not-found phrase or a failed verification it shows an identity-free message plus
  the identity-free `failureReason`, and **no** office/candidate content.
- Linked-office **integrity** verification needed no command change: it was already
  reachable generically via `/modnvote verify participation <pollId>`
  (`IntegrityVerificationService.verifyPollIntegrity` delegates `LINKED_OFFICES` to
  `LinkedOfficesIntegrityVerifier` since Tranche 2H). Command help text was updated
  to say `verify participation` covers all poll types and `verify ballot` covers
  yes/no, ranked, and linked-offices proof phrases.

Explicitly NOT done by Tranche 2J:

- No voting, vote session, voter GUI, player linked-offices voting command,
  counting, IRV/approval tallying, dependency-outcome application, or result
  calculation. No schema changes, no new verification logic, no new command,
  permission, or tab-completion entry (the existing `modnvote.verify` permission
  and `verify ballot`/`verify participation` arguments are reused). Existing
  single-contest proof/integrity command output, canonical payloads,
  participation-token hashing, and proof-phrase generation are unchanged. Command
  output is structurally incapable of echoing voter identity or the proof phrase,
  preserving identity↔content non-joinability.

---

## Current product state

ModNVote 2.x is a clean-install, privacy-first, auditable voting plugin for Paper servers.

The legacy 1.x Yes/No-only workflow has been replaced by a GUI-first ballot platform supporting:

- Ranked single-winner polls
- Yes/No polls
- Anonymous ballot storage
- Identity-aware participation tracking without joining identity to vote content
- Ballot proof-phrase verification
- Tamper-evident audit records
- External witness publication through Discord-compatible webhooks
- Automatic and manual integrity checkpoints
- Transparent ranked-choice result reporting

Migration from legacy 1.x databases is not currently supported.

---

## Proven implemented state

The following are implemented and should be treated as current behavior unless code inspection proves otherwise.

### Poll authoring and lifecycle

- GUI Poll Builder for ranked single-winner polls
- GUI Poll Builder for Yes/No polls
- `/modnvote create ranked_single_winner <optionCount>`
- `/modnvote create yes_no`
- `/modnvote edit <draftPollId>`
- `/modnvote clone <sourcePollId>`
- `/modnvote guide`
- Builder title editing through chat prompts
- Builder description editing through chat prompts
- Builder option name editing through chat prompts
- Builder option description editing through chat prompts
- Ranked builder Allow Partial toggle
- Ranked builder Max Rankings cycle control
- Builder READY validation and transition
- Builder Cancel closes without deleting draft
- Poll deletion for DRAFT/READY polls
- `/poll` alias for `/modnvote`

### Voting

- Ranked voting GUI
- Yes/No voting GUI
- Mandatory vote confirmation
- Anonymous ballot submission
- Join notifications for open unvoted polls
- Pane-less Java/Bedrock-friendly GUI design

### Verification and integrity

- `/modnvote mypolls`
- `/modnvote verify participation <pollId>`
- `/modnvote verify ballot <pollId> <proofPhrase>`
- Participation verification
- Ballot proof-phrase verification
- Audit chain verification
- Ballot hash and commitment verification
- Result display from anonymous ballots only

### Witness publication

- Configurable Discord-compatible webhook publication
- Best-effort poll-opened witness publication
- Best-effort poll-closed witness publication
- Automatic integrity checkpoints at configured accepted-ballot intervals
- Manual checkpoint publication through `/modnvote checkpoint <pollId>`
- Manual closed-result republication through `/modnvote publishresult <pollId>`

Webhook delivery is intentionally best-effort and must never block or roll back poll lifecycle, voting, closing, or persistence.

---

## Core invariants

Do not break these.

- Anonymous ballots are the source of truth for vote content.
- Participation records are identity-aware but separate from vote content.
- Identity and vote content must not be joinable.
- Results must come from anonymous ballots only.
- `/verify participation` must not reveal vote content.
- `/verify ballot` is proof-phrase bearer-token ballot verification.
- Proof phrases must not be derived from player identity.
- GUI/session layer must not write ballots or lifecycle state directly.
- Service layer owns validation, lifecycle, and persistence authority.
- GUI/session work must remain Folia-aware through `ModNScheduler`.
- Witness publication must be privacy-safe and must not include voter identity, proof phrases, participation receipts, IP data, or per-player vote content.
- Webhook failures must be logged safely without exposing full webhook URLs.

---

## Command surface

All commands use `/modnvote`; `/poll` is a direct alias for the same command executor and tab completer.

### Normal admin workflow

```text
/modnvote guide
/modnvote create ranked_single_winner <optionCount>
/modnvote create yes_no
/modnvote edit <draftPollId>
/modnvote clone <sourcePollId>
/modnvote list
/modnvote show <pollId>
/modnvote validate-definition <pollId>
/modnvote create linked_offices
/modnvote config <pollId> set <json>
/modnvote config <pollId> import <file>
/modnvote edit-definition <pollId>
/modnvote delete <pollId>
/modnvote open <pollId>
/modnvote close <pollId>
/modnvote result <pollId>
/modnvote checkpoint <pollId>
/modnvote publishresult <pollId>
```

### Player workflow

```text
/modnvote vote <pollId>
/modnvote mypolls
/modnvote verify participation <pollId>
/modnvote verify ballot <pollId> <proofPhrase>
```

### Utility commands

```text
/modnvote status
/modnvote reload
```

### Hidden or recovery authoring commands

These may remain callable but are not the preferred normal workflow:

```text
/modnvote set <pollId> <field> <value>
/modnvote option <add|edit|move|remove> ...
/modnvote validate <pollId>
/modnvote ready <pollId>
/modnvote rankedpolldemo
```

Prefer the GUI Poll Builder for normal authoring.

---

## Result model

Results are calculated by `ResultService` from anonymous ballots only.

### Yes/No polls

Yes/No polls use canonical service-managed options and straightforward tally output.

### Ranked single-winner polls

Ranked single-winner polls use IRV-style transfer rounds.

Important points for future sessions:

- First-preference totals are not necessarily the final result.
- A candidate can win after transfers even if they did not lead the first-preference round.
- The public result output must make this clear.
- `ResultService.PollResult` includes ranked-choice round data, final winner tally, and exhausted ballot count.
- `ResultService.RankedChoiceRound` snapshots each IRV round.
- Empty ranked polls must not resolve to a winner.

### Canonical presentation layer

`ResultDisplayFormatter` is the shared result presentation helper.

Use it for:

- in-game result output
- Discord witness result fields
- future result-display paths where practical

Do not duplicate ranked-choice result formatting in command or publication code unless there is a deliberate reason.

Current ranked output should distinguish:

- poll winner
- final winner tally
- first preference round
- final IRV round
- full IRV round breakdown
- eliminated option per non-final round
- exhausted ballots where applicable

This was added to avoid the previous failure mode where the winner was correct but the displayed counts looked like first-preference-only totals.

---

## Witness publication model

Witness publication is handled by the publication layer, with command/lifecycle code invoking it after successful state changes.

Expected events:

- poll opened
- poll closed with result summary
- automatic integrity checkpoint
- manual integrity checkpoint
- manual closed-result republication

Design rules:

- Best-effort only.
- Use asynchronous webhook delivery.
- Log failures.
- Never block or roll back voting, lifecycle transitions, or persistence because a webhook failed.
- Never log full webhook URLs.
- Keep payloads privacy-safe.

Manual result republication:

```text
/modnvote publishresult <pollId>
```

This is intended for republishing corrected/updated closed-poll result formatting to configured witness webhooks. It requires the poll to be `CLOSED`.

Manual checkpoint publication:

```text
/modnvote checkpoint <pollId>
```

This publishes a privacy-safe integrity snapshot, not per-player vote content.

---

## Config surface

`config.yml` includes publication controls similar to:

```yaml
publication:
  discord_webhooks: []
  publish_poll_opened: true
  publish_poll_closed: true
  publish_checkpoints: true

integrity:
  checkpoint_interval_ballots: 25
  canonicalization_version: 1
```

Rules:

- `discord_webhooks: []` disables external publication.
- One or more Discord-compatible webhook URLs can be configured.
- Real webhook URLs must never be committed.
- Automatic checkpoints require publication to be enabled and at least one webhook configured.
- `checkpoint_interval_ballots <= 0` disables automatic interval checkpoints.

---

## Important implementation notes

- `PollCommand.java` is large. Prefer targeted local edits unless doing a fresh full-file replacement with extreme care.
- Always fetch/read current canonical files before editing.
- Never write snippet-only placeholder files to the repo.
- Keep Gradle Java source/target at Java 21 unless explicitly agreed.
- Do not commit `.gradle/`.
- Prefer changes that build after each tranche.
- Use service-layer APIs for poll lifecycle, validation, persistence, results, and verification.
- GUI/session code must delegate authoritative mutations to services.
- Result display changes should generally go through `ResultDisplayFormatter`.
- Witness publication changes should preserve privacy and best-effort delivery semantics.
- Anonymous-ballot canonical payloads must go through `BallotCanonicalizer` (`service.canonical`); never re-inline a private canonical builder in a service.

---

## Main source map

Key files and areas for future sessions:

```text
src/main/java/com/modnmetl/modnvote/ModNVotePlugin.java
```

Plugin bootstrap, dependency wiring, command registration, configuration reload, listener registration.

```text
src/main/java/com/modnmetl/modnvote/commands/PollCommand.java
```

Root `/modnvote` and `/poll` command executor/tab completer.

```text
src/main/java/com/modnmetl/modnvote/service/PollService.java
```

Poll creation, cloning, lifecycle, option mutation, validation.

```text
src/main/java/com/modnmetl/modnvote/service/BallotService.java
```

Ballot submission and participation/ballot verification.

```text
src/main/java/com/modnmetl/modnvote/service/canonical/BallotCanonicalizer.java
```

Shared canonical anonymous-ballot payload construction used by both
`BallotService` and `IntegrityVerificationService`. Tranche 2F added a separate
`canonicalLinkedOfficesBallotPayload(...)` method (version `linked_offices_v1`)
that builds the deterministic hashing input for a multi-contest linked-offices
ballot; it validates first, is not yet wired to any production path, and leaves
the single-contest `v2` output byte-for-byte unchanged.

```text
src/main/java/com/modnmetl/modnvote/domain/election/
```

Generic linked-offices election definition layer (Tranche 2A): immutable
definition model, `ElectionDefinitionParser`, and `ElectionDefinitionValidator`.
Definition/config only — no voting, persistence of multi-contest content,
counting, or GUI.

```text
src/main/java/com/modnmetl/modnvote/domain/election/execution/
```

In-memory linked-offices execution model (Tranche 2E): `ContestVote`
(`RankedContestVote`/`ApprovalContestVote`), `LinkedElectionBallot`,
`LinkedElectionBallotValidator` (structured `BallotValidationResult`),
`ElectionDependencyEvaluator` (no outcomes applied), and
`LinkedElectionCanonicalModel` (ordering planning for future hashing). Pure
in-memory types — no submission, persistence, counting, or voter GUI.

```text
src/main/java/com/modnmetl/modnvote/storage/AnonymousBallotContestResponseDao.java
src/main/java/com/modnmetl/modnvote/service/LinkedBallotStorageService.java
src/main/java/com/modnmetl/modnvote/service/LinkedBallotReconstructor.java
```

Linked-offices anonymous storage (Tranche 2G): DAO for the
`anonymous_ballot_contest_responses` content table (no identity, FK only to
`anonymous_ballots`), the Bukkit-free `LinkedBallotStorageService` (validates +
hashes via `canonicalLinkedOfficesBallotPayload`, transactionally writes one
participation + one anonymous ballot + contest rows; not a voting path, not
called in production), and `LinkedBallotReconstructor` (rebuilds a ballot from
stored rows for recount/debugging; hardened in Tranche 2H to reject malformed
rows via `LinkedBallotReconstructionException`). No counting, results, or voter
GUI.

```text
src/main/java/com/modnmetl/modnvote/service/IntegrityVerificationService.java
src/main/java/com/modnmetl/modnvote/service/LinkedOfficesIntegrityVerifier.java
```

Integrity verification / recount. `IntegrityVerificationService` recomputes
single-contest anonymous-ballot hashes and, since Tranche 2H, branches on poll
type: `LINKED_OFFICES` polls are delegated to the standalone, Bukkit-free
`LinkedOfficesIntegrityVerifier`, which parses the definition from `config_json`,
reconstructs each anonymous ballot from its `anonymous_ballot_contest_responses`
rows, re-canonicalises it, recomputes `ballot_hash`, and compares it to the
stored hash. Verification only — no counting, results, or voter GUI; only
`ballot_hash` is recomputed (commitment recomputation lives in the bearer-token
proof path below).

```text
src/main/java/com/modnmetl/modnvote/service/LinkedOfficesProofVerifier.java
src/main/java/com/modnmetl/modnvote/service/LinkedOfficeBallotProofVerificationResult.java
```

Linked-offices bearer-token proof verification (Tranche 2I). `BallotService`
gains `verifyLinkedOfficeBallotProof(pollId, proofPhrase)`, which resolves +
type-checks the poll and delegates to the standalone, Bukkit-free
`LinkedOfficesProofVerifier`. The verifier hashes the phrase to
`ballot_proof_hash`, loads the matching anonymous ballot, reconstructs it from its
`anonymous_ballot_contest_responses` rows, re-canonicalises it, and compares
**both** the recomputed `ballot_hash` and the phrase-bound
`ballot_commitment_hash` to the stored values. `LinkedOfficeBallotProofVerificationResult`
exposes anonymous content only (per-office response keys on success; identity-free
`failureReason` otherwise) and never reveals voter identity. Verification only —
no counting, results, or voter GUI. The single-contest `verifyBallotProof` path is
unchanged.

```text
src/main/java/com/modnmetl/modnvote/presentation/LinkedOfficeProofDisplayFormatter.java
```

Linked-offices proof verification command output (Tranche 2J). A Bukkit-free,
unit-tested formatter that turns a `LinkedOfficeBallotProofVerificationResult` into
in-game chat lines. `/modnvote verify ballot` now branches on poll type:
`LINKED_OFFICES` polls call `BallotService.verifyLinkedOfficeBallotProof` and print
these lines (poll id, `submitted_at`, anonymous `ballot_hash`, and per-office
responses — ranked numbered, approval bulleted, on success; an identity-free
message and reason with no content on not-found/failure), while the
`YES_NO`/`RANKED_SINGLE_WINNER` rendering is unchanged. Linked-office integrity
remains reachable via `verify participation`. The formatter only reads anonymous
content already on the result and is incapable of emitting voter identity or the
proof phrase. Command access only — no voting, counting, or results.

```text
src/main/java/com/modnmetl/modnvote/service/ResultService.java
```

Result calculation, including ranked-choice IRV rounds.

```text
src/main/java/com/modnmetl/modnvote/presentation/ResultDisplayFormatter.java
```

Canonical result display formatting for in-game and Discord-facing output.

```text
src/main/java/com/modnmetl/modnvote/publication/WitnessPublicationService.java
```

High-level witness publication orchestration.

```text
src/main/java/com/modnmetl/modnvote/ui/
```

Builder, voting GUI, session, renderer, and listener code.

```text
src/test/java/com/modnmetl/modnvote/
```

Test foundation (added in the 2.2.0 groundwork): canonicalizer golden/stability
tests and schema privacy/non-joinability tests.

```text
src/main/resources/plugin.yml
```

Bukkit/Paper command metadata, aliases, and permissions.

```text
src/main/resources/config.yml
```

Default plugin configuration.

```text
src/main/resources/messages.yml
```

Message strings used by the command/UI layer.

---

## Known technical debt and caution areas

- `PollCommand.java` is large and mixes many command branches; keep changes small and verify tab-completion/help updates when adding commands.
- Some low-level authoring commands remain as recovery paths even though GUI builder is preferred.
- Result output is user-facing and witness-facing; avoid wording that makes first-preference totals look like final ranked-choice totals.
- Any future multi-winner/STV work must not reuse single-winner IRV assumptions blindly.
- Publication should remain decoupled from lifecycle persistence.
- Avoid introducing identity-to-ballot joins for convenience reporting.

---

## Recommended smoke test

After significant changes, run:

```text
/modnvote status
/modnvote create ranked_single_winner 3
/modnvote create yes_no
/modnvote edit <draftPollId>
/modnvote open <readyPollId>
/modnvote vote <openPollId>
/modnvote close <openPollId>
/modnvote result <closedPollId>
/modnvote publishresult <closedPollId>
/modnvote checkpoint <pollId>
/modnvote mypolls
/modnvote verify participation <pollId>
/modnvote verify ballot <pollId> <proofPhrase>
```

Also verify:

```text
/poll status
/poll vote <pollId>
```

and tab completion for common admin commands.

---

## New-session rule

Do not rely on prior chat memory if repo docs and code can answer the question.

When starting a new session:

1. Read `README.md`.
2. Read `ARCHITECTURE.md`.
3. Read `CHANGELOG.md`.
4. Read this file.
5. Inspect current code before editing, especially if the task touches commands, result calculation, witness publication, or persistence.

If docs and code disagree, resolve the disagreement explicitly before editing.
