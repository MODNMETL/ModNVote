# CURRENT_STATE — ModNVote

This is the primary handoff document for new development sessions.

Read this after:

1. `README.md`
2. `ARCHITECTURE.md`
3. `CHANGELOG.md`
4. `Project-Context.txt` if present in the active context upload

If this file disagrees with code, verify against the code and resolve the disagreement before editing.

---

## Baseline

- Branch: `main`
- Current release: `v2.1.1`
- In development: `2.2.0` (Linked Offices development stretch; groundwork only so far — see CHANGELOG and the "2.2.0 groundwork" section below)
- Java target: 21
- Platform target: Paper 1.21.x
- Folia-aware scheduling: through `ModNScheduler`
- Build command, Unix/macOS: `./gradlew clean build`
- Build command, Windows: `gradlew.bat clean build`
- Release jar: produced by `shadowJar` as `build/libs/modnvote-<version>.jar`

`build.gradle.kts` is authoritative for the current plugin version.

---

## 2.2.0 groundwork (in progress)

The 2.2.0 stretch targets a generic Linked Offices election model (multiple
contests resolved from a single anonymous ballot). That feature is **not yet
implemented**.

Completed groundwork so far:

- Extracted a shared `BallotCanonicalizer` (`service.canonical`) as the single
  source of truth for anonymous-ballot canonical payload construction. Both
  `BallotService` (submission) and `IntegrityVerificationService`
  (recount/verification) now use it instead of duplicated private builders.
  Canonical output is byte-for-byte unchanged (`rule_snapshot_version=v2`), so
  existing stored ballot hashes and proof commitments still verify.
- Added a test foundation under `src/test`:
  - golden/stability tests locking the canonical payload format for existing
    `YES_NO` and `RANKED_SINGLE_WINNER` ballots
  - schema privacy/non-joinability regression tests over the live schema
- Bumped the project version to `2.2.0`.

Tranche 2A added the generic election-definition/config layer (definition only, no voting):

- Generic election-definition domain model under `domain.election`
  (`ElectionDefinition`, `ContestDefinition`, `CandidateDefinition`,
  `OfficeDependencyRule`, `CountingMethod`, `OfficeDependencyType`). Mayor/Council
  are examples only; nothing is hardcoded.
- `ElectionDefinitionParser` (parses `polls.config_json`; preserves office and
  candidate order; converts `excludeWinnersFrom` into `EXCLUDE_WINNERS`
  dependencies; rejects unknown models/methods) and `ElectionDefinitionValidator`
  (generic structural rules including an acyclic dependency check).
- Surfaced `config_json` on `Poll`/`PollDao` and `metadata_json` on
  `PollOption`/`PollOptionDao`, both defaulting to `"{}"` via backward-compatible
  constructors. No schema change.
- Gson is used for parsing as a `compileOnly` (Paper-provided) + test-only
  dependency; it is not shaded into the plugin jar.

Tranche 2B added read-only admin definition validation (still no voting):

- `ElectionDefinitionService` (`service`) parses+validates a poll's `config_json`
  and returns a structured `ElectionDefinitionValidationResult` (no throwing, no
  persistence, no identity/ballot involvement).
- `/modnvote validate-definition <pollId>` admin command (permission
  `modnvote.admin.poll.create`) reports valid/invalid + issues. Read-only: no
  status change, no DB write, no GUI.
- Reserved, NON-VOTABLE `PollType.LINKED_OFFICES`. It is guarded out of the vote
  command, the vote session layer, `ResultService`, and authoring/lifecycle
  (cannot be created or readied for voting). Existing types/data stay compatible.

Tranche 2C added linked-offices authoring + lifecycle readiness (still no voting):

- `/modnvote create linked_offices` creates a DRAFT, non-votable poll
  (`PollService.createPoll` now accepts `LINKED_OFFICES`; default `config_json`
  is `"{}"`, no default options).
- `/modnvote config <pollId> set <json>` and `/modnvote config <pollId> import
  <file>` store a definition via `PollService.updatePollConfigJson` /
  `PollDao.updatePollConfigJson` (writes only `config_json`). Definitions are
  parsed+validated first; invalid ones are rejected with no write. File import
  uses `LinkedOfficesDefinitionFileLoader`, reading UTF-8 from
  `plugins/ModNVote/definitions` with path-traversal rejection. Config writes are
  allowed only for `LINKED_OFFICES` polls and only while DRAFT.
- A `POLL_CONFIG_UPDATED` audit event stores poll id, actor, declared model, a
  SHA-256 hash of the definition, and its byte length — never the raw JSON.
- `validatePollDefinition`/`readyPoll` accept `LINKED_OFFICES` only with a valid
  definition; an explicit `openPoll` guard rejects it even when READY.
- Example: `docs/examples/linked-offices-mayor-council.json` (example only).

Explicitly NOT done in this groundwork:

- `PollType.LINKED_OFFICES` is authorable/readyable but remains non-votable;
  there is NO linked-offices voting, submission, counting, or result calculation,
  and it cannot be opened.
- No `anonymous_ballot_contest_responses` table or any schema change.
- No multi-contest ballot submission, counting pipeline, or IRV extraction.
- No GUI/session flow for linked offices, and no proof-phrase or
  participation-token changes.

The canonicalizer is intentionally shaped so multi-contest canonicalization can
be added later without altering the existing single-contest format.

---

## Current product state

ModNVote 2.x is a clean-install, privacy-first, auditable voting plugin for Paper servers.

The legacy 1.x Yes/No-only workflow has been replaced by a GUI-first ballot platform supporting:

- Ranked single-winner polls
- Yes/No polls
- Anonymous ballot storage
- Identity-aware participation tracking without joining identity to vote content
- Ballot proof-phrase verification
- Tamper-evident audit records
- External witness publication through Discord-compatible webhooks
- Automatic and manual integrity checkpoints
- Transparent ranked-choice result reporting

Migration from legacy 1.x databases is not currently supported.

---

## Proven implemented state

The following are implemented and should be treated as current behavior unless code inspection proves otherwise.

### Poll authoring and lifecycle

- GUI Poll Builder for ranked single-winner polls
- GUI Poll Builder for Yes/No polls
- `/modnvote create ranked_single_winner <optionCount>`
- `/modnvote create yes_no`
- `/modnvote edit <draftPollId>`
- `/modnvote clone <sourcePollId>`
- `/modnvote guide`
- Builder title editing through chat prompts
- Builder description editing through chat prompts
- Builder option name editing through chat prompts
- Builder option description editing through chat prompts
- Ranked builder Allow Partial toggle
- Ranked builder Max Rankings cycle control
- Builder READY validation and transition
- Builder Cancel closes without deleting draft
- Poll deletion for DRAFT/READY polls
- `/poll` alias for `/modnvote`

### Voting

- Ranked voting GUI
- Yes/No voting GUI
- Mandatory vote confirmation
- Anonymous ballot submission
- Join notifications for open unvoted polls
- Pane-less Java/Bedrock-friendly GUI design

### Verification and integrity

- `/modnvote mypolls`
- `/modnvote verify participation <pollId>`
- `/modnvote verify ballot <pollId> <proofPhrase>`
- Participation verification
- Ballot proof-phrase verification
- Audit chain verification
- Ballot hash and commitment verification
- Result display from anonymous ballots only

### Witness publication

- Configurable Discord-compatible webhook publication
- Best-effort poll-opened witness publication
- Best-effort poll-closed witness publication
- Automatic integrity checkpoints at configured accepted-ballot intervals
- Manual checkpoint publication through `/modnvote checkpoint <pollId>`
- Manual closed-result republication through `/modnvote publishresult <pollId>`

Webhook delivery is intentionally best-effort and must never block or roll back poll lifecycle, voting, closing, or persistence.

---

## Core invariants

Do not break these.

- Anonymous ballots are the source of truth for vote content.
- Participation records are identity-aware but separate from vote content.
- Identity and vote content must not be joinable.
- Results must come from anonymous ballots only.
- `/verify participation` must not reveal vote content.
- `/verify ballot` is proof-phrase bearer-token ballot verification.
- Proof phrases must not be derived from player identity.
- GUI/session layer must not write ballots or lifecycle state directly.
- Service layer owns validation, lifecycle, and persistence authority.
- GUI/session work must remain Folia-aware through `ModNScheduler`.
- Witness publication must be privacy-safe and must not include voter identity, proof phrases, participation receipts, IP data, or per-player vote content.
- Webhook failures must be logged safely without exposing full webhook URLs.

---

## Command surface

All commands use `/modnvote`; `/poll` is a direct alias for the same command executor and tab completer.

### Normal admin workflow

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
/modnvote checkpoint <pollId>
/modnvote publishresult <pollId>
```

### Player workflow

```text
/modnvote vote <pollId>
/modnvote mypolls
/modnvote verify participation <pollId>
/modnvote verify ballot <pollId> <proofPhrase>
```

### Utility commands

```text
/modnvote status
/modnvote reload
```

### Hidden or recovery authoring commands

These may remain callable but are not the preferred normal workflow:

```text
/modnvote set <pollId> <field> <value>
/modnvote option <add|edit|move|remove> ...
/modnvote validate <pollId>
/modnvote ready <pollId>
/modnvote rankedpolldemo
```

Prefer the GUI Poll Builder for normal authoring.

---

## Result model

Results are calculated by `ResultService` from anonymous ballots only.

### Yes/No polls

Yes/No polls use canonical service-managed options and straightforward tally output.

### Ranked single-winner polls

Ranked single-winner polls use IRV-style transfer rounds.

Important points for future sessions:

- First-preference totals are not necessarily the final result.
- A candidate can win after transfers even if they did not lead the first-preference round.
- The public result output must make this clear.
- `ResultService.PollResult` includes ranked-choice round data, final winner tally, and exhausted ballot count.
- `ResultService.RankedChoiceRound` snapshots each IRV round.
- Empty ranked polls must not resolve to a winner.

### Canonical presentation layer

`ResultDisplayFormatter` is the shared result presentation helper.

Use it for:

- in-game result output
- Discord witness result fields
- future result-display paths where practical

Do not duplicate ranked-choice result formatting in command or publication code unless there is a deliberate reason.

Current ranked output should distinguish:

- poll winner
- final winner tally
- first preference round
- final IRV round
- full IRV round breakdown
- eliminated option per non-final round
- exhausted ballots where applicable

This was added to avoid the previous failure mode where the winner was correct but the displayed counts looked like first-preference-only totals.

---

## Witness publication model

Witness publication is handled by the publication layer, with command/lifecycle code invoking it after successful state changes.

Expected events:

- poll opened
- poll closed with result summary
- automatic integrity checkpoint
- manual integrity checkpoint
- manual closed-result republication

Design rules:

- Best-effort only.
- Use asynchronous webhook delivery.
- Log failures.
- Never block or roll back voting, lifecycle transitions, or persistence because a webhook failed.
- Never log full webhook URLs.
- Keep payloads privacy-safe.

Manual result republication:

```text
/modnvote publishresult <pollId>
```

This is intended for republishing corrected/updated closed-poll result formatting to configured witness webhooks. It requires the poll to be `CLOSED`.

Manual checkpoint publication:

```text
/modnvote checkpoint <pollId>
```

This publishes a privacy-safe integrity snapshot, not per-player vote content.

---

## Config surface

`config.yml` includes publication controls similar to:

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

Rules:

- `discord_webhooks: []` disables external publication.
- One or more Discord-compatible webhook URLs can be configured.
- Real webhook URLs must never be committed.
- Automatic checkpoints require publication to be enabled and at least one webhook configured.
- `checkpoint_interval_ballots <= 0` disables automatic interval checkpoints.

---

## Important implementation notes

- `PollCommand.java` is large. Prefer targeted local edits unless doing a fresh full-file replacement with extreme care.
- Always fetch/read current canonical files before editing.
- Never write snippet-only placeholder files to the repo.
- Keep Gradle Java source/target at Java 21 unless explicitly agreed.
- Do not commit `.gradle/`.
- Prefer changes that build after each tranche.
- Use service-layer APIs for poll lifecycle, validation, persistence, results, and verification.
- GUI/session code must delegate authoritative mutations to services.
- Result display changes should generally go through `ResultDisplayFormatter`.
- Witness publication changes should preserve privacy and best-effort delivery semantics.
- Anonymous-ballot canonical payloads must go through `BallotCanonicalizer` (`service.canonical`); never re-inline a private canonical builder in a service.

---

## Main source map

Key files and areas for future sessions:

```text
src/main/java/com/modnmetl/modnvote/ModNVotePlugin.java
```

Plugin bootstrap, dependency wiring, command registration, configuration reload, listener registration.

```text
src/main/java/com/modnmetl/modnvote/commands/PollCommand.java
```

Root `/modnvote` and `/poll` command executor/tab completer.

```text
src/main/java/com/modnmetl/modnvote/service/PollService.java
```

Poll creation, cloning, lifecycle, option mutation, validation.

```text
src/main/java/com/modnmetl/modnvote/service/BallotService.java
```

Ballot submission and participation/ballot verification.

```text
src/main/java/com/modnmetl/modnvote/service/canonical/BallotCanonicalizer.java
```

Shared canonical anonymous-ballot payload construction used by both
`BallotService` and `IntegrityVerificationService`.

```text
src/main/java/com/modnmetl/modnvote/domain/election/
```

Generic linked-offices election definition layer (Tranche 2A): immutable
definition model, `ElectionDefinitionParser`, and `ElectionDefinitionValidator`.
Definition/config only — no voting, persistence of multi-contest content,
counting, or GUI.

```text
src/main/java/com/modnmetl/modnvote/service/ResultService.java
```

Result calculation, including ranked-choice IRV rounds.

```text
src/main/java/com/modnmetl/modnvote/presentation/ResultDisplayFormatter.java
```

Canonical result display formatting for in-game and Discord-facing output.

```text
src/main/java/com/modnmetl/modnvote/publication/WitnessPublicationService.java
```

High-level witness publication orchestration.

```text
src/main/java/com/modnmetl/modnvote/ui/
```

Builder, voting GUI, session, renderer, and listener code.

```text
src/test/java/com/modnmetl/modnvote/
```

Test foundation (added in the 2.2.0 groundwork): canonicalizer golden/stability
tests and schema privacy/non-joinability tests.

```text
src/main/resources/plugin.yml
```

Bukkit/Paper command metadata, aliases, and permissions.

```text
src/main/resources/config.yml
```

Default plugin configuration.

```text
src/main/resources/messages.yml
```

Message strings used by the command/UI layer.

---

## Known technical debt and caution areas

- `PollCommand.java` is large and mixes many command branches; keep changes small and verify tab-completion/help updates when adding commands.
- Some low-level authoring commands remain as recovery paths even though GUI builder is preferred.
- Result output is user-facing and witness-facing; avoid wording that makes first-preference totals look like final ranked-choice totals.
- Any future multi-winner/STV work must not reuse single-winner IRV assumptions blindly.
- Publication should remain decoupled from lifecycle persistence.
- Avoid introducing identity-to-ballot joins for convenience reporting.

---

## Recommended smoke test

After significant changes, run:

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

Also verify:

```text
/poll status
/poll vote <pollId>
```

and tab completion for common admin commands.

---

## New-session rule

Do not rely on prior chat memory if repo docs and code can answer the question.

When starting a new session:

1. Read `README.md`.
2. Read `ARCHITECTURE.md`.
3. Read `CHANGELOG.md`.
4. Read this file.
5. Inspect current code before editing, especially if the task touches commands, result calculation, witness publication, or persistence.

If docs and code disagree, resolve the disagreement explicitly before editing.
