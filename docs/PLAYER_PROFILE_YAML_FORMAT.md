# PlayerProfile YAML format

Profiles live under `plugins/IceSMP/player-profiles/<uuid>/`. `manifest.yml` records owner UUID, format, schema, generation, lifecycle status, timestamps and every section schema/revision/digest. Each section is a separate structured YAML file with `format: ICESMP-PLAYER-PROFILE-SECTION`, stable section ID, schema, revision, updated timestamp, `data` and preserved `extensions`.

The serializer is deterministic and key based. There is no Java serialization and no Base64-encoded complete profile. Missing documented optional fields use safe defaults; missing mandatory fields, wrong types, owner/section mismatches, duplicate normalized keys, invalid UTF-8, oversized input and skipped revisions fail closed. Unknown extension namespaces are preserved. Atomic replacement uses a temporary file, file sync and directory sync where supported; orphan temporary files are removed during recovery.

A corrupt section is copied to immutable evidence and quarantined independently. Identity or manifest corruption blocks the whole profile; subsystem corruption blocks only that subsystem unless a documented lossless default is safe. Recovery requires an explicit, idempotent admin operation and never deletes evidence.
