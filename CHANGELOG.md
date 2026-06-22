# Changelog

All notable changes to ModNVote are documented in this file.

The format is loosely based on Keep a Changelog and follows semantic versioning.

---

# [2.2.0] - 2025

## Added

### Linked Offices Elections

Introduced a new election type supporting multiple interconnected contests within a single election.

Features include:

* Multiple offices in a single election
* Independent candidate pools per office
* Office dependency rules
* Single ballot covering the entire election
* Anonymous ballot storage and verification
* Witness publication support

Example use cases:

* Mayor + Council elections
* President + Cabinet elections
* Chairperson + Committee elections

---

### Instant Runoff Voting (IRV)

Linked Offices contests can use Instant Runoff Voting.

Features:

* Ranked ballots
* Majority winner detection
* Automatic elimination rounds
* Vote transfers
* Exhausted ballot handling
* Deterministic recounts

Recommended for:

* Single-seat executive offices

Examples:

* Mayor
* President
* Chairperson

---

### Single Transferable Vote (STV)

Linked Offices contests can use Single Transferable Vote.

Features:

* Multi-seat ranked elections
* Droop quota
* Gregory surplus transfers
* Elimination transfers
* Exhausted ballot value tracking
* Deterministic recounts

Recommended for:

* Councils
* Boards
* Committees

Examples:

* Town Council
* Senate
* Executive Board

---

### Approval Top-N Elections

Linked Offices contests can use Approval Top-N voting.

Features:

* Multi-seat approval elections
* Fair cutoff tie handling
* Deterministic counting

Seat-deciding ties are reported as unresolved rather than being awarded arbitrarily.

---

### Dependency Rules

Added support for office dependencies.

Current dependency type:

```text
EXCLUDE_WINNERS
```

Example:

```text
Mayor winners cannot also win Council seats
```

Dependencies are evaluated during counting.

---

### Anonymous Multi-Contest Ballots

Added anonymous storage for Linked Offices ballots.

New capabilities:

* Multi-contest ballot persistence
* Canonical ballot hashing
* Commitment verification
* Deterministic ballot reconstruction
* Integrity auditing

---

### Linked Offices Verification

Added:

* Election integrity verification
* Anonymous ballot reconstruction
* Proof-phrase ballot verification
* Deterministic recounting

Verification operates entirely from anonymous ballot data.

---

### Linked Offices Results

Added:

* Multi-office result calculation
* Multi-office result display
* Multi-office witness publication
* STV round reporting
* IRV round reporting
* Dependency reporting
* Tie-resolution reporting

---

### Linked Offices Voting UI

Added:

* Linked Offices vote sessions
* Multi-office voting workflow
* Ranked contest screens
* Approval contest screens
* Review-and-submit workflow
* Proof phrase generation

---

### Linked Offices Administration

Added:

* Election definition import/export
* Definition validation
* Linked Offices poll creation
* Linked Offices configuration UI
* Enhanced poll display output

---

## Changed

### Result Fairness

Approval Top-N elections no longer use candidate order to resolve seat-deciding ties.

Instead:

* Clear winners are elected
* Tied candidates remain unresolved
* Additional resolution is required

This prevents arbitrary election outcomes.

---

### STV Tie Handling

STV seat-deciding ties are never resolved by candidate order.

When a tie would determine a winning seat:

* The election remains incomplete
* The tied candidates are reported
* Administrator or runoff resolution is required

This guarantees that candidate ordering never awards seats.

---

### Poll Display

Improved Linked Offices administration views.

Added:

* Office summaries
* Candidate counts
* Dependency visibility
* Validation information

---

### Documentation

Completely refreshed documentation for 2.2.0.

Updated:

* README
* CURRENT_STATE
* ARCHITECTURE
* Smoke testing documentation
* Example election definitions

---

## Privacy

The privacy model remains unchanged.

ModNVote continues to maintain strict separation between:

```text
Participation records
```

and

```text
Anonymous ballot content
```

New Linked Offices functionality fully preserves:

* Anonymous counting
* Anonymous verification
* Anonymous witness publication
* Non-joinability between identity and vote content

---

## Upgrade Notes

### Upgrading from 2.1.1

Existing polls continue to function unchanged.

No migration of election data is required.

Existing poll types remain supported:

* YES_NO
* RANKED_SINGLE_WINNER

Linked Offices functionality becomes available immediately after upgrading.

---

## Recommended Configuration

For most governance elections:

```text
Mayor: IRV (1 seat)

Council: STV (4+ seats)

Dependency:
  EXCLUDE_WINNERS(Council, Mayor)
```

This configuration provides:

* Majority-supported executive leadership
* Proportional council representation
* No arbitrary tie-breaking
* Anonymous verification and recounting

---

# Previous Releases

Earlier releases focused on:

* Anonymous voting infrastructure
* Participation verification
* Ranked-choice voting
* Integrity auditing
* Witness publication
* Privacy protections

Refer to Git history for detailed implementation history.
