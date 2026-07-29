# Natív AFK-zónák

## Hatókör és garanciahatár

Az IceSMP natív AFK-rendszere a szerverhez ténylegesen szükséges funkciókat biztosítja:
globális tétlenségfigyelés, önkéntes `/afk`, tablistajelzés, AFK-jutalomkapuk, több
konfigurálható jutalmazó zóna és adminisztráció. Nem cél az AxAFKZone/AxAPI parancs-,
config- vagy placeholder-kompatibilitása, az IP-limit, illetve bármilyen legacy migráció.

A kód Java 21-en fordul, az `afkRegressionTest` és a teljes Gradle `check` része. A valódi
Folia region-thread viselkedést, világváltást és inventory-overflowt a `PLAYTEST.md` szerint
külön runtime tesztelni kell; addig az AxAFKZone/AxAPI eltávolítása nem tekinthető bizonyítottnak.

## Reuse-audit

| Szükséges képesség | Meglévő IceSMP-komponens | Újrahasználás | Bővítés | Új komponens? |
|---|---|---|---|---|
| kétpontos 3D kijelölés | `ClaimManager` selection és claim wand | a selection felelősség kiemelve közös szolgáltatásba; a claim változatlan parancs/wand felülete ezt használja | világ-UUID, normalizált 3D cuboid, overflow-safe méretek, preview lifecycle | `CuboidSelectionService`, csak a közös minimum |
| globális AFK | `AfkManager`, `/afk`, tablista és reward gate | közvetlen bővítés | több zóna, zone state, catalog-revízió és reward clock | nincs párhuzamos AFK manager |
| config | `ConfigManager` | a meglévő merge és reload | több összetartozó override atomikus írása `YamlStore.saveAtomic`-kal | nincs új config loader |
| üzenet | `MessageManager` | közös legacy/MiniMessage renderer | domainből érkező zónaszöveg renderelése | nincs új message framework |
| valuta | `CurrencyManager` | `payOutTokens` | fizikai reward hard stack-budgetje | nincs új currency API |
| item | Bukkit inventory API | meglévő player-scheduler minta | overflow a játékos helyén ledobva | nincs új item resolver a vanilla rewardhoz |
| command reward | global region scheduler | támogatott konzol-dispatch | pontosan whitelistelt `{player}`, `{uuid}`, `{zone}`, generation gate és valós siker-visszajelzés | nincs általános placeholder engine |
| cleanup | `PlayerStateCleanup` és `PlayerSessionCleanupListener` | központi registry | selection preview és AFK zone state | `IdentityTaskRegistry`, kizárólag a preview task-lease minimuma |

## Közös 3D selection

A `/claim pos1`, `/claim pos2` és a meglévő birtokmérő pálca ugyanazt a
`CuboidSelectionService` sessiont állítja, amelyből a `/claim area` és az `/afkzone` is
olvas. Az AFK-rendszer nem tart külön sarkokat, wandot vagy párhuzamos selection frameworköt.

A szolgáltatás felelőssége:

- playerenkénti első és második sarok;
- világ UUID + név és cross-world reset;
- inclusive min/max normalizálás;
- overflow-safe XZ footprint és 3D volume;
- domainenként helyes limit: a claim megőrzi a történeti XZ footprint-plafont, az AFK-zóna
  az explicit 3D `selection.max-volume` és `afk.max-zone-volume` minimumát használja;
- player entity scheduleren futó, pontszámban és időben korlátozott particle preview;
- identitás- és generációalapú preview lease, ezért egy régi retirement callback nem törölheti
  az új previewt, reload pedig nem hagyhat előtte indult taskot életben;
- clear, quit, kick, reload és disable cleanup.

## Adminparancs

Permission: `icesmp.admin.afk` (az `icesmp.admin.all` gyereke).

| Parancs | Művelet |
|---|---|
| `/afkzone create <id> [név]` | zóna létrehozása az aktuális közös selectionből |
| `/afkzone replace <id>` | meglévő zóna cuboidjának cseréje |
| `/afkzone delete <id>` | zóna tartós tombstone-nal történő törlése |
| `/afkzone list` | érvényes és hibás definíciók listája |
| `/afkzone status [id]` | összegzés vagy részletes configdiagnosztika |
| `/afkzone tp <id>` | aszinkron teleport a zóna közepéhez |
| `/afkzone show [id\|selection]` | tárolt vagy aktuális cuboid preview |
| `/afkzone clear` | közös selection törlése |

A törlés `deleted: true` override-ot ír. Ez azért szükséges, mert a `config/afk.yml` csomagolt
alapdefiníciói és a `config.yml` felülírásai merge-ölődnek: egy egyszerű parent-section törlés
után az alapdefiníció egyébként újra megjelenne.

## Zónakonfiguráció

```yaml
afk:
  # HUD/tablista kapcsolótól független driver; reloadkor újraütemeződik.
  refresh-ticks: 20       # 5..72000, hibás értéknél 20
  afk-after-seconds: 180  # 1..31536000, hibás értéknél 180
  max-zone-volume: 1000000
  max-currency-reward: 1000
  max-item-amount: 64
  zones:
    pihenokert:
      enabled: true
      deleted: false
      display-name: 'Pihenőkert'
      world: world
      world-uuid: 'opcionális, adminparancs kitölti'
      permission: ''
      min: {x: 0, y: 60, z: 0}
      max: {x: 10, y: 70, z: 10}
      reward-interval-seconds: 600
      roll-count: 1
      rewards:
        - {type: CURRENCY, weight: 80.0, currency: NEUTRAL, amount: 2}
        - {type: ITEM, weight: 20.0, material: BREAD, amount: 1}
        - type: COMMAND
          weight: 1.0
          command: 'give {player} bread 1'
          description: 'kenyér'
      messages:
        enter: '&bBeléptél: &f{zone}'
        leave: '&7Elhagytad: &f{zone}'
      title: ''
      subtitle: ''
      actionbar: '&b{zone} &7— &f{minutes}p {seconds}mp'
      bossbar:
        text: '⌚ {zone} — {minutes}p {seconds}mp'
        color: BLUE
        overlay: PROGRESS
```

Szigorúan elutasított állapotok:

- hiányzó vagy nem betöltött világ, eltérő UUID/név;
- nem egész, nem 32 bites vagy világmagasságon kívüli koordináta;
- túl nagy cuboid;
- `refresh-ticks` az 5..72000, illetve `afk-after-seconds` az 1..31536000 tartományon kívül;
- nulla, negatív, nem egész vagy túl nagy interval/roll/item/currency érték;
- 1000-nél nagyobb fizikai currency reward még akkor is, ha a config ennél nagyobb értéket kér;
- NaN/Infinity vagy túl nagy reward weight;
- ismeretlen reward/currency/material/bossbar enum;
- nem `icesmp.*` névtérbeli per-zone permission;
- ismeretlen, eltérő case-ű, lezáratlan, árva vagy egymásba ágyazott command placeholder;
- command control character vagy 256 karakternél hosszabb command.

Egy hibás zóna izoláltan letiltódik és `/afkzone status` alatt látható. A többi érvényes
zóna tovább működik. Hibás globális biztonsági limitnél biztonságos fallback marad érvényben,
a hiba pedig `_global` diagnosztikaként jelenik meg.

## Reward, reload és Folia ownership

A global region scheduler csak drivert futtat. Az AFK driver külön scheduler-életciklust kapott:
a `hud.enabled` és a tablista beállításától függetlenül indul, reloadkor a régi task törlődik és az
új, validált `refresh-ticks` periodussal indul újra. Scheduler rejection esetén nincs néma, hamis
aktív állapot: a rendszer súlyos hibát naplóz, és jutalomtick nem fut.

Minden játékos helye, permissionje, bossbarja, title/actionbarja és inventoryja a saját entity
schedulerén kezelődik. A command reward a global region scheduleren fut konzolként. A player
thread és a global scheduler között csak már feloldott, immutable command string kerül át.

Minden player-tick egy immutable catalog-revíziót visz magával. Reload új revíziót publikál; a
korábban sorba állt entity callback fail-closed, a későn befejeződő command callback pedig nem
futtat régi reward-definíciót. Scheduler retirementkor a transient zone/progress/bossbar registry
kitisztul. A command reward csak sikeres `dispatchCommand` után kerül sikeresként naplózásra és
csak ekkor kap a játékos visszajelzést; ismeretlen command vagy scheduler rejection nem jelez
hamis jutalmat.

A reward clock egy tickben legfeljebb egy ciklust enged át, a maradékidőt megtartja. Így egy
késői tick nem okoz catch-up rewardvihart vagy dupla payoutot. Item overflow nem vész el: a
megmaradt stackek a player aktuális helyén esnek ki. A transient progress restartkor/reloadkor nem
állít crash-safe vagy exactly-once garanciát; ezt a runtime playtest-lista külön kezeli.
