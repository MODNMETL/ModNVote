# Changelog

All notable changes to ModNVote are documented in this file.

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
