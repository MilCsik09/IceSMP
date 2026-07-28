# IceSMP külsőplugin-kiváltási mátrix

**Mérvadó base:** `claude/projekt-audit-u0hkcz` @ `f4e82263d35c645a053eccac09a3ad254a36ed90`
**Ellenőrzés:** 2026-07-28
**Elv:** az `Other/plugins/` csak funkcionális követelményforrás. Nincs production adat, ezért
nincs legacy dekóder, migráció vagy upstream configkompatibilitás.

| Terület | IceSMP számára szükséges | Már megvan ezen a branchen | Megvalósítandó / igazolandó | Tudatosan nem kell | Állapot | Teszt |
|---|---|---|---|---|---|---|
| AxAFKZone / AxAPI | több zóna, közös 3D selection, weighted currency/item/command reward, adminműveletek | teljes célzott implementáció és configvalidáció | valódi Folia, reload, permission, full-inventory és restart nélküli config playtest | upstream command/config/PAPI paritás, IP-limit, migráció | **PARTIAL** | `afkRegressionTest`, teljes build; runtime még kell |
| SModeration / InvSee++ | egységes punishment, online live inv/ender, SocialSpy, vanish | ezen az önálló branchen nincs benne; külön draft PR #45 | PR #45 runtime playtest és merge-döntés | offline playerdata, legacy DB/config | **DEFERRED** | külön moderációs scope |
| GSit | sit/click-to-sit, lifecycle, policy, egyszerű pose | meglévő alap | külön sit/pose scope | teljes GSit API, stack, NMS | **PARTIAL** | külön scope |
| MiniMOTD | random/idő/event rotáció, ikonok, count/reload | meglévő alap | külön MOTD scope; vanish integráció stacked lehet | proxy/vhost/upstream config | **PARTIAL** | külön scope |
| CrazyCrates | lista/preview, permission/world/cooldown/stats, strict rewards | meglévő alap | külön crate scope | legacy key/config és teljes animációparitás | **PARTIAL** | külön scope |
| TAB | jelenlegi IceSMP header/footer, név, nametag, sorting, ping, AFK/raid/HUD | natív rendszer | csak közvetlen integráció és runtime ellenőrzés | condition/layout/proxy/PAPI engine | **READY** a jelenlegi igényre | meglévő build + manuális viewer teszt |
| ICEsmpadditions | Warden XP | `WorldTweaksListener` | kézi eseményteszt | upstream extrák | **READY** | build + manual |
| FarmProtect | crop-trample védelem | `WorldTweaksListener` | player/mob kézi teszt | upstream extrák | **READY** | build + manual |

Az `AxAFKZone` és `AxAPI` csak a dokumentált valódi Folia playtest után jelölhető eltávolíthatónak.
A mátrix nem állít upstream feature-paritást.
