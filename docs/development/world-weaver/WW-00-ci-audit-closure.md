# WW-00 CI audit closure — internal developer evidence

Findings from exact WW-00 head `198e812e0041e5674b6ecab2d2f3a8a491b4de9a`, run 34146298490, verification job 101818962763:

- Config audit validated the exact cumulative major-event list, then removed its leaf from the drift set. Upstream now also requires that leaf in the explicit allowlist, so the same valid change was subsequently reported missing. Retain the validated leaf, as already fixed in WW-02; keep the exact before/after check and all unexpected/missing leaf rejection.
- The sprite audit reached complete regenerated output (330/330 identities, 27/27 phases), then called `get_flattened_data`, unavailable on CI-pinned Pillow 11.3. Use channel histogram and bounded `getcolors(4096)` instead. Exact 64x64 RGBA, nonempty binary alpha, and 1..8 visible tones remain mandatory. Close image files on rejection as well as success.

Six fixture tests cover accepted binary alpha/eight tones, transparent colour exclusion, rejected missing/partial alpha, ninth tone, wrong dimensions/mode, and incomplete catalog counts. The unavailable flattened-data API is deliberately forbidden in the fixtures. The WorldWeaver CI executes these tests with its existing pinned Pillow; neither dependencies nor acceptance are weakened.

Local tests pass with ResourceWarning treated as error; the unchanged full source/output gate passes 330 identities and 27 phases; config audit passes 24,634 leaves. This patch changes tooling and internal evidence only. Refreshed exact-head CI must still confirm execution on actual Pillow 11.3. Native/client gameplay, universal coverage and production acceptance remain open.
