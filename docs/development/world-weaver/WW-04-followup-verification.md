# WW-04 follow-up verification

Internal developer evidence; neither phase completion nor production admission.

Base branch: `feature/world-weaver-ww04-pve-faction-integrity`, PR #158.
Base commit: `1aead208f3b656002dc6e6ce980684505e37727c`.

Exact-base run 34129979150 passed native Paper 101767502211 and Folia
101767502546, and artifact lifecycle 101767502389 / 101767502369.
Resource pack 34129979147 and Trash hardening 34129979170 passed.
Verification 101767502077 failed three tasks: inherited `trashSpriteAssetAudit`,
`prologueRegressionTest`, and `lifecycleShutdownRegressionTest`. The latter two
share a stale source assertion for the pre-extraction child scheduler variable.

The assertion now follows the routing handle, UUID resolution and owner guard.
It also rejects live-Mob map storage and Bukkit/PDC access in retired cleanup;
the ownership invariant is retained. Full Java 21 compilation and both affected
regression suites pass locally. Fresh exact-head CI remains required.

Coverage remains 323 authorities / 55 domains / 51 blockers. Native damage
propagation, non-kill rewards, subsequent provider phases and client evidence are
still open. Physical artifact issuance and durable gameplay admission stay off.
