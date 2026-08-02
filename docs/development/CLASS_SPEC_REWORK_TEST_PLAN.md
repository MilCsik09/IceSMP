# Profile v2 verification plan

## Automated gates

The Gradle `check` graph executes the real domain/application/persistence code and includes:

- `classProfileV2RegressionTest` — domain invariants, owner-bound deterministic ICS2 codec, corruption limits;
- `classSpecApplicationRegressionTest` — per-player mutation serialization, session fencing, complete DARK gates,
  fail-closed runtime errors, atomic Soulforge, class XP receipts and companion isolation;
- `classProfileRepositoryRegressionTest` — strict YAML, owner mismatch, cross-instance CAS, first-write races,
  quarantine/recovery, atomic-write failure and bounded shutdown;
- `classProfileLifecycleRegressionTest` — greenfield initialization, concurrent first login, join/quit/reconnect and
  disable order;
- `respecTransactionRegressionTest` — WAL/wallet/profile crash points and restart recovery;
- `spellGrantLedgerRegressionTest` — explicit BASE/SPEC/TALENT/QUEST/ADMIN provenance.

Concurrency tests use controlled stores, barriers/latches and deterministic executors rather than sleep-based
success assumptions. Persistence failure tests inject faults between durable steps.

## Required repository checks

```bash
./gradlew clean build --console=plain --no-daemon --stacktrace
./gradlew check --console=plain --no-daemon --stacktrace
python3 scripts/check_consistency.py
python3 scripts/check_markdown_links.py --root .
python3 -m unittest discover -s scripts/tests -p "test_*.py"
git diff --check
```

The CI log must contain the pass marker for every Profile v2 suite. A successful workflow without those markers is
not accepted as evidence.

## Mandatory behavioral cases

### Domain and codec

- classless-state cleanup and exactly two slots;
- duplicate/wrong-parent specialization rejection;
- sealed/review/quarantine activation rejection;
- class XP, mastery, revision, cost and companion overflow boundaries;
- insertion-order-independent serialization and exact roundtrip;
- malformed UTF-8, lengths, trailing data, unknown enum/schema and owner mismatch.

### Persistence

- `-1 -> 0`, `n -> n+1`, stale/skipped/backward revision rejection;
- two repository instances racing on one player;
- unrelated players proceeding concurrently;
- cache changes only after durable replacement;
- strict envelope types/keys/ranges;
- corrupt evidence preservation and explicit idempotent recovery;
- stuck I/O and bounded shutdown.

### Lifecycle/runtime

- two concurrent first-profile initializers produce one revision-0 profile;
- logout and rapid reconnect before old callback completion;
- stale callback after the new session is READY;
- scheduler rejection and plugin disable callback;
- durable success plus runtime failure reported distinctly;
- pet/spell/form cleanup failure leaves reconciliation-required state.

### DARK, spell and companion

- all five DARK specializations and every gate combination;
- repeated seal/unseal events and restart roundtrip;
- BASE/SPEC mixed provenance with admin/talent/quest preservation;
- complete companion mutation chain, namespace/slot/owner isolation and stale callbacks;
- Soulforge duplicate operation ID after repository reload.

### Economic recovery

- respec crash after intent, debit, Profile CAS and commit;
- CAS/write/refund failure and duplicate callback;
- restart recovery with the same operation ID;
- no double debit, free respec or double Soulforge rank.

## Folia staging

Automated adapter tests do not replace staging for:

- player and pet in different regions;
- reconnect during slow durable I/O;
- world change during runtime rebuild;
- disable during Soulforge/respec;
- entity scheduler rejection/retired entity;
- real permission, ENOSPC and filesystem failure;
- controlled restart with a pending economic operation;
- multi-player soak testing for cache, future, lock and entity leaks.
