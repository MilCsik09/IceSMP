# IceSMP külsőplugin-kiváltási mátrix

> Ez a branch a crate-scope-ot tartalmazza. A többi replacement külön, még nem merge-ölt draft PR-ben
> él; azok kódja nincs átmásolva ide. `READY` kódszintű/buildelt állapotot jelent, nem automatikus
> production-eltávolíthatóságot.

| Terület | IceSMP számára szükséges | Már megvan / branch | Megvalósítandó | Tudatosan nem kell | Állapot | Teszt |
|---|---|---|---|---|---|---|
| Moderáció / InvSee | egységes punishment, SocialSpy, vanish, online live inv/ender | PR #45, `feature/native-moderation-suite` | valódi Folia átvételi teszt | offline playerdata, upstream kompatibilitás | DEFERRED | remote CI zöld a saját PR-en |
| AFK-zónák | közös 3D selection, több zóna, biztonságos reward | PR #46, `feature/native-afk-zones` | valódi Folia reward/reload teszt | IP-limit, AxAFKZone kompatibilitás | DEFERRED | remote CI zöld a saját PR-en |
| Sit/póz | click sit, lifecycle, lay/crawl | PR #47, `feature/native-sit-poses` | valódi Folia/anticheat teszt | stack, player/NPC sit, GSit paritás | DEFERRED | remote CI zöld a saját PR-en |
| MOTD | rotáció, eventprioritás, ikon, vanish count | stacked PR #48, `feature/native-motd-completion` | valódi ping/reload/jar-nélküli teszt | proxy, vhost, MiniMOTD configparitás | DEFERRED | remote CI zöld a saját PR-en |
| Crate core | fizikai hely, PDC-kulcs, vásárlás, súlyozott roll, spin/reveal | `CrateManager`, `CrateKeyFactory`, meglévő GUI-k | — | legacy key/config/location | READY | `crateRegressionTest`, teljes build |
| Crate access | `/crates`, preview, per-crate permission/world, required keys, cooldown | ez a branch | valódi permission/world playtest | CrazyCrates commandparitás | READY | domain + source regression |
| Crate reward | item, command, currency, unique, recipe, blueprint, crate-key | meglévő resolverek közvetlen újrahasználása | valódi reward/full-inventory teszt | új általános reward framework | READY | validator/selector/key regresszió |
| Crate state | location, stat, cooldown, reset, audit, rollback | közös `PersistentStore`/coordinator/atomic YAML | ENOSPC és process-kill playtest | migration és exactly-once állítás | READY | ledger/restart/rollback regresszió |
| Crate mass-open | teljesen finanszírozott bounded nyitások | ez a branch | nagy inventory/runtime teszt | korlátlan open | READY | `maxOpenable` + exact key plan |
| TAB | a jelenlegi IceSMP alapok | baseline natív tablist/HUD | csak közvetlen feature-integráció | teljes TAB engine | READY | baseline build/playtest szerint |
| ICEsmpadditions | Warden XP | `WorldTweaksListener` | kézi Warden teszt | további upstream funkciók | READY | MANUAL |
| FarmProtect | crop trample védelem | `WorldTweaksListener` | játékos/mob kézi teszt | további upstream funkciók | READY | MANUAL |
| Cosmic/Quad/Casino/FireCracker | nem IceSMP-követelmény | — | — | teljes egészében | OUT_OF_SCOPE | — |
