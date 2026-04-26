# CURRENT_STATE — ModNVote

This file is the primary handoff document for new development sessions.

Read this after:

1. `README.md`
2. `ARCHITECTURE.md`
3. `CHANGELOG.md`
4. `Project-Context.txt` if present in the active context upload

## Baseline

- Branch: `main`
- Release: `v2.0.0`
- Java target: 21
- Platform target: Paper 1.21.x, Folia-aware by design through `ModNScheduler`
- Build: `gradlew.bat clean build`

## Proven v2.0.0 state

ModNVote 2.0 has replaced the legacy Yes/No-only workflow.

Working and tested:

- GUI Poll Builder for ranked single-winner polls
- GUI Poll Builder for Yes/No polls
- `/modnvote create ranked_single_winner <optionCount>`
- `/modnvote create yes_no`
- `/modnvote edit <draftPollId>`
- `/modnvote guide`
- Builder title/description editing through chat prompts
- Builder option name/description editing through chat prompts
- Ranked builder Allow Partial toggle
- Ranked builder Max Rankings cycle control
- Builder READY validation and transition
- Builder Cancel closes without deleting draft
- Ranked voting GUI
- Yes/No voting GUI
- Mandatory vote confirmation
- Anonymous ballot submission
- Participation verification
- Ballot proof-phrase verification
- Result display from anonymous ballots only
- Join notifications for open unvoted polls
- Pane-less Java/Bedrock-friendly GUI design

## Core invariants

- Anonymous ballots are the source of truth for vote content.
- Participation records are identity-aware but separate.
- Identity and vote content must not be joinable.
- Results must come from anonymous ballots only.
- `/verify participation` must not reveal vote content.
- `/verify ballot` is proof-phrase bearer-token ballot verification.
- GUI/session layer must not write ballots or lifecycle state directly.
- Service layer owns validation, lifecycle, and persistence authority.
- GUI/session work must remain Folia-aware through `ModNScheduler`.

## Command surface

Normal admin workflow:

- `/modnvote guide`
- `/modnvote create ranked_single_winner <optionCount>`
- `/modnvote create yes_no`
- `/modnvote edit <draftPollId>`
- `/modnvote list`
- `/modnvote show <pollId>`
- `/modnvote delete <pollId>`
- `/modnvote open <pollId>`
- `/modnvote close <pollId>`
- `/modnvote result <pollId>`
- 
  Alias:

- `/poll` is a direct alias of `/modnvote` for all commands.

Player workflow:

- `/modnvote vote <pollId>`
- `/modnvote mypolls`
- `/modnvote verify participation <pollId>`
- `/modnvote verify ballot <pollId> <proofPhrase>`

Hidden/recovery authoring commands may remain callable:

- `set`
- `option`
- `validate`
- `ready`
- `rankedpolldemo`

## Important implementation notes

- `PollCommand.java` is large. Prefer manual/local edits unless doing a fresh full-file replacement with extreme care.
- Small files such as builder renderer/listener/input prompt classes can be handled repo-direct with full-file replacements.
- Never write snippet-only placeholder files to the repo.
- Always fetch/read current canonical files before edits.
- Keep Gradle Java source/target at Java 21 unless explicitly agreed.
- Do not commit `.gradle/`.

## Current known config stub

`config.yml` includes:

```yaml
publication:
  discord_webhooks: []
  publish_poll_opened: true
  publish_poll_closed: true
  publish_checkpoints: true
```

As of v2.0.0 this is configuration only. External witness publication is not implemented yet.

## Immediate 2.1.0 backlog

### 1. Poll clone command

Add:

```text
/modnvote clone <sourcePollId>
```

Desired behavior:

- Requires admin create permission.
- Player-only if opening the builder immediately.
- Source poll may be DRAFT, READY, OPEN, or CLOSED.
- Creates a new DRAFT poll.
- Opens the Poll Builder for the new poll.

Clone should copy:

- poll type
- title
- description
- maxRankings
- seatCount
- allowPartialRanking
- requiresConfirmation
- option keys
- option display names
- option descriptions
- option display order

Clone must not copy:

- source poll ID
- slug
- status
- opensAt/closesAt
- participation secret
- anonymous ballots
- ballot preferences
- participation records
- audit event history
- proof phrases or commitments

Service method direction:

```java
public long clonePoll(long sourcePollId, String actor) throws PollServiceException
```

Insert the cloned poll and options in one transaction and emit a fresh `POLL_CLONED` audit event.

### 2. External witness publication

Implement Discord webhook publication using the ModNEquine market webhook as the reference pattern.

Reference repo/class:

- `MODNMETL/ModNEquine`
- `src/main/java/com/modnmetl/modnequine/market/MarketDiscordWebhookService.java`

Design rules:

- Best-effort only.
- Use Java `HttpClient.sendAsync`.
- Log failures.
- Never block or roll back poll lifecycle, voting, closing, or persistence because a webhook failed.
- Never log full webhook URLs.

Initial events:

- poll opened
- poll closed

Later/manual checkpoint:

- integrity checkpoint publication

Suggested class:

```text
src/main/java/com/modnmetl/modnvote/publication/DiscordWitnessPublicationService.java
```

Wire through `ModNVotePlugin`, then call from command/lifecycle flow after successful service-layer state changes.

### 3. Optional checkpoint command

Possible command:

```text
/modnvote checkpoint <pollId>
```

or:

```text
/modnvote publish checkpoint <pollId>
```

Use this to publish an integrity snapshot to configured webhooks.

## Recommended 2.1.0 tranche order

1. `clonePoll` service method and `/modnvote clone`
2. Discord witness publication service foundation
3. Poll opened/closed publication
4. Manual checkpoint publication
5. README/CHANGELOG update for 2.1.0

## New-session rule

Do not rely on prior chat memory if repo docs and code can answer the question.

If docs and code disagree, resolve the disagreement explicitly before editing.
