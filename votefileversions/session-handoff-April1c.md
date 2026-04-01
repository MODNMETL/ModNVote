# Session Handoff

## Project
ModNVote 2.0

## Current objective
Build the 2.0 runtime and persistence layer cleanly so the first real poll type, the ranked horse-breed poll, can be implemented on a proper ballot-first foundation.

## Agreed decisions so far

- Use the existing repo, not a separate IntelliJ project
- Keep the existing repo history intact
- Work on branch: `feature/modnvote-2.0-core`
- Treat 2.0 as a clean architectural reset
- Replace the 1.x runtime rather than maintaining transition compatibility
- Ballots will be the canonical source of truth
- Discord webhooks will be used as an external witness mechanism
- Design now for later Folia support via platform abstractions
- First real-world 2.0 target is a ranked horse-breed poll
- Second real-world target is a Mayor + 5-seat Council election

## Current status

- Gradle updated for 2.0 snapshot work
- Project name updated to ModNVote
- Java 21 toolchain retained
- ShadowJar adopted for self-contained release jars
- 1.x runtime replaced with 2.0 bootstrap, schema initializer, and root command scaffold
- Clean-break config now uses `modnvote.db`
- Poll persistence work is now being added

## Current implemented runtime pieces

- 2.0 plugin bootstrap
- platform abstraction with Paper adapter
- schema initializer
- ballot and poll domain model foundation
- root admin command scaffold
- initial poll persistence DAOs

## Next tasks

- Add poll persistence and option persistence
- Add first seed breed poll creation flow
- Add poll listing flow
- Add audit event persistence
- Add first real poll open/close lifecycle
- Begin ranked single-winner counting support

## Open questions

- Exact poll creation UX for admins
- Exact Bedrock renderer strategy for complex ranked ballots
- Final shape of Discord publication payloads
- Tie-break rules for later election engines