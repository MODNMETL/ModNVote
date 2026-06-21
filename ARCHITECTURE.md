# ModNVote 2.0 Architecture

This document describes the high-level architecture of ModNVote 2.0 and the design constraints that must be preserved when extending the system.

---

## Core design goals

ModNVote 2.0 is built around four non-negotiable goals:

1. **Privacy** — identity and vote content must not be joinable
2. **Verifiability** — players can verify their participation and/or ballot
3. **Tamper evidence** — changes must be detectable
4. **Usability** — voting must be clear, guided, and intuitive

All architectural decisions must reinforce these goals.

---

## Layered architecture

The system is structured into clear layers with strict separation of concerns.

### Command layer

- Parses user input
- Performs permission checks
- Displays formatted output
- Delegates all business logic to services

The command layer must not:
- write directly to the database
- reconstruct ballot logic
- bypass validation rules

---

### GUI / session layer

- Manages inventory-based voting interfaces
- Tracks player interaction state
- Handles click events and transitions between screens

Key components:

- `VoteSession`
- `YesNoVoteSession`
- `VoteSessionManager`
- `YesNoVoteSessionManager`
- `JavaInventoryVoteRenderer`
- `YesNoInventoryVoteRenderer`
- `VoteGuiListener`
- `YesNoVoteGuiListener`
- `VoteSubmissionCoordinator`

Responsibilities:

- Present poll options to the player
- Capture user selections
- Enforce UX rules (confirmation step, slot restrictions)
- Forward final selections to the service layer

The GUI/session layer must not:
- write ballots to the database
- modify poll lifecycle state

---

### Service layer

The service layer is authoritative.

Key services include:

- `PollService`
- `BallotService`
- `IntegrityVerificationService`

Responsibilities:

- Poll lifecycle management (`DRAFT -> READY -> OPEN -> CLOSED`)
- Validation of poll definitions
- Enforcement of Yes/No semantics
- Ballot submission and validation
- Duplicate prevention
- Result calculation
- Verification logic

All validation must occur here.

#### Anonymous-ballot canonicalization

Canonical anonymous-ballot payload construction is centralized in a single
shared component, `BallotCanonicalizer` (`service.canonical`).

- It produces the exact byte sequence that is hashed to form the anonymous
  ballot hash and the ballot commitment hash.
- Both the submission path (`BallotService`) and the recount/verification path
  (`IntegrityVerificationService`) use it, so the two layers can never silently
  drift apart.
- It depends only on poll rule context and anonymous, ordered option-id content.
  It is independent of player identity, UUID, name, IP address, session state,
  and participation records.
- The canonical format is versioned (`rule_snapshot_version`) and must not change
  without an intentional version bump, or previously stored ballot hashes would
  fail verification.

#### Linked offices election definition (definition-only)

The `domain.election` package holds a generic, immutable election-definition
model (`ElectionDefinition`, `ContestDefinition`, `CandidateDefinition`,
`OfficeDependencyRule`, `CountingMethod`, `OfficeDependencyType`), together with
an `ElectionDefinitionParser` (from `polls.config_json`) and an
`ElectionDefinitionValidator`.

- Offices, contests, candidates, and dependencies are fully generic. No office
  name (such as Mayor or Council) is hardcoded; those appear only in
  configuration/examples.
- This layer is definition/config infrastructure only. It does not implement
  voting, persistence of multi-contest ballot content, counting, or GUI flow.
- The anonymous-ballot privacy model is untouched: definitions live in the
  existing `polls.config_json` / `poll_options.metadata_json` columns and carry
  no voter identity.

The service layer exposes a read-only boundary, `ElectionDefinitionService`,
which parses and validates a poll's definition and returns a structured result
(it performs no persistence, lifecycle, or identity work). The admin command
`/modnvote validate-definition <pollId>` uses it for read-only validation.

Admins author a linked-offices definition before opening the poll for voting
(voting itself is described in "Linked offices voter session and ballot submission"):

- `/modnvote create linked_offices` creates a DRAFT poll.
- `/modnvote config <pollId> set <json>` / `import <file>` store a definition in
  `polls.config_json` via `PollService.updatePollConfigJson`. The JSON is parsed
  and validated first; invalid definitions are rejected without any write. File
  import uses `LinkedOfficesDefinitionFileLoader`, which reads UTF-8 JSON from
  `plugins/ModNVote/definitions` and rejects path traversal. A
  `POLL_CONFIG_UPDATED` audit event records only poll id, actor, declared model,
  a SHA-256 hash of the definition, and its byte length — never the raw content.
- `validatePollDefinition` / `readyPoll` accept `LINKED_OFFICES` only when its
  definition validates, so such a poll can reach READY only with a valid
  definition.
- `/modnvote edit-definition <pollId>` opens an in-game builder GUI
  (`ui.builder.election`) that **edits the `ElectionDefinition` only**. The GUI is
  not a source of truth: it loads by parsing `config_json` into a Bukkit-free edit
  buffer (`LinkedOfficesBuilderState`), and Save serializes the buffer
  (`ElectionDefinitionSerializer`) and writes it back exclusively through
  `PollService.updatePollConfigJson` — it never writes the DAO directly. Validation
  reuses `ElectionDefinitionService`, so there is no duplicate validation logic in
  the GUI. The serializer is the deterministic, order-stable inverse of the parser
  (`parse(serialize(x))` equals `x`). The builder edits offices, candidates, and
  EXCLUDE_WINNERS dependencies; it implements no voting, ballot storage, counting,
  or result calculation, and is not a voter GUI.

`PollType.LINKED_OFFICES` is now **votable** (Tranche 2L): `openPoll` accepts it
once `READY` with a valid definition, `/modnvote vote` routes it to the
linked-offices voting GUI, and `ResultService` produces its results. Config
definitions are still accepted only for linked-offices polls and only while DRAFT;
other poll types reject config writes.
The legacy `poll_options` authoring methods are rejected for `LINKED_OFFICES`
polls, so the `ElectionDefinition` in `config_json` is the single source of truth
for linked-offices candidates.

#### Linked offices execution model (in-memory only)

The `domain.election.execution` package formalises, purely in memory, how a
linked-offices vote is represented — before the anonymous ballot layer is
touched. It implements no persistence, schema change, ballot submission,
counting, or voter GUI; it exists so the shape of a vote, its validation, its
dependency interpretation, and its canonical ordering are pinned down ahead of
the storage tranche.

- **Response model:** a sealed `ContestVote` with two shapes —
  `RankedContestVote` (ordered preferences, for IRV) and `ApprovalContestVote`
  (selections, for APPROVAL_TOP_N) — plus `LinkedElectionBallot`, an
  `ElectionDefinition` together with the voter's per-contest responses. All
  immutable records.
- **Validation:** `LinkedElectionBallotValidator` checks a ballot against its
  definition and returns a structured `BallotValidationResult`
  (`BallotValidationIssue` + `BallotValidationCode`) rather than throwing for
  ordinary voter mistakes. It enforces office existence, vote-shape/method match,
  one response per office, candidate existence/uniqueness/eligibility, approval
  `maxSelections`, and resolvable dependency references.
- **Dependencies:** `ElectionDependencyEvaluator` interprets the generic
  dependency rules **without applying any outcomes** — no winners are computed.
  It provides structural eligibility (`determineCandidatesEligibleForContest`)
  and a deterministic `DependencyEvaluation` (`evaluateDependencies`) with
  per-office preceding offices, a topological counting order, and cycle
  detection.
- **Canonicalization planning:** `LinkedElectionCanonicalModel` fixes the
  deterministic ordering for future ballot hashing — contests in definition
  order, ranked candidate order preserved (significant), approval selections
  normalised to contest order (not significant), one response per office — and
  reduces a ballot to a `CanonicalBallot`. This is ordering policy only, **not**
  the final hash implementation.

This layer is Bukkit-free and fully unit-tested. Nothing in production
constructs or stores a `LinkedElectionBallot`; the anonymous-ballot and
participation/privacy models are untouched.

#### Linked offices canonical payload (hashing input only)

`BallotCanonicalizer` gains a second, independent method,
`canonicalLinkedOfficesBallotPayload(Poll, ElectionDefinition,
LinkedElectionBallot, Instant)`, which produces the deterministic byte sequence a
later tranche will hash for a multi-contest linked-offices ballot (ballot hash,
ballot commitment, proof-phrase verification, recount). It computes no hashes,
stores nothing, and is not yet called from any production path.

- **Separation from single-contest output.** The existing
  `canonicalAnonymousBallotPayload` format and its `rule_snapshot_version=v2` are
  untouched. The linked-offices payload is built by a distinct method with its own
  version, `rule_snapshot_version=linked_offices_v1`, so existing `YES_NO` /
  `RANKED_SINGLE_WINNER` payloads (and therefore all stored ballot hashes and
  proof commitments) remain byte-for-byte identical.
- **Validation gate.** Unlike `LinkedElectionCanonicalModel` (which is defensive
  and will canonicalise even a malformed ballot for ordering tests), the payload
  builder requires a valid ballot. It runs `LinkedElectionBallotValidator` first
  and throws `IllegalArgumentException` (naming the failing issue codes/messages)
  for an invalid ballot; it also rejects a non-`LINKED_OFFICES` poll and a
  `definition` that does not match the ballot's own definition. Invalid ballots can
  never produce a hashable payload.
- **Determinism and ordering.** Contest responses are emitted in definition
  contest order via `LinkedElectionCanonicalModel`; ranked preferences are
  preserved (significant); approval selections are normalised to contest candidate
  order (selection order not significant). Input ordering of responses or approval
  selections does not change the payload or its hash.
- **Privacy.** The payload depends only on poll rule context, the election
  definition, and anonymous ballot content. It contains no player UUID, name, IP
  address, session state, or participation token — the same invariants as the
  single-contest payload.

This is the hashing input only: there is still no ballot submission, no
vote session, no counting, and no result calculation for linked offices.

#### Linked offices anonymous storage (infrastructure only)

Tranche 2G adds anonymous storage for multi-contest ballot content and wires the
canonical payload above into hash derivation. It is infrastructure only: there is
still no voter GUI, vote session, player voting command, counting, or result
calculation, and nothing in production stores a linked-offices ballot.

- **Schema.** A new content table `anonymous_ballot_contest_responses` stores one
  row per candidate within a contest response: `response_id`,
  `anonymous_ballot_id`, `office_key`, `response_type` (`RANKED`/`APPROVAL`),
  `candidate_key`, `rank_position` (ranked only), `selection_order` (approval
  only, canonical order), `created_at`. Its only foreign key is
  `anonymous_ballot_id → anonymous_ballots(...) ON DELETE CASCADE`. It carries
  **no** identity column and **no** link to `participation_records`, so one
  anonymous ballot may own many contest-response rows while remaining on the
  content side of the privacy split. A UNIQUE index
  `(anonymous_ballot_id, office_key, candidate_key)` blocks duplicate candidate
  rows within an office.
- **DAO/domain.** `AnonymousBallotContestResponse` (domain) and
  `AnonymousBallotContestResponseDao` (insert canonical rows, read by
  `anonymous_ballot_id` in deterministic order, delete by ballot) — the
  multi-contest analogue of `AnonymousBallotPreferenceDao`, with no identity-aware
  queries and no participation join.
- **Storage service.** `LinkedBallotStorageService` (service layer, Bukkit-free)
  validates a ballot via `BallotCanonicalizer.canonicalLinkedOfficesBallotPayload`,
  derives `ballot_hash = SHA-256(canonical payload)` and `ballot_commitment_hash`
  with the existing proof-phrase semantics, and writes exactly **one participation
  record + one anonymous ballot + the contest-response rows** in a single
  transaction (all-or-nothing). Approval selections are stored in canonical
  contest order so the rows match the hashed payload. It is **not** a player-facing
  voting path — it is not reachable from any command, session, or GUI, and as a
  storage primitive it requires a `LINKED_OFFICES` poll but deliberately does not
  enforce the `OPEN` lifecycle state (a future real submission path must add it).
- **Reconstruction.** `LinkedBallotReconstructor` rebuilds an in-memory
  `LinkedElectionBallot` from stored rows; re-canonicalising it reproduces the
  original payload and ballot hash. This is the recount/debugging foundation; it
  performs no counting or result verification.

The privacy invariant is preserved: one participation record + one anonymous
ballot per stored ballot, with the contest-response rows linked only to the
anonymous ballot, so identity and vote content remain non-joinable.

#### Linked offices integrity verification (recount, verification only)

Tranche 2H wires linked-offices stored content into integrity verification. It is
verification only — **no schema changes** (it consumes the 2G schema) and still no
voter GUI, vote session, player voting command, counting, or result calculation.

- **Branching.** `IntegrityVerificationService.verifyPollIntegrity` branches on
  poll type. `YES_NO` / `RANKED_SINGLE_WINNER` use the existing, unchanged
  single-contest recount (recompute `ballot_hash` from `anonymous_ballot_preferences`).
  `LINKED_OFFICES` is delegated to `LinkedOfficesIntegrityVerifier`.
- **Why a standalone verifier.** `LinkedOfficesIntegrityVerifier` is a Bukkit-free
  collaborator constructed with only a `DatabaseManager` (the same pattern as
  `LinkedBallotStorageService`), so it is fully unit-testable; the full
  `IntegrityVerificationService` cannot be constructed in tests because its
  `PlatformAdapter` exposes Bukkit types and the Paper API is `compileOnly`. The
  verifier returns the existing `IntegrityVerificationResult`.
- **Recount.** For a linked-offices poll it parses + validates the definition from
  `config_json` (via `ElectionDefinitionService`), then for each anonymous ballot
  loads its `anonymous_ballot_contest_responses` rows, reconstructs the
  `LinkedElectionBallot` (`LinkedBallotReconstructor`), rebuilds the canonical
  payload (`BallotCanonicalizer.canonicalLinkedOfficesBallotPayload`), recomputes
  `ballot_hash` via the shared `BallotHashingService.sha256`, and compares it to
  the stored hash. Reported failures: missing/invalid `config_json`, a ballot with
  no rows, rows that cannot reconstruct a valid ballot (unknown office/candidate,
  ineligible candidate), and any recomputed-vs-stored hash mismatch.
- **Commitment boundary.** Only `ballot_hash` is recomputed.
  `ballot_commitment_hash` binds the voter's proof phrase, which integrity does not
  possess, so commitment recomputation stays in the bearer-token proof path (now
  implemented for linked offices in Tranche 2I, below); there is no linked-office
  proof-phrase bypass in the integrity layer.
- **Reconstructor hardening.** Because stored rows are recount input,
  `LinkedBallotReconstructor` no longer silently accepts malformed rows: it throws
  `LinkedBallotReconstructionException` for offices that mix response types,
  missing/non-positive/duplicate ordering positions, duplicate candidate rows, and
  unknown response types.
- **Privacy.** Integrity failure messages carry only poll id, anonymous ballot id,
  failure type, and hashes — never voter identity — so verification cannot become a
  back-channel that re-links identity to vote content.

#### Linked offices proof verification (bearer-token, verification only)

Tranche 2I adds bearer-token proof-phrase verification for linked-offices
anonymous ballots — the path the 2H commitment boundary deferred. It is
verification only — **no schema changes** (it consumes the 2G schema) and still no
voter GUI, vote session, player voting command, counting, or result calculation.

- **Entry point.** `BallotService.verifyLinkedOfficeBallotProof(pollId, proofPhrase)`
  resolves and type-checks the poll, then delegates to `LinkedOfficesProofVerifier`.
  The single-contest `verifyBallotProof` (YES_NO / RANKED) path and its
  `BallotProofVerificationResult` are untouched; linked offices has its own entry
  point and result because a flat option-id list cannot represent multi-contest
  content.
- **Why a standalone verifier.** `LinkedOfficesProofVerifier` is a Bukkit-free
  collaborator constructed with only a `DatabaseManager` (the same pattern as
  `LinkedBallotStorageService` / `LinkedOfficesIntegrityVerifier`), so it is fully
  unit-testable; `BallotService` cannot be constructed in tests (its
  `PlatformAdapter` exposes Bukkit types, Paper API is `compileOnly`).
- **Proof loop.** The phrase is hashed to `ballot_proof_hash`
  (`BallotHashingService.buildBallotProofHash`); the matching anonymous ballot is
  loaded and confirmed to belong to the poll; the definition is parsed + validated
  from `config_json`; the ballot's `anonymous_ballot_contest_responses` rows are
  reconstructed (`LinkedBallotReconstructor`) and re-canonicalised
  (`BallotCanonicalizer.canonicalLinkedOfficesBallotPayload`); then **both** the
  recomputed `ballot_hash` and the phrase-bound `ballot_commitment_hash`
  (`BallotHashingService.buildBallotCommitmentHash`) are compared to the stored
  values. A match proves the held phrase commits to that exact stored ballot.
- **Result.** `LinkedOfficeBallotProofVerificationResult` (nested `OfficeResponse`)
  exposes anonymous content only — poll id, anonymous ballot id, `submitted_at`, a
  `verified` flag, the anonymous `ballot_hash`, and per-office response content
  (office key, response type, ordered candidate keys) on success, with an
  identity-free `failureReason` on failure (and no content). Reported failures:
  phrase not found, mismatched/non-linked poll, invalid `config_json`, missing
  rows, reconstruction/canonicalization failure, and `ballot_hash`/commitment
  mismatch.
- **Privacy.** Proof verification is bearer-token based: the holder of the phrase
  legitimately sees the anonymous ballot content on success, but the verifier reads
  only content keyed by `anonymous_ballot_id`, never joins `participation_records`,
  and never reveals voter identity (no UUID/name/IP/Floodgate id/participation
  token/receipt) in any result or failure reason.

#### Linked offices verification command wiring (command access only)

Tranche 2J exposes the already-built linked-offices verification through the
existing `/modnvote verify` command — **no schema changes**, no new verification
logic, and still no voting, counting, or results.

- **Proof phrase command.** `PollCommand.handleBallotVerification` branches on poll
  type: `LINKED_OFFICES` polls route to `BallotService.verifyLinkedOfficeBallotProof`
  (Tranche 2I); `YES_NO` / `RANKED_SINGLE_WINNER` keep their existing inline
  rendering byte-for-byte. The new branch is an early route added at the top of the
  handler, so the single-contest path is untouched. No new command, permission, or
  tab-completion entry is added — the existing `modnvote.verify` permission and the
  `verify ballot` / `verify participation` arguments are reused.
- **Thin handler + Bukkit-free formatter.** Rendering lives in
  `presentation.LinkedOfficeProofDisplayFormatter`, a Bukkit-free helper (the same
  pattern as `ResultDisplayFormatter`) that turns a
  `LinkedOfficeBallotProofVerificationResult` into chat lines, so command output is
  unit-testable without a server. The handler only forwards each line. On success
  it shows poll id, `submitted_at`, the anonymous `ballot_hash`, and per-office
  responses (ranked offices numbered, approval offices bulleted); on a not-found
  phrase or failed verification it shows an identity-free message and the
  identity-free `failureReason` with no office/candidate content.
- **Integrity command.** Linked-office integrity needed no change: it is already
  reachable via `/modnvote verify participation <pollId>`, which calls
  `IntegrityVerificationService.verifyPollIntegrity` (delegating `LINKED_OFFICES` to
  `LinkedOfficesIntegrityVerifier` since Tranche 2H) and renders the generic
  `IntegrityVerificationResult` (audit chain / ballot hashes / record counts /
  overall + issues).
- **Privacy.** The formatter only reads anonymous content already present on the
  result; it has no access to identity material and is structurally incapable of
  echoing voter identity or the proof phrase, in both success and failure cases.

#### Linked offices counting and result calculation (results only)

Tranche 2K implements deterministic linked-offices counting over already-stored
anonymous ballots — **no schema changes**, and still no voter GUI, vote session, or
ballot submission path.

- **Result domain model.** `domain.election.results` holds immutable, anonymous
  result records: `LinkedElectionResult` (poll id/title, completeness, counted /
  skipped counts, per-contest results, issues), `ContestResult` (office, method,
  seats, winners, candidate results, excluded keys, exhausted ballots, IRV rounds,
  issues), `CandidateResult`, `IrvRoundResult`, and `CandidateTally`. They carry
  office/candidate keys and counts only — never voter identity.
- **Pure calculator.** `LinkedElectionCountingService` (`domain.election.results`)
  is database-free and Bukkit-free. It counts contests in the topological order from
  `ElectionDependencyEvaluator`, applies `EXCLUDE_WINNERS` (a source office's winners
  are removed from a dependent office before it is counted, which forces the source
  to count first), and is generic — no office/candidate name is hardcoded. IRV
  (single-seat) elects on a strict majority of active ballots, eliminating the
  lowest tally with a deterministic latest-in-contest-order tie-break and exhausting
  ballots with no continuing candidate; approval top-N ranks by score then contest
  order. A dependency cycle is reported and the result marked incomplete rather than
  pretending success.
- **Loader collaborator.** `LinkedElectionResultService` (`service`) is a Bukkit-free
  collaborator (holds only a `DatabaseManager`, like `LinkedOfficesIntegrityVerifier`)
  that loads `anonymous_ballots` + `anonymous_ballot_contest_responses`, reconstructs
  each ballot via `LinkedBallotReconstructor`, skips and reports any unreconstructable
  ballot, and runs the calculator. It reads only anonymous content + the election
  definition; it never reads or joins `participation_records`.
- **ResultService integration.** `ResultService.getLinkedElectionResult(pollId)`
  (CLOSED polls only) returns a `LinkedElectionResult`; the single-contest
  `getPollResult` path and `PollResult` shape are unchanged. `/modnvote result`
  branches on poll type and renders `LINKED_OFFICES` through the Bukkit-free
  `presentation.LinkedElectionResultDisplayFormatter`.

#### Linked offices voter session and ballot submission (votable)

Tranche 2L makes `LINKED_OFFICES` **votable end to end** — **no schema changes**, it
reuses the Tranche 2G storage and Tranche 2K counting.

- **Open gate.** `PollService.openPoll` opens a `LINKED_OFFICES` poll only when it is
  `READY` and its definition still validates; an invalid definition cannot open.
  `YES_NO`/`RANKED_SINGLE_WINNER` open behaviour is unchanged.
- **Bukkit-free voting core.** `LinkedOfficesVoteState` (`ui.session.election`) holds
  the voter's per-office selections, enforces `maxSelections` for approval offices,
  builds the immutable `LinkedElectionBallot`, and reports submit-readiness via
  `LinkedElectionBallotValidator`. `EXCLUDE_WINNERS` is **not** applied at cast time —
  every structurally eligible candidate is offered for each office; exclusion is a
  count-time outcome. `LinkedOfficesVoteSession`/`LinkedOfficesVoteScreen`/
  `LinkedOfficesVoteSessionManager` model navigation and one session per player.
- **GUI layer.** `ui.render` adds `LinkedOfficesInventoryHolder`,
  `LinkedOfficesVoteRenderer`, `LinkedOfficesVoteListener`, and quit/close cleanup
  listeners, mirroring the yes/no and ranked GUI architecture (overview → per-office
  ranking/approval → review/submit) and reusing `ModNScheduler`, `VoteSoundService`,
  and `messages.yml`.
- **Submission service.** `LinkedOfficesSubmissionService` (`service`) is Bukkit-free
  (holds only a `DatabaseManager`). It re-enforces the gate server-side (poll exists /
  `LINKED_OFFICES` / `OPEN` / definition valid / ballot valid), issues a fresh proof
  phrase via the shared `BallotProofPhraseGenerator`, and delegates the transactional
  write to `LinkedBallotStorageService` (one participation record, one anonymous
  ballot, the contest-response rows). Duplicate voting is prevented by the existing
  participation-token semantics; a rejected/duplicate submission writes nothing.
  `VoteSubmissionCoordinator.submitLinkedOfficesVote(...)` bridges the GUI to it, and
  `/modnvote vote` routes `LINKED_OFFICES` to the linked-offices GUI.
- **Privacy.** Voter identity is used only to derive the participation token/record;
  vote content goes only to `anonymous_ballots` and `anonymous_ballot_contest_responses`,
  and no contest-response row or message combines identity with vote content.

#### Linked offices close / result witness publication

Tranche 2M wires `LINKED_OFFICES` results into the existing close/publish witness
flow — **no schema changes**, **no privacy-model change**.

- **Close / publishresult routing.** `/modnvote close` and `/modnvote publishresult`
  route through a shared `PollCommand.publishClosedResult(poll)` helper that branches on
  poll type. `LINKED_OFFICES` computes a `LinkedElectionResult` via
  `ResultService.getLinkedElectionResult` and publishes it through the linked overload;
  `YES_NO`/`RANKED_SINGLE_WINNER` stay on the unchanged single-contest `getPollResult` +
  `publishPollClosed(Poll, options, PollResult)` path. The close lifecycle operation
  itself (`PollService.closePoll`) was always poll-type agnostic (status flip + audit
  event); only publication needed routing.
- **Publication overload.** `WitnessPublicationService.publishPollClosed(Poll,
  LinkedElectionResult)` renders the linked result onto the existing Discord "Poll
  Closed" embed, gated by the same `publication.publish_poll_closed` flag.
- **Deterministic payload.** `presentation.LinkedElectionWitnessPayloadFormatter` is a
  Bukkit-free, database-free builder that turns a `LinkedElectionResult` into witness
  fields: poll id/type/status/close time/completeness/counted + skipped ballots, one
  field per office (counting method, seats, winners, candidate tallies, dependency
  exclusions, IRV rounds, issues), then an election-issues field. Offices render in
  result order, candidates in contest order, rounds in round order. It receives only
  anonymous result data, so it is structurally incapable of emitting voter identity,
  participation token/receipt, anonymous ballot id, or proof material.
- **Checkpoint.** `/modnvote checkpoint` is integrity-only and poll-type agnostic; it
  already works for `LINKED_OFFICES` via Tranche 2H integrity verification and is
  unchanged.

---

### Persistence (DAO) layer

- Handles all database interaction
- Encapsulates SQL logic

Key data domains:

- Polls
- Poll options
- Participation records (identity-aware)
- Anonymous ballots (vote content)
- Ballot preferences (ranked ordering)
- Anonymous contest responses (linked-offices multi-contest vote content)
- Audit events

---

## Privacy model

The system enforces strict separation between:

### Participation

- Stores player identity (UUID, IP heuristics)
- Tracks whether a player has voted
- Used for duplicate prevention

### Ballots

- Stores vote selections
- Contains no player identity

These datasets must not be joinable.

---

## Verification model

### Participation verification

```
/modnvote verify participation <pollId>
```

- Confirms that a player has voted
- Does not reveal vote content

### Ballot verification

```
/modnvote verify ballot <pollId> <proof phrase>
```

- Uses a proof phrase (bearer token)
- Reveals the ballot selection
- Does not identify the voter

The proof phrase must not be derived from or linked to player identity.

---

## Poll lifecycle

```
DRAFT -> READY -> OPEN -> CLOSED
```

- **DRAFT**: fully editable
- **READY**: validated and locked for editing
- **OPEN**: accepts votes
- **CLOSED**: results available

Deletion is allowed only in `DRAFT` or `READY`.

---

## Result model

Results must be derived from anonymous ballots only.

The system must not:
- use participation records to reconstruct votes
- expose identity-linked vote data

Single-contest results (`YES_NO`, `RANKED_SINGLE_WINNER`) flow through
`ResultService.getPollResult` → `PollResult`. Linked-offices results (Tranche 2K)
flow through `ResultService.getLinkedElectionResult` → `LinkedElectionResult`
(multi-contest), counted by the pure `LinkedElectionCountingService`. Both paths
read anonymous content only.

---

## Audit model

- Append-only event log
- Records poll lifecycle changes and mutations
- Supports integrity verification and debugging

Typical events:

- `POLL_CREATED`
- `POLL_UPDATED`
- `POLL_CONFIG_UPDATED`
- `POLL_READY`
- `POLL_OPENED`
- `POLL_CLOSED`
- `POLL_DELETED`

---

## GUI design constraints

- No glass pane backgrounds (Bedrock rendering issues)
- Mandatory confirmation step
- All clicks cancelled by default
- Drag and shift-click blocked
- Inventory ownership validated

---

## Key constraints (do not break)

- No identity ↔ ballot linkage
- GUI layer must remain non-authoritative
- Service layer must be the single source of truth
- Verification must not leak vote content through identity
- Results must be deterministic and reproducible

---

## Future evolution

Planned extensions include:

- Multi-winner STV
- Linked Offices election model (multiple contests in one anonymous ballot)
- Expanded audit tooling
- Exportable verification data
- Advanced result visualisation

All future features must preserve the privacy and integrity guarantees described above.
