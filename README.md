# ModNVote

**Privacy-first, auditable community voting for PaperMC servers**

ModNVote is a Minecraft voting and polling plugin for communities that want voting to be easy for players, practical for admins, and verifiable after the fact.

ModNVote 2.x replaces the original Yes/No-only workflow with a GUI-first, ballot-based polling platform supporting ranked single-winner polls, Yes/No polls, anonymous ballots, poll lifecycle controls, tamper-evident integrity checks, Discord-compatible witness publication, and transparent ranked-choice result reporting.

Developed by [MODN METL LTD](https://modnmetl.com).

![CI](https://github.com/MODNMETL/ModNVote/actions/workflows/ci.yml/badge.svg)
![Java](https://img.shields.io/badge/Java-21-007396)
![Paper](https://img.shields.io/badge/Paper-1.21.x-blue)
![License: MIT](https://img.shields.io/badge/License-MIT-green)

---

## ModNVote 2.x status

ModNVote 2.1.1 is the current active replacement for the legacy 1.x Yes/No-only plugin.

2.x includes:

- GUI-driven poll creation and editing
- Ranked single-winner polls
- Yes/No polls
- Anonymous ballot storage
- Identity-aware participation tracking without joining identity to vote content
- Ballot proof-phrase verification
- Tamper-evident audit records
- Java/Bedrock-friendly inventory interfaces
- Mandatory confirmation before ballots are cast
- Poll cloning for repeated or template-based poll setup
- Optional external witness publication via Discord-compatible webhooks
- Automatic and manual integrity checkpoint publication
- Transparent IRV round reporting for ranked single-winner polls
- Manual closed-result republication with `/modnvote publishresult <pollId>`
- `/poll` as a short alias for `/modnvote`

2.x is a clean install target. Migration from legacy 1.x databases is not currently supported.

---

## Core privacy model

ModNVote 2.x is designed around one central rule:

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
- `/poll` may be used as a shorter alias for `/modnvote`.
- GUI/session state does not directly write ballots or lifecycle state.
- Witness publication must not include player names, UUIDs, IP addresses, proof phrases, participation receipts, or per-player vote content.

---

## Supported poll types

### Ranked single-winner polls

Ranked polls let players rank options in preference order. Results are calculated from anonymous ranked ballots using IRV-style transfer rounds.

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

## Ranked-choice result transparency

Ranked single-winner results are not first-past-the-post results.

For ranked polls:

- First-preference totals are shown as the first IRV round.
- Later rounds show transfers after eliminations.
- The winning option may be different from the first-preference leader.
- Exhausted ballots are reported where applicable.
- Final winner tally is shown separately from first-preference totals.

In-game result output and Discord witness output are both designed to distinguish:

- Poll winner
- Final winner tally
- First Preference Round
- Final IRV Round
- IRV Round Breakdown
- Eliminated option per non-final round
- Exhausted ballots where applicable

This avoids the misleading situation where a ranked-choice winner is correct but the displayed counts look like first-preference-only totals.

---

## Command alias

All `/modnvote` commands can also be used via the shorter alias:

```text
/poll ...
```

For example:

```text
/poll create ranked_single_winner 5
/poll open <pollId>
/poll vote <pollId>
```

---

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

### Clone an existing poll

```text
/modnvote clone <sourcePollId>
```

This creates a new DRAFT poll by copying the source poll's definition and options, then opens the Poll Builder so the clone can be adjusted.

Cloning does not copy ballots, participation records, lifecycle timestamps, proof phrases, or audit history.

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

Closing an open poll calculates results and, when enabled, publishes a best-effort poll-closed witness message to configured webhooks.

### Show results

```text
/modnvote result <pollId>
```

Results are calculated from anonymous ballots only.

For ranked single-winner polls, this shows the poll winner, final winner tally, and round-by-round IRV breakdown.

### Republish a closed poll result

```text
/modnvote publishresult <pollId>
```

This republishes a CLOSED poll's current result display to configured witness webhooks.

Use this after upgrading result formatting or correcting public result presentation. The command requires the poll to already be `CLOSED`; it does not reopen or recalculate lifecycle state beyond reading current anonymous ballot results.

### Publish a manual integrity checkpoint

```text
/modnvote checkpoint <pollId>
```

This publishes a privacy-safe witness checkpoint to the configured webhook targets.

Manual checkpoints include poll-level integrity status only. They do not publish player names, UUIDs, IP addresses, proof phrases, participation receipts, or per-player vote content.

---

## Witness publication

ModNVote can publish public witness events to configured Discord-compatible webhooks.

Supported witness events:

- Poll opened
- Poll closed, including a public result summary
- Ranked poll closed, including winner, final IRV round, and IRV round breakdown
- Automatic integrity checkpoints every configured number of accepted ballots
- Manual integrity checkpoints via `/modnvote checkpoint <pollId>`
- Manual closed-result republication via `/modnvote publishresult <pollId>`

Webhook delivery is best-effort and non-blocking. A failed webhook does not cancel voting, poll opening, poll closing, result calculation, or persistence.

Configure webhook publication in `config.yml`:

```yaml
publication:
  # External witness publication targets.
  #
  # Leave this as [] to disable webhook publication:
  # discord_webhooks: []
  #
  # To enable Discord publication, change it to a YAML list:
  # discord_webhooks:
  #   - "DISCORD_WEBHOOK_URL_PLACEHOLDER"
  #
  # Multiple webhooks are supported:
  # discord_webhooks:
  #   - "FIRST_DISCORD_WEBHOOK_URL_PLACEHOLDER"
  #   - "SECOND_DISCORD_WEBHOOK_URL_PLACEHOLDER"
  #
  # Never commit real webhook URLs to source control.
  discord_webhooks: []
  publish_poll_opened: true
  publish_poll_closed: true
  publish_checkpoints: true

integrity:
  # Automatic witness checkpoints are published every N accepted ballots
  # when publication.publish_checkpoints is true and at least one webhook is configured.
  #
  # Set to 0 or a negative number to disable automatic interval checkpoints.
  checkpoint_interval_ballots: 25
  canonicalization_version: 1
```

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

All commands below may use either `/modnvote` or `/poll`.

Normal admin-facing commands:

```text
/modnvote guide
/modnvote create ranked_single_winner <optionCount>
/modnvote create yes_no
/modnvote edit <draftPollId>
/modnvote clone <sourcePollId>
/modnvote list
/modnvote show <pollId>
/modnvote validate-definition <pollId>
/modnvote create linked_offices
/modnvote config <pollId> set <json>
/modnvote config <pollId> import <file>
/modnvote delete <pollId>
/modnvote open <pollId>
/modnvote close <pollId>
/modnvote result <pollId>
/modnvote publishresult <pollId>
/modnvote checkpoint <pollId>
```

`/modnvote validate-definition <pollId>` is a read-only admin check that parses and validates a linked-offices election definition stored in a poll's `config_json`. It also warns if a poll's type and its declared config model disagree.

`/modnvote create linked_offices` creates a DRAFT, non-votable Linked Offices poll. `/modnvote config <pollId> set <json>` stores an inline definition, and `/modnvote config <pollId> import <file>` imports one from `plugins/ModNVote/definitions/<file>` (UTF-8 JSON, path-traversal rejected). Definitions are validated before they are saved; invalid definitions are rejected without writing. A valid Linked Offices poll can be marked READY, but **Linked Offices voting is not implemented yet** — such polls cannot be opened, voted, or resulted. See `docs/examples/linked-offices-mayor-council.json` for an example definition.

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

Short alias examples:

```text
/poll status
/poll guide
/poll vote <pollId>
/poll publishresult <pollId>
```

Some older low-level authoring commands may remain callable as recovery tools, but normal poll setup should use the GUI builder.

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

ModNVote 2.x uses a service-authoritative design.

### Command layer

Responsible for:

- Parsing user input
- Checking permissions
- Displaying formatted output
- Delegating business logic to services

Not responsible for:

- Writing ballots directly
- Writing lifecycle state directly
- Calculating results independently
- Reconstructing ballot logic
- Bypassing validation

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
- Poll cloning
- Poll validation
- Lifecycle transitions
- Option mutation
- Ballot submission
- Result calculation
- Integrity and audit enforcement

### Persistence layer

Responsible for:

- Poll definitions
- Poll options
- Anonymous ballots
- Ballot preferences
- Participation records
- Audit records

### Presentation/publication layer

Responsible for:

- Formatting in-game result output
- Formatting Discord witness result fields
- Publishing privacy-safe webhook events
- Keeping ranked-choice public result wording clear and non-misleading

`ResultDisplayFormatter` is the canonical result presentation helper. Future result-display changes should generally go through that class rather than duplicating formatting in command or publication code.

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

ModNVote 2.x includes audit and verification features intended to make election data tamper-evident.

Integrity checks include:

- Participation inclusion checks
- Ballot hash verification
- Ballot commitment verification
- Audit chain validation
- Optional witness checkpoint publication

The goal is not to identify how someone voted. The goal is to verify that the stored election data remains internally consistent and that a voter can verify their own ballot proof phrase.

Witness publication can optionally publish poll-level lifecycle, result, and checkpoint events to configured webhooks. These events are privacy-safe and do not include voter identity, proof phrases, participation receipts, IP data, or per-player vote content.

---

## Installation

ModNVote 2.x requires a clean install. There is no supported upgrade path from v1.x databases.

1. Stop the server.
2. Remove any legacy ModNVote 1.x jar.
3. Back up and remove old ModNVote 1.x database/config files if present.
4. Install the current ModNVote 2.x jar into `/plugins/`.
5. Start the server.
6. Configure permissions.
7. Configure witness publication if desired.
8. Create and test a new poll.

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

The release jar is produced by the Shadow plugin under `build/libs/`.

---

## Development notes

- Keep `.gradle/` out of version control.
- Do not commit local Gradle cache files.
- Prefer tranche-based changes that build after each tranche.
- GUI features should remain Folia-aware through `ModNScheduler`.
- Results must always come from anonymous ballots only.
- Participation verification must never reveal vote content.
- Witness publication must remain best-effort and privacy-safe.
- Ranked-choice result wording must not make first-preference totals look like final results.
- Read `CURRENT_STATE.md` before starting a new implementation session.

---

## Recommended smoke test

After significant changes, test a representative flow:

```text
/modnvote status
/modnvote create ranked_single_winner 3
/modnvote create yes_no
/modnvote edit <draftPollId>
/modnvote open <readyPollId>
/modnvote vote <openPollId>
/modnvote close <openPollId>
/modnvote result <closedPollId>
/modnvote publishresult <closedPollId>
/modnvote checkpoint <pollId>
/modnvote mypolls
/modnvote verify participation <pollId>
/modnvote verify ballot <pollId> <proofPhrase>
```

Also verify alias behavior:

```text
/poll status
/poll vote <pollId>
/poll publishresult <closedPollId>
```

---

## Roadmap

Potential future 2.x work:

- Multi-winner STV
- Combined elections such as Mayor + Council
- Exportable signed audit snapshots
- Advanced reporting and dashboards
- Dedicated GUI delete confirmation flow
- Additional admin transparency tooling
- Multi-target witness publication beyond Discord-compatible webhooks

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
