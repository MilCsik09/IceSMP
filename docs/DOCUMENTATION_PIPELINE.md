# IceSMP repository → Player Docs pipeline

## Purpose

This tooling turns the production repository into a deterministic, evidence-backed inventory. It is intentionally separate from gameplay code: scanners read production sources and resources, while human decisions live in `docs/documentation-manifest.yml`. Uncertain data is never silently dropped; it is emitted as `REVIEW_REQUIRED` with a source path, line and symbol whenever those can be resolved.

The initial implementation phase uses **report mode**. Missing Player Docs coverage remains visible but does not block unrelated feature work. The later release-documentation phase uses **strict mode**, where every command, alias, player/admin feature, permission, component classification and marker must be complete.

## Architecture

The command-line entry points are:

```text
scripts/generate_repository_inventory.py
scripts/check_documentation_coverage.py
scripts/compare_repository_inventory.py
scripts/generate_release_inventory.py
```

The implementation package is `scripts/repository_inventory/`:

| Module | Responsibility |
| --- | --- |
| `models.py` | Evidence, findings, confidence/severity vocabulary. |
| `util.py` | UTF-8 I/O, Java comment/string-safe token scanning, balanced delimiters, conservative YAML paths, stable JSON/Markdown. |
| `java_scanner.py` | Production Java index, package/class/constant resolution. |
| `command_scanner.py` | `registerCommand` calls, root aliases, `BasicCommand`, `AbstractDispatchCommand`, switch/if/helper dispatch, usage/help/completion and permissions. |
| `permission_scanner.py` | Central and inline permission usage across commands, GUIs and listeners. |
| `config_scanner.py` | Default YAML paths, typed code reads, fallbacks and startup/reload evidence. |
| `message_scanner.py` | `MessageManager` keys, default resources, fallback drift and hard-coded player messages. |
| `feature_scanner.py` | Component candidates and conservative feature grouping. |
| `documentation_scanner.py` | Manifest/path/marker coverage and stale-entry checks. |
| `delta.py` | Stable-ID inventory comparison. |
| `inventory.py` | Repository orchestration and deterministic fingerprint. |
| `report.py` | JSON, Markdown and mobile-summary rendering. |

No third-party Python package is required. Python's standard library is sufficient, so base inventory generation works offline after checkout.

## Java analysis strategy

This is not a `*Command.java` filename inventory and not a regex-only declaration list. The scanner:

1. locates actual `registerCommand(...)` calls using comment-aware and string-aware balanced-parenthesis scanning;
2. resolves literal and same-repository string constants;
3. resolves inline constructors and variables assigned from command constructors;
4. parses aliases from static collection factories;
5. indexes `BasicCommand` implementations and flags unregistered production implementations;
6. scans `switch (args[n])`, string-based `if/else` routes, helper methods and `AbstractDispatchCommand.register(new ...Subcommand())` calls;
7. links usage/help string evidence, arguments, permission checks, player-only guards and static completion options;
8. emits dynamic/config/factory/reflection paths as `REVIEW_REQUIRED` instead of omitting them.

The parser is deliberately conservative. It does not claim to be a full Java compiler. Balanced delimiters and lexical state avoid common multiline/comment/string failures; the manifest supplies human overrides when semantic meaning cannot be proven statically.

## Stable IDs

Stable IDs are independent of line numbers:

```text
command.crate
command.crate.buy
alias.crate.ladak
permission.icesmp.admin.crate
config.crates.key-price
message.crate-key-bought
feature.crate
component.hu.taliann.icesmp.managers.CrateManager
```

A duplicate stable ID is always blocking. Line numbers are evidence only and may change without changing identity.

## Documentation manifest

`docs/documentation-manifest.yml` is JSON-compatible YAML (JSON is a valid YAML 1.2 subset). This keeps parsing deterministic and dependency-free while preserving a normal `.yml` workflow.

Required top-level keys:

```yaml
version: 1
commands: {}
features: {}
permissions: {}
config-sections: {}
components: {}
explicit-ignores: {}
```

A typical entry is:

```json
"command.crate.buy": {
  "audience": ["PLAYER"],
  "docs": ["docs/player-guide/18-teljes-parancslista.md"]
}
```

The target document must eventually contain:

```html
<!-- icesmp-doc-id: command.crate.buy -->
```

A filename match or a textual occurrence of the command name does **not** count as documentation.

### Component classification

A component entry can bind an otherwise ambiguous class to a feature:

```json
"component.hu.taliann.icesmp.managers.CrateManager": {
  "feature": "feature.native-crates"
}
```

### Explicit ignores

Ignores are exact stable IDs, never package wildcards. Every ignore must give an auditable reason:

```json
"component.hu.taliann.icesmp.internal.GeneratedFixture": {
  "reason": "Synthetic tooling fixture; not packaged in the production source set."
}
```

Do not use ignores to hide production functionality or parser uncertainty.

## Confidence

| Level | Meaning |
| --- | --- |
| `HIGH` | Direct static evidence resolves identity and relationship. |
| `MEDIUM` | Conservative inference is strong but not compiler-proven. |
| `LOW` | Weak evidence; suitable only as a review lead. |
| `REVIEW_REQUIRED` | The item is retained but a human/manifest decision is mandatory. |

## Report and strict modes

Report mode:

```bash
python3 scripts/check_documentation_coverage.py --mode report
```

Parser/integrity failures remain blocking. Missing markers, new commands/features/config sections and unresolved classifications remain visible as warnings or review items.

Strict mode:

```bash
python3 scripts/check_documentation_coverage.py --mode strict
```

Strict mode fails for undocumented roots/subcommands/aliases, undocumented player/admin features and permissions, missing/stale markers, bad paths, unclassified production components, or any unresolved `REVIEW_REQUIRED` result.

## Local execution

```bash
python3 -m unittest discover -s scripts/tests -p 'test_*.py' -v
python3 scripts/generate_repository_inventory.py --root . --output build/repository-inventory
python3 scripts/check_documentation_coverage.py --mode report
python3 scripts/generate_release_inventory.py \
  --base-ref 49cb32740629f3d91a08b753436f3e16d33a494d \
  --head-ref HEAD \
  --output build/release-inventory
```

Generated reports remain under `build/` and are not committed.

Exit codes:

| Code | Meaning |
| --- | --- |
| `0` | The selected policy passed. |
| `1` | Inventory/coverage completed but blocking findings exist for the selected mode. |
| `2` | Parser crash, invalid manifest, unresolvable Git ref or worktree/report generation failure. |

## GitHub Actions

Workflow: **Repository Docs Inventory** (`.github/workflows/repository-docs-inventory.yml`).

It runs on every pull request, pushes to `master`, and manual dispatch. It uses only:

```yaml
permissions:
  contents: read
  pull-requests: read
```

The Actions Summary is the phone-friendly entry point. Complete JSON and Markdown files are uploaded as `repository-docs-inventory-<run-id>-<attempt>`.

Manual inputs:

| Input | Default | Purpose |
| --- | --- | --- |
| `base_ref` | `49cb32740629f3d91a08b753436f3e16d33a494d` | Released baseline. |
| `head_ref` | `master` | Candidate release head. |
| `strict_docs` | `false` | Enforce final zero-gap policy. |
| `generate_release_delta` | `true` | Build both inventories and their delta. |

## Adding a command

1. Implement and register the command in production code.
2. Run repository inventory locally.
3. Resolve every `REVIEW_REQUIRED` route or add an exact manifest classification.
4. Add root, subcommand and alias manifest entries.
5. Add matching `icesmp-doc-id` markers to Player Docs.
6. Verify permission, usage, arguments, audience and completion evidence.
7. Run report mode during implementation; strict mode before release documentation is declared complete.

## Adding a feature

1. Add the production components normally; do not refactor solely for the scanner.
2. Inspect generated candidates and component IDs.
3. Create or update the feature manifest entry with audience and docs paths.
4. Bind ambiguous components explicitly.
5. Classify persistence, config, permissions, commands, GUI and message keys.
6. Add documentation markers in the later documentation branch.

## False positives

Prefer, in order:

1. improve the scanner with a regression fixture;
2. add an exact component/feature/command manifest override;
3. add an exact explicit ignore with a concrete non-production reason;
4. retain `REVIEW_REQUIRED` until evidence exists.

Never weaken an invariant globally to suppress one finding.
