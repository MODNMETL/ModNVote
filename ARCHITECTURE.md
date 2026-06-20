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

Admins can author a linked-offices definition without enabling voting:

- `/modnvote create linked_offices` creates a DRAFT, non-votable poll.
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

`PollType.LINKED_OFFICES` is a reserved, **non-votable** type. It is guarded out
of the vote command, the vote session layer, `ResultService`, and `openPoll`
(which rejects it even when READY), so no accidental voting path can exist until
a later tranche deliberately enables it. Config definitions are accepted only for
linked-offices polls and only while DRAFT; other poll types reject config writes.
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
