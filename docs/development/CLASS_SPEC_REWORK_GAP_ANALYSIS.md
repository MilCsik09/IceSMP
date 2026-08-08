# Class/spec rework gap analysis — Profile v2 foundation

## Closed in this PR

- greenfield Profile v2 as the sole class/spec authority;
- owner-bound immutable aggregate and deterministic codec;
- strict YAML envelope, CAS, atomic replacement, quarantine and explicit recovery;
- concurrent first-profile initialization and session-generation fencing;
- complete DARK gate-set seal/unseal persistence;
- explicit spell provenance;
- durable class XP/level, pet roster and Soulforge receipts;
- respec WAL/wallet/profile recovery protocol;
- bounded lifecycle shutdown and Folia scheduler boundaries;
- focused deterministic regression suites and operator documentation.

## Deliberately not part of this foundation

- mechanics-core primitives (meter, stack, mark, link, zone, stagger, echo, empower, attunement);
- doctrine runtime and mastery contribution;
- second-spec channelled switching;
- physical Soulbond item;
- CraftEngine/BetterHud/ModelEngine/MythicMobs content;
- complete ability kits for the 35 specializations.

## Release gates outside unit/regression execution

- real multi-region Folia staging;
- deployment plugin bundle and dependency-lock validation;
- controlled filesystem permission/ENOSPC tests;
- restart with pending economic operations;
- longer multi-player leak/soak testing.

These staging gates do not authorize a legacy fallback. Failure remains fail closed and must be corrected before
release.
