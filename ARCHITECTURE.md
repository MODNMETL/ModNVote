# ARCHITECTURE

## Overview

ModNVote is a privacy-first election platform for PaperMC servers.

The architecture is built around four principles:

1. Anonymous ballots are the source of truth for election results.
2. Participation tracking is separated from vote content.
3. All election outcomes are reproducible from stored anonymous data.
4. Election integrity must be independently verifiable.

The system supports:

* YES_NO polls
* RANKED_SINGLE_WINNER polls
* LINKED_OFFICES elections

---

# High-Level Architecture

```text
Player
  │
  ▼
GUI / Commands
  │
  ▼
Service Layer
  │
  ├── Validation
  ├── Canonicalization
  ├── Hashing
  ├── Persistence
  └── Audit
  │
  ▼
SQLite Storage
  │
  ▼
Results / Verification / Publication
```

The service layer is authoritative.

GUI state is never trusted as election data.

All submitted ballots are validated and reconstructed through the service layer before persistence.

---

# Core Domain Model

## Poll

Represents an election or poll.

Contains:

* Poll type
* Title
* Description
* Lifecycle state
* Timing information
* Linked Offices configuration JSON (where applicable)

Supported types:

```text
YES_NO
RANKED_SINGLE_WINNER
LINKED_OFFICES
```

---

## ElectionDefinition

Used only by LINKED_OFFICES.

Defines:

* Offices
* Candidates
* Counting methods
* Dependencies
* Validation rules

Stored inside:

```text
polls.config_json
```

ElectionDefinition is the single source of truth for Linked Offices configuration.

Legacy poll options are not used.

---

## Anonymous Ballot

Represents persisted vote content.

Anonymous ballots contain:

* Canonical ballot hash
* Commitment hash
* Proof hash
* Submission timestamp

They do not contain:

* UUID
* Username
* Floodgate ID
* IP address
* Participation token

---

# Privacy Architecture

## Identity Path

Identity information exists only for participation control.

Stored in:

```text
participation_records
```

Used for:

* Duplicate-vote prevention
* Participation verification

Identity information never participates in counting.

---

## Content Path

Vote content exists only in anonymous storage.

Stored in:

```text
anonymous_ballots
anonymous_ballot_preferences
anonymous_ballot_contest_responses
```

Used for:

* Counting
* Verification
* Recounting
* Witness publication

Vote content never stores voter identity.

---

## Privacy Boundary

A core design rule is maintained throughout the codebase:

```text
Participation records know who voted.
Anonymous ballots know what was voted.

Neither knows both.
```

Result calculation, proof verification, recounting, and witness publication operate exclusively on anonymous content.

---

# Lifecycle

Every poll follows:

```text
DRAFT
  ↓
READY
  ↓
OPEN
  ↓
CLOSED
```

## DRAFT

Poll configuration may be edited.

## READY

Validation complete.

Ready for voting.

## OPEN

Voting allowed.

## CLOSED

Results and verification available.

---

# Linked Offices Architecture

## Overview

Linked Offices is a multi-office election model.

A single election may contain multiple contests.

Example:

```text
Mayor
Council
Treasurer
```

Each contest defines:

* Counting method
* Seat count
* Candidates
* Dependency rules

---

## Supported Methods

### IRV

Single-winner ranked contest.

Used for:

* Mayor
* President
* Chairperson

---

### STV

Multi-seat ranked contest.

Uses:

* Droop quota
* Surplus transfer
* Elimination transfer

Recommended for:

* Councils
* Boards
* Committees

---

### APPROVAL_TOP_N

Multi-seat approval contest.

Candidates are elected by approval score.

Seat-deciding ties remain unresolved.

---

# Dependency Engine

Current dependency support:

```text
EXCLUDE_WINNERS
```

Example:

```text
Council excludes Mayor winners
```

Execution order:

1. Count Mayor
2. Determine winners
3. Remove winners from Council eligibility
4. Count Council

Dependencies are evaluated at count time.

Voters always see structurally eligible candidates.

---

# Vote Submission Pipeline

```text
Vote GUI
  ↓
Vote State
  ↓
LinkedElectionBallot
  ↓
Validation
  ↓
Canonicalization
  ↓
Hashing
  ↓
Persistence
  ↓
Audit Event
```

A submission is committed only after successful validation.

Failed validation produces no database writes.

---

# Canonicalization

Canonicalization produces deterministic ballot representations.

Purposes:

* Hash stability
* Proof verification
* Recount reproducibility
* Integrity verification

The same logical ballot always produces the same canonical payload.

Current Linked Offices version:

```text
linked_offices_v1
```

---

# Hashing Architecture

Shared hashing functionality is centralized in:

```text
BallotHashingService
```

Responsibilities:

* SHA-256 ballot hashes
* Participation token hashing
* Proof hashes
* Commitment hashes

Hash semantics are shared by:

* BallotService
* LinkedBallotStorageService
* Integrity verification
* Proof verification

This prevents hashing drift.

---

# Counting Architecture

## IRV

Features:

* Majority detection
* Elimination rounds
* Vote transfer
* Exhausted ballots

---

## STV

Features:

* Droop quota
* Gregory surplus transfer
* Elimination transfer
* Exhausted ballot value tracking

Seat-deciding quota ties remain unresolved.

Seat-deciding elimination ties remain unresolved.

Candidate order is never used to award seats.

---

## Approval Top-N

Features:

* Approval counting
* Fair cutoff tie handling

Candidate order is never used to award seats.

Unresolved cutoff ties require runoff or administrator resolution.

---

# Verification Architecture

## Participation Verification

Confirms:

* Participation status
* Integrity status

Does not reveal vote content.

---

## Proof Verification

Uses:

```text
proofPhrase
```

Workflow:

```text
Proof Phrase
  ↓
Proof Hash
  ↓
Anonymous Ballot Lookup
  ↓
Canonical Rebuild
  ↓
Hash Verification
  ↓
Anonymous Content Return
```

No voter identity is revealed.

---

## Integrity Verification

Integrity verification reconstructs election content from storage.

Verification includes:

* Anonymous ballots
* Contest responses
* Canonical payloads
* Ballot hashes
* Commitment data

The goal is deterministic reproducibility.

---

# Result Architecture

Results are calculated exclusively from anonymous content.

Result generation:

```text
Anonymous Ballots
  ↓
Reconstruction
  ↓
Dependency Evaluation
  ↓
Counting
  ↓
Result Model
  ↓
Formatting
  ↓
Publication
```

Identity data is never required.

---

# Witness Publication

Publication is handled separately from election execution.

Supported publications:

* Poll opened
* Poll closed
* Results
* Integrity checkpoints

Publication is:

```text
best-effort
non-blocking
```

Election execution never depends on webhook success.

---

# Testing Strategy

The project emphasizes Bukkit-free testability.

Most business logic exists in pure services and domain models.

Coverage includes:

* Validation
* Canonicalization
* Hashing
* Storage
* Counting
* Dependencies
* Proof verification
* Integrity verification
* Result generation

GUI and command layers remain intentionally thin.

---

# Extension Points

Future development can safely add:

* Additional dependency types
* Additional counting methods
* Alternative publication targets
* Additional verification tooling
* New governance templates

The anonymous-ballot architecture should remain unchanged.

It is the foundational privacy model of the project.

---

# Design Invariants

The following rules should be considered architectural invariants:

1. Anonymous ballots are the source of truth for results.
2. Participation records must not contain vote content.
3. Vote content must not contain voter identity.
4. Results must be reproducible from stored anonymous data.
5. Proof verification must remain identity-free.
6. Witness publication must remain identity-free.
7. Candidate order must never be used to award seats.
8. Hashing semantics must remain centralized.
9. Election definitions are the source of truth for Linked Offices.
10. GUI state must never be authoritative.
