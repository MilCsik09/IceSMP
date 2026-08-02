# Profile v2 — 1.21.11 to 26.2 port boundary

Profile v2 deliberately separates stable domain/persistence code from Paper/Folia adapters. The port does not
require legacy data migration or a second class/spec runtime.

## Stable across the port

- owner-bound `ClassProfile` schema semantics and stable class/spec IDs;
- revision/CAS contract;
- deterministic ICS2 codec and strict YAML envelope;
- quarantine evidence and explicit recovery model;
- session-generation protocol;
- operation receipts, Soulforge idempotency and respec recovery decisions;
- DARK seal reasons and companion namespaces.

## Revalidate or replace

- Java/Paper/Folia toolchain versions and dependency lock;
- player, entity, region, global and async scheduler adapters;
- Paper command/dialog APIs;
- persistent-data and item-model API call sites;
- external CraftEngine/BetterHud/ModelEngine/MythicMobs adapters;
- filesystem atomic-move and lock behavior in the deployment environment.

## Port procedure

1. Port the compatibility base and make its dependency preflight pass.
2. Compile Profile v2 without changing stable IDs or schema semantics.
3. Run every Profile v2 regression task and repository tooling gate.
4. Load a copied Profile v2 store and verify owner/revision/digest roundtrips.
5. Run the Folia staging matrix from the test plan.
6. Only then deploy the new server version.

There is no fallback to old PDC state if the 26.2 adapter cannot initialize. Startup must fail clearly and remain
closed until the adapter or dependency problem is corrected.
