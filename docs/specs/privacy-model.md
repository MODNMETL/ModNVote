# 🔐 ModNVote 2.0 — Privacy Model Specification

## Purpose

Define how ModNVote 2.0 guarantees:

- voter anonymity
- inclusion verification
- tamper-evident integrity

without allowing database inspection to reveal vote choices.

---

## Core Principle

> A database reader must not be able to determine how a player voted.

---

## Separation of Concerns

Voting is split into two independent layers:

### 1. Participation Layer (Identity-aware)

Tracks:
- who has voted
- eligibility enforcement
- anti-abuse auditing

Does NOT store vote content.

---

### 2. Anonymous Ballot Layer (Identity-free)

Stores:
- vote content
- ranked preferences
- ballot hash

Does NOT store identity.

---

## Critical Rule

> No persistent structure may allow identity → vote reconstruction.

---

## Participation Token

A one-way token derived from:

- poll_id
- identity_key
- per-poll secret

Stored as:
- participation_token_hash

Used for:
- duplicate prevention
- inclusion verification

---

## Anonymous Ballot

Stores:

- anonymous_ballot_id
- poll_id
- ballot_hash
- submitted_at

Preferences stored separately:

- option_id
- rank_position

---

## Verification Model

Players can verify:

- their vote is included
- the poll is still valid
- the audit chain is intact

Players cannot reveal their vote via verification.

---

## Edge Cases

Privacy cannot be guaranteed if:

- only one vote exists
- all votes are identical

This is inherent and unavoidable.

---

## Non-Goals

- Preventing a malicious server from modifying code at runtime
- Cryptographic anonymity against a fully hostile execution environment

---

## Summary

| Property            | Guaranteed |
|--------------------|-----------|
| Vote anonymity     | ✅ Yes |
| Inclusion proof    | ✅ Yes |
| Tamper detection   | ✅ Yes |
| Recount accuracy   | ✅ Yes |
