# Session Handoff

## Project
ModNVote 2.0

## Current objective
Continue building the 2.0 voting system with a focus on ballot submission, ranked voting support, and a high-integrity user experience.

## Agreed decisions so far

- Use the existing repo, not a separate IntelliJ project
- Keep the existing repo history intact
- Work on branch: `feature/modnvote-2.0-core`
- Treat 2.0 as a clean architectural reset
- Replace the 1.x runtime entirely
- Require clean install for 2.0 releases
- Ballots are the canonical source of truth
- Poll results are derived from ballots
- Discord webhooks will act as an external witness layer
- Design for future Folia compatibility via platform abstraction
- Use a unified GUI interaction model for Java and Bedrock players

## Current status

- 2.0 bootstrap implemented
- SQLite schema implemented
- Poll persistence implemented
- Poll lifecycle implemented (DRAFT → OPEN → CLOSED)
- Audit chain implemented with hash chaining
- Commands implemented:
    - status
    - reload
    - list
    - seedbreed
    - open
    - close

## Verified behaviour

- Polls can be created and stored
- Poll options persist correctly
- Poll lifecycle transitions work
- Audit events are recorded with:
    - sequence numbers
    - previous hash linking
    - SHA-256 event hashing

## CRITICAL UX REQUIREMENT

The voting interface must follow the defined interaction model:

### Ranked voting

- Players select options in order of preference
- Each click assigns the next rank
- Visual ordering must be clear

### Vote submission flow

1. Player builds selection
2. "CAST YOUR VOTE!" only activates when valid
3. Tooltip shows exact vote summary
4. Confirmation screen appears:
    - Green = commit vote
    - Red = cancel
    - Both show vote summary

### Option representation

- Each option is icon-based (Material or player head)
- Tooltip shows description/details

### Safety requirements

- No item dragging or inventory exploits
- All validation server-side
- No partial/invalid ballot commits

### Cross-platform requirement

- Java and Bedrock players must follow the same logical flow
- Rendering may differ, behaviour must not

## Next tasks

- Implement ballot submission system
- Implement ballot DAO and preference storage
- Implement ranking validation rules
- Implement vote confirmation flow
- Extend audit chain to include ballot submissions
- Begin ranked vote counting (IRV)

## Open questions

- Bedrock UI mapping for ranked selection
- Final GUI layout implementation details
- Discord publication payload format
- Identity policy refinement
- Tie-break rules for ranked/STV

## Notes for next session

- Do NOT simplify or bypass the confirmation UX
- Do NOT introduce derived tally storage
- Maintain ballot-first architecture
- Maintain audit chain integrity
- UX clarity is part of the trust model