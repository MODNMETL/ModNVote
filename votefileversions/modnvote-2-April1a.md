# ModNVote 2.0 Architecture

## Purpose

ModNVote 2.0 is a ground-up redesign of the original ModNVote plugin.

Its purpose is to provide a secure, auditable, extensible poll and election platform for Paper servers, with architecture choices made from the start to keep later Folia support achievable.

The first production-grade target is a ranked horse-breed selection poll.  
The second is a higher-trust community election system for a Mayor and a 5-seat Council.

## Primary goals

- Replace the 1.x single-round yes/no design with a true multi-poll architecture
- Store ballots as the canonical source of truth
- Support multiple vote types from a shared core
- Provide deterministic recounting
- Provide tamper-evident audit trails
- Publish integrity checkpoints to external witnesses such as Discord webhooks
- Keep Java and Bedrock voting flows aligned conceptually
- Design platform abstractions so Paper works now and Folia adaptation remains practical later

## Non-goals for the first 2.0 milestone

- Full STV implementation in the very first scaffold
- Full Mayor + Council combined election delivery in the first milestone
- Backward-compatible migration of the old 1.x schema into the new model
- Forcing identical renderers for every Java and Bedrock interaction before usability testing

## Core principles

### 1. Ballots are the source of truth
Tallies, winners, recounts, and audit summaries are derived from stored ballots.

### 2. Validation is server-side
The GUI helps the player understand their vote, but the server always re-validates the ballot before commit.

### 3. Auditability is first-class
Every poll and ballot lifecycle event must be representable in an append-only audit chain.

### 4. External witness publication matters
A local seal alone is not enough against a privileged hostile admin. Checkpoints must be publishable to external systems.

### 5. Platform work must be isolated
Business logic must not depend on brittle “main thread only” assumptions. Platform scheduling and player-bound execution are abstracted.

## Supported vote engines

### Phase 1
- Yes / No
- Single-choice plurality
- Ranked single-winner (IRV-style)

### Phase 2
- Ranked multi-winner STV
- Combined executive + council election workflow

## High-level package plan

- `api` — public enums and stable shared concepts
- `domain` — poll, option, ballot, preference models
- `platform` — Paper/Folia-aware scheduling abstractions
- `storage` — DAOs and schema management
- `service` — poll lifecycle, ballot submission, recount, verification
- `integrity` — canonicalization, hashing, checkpointing, verification
- `ui` — vote session state and renderers
- `counting` — vote engine implementations
- `commands` — admin and player command entrypoints

## Initial delivery target

ModNVote 2.0.0 should ship with:
- new poll architecture
- new ballot-first data model
- ranked single-winner support
- horse-breed poll support
- audit chain foundation
- Discord checkpoint publication foundation
- Java GUI draft + confirmation flow
- recount and verification commands

## Folia direction

ModNVote 2.0 is being designed with a platform abstraction layer so that:
- Paper can be supported immediately
- Folia support can be added without rewriting domain logic
- player-bound actions can later be routed through entity-aware schedulers
- global tasks can later be routed through appropriate Folia-safe scheduling

Folia support will only be declared when the implementation is genuinely ready.