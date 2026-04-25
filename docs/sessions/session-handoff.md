## Current Objective

Dedicated `YES_NO` voting support is now implemented alongside the ranked Java GUI flow on top of the existing privacy-preserving backend.

The next major implementation target is **closed-poll result reporting** via `/modnvote result <pollId>`, with poll-type-specific output derived from anonymous ballots.

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
- authoritative ranked ballot submission
- authoritative yes/no ballot submission

### Ranked Java GUI/session flow
- `VoteSession`
- `VoteSessionManager`
- `BallotSummaryFormatter`
- `VoteRenderer`
- `JavaInventoryVoteRenderer`
- `ModNVoteInventoryHolder`
- `VoteUiFlow`
- `VoteGuiListener`
- `VoteSubmissionCoordinator`
- `VoteSessionCleanupListener`
- `VoteSessionCloseCleanupListener`

### YES_NO Java GUI/session flow
- `YesNoVoteSession`
- `YesNoVoteSessionManager`
- `YesNoGuiText`
- `YesNoInventoryVoteRenderer`
- `YesNoVoteGuiListener`
- `YesNoVoteSessionCleanupListener`
- `YesNoVoteSessionCloseCleanupListener`

### GUI/config/message improvements now implemented
- plugin injected directly into renderers
- GUI item text externalised through `MessageService` + GUI text composition classes
- optional config-driven UI sounds via `VoteSoundService`
- managed GUI ownership now separated by `VoteUiFlow` so ranked and yes/no listeners do not collide
- confirmation-submit failure now closes the GUI and shows the failure message clearly instead of leaving the player stuck on the confirmation screen
- inventory backgrounds are currently rendered **without glass filler panes** to avoid Bedrock pane-angle visual issues while preserving the same interaction model
- cursor-clearing safeguards added during managed GUI interactions/opening to reduce client-side ghost-item artefacts

### Current voting capabilities
- `/modnvote vote <pollId>` opens the correct Java GUI flow for an OPEN poll:
    - ranked single-winner polls -> ranked session/renderer path
    - yes/no polls -> dedicated yes/no session/renderer path
- ranked flow:
    - player can select ranked options
    - player can remove ranked options
    - player can reset all selections
    - player must pass through a confirmation screen
    - confirmed submission goes through `VoteSubmissionCoordinator`
    - final ballot commit goes through authoritative `BallotService.submitRankedBallot(...)`
- yes/no flow:
    - player can choose one of two options
    - player can clear their current choice
    - player must pass through a confirmation screen
    - confirmed submission goes through `VoteSubmissionCoordinator`
    - final ballot commit goes through authoritative `BallotService.submitYesNoBallot(...)`
- both flows:
    - success closes GUI, removes session, and shows ballot/receipt references
    - submission failure closes GUI and shows the reason clearly

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

## Immediate Next Phase

Implement **`/modnvote result <pollId>`**.

Expected behaviour:
- if the poll is still OPEN, reject and tell the player to try again once it is closed
- if the poll is CLOSED, display:
    - total vote count
    - poll-type-specific result output derived from anonymous ballots
- `YES_NO` result output should show:
    - total votes
    - yes count
    - no count
- ranked single-winner result output should show:
    - total votes
    - winner
    - ordered result/tally output for all options

## Important architectural reminder for next session

Do **NOT** implement `/modnvote verify` in a way that reveals how a player voted unless the privacy model is intentionally revised.

Current 2.0 privacy architecture separates identity-aware participation from identity-free ballot content, and the docs explicitly treat identity-to-vote reconstruction as invalid.

## Additional reminders

- Do NOT reintroduce identity-to-vote linkage
- Do NOT bypass the confirmation UX
- Do NOT move validation out of the service layer
- GUI/session layer must never write directly to the database
- Keep Java and Bedrock voting flows logically aligned
- Current Java inventory UI is intentionally pane-less at present for cross-platform visual consistency
- Current ranked and yes/no Java GUIs are poll-type-specific implementations, not the final universal renderer model