# 14. Parancsok listája 📜

Itt minden parancsot megtalálsz egy helyen. A `< >` közé **te írsz be** valamit; a `[ ]` azt
jelenti, hogy **elhagyható**.

> ## 🖱️ A legegyszerűbb út: `/menu`
> Nem kell parancsokat gépelned! Írd be: **`/menu`** (vagy `/hub`), és egy **kattintós
> főmenü** nyílik meg, ahonnan minden rendszer egy gombnyomásra elérhető, tematikus sorokba
> rendezve: **Karakter** (Karakterlap, Varázskönyv, Társ, Küldetésnapló, Napi küldetés,
> Elérések, Ranglisták) • **Közösség & világ** (Frakció, Csapat, Birtok, Események,
> Körözési lista, Relikviák, Lélekszilánk) • **Gazdaság** (Bank & Pénz, Piac — és
> adminoknak egy admin panel). Minden almenüben gombokkal intézhetsz mindent —
> a háttérben ugyanazokat a parancsokat futtatja, amiket lent látsz.

## Mindennapi parancsok (mindenkinek)

| Parancs | Aliasok | Mit csinál |
|---|---|---|
| `/menu` | `hub`, `m` | **Központi kattintós menü** — innen minden elérhető |
| `/leaderboard` | `lb`, `top`, `rangsor` | Ranglisták: leggazdagabb, legmagasabb szint, legtöbb raid-kill |
| `/achievements` | `ach`, `eleresek` | Elérések (mérföldkövek + jutalmak) |
| `/stats [név]` | — | Statisztika-profil: ölések, halálok, K/D, mob-ölések, castolt spellek, teljesített questek |
| `/hud <szekció>` | — | HUD-oldalsáv szekciók ki-be kapcsolása (frakcio/kaszt/eroforras/esemeny/valuta/csapat/mind) |
| `/sit` | — | Leülés, ahol állsz (újra `/sit` vagy sneak = felállás); lépcsőre/fél-lapra üres kézzel jobb-katt is leültet |
| `/sit fekves` | — | Fekvő póz (LibsDisguises szükséges hozzá); újra kiadva vagy mozgásra felállsz |
| `/crate buy <id> [db]` / `/crate info [id]` | `ladak`, `crates` | Láda-kulcs vásárlása frakció-valutáért / jutalom-esélyek megtekintése (kulccsal a ládára jobb-katt = nyitás; a nyereményt egy 3D-ikon tárja fel a láda fölött) |
| `/report <név> <ok>` | `bejelent` | Játékos bejelentése a moderátoroknak (percenként egyszer) |
| `/daily` | `napi` | A napi küldetés és haladásod |
| `/pet [menu\|item\|summon\|dismiss\|name\|stance\|info]` | `tars`, `companion` | Társ-GUI (üresen), befogó eszköz, idézés, név, állásmód (Vadmester / Nekromanta / Szentségtelen / Boszorkánymester) |
| `/parkour list\|start <id>` | `trial`, `palya` | Időmérős parkour-pályák |
| `/market search <szöveg>` | `piac`, `ah` | Keresés a piacon (a /market mellett) |
| `/profile` | `karakter`, `char`, `status` | A **karakterlap** — kaszt, spec, szakma, talent, képesség-fa menük |
| `/faction join <frakció>` | `/f` | Belépés egy frakcióba (`red`/`blue`/`neutral`) |
| `/faction leave` | `/f` | Kilépés a frakcióból |
| `/faction king vote <játékos>` | `/f` | Szavazás a frakciód királyára |
| `/bank balance` | `wallet`, `vault` | Banki egyenlegeid |
| `/bank deposit` | | Tokenek bankba helyezése (**csak fővárosban**) |
| `/bank withdraw <valuta> <összeg>` | | Tokenek kivétele (**csak fővárosban**) |
| `/currency balance` | `money`, `eco` | Egyenleg gyorsnézet |
| `/currency pay <játékos> <összeg> [valuta]` | | Közvetlen utalás — **alapból kikapcsolva** (KP-gazdaság) |
| `/currency exchange <összeg> <honnan> <hová>` | | Valutaváltás (**csak fővárosban**) |
| `/currency rates` | | Aktuális árfolyamok |
| `/market` | `piac`, `ah` | Piactér böngésző (vásárlás / licitálás) |
| `/market sell <ár> [valuta]` | | A kézben tartott tárgy eladása |
| `/market auction <kikiáltási ár> [óra] [valuta] [buyout:<ár>]` | | Aukció indítása a kézben tartott tárgyra (a `buyout:` opcionális azonnali-vétel ár) |
| `/market claim` | | Megnyert / visszajáró aukciós tárgyak átvétele |
| `/market cancel` | | Saját eladásaid visszavonása (élő licites aukció nem) |
| `/adomany` | `donate`, `adomanylada` | Közösségi adomány-láda böngésző (ingyenes elvétel) |
| `/adomany add` | | A kézben tartott tárgy (teljes stack) adományozása a közös ládába |
| `/spec list` / `/spec choose <id>` | `specialization`, `specializacio` | Specializációk |
| `/spec respec <class\|profession>` | | Specializáció visszaváltása |
| `/talent` / `/talent spend <class\|profession> <talent>` | `talents`, `talentfa` | Talentek |
| `/emlek` / `/emlek xp\|talent\|spec\|lore` | `memory`, `emlekek` | Emlékszilánk-beváltás: kaszt-XP / talentpont / spec-kapu / emlék-töredék |
| `/suttogas <üzenet>` / `/suttogas vad <játékos>` | `sutt` | A Suttogók titkos csatornája / tanú-vád (K9) |
| `/lore <téma>` | `kodex` | A kódex lapjai chatben (frakciók, a Fa, a Kapu, a Suttogók) |
| `/kronika` | `chronicle` | Az utolsó Heti Krónika visszaolvasása (liga-állás, toplisták) |
| `/profession join <szakma>` / `/profession info` | `prof`, `szakma` | Szakmák |
| `/profession recipes` | `prof`, `szakma` | **Recept-könyv** — tanult/zárolt receptek, 1 kattintásos craft |
| `/class givecatalyst` | `kaszt`, `job` | Elveszett Lélekkapocs pótlása |
| `/quest list` / `/quest accept <id>` / `/quest info` | `quests`, `kuldetes` | Küldetések |
| `/quest log` | `gui`, `naplo` | **Küldetésnapló GUI** — Aktív / Felvehető / Teljesített fülek, lapozható |
| `/souls` / `/souls champion` | `soul`, `lelek` | Lélekszilánk (csak Nekromanta) |
| `/soulforge` / `/soulforge fejleszt <ág>` | `lelekkovacs` | **Lélek-kovács** (csak Nekromanta): minion-fejlesztési ágak szilánkért |
| `/bounty` | `fejvadasz`, `korozes` | Körözési lista: ki körözött és mennyit ér a feje |
| `/ceh` / `/ceh alapit\|meghiv\|elfogad\|elhagy\|kirug\|befizet` | `guild`, `gild` | **Céh**: frakción belüli kisközösség (közös XP, céh-szint) |
| `/bestiarium` | `bestiary`, `lajstrom` | **Bestiárium GUI**: elejtett fajok, receptek, territóriumok, bossok — mérföldkő-jutalmakkal |
| `/szakmacel` | `weeklygoal` | A szakmád heti közös célja: állás + a saját hozzájárulásod |
| `/parbaj kihiv <név>` / `/parbaj elfogad\|elutasit` | `duel` | **Becsület-párbaj**: bűnösként elégtétel — győzelemért −1 bűnpont |
| `/kem <célfrakció>` | `spy` | **Kém-álca**: rövid álruha felderítéshez (egy ütés lebuktat) |
| `/market ereklye` | | A piac **ereklye-börze** szűrője (szilánkok, unique anyagok) |
| `/spell` / `/spell upgrade <id>` | `mastery`, `mesterseg` | Spell-mesterség: valutáért rövidebb cooldown ÉS erősebb hatás (sebzés, gyógyítás, effekt-időtartam) |
| `/spellbook` | `varazskonyv`, `konyv`, `sb` | **Varázskönyv**: spellek böngészése (leírás, költség, sebzés, CD) és kiválasztása kattintással. *Sunyíts + jobb katt a Lélekkapcson* is megnyitja. |
| `/events status` | `event`, `esemeny` | „Mi történik most?" — minden aktív világesemény + szezon-állás egyben |
| `/komp [útvonal]` | `ferry` | Átkelés a kompon (a kikötőben állva); útvonal nélkül a járatok listája |
| `/tanacs [szavaz <játékos>\|vasarhet]` | `council` | A Menedék Vének Tanácsa: heti szavazás, tanácstagként Vásár-hét |
| `/events season` / `/events blood-moon` / `/events caravan` | `event`, `esemeny` | Világesemények állása (szezon, vérhold, kereskedő-karaván) |
| `/party invite\|accept\|decline\|leave\|list` | `p`, `parti` | **Party (csapat)**: meghívás, csatlakozás, kilépés, taglista (max 5 fő) |
| `/party kick\|promote <név>` | | Csapatvezetői műveletek: kirúgás, vezetés átadása |
| `/party disband` | | Csapat feloszlatása (csak vezető) |
| `/p <üzenet>` | | **Csapat-chat** — csak a párttagok látják |
| `/claim` | `birtok` | 16×16 blokk gyorsfoglalása körülötted (**saját birtok**, ±20 blokk magasan) |
| `/claim unclaim\|info\|list\|show` | | Claim felszabadítása / infó / lista / határ-kirajzolás |
| `/claim pos1\|pos2\|area` | | Blokk-pontos terület kijelölése és foglalása |
| `/claim wand` | `palca` | **Birtokmérő pálca**: bal katt = 1. sarok, jobb = 2. sarok (ár-előnézet), SNEAK+jobb = foglalás |
| `/claim extend up\|down` | | Claim magasítása / mélyítése (+5 blokk, pénzért) |
| `/claim trust\|untrust <név>` | | Megbízott hozzáadása / elvétele (teljes hozzáférés a claimjeidhez) |

## Király-parancsok (csak a frakció királyának)

| Parancs | Mit csinál |
|---|---|
| `/faction treasury withdraw <összeg>` | Kivétel a frakciókasszából (veretben a kézbe, napi kerettel) |
| `/faction king tax <százalék>` | Frakció adókulcs beállítása |
| `/faction raid <célfrakció> [terület]` | Háború (raid) hirdetése — alapból a védő fővárosáért |
| `/faction caravan send <összeg>` | **Játékos-karaván**: rakomány indítása a kasszából — sikeres kíséretnél +25% érkezik vissza |

A raidhez **mindenki** (nem csak a király) így kapcsolódik:

| Parancs | Mit csinál |
|---|---|
| `/faction raid join` | Jelentkezés harcosnak a felkészülés alatt (max 10/oldal) |
| `/faction raid status` | Raid-állás: fázis, pontok, létszám |

## Admin parancsok (csak adminoknak)

| Parancs | Mit csinál |
|---|---|
| `/icesmp reload` | Konfiguráció újratöltése |
| `/icesmp inspect <név>` | Teljes játékos-riport: kaszt/erőforrás/statok/bűn/claim/questek/cooldownok |
| `/invsee <név>` | Inventory + ender-láda betekintés (pillanatkép, csak olvasás) |
| `/mute <név> [perc] [ok]` / `/unmute <név>` | Némítás (0 = végtelen; perc kihagyva = automatikus eszkaláció a némítás-történet alapján; chat + privát üzenetek), feloldás; `/mute list` |
| `/reports` / `/reports resolve <id>` | Játékos-bejelentések listája és lezárása |
| `/class addxp\|setxp <játékos> <mennyiség>` | Kaszt-XP adása/beállítása |
| `/class givecatalyst\|unlockspell <játékos> [spell]` | Lélekkapocs adása / spell feloldása |
| `/class admin <resetcd\|unlockallskills\|resetskills\|resetclass> <játékos>` | Cooldown-/spell-/**teljes kaszt-reset** egy játékosnak |
| `/profession set\|clear\|addxp` | Szakma-adatok kezelése |
| `/profession blueprint <játékos> <recept-id>` | Tervrajz (recept-feloldó) átadása egy játékosnak |
| `/spec reset <játékos>` | Specializációk törlése |
| `/sinner <játékos> set\|clear\|add` | Bűnös állapot kezelése |
| `/quest complete <játékos> <id>` | Küldetés azonnali teljesítése |
| `/quest admin create <id> <objektíva> <darab> <név...>` | Új küldetés készítése játékon belül (több-objektívás is lehet) |
| `/quest admin addobjective <id> <objektíva> <darab> [leírás]` | További feladat hozzáadása egy questhez |
| `/quest admin set <id> objectives-mode ALL\|SEQUENCE` | Több-objektívás mód: párhuzamos vagy sorban |
| `/quest admin set <id> <mező> <érték...>` | Küldetés-mező beállítása (feltétel, jutalom, giver-npc, dialogue.choices, rotation-group, seasonal...) |
| `/quest admin delete/info/list` | Admin-küldetés törlése / megtekintése / listája |
| `/quest admin builder <id>` | **Kattintós küldetés-szerkesztő** — új id-vel létrehozó varázsló (objektíva-választó + chat-bevitel), létező custom questtel mező-szerkesztő GUI |
| `/currency set <játékos> <összeg> [valuta]` | Valuta-egyenleg beállítása |
| `/faction set <játékos> <frakció>` | Játékos frakcióba sorolása |
| `/relic give <játékos> <relic_id>` | Relikvia adása |
| `/territory circle\|create <típus> ...` | Kör- vagy poligon-zóna kijelölése (típus: faction, protected-faction, protected-city, capital, doom-gate, dungeon) |
| `/territory pos\|undo\|clearpoints\|points\|show [id]` | Poligon-határpontok bejárása és előnézete (pl. városfal mentén) |
| `/territory tp <id>` | Teleportálás a zóna középpontjához |
| `/territory dungeonchest [tábla]` | A nézett láda/hordó regisztrálása kazamata-kincsesládának (újra kiadva törli) |
| `/territory dungeonboss <zóna-id> [tábla]` | Kazamata mini-boss spawn-pont kijelölése (clear <zóna-id> = törlés) |
| `/territory rename\|resize\|settype\|sety <id> ...` | Meglévő zóna módosítása (név / sugár / típus / magassági sáv) |
| `/territory setcapital\|remove\|list\|info` | Főváros gyorskijelölés / zóna törlése / listája / infó |
| `/exchangeboard place\|remove` | Árfolyamtábla lerakása/törlése |
| `/events blood-moon start\|stop` | Vérhold kézi indítása / leállítása |
| `/events worldboss` | Világboss azonnali megidézése |
| `/events invasion` | Szörny-invázió azonnali indítása |
| `/events caravan arrive\|depart` | Kereskedő-karaván kézi indítása / elküldése |
| `/events ambient` | Hangulat-esemény azonnali kiváltása |
| `/events gathering` | Gyűjtögető buff-ablak megnyitása |
| `/events treasure` | Elrejtett kincs elhelyezése |
| `/events wild-hunt` | Vad Hajsza fenevad megidézése |
| `/events abundance` | Bőség-idő indítása |
| `/events challenge` | Kollektív szerver-kihívás indítása |
| `/events escort` | Karaván-kíséret (konvoj) indítása |
| `/events meteor` | Meteor-becsapódás kiváltása |
| `/events stranger` | A Rejtélyes Idegen megidézése |
| `/events spawnpoint add\|remove\|list` | Esemény-spawnpontok (pl. állandó világboss-aréna) — mód: `world-events.anchors.*` |
| `/events cultists` | Kultista esemény azonnali indítása (portya / rítus / hírvivő sorsolással) |
| `/events corruption` | Rontás-góc azonnali nyitása a közeledben |
| `/events archeology` | Régészeti lelőhely azonnali felbukkanása |
| `/events intro [játékos]` | Bemutató újrajátszása |
| `/iceitem <unique\|recept\|relikvia\|tervrajz\|erszeny> <id> [db] [játékos]` | Bármely plugin-item admin-adása |
| `/icesmp config menu` | Kattintható élő-config szerkesztő (kategóriákra bontva) |
| `/claim admin unclaim` | Idegen claim törlése admin-jogon |
| `/parkour setstart\|setfinish\|remove <id>` | Parkour-pálya beállítása |
| `/npcbind <npc> quest\|shop\|bank\|exchange\|clear` (`npckotes`) | NPC explicit kötése küldetéshez/bolthoz/bankárhoz/valutaváltóhoz (a bank/exchange a meglévő bank menüt nyitja) |
| `/npcbind list` | Minden NPC-kötés kiírása |

---

➡️ Tovább: [Party (csapat)](15-csapat.md) • [Vissza a tartalomhoz](README.md)
