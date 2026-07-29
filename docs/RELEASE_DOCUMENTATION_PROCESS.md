# Release documentation process

This process starts only after the external-plugin replacement implementation round has finished and all implementation pull requests are merged.

## Fixed baseline

The last released baseline is:

```text
49cb32740629f3d91a08b753436f3e16d33a494d
```

The release head must be the final full SHA of `master`; do not use an assumed or moving SHA in the final changelog appendix.

## Procedure

1. Merge every approved implementation pull request for native moderation, AFK zones, sitting/poses, MOTD, crates and any other release scope.
2. Record the final `master` SHA.
3. Open **Actions → Repository Docs Inventory → Run workflow**.
4. Set `base_ref` to `49cb32740629f3d91a08b753436f3e16d33a494d`.
5. Set `head_ref` to the recorded final `master` SHA (preferred over the moving branch name).
6. Set `generate_release_delta` to `true`.
7. Initially set `strict_docs` to `false` and download the `repository-docs-inventory-<run-id>-<attempt>` artifact.
8. Create the dedicated documentation branch from that exact final `master` SHA.
9. Update every Player Docs page identified by `documentation-coverage.md`, `review-required.md`, command/config/message/feature inventories and delta reports.
10. Update the central command reference, planned as `docs/player-guide/18-teljes-parancslista.md`, including audience labels: `JÁTÉKOS`, `MODERÁTOR`, `ADMIN`, `TESZTELŐ`, `FEJLESZTŐI`, `KONZOL`.
11. Update admin/operator documentation for permissions, config defaults, reload/restart behaviour, persistence files, soft dependencies and plugin replacements.
12. Update tester documentation for multiplayer, Folia-region, restart/reload, persistence, negative and exploit cases.
13. Write the human changelog from the evidence inventory; do not present raw scanner output as marketing copy.
14. Add the technical appendix with exact base/head SHAs, commits, PRs, command/permission/config/message/component deltas and verification results.
15. Add every required `<!-- icesmp-doc-id: ... -->` marker and remove stale manifest entries.
16. Re-run the workflow with the same immutable base/head SHAs and `strict_docs: true`.
17. Do not declare the documentation or changelog complete until all final metrics are satisfied:

```text
Commands documented:          100%
Subcommands documented:       100%
Aliases documented:           100%
Player features documented:   100%
Admin features documented:    100%
Permissions documented:       100%
Config sections classified:   100%
Unclassified components:        0
Review required findings:       0
```

## Changelog structure

### Players

Include every new/changed command and feature, GUI, item, recipe, quest, event, class/ability change, economy/balance change, cooldown, limit, resource-pack change and removed/renamed behaviour.

### Administrators

Include admin commands, permissions, config files/keys/defaults, external-plugin replacements, installation requirements, persistence files, restart/reload guidance, soft dependencies and integrations.

### Testers

Include new systems, multiplayer flows, Folia-specific cases, restart/reload and persistence tests, negative/exploit tests, known constraints and required server configurations.

### Technical appendix

Include exact base/head SHAs, commit and PR lists, all inventory deltas, persistence changes, and build/consistency/strict-coverage results.

## Release gate

A successful Gradle build alone is not a release-documentation pass. The gate is the combination of build success, consistency success, valid inventory artifacts and strict zero-gap documentation coverage on the immutable release head.
