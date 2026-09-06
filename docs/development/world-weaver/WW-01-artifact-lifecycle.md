# WW-01 — DEV artifact lifecycle implementation status

Internal developer evidence. The lifecycle integration is implemented; exact-head server/client acceptance is still open. WorldWeaver remains code-disabled. This is not a production-readiness verdict.

## Exact dependency

- Repository: `MilCsik09/IceSMP`.
- Branch: `feature/world-weaver-ww01-artifact`; draft PR #154.
- Base branch: `feature/world-weaver-ww00-coverage` (PR #153).
- Base commit: `f1d7ce81939d7a0e6c7bb26780d639c5d819bcf0`.
- Original cumulative gameplay base: `feature/trash-production-hardening`, `a335b3b5acaea66772534e51527a1c9233a85d1a`.
- Earlier data-helper checkpoint: `a2f90b3858b2e611b618bb8334026759a71e1987`.
- Reproducible Java 21 toolchain/evidence checkpoint: `67948b9126b5b3823f0a96d802eaffde81a715af`.

## Implementation evidence

| Requirement | Implementation | Evidence and limit |
|---|---|---|
| One shared identity/lifecycle authority | Existing `DevItemManager` owns registration, identity ledger, issue/recover, tick dispatch and shutdown | Complete Java source compilation; in-game lifecycle evidence still required |
| Separate behavior adapters | `BingulusRewardBehavior`, `WorldWeaverArtifactBehavior`, generic interaction/context/registration contracts | Existing reward state suite plus extracted-behavior migration tests |
| Fixed primary developer | WorldWeaver definition uses `FixedArtifactOwner(HiddenDevAuthority.PRIMARY_DEVELOPER)`; startup rejects a mismatching persisted fixed owner | Fixed/configured policy regression; no permissions or OP grant |
| Physical authority | Issued identity, owner, instance, current login generation, exact item presentation/components, one visible copy, owner scheduler and active inventory/main-hand checks | Forged marker tests; physical clone/creative-client cases require client evidence |
| Recovery | Shared manager removes foreign/stale items, rotates missing/duplicate instances durably before insertion, retains state on full inventory; drop/container/ender/death routes guarded | Implementation and compile evidence; full-inventory/death/relog client matrix not yet observed |
| Schema 2 | `DevArtifactStateCodec` + existing `YamlStore.saveAtomic`, serialized bounded I/O executor | Pure migration/round-trip tests; new real Bukkit/YAML runtime probe awaits exact-head CI |
| Legacy preservation | Existing Bingulus ID/PDC keys, owner/instance, progress, pity and exact pending bytes survive; reward factories and rarity selection remain canonical | Existing reward suite, migration suite, real-item serialization probe |
| Owner-safe continuations | `DevArtifactContext` carries immutable IDs; `ArtifactSessionFence` invalidates logout/death/disable generations; each resumed action resolves and validates on the owner | Session regression, no new blocking future wait or legacy scheduler; server/client scheduling evidence open |
| Durable publication and close | Candidate publishes after atomic write; admission and executor submission share a lock; shutdown closes admission and drains accepted writes before final snapshot | Injected writer/queue failure, concurrent close-vs-submit test, reentrant continuation and two-artifact serialization tests |
| Generic interaction dispatch | Right-click air/block/entity, sneak flag and F route as immutable interaction data through the existing protection listener; entity-event deduplication | Compile evidence; Subject/Thread GUI intentionally belongs to WW-02/WW-07 |
| Four modern states | Idle/subject/thread/canon modern item definitions with shared asymmetric echo/amethyst geometry; no numeric CustomModelData | Resource-pack validator and asset-link regression; human visual acceptance required |
| Failure isolation | Adapter exceptions quarantine that adapter; state-store failure suspends shared durable mutations; generic bounded public-log messages contain no hidden content | Pure storage failure tests; broader provider circuit breaker belongs to WW-08 |

## Finding-driven corrections

1. **Accepted-write/shutdown race:** the data-helper checkpoint reserved a mutation before submitting it to the executor, permitting a concurrent close to overtake it. Submission and admission now share the ledger lock. A two-thread regression exercises this ordering.
2. **Old Bingulus crash replay:** the old manager inserted the item before clearing the pending reward, with an explicit comment that no transaction layer existed. A crash/write failure could replay that exact pending item. The extracted behavior durably records a delivery ambiguity fence before touching inventory. Normal success completes progress/pity through the shared store. A callback known not to have begun compensates only the unchanged claim. An actually ambiguous interrupted delivery preserves its exact pending payload as `NEEDS_REVIEW` and cannot run again automatically. This narrowly changes failure behavior to preserve the no-duplicate invariant; it does not change reward selection, mint a receipt, delete history or grant a replacement reward. Review resolution remains part of the later developer surface.
3. **Physical offhand escape:** the existing generic inventory guards did not distinguish an artifact that must remain out of offhand. Swap, inventory click/drag and recovery now enforce the descriptor's main-hand policy.
4. **Late continuation authority:** queued work no longer carries a live Player through a storage stage. Retired login generations, changed owner/instance, pending identity writes and missing physical items reject resumed behavior.

## Verification ledger

- Complete main/regression source sets compiled locally using Java 21 and the exact 49 compile dependencies exported by CI: 0 errors, 3 existing deprecation warnings.
- `DevArtifactLifecycleRegressionSuite`, `DevArtifactMigrationRegressionSuite`, existing `DevItemRewardRegressionSuite`: passed locally. Their scope includes state/authority/failure contracts, not a connected Minecraft client.
- `python3 scripts/check_consistency.py`: 0 FAIL / 0 WARN.
- `python3 scripts/audit_world_weaver_coverage.py`: 0 inventory errors; 55 domains and **51 DEFERRED_BLOCKER** remain. Lifecycle code does not close provider coverage.
- `python3 scripts/resource_pack.py validate`: new modern item/model graph validated.
- `scripts/tests/test_dev_artifact_assets.py`: four code-defined states resolve to distinct modern model textures.
- The local Gradle wrapper cannot download its distribution (`Network is unreachable`); no mocked API or filtered source compilation is used. The exact-head GitHub full build remains the required build evidence.
- CI run [34055083190](https://github.com/MilCsik09/IceSMP/actions/runs/34055083190), at the earlier workflow checkpoint, compiled both source sets; its full build retained the pre-existing `trashSpriteAssetAudit` failure. No gate is removed or skipped.
- The WorldWeaver workflow now includes dedicated lock-pinned Paper 1.21.11 and Folia 1.21.11 jobs. `DevArtifactRuntimeProbe` tests the assembled manager, real detached Bukkit item components/byte round-trip, exact legacy payload conversion, schema-2 YAML and final durable shutdown. It has no reflection or player mutation and never enables WorldWeaver.

## Remaining acceptance gates

Connected-client evidence remains required for forged/duplicate artifacts, full inventory, cursor/offhand/container, death/relog, stale inputs and visual appearance. Broader WorldWeaver acceptance (kernel, provider coverage, reward influence, persistence/journal/undo, Thread/AREA/Binding/Fork and production epoch) remains open in its assigned later phases. The corrupted baseline Trash sprite sources are still a separate failing resource gate.

## Exact runtime evidence — lifecycle head `800b0ae12e18e0e6f798f22dfe2f6b4c966a8f54`

- [WorldWeaver run 34057010953](https://github.com/MilCsik09/IceSMP/actions/runs/34057010953): full Java/main/regression compilation and all executed Java regressions succeeded. The only failed Gradle task was `trashSpriteAssetAudit`, retaining the pre-existing corrupt source PNG gate.
- [Paper job 101550637015](https://github.com/MilCsik09/IceSMP/actions/runs/34057010953/job/101550637015): **PASS for the bounded artifact runtime probe and durable shutdown only**.
- [Folia job 101550637010](https://github.com/MilCsik09/IceSMP/actions/runs/34057010953/job/101550637010): **PASS for the same probe and durable shutdown only**, Folia `1.21.11-14-ver/1.21.11@529aabc`.
- Both server jobs observed `ICESMP_DEV_ARTIFACT_RUNTIME_PROBE_PASS` and `ICESMP_DEV_ARTIFACT_RUNTIME_SHUTDOWN_PASS` and completed successfully. These markers do not prove cross-region connected-player interactions or forged-copy client handling.
- Resource-pack CI run [34057010948](https://github.com/MilCsik09/IceSMP/actions/runs/34057010948) and inherited Trash runtime/regression run [34057011012](https://github.com/MilCsik09/IceSMP/actions/runs/34057011012) succeeded.
- The broader local Python suite found one additional authority-inventory omission: the manager's ephemeral entity-interaction deduplication map lacked an exact runtime classification. Its owner-only insertion and centralized quit/kick/death/disable cleanup are implemented. The exact field is now classified with that evidence; unknown UUID maps remain review-blocking. No gameplay authority was moved into an exception.
