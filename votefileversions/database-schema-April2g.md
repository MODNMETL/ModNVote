# 🗄️ ModNVote 2.0 — Schema (Privacy-Preserving Revision)

## Design Change

The previous ballot model is replaced with:

- participation records (identity-aware)
- anonymous ballots (identity-free)

---

## Tables

### participation_records

Stores voting eligibility + inclusion.

Fields:

- participation_id
- poll_id
- participation_token_hash (UNIQUE per poll)
- submitted_at
- receipt_hash
- client_platform
- ip_hash (optional)
- floodgate_id (optional)

---

### anonymous_ballots

Stores vote content.

Fields:

- anonymous_ballot_id
- poll_id
- ballot_hash
- submitted_at

---

### anonymous_ballot_preferences

Stores ranked selections.

Fields:

- anonymous_ballot_id
- option_id
- rank_position

---

## Removed from ballots

❌ voter_uuid  
❌ identity_key  
❌ identity_type

---

## Critical Rule

> Participation and ballot tables must not be joinable to reveal vote choice.

---

## Integrity Compatibility

This model still supports:

- deterministic recounting
- audit chains
- checkpoint hashing
- external publication
