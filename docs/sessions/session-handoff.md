# Session Handoff

## Project
ModNVote 2.0

## Current objective
Create the 2.0 architecture scaffold and document the core design so that later chat sessions can continue the project cleanly and consistently.

## Agreed decisions so far

- Use the existing repo, not a separate IntelliJ project
- Keep the existing repo history intact
- Work on branch: `feature/modnvote-2.0-core`
- Treat 2.0 as a clean architectural reset
- Do not extend the 1.x single-round yes/no model
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
- Initial 2.0 docs and source scaffold are being created

## Important caution

Do not delete the old 1.x runtime files yet.  
The first 2.0 steps are additive until the new bootstrap and runtime path are ready.

## Next tasks

- Add 2.0 docs
- Add core enums and domain classes
- Add Paper platform abstraction
- Design new database schema
- Begin replacement of old runtime bootstrap once the new scaffold is stable

## Open questions

- Exact poll creation UX for admins
- Exact Bedrock renderer strategy for complex ranked ballots
- Final shape of Discord publication payloads
- Tie-break rules for later election engines