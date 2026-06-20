# Changelog

All notable changes to ModNVote are documented in this file.

## [2.2.0] - Unreleased

### Summary

Groundwork for the ModNVote 2.2.0 development stretch, delivered in small tranches. This is preparation only; Linked Offices **voting is not implemented yet**. There is no multi-contest ballot storage, GUI/session flow, or counting pipeline.

### Added

- **Tranche 1 — canonicalization foundation:**
  - Shared `BallotCanonicalizer` (`service.canonical`) as the single source of truth for anonymous-ballot canonical payload construction, used by both `BallotService` (submission) and `IntegrityVerificationService` (recount/verification).
  - Test foundation under `src/test`: golden/stability tests locking the canonical payload format byte-for-byte for existing `YES_NO` and `RANKED_SINGLE_WINNER` ballots, plus schema privacy/non-joinability regression tests.
- **Tranche 2A — election definition infrastructure (definition/config only):**
  - Generic election-definition domain model under `domain.election`: `ElectionDefinition`, `ContestDefinition`, `CandidateDefinition`, `OfficeDependencyRule`, plus `CountingMethod` (`IRV`, `APPROVAL_TOP_N`) and `OfficeDependencyType` (`EXCLUDE_WINNERS`). Offices/contests/candidates/dependencies are fully generic; Mayor/Council are examples only, never hardcoded.
  - `ElectionDefinitionParser` parsing `polls.config_json` into an `ElectionDefinition` (preserves office and candidate order; converts `excludeWinnersFrom` into `EXCLUDE_WINNERS` dependencies; rejects unknown models and counting methods).
  - `ElectionDefinitionValidator` enforcing generic structural rules (unique keys, non-blank names, seats, IRV single-seat, approval `maxSelections`, candidate eligibility, dependency endpoints, acyclic dependency graph, enough eligible candidates per seat count).
  - `config_json` surfaced through the `Poll` domain model and `PollDao`; `metadata_json` surfaced through the `PollOption` domain model and `PollOptionDao` — both via backward-compatible constructors that default to `"{}"`.
  - Tests for the parser, validator, and `config_json`/`metadata_json` persistence/defaulting.
- **Tranche 2B — read-only admin definition validation:**
  - `ElectionDefinitionService` (`service`) — a read-only boundary that parses and validates a poll's `config_json`, returning a structured `ElectionDefinitionValidationResult` (`valid`, optional `definition`, `issues`, optional `rawModel`) instead of throwing. No persistence, lifecycle, identity, or ballot involvement.
  - `/modnvote validate-definition <pollId>` admin command (permission `modnvote.admin.poll.create`): reads `config_json`, validates it, and reports valid/invalid + issues, missing/non-linked model, or poll-not-found. Read-only — never changes status, writes the database, or opens a GUI.
  - Reserved, **non-votable** `PollType.LINKED_OFFICES`. Guards reject it from voting (vote command + session layer), result calculation (`ResultService`), and authoring/lifecycle (cannot be created or readied for voting). Existing `PollType` values and stored data remain compatible.
  - Tests for the service (valid/empty/malformed/unknown-model/structurally-invalid) and guards (result rejection, YES_NO still works, no vote session for linked offices, `PollType` parsing backward compatible).
- **Tranche 2C — linked offices authoring + lifecycle readiness (still non-votable):**
  - Admins can now create a `LINKED_OFFICES` poll in DRAFT (`/modnvote create linked_offices`), give it a definition, validate it, and mark it READY — but it still **cannot be opened, voted, or resulted**.
  - `/modnvote config <pollId> set <json>` and `/modnvote config <pollId> import <file>` set a linked-offices definition on a DRAFT poll. Inline JSON is joined from the remaining arguments; file import reads UTF-8 JSON from `plugins/ModNVote/definitions/<file>` with path-traversal rejection. Definitions are parsed and validated before anything is written; invalid definitions are rejected and not persisted.
  - `PollService.updatePollConfigJson(pollId, configJson, actor)` and `PollDao.updatePollConfigJson(...)` write only `config_json`. Config definitions are accepted only for `LINKED_OFFICES` polls and only while DRAFT; other poll types reject config updates. A `POLL_CONFIG_UPDATED` audit event records poll id, actor, declared model, a SHA-256 hash of the definition, and its byte length — never the raw definition.
  - Lifecycle readiness: `validatePollDefinition` and `readyPoll` now accept `LINKED_OFFICES` only when its `config_json` parses and validates through `ElectionDefinitionService`. An explicit `openPoll` guard rejects `LINKED_OFFICES` even when READY ("Linked Offices voting is not implemented yet"), in addition to the existing vote/session/result guards.
  - `/modnvote validate-definition <pollId>` now warns when a poll's type and its declared config model disagree.
  - Example definition `docs/examples/linked-offices-mayor-council.json` (generic Mayor IRV + Council approval top-N, example only).
  - New `LinkedOfficesDefinitionFileLoader` (safe, Bukkit-free file reader) with path-traversal tests; service tests for config update (valid/invalid/non-draft/non-linked/audit), creation, and lifecycle readiness/open guard; a DAO config-update persistence test.
- **Tranche 2D — linked offices admin builder GUI (definition editing only, still non-votable):**
  - `/modnvote edit-definition <pollId>` opens an in-game GUI builder/editor for a `LINKED_OFFICES` poll's `ElectionDefinition`. Screens: Main (counts + validity), Offices (list/create/edit/delete), Office editor (key, display name, seats, counting method, max selections, allow-abstain), Candidates (list/create/edit/delete), Candidate editor (key, display name, eligible offices), Dependencies (list/add/delete EXCLUDE_WINNERS), plus Validate and Save.
  - The GUI is an editor for `ElectionDefinition`, never a separate source of truth. It loads an existing definition by parsing `config_json`, and Save serializes the edit buffer and writes it **only** through `PollService.updatePollConfigJson` (no DAO bypass). Validate reuses `ElectionDefinitionService` (no duplicated validation logic). Invalid definitions cannot be saved. The JSON `set`/`import` paths from Tranche 2C remain fully supported.
  - New `ElectionDefinitionSerializer` (`domain.election`) — deterministic, order-stable inverse of `ElectionDefinitionParser`; `parse(serialize(x))` equals `x` (including `excludeWinnersFrom` dependency round-tripping).
  - Bukkit-free, unit-tested core: `LinkedOfficesBuilderState` (edit buffer), `LinkedOfficesBuilderService` (validate/serialize/load/save bridge to `PollService`), `LinkedOfficesBuilderSession`. GUI rendering/click/chat handling live in `ui.builder.election` and reuse the Folia-aware `ModNScheduler`.
  - Tests: serializer (round-trip, deterministic ordering, dependency serialization, empty definition), builder state (office/candidate create+delete, derived membership, dependency add/remove, valid/invalid validation state), and builder-service integration (valid save persists via `PollService`, invalid save rejected, save honours `PollService` guards, load round-trips, blank config opens empty buffer).
  - Hardening: the legacy `poll_options` authoring workflow (`addOption`/`updateOptionName`/`updateOptionDescription`/`moveOption`/`removeOption`) is rejected for `LINKED_OFFICES` polls, so an `ElectionDefinition` in `config_json` is the single source of truth for linked-offices candidates.
- **Tranche 2E — linked offices execution model foundation (in-memory only, still non-votable):**
  - New `domain.election.execution` package formalising, purely in memory, how a linked-offices vote is represented before the anonymous ballot layer is touched. No ballot persistence, no schema changes, no counting, no voter GUI.
  - Response model: sealed `ContestVote` with `RankedContestVote` (ordered preferences) and `ApprovalContestVote` (selections), and `LinkedElectionBallot` (an `ElectionDefinition` plus the voter's per-contest responses). Immutable records throughout.
  - `LinkedElectionBallotValidator` validating a ballot against its definition and returning a structured `BallotValidationResult` (`BallotValidationIssue` + `BallotValidationCode`) — it never throws for ordinary voter mistakes. Rules: office must exist; vote shape must match the contest's counting method; no duplicate response per office; ranked/approval candidates must exist, not duplicate, and be eligible; approval selections must not exceed `maxSelections`; definition dependency references must resolve.
  - `ElectionDependencyEvaluator` interpreting generic dependency rules **without applying any outcomes**: `determineCandidatesEligibleForContest(...)` (structural eligibility in contest order) and `evaluateDependencies(...)` (deterministic `DependencyEvaluation`: unresolved references, per-office preceding offices, a topological counting order, cycle detection).
  - `LinkedElectionCanonicalModel` fixing the deterministic ordering for future ballot hashing (contest order = definition order; ranked candidate order preserved; approval selections normalised to contest order; one response per office) and reducing a ballot to a `CanonicalBallot`/`CanonicalContestResponse`. This is canonicalization planning, not the final hash implementation.
  - Tests (24): ballot validation (valid mayor ranking, valid council approval, invalid office, invalid candidate, duplicate ranked/approval candidate, exceeds maxSelections, ineligible candidate, wrong vote type, duplicate response, unresolved dependency, never-throws/deterministic), dependency evaluator (resolves dependencies, missing references, deterministic output, eligible candidates, unknown office), canonical model (stable contest/candidate ordering, response ordering, ranked preserved, approval normalised, deterministic across input orderings), all driven by a reusable Mayor/Council fixture.
- **Tranche 2F — linked offices canonical ballot payload (hashing input only, still non-votable):**
  - New `BallotCanonicalizer.canonicalLinkedOfficesBallotPayload(Poll, ElectionDefinition, LinkedElectionBallot, Instant)` building a deterministic, versioned canonical payload for a multi-contest linked-offices ballot — the exact byte sequence a later tranche will hash for the ballot hash, ballot commitment, proof-phrase verification, and recount. It stores, submits, and counts nothing, and nothing in production calls it yet.
  - Distinct payload version `rule_snapshot_version=linked_offices_v1`, separate from the single-contest `v2`. The existing single-contest path is untouched, so `YES_NO` and `RANKED_SINGLE_WINNER` canonical payloads remain byte-for-byte identical.
  - Validation gate: the payload builder validates the ballot with `LinkedElectionBallotValidator` first and throws `IllegalArgumentException` (naming the failing issue codes/messages) for an invalid ballot, rather than silently canonicalizing it. It also rejects a non-`LINKED_OFFICES` poll and a definition that does not match the ballot's own definition.
  - Payload content: `poll_id`, `poll_type=LINKED_OFFICES`, `election_model=LINKED_OFFICES`, `submitted_at` (epoch millis), the payload version, `contest_count`, and one line per contest (`contest=<office>;method=<METHOD>;type=<RANKED|APPROVAL>;candidates=<keys>`), in definition contest order via `LinkedElectionCanonicalModel`. Ranked preferences are preserved (significant); approval selections are normalised to contest candidate order (selection order not significant). It depends only on poll rule context, the election definition, and anonymous ballot content — no player UUID/name/IP, session state, or participation token.
  - Tests (10): golden payload string, determinism across distinct list instances, contest-response input order does not affect output (mayor before council), approval selection order does not affect output (normalised to contest order), ranked order significance, approval-set significance, invalid ballots rejected (unknown candidate, exceeds maxSelections, ineligible candidate — the spec's "bob in council" example), non-linked poll rejected, no voter identity in the payload, and SHA-256 determinism/content-sensitivity (input order does not change the hash; ranked order and approval set do).

### Changed

- `BallotService` and `IntegrityVerificationService` now delegate canonical payload construction to the shared `BallotCanonicalizer` instead of duplicating private builder methods. Canonical output is unchanged (byte-for-byte identical, `rule_snapshot_version=v2`), so existing stored ballot hashes and proof commitments continue to verify.
- `Poll` now carries `configJson` and `PollOption` now carries `metadataJson`, both read from existing columns. Existing call sites are unaffected via backward-compatible constructors.
- Gson is used for election-definition parsing. It is `compileOnly` (provided by the Paper runtime) and on the test classpath only; it is **not** shaded into the plugin jar.
- Project version bumped to `2.2.0`.

### Notes

- No database schema changes. `config_json` and `metadata_json` already existed in the schema and are now surfaced/used.
- No changes to proof-phrase generation, participation token hashing, GUI/session behaviour, or poll lifecycle.
- Existing `YES_NO` and `RANKED_SINGLE_WINNER` polls continue to work unchanged.
- Linked Offices voting, multi-contest anonymous-ballot storage, counting, and result calculation are deliberately **not** implemented yet. Through Tranche 2D admins can create a `LINKED_OFFICES` poll, author its definition (via JSON `set`/`import` or the in-game `edit-definition` builder GUI), validate it, and mark it READY — but it cannot be opened, voted, or resulted. The builder GUI edits definition data only; it is not a voter GUI. The reserved `PollType.LINKED_OFFICES` is non-votable and guarded out of every voting/result/open path. The definition layer is generic so later tranches can parse, validate, and execute elections without rework.
- Tranche 2E adds the `domain.election.execution` model as **pure in-memory types**. It introduces no database tables, no `SchemaInitializer` changes, no ballot submission, no vote sessions, no counting, no tallying, and no voter GUI. Nothing in this tranche writes to the anonymous ballot layer or touches the participation/privacy model; it only formalises how a linked-offices vote, its validation, its dependency interpretation, and its canonical ordering are represented for later tranches.
- Tranche 2F adds only the **linked-offices canonical payload** (the deterministic hashing input). It is not wired to submission, storage, sessions, or counting; nothing in production constructs or hashes a linked-offices payload yet. There are no database/schema changes, no `SchemaInitializer` changes, no `anonymous_ballot*` changes, and no changes to participation-token hashing or proof-phrase generation. The existing single-contest canonical payloads (`rule_snapshot_version=v2`) are unchanged byte-for-byte; the linked-offices payload uses its own `linked_offices_v1` version and a separate method.

## [2.1.1] - 2026-05-09

### Added

- Transparent round-by-round IRV result reporting for `RANKED_SINGLE_WINNER` polls.
- Final IRV round reporting so public output distinguishes first-preference totals from the decisive final round.
- Ranked-choice elimination tracking in result output.
- Exhausted ballot reporting where applicable.
- Shared result presentation formatter for in-game and Discord-facing result output.
- `/modnvote publishresult <pollId>` for manually republishing a CLOSED poll result to configured witness webhooks.
- Tab-completion support for the `publishresult` command and CLOSED poll-id completions.

### Changed

- Ranked-choice `/modnvote result <pollId>` output now shows:
  - poll winner
  - final winner tally
  - first preference round
  - each IRV round
  - eliminated option per non-final round
  - exhausted ballots where applicable
- Discord poll-closed witness messages for ranked polls now publish:
  - winner
  - final IRV round
  - full IRV round breakdown
- Ranked-choice result display no longer presents first-preference totals as a generic final “Result Summary”.
- In-game ranked winner wording changed to `Poll winner:` for clarity.
- `ResultService.PollResult` now includes ranked-choice round snapshots, final winner tally, and exhausted ballot count.

### Fixed

- Fixed misleading ranked-choice result presentation where the reported winner was correct but only first-preference counts were shown.
- Fixed empty ranked polls being able to resolve to a winner when no ballots had been cast.
- Fixed `publishresult` discoverability by adding it to command help/tab-completion metadata.

### Notes

- Existing ranked poll results can be republished after upgrading with:

```text
/modnvote publishresult <pollId>
```

- Example:

```text
/modnvote publishresult 2
```

## [2.1.0] - 2026-04-26

### Added

- `/modnvote clone <sourcePollId>` for cloning existing polls into new editable drafts.
- `/modnvote checkpoint <pollId>` for manual witness checkpoint publication.
- `/poll` as a short alias for `/modnvote`.
- External witness publication via configured Discord-compatible webhooks.
- Poll opened witness publication.
- Poll closed witness publication with result summary.
- Automatic integrity checkpoint publication every configured ballot interval.
- Clear first-run config guidance for webhook list formatting.

### Changed

- Poll lifecycle commands can now publish best-effort external witness events.
- Vote submission can now trigger automatic privacy-safe checkpoint publication.
- Config comments clarify how to configure one or more webhook URLs.

### Security / Privacy

- Witness publication does not include player names, UUIDs, IP addresses, proof phrases, participation receipts, or per-player vote content.
- Webhook delivery failures are logged without exposing full webhook URLs and do not affect poll lifecycle or ballot persistence.

## [2.0.0] - 2026-04-25

### Summary

ModNVote 2.0 replaces the original Yes/No-only plugin with a privacy-first, audit-aware polling system.

This release introduces GUI-driven poll creation, ranked single-winner voting, improved Yes/No poll handling, anonymous ballot storage, participation verification, ballot proof verification, and lifecycle controls.

### Added

- GUI Poll Builder for ranked single-winner polls
- GUI Poll Builder support for Yes/No polls
- `/modnvote create ranked_single_winner <optionCount>`
- `/modnvote create yes_no`
- `/modnvote edit <draftPollId>`
- `/modnvote guide`
- Draft poll creation with placeholder ranked options
- Service-authoritative poll title editing
- Service-authoritative poll description editing
- Service-authoritative option name editing
- Service-authoritative option description editing
- Builder chat input prompts with field-specific context
- Wrapped multiline lore for poll and option descriptions
- Red/green builder completion indicators
- Builder validation status item
- READY action from the builder GUI
- Builder Cancel action (non-destructive)
- Allow Partial Rankings toggle in GUI
- Max Rankings cycle control in GUI
- `/modnvote mypolls`
- `/modnvote verify participation <pollId>`
- `/modnvote verify ballot <pollId> <proofPhrase>`
- Anonymous ballot verification using proof phrases
- Participation integrity checks
- Audit chain integrity checks
- Ballot hash and commitment verification
- Ranked voting GUI
- Yes/No voting GUI
- Mandatory vote confirmation UX
- Join notifications for open polls
- Folia-aware scheduling via `ModNScheduler`
- Poll-local numbering for options
- Cleaner command help and tab-completion

### Changed

- Replaced command-heavy authoring with GUI-first builder workflow
- Results calculated from anonymous ballots only
- Participation records separated from vote content
- Verification commands aligned with privacy model
- Ranked vote icons stabilised (paper items retained)
- Builder descriptions wrapped and colour-consistent
- Builder placeholders now red, completed fields green
- Builder READY reflects placeholder validation
- Command help simplified and focused on GUI workflow
- Low-level commands hidden from normal help
- GUI design avoids glass panes for Bedrock compatibility

### Fixed

- `.gradle` cache tracking issues
- `/modnvote show` option numbering
- Builder persistence via `PollService`
- Builder refresh after edits
- Option description updates in GUI
- Lore wrapping colour loss
- Premature READY state
- Yes/No builder option duplication issue
- Ranked vote icon instability
- Builder Cancel placeholder
- Command guide placement
- Ranked create tab-complete hints

### Architecture

- Introduced builder session system
- Added renderer/listener/input manager structure
- Integrated `ModNScheduler`
- Clean separation of GUI, service, and persistence layers

### Privacy and integrity

- Identity and ballot content separated
- Anonymous ballots are source of truth
- Participation prevents duplicates without exposing votes
- Verification preserves privacy boundaries

### Migration notes

- 2.0 supersedes v1
- No migration from 1.x supported
- GUI builder replaces legacy setup commands

### Recommended smoke test

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
