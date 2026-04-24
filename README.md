# ModNVote

**Privacy-first, auditable community voting for PaperMC servers**

ModNVote is an open-source Minecraft plugin for communities that want voting to be easy for players, practical for admins, and verifiable after the fact.

Originally built as a simple Yes/No voting tool, ModNVote 2.0 is a clean architectural redesign into a ballot-based voting platform with anonymous ballots, poll lifecycles, ranked-choice support, deterministic results, and tamper-evident audit data.

Developed by [MODN METL LTD](https://modnmetl.com).

![CI](https://github.com/MODNMETL/ModNVote/actions/workflows/ci.yml/badge.svg)
![Java](https://img.shields.io/badge/Java-21-007396)
![Paper](https://img.shields.io/badge/Paper-1.21.x-blue)
![License: MIT](https://img.shields.io/badge/License-MIT-green)

---

## ModNVote 2.0 status

> ModNVote 2.0 is in active development on the `feature/modnvote-2.0-core` branch.

Current 2.0 builds are functional for testing and SMP use, but schema, commands, and APIs may continue to evolve before the 2.0 branch replaces the legacy 1.x line.

2.0 is a clean install target. Migration from legacy 1.x databases is not currently supported.

---

## Core principles

### Privacy

ModNVote 2.0 deliberately separates identity-aware participation records from anonymous ballot content.

- Participation records say that a player voted in a poll.
- Anonymous ballots contain the actual vote selections.
- The system must not introduce a joinable identity-to-vote path.

This means admins can verify participation and enforce duplicate-prevention rules without being handed a direct record of how a named player voted.

### Verifiability

Votes should not require blind trust.

Players receive proof material after voting so they can later verify that their ballot still exists and still represents the selection they submitted.

### Tamper evidence

ModNVote does not claim to make tampering impossible. Instead, it is designed to make tampering detectable through stored ballot hashes, proof commitments, and an append-only audit chain.

### Usability

Voting is a trust interface. Players should be able to understand what they are voting on, review their choices, confirm intentionally, and later verify their participation or ballot without needing technical knowledge.

---

## Current 2.0 capabilities

- Multi-poll architecture
- Poll lifecycle: `DRAFT -> READY -> OPEN -> CLOSED`
- Yes/No polls
- Ranked single-winner polls using deterministic IRV-style counting
- Draft poll authoring from commands
- Draft poll title and description editing
- Ranked option authoring with names and descriptions
- Ready-state validation before a poll can open
- Draft/ready poll deletion for abandoned setup work
- Pane-less inventory GUI design for better Bedrock compatibility
- Separate Java inventory renderers and session managers for ranked and Yes/No polls
- Mandatory confirmation screen before submission
- Authoritative ballot submission through the service layer
- Anonymous ballots as the source of truth for vote content
- Participation tracking separate from ballot data
- IP duplicate-prevention heuristics with bypass support
- Player participation listing via `/modnvote mypolls`
- Participation verification that does not reveal vote choices
- Ballot proof phrase verification for players who possess the proof phrase
- Result reporting from anonymous ballots only
- Append-only audit event chain
- Ballot integrity verification
- Deterministic recounting from stored anonymous ballots

---

## Poll lifecycle

Polls move through explicit lifecycle states:

```text
DRAFT -> READY -> OPEN -> CLOSED
```

### DRAFT

The poll is editable. Admins can set the title, set the description, add/edit/move/remove ranked options, and validate the poll definition.

### READY

The poll has passed validation and is staged for opening. It is no longer editable, but it can still be deleted if it has not opened.

### OPEN

Players can vote. The GUI is available through `/modnvote vote <pollId>`.

### CLOSED

Voting is finished. Public results can be reported with `/modnvote result <pollId>`.

---

## Poll types

### Yes/No

Yes/No polls use canonical `yes` and `no` option keys. Admins may customise the display names and descriptions while the poll is in draft, but the semantic option structure is protected.

### Ranked single-winner

Ranked polls allow players to order choices by preference. The current result model determines the winner through deterministic IRV-style counting and displays an ordered tally view.

---

## Voting UX

Players vote with inventory GUIs rather than chat commands that reveal their choices.

Current flows include:

1. `/modnvote vote <pollId>`
2. Poll-type-specific selection screen
3. Mandatory confirmation screen
4. Service-layer ballot submission
5. Chat receipt with poll context, ballot hash, and proof phrase

Design notes:

- Ranked and Yes/No voting use separate session and renderer implementations.
- The GUI layer is not authoritative and does not write to the database directly.
- All voting validation happens in the service layer.
- Background panes are intentionally omitted to avoid Bedrock angled-pane rendering issues.
- Long poll and option descriptions are wrapped in item lore for readability.

---

## Verification model

ModNVote has two user-facing verification paths.

### Participation verification

```text
/modnvote verify participation <pollId>
```

This tells a player whether their participation is recorded for a poll and reports integrity status. It does not reveal how they voted.

Legacy shorthand is also supported:

```text
/modnvote verify <pollId>
```

### Ballot proof verification

```text
/modnvote verify ballot <pollId> <proof phrase>
```

The proof phrase acts as a bearer token for that ballot. Anyone who has the phrase can reveal the ballot selection, so players are warned not to share it.

The proof phrase is deliberately not player-specific. If someone brute-forces or guesses a phrase, the discovered ballot is still not directly attributable to a player through the participation table.

Proof phrase input is normalised for usability, so players may type phrases with spaces or hyphens and mixed case.

---

## Results model

```text
/modnvote result <pollId>
```

Results are derived from anonymous ballots only.

- Open polls reject result requests.
- Closed Yes/No polls show total votes, Yes count, and No count.
- Closed ranked single-winner polls show total votes, the winning option, and an ordered tally of options.

The result command does not use participation records to reconstruct vote content.

---

## Commands

All commands are rooted at `/modnvote`.

### General

```text
/modnvote status
/modnvote reload
/modnvote list
```

`status` reports service readiness without exposing sensitive server filesystem paths.

### Admin authoring

```text
/modnvote create <yes_no|ranked_single_winner>
/modnvote set <pollId> title <title text>
/modnvote set <pollId> description <description text>
/modnvote set <pollId> maxrankings <number>
/modnvote set <pollId> allowpartial <true|false>
/modnvote option add <pollId> <key> <displayName> | <description>
/modnvote option edit <pollId> <optionId> <name|description> <value>
/modnvote option move <pollId> <optionId> <displayOrder>
/modnvote option remove <pollId> <optionId>
/modnvote validate <pollId>
/modnvote ready <pollId>
/modnvote delete <pollId>
```

`delete` is limited to polls that have not opened yet: `DRAFT` or `READY`.

### Admin lifecycle

```text
/modnvote open <pollId>
/modnvote close <pollId>
```

A poll must be `READY` before it can open.

### Demo and testing

```text
/modnvote rankedpolldemo
/modnvote testvote <pollId> <optionId1> <optionId2> ...
```

`rankedpolldemo` creates a ready-to-open ranked horse-breed demo poll for admins who want to see the plugin flow before building their own poll.

### Player commands

```text
/modnvote vote <pollId>
/modnvote mypolls
/modnvote verify participation <pollId>
/modnvote verify ballot <pollId> <proof phrase>
/modnvote result <pollId>
```

---

## Permissions

Permissions are still evolving during 2.0 development. Current command gating uses nodes including:

```text
modnvote.admin.reload
modnvote.admin.poll.list
modnvote.admin.poll.create
modnvote.admin.poll.open
modnvote.admin.poll.close
modnvote.verify
modnvote.testvote
```

Duplicate-prevention bypass support remains configurable through the plugin configuration.

---

## Installation

> ModNVote 2.0 currently requires a clean install.

1. Stop the server.
2. Remove any legacy ModNVote 1.x jar.
3. Back up and remove old ModNVote 1.x database/config files if present.
4. Install the 2.0 jar into `/plugins/`.
5. Start the server.
6. Configure permissions.
7. Create and test a new poll.

### Requirements

- Paper 1.21.x+
- Java 21

---

## Architecture notes

The 2.0 architecture keeps clear separation between layers:

- Command layer: parses user input and displays messages.
- GUI/session layer: owns inventory state and player interaction flow.
- Service layer: enforces voting, validation, lifecycle, privacy, and integrity rules.
- DAO layer: persists poll, option, participation, anonymous ballot, preference, and audit data.

The GUI/session layer must not write ballots or lifecycle state directly to the database. Ballot submission goes through the authoritative ballot service.

---

## Roadmap

### Toward 2.0.0

- Continue SMP testing of Yes/No and ranked single-winner polls
- Polish result display formatting
- Improve authoring ergonomics for longer option sets
- Expand audit/admin visibility without weakening ballot privacy
- Prepare the 2.0 branch to replace the legacy 1.x line

### Later 2.x

- Multi-winner STV
- Combined elections such as Mayor + Council
- Exportable audit snapshots
- Optional external witness publication
- Advanced reporting and dashboards

---

## Important notes

- 2.0 is a clean architectural reset.
- No migration from 1.x is currently supported.
- Schema and APIs are still evolving.
- Early builds are intended for testing and iteration.
- Privacy depends on preserving the separation between participation and ballot content.

---

## Contributing

1. Fork the repo.
2. Create a feature branch.
3. Build with `./gradlew clean build`.
4. Submit a PR with clear rationale.

Please preserve the privacy model when contributing. In particular, do not introduce identity-to-ballot joins or verification flows that reveal how a named player voted.

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

> “Trust, but verify.”  
> ModNVote is built to help communities make fair, transparent decisions.
