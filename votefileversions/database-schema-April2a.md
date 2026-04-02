# Database Schema Specification

## Design intent

The 2.0 schema replaces the 1.x participant/tally-only model with a ballot-first architecture.

The stored ballot is the canonical record.  
Derived counts and winners must always be reproducible from stored ballots.

## Planned core tables

### `polls`
Stores poll definitions.

Suggested fields:
- `poll_id`
- `slug`
- `title`
- `description`
- `poll_type`
- `status`
- `created_by`
- `created_at`
- `opens_at`
- `closes_at`
- `max_rankings`
- `seat_count`
- `allow_partial_ranking`
- `requires_confirmation`
- `identity_policy`
- `config_json`

### `poll_options`
Stores the selectable options for a poll.

Suggested fields:
- `option_id`
- `poll_id`
- `key`
- `display_name`
- `description`
- `display_order`
- `icon_type`
- `icon_value`
- `metadata_json`

### `ballots`
Stores one committed ballot per voter per poll.

Suggested fields:
- `ballot_id`
- `poll_id`
- `voter_uuid`
- `voter_name_snapshot`
- `identity_key`
- `identity_type`
- `ip_hash`
- `floodgate_id`
- `submitted_at`
- `client_platform`
- `ballot_hash`
- `receipt_hash`
- `is_valid`
- `invalid_reason`

### `ballot_preferences`
Stores ranked or ordered selections.

Suggested fields:
- `ballot_id`
- `option_id`
- `rank_position`

### `audit_events`
Append-only lifecycle and integrity events.

Suggested fields:
- `event_id`
- `poll_id`
- `sequence_no`
- `event_type`
- `canonical_payload`
- `prev_hash`
- `event_hash`
- `created_at`

### `seal_checkpoints`
Checkpoint summaries suitable for external publication.

Suggested fields:
- `checkpoint_id`
- `poll_id`
- `sequence_no`
- `ballot_count`
- `state_hash`
- `signature_or_mac`
- `created_at`

### `external_publications`
Tracks Discord or other witness publications.

Suggested fields:
- `publication_id`
- `poll_id`
- `checkpoint_id`
- `target_name`
- `target_type`
- `response_ref`
- `published_at`
- `success`
- `error_message`

## Canonical truth

The canonical truth for a poll is:
1. the poll definition
2. the poll options
3. the committed ballots
4. the ordered ballot preferences
5. the append-only audit chain

Aggregate counts are derived data.

## Migration note

No automatic migration from the 1.x schema is assumed at this stage.  
The 2.0 schema is a deliberate clean redesign.