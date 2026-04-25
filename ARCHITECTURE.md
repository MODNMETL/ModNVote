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
- Expanded audit tooling
- Exportable verification data
- Advanced result visualisation

All future features must preserve the privacy and integrity guarantees described above.
