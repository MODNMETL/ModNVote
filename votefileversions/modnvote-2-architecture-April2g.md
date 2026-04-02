# ModNVote 2.0 Architecture

## Purpose

ModNVote 2.0 is a ground-up redesign of the original ModNVote plugin.

Its purpose is to provide a secure, auditable, extensible poll and election platform for Paper servers, with architecture choices made from the start to keep later Folia support achievable.

The first production-grade target is a ranked horse-breed selection poll.  
The second is a higher-trust community election system for a Mayor and a 5-seat Council.

## Primary goals

- Replace the 1.x single-round yes/no design with a true multi-poll architecture
- Store anonymous ballots as the canonical source of truth for vote content
- Store participation records as the canonical source of truth for voter eligibility and inclusion
- Support multiple vote types from a shared core
- Provide deterministic recounting
- Provide tamper-evident audit trails
- Publish integrity checkpoints to external witnesses such as Discord webhooks
- Keep Java and Bedrock voting flows aligned conceptually
- Design platform abstractions so Paper works now and Folia adaptation remains practical later

## Non-goals for the first 2.0 milestone

- Full STV implementation in the very first scaffold
- Full Mayor + Council combined election delivery in the first milestone
- Backward-compatible migration of the old 1.x schema into the new model
- Forcing identical renderers for every Java and Bedrock interaction before usability testing

## Core principles

### 1. Anonymous ballots are the source of truth for vote content

Tallies, winners, recounts, and audit summaries are derived from stored anonymous ballots.

Voter eligibility and inclusion are tracked separately via participation records.

### 1b. Identity and vote content must be separated

At no point may persistent storage allow identity-to-vote reconstruction.

Participation (identity-aware) and ballot content (identity-free) are stored separately and cannot be joined to reveal how a player voted.

### 2. Validation is server-side
The GUI helps the player understand their vote, but the server always re-validates the ballot before commit.

### 3. Auditability is first-class
Every poll and ballot lifecycle event must be representable in an append-only audit chain.

### 4. External witness publication matters
A local seal alone is not enough against a privileged hostile admin. Checkpoints must be publishable to external systems.

### 5. Platform work must be isolated
Business logic must not depend on brittle “main thread only” assumptions. Platform scheduling and player-bound execution are abstracted.

### 6. Privacy is enforced structurally
This is not optional — any design that allows identity-to-vote linkage is invalid.
The system must ensure that database access alone cannot reveal how a player voted.

See: `privacy-model.md`

## Supported vote engines

### Phase 1
- Yes / No
- Single-choice plurality
- Ranked single-winner (IRV-style)

### Phase 2
- Ranked multi-winner STV
- Combined executive + council election workflow

## High-level package plan

- `api` — public enums and stable shared concepts
- `domain` — poll, option, ballot, preference models
- `platform` — Paper/Folia-aware scheduling abstractions
- `storage` — DAOs and schema management
- `service` — poll lifecycle, ballot submission, recount, verification
- `integrity` — canonicalization, hashing, checkpointing, verification
- `ui` — vote session state and renderers
- `counting` — vote engine implementations
- `commands` — admin and player command entrypoints

## Initial delivery target

ModNVote 2.0.0 should ship with:
- new poll architecture
- new ballot-first data model
- ranked single-winner support
- horse-breed poll support
- audit chain foundation
- Discord checkpoint publication foundation
- Java GUI draft + confirmation flow
- recount and verification commands

## Folia direction

ModNVote 2.0 is being designed with a platform abstraction layer so that:
- Paper can be supported immediately
- Folia support can be added without rewriting domain logic
- player-bound actions can later be routed through entity-aware schedulers
- global tasks can later be routed through appropriate Folia-safe scheduling

Folia support will only be declared when the implementation is genuinely ready.

## Voting UX and Interaction Model (Critical Design)

The voting experience is a core part of ModNVote 2.0 and must be treated as a first-class system, not an afterthought.

### Design goals

- Ensure players clearly understand what they are voting for
- Ensure players clearly understand what they are about to submit
- Prevent accidental or ambiguous ballot submissions
- Provide a consistent interaction model across Java and Bedrock players
- Allow flexible poll configuration while maintaining a predictable UX

---

## Unified GUI philosophy

ModNVote 2.0 will use a unified conceptual GUI design for both Java and Bedrock players.

- Java uses inventory-based GUIs
- Bedrock (via Geyser/Floodgate) will map these interactions into compatible UI flows

The goal is NOT identical rendering, but:
- identical decision flow
- identical vote semantics
- identical confirmation guarantees

---

## Selection models

### 1. Single-choice voting

- Player selects exactly one option
- Selection highlights chosen option
- "CAST YOUR VOTE" button remains disabled until selection is valid

---

### 2. Ranked voting (core use case)

Players must rank options in order of preference.

Interaction model:

- Each selectable option is represented as an icon
- Player selects options one-by-one in order:
    - first click = rank 1
    - second click = rank 2
    - etc.
- The system visually indicates ranking order

Constraints:
- Ranking must respect `max_rankings`
- Partial ranking allowed only if configured

---

## Option representation

Each poll option supports:

- icon (Material or player head)
- display name
- description (shown on hover / tooltip)
- optional metadata

Examples:
- Horse breed → icon + breed description
- Election candidate → player head + manifesto summary

---

## Vote confirmation flow (MANDATORY)

This is a critical integrity feature.

### Step 1 — Selection phase

- Player builds their selection/ranking
- "CAST YOUR VOTE!" button remains:
    - RED (disabled) until valid
    - GREEN (enabled) when valid

Tooltip MUST show:
- exactly what their vote currently represents

---

### Step 2 — Confirmation screen

After pressing "CAST YOUR VOTE":

A second GUI MUST appear:

- GREEN button: "Yes! Commit my vote!"
- RED button: "No! I've changed my mind!"

Both buttons MUST display:
- the full vote summary (ranking or selection)

This ensures:
- no accidental submissions
- full user awareness

---

## Interaction safety

The GUI must enforce:

- no item dragging into player inventory
- no item duplication exploits during lag
- no partial interaction bypass

All vote validation MUST occur server-side before commit.

---

## Accessibility considerations

- Clear color use (red/green states)
- readable tooltips
- minimal reliance on chat commands
- consistent layout across poll types

---

## Extensibility

The GUI system must support:

- different poll types (yes/no, single choice, ranked, STV)
- dynamic option counts
- future election-specific flows

---

## Design priority

The voting UX is NOT secondary.

It is part of the trust model:

- unclear UI → invalid ballots
- confusing UX → distrust in results

Therefore:
- clarity > compactness
- correctness > speed