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
- ip_hash (optional; used only for duplicate-prevention heuristics)
- floodgate_id (optional)

Notes:

- This table is identity-adjacent participation data, not vote content.
- `ip_hash` exists only to support anti-abuse / duplicate-prevention checks.
- This table must never be used to reveal ballot selections.

---

### anonymous_ballots

Stores vote content.

Fields:

- anonymous_ballot_id
- poll_id
- ballot_hash
- receipt_hash
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
- inclusion verification via receipt binding

---

## Poll definition columns: config_json and metadata_json

The `polls` table already includes a `config_json` column and the `poll_options`
table already includes a `metadata_json` column. These are **existing** columns;
no schema change is required to use them.

- `polls.config_json` — an opaque JSON payload describing richer poll/election
  configuration. For existing `YES_NO` and `RANKED_SINGLE_WINNER` polls this is
  simply `"{}"`. From 2.2.0 it is surfaced through the `Poll` domain model and
  `PollDao`, and is the home for the generic linked-offices election definition
  (offices, candidates, dependencies) parsed by `ElectionDefinitionParser`.
- `poll_options.metadata_json` — an opaque JSON payload for per-option/candidate
  metadata (for example, the offices a candidate is eligible for). Defaults to
  `"{}"`. From 2.2.0 it is surfaced through the `PollOption` domain model and
  `PollOptionDao`.

Privacy note:

- These columns describe the *definition* of a poll/election, not vote content,
  and contain no voter identity. They do not affect the participation/anonymous
  ballot separation or the non-joinability guarantee above.

Status note (2.2.0):

- As of the 2.2.0 definition groundwork, these columns are surfaced, parsed, and
  validated, but linked-offices **voting is not implemented**. There is no
  multi-contest ballot content table (for example, no
  `anonymous_ballot_contest_responses`) and no change to the tables above.
