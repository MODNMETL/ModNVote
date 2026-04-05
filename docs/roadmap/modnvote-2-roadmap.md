# ModNVote 2.0 Roadmap

## Current branch goal

Build ModNVote 2.0 as a ballot-first, auditable voting platform with clean poll-type-specific UX paths rather than extending the old 1.x single-round yes/no code.

## Milestone 1 — Foundation
Status: COMPLETE

- Create 2.0 architecture docs
- Create core enums and domain model
- Create platform abstraction layer
- Design new schema
- Introduce ballot-first thinking across the codebase
- Keep 1.x files present until 2.0 bootstrap is ready

## Milestone 2 — Horse breed ranked poll
Status: COMPLETE ENOUGH TO MOVE ON

Completed:
- ranked single-winner ballot model
- poll/options storage
- ballot submission transaction flow
- recount logic for ranked single-winner polls
- audit event chain
- Java ranked GUI session flow
- Java ranked confirmation flow
- end-to-end ranked GUI submission flow
- ranked GUI text/config externalisation
- optional GUI sound feedback
- renderer/listener cleanup
- pane-less inventory background approach for improved Java/Bedrock visual consistency
- real “breed of the month” ranked poll foundation

Still later if needed:
- further UI polish refinements
- alternative Bedrock-specific rendering if future testing justifies it
- Discord checkpoint publication

## Milestone 3 — Legacy poll compatibility + election foundation
Status: IN PROGRESS

Completed in this milestone:
- dedicated legacy-style `YES_NO` poll type UI/session flow
- separate yes/no session manager, renderer, listener, and GUI text layer
- authoritative yes/no ballot submission path
- poll-type-specific GUI ownership via `VoteUiFlow`

Next within this milestone:
- `/modnvote result <pollId>` closed-poll reporting
- yes/no poll admin creation/seeding path
- candidate-centric option metadata
- richer result reporting
- election package concept
- single-winner executive contest support
- multi-winner STV foundation

## Current priority order

1. `/modnvote result <pollId>` for CLOSED polls only
2. yes/no poll admin creation / seeding support
3. audit + external witness publication
4. richer reporting
5. STV / executive / combined election package

## Milestone 4 — Mayor + Council election
Status: LATER

- implement combined election package
- finalize sequencing rules
- add round-by-round reporting
- add recount/export/reporting for civic elections

## Release strategy

### 2.0.0
- horse-breed ranked poll ready
- new architecture in place
- audit/checkpoint foundation in place
- ranked Java GUI voting flow
- privacy-preserving verification flow

### 2.1.x
- legacy-style yes/no support in 2.0 architecture
- Mayor + Council election package
- multi-winner STV
- richer reporting and election-specific UX