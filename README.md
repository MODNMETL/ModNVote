# ModNVote

**Privacy-first, auditable community voting for PaperMC servers**

ModNVote is a Minecraft voting and polling plugin for communities that want voting to be easy for players, practical for admins, and verifiable after the fact.

ModNVote 2.2.0 adds **Linked Offices** elections: a full multi-office election model for scenarios such as a Mayor election and a Council election run together from one voter flow. It also continues to support Yes/No polls and ranked single-winner polls.

Developed by [MODN METL LTD](https://modnmetl.com).

![CI](https://github.com/MODNMETL/ModNVote/actions/workflows/ci.yml/badge.svg)
![Java](https://img.shields.io/badge/Java-21-007396)
![Paper](https://img.shields.io/badge/Paper-1.21.x-blue)
![License: MIT](https://img.shields.io/badge/License-MIT-green)

---

## Current status

ModNVote 2.2.0 is the current feature-complete Linked Offices release.

Supported election types:

- **Yes/No polls**
- **Ranked single-winner polls** using IRV-style counting
- **Linked Offices elections** with multiple offices in one election
- **Linked Offices IRV contests**
- **Linked Offices STV contests**
- **Linked Offices Approval Top-N contests**

Linked Offices is now supported end to end:

- Admin creation
- JSON import
- In-game definition editor
- Definition validation
- Ready/open lifecycle
- Player voting GUI
- Anonymous multi-contest ballot storage
- Duplicate-vote prevention
- Proof phrase verification
- Integrity verification
- Result calculation
- Witness publication
- Manual smoke-tested Mayor + Council flow

---

## What Linked Offices is for

Linked Offices allows a community to run an election containing more than one office while still giving each voter one clear voting session.

Example:

- **Mayor**: 1 seat, elected by IRV
- **Council**: 4 seats, elected by STV
- **Dependency**: the Mayor winner is excluded from winning a Council seat

This solves the common governance problem where a candidate may stand for Mayor, but if they win Mayor they should not also occupy a Council seat.

The recommended configuration for a Mayor + Council election is:

| Office | Seats | Recommended method |
|---|---:|---|
| Mayor | 1 | IRV |
| Council | 2+ | STV |

Approval Top-N remains supported, but STV is recommended for representative multi-seat bodies such as councils, boards, and committees.

---

## Core privacy model

ModNVote is built around one central rule:

> Anonymous ballots are the source of truth for vote content.

The system deliberately separates:

| Data type | Purpose |
|---|---|
| Anonymous ballots | Store vote content and drive results |
| Anonymous ballot contest responses | Store per-office Linked Offices ballot content |
| Participation records | Track who has participated and prevent duplicate voting |
| Audit records | Provide lifecycle and integrity evidence |
| Proof phrases | Let a voter verify a ballot without revealing identity links |

This means:

- Results are calculated from anonymous ballot content only.
- Participation records do not contain vote content.
- Vote content and voter identity are not stored together.
- The system does not join `participation_records` to vote content for results.
- `/modnvote verify participation` confirms participation and integrity without revealing a vote.
- `/modnvote verify ballot` uses a proof phrase as a bearer-token ballot verification mechanism.
- GUI/session state is not authoritative; the service layer validates and persists final submissions.
- Witness publication must not include player names, UUIDs, IP addresses, Floodgate ids, proof phrases, participation receipts, or per-player vote content.

---

## Supported poll types

### Yes/No polls

Yes/No polls use canonical Yes and No options managed by the service layer.

Create one with:

```text
/modnvote create yes_no
```

This creates a DRAFT Yes/No poll and opens the Poll Builder GUI.

### Ranked single-winner polls

Ranked single-winner polls let players rank options in preference order. Results are calculated from anonymous ranked ballots using IRV-style transfer rounds.

Create one with:

```text
/modnvote create ranked_single_winner <optionCount>
```

Example:

```text
/modnvote create ranked_single_winner 5
```

The Poll Builder GUI lets admins configure:

- title
- description
- option names
- option descriptions
- whether partial rankings are allowed
- maximum rankings

### Linked Offices elections

Linked Offices polls are created with:

```text
/modnvote create linked_offices
```

A Linked Offices poll does not use legacy `poll_options`. Its candidates, offices, counting methods, and dependencies are defined in an `ElectionDefinition` stored in the poll's `config_json`.

Admins can configure the definition by:

- importing JSON with `/modnvote config <pollId> import <file>`
- setting inline JSON with `/modnvote config <pollId> set <json>`
- editing visually with `/modnvote edit-definition <pollId>`

Linked Offices supports these office-level counting methods:

| Method | Use case | Voter action |
|---|---|---|
| `IRV` | Single-winner offices such as Mayor | Rank candidates |
| `STV` | Multi-seat representative offices such as Council | Rank candidates |
| `APPROVAL_TOP_N` | Simpler multi-seat selection | Approve up to the configured maximum |

---

## Recommended Mayor + Council setup

For a Mayor and four Council seats, use:

```text
Mayor: IRV, 1 seat
Council: STV, 4 seats
Dependency: Council excludes winners from Mayor
```

This gives voters a simple flow:

1. Rank Mayor candidates.
2. Rank Council candidates.
3. Review the whole ballot.
4. Submit once.

At count time:

1. Mayor is counted first.
2. The Mayor winner is elected.
3. The Mayor winner is excluded from the Council contest.
4. Council is counted by STV from the remaining eligible candidates.

This avoids electing the same player to both Mayor and Council.

A ready-to-adapt example is provided at:

```text
docs/examples/pineton-mayor-stv-council.json
```

For older or simpler workflows, an Approval Top-N example is also available:

```text
docs/examples/linked-offices-mayor-council.json
```

For live governance elections, prefer the STV example for multi-seat councils.

---

## Linked Offices JSON definitions

Definition files are imported from:

```text
plugins/ModNVote/definitions/
```

Import with:

```text
/modnvote config <pollId> import <fileName>
```

Example:

```text
/modnvote config 12 import pineton-mayor-stv-council.json
```

A Linked Offices definition contains:

- `model`: always `LINKED_OFFICES`
- `offices`: the offices being elected
- `candidateDefinitions`: candidate display names and eligibility
- optional office dependencies such as `excludeWinnersFrom`

Example structure:

```json
{
  "model": "LINKED_OFFICES",
  "offices": {
    "mayor": {
      "displayName": "Pineton Mayor",
      "method": "IRV",
      "seats": 1,
      "allowAbstain": false,
      "candidates": ["vradow", "space", "rooster"]
    },
    "council": {
      "displayName": "Pineton Council",
      "method": "STV",
      "seats": 4,
      "allowAbstain": false,
      "candidates": ["vradow", "katie", "space", "rooster", "metta", "mort", "fitzy"],
      "excludeWinnersFrom": ["mayor"]
    }
  },
  "candidateDefinitions": {
    "vradow": {
      "displayName": "Vradow",
      "eligibleFor": ["mayor", "council"]
    }
  }
}
```

The full example file includes every candidate definition.

---

## Linked Offices admin workflow

### 1. Create the poll

```text
/modnvote create linked_offices
```

Record the poll id.

### 2. Set title and description

Use the normal edit flow for poll title and description. For example:

```text
/modnvote edit <pollId>
```

or the available title/description edit commands in your admin workflow.

### 3. Import or edit the election definition

Import JSON:

```text
/modnvote config <pollId> import pineton-mayor-stv-council.json
```

Or open the definition editor:

```text
/modnvote edit-definition <pollId>
```

The editor lets admins manage:

- offices
- office display names
- counting methods
- seats
- candidates
- candidate eligibility
- `EXCLUDE_WINNERS` dependencies
- validation and save

The GUI editor writes back through the same validated service path as JSON import.

### 4. Validate

```text
/modnvote validate-definition <pollId>
```

The definition must be valid before the poll can be marked READY.

### 5. Review

```text
/modnvote show <pollId>
```

For Linked Offices polls this shows definition status, office count, candidate count, dependency count, and each office's method and seats.

### 6. Mark READY

```text
/modnvote ready <pollId>
```

### 7. Open voting

```text
/modnvote open <pollId>
```

### 8. Voters cast ballots

```text
/modnvote vote <pollId>
```

### 9. Close and calculate results

```text
/modnvote close <pollId>
```

### 10. Show results

```text
/modnvote result <pollId>
```

### 11. Republish results if needed

```text
/modnvote publishresult <pollId>
```

### 12. Verify integrity

```text
/modnvote verify participation <pollId>
```

---

## Voter experience

Players vote with:

```text
/modnvote vote <pollId>
```

For Linked Offices:

1. The first screen lists every office.
2. The voter opens each office.
3. IRV and STV offices ask the voter to rank candidates.
4. Approval Top-N offices ask the voter to approve up to the configured maximum.
5. Required offices must be completed before the final submit button is enabled.
6. The voter reviews all office responses.
7. The voter submits once.
8. The voter receives:
    - ballot hash
    - participation receipt
    - private proof phrase

The proof phrase should be treated like a bearer token. Anyone with the phrase can verify the anonymous ballot content associated with it.

---

## Counting methods

### IRV

IRV is used for single-winner ranked offices.

The count proceeds through rounds:

1. Count first preferences.
2. If a candidate has a majority, they win.
3. Otherwise eliminate the lowest candidate.
4. Transfer ballots to next continuing preferences.
5. Repeat until a winner is found.

IRV round breakdowns are shown in results and witness publication.

### STV

STV is used for multi-seat ranked offices.

STV uses:

- ranked ballots
- Droop quota
- surplus transfer
- lowest-candidate elimination
- exhausted ballot reporting
- deterministic round summaries

STV is recommended for councils and other representative multi-seat bodies because it is better suited to proportional representation than Approval Top-N.

For Linked Offices dependencies, exclusions are applied before the dependent office is counted. For example, if the Mayor winner is excluded from Council, that candidate is removed from the Council candidate set before the Council STV count begins.

### Approval Top-N

Approval Top-N lets voters approve up to a configured maximum number of candidates.

Candidates are elected by approval score descending.

If a tie crosses the final seat cutoff, candidate definition order is **not** used to pick winners. Instead:

- clearly elected candidates above the cutoff remain elected;
- the tied group at the cutoff is listed;
- the affected seats are marked unresolved;
- a runoff or administrator-defined resolution is required.

This prevents arbitrary candidate-order tie-breaking.

---

## Dependencies

Linked Offices currently supports:

```text
EXCLUDE_WINNERS
```

In JSON this is written as:

```json
"excludeWinnersFrom": ["mayor"]
```

on the dependent office.

Example:

```text
Council excludes winners from Mayor
```

Meaning:

- Mayor is counted first.
- The Mayor winner is elected Mayor.
- That winner is excluded from the Council contest.
- Council is then counted without that candidate.

Dependencies are applied at count time, not vote time. Voters still see every candidate who is structurally eligible for each office.

---

## Results

Show results with:

```text
/modnvote result <pollId>
```

Results are calculated from anonymous ballot content only.

For Linked Offices results, output includes:

- each office
- method and seats
- winners
- candidate tallies
- dependency exclusions
- IRV round breakdowns where applicable
- STV quota and rounds where applicable
- exhausted ballots or exhausted value
- unresolved seats or election issues where applicable

If the result is incomplete because of an unresolved tie, the result remains visible and publishable, but the unresolved office must be settled by runoff or administrator-defined election rules.

---

## Verification commands

### Show polls you participated in

```text
/modnvote mypolls
```

### Verify participation and integrity

```text
/modnvote verify participation <pollId>
```

This confirms whether the player participated in a poll and reports integrity status. It does not reveal vote content.

### Verify a ballot proof phrase

```text
/modnvote verify ballot <pollId> <proofPhrase>
```

This checks whether a proof phrase matches a stored anonymous ballot and verifies the ballot's commitment data.

For Yes/No and ranked single-winner polls, it reports the verified selection or ranking.

For Linked Offices polls, it reports the verified per-office anonymous responses.

On a non-matching phrase or failed integrity check, it reports an identity-free failure and shows no ballot content.

---

## Witness publication

ModNVote can publish public witness events to configured Discord-compatible webhooks.

Supported witness events include:

- poll opened
- poll closed
- closed result summary
- Linked Offices multi-contest result summary
- automatic integrity checkpoints
- manual integrity checkpoints
- manual closed-result republication

Webhook delivery is best-effort and non-blocking. A failed webhook does not cancel voting, poll opening, poll closing, result calculation, or persistence.

Configure in `config.yml`:

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

Never commit real webhook URLs to source control.

---

## Command alias

All `/modnvote` commands can also be used via:

```text
/poll
```

Examples:

```text
/poll status
/poll vote <pollId>
/poll result <pollId>
/poll publishresult <pollId>
```

---

## Admin command reference

All commands below may use either `/modnvote` or `/poll`.

Common admin commands:

```text
/modnvote guide
/modnvote create yes_no
/modnvote create ranked_single_winner <optionCount>
/modnvote create linked_offices
/modnvote edit <draftPollId>
/modnvote edit-definition <linkedPollId>
/modnvote config <linkedPollId> set <json>
/modnvote config <linkedPollId> import <file>
/modnvote validate-definition <linkedPollId>
/modnvote clone <sourcePollId>
/modnvote list
/modnvote show <pollId>
/modnvote ready <pollId>
/modnvote open <pollId>
/modnvote close <pollId>
/modnvote result <pollId>
/modnvote publishresult <pollId>
/modnvote checkpoint <pollId>
/modnvote delete <pollId>
```

Player-facing commands:

```text
/modnvote vote <pollId>
/modnvote mypolls
/modnvote verify participation <pollId>
/modnvote verify ballot <pollId> <proofPhrase>
```

Utility commands:

```text
/modnvote status
/modnvote reload
```

---

## Permissions

Exact permission defaults are defined in `plugin.yml`.

Common permissions include:

| Permission | Purpose |
|---|---|
| `modnvote.admin.poll.create` | Create, clone, edit, inspect, checkpoint, and manage draft polls |
| `modnvote.admin.poll.list` | List polls |
| `modnvote.admin.poll.open` | Open polls for voting |
| `modnvote.admin.poll.close` | Close polls and republish closed poll results |
| `modnvote.admin.reload` | Reload plugin configuration |
| `modnvote.verify` | Use verification commands |
| `modnvote.testvote` | Access test vote tooling where enabled |

---

## Installation

### Requirements

- Paper 1.21.x
- Java 21

### Fresh install

1. Stop the server.
2. Install the ModNVote jar into `/plugins/`.
3. Start the server.
4. Configure permissions.
5. Configure witness publication if desired.
6. Create and test a poll.

### Upgrading from ModNVote 2.1.1

ModNVote 2.2.0 is intended to be compatible with existing ModNVote 2.1.1 installs.

The 2.2.0 Linked Offices storage adds a new anonymous contest-response table for multi-office ballots. Existing Yes/No and ranked single-winner ballot formats remain unchanged.

Recommended upgrade process:

1. Stop the server.
2. Back up the database and config.
3. Replace the old jar with the 2.2.0 jar.
4. Start the server.
5. Check startup logs for schema errors.
6. Verify old polls still list and display.
7. Verify old closed results still match.
8. Verify old proof phrases still work.
9. Create a test Linked Offices poll before using it for live governance.

There is no supported upgrade path from legacy ModNVote 1.x databases.

---

## Build requirements

Build locally:

```text
./gradlew clean build
```

On Windows:

```text
gradlew.bat clean build
```

The Java source/target level should remain Java 21 unless intentionally changed.

The release jar is produced by the Shadow plugin under:

```text
build/libs/
```

---

## Recommended smoke test

After significant changes, test a representative flow:

```text
/modnvote status
/modnvote create yes_no
/modnvote create ranked_single_winner 3
/modnvote create linked_offices
/modnvote config <linkedPollId> import pineton-mayor-stv-council.json
/modnvote validate-definition <linkedPollId>
/modnvote show <linkedPollId>
/modnvote ready <linkedPollId>
/modnvote open <linkedPollId>
/modnvote vote <linkedPollId>
/modnvote close <linkedPollId>
/modnvote result <linkedPollId>
/modnvote publishresult <linkedPollId>
/modnvote verify participation <linkedPollId>
/modnvote verify ballot <linkedPollId> <proofPhrase>
```

Also verify alias behavior:

```text
/poll status
/poll vote <pollId>
/poll result <pollId>
```

For the full Linked Offices release validation, use:

```text
docs/release/2.2.0-linked-offices-smoke-test.md
```

---

## Architecture overview

ModNVote uses a service-authoritative design.

### Command layer

Responsible for:

- parsing user input
- checking permissions
- displaying formatted output
- delegating business logic to services

Not responsible for:

- writing ballots directly
- writing lifecycle state directly
- calculating results independently
- bypassing validation

### GUI/session layer

Responsible for:

- rendering inventories
- holding temporary player interaction state
- capturing chat input for builder fields
- delegating all mutations to services

Not responsible for:

- writing ballots directly
- writing lifecycle state directly
- calculating results
- bypassing validation

### Service layer

Responsible for:

- poll creation
- poll validation
- lifecycle transitions
- ballot submission
- result calculation
- integrity verification
- audit enforcement

### Persistence layer

Responsible for:

- poll definitions
- poll options for non-linked polls
- anonymous ballots
- anonymous ballot preferences
- anonymous linked-office contest responses
- participation records
- audit records

### Presentation/publication layer

Responsible for:

- formatting in-game output
- formatting result output
- formatting witness output
- publishing privacy-safe webhook events

---

## Development notes

- Keep `.gradle/` out of version control.
- Do not commit local Gradle cache files.
- GUI features should remain Folia-aware through `ModNScheduler`.
- Results must always come from anonymous ballots only.
- Participation verification must never reveal vote content.
- Witness publication must remain best-effort and privacy-safe.
- Ranked-choice and STV result wording must make the counting method clear.
- Approval Top-N must not silently break seat-deciding ties by candidate order.
- Read `CURRENT_STATE.md` before starting a new implementation session.

---

## Roadmap

Potential future 2.x work:

- Exportable signed audit snapshots
- Advanced reporting and dashboards
- Dedicated GUI delete confirmation flow
- Additional admin transparency tooling
- Multi-target witness publication beyond Discord-compatible webhooks
- Larger-election pagination refinements for very large Linked Offices definitions

---

## Security

If you find a vulnerability or integrity issue:

- Do not disclose it publicly.
- Contact: security@modnmetl.com

---

## License

MIT License

---

## Credits

- Development Lead: Jamie E. Thompson
- Maintainer: MODN METL LTD
- Community testing: Pinecraft Equestrian SMP

---

> "Trust, but verify."  
> ModNVote is built to help communities make fair, transparent decisions.
