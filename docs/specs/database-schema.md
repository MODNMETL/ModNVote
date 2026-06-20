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

### anonymous_ballot_contest_responses

Stores anonymous **multi-contest** vote content for linked-offices ballots
(Tranche 2G). One anonymous ballot may own many rows — one per candidate within
each contest response. This table is anonymous vote content only: it carries no
player UUID, name, IP, Floodgate id, participation token, or receipt, and its
only foreign key is to `anonymous_ballots`. It has **no** link to
`participation_records`.

Fields:

- response_id — INTEGER PRIMARY KEY AUTOINCREMENT
- anonymous_ballot_id — INTEGER NOT NULL, FK → `anonymous_ballots(anonymous_ballot_id)` ON DELETE CASCADE
- office_key — TEXT NOT NULL (the contest/office)
- response_type — TEXT NOT NULL (`RANKED` or `APPROVAL`)
- candidate_key — TEXT NOT NULL
- rank_position — INTEGER NULL (1-based rank for `RANKED` responses; null for approval)
- selection_order — INTEGER NULL (1-based index in canonical contest order for
  `APPROVAL` responses; null for ranked). Canonical order is stored so the rows
  match the Tranche 2F canonical hash input.
- created_at — TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP

Indexes:

- `idx_ab_contest_responses_ballot` on `(anonymous_ballot_id)`
- `idx_ab_contest_responses_ballot_office` on `(anonymous_ballot_id, office_key)`
- `idx_ab_contest_responses_unique_candidate` — UNIQUE on
  `(anonymous_ballot_id, office_key, candidate_key)` (prevents duplicate candidate
  rows within one office response)

Status: storage infrastructure only. Written by the internal, Bukkit-free
`LinkedBallotStorageService` (not a player voting path; not called in
production). There is no linked-offices voter GUI, vote session, voting command,
counting, or result calculation. Existing `YES_NO`/`RANKED_SINGLE_WINNER` storage
in `anonymous_ballot_preferences` is unchanged.

Integrity usage (Tranche 2H, no schema change): these rows are the recount input
for linked-offices integrity verification. `IntegrityVerificationService`
(delegating to `LinkedOfficesIntegrityVerifier`) reconstructs each anonymous
ballot from its rows here, re-canonicalises it, and recompares the recomputed
`ballot_hash` against `anonymous_ballots.ballot_hash`, so an offline edit to these
rows is detected. The read is by `anonymous_ballot_id` only; it never joins to
`participation_records`, and integrity failure reports never include identity.
Only `ballot_hash` is recomputed — `ballot_commitment_hash` (which binds the
voter's proof phrase) stays in the bearer-token proof path.

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
  From Tranche 2C it is also written: `PollDao.updatePollConfigJson` updates only
  this column, and `PollService.updatePollConfigJson` writes it only for
  `LINKED_OFFICES` polls in DRAFT, only after the definition parses and validates.
  Each successful write records a `POLL_CONFIG_UPDATED` audit event containing the
  poll id, actor, declared model, a SHA-256 hash of the definition, and its byte
  length — never the raw definition JSON.
- `poll_options.metadata_json` — an opaque JSON payload for per-option/candidate
  metadata (for example, the offices a candidate is eligible for). Defaults to
  `"{}"`. From 2.2.0 it is surfaced through the `PollOption` domain model and
  `PollOptionDao`.

Privacy note:

- These columns describe the *definition* of a poll/election, not vote content,
  and contain no voter identity. They do not affect the participation/anonymous
  ballot separation or the non-joinability guarantee above.

Status note (2.2.0):

- As of the 2.2.0 groundwork (through Tranche 2C), these columns are surfaced,
  parsed, validated, and — for `config_json` on DRAFT `LINKED_OFFICES` polls —
  written via authoring commands, but linked-offices **voting is not
  implemented**. There is no multi-contest ballot content table (for example, no
  `anonymous_ballot_contest_responses`) and no change to the tables above.
