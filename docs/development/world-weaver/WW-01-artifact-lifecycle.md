# WW-01 — DEV artifact lifecycle implementation status

Internal developer evidence. This phase is IN PROGRESS and is not production-enabled.

## Exact dependency

- Repository: `MilCsik09/IceSMP`.
- Branch: `feature/world-weaver-ww01-artifact`.
- Base branch: `feature/world-weaver-ww00-coverage` (PR #153).
- Base commit: `f1d7ce81939d7a0e6c7bb26780d639c5d819bcf0`.
- Original cumulative gameplay base: `feature/trash-production-hardening`, `a335b3b5acaea66772534e51527a1c9233a85d1a`.

## First reviewable checkpoint

This checkpoint introduces the data and persistence contracts that the existing `DevItemManager` will use. They are not registered as a second gameplay manager. The existing manager and Bingulus behavior are still unchanged and are not yet connected to these helpers.

| Requirement | Implementation evidence | Status |
|---|---|---|
| Fixed/configured owner policies | `FixedArtifactOwner`, `ConfiguredArtifactOwner`; fixed policy never calls configuration | Implemented data contract; primary-developer registration pending |
| Definition/presentation/policy sources | `DevArtifactDefinition`, presentation and policy records/sources | Implemented data contracts |
| Four model states and precedence | `DevArtifactPresentation.ModelState` and exhaustive eight-input precedence regression | State model implemented; resource-pack assets pending |
| No live object in persisted state | `ArtifactStateValue` recursively copies bounded YAML-safe values, rejects arbitrary objects/non-finite numbers | Implemented data boundary |
| Durable identity publication | `DevArtifactLedger.commit` writes candidate snapshot through its serial executor before publishing identity | Tested helper; manager writer/scheduler integration pending |
| Conditional mutation | Expected revision, one pending operation per artifact, monotonic revision | Tested helper |
| Failure isolation at state boundary | Writer/queue failure closes ledger; failed candidate never publishes; accepted writes drain before final save | Tested helper; plugin shutdown integration pending |
| Legacy migration | `DevArtifactStateCodec`: strict legacy Bingulus identity, pending exact-item encoder, progress and pity; schema 2 retains other artifacts | Tested pure codec; Bukkit exact-item adapter/persistence migration pending |
| Shared singleton lifecycle | Existing `DevItemManager` | Refactor pending |
| Bingulus extraction | Existing reward behavior must retain its semantics | Pending |
| WorldWeaver physical shell | Primary owner, authoritative instance, recovery, interaction routing | Pending |

## Verification scope

- `DevArtifactLifecycleRegressionSuite`: fixed-owner policy independence, unissued/foreign/forged identity markers, immutable snapshots, YAML-safe bounds, durable publication ordering, injected fsync failure, stale revision, concurrent operation refusal, reentrant continuation, two-artifact serialized writes, shutdown drain, executor rejection, model-state precedence.
- `DevArtifactMigrationRegressionSuite`: exact pending payload retained without reroll/rebuild, owner/instance/progress/pity preservation, valid unissued migration, schema-2 restart round-trip and unknown artifact retention, invalid/missing/partial legacy state, unsupported schema and arbitrary live-object rejection.
- Both suites executed with Java 21 and passed; both are registered as Gradle `check` dependencies. A successful data-helper test is not an in-game forged-copy, Folia scheduling, restart or full-inventory recovery proof.
- The base's complete Java source set compiled locally with the exact 49 resolved compile dependencies exported by CI. The full main and regression source sets, including this checkpoint, compiled with Java 21 against those dependencies with 0 errors (3 existing deprecation warnings). Both new suites and the existing Bingulus reward suite passed. Exact-head CI must still be recorded before phase completion.
- Base CI run [34046069105](https://github.com/MilCsik09/IceSMP/actions/runs/34046069105) retains a failed `trashSpriteAssetAudit` for two pre-existing corrupted source PNGs. No gate is removed or skipped.

## Remaining acceptance gates

Manager/factory/listener integration; owner-safe async continuations; actual schema-2 file migration; behavior extraction; all physical singleton recovery/forgery/inventory routes; four modern model assets; boot/shutdown probes on Paper and Folia; in-game client evidence. WW-01 is not complete until those gates have implementation and evidence.
