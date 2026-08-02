# Profile v2 operations runbook

## Authority model

Profile v2 is always enabled and is the sole class/spec authority. There is no migration mode, legacy fallback,
dual-write period or kill switch. `class-spec-rework.dependencies.enforce` controls dependency validation only; it
does not select another runtime.

## Normal states

| State | Gameplay | Operator action |
|---|---:|---|
| `READY` | allowed when session is READY and no operation is pending | none |
| `REVIEW` | blocked | inspect diagnostic and recovery-required operation |
| `QUARANTINED` | blocked | preserve evidence and use explicit recovery |
| `SEALED` loadout | that loadout blocked | restore all eligible gates or use documented admin action |
| reconciliation required | blocked | inspect runtime failure, retry cleanup/reconnect |

## Diagnostics

Use `/spec info` for schema, revision, class, slots, complete seal reasons and session block detail. Repository logs
include owner UUID, bounded error detail and evidence ID; raw payloads are not dumped into logs.

## Quarantine recovery

1. Stop repeated login attempts for the target while investigating.
2. Record the owner UUID, evidence ID, quarantine reason and filesystem backup.
3. Confirm that the evidence/marker belongs to the target UUID.
4. Run:

   ```text
   /spec recover <player|uuid> confirm
   ```

   Permission: `icesmp.admin.spec.recover`.
5. The command preserves evidence, records an audit ID and creates a clean, inactive revision-0 profile.
6. Have the player reconnect and choose class/spec again.

Do not use normal reset for a quarantined profile and do not copy another player's profile file into place.

## Runtime reconciliation failure

A durable profile commit and runtime application are separate reported phases. If persistence succeeded but
spell/pet/form cleanup or rebuild failed, the session is marked reconciliation-required. Do not refund or replay
economic operations manually. Correct the scheduler/runtime cause, perform idempotent cleanup, then reconnect.

## Respec recovery

On startup/login the recovery protocol compares the respec journal, wallet witness and Profile v2 receipt. It
finishes or rolls back a partial operation deterministically. Do not edit only one of these stores. Preserve all
three before manual intervention.

## Soulforge and companion incidents

Soulforge rank and shard balance commit in one profile revision and are protected by a durable operation ID.
Repeated committed IDs are no-ops. Companion roster state is durable; live entity UUIDs are runtime-only. If
spawn/rebuild fails, keep the roster and repair/retry the runtime side rather than deleting the profile.

## Shutdown

Disable closes mutation admission, drains accepted operations for bounded intervals, flushes the repository,
invalidates sessions and then stops executors/runtime adapters. A timeout is an operational failure: preserve logs
and stores, perform a controlled restart and let recovery protocols evaluate pending operations. Never wait
indefinitely or repeatedly reload the plugin in-process.

## Backup and restore

Back up the complete Profile v2 directory, quarantine evidence, respec journal and currency store together.
Restore them as one consistent set. Restoring legacy class/spec PDC is not a supported rollback.
