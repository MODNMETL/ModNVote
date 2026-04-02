# ModNVote 2.0 Roadmap

## Current branch goal

Build the 2.0 foundation cleanly and deliberately rather than extending the old 1.x single-round yes/no code.

## Milestone 1 — Foundation

- Create 2.0 architecture docs
- Create core enums and domain model
- Create platform abstraction layer
- Design new schema
- Introduce ballot-first thinking across the codebase
- Keep 1.x files present until 2.0 bootstrap is ready

## Milestone 2 — Horse breed ranked poll

- Implement ranked single-winner ballot model
- Implement poll/options storage
- Implement ballot submission transaction flow
- Implement recount logic for ranked single-winner polls
- Implement audit event chain
- Implement Discord checkpoint publishing
- Implement Java GUI draft + confirmation flow
- Test with real “breed of the month” use case

## Milestone 3 — Election foundation

- Add candidate-centric option metadata
- Add richer result reporting
- Add election package concept
- Add single-winner executive contest support
- Add multi-winner STV foundation

## Milestone 4 — Mayor + Council election

- Implement combined election package
- Finalize sequencing rules
- Add round-by-round reporting
- Add recount/export/reporting for civic elections

## Release strategy

### 2.0.0
- Horse-breed ranked poll ready
- New architecture in place
- Audit/checkpoint foundation in place

### 2.1.x
- Mayor + Council election package
- Multi-winner STV
- richer reporting and election-specific UX

## Current priority order

1. Architecture and documentation
2. Core source scaffold
3. Ballot-first schema design
4. Ranked single-winner poll support
5. Audit + external witness publication
6. GUI/session model
7. Advanced election package