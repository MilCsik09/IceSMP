# Config GUI coverage

The admin GUI is an explicit **runtime-safe allowlist**, not a blind renderer for every scalar in the content configs.
The build-time `configGuiCoverageRegressionTest` merges every supported YAML file and proves:

- every GUI path exists exactly once;
- GUI type and packaged default type agree;
- numeric defaults are inside the declared range;
- enum defaults are among the declared options;
- all scalar entries under `world-events.safety.*`, `moderation.vanish.*` and
  `territory.mob-rules.doom-gate.*` are exposed;
- every remaining scalar is intentionally file/command-only (content records, lore, rewards, item definitions,
  spell/quest tables or advanced startup tuning), rather than accidentally omitted.

The test prints the exact `total / displayed / intentionally_excluded / missing / stale / duplicate` counts on every build.
A new scalar under a mandatory runtime-admin prefix fails the build until a matching GUI component is added.

## Transaction semantics

Opening the menu captures the effective values, packaged defaults, config generation and SHA-256 fingerprint of `config.yml`.
Clicks only modify an in-memory per-admin session. **Save** performs one asynchronous batch write; **Cancel**, closing the
inventory or disconnecting writes nothing. Middle-click removes the override and restores the packaged default.
A second admin save or external file edit makes an older session stale; stale sessions are rejected without overwriting data.

Entries display whether their effect is live, applied by a reload hook, or requires a restart. In particular the faction-tax
scheduler toggle/interval is restart-required; event safety and vanish capabilities are live/reload-safe.
