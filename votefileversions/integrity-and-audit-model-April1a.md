# Integrity and Audit Model

## Problem statement

A local database seal alone does not provide strong assurance if a hostile admin has offline access to the same machine that stores both the database and the signing material.

ModNVote 2.0 therefore uses a stronger integrity model:
- ballot-first canonical storage
- append-only audit chain
- checkpoint hashing
- external witness publication
- deterministic recounting

## Ballot integrity

Each ballot will be canonicalized and hashed.  
The canonical form will include:
- poll id
- voter identity key
- ordered preferences
- submission timestamp
- rule snapshot version

## Audit chain

Important lifecycle events will be recorded in sequence:
- poll created
- poll opened
- ballot submitted
- poll closed
- recount performed
- result published

Each event will include:
- `prev_hash`
- `event_hash`

This creates a tamper-evident chain.

## Checkpoints

Periodic checkpoints will summarize:
- poll id
- checkpoint sequence
- current ballot count
- current audit tip
- canonical state hash

These checkpoints are suitable for publication to external witnesses.

## External witness publication

Discord webhook publication is part of the trust model.

Expected publication moments:
- poll opened
- key checkpoint intervals
- poll closed
- final result confirmed

Published material must avoid exposing private vote contents while still allowing external proof that the recorded state existed at a known time.

## Recount model

Final results must always be reproducible from stored ballots.

This means:
- no tally row is treated as the ultimate truth
- counting engines must be deterministic
- verification must operate from ballots and poll rules

## Threat model position

ModNVote 2.0 aims to be strongly tamper-evident and operationally trustworthy.

It does not assume that a single machine can make hostile privileged access magically impossible.  
Instead it raises the cost of fraud and makes silent tampering much harder through external witnesses and deterministic recounting.