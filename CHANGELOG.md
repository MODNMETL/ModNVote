# Changelog - ModNVote

All notable changes to this project will be documented in this file.

The format is loosely based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

---

## [2.0.0-dev] - Ballot-based audit architecture and poll platform rebuild

> Development branch: `feature/modnvote-2.0-core`

ModNVote 2.0 is a clean architectural reset from the legacy 1.x single Yes/No model into a multi-poll, privacy-first, ballot-based voting platform.

### Added

- Multi-poll architecture with poll records, poll options, lifecycle state, anonymous ballots, ballot preferences, participation records, and audit events.
- Poll lifecycle flow:
  - `DRAFT -> READY -> OPEN -> CLOSED`
- Admin authoring command flow:
  - `/modnvote create <yes_no|ranked_single_winner>`
  - `/modnvote set <pollId> title <title>`
  - `/modnvote set <pollId> description <description>`
  - `/modnvote set <pollId> maxrankings <number>`
  - `/modnvote set <pollId> allowpartial <true|false>`
  - `/modnvote option add/edit/move/remove ...`
  - `/modnvote validate <pollId>`
  - `/modnvote ready <pollId>`
  - `/modnvote open <pollId>`
  - `/modnvote close <pollId>`
- Draft and ready poll deletion through `/modnvote delete <pollId>` for abandoned setup work.
- Ranked poll demo command renamed to `/modnvote rankedpolldemo`.
- Yes/No poll support using protected canonical `yes` and `no` option keys.
- Ranked single-winner poll support with deterministic IRV-style counting.
- Separate GUI/session flows for ranked and Yes/No polls.
- Mandatory GUI confirmation step before ballot submission.
- Pane-less inventory GUI layout to avoid Bedrock angled-pane rendering issues.
- Poll and option description lore wrapping for improved tooltip readability.
- Poll description display in poll information tooltips.
- Anonymous ballot submission through the authoritative ballot service.
- Participation tracking separate from anonymous ballot vote content.
- IP duplicate-prevention heuristics with bypass permission support.
- Player poll participation listing through `/modnvote mypolls`.
- Login notification for open polls the joining player has not yet participated in.
- Participation verification through `/modnvote verify participation <pollId>`.
- Ballot proof phrase verification through `/modnvote verify ballot <pollId> <proof phrase>`.
- Flexible proof phrase input normalisation for spaces, hyphens, and mixed case.
- Player-facing ballot verification output that distinguishes:
  - proof phrase not found
  - matched ballot with failed integrity checks
  - verified ballot and recovered anonymous selection/ranking
- `/modnvote result <pollId>` for closed poll result reporting from anonymous ballots only.
- Lifecycle-aware tab completion for poll IDs and option IDs.
- Service-layer validation for poll readiness and Yes/No semantic integrity.
- Append-only audit-chain model for poll lifecycle and mutation events.
- Ballot hash and proof commitment validation foundations.
- `ModNScheduler` bridge for Paper/Folia-aware player and async scheduling.

### Changed

- Replaced the legacy single-round Yes/No command model with explicit poll IDs and poll lifecycle management.
- Reworked voting around anonymous ballots as the source of truth for vote content.
- Moved command authoring toward a `create -> set -> validate -> ready -> open` workflow.
- Removed sensitive database path output from `/modnvote status`.
- Renamed the old `seedbreed` demo command to the clearer `rankedpolldemo` command.
- Updated README documentation to describe the current 2.0 branch architecture, commands, privacy model, and roadmap.
- Replaced direct Bukkit delayed scheduling in vote renderers with the scheduler bridge.
- Reworked join poll notifications so database checks run asynchronously and player messaging returns to the player scheduler.
- Restored the full bundled `messages.yml` after an accidental partial overwrite, keeping updated 2.0 command wording.
- Clarified option-added messaging so global database option IDs are labelled as internal IDs.

### Privacy and integrity notes

- Identity-aware participation data and anonymous ballot content are intentionally separate.
- `/modnvote verify participation <pollId>` must not reveal vote choices.
- `/modnvote verify ballot <pollId> <proof phrase>` reveals a ballot only to someone possessing the proof phrase, and must not require or use player UUID/IP.
- Results must be reconstructed from anonymous ballot data only, not participation records.
- GUI/session code must not write ballots or lifecycle state directly to the database.
- Join notifications are identity-aware participation checks only and do not inspect ballot content.

### Known development notes

- 2.0 remains a clean-install target; no 1.x database migration is currently supported.
- Schema and APIs may still evolve before merge to the legacy replacement line.
- Future work should consider whether draft/ready deletion should remain hard-delete or become soft-delete/archive if long-term audit preservation is required.
- Result display, admin audit transparency, bulk option authoring, and exportable audit snapshots remain useful next polish areas.
- `/modnvote show` still needs a small code-side polish so displayed option numbering is poll-local while retaining internal option IDs for admin editing/debugging.
- Java 21 remains the intended build target for broad Paper 1.21.x compatibility; Java 25 runtimes can run Java 21 bytecode.

---

## [1.1.5] - GUI voting & stronger privacy

- Added a **GUI-based voting flow** triggered via `/modnvote`.
    - Players now click **Yes** or **No** in a menu instead of typing `/modnvote yes` or `/modnvote no`.
    - This avoids vote choices appearing as chat or command entries in the server console/logs.
- Removed the `/modnvote yes` and `/modnvote no` subcommands entirely.
- Ensured vote handling:
    - Verifies the existing tally's integrity before accepting a new vote.
    - Applies the vote only if the tally is cryptographically valid.
    - Recomputes and stores a new HMAC after the vote to maintain the integrity seal.
- Improved player feedback:
    - On voting, players are told whether the tally was valid before their vote and that the integrity seal has been re-applied after.
    - `/modnvote status` now reports:
        - whether the tally is **cryptographically valid** or not, and
        - whether the tally currently **includes a vote from the viewer**.
- Kept admin tools (`audit`, `fullaudit`, `reset`, `reload`, `verify`) while tightening their messaging around integrity status and compromised tallies.

---

## [1.1.4] - Integrity messaging & docs

- Refined integrity checks and error handling around the HMAC tally seal.
- Improved messages when verification fails, including clearer guidance for staff.
- Updated README and documentation to better describe the privacy and integrity model.
- Added a structured `CHANGELOG.md` to track future changes.

---

## [1.1.3] - Clean rebuild & repository hygiene

- Rebuilt the project cleanly in a fresh Gradle setup.
- Restored and fixed CI workflows for GitHub Actions.
- Tightened SQLite schema and DAO handling.
- Updated README to reflect the production-ready state of the plugin.

---

## [1.1.2] - Initial public release

- First public release of ModNVote as a PaperMC voting plugin.
- Core features:
    - Yes/No voting with per-UUID and per-IP checks.
    - SQLite persistence.
    - Basic cryptographic sealing over tallies and participant lists.
    - Admin audit commands and PlaceholderAPI support.
