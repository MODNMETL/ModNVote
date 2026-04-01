# Session Handoff

## Project
ModNVote 2.0

## Current objective
Build the 2.0 voting system from a clean architectural foundation, focusing on ballot-first integrity, auditability, and support for advanced voting systems such as ranked choice and STV.

## Agreed decisions so far

- Use the existing repo, not a separate IntelliJ project
- Keep the existing repo history intact
- Work on branch: `feature/modnvote-2.0-core`
- Treat 2.0 as a clean architectural reset
- Replace the 1.x runtime entirely (no transition compatibility)
- Require clean install for 2.0 releases
- Ballots will be the canonical source of truth
- Polls and results are derived from ballots, not stored tallies
- Discord webhooks will act as an external witness layer
- Design now for future Folia support via platform abstraction
- Use a unified GUI approach for Java and Bedrock players
- First real-world target is ranked horse-breed poll
- Second target is Mayor + Council election using STV

## Current status

- Gradle configured for Java 21
- ShadowJar enabled for self-contained builds
- 2.0 bootstrap implemented
- Platform abstraction in place
- SQLite schema fully implemented
- Clean-break config using `modnvote.db`

## Current implemented runtime pieces

- Plugin bootstrap
- Database manager + schema initializer
- Poll DAO and PollOption DAO
- AuditEvent DAO with hash-chain structure
- PollService with:
    - poll creation (seed breed poll)
    - poll listing
    - poll lookup by ID
    - poll lifecycle transitions (DRAFT → OPEN → CLOSED)
- Audit events recorded for:
    - POLL_CREATED
    - POLL_OPENED
    - POLL_CLOSED
- Root command with:
    - status
    - reload
    - list
    - seedbreed
    - open
    - close

## Verified behaviour

- Polls can be created and stored
- Poll options are persisted correctly
- Poll lifecycle transitions work as expected
- Audit events are recorded with:
    - per-poll sequence numbers
    - previous hash chaining
    - SHA-256 event hashing
- Audit chain is append-only and deterministic

## Next tasks

- Implement ballot submission system
- Add ballot DAO and preference storage
- Add ballot validation rules (ranking constraints)
- Add vote confirmation workflow (pre-commit + confirm)
- Introduce ballot hashing and receipt generation
- Begin ranked vote counting (single-winner IRV)
- Extend audit chain to include ballot submissions

## Open questions

- Final GUI layout for ranked voting interaction
- Bedrock UI handling for multi-step ranking input
- External publication payload formats (Discord)
- Identity policy refinement (UUID/IP/Floodgate)
- Tie-break rules for ranked and STV systems

## Notes for next session

- Audit chain is now active and must remain append-only
- All future write operations must include audit events
- Do not introduce derived vote tallies into storage
- Continue enforcing ballot-first architecture