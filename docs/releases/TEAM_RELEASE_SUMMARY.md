# IceSMP integrált tesztrelease — csapatközlemény

<!-- icesmp-doc-id: release.team-summary -->

Az IceSMP jelenlegi integrált állapota mostantól egységes, dokumentált
**teszt- és buildalap**. A dokumentált forrás a `master` branch
`4643ab53586f0c1ee7352df16dcd477013e6fad4` commitja; Minecraft/Paper 1.21.11,
Folia API 1.21.11, Java 21 és Gradle 9.4.1 mellett. Ez nem automatikus production
engedély: a CI azt bizonyítja, hogy a kód fordul és az automatizált ellenőrzések
zöldek, a valódi szerveres átvételt viszont a mellékelt acceptance checklist
szerint még le kell futtatni.

## Mihez képest változik?

A szerveren jelenleg futó autoritatív baseline a csatolt
`IceSMP-1.0-TESTING.jar`, SHA-256:
`da039f0e2bdf0e67b216ce82d7d3fe3b6da0af6e18f6fa175762c37493795a05`.
A JAR nem tartalmaz Git commitazonosítót, és reprodukálható teljes egyezést nem
lehetett bizonyítani, ezért a forrásmapping státusza `BINARY_ONLY`. A változáslista
nem egy feltételezett régi commitot, hanem közvetlenül ennek a binárisnak a
parancsait, bytecode-ját, resource-ait, GUI-it, listenereit, store-jait és
adatvezérelt tartalmát hasonlítja a végleges forráshoz.

A deployed build 30 root parancsot, 56 root aliast, 16 bizonyított permissiont,
14 GUI-családot és 17 tartós állapottulajdonost tartalmaz. Az új release-ben
68 root parancs, 79 root alias, 93 routing alias, 44 bizonyított permission,
22 GUI-holder és
34 lifecycle-ban kezelt persistent owner található. A régi 30 root és 56 root alias
mind megmaradt; a változás főként bővítés, új admineszköz és megbízhatósági
átdolgozás.

## A legfontosabb újdonságok

Az első nagy változás a **natív moderációs és online admincsomag**. Warning,
kick, mute, temporary mute, ban, temporary ban, unmute és unban mellett
előzmények, aktív punishmentek, reportok, privát üzenetek, reply, SocialSpy,
vanish, külön vanish-láthatóság, online inventory- és ender chest read/edit,
invsee escrow/recovery, offline teleport és moderációs GUI tartozik hozzá.
Ezeknél nem elég azt látni, hogy egy parancs hiba nélkül lefutott: restart,
expiry, audit, reconnect és jogosultsági oldalról is igazolni kell az eredményt.

A második nagy terület a **natív MOTD és szervermegjelenítés**. A MOTD default
variánst, `TIME` vagy `RANDOM` kiválasztást, eseményprioritást, vanished játékosokat figyelembe
vevő létszámot és több ikonmódot támogat. A natív tablista headert, footert,
névmegjelenítést, rangrendezést, frakció-, AFK- és vanish-állapotot, valamint
pinget kezel. Ez az IceSMP számára szükséges subset; nem teljes TAB-klón.

A harmadik terület a **sit-only rendszer**. Támogatott blokkformán, ellenőrzött
seat pozícióval, helyfoglalással és lifecycle takarítással lehet ülni. A rendszer
kezeli többek között a damage, sneak, blokktörés, teleport, világváltás, quit,
kick, dismount, reload és disable esetét. Tudatos scope-határ, hogy nincs lay,
crawl, stacking, player sitting vagy NPC sitting, és nem cél a GSit teljes
upstream-paritása.

A negyedik nagy újdonság a **natív crate-rendszer**. Fizikai crate-helyet,
PDC-kulcsot, böngésző- és nyitási GUI-t, súlyozott jutalmakat, tömeges és
részleges nyitást, tartós opening ledgert, auditot, generation fencinget és
settlement/recovery folyamatot ad. Bizonytalan külső side effect esetén nem
állítja automatikusan, hogy minden rendben volt: manuális adminvizsgálatot
igénylő állapotot tarthat fenn. Ezért a command- és currency failure,
full inventory, restart, definíciócsere és fault injection a rollout kötelező
része.

Az ötödik terület a **globális AFK**. Automatikus inaktivitásészlelés, valódi
`/afk` ki/be kapcsolás, aktivitásra visszatérés, tablistajelzés és közös
jutalomkapu került be. A személyes mob-, boss-, dungeon- és Wild Hunt jutalmak
AFK esetben blokkolhatók. A fejlesztés közben megjelent jutalmazó AFK-zóna viszont
teljesen kikerült: nincs zóna, zónaidő, payout, bossbar vagy külön zónascheduler.
Az AxAFKZone és AxAPI nem kerül deploymentbe.

Emellett a forrásban natív **Warden-XP** és külön player/mob
**farmland-trample védelem** található. Ezeket az ICEsmpadditions és FarmProtect
eltávolítása előtt valós szerveren, több értékkel és ki-/bekapcsolással is kézzel
tesztelni kell.

## Nem csak replacementekről van szó

Az új dokumentáció az egész plugint lefedi. A végleges tartalomleltár többek
közt 13 kasztot, 35 specializációt, 160 questet, 21 achievementet,
8 professiont, 16 profession-specializációt, 438 professionreceptet,
54 blueprintet, 81 professionanyagot, 2 crate-et, 6 relikviát,
21 rituálét, 4 frakciót/valutát és 6 territorytípust azonosít.

A teljes rendszer része a kaszt-, spell-, mastery-, combo- és talentfejlődés;
a frakció, király, tanács, adó, bűn, bounty, párbaj, war, raid és szezon;
a bank, váltó, market, auction, bolt, caravan és komp; a claim, territory,
dungeon, világregen, portal policy és combat tag; a quest, profession, crafting,
recept, blueprint, unique item és relikvia; valamint a pet, party, céh, parkour,
event, boss, NPC-binding, HUD, tablista, MOTD, admin GUI és configkezelés.

Fontos különbség, hogy egy registry- vagy configdefiníció nem bizonyít kész
fizikai világhelyszínt. A 18 hivatkozott NPC-ID, a territory- és parkourhivatkozás,
a boss- és eventdefiníció builder/admin előkészítést igényelhet. A lore- és
teaseranyag pedig tervezési/kommunikációs forrás: csak az került aktív
funkcióként a katalógusba, amelynek tényleges registry-, config- és runtime
elérési bizonyítéka van.

## Mit kell tenniük az adminoknak és moderátoroknak?

Az admincsapat először készítsen mentést a jelenlegi JAR-ról, configokról,
pluginadatokról és világokról. A régi élő configot ne másolja át vakon: a bundled
default, a forrás által támogatott capability és az élő override külön állapot.
Külön figyelmet kell fordítani a moderation, MOTD, sit, crate, tablista és AFK
új configterületeire, a stale AFK-zone/lay/crawl/stacking kulcsok eltávolítására,
valamint az öt ismert, kódban olvasott, de bundled defaultból hiányzó configpathra.

A permissionkiosztásnál külön szerepbe kerüljön a punishment, SocialSpy,
vanish visibility, inventory read, inventory edit, ender chest edit, offline
teleport, crate admin/recovery, currency és developer/debug jog. A moderátori
folyamatokat legalább két játékossal, helper/mod/admin szerepkörrel és konzolról
is tesztelni kell. Minden hibához kerüljön szerverlog, config snapshot, érintett
UUID és reprodukció.

## Mit kell tenniük a buildereknek és eventeseknek?

A crate-helyeket natív adminfolyammal kell létrehozni, cserélni és törölni.
Világátnevezés, WorldEdit vagy crate-definition csere után a hely- és generation
kötést újra kell ellenőrizni. Tesztelendő a main/off-hand kulcs, több stack,
dupla kattintás, full inventory, overflow, eltűnt blokk és rossz világpolicy.

Az üléshez stairs, alsó/felső slabs, carpet, moss carpet, pale moss carpet és
hóréteg szükséges tesztmátrixban; folyadék, veszélyes support és rossz clearance
negatív eset. A support blokk törése és minden kilépési életciklus után nem
maradhat seat entity.

A questekhez, dungeonökhöz, bossokhoz, rituálékhoz, eventekhez, NPC-khez,
territorykhoz, komp- és teleportpontokhoz fizikai világbejárás kell.
WorldGuard, claim és spawn policy mellett és nélkül is ellenőrizni kell a
spawnokat és az interakciókat. Élő eventet csak start/stop/restart/timeout és
cleanup bizonyítása után szabad meghirdetni.

## Mi került ki?

Végleg nincs jutalmazó AFK-zóna, AFK payout és AFK bossbar. Nincs lay, crawl,
stacking, player sitting és NPC sitting. Nem cél a GSit vagy TAB teljes
upstream-paritása. Ezeket a csapat ne ígérje játékosfunkcióként és ne tartsa
deploymentblokkoló „majdnem kész” scope-ként.

## Mely külső pluginokat nem szabad még eltávolítani?

- **GSit:** a sit-only runtime acceptance végéig maradjon.
- **CrazyCrates:** a teljes crate runtime és fault-injection acceptance végéig
  maradjon.
- **SModeration és InvSee++:** a natív moderation/admin suite runtime elfogadásáig
  maradjon.
- **MiniMOTD:** a valós szerverlista-ping és ikon teszt végéig maradjon.
- **TAB:** az élő TAB-config leltára és a szükséges subset átvétele alapján
  dönthető el; teljes paritás nincs.
- **ICEsmpadditions és FarmProtect:** a Warden-XP és mindkét trample útvonal
  kézi tesztjéig maradjon.

Az AxAFKZone és AxAPI nem kerül deploymentbe, mert a hozzájuk tartozó jutalmazó
AFK-zóna scope törölve lett.

## Hol található a teljes dokumentáció?

- Fő kézikönyv: `docs/releases/ICESMP_RELEASE_AND_TEAM_GUIDE.md`
- A futó buildhez képesti changelog:
  `docs/releases/DEPLOYED_BUILD_TO_RELEASE_CHANGELOG.md`
- Teljes Git-fejlesztési changelog:
  `docs/releases/ICESMP_FULL_DEVELOPMENT_CHANGELOG.md`
- Admin/moderátor kézikönyv:
  `docs/guides/ADMIN_AND_MODERATOR_GUIDE.md`
- Builder/world designer kézikönyv:
  `docs/guides/BUILDER_AND_WORLD_DESIGNER_GUIDE.md`
- Command, permission, config, GUI és adat referenciák:
  `docs/reference/`
- Külső pluginok döntési mátrixa:
  `docs/releases/EXTERNAL_PLUGIN_STATUS.md`
- Pipálható runtime tesztcsomag:
  `docs/releases/RELEASE_ACCEPTANCE_CHECKLIST.md`
- Egyben nyomtatható változat:
  `docs/releases/ICESMP_RELEASE_PACK.md`

A javasolt használati sorrend: a csapat először ezt a közleményt és a fő
kézikönyvet olvassa el; az adminok és builderek ezután a saját guide-jukat;
a deploymentért felelős személy a changelogot, config/permission referenciát és
pluginstátuszt; a tesztelők végül az acceptance checklistet használják
bizonyítékvezérelt jegyzőkönyvként.
