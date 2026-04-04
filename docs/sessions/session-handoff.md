## Current Objective

Ranked Java GUI voting is now implemented end-to-end on top of the existing privacy-preserving backend.

## Implemented up to this milestone

### Backend / integrity foundation
- privacy-preserving participation tracking
- anonymous ballot storage
- anonymous ballot preference storage
- privacy-safe audit-chain ballot events
- player inclusion verification
- audit-chain validation
- ballot-content integrity verification
- IP-based duplicate-prevention heuristics
- configurable bypass support for same-household exceptions

### Ranked Java GUI/session flow
- `VoteSession`
- `VoteSessionManager`
- `BallotSummaryFormatter`
- `VoteRenderer`
- `JavaInventoryVoteRenderer`
- `ModNVoteInventoryHolder`
- `VoteGuiListener`
- `VoteSubmissionCoordinator`
- `VoteSessionCleanupListener`
- `VoteSessionCloseCleanupListener`

### Current ranked GUI capabilities
- `/modnvote vote <pollId>` opens a ranked vote GUI for an OPEN poll
- player can select ranked options
- player can remove ranked options
- player can reset all selections
- player must pass through a confirmation screen
- confirmed submission goes through `VoteSubmissionCoordinator`
- final ballot commit goes through authoritative `BallotService.submitRankedBallot(...)`
- success closes GUI, removes session, and shows ballot/receipt references
- failure leaves session intact and refreshes GUI

### Current Commands
- `/modnvote status`
- `/modnvote reload`
- `/modnvote list`
- `/modnvote seedbreed`
- `/modnvote open <pollId>`
- `/modnvote close <pollId>`
- `/modnvote verify <pollId>`
- `/modnvote vote <pollId>`
- `/modnvote testvote <pollId> <optionId1> <optionId2> ...`

## Next Major Phase

Ranked GUI polish/stabilisation, followed by legacy-style `YES_NO` poll support as a separate dedicated UI/session path.

## Ranked GUI polish/stabilisation priorities

1. replace plugin-name lookup in `JavaInventoryVoteRenderer` with direct plugin injection
2. externalise GUI item titles/lore into configurable GUI message sources
3. polish tooltip clarity, button wording, and visual feedback
4. optionally add sound/feedback cues
5. clean up any remaining renderer/listener wiring rough edges

## After ranked polish

Implement legacy-style `YES_NO` poll support with a dedicated renderer/session path that preserves the simpler legacy UX rather than forcing Yes/No into ranked GUI assumptions.

## Important reminders

- Do NOT reintroduce identity-to-vote linkage
- Do NOT bypass the confirmation UX
- Do NOT move validation out of the service layer
- GUI/session layer must never write directly to the database
- Keep Java and Bedrock voting flows logically aligned
- Duplicate prevention currently uses identity + IP heuristic with bypass support
- Current ranked Java GUI is one poll-type-specific flow, not the final universal renderer model