# CURRENT STATE

## Version

Current version: **2.2.0**

Status: **Release Candidate**

Build status:

* Linked Offices feature complete
* Automated test suite passing
* Manual smoke test completed successfully
* Ready for production deployment

---

# Overview

ModNVote is a privacy-first election and polling platform for PaperMC servers.

The plugin currently supports:

* Yes/No polls
* Ranked single-winner polls (IRV)
* Linked Offices elections

The system is designed around anonymous ballot storage, integrity verification, proof-based ballot verification, and public witness publication.

---

# Supported Election Types

## YES_NO

Status: Complete

Capabilities:

* Anonymous voting
* Duplicate-vote prevention
* Participation verification
* Ballot proof verification
* Witness publication
* Integrity verification

Production ready.

---

## RANKED_SINGLE_WINNER

Status: Complete

Capabilities:

* Ranked ballots
* IRV counting
* Anonymous ballot storage
* Proof verification
* Integrity verification
* Witness publication

Production ready.

---

## LINKED_OFFICES

Status: Complete

Capabilities:

* Multi-office elections
* JSON definition import
* In-game election editor
* Definition validation
* Office dependencies
* Multi-contest anonymous ballots
* Proof verification
* Integrity verification
* Result calculation
* Witness publication
* Player voting GUI

Production ready.

---

# Linked Offices Features

## Office Types

Supported:

| Method         | Status   |
| -------------- | -------- |
| IRV            | Complete |
| STV            | Complete |
| APPROVAL_TOP_N | Complete |

---

## Dependencies

Supported:

```text
EXCLUDE_WINNERS
```

Example:

```text
Mayor winner excluded from Council
```

Dependencies are applied during counting.

---

## Recommended Governance Configuration

For towns, nations, and community governments:

| Office  | Seats | Method |
| ------- | ----- | ------ |
| Mayor   | 1     | IRV    |
| Council | 2+    | STV    |

This is the recommended ModNVote 2.2.0 governance model.

---

# Privacy Model

The system intentionally separates:

| Component             | Contains                   |
| --------------------- | -------------------------- |
| Participation records | Identity evidence          |
| Anonymous ballots     | Vote content               |
| Contest responses     | Linked-office vote content |
| Audit records         | Lifecycle evidence         |

Results are calculated solely from anonymous ballot content.

The system does not join voter identity to vote content during counting, verification, or witness publication.

---

# Verification Features

## Participation Verification

```text
/modnvote verify participation <pollId>
```

Confirms:

* Participation
* Integrity status

Does not reveal vote content.

---

## Ballot Proof Verification

```text
/modnvote verify ballot <pollId> <proofPhrase>
```

Confirms:

* Ballot existence
* Ballot integrity
* Anonymous ballot contents

Does not reveal voter identity.

---

## Integrity Verification

```text
/modnvote checkpoint <pollId>
```

and

```text
/modnvote verify participation <pollId>
```

allow administrators to verify election integrity without exposing voter identities.

---

# Witness Publication

Supported events:

* Poll opened
* Poll closed
* Result publication
* Integrity checkpoints
* Linked Offices result publication

Publication is best-effort and non-blocking.

Webhook delivery failure never affects election execution.

---

# Result Calculation

## IRV

Supported.

Features:

* Majority detection
* Elimination rounds
* Vote transfer
* Exhausted ballot handling

---

## STV

Supported.

Features:

* Droop quota
* Surplus transfer
* Elimination transfer
* Exhausted ballot handling
* Deterministic counting

For multi-seat representative elections, STV is the recommended counting method.

---

## Approval Top-N

Supported.

Features:

* Approval counting
* Fair cutoff tie handling
* Unresolved seat reporting

Candidate definition order is never used to award seats.

A seat-deciding approval tie produces an incomplete result requiring runoff or administrator resolution.

---

# Tie Handling

ModNVote intentionally avoids using candidate definition order to award seats.

Approval Top-N:

* Seat-deciding ties remain unresolved.

STV:

* Seat-deciding elimination ties remain unresolved.
* Seat-deciding quota ties remain unresolved.

IRV:

* Existing deterministic elimination ordering remains in place for non-seat-deciding elimination ties.

This prevents arbitrary winner selection.

---

# Storage Architecture

Anonymous content is stored in:

```text
anonymous_ballots
anonymous_ballot_preferences
anonymous_ballot_contest_responses
```

Identity tracking remains separate:

```text
participation_records
```

This separation is intentional and foundational to the plugin's privacy guarantees.

---

# Included Example Definitions

Recommended:

```text
docs/examples/pineton-mayor-stv-council.json
```

Alternative:

```text
docs/examples/linked-offices-mayor-council.json
```

The STV example is the recommended starting point for real elections.

---

# Intentionally Unsupported

The following top-level poll types remain reserved and are not implemented:

* RANKED_MULTI_WINNER_STV
* SINGLE_CHOICE
* Other future election models

Use Linked Offices for multi-seat STV elections.

---

# Known Limitations

* Discord embeds may truncate very large election result payloads.
* Command wiring is covered primarily through service-level tests rather than a Bukkit command harness.
* Witness publication is best-effort and does not guarantee webhook delivery.
* Actual close timestamps are not stored separately from scheduled close timestamps.

None of these limitations block production use.

---

# Recommended Next Major Release

Potential future work for 2.3.x:

* Additional dependency types
* Advanced STV configuration options
* Result pagination improvements
* Additional governance templates
* Expanded witness publication formats

No mandatory development work remains for 2.2.0.
