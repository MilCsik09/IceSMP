# Profile v2 greenfield rollout and legacy class/spec removal

> Historical filename retained for stable documentation links. This is not a player-data migration plan.

## Decision

IceSMP has not operated with production class/spec player data. Profile v2 therefore starts from a clean,
greenfield store and is the sole runtime authority.

Unsupported behaviors:

- importing old class/spec PDC;
- dual-read or dual-write operation;
- migration marker/receipt processing;
- legacy authority fallback after a Profile v2 error;
- a config flag that disables Profile v2 or re-enables the old runtime;
- automatic downgrade or rollback to legacy class/spec state.

## Rollout procedure

1. Back up the server directory as an operational precaution.
2. Deploy the build and the unchanged dependency lock for the target 1.21.11 environment.
3. Ensure `class-spec-rework.dependencies.enforce: true`.
4. Start the server and verify Profile v2 repository initialization.
5. Join with a new player and confirm a clean revision-0, classless profile.
6. Choose a class/spec and verify revision increments, runtime reconciliation and reconnect persistence.
7. Exercise `/spec info` and the explicit quarantine recovery permission on staging.

No step reads old class/spec PDC. Other unrelated PDC and shared plugin data remain untouched.

## Existing development data

Old development class/spec data may remain on a player object, but it is inert. It is not imported, mirrored back
as authority or used to recover a missing/corrupt profile. Operators may remove known retired keys only as an
explicit cleanup after verifying that no unrelated system shares them.

## Missing profile

A missing file is initialized as an empty Profile v2 revision 0. Concurrent initializers use CAS; the losing
initializer reloads the winner.

## Corrupt or unknown profile

A malformed envelope, owner mismatch, digest failure, unsupported codec/schema or invalid domain state is
preserved as quarantine evidence. The session remains fail closed. Do not delete or replace the file manually.
Use the runbook and explicit recovery command after recording the evidence ID.

## Rollback policy

Application rollback means restoring a complete backup of the Profile v2 store and compatible plugin build.
There is no supported runtime rollback to legacy class/spec PDC and no kill switch.
