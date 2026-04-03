## Current Objective

Backend privacy-preserving ballot foundation is implemented and verified.

## Implemented in this session

- privacy-preserving participation tracking
- anonymous ballot storage
- anonymous ballot preference storage
- privacy-safe audit-chain ballot events
- player inclusion verification
- audit-chain validation
- IP-based duplicate-prevention heuristics
- configurable bypass support for same-household exceptions
- temporary ranked ballot submission test command

## Current Commands

- /modnvote status
- /modnvote reload
- /modnvote list
- /modnvote seedbreed
- /modnvote open <pollId>
- /modnvote close <pollId>
- /modnvote verify <pollId>
- /modnvote testvote <pollId> <optionId1> <optionId2> ...

## Next Major Phase

Implement the GUI/session layer for ranked voting.

## GUI-phase priorities

1. VoteSession model
2. VoteSessionManager
3. Java inventory renderer
4. safe click/drag handling
5. confirmation flow
6. integration with the existing anonymous ballot backend

## Important reminders

- Do NOT reintroduce identity-to-vote linkage
- Do NOT bypass the confirmation UX
- Do NOT move validation out of the service layer
- Keep Java and Bedrock voting flows logically aligned
- Duplicate prevention currently uses identity + IP heuristic with bypass support