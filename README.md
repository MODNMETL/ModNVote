# ModNVote

**Privacy-first, auditable community voting for PaperMC servers**

ModNVote is a Minecraft voting and polling plugin for communities that want voting to be easy for players, practical for admins, and verifiable after the fact.

ModNVote 2.0 replaces the original Yes/No-only workflow with a GUI-first, ballot-based polling platform supporting ranked single-winner polls, Yes/No polls, anonymous ballots, poll lifecycle controls, and tamper-evident integrity checks.

Developed by [MODN METL LTD](https://modnmetl.com).

![CI](https://github.com/MODNMETL/ModNVote/actions/workflows/ci.yml/badge.svg)
![Java](https://img.shields.io/badge/Java-21-007396)
![Paper](https://img.shields.io/badge/Paper-1.21.x-blue)
![License: MIT](https://img.shields.io/badge/License-MIT-green)

---

## ModNVote 2.0 status

ModNVote 2.0 is the active replacement for the legacy 1.x Yes/No-only plugin.

2.0 introduces:

- GUI-driven poll creation and editing
- Ranked single-winner polls
- Yes/No polls
- Anonymous ballot storage
- Identity-aware participation tracking without joining identity to vote content
- Ballot proof-phrase verification
- Tamper-evident audit records
- Java/Bedrock-friendly inventory interfaces
- Mandatory confirmation before ballots are cast

2.0 is a clean install target. Migration from legacy 1.x databases is not currently supported.

---

## Core privacy model

ModNVote 2.0 is designed around one central rule:

> Anonymous ballots are the source of truth for vote content.

The system deliberately separates:

| Data type | Purpose |
|---|---|
| Anonymous ballots | Store vote content and drive results |
| Participation records | Track who has participated and prevent duplicate voting |
| Audit records | Provide lifecycle and integrity evidence |
| Proof phrases | Let a voter verify a ballot without revealing identity links |

This means:

- Results are calculated from anonymous ballots only.
- Participation records do not contain vote content.
- Vote content and voter identity are not stored together.
- `/modnvote verify participation` confirms participation without revealing a vote.
- `/modnvote verify ballot` uses a proof phrase as a bearer-token style verification mechanism.
- GUI/session state does not directly write ballots or lifecycle state.

---

## Supported poll types

### Ranked single-winner polls

Ranked polls let players rank options in preference order. Results are calculated from anonymous ranked ballots.

Admins can configure through the Poll Builder GUI:

- Poll title
- Poll description
- Option names
- Option descriptions
- Whether partial rankings are allowed
- Maximum number of rankings a player may submit

Example:

```text
/modnvote create ranked_single_winner 5
```

This creates a DRAFT poll with five placeholder options and immediately opens the Poll Builder GUI.

### Yes/No polls

Yes/No polls use canonical Yes and No options managed by the service layer.

Example:

```text
/modnvote create yes_no
```

This creates a DRAFT Yes/No poll and opens the Poll Builder GUI.

---

### Command alias

All `/modnvote` commands can also be used via the shorter alias:

```text
/poll ...

## Admin workflow

### Create a ranked poll

```text
/modnvote create ranked_single_winner <optionCount>
```

Example:

```text
/modnvote create ranked_single_winner 5
```

The Poll Builder opens automatically.

In the builder:

- Left-click the title item to edit the poll title.
- Click the description book to edit the poll description.
- Left-click an option item to edit its display name.
- Right-click an option item to edit its description.
- Click the Allow Partial Rankings item to toggle partial ranking.
- Click the Max Rankings item to cycle the maximum number of rankings.
- Red fields still need work.
- Green fields are complete.
- When READY turns green, click it to mark the poll ready.

### Create a Yes/No poll

```text
/modnvote create yes_no
```

The Poll Builder opens automatically.

Yes/No polls do not show ranked-only settings such as Max Rankings or Allow Partial Rankings.

### Resume editing a draft poll

```text
/modnvote edit <draftPollId>
```

This reopens the Poll Builder for an existing DRAFT poll.

### Open a poll for voting

```text
/modnvote open <pollId>
```

Only READY polls can be opened.

### Vote in an open poll

```text
/modnvote vote <pollId>
```

Voting uses an inventory GUI.

For ranked polls:

- Options remain visually stable as paper items.
- Hovering an option shows whether it is currently ranked and at what position.
- Players review their selection before casting.
- Ballot submission requires confirmation.

### Close a poll

```text
/modnvote close <pollId>
```

### Show results

```text
/modnvote result <pollId>
```

Results are calculated from anonymous ballots only.

---

## Verification commands

### Show polls you participated in

```text
/modnvote mypolls
```

### Verify participation

```text
/modnvote verify participation <pollId>
```

This confirms whether the player participated in a poll and reports integrity status. It does not reveal vote content.

### Verify a ballot proof phrase

```text
/modnvote verify ballot <pollId> <proofPhrase>
```

This checks whether a proof phrase matches a stored anonymous ballot and verifies the ballot integrity data.

Treat ballot proof phrases like bearer tokens: anyone with the phrase can verify that ballot reference.

---

## Admin command reference

Normal admin-facing commands:
Alias:
/poll ...
```text
/modnvote guide
/modnvote create ranked_single_winner <optionCount>
/modnvote create yes_no
/modnvote edit <draftPollId>
/modnvote list
/modnvote show <pollId>
/modnvote delete <pollId>
/modnvote open <pollId>
/modnvote close <pollId>
/modnvote result <pollId>
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

Some older low-level authoring commands may remain callable as recovery tools, but normal poll setup should use the GUI builder.

---

## Permissions

Exact permission defaults are defined in `plugin.yml`.

Common permissions include:

| Permission | Purpose |
|---|---|
| `modnvote.admin.poll.create` | Create, edit, inspect, and manage draft polls |
| `modnvote.admin.poll.list` | List polls |
| `modnvote.admin.poll.open` | Open polls for voting |
| `modnvote.admin.poll.close` | Close polls |
| `modnvote.admin.reload` | Reload plugin configuration |
| `modnvote.verify` | Use verification commands |
| `modnvote.testvote` | Access test vote tooling where enabled |

Duplicate-prevention bypass support remains configurable through the plugin configuration.

---

## GUI design notes

The current GUI intentionally avoids decorative glass panes.

This keeps the interface simpler and more compatible with Bedrock players while still providing clear interaction cues through:

- Item names
- Item lore
- Red/green completion status
- Confirmation screens
- Wrapped multiline descriptions

The Poll Builder and voting GUI are designed to remain intuitive across Java and Bedrock clients.

---

## Architecture overview

ModNVote 2.0 uses a service-authoritative design.

### GUI/session layer

Responsible for:

- Rendering inventories
- Holding temporary player interaction state
- Capturing chat input for builder fields
- Delegating all mutations to services

Not responsible for:

- Writing ballots directly
- Writing lifecycle state directly
- Calculating results
- Bypassing validation

### Service layer

Responsible for:

- Poll creation
- Poll validation
- Lifecycle transitions
- Option mutation
- Ballot submission
- Integrity and audit enforcement

### Persistence layer

Responsible for:

- Poll definitions
- Poll options
- Anonymous ballots
- Participation records
- Audit records

---

## Lifecycle

A poll progresses through lifecycle states such as:

```text
DRAFT -> READY -> OPEN -> CLOSED
```

Typical admin flow:

```text
create -> edit in builder -> mark READY -> open -> players vote -> close -> result
```

The builder keeps polls in DRAFT until required fields are complete and the admin marks the poll ready.

---

## Integrity and audit model

ModNVote 2.0 includes audit and verification features intended to make election data tamper-evident.

Integrity checks include:

- Participation inclusion checks
- Ballot hash verification
- Ballot commitment verification
- Audit chain validation

The goal is not to identify how someone voted. The goal is to verify that the stored election data remains internally consistent and that a voter can verify their own ballot proof phrase.

---

## Installation

ModNVote 2.0 currently requires a clean install.

1. Stop the server.
2. Remove any legacy ModNVote 1.x jar.
3. Back up and remove old ModNVote 1.x database/config files if present.
4. Install the 2.0 jar into `/plugins/`.
5. Start the server.
6. Configure permissions.
7. Create and test a new poll.

### Requirements

- Paper 1.21.x
- Java 21

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

The Java source/target level should remain Java 21 unless explicitly changed.

---

## Development notes

- Keep `.gradle/` out of version control.
- Do not commit local Gradle cache files.
- Prefer tranche-based changes that build after each tranche.
- GUI features should remain Folia-aware through `ModNScheduler`.
- Results must always come from anonymous ballots only.
- Participation verification must never reveal vote content.

---

## Release smoke test

Recommended smoke test before release:

```text
/modnvote create ranked_single_winner 3
/modnvote create yes_no
/modnvote edit <draftPollId>
/modnvote open <readyPollId>
/modnvote vote <openPollId>
/modnvote close <openPollId>
/modnvote result <closedPollId>
/modnvote mypolls
/modnvote verify participation <pollId>
/modnvote verify ballot <pollId> <proofPhrase>
```

---

## Roadmap

Potential future 2.x work:

- Multi-winner STV
- Combined elections such as Mayor + Council
- Exportable audit snapshots
- Optional external witness publication
- Advanced reporting and dashboards
- Dedicated GUI delete confirmation flow
- Additional admin transparency tooling

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
