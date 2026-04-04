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
Status: LARGELY COMPLETE

Completed:
- ranked single-winner ballot model
- poll/options storage
- ballot submission transaction flow
- recount logic for ranked single-winner polls
- audit event chain
- Java ranked GUI session flow
- Java ranked confirmation flow
- end-to-end ranked GUI submission flow
- real “breed of the month” ranked poll foundation

Still to finish within this milestone:
- Discord checkpoint publication
- ranked GUI polish/stabilisation pass
- configurable GUI message externalisation
- further UX refinement/testing

## Milestone 3 — Legacy poll compatibility + election foundation
Status: NEXT

Planned:
- add legacy-style `YES_NO` poll type with dedicated UI/session flow
- preserve intuitive/simple legacy UX for yes/no polls
- add candidate-centric option metadata
- add richer result reporting
- add election package concept
- add single-winner executive contest support
- add multi-winner STV foundation

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

## Current priority order

1. Ranked GUI polish / stabilisation
2. GUI message/config polish
3. Legacy-style `YES_NO` poll support
4. Audit + external witness publication
5. STV / executive / combined election package