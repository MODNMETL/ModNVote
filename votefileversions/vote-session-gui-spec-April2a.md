Perfect call—that’s exactly the right way to lock this in as a canonical reference before we move back to the ballot core.

Here’s a clean, production-ready **Markdown design spec** you can drop straight into your repo (I’d suggest something like `docs/vote-session-gui-spec.md`).

---

# 📄 ModNVote 2.0 — Vote Session & GUI Design Specification

## Purpose

This document defines the **canonical voting interaction model** and **GUI/session architecture** for ModNVote 2.0.

It ensures:

* consistent voting behaviour across Java and Bedrock players
* clear, unambiguous user experience
* strong alignment with the plugin’s **audit-first trust model**
* separation between **UI rendering** and **ballot integrity logic**

---

## Core Design Principles

### 1. Ballots are the source of truth

The GUI never commits data directly.
All submissions must go through the service layer.

### 2. Validation is server-side

The GUI may guide the user, but final validation is always performed server-side.

### 3. UX is part of the trust model

Confusing or ambiguous UX undermines trust.
Clarity and confirmation are mandatory.

### 4. One interaction model, multiple renderers

* Java: inventory GUI
* Bedrock: mapped equivalent (via Geyser/Floodgate)

The goal is:

* identical decision flow
* identical vote semantics
* identical confirmation guarantees

Rendering may differ.

---

## Supported Interaction Types (Phase 1)

* Yes / No voting
* Single-choice voting
* Ranked single-winner voting (IRV-style)

---

## Voting Flow (Canonical)

### Phase 1 — Selection

Player opens voting interface.

They can:

* view poll title and description
* inspect options via icons + tooltips
* make selections

#### Ranked voting behaviour

* first click → rank 1
* second click → rank 2
* etc.

#### Visual requirements

* selected options show rank clearly
* unselected options remain neutral
* hover tooltip shows option details

---

### Phase 2 — Validation state

“CAST YOUR VOTE!” button:

* 🔴 RED (disabled) when invalid
* 🟢 GREEN (enabled) when valid

Tooltip MUST show:

* exact current vote summary

---

### Phase 3 — Confirmation (MANDATORY)

After clicking cast:

A second GUI MUST appear:

* 🟢 Green: **“Yes! Commit my vote!”**
* 🔴 Red: **“No! I've changed my mind!”**

Both MUST display:

* full vote summary

This prevents accidental or unclear submissions.

---

### Phase 4 — Commit

On confirmation:

1. GUI triggers service call
2. Service revalidates everything
3. Ballot is committed atomically
4. Audit event is recorded

---

## Session Architecture

### `VoteSession`

Represents one active voting interaction.

#### Fields

* `UUID playerUuid`
* `long pollId`
* `PollType pollType`
* `VoteScreen currentScreen`
* `List<VoteOptionView> options`
* `List<Long> rankedOptionIds`
* `Long singleSelectedOptionId`
* `Instant createdAt`
* `Instant lastInteractionAt`

#### Responsibilities

* assign/remove ranks
* track selection state
* generate ballot summary
* determine validity
* track current UI screen

---

### `VoteScreen`

Enum:

```
SELECTION
CONFIRMATION
```

---

### `VoteOptionView`

Renderer-friendly option state.

Fields:

* `long optionId`
* `String optionKey`
* `String displayName`
* `String description`
* `int displayOrder`
* `boolean selected`
* `Integer assignedRank`
* `OptionIcon optionIcon`

---

### `OptionIcon`

Fields:

* `OptionIconType iconType`
* `String iconValue`

---

### `OptionIconType`

```
MATERIAL
PLAYER_HEAD
```

---

## Session Manager

### `VoteSessionManager`

Responsibilities:

* create sessions
* retrieve sessions by player UUID
* replace existing sessions
* remove sessions on completion/cancel
* expire stale sessions

Storage:

```
Map<UUID, VoteSession>
```

---

## Ranked Interaction Rules

### Selection

* clicking unselected option → assign next rank

### Unselection

* clicking selected option → remove it
* later ranks shift down automatically

### Reset

* reset control clears all selections

### Validity

* must respect `max_rankings`
* partial ranking allowed only if configured

---

## Validation Model

### Session-level (UI)

Used for:

* enabling/disabling cast button
* updating summaries

Examples:

* at least one selection
* ranking within allowed limits

---

### Service-level (authoritative)

Performed on commit:

* poll exists
* poll is OPEN
* options belong to poll
* rules are satisfied
* identity policy enforced

---

## Ballot Summary

### `BallotSummaryFormatter`

Generates consistent output for:

* cast button tooltip
* confirmation screen
* feedback messages

#### Examples

**Single choice:**

```
Your vote: Arabian
```

**Ranked:**

```
Your ranking:
1. Arabian
2. Friesian
3. Mustang
```

---

## Java GUI Layout (Initial)

### Recommended size

* 54-slot inventory (6 rows)

### Structure

#### Top

* poll info
* instructions

#### Middle

* option icons grid

#### Bottom

* reset button
* cancel button
* summary item
* cast vote button

---

### Confirmation GUI

* simplified layout (27–45 slots)
* summary in center
* red cancel
* green confirm

---

## Interaction Safety (MANDATORY)

The GUI must:

* cancel all clicks by default
* cancel drag events
* block shift-clicks
* block hotbar swaps
* verify inventory ownership
* prevent item movement into player inventory

No client-side assumptions are trusted.

---

## Renderer Architecture

### `VoteRenderer`

Interface:

* openSelection(...)
* openConfirmation(...)
* refresh(...)

---

### `JavaInventoryVoteRenderer`

Responsibilities:

* build inventory layouts
* render options and ranks
* display summary and controls

---

### `VoteGuiListener`

Handles:

* click events
* drag events
* inventory close
* player quit

Must NOT contain:

* business logic
* validation rules
* persistence logic

---

## Service Integration

GUI must call:

* ballot submission service methods

Example (conceptual):

```
submitRankedBallot(player, pollId, rankedOptionIds)
```

Service handles:

* validation
* canonicalization
* hashing
* persistence
* audit event insertion

---

## Bedrock Strategy

* reuse same session model
* reuse same validation logic
* reuse same summary logic
* reuse same confirmation model

Renderer may differ if required.

---

## Implementation Order

### Step 1

Ballot DAO + submission service

### Step 2

VoteSession + VoteSessionManager

### Step 3

Summary formatter + validation helpers

### Step 4

Java inventory renderer

### Step 5

GUI listener

### Step 6

Command to open voting UI

### Step 7

End-to-end testing

---

## Non-Negotiable Rules

* no GUI-only validation
* no direct DB writes from UI
* no vote without confirmation
* no partial/invalid commits
* no draggable GUI items
* session model before renderer
* consistent behaviour across platforms

---

## Scope Note

This spec covers:

* Yes/No
* single-choice
* ranked single-winner polls

It does NOT yet cover:

* STV multi-winner flows
* combined election workflows

Those will extend this system later.

---

## Final Note

The voting interface is not just UI—it is part of the **trust and integrity model** of ModNVote.

Clarity, confirmation, and correctness must always take priority over speed or compactness.

