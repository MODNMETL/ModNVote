# Linked Offices Elections

## Overview

Linked Offices is ModNVote's multi-office election system.

It allows a community to run multiple connected elections through a single voter experience while preserving the plugin's privacy and auditability guarantees.

Typical examples include:

* Mayor + Council
* President + Senate
* Chairperson + Committee
* Governor + Assembly

A voter submits a single ballot containing responses for every office in the election.

Each office may use a different counting method.

Example:

| Office  | Seats | Method |
| ------- | ----- | ------ |
| Mayor   | 1     | IRV    |
| Council | 4     | STV    |

The winner of one office may optionally be excluded from another office through election dependencies.

---

# Key Concepts

## Office

An office is a position or group of positions being elected.

Examples:

* Mayor
* Council
* Treasurer
* Board Member

Each office has:

* A display name
* A counting method
* A seat count
* A candidate list
* Optional dependency rules

---

## Candidate

Candidates are defined once and may be eligible for multiple offices.

Example:

```json
"vradow": {
  "displayName": "Vradow",
  "eligibleFor": ["mayor", "council"]
}
```

This allows a candidate to stand for both Mayor and Council.

---

## Dependency Rules

Linked Offices currently supports:

```text
EXCLUDE_WINNERS
```

Example:

```json
"excludeWinnersFrom": ["mayor"]
```

Applied to the Council office.

Meaning:

1. Mayor is counted first.
2. Mayor winners are determined.
3. Mayor winners are removed from Council eligibility.
4. Council is counted.

This prevents the same candidate from occupying both positions.

Dependencies are applied during counting, not voting.

Voters still see all eligible candidates when casting ballots.

---

# Supported Counting Methods

## IRV (Instant Runoff Voting)

Use for:

* Mayor
* President
* Chairperson
* Any single-seat office

Voters rank candidates in order of preference.

Example:

```text
1. Space
2. Vradow
3. Rooster
```

Counting proceeds through elimination rounds until a winner is found.

Recommended seat count:

```text
1
```

---

## STV (Single Transferable Vote)

Use for:

* Councils
* Committees
* Boards
* Representative multi-seat bodies

Voters rank candidates.

Example:

```text
1. Katie
2. Mort
3. Metta
4. Space
5. Rooster
```

STV uses:

* Droop quota
* Surplus transfers
* Elimination transfers
* Exhausted ballot handling

Benefits:

* More proportional outcomes
* Reduced vote splitting
* Fairer multi-seat representation

Recommended whenever:

```text
Seats > 1
```

---

## Approval Top-N

Use when a simpler election is desired.

Voters approve candidates rather than rank them.

Example:

```text
✓ Katie
✓ Mort
✓ Space
```

Candidates with the highest approval totals win.

If a tie crosses the final seat boundary:

* No arbitrary winner is chosen
* Candidate order is not used
* The contest is marked incomplete
* Runoff or administrator resolution is required

---

# Recommended Mayor + Council Configuration

For most towns and nations:

| Office  | Seats | Method |
| ------- | ----- | ------ |
| Mayor   | 1     | IRV    |
| Council | 4     | STV    |

Dependency:

```text
Council excludes winners from Mayor
```

Benefits:

* Strong mayor mandate
* Representative council
* No double office holding
* Fair tie handling

This is the recommended ModNVote 2.2.0 configuration.

---

# Creating a Linked Offices Election

## Step 1: Create

```text
/modnvote create linked_offices
```

Record the poll ID.

---

## Step 2: Set Title and Description

Configure the election title and description using the normal poll editing workflow.

---

## Step 3: Import a Definition

Place a definition file inside:

```text
plugins/ModNVote/definitions/
```

Import:

```text
/modnvote config <pollId> import <file>
```

Example:

```text
/modnvote config 12 import pineton-mayor-stv-council.json
```

---

## Step 4: Validate

```text
/modnvote validate-definition <pollId>
```

Validation must succeed before the poll can become READY.

---

## Step 5: Review

```text
/modnvote show <pollId>
```

Review:

* Office count
* Candidate count
* Dependencies
* Counting methods
* Seat counts

---

## Step 6: Mark Ready

```text
/modnvote ready <pollId>
```

---

## Step 7: Open Voting

```text
/modnvote open <pollId>
```

---

# Voter Experience

Players vote using:

```text
/modnvote vote <pollId>
```

The workflow is:

1. Open election
2. Select an office
3. Complete the office ballot
4. Repeat for remaining offices
5. Review the full ballot
6. Submit once

Upon submission the voter receives:

* Participation receipt
* Ballot hash
* Proof phrase

The proof phrase should be stored securely.

Anyone possessing the phrase can verify the anonymous ballot associated with it.

---

# Results

View results:

```text
/modnvote result <pollId>
```

Results include:

* Winners
* Candidate tallies
* Dependency exclusions
* IRV round breakdowns
* STV round breakdowns
* Quota information
* Election issues
* Unresolved ties

Results are calculated solely from anonymous ballot content.

---

# Verification

## Verify Participation

```text
/modnvote verify participation <pollId>
```

Confirms participation and integrity status.

Does not reveal vote content.

---

## Verify Ballot

```text
/modnvote verify ballot <pollId> <proofPhrase>
```

Verifies:

* Ballot existence
* Ballot integrity
* Anonymous ballot content

Does not reveal voter identity.

---

# Privacy Model

ModNVote deliberately separates:

| Component             | Purpose                    |
| --------------------- | -------------------------- |
| Anonymous ballots     | Vote content               |
| Contest responses     | Per-office selections      |
| Participation records | Duplicate prevention       |
| Audit records         | Verification and integrity |

The system does not calculate results using voter identities.

Identity information and ballot content are intentionally stored separately.

Linked Offices preserves the same privacy guarantees as every other ModNVote election type.

---

# Example Definitions

Recommended STV configuration:

```text
docs/examples/pineton-mayor-stv-council.json
```

Alternative Approval Top-N configuration:

```text
docs/examples/linked-offices-mayor-council.json
```

For real governance elections, STV is recommended.
