# IceSMP teljes parancsreferencia

> Dokumentált HEAD: `4643ab53586f0c1ee7352df16dcd477013e6fad4`  
> Deployed baseline: `IceSMP-1.0-TESTING.jar` (`da039f…95a05`, source mapping: `BINARY_ONLY`).

Ez a referencia a tényleges bootstrap-regisztrációból és az oda bekötött végrehajtási ágakból készült. A forrásban található, de nem regisztrált parancsosztály nem számít aktív parancsnak. A `<…>` kötelező, a `[…]` opcionális argumentum.

## Lefedettség és státuszjelölés

- root parancs: **68 / 68**;
- root alias: **79 / 79**;
- feloldott funkcionális route: **286**;
- korábban dinamikusnak vagy nestednek jelölt, feloldatlan route: **0**;
- `Új`: a deployed JAR nem regisztrálta; `Megváltozott`: a route család jelen volt, de a handler bytecode-ja vagy a routing bővült; `Változatlan`: az aktív handler bytecode-ja egyezik.

## Root parancsok és aliasok

| Root | Aliasok | Rövid cél | Tab | Deployed státusz |
|---|---|---|---|---|
| `/achievements` | `/ach`, `/eleresek` | Elérések (mérföldkövek + jutalmak) | van | Megváltozott |
| `/adomany` | `/adomanylada`, `/donate` | Közösségi adomány-láda | van | Megváltozott |
| `/afk` | — | Önkéntes AFK-jelölés | nincs | Új |
| `/ban` | — | Kitiltás (admin) | van | Új |
| `/bank` | `/vault`, `/wallet` | Bank parancsok | nincs | Változatlan |
| `/bestiarium` | `/bestiary`, `/lajstrom` | Bestiárium — a krónikás-lajstromod | van | Új |
| `/bounty` | `/fejvadasz`, `/korozes` | Körözési lista (fejpénzek) | van | Változatlan |
| `/ceh` | `/gild`, `/guild` | Céh (frakción belüli kisközösség) parancsok | van | Új |
| `/claim` | `/birtok` | Terület-claim parancsok | van | Megváltozott |
| `/class` | `/job`, `/kaszt` | Kaszt (class): szint, Lélekkapocs, admin | nincs | Megváltozott |
| `/crate` | `/crates`, `/ladak` | Láda (crate) parancsok | van | Új |
| `/currency` | `/eco`, `/money` | Valuta parancsok | nincs | Megváltozott |
| `/daily` | `/napi` | Napi küldetés | van | Megváltozott |
| `/emlek` | `/emlekek`, `/memory` | Emlékszilánk-beváltás (visszaemlékezés) | van | Új |
| `/events` | `/esemeny`, `/event` | Világesemény parancsok | van | Megváltozott |
| `/exchangeboard` | `/arfolyamtabla`, `/ratesboard` | Árfolyamtábla admin | van | Megváltozott |
| `/faction` | `/f` | Frakció parancsok | nincs | Megváltozott |
| `/history` | — | Teljes büntetési előzmény (admin) | van | Új |
| `/hud` | — | HUD beállítások | van | Új |
| `/iceitem` | `/icegive`, `/iitem` | Plugin-item kiadása (admin): unique/recept/relikvia/tervrajz/erszeny/dev | van | Új |
| `/icesmp` | `/ismp` | IceSMP admin | van | Megváltozott |
| `/invsee` | — | Online inventory/ender live nézet (admin) | van | Új |
| `/kem` | `/spy` | Kém-álca — rövid felderítő álöltözet | van | Új |
| `/kick` | — | Játékos kirúgása (admin) | van | Új |
| `/komp` | `/ferry` | Kompjárat: átkelés a túlpartra | van | Új |
| `/kronika` | `/chronicle` | Az utolsó Heti Krónika visszaolvasása | nincs | Új |
| `/leaderboard` | `/lb`, `/rangsor`, `/top` | Ranglisták (szint, vagyon, raid-kill) | van | Megváltozott |
| `/lore` | `/kodex` | A kódex lapjai — frakciók és helyek története | van | Új |
| `/market` | `/ah`, `/piac` | Piactér parancsok | van | Megváltozott |
| `/menu` | `/hub`, `/m` | Központi menü — minden parancs egy helyen | van | Megváltozott |
| `/moderation` | `/mod` | Natív moderációs admin GUI | van | Új |
| `/msg` | — | Privát üzenet | van | Új |
| `/mute` | — | Némítás (admin) | van | Új |
| `/npcbind` | `/npckotes` | NPC-kötések: küldetés/bolt/bankár/valutaváltó (admin) | van | Megváltozott |
| `/offlinetp` | — | Teleport az utolsó kijelentkezési helyre | van | Új |
| `/parbaj` | `/duel` | Becsület-párbaj — elégtétel a bűnökért | van | Új |
| `/parkour` | `/palya`, `/trial` | Parkour-pályák (futás, admin beállítás) | van | Megváltozott |
| `/party` | `/p`, `/parti` | Party (csapat) parancsok | van | Megváltozott |
| `/pet` | `/companion`, `/tars` | Társ (befogó item, idézés, név, szint) | van | Megváltozott |
| `/profession` | `/prof`, `/szakma` | Szakma (profession) parancsok | van | Megváltozott |
| `/profile` | `/char`, `/karakter`, `/status` | Karakterlap — kaszt, spec, szakma, talent menük | van | Változatlan |
| `/punishments` | — | Aktív büntetések (admin) | van | Új |
| `/quest` | `/kuldetes`, `/quests` | Küldetés parancsok | van | Megváltozott |
| `/relic` | `/relics`, `/relikvia` | Relikvia parancsok (admin) | van | Megváltozott |
| `/reply` | `/r` | Válasz privát üzenetre | van | Új |
| `/report` | `/bejelent` | Játékos bejelentése (admin: /reports) | van | Új |
| `/reports` | — | Bejelentések kezelése (admin) | van | Új |
| `/sinner` | — | Bűnös állapot kezelése (admin) | van | Megváltozott |
| `/sit` | — | Ülés (leül/feláll) | van | Új |
| `/socialspy` | — | Privát üzenetek megfigyelése (admin) | van | Új |
| `/soulforge` | `/lelekkovacs` | Lélek-kovács — a Nekromanta minion-fejlesztései | van | Új |
| `/souls` | `/lelek`, `/soul` | Lélekszilánk parancsok | van | Megváltozott |
| `/spec` | `/specializacio`, `/specialization` | Specializáció parancsok | van | Megváltozott |
| `/spell` | `/mastery`, `/mesterseg`, `/spells` | Spell-mesterség (cooldown + erő valutáért) | van | Megváltozott |
| `/spellbook` | `/konyv`, `/sb`, `/varazskonyv` | Varázskönyv: spellek böngészése és kiválasztása | van | Megváltozott |
| `/stats` | — | Statisztika-profil | van | Új |
| `/suttogas` | `/sutt` | A Suttogók titkos csatornája és tanú-vád | van | Új |
| `/szakmacel` | `/weeklygoal` | Szakma-céhek heti közös céljai | van | Új |
| `/talent` | `/talentfa`, `/talents` | Talent-fa parancsok | van | Megváltozott |
| `/tanacs` | `/council` | A Menedék Vének Tanácsa: szavazás, Vásár-hét | van | Új |
| `/tell` | — | Privát üzenet | van | Új |
| `/tempban` | — | Ideiglenes kitiltás (admin) | van | Új |
| `/territory` | `/terulet` | Frakció terület parancsok | van | Megváltozott |
| `/unban` | — | Kitiltás feloldása (admin) | van | Új |
| `/unmute` | — | Némítás feloldása (admin) | van | Új |
| `/vanish` | `/v` | Admin láthatatlanság | van | Új |
| `/w` | — | Privát üzenet | van | Új |
| `/warn` | — | Figyelmeztetés (admin) | van | Új |

## Route-ok

A „GUI” oszlop alternatív elérést jelez; a GUI-gomb ugyanazt a command/service kaput használja. A konzol-kompatibilitás az aktív végrehajtási ágból következik, nem az osztály nevéből.

### `/achievements`

Aliasok: `/ach`, `/eleresek`. Deployed státusz: **Megváltozott**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/achievements` | Elérések read-only GUI-ja. | Játékos | — | Nincs | Elérések nézet | — | Megváltozott |

### `/adomany`

Aliasok: `/adomanylada`, `/donate`. Deployed státusz: **Megváltozott**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/adomany` | Lapozható közösségi adományláda. | Játékos | — | Nincs | Adományláda | — | Megváltozott |
| `/adomany add` | A főkéz teljes stackjének adományozása. | Játékos | — | add | Adományláda hozzáadás gomb | — | Megváltozott |

### `/afk`

Aliasok: nincs. Deployed státusz: **Új**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/afk` | A globális kézi AFK-jelölés ki-/bekapcsolása. | Játékos | — | Nincs | — | Nem zónaparancs és nem fizet jutalmat; aktivitás automatikusan visszahoz. | Új |

### `/ban`

Aliasok: nincs. Deployed státusz: **Új**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/ban <játékos> [ok...]` | Végleges kitiltás. | Játékos vagy konzol | icesmp.moderation.ban | jogosultság szerint látható online játékosok; időzített műveletnél időminták | Moderációs GUI | Offline, de ismert célpont is használható. | Új |

### `/bank`

Aliasok: `/vault`, `/wallet`. Deployed státusz: **Változatlan**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/bank` | Bankparancsok súgója. | Játékos | — | balance/deposit/withdraw | — | — | Változatlan |
| `/bank balance` | Minden saját bankegyenleg. | Játékos | — | Nincs | Bank menü | A deposit/withdraw főváros-only lehet. A help és tab nem sorolja a dark értéket, de a parser elfogadja. | Változatlan |
| `/bank deposit` | Fizikai valuta teljes befizetése. | Játékos | — | Nincs | Bank menü | A deposit/withdraw főváros-only lehet. A help és tab nem sorolja a dark értéket, de a parser elfogadja. | Változatlan |
| `/bank withdraw <red\|blue\|neutral\|dark> <összeg>` | Fizikai valuta kivétele. | Játékos | — | red/blue/neutral (a dark elfogadott, de nincs javasolva) | Bank menü | A deposit/withdraw főváros-only lehet. A help és tab nem sorolja a dark értéket, de a parser elfogadja. | Változatlan |

### `/bestiarium`

Aliasok: `/bestiary`, `/lajstrom`. Deployed státusz: **Új**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/bestiarium` | Fajok, receptek, territóriumok és bossok read-only lajstroma. | Játékos | — | Nincs | Bestiárium | — | Új |

### `/bounty`

Aliasok: `/fejvadasz`, `/korozes`. Deployed státusz: **Változatlan**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/bounty` | Online körözési lista. | Játékos vagy konzol | — | Nincs | — | — | Változatlan |

### `/ceh`

Aliasok: `/gild`, `/guild`. Deployed státusz: **Új**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/ceh` | Céhparancsok súgója. | Játékos | — | magyar alparancsok; meghívás/kirúgásnál online nevek | — | — | Új |
| `/ceh letrehoz <név...>`<br>Routing alias: `create` | Céh alapítása. | Játékos | — | magyar alparancsok; meghívás/kirúgásnál online nevek | — | — | Új |
| `/ceh meghiv <online-játékos>`<br>Routing alias: `invite` | Tag meghívása. | Játékos | — | magyar alparancsok; meghívás/kirúgásnál online nevek | — | — | Új |
| `/ceh elfogad`<br>Routing alias: `accept` | Meghívás elfogadása. | Játékos | — | magyar alparancsok; meghívás/kirúgásnál online nevek | — | — | Új |
| `/ceh elhagy`<br>Routing alias: `leave` | Céh elhagyása. | Játékos | — | magyar alparancsok; meghívás/kirúgásnál online nevek | — | — | Új |
| `/ceh kirug <ismert-játékos>`<br>Routing alias: `kick` | Tag kirúgása. | Játékos | — | magyar alparancsok; meghívás/kirúgásnál online nevek | — | — | Új |
| `/ceh befizet <összeg>`<br>Routing alias: `deposit` | Befizetés a céhkasszába. | Játékos | — | magyar alparancsok; meghívás/kirúgásnál online nevek | — | — | Új |
| `/ceh info` | Saját céh részletei. | Játékos | — | magyar alparancsok; meghívás/kirúgásnál online nevek | — | — | Új |
| `/ceh lista`<br>Routing alias: `list` | Top céhek listája. | Játékos | — | magyar alparancsok; meghívás/kirúgásnál online nevek | — | — | Új |

### `/claim`

Aliasok: `/birtok`. Deployed státusz: **Megváltozott**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/claim [claim]` | Aktuális chunk gyorsfoglalása. | Játékos | — | alparancs; trustnál online játékos; extendnél up/down | Claim menü | — | Megváltozott |
| `/claim unclaim` | Aktuális saját claim feloldása. | Játékos | — | alparancs; trustnál online játékos; extendnél up/down | Claim menü | — | Megváltozott |
| `/claim info` | Aktuális chunk tulajdonosa és határa. | Játékos | — | alparancs; trustnál online játékos; extendnél up/down | Claim menü | — | Megváltozott |
| `/claim list` | Saját claimek listája. | Játékos | — | alparancs; trustnál online játékos; extendnél up/down | Claim menü | — | Megváltozott |
| `/claim trust <online-játékos>` | Megbízott hozzáadása minden claimhez. | Játékos | — | alparancs; trustnál online játékos; extendnél up/down | Megbízottak GUI | — | Megváltozott |
| `/claim untrust <online-játékos>` | Megbízott eltávolítása. | Játékos | — | alparancs; trustnál online játékos; extendnél up/down | Megbízottak GUI | — | Megváltozott |
| `/claim trustgui` | Megbízottak/közeli játékosok GUI. | Játékos | — | alparancs; trustnál online játékos; extendnél up/down | Megbízottak GUI | — | Új |
| `/claim show` | Claimhatárok kirajzolása. | Játékos | — | alparancs; trustnál online játékos; extendnél up/down | Claim menü | — | Megváltozott |
| `/claim pos1` | Első blokk-pontos sarok. | Játékos | — | alparancs; trustnál online játékos; extendnél up/down | Claim menü | — | Megváltozott |
| `/claim pos2` | Második blokk-pontos sarok. | Játékos | — | alparancs; trustnál online játékos; extendnél up/down | Claim menü | — | Megváltozott |
| `/claim wand`<br>Routing alias: `palca` | Birtokmérő pálca. | Játékos | — | alparancs; trustnál online játékos; extendnél up/down | Claim menü | — | Új |
| `/claim area` | Kijelölt blokkterület foglalása. | Játékos | — | alparancs; trustnál online játékos; extendnél up/down | Claim menü | — | Megváltozott |
| `/claim extend [up\|down]` | Függőleges kiterjesztés; alapértelmezés: up. | Játékos | — | alparancs; trustnál online játékos; extendnél up/down | Claim menü | — | Megváltozott |
| `/claim admin unclaim` | Idegen claim törlése az aktuális helyen. | Játékos | icesmp.admin.territory | alparancs; trustnál online játékos; extendnél up/down | Admin menü | — | Megváltozott |
| `/claim help` | Súgó. | Játékos | — | alparancs; trustnál online játékos; extendnél up/down | — | — | Megváltozott |

### `/class`

Aliasok: `/job`, `/kaszt`. Deployed státusz: **Megváltozott**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/class` | Kaszt-admin súgó. | Játékos vagy konzol | — | hét admin alparancs | — | — | Megváltozott |
| `/class addxp <online-játékos> <mennyiség>` | Kaszt-XP hozzáadása. | Játékos vagy konzol | icesmp.admin.job | online játékosok | — | A célpontot igénylő ágak csak online játékost fogadnak. | Megváltozott |
| `/class setxp <online-játékos> <mennyiség>` | Kaszt-XP beállítása. | Játékos vagy konzol | icesmp.admin.job | online játékosok | — | A célpontot igénylő ágak csak online játékost fogadnak. | Megváltozott |
| `/class status <online-játékos>` | Célpont kasztállapota. | Játékos vagy konzol | icesmp.admin.job | online játékosok | — | A célpontot igénylő ágak csak online játékost fogadnak. | Megváltozott |
| `/class unlockspell <online-játékos> <spell-id>` | Spell adminfeloldása. | Játékos vagy konzol | icesmp.admin.job | online játékos → spell ID | — | A célpontot igénylő ágak csak online játékost fogadnak. | Megváltozott |
| `/class givecatalyst <online-játékos>` | Lélekkapocs adminátadása. | Játékos vagy konzol | icesmp.admin.job | online játékosok | — | A célpontot igénylő ágak csak online játékost fogadnak. | Megváltozott |
| `/class listspells` | Regisztrált spellek adminlistája. | Játékos vagy konzol | icesmp.admin.job | Nincs | — | A célpontot igénylő ágak csak online játékost fogadnak. | Megváltozott |
| `/class admin resetcd <online-játékos>` | A célpont minden spell-cooldownjának törlése. | Játékos vagy konzol | icesmp.admin.job | adminművelet → online játékos | — | Csak online célpont; tartós adminmutáció. | Megváltozott |
| `/class admin unlockallskills <online-játékos>` | Minden regisztrált spell feloldása a célpontnak. | Játékos vagy konzol | icesmp.admin.job | adminművelet → online játékos | — | Csak online célpont; tartós adminmutáció. | Megváltozott |
| `/class admin resetskills <online-játékos>` | A célpont feloldott spelljeinek törlése. | Játékos vagy konzol | icesmp.admin.job | adminművelet → online játékos | — | Csak online célpont; tartós adminmutáció. | Megváltozott |
| `/class admin resetclass <online-játékos>` | A célpont kasztjának és kapcsolódó fejlődésének resetje. | Játékos vagy konzol | icesmp.admin.job | adminművelet → online játékos | — | Csak online célpont; tartós adminmutáció. | Megváltozott |

### `/crate`

Aliasok: `/crates`, `/ladak`. Deployed státusz: **Új**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/crate` | Natív ládalista GUI; konzolnak vagy ismeretlen alparancsnál crate-súgó. | Játékos; konzolnak csak súgó | icesmp.crate.use | Nincs | Crate böngésző | Az ismeretlen első argumentum a jogosultságfüggő súgóra esik vissza. | Új |
| `/crate buy <láda-id> [darab]` | Kulcs vásárlása. | Játékos | icesmp.crate.use + opcionális crate-specifikus jog | láda-id-k | Crate böngésző | Darab: 1..a kódbeli biztonsági maximum. | Új |
| `/crate info [láda-id]` | Kulcsár, cooldown, mass-open szabály és esélyek. | Játékos | icesmp.crate.use + opcionális crate-specifikus jog | láda-id-k | Crate böngésző | ID nélkül a böngésző nyílik. | Új |
| `/crate preview <láda-id>` | Jutalom-előnézet megnyitása. | Játékos | icesmp.crate.use + opcionális crate-specifikus jog | láda-id-k | Crate preview | Read-only előnézet. | Új |
| `/crate set <láda-id>` | A legfeljebb 5 blokkra nézett blokk crate-helynek mentése. | Játékos | icesmp.admin.crate | láda-id-k | — | Tartós helymutáció. | Új |
| `/crate remove` | A nézett crate-hely törlése. | Játékos | icesmp.admin.crate | remove | — | A definíciót nem törli. | Új |
| `/crate give <online-játékos> <láda-id> [darab]` | Hiteles PDC-kulcs átadása. | Játékos vagy konzol | icesmp.admin.crate | online játékos → láda-id | — | A célpontnak online kell lennie. | Új |
| `/crate list` | Tartós fizikai crate-helyek listája. | Játékos vagy konzol | icesmp.admin.crate | list | — | — | Új |
| `/crate stats [játékos\|uuid] [láda-id]` | Nyitási statisztikák lekérdezése. | Játékos vagy konzol | icesmp.admin.crate | ismert játékos → láda-id | — | Konzolról a cél kötelező. | Új |
| `/crate resetstats <játékos\|uuid> [láda-id\|all]` | Nyitási stat/cooldown törlése. | Játékos vagy konzol | icesmp.admin.crate | ismert játékos → láda-id/all | — | Tartós adminmutáció. | Új |
| `/crate status` | Valid definíciók, config-hibák és manuális recovery tételek. | Játékos vagy konzol | icesmp.admin.crate | status | — | A MANUAL_REVIEW tételek adminfolyamatot igényelnek. | Új |

### `/currency`

Aliasok: `/eco`, `/money`. Deployed státusz: **Megváltozott**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/currency` | Valutaparancsok súgója. | Játékos vagy konzol | — | balance/pay/set/exchange/rates | — | — | Megváltozott |
| `/currency balance [currency]` | Saját bankegyenleg megjelenítése. | Játékos | — | valutatípusok | Bank/valutaváltó menü | A pay alapból kikapcsolható; banki műveletek fővároshoz köthetők. | Megváltozott |
| `/currency pay <online-játékos> <összeg> [currency]` | Közvetlen átutalás. | Játékos | — | online játékos → valutatípus | Bank/valutaváltó menü | A pay alapból kikapcsolható; banki műveletek fővároshoz köthetők. | Megváltozott |
| `/currency set <online-játékos> <összeg> [currency]` | Egyenleg adminbeállítása. | Játékos vagy konzol | icesmp.admin.currency | online játékos → valutatípus | Bank/valutaváltó menü | A pay alapból kikapcsolható; banki műveletek fővároshoz köthetők. | Megváltozott |
| `/currency exchange <összeg> <honnan> <hová>` | Valutaváltás. | Játékos | — | valutatípusok | Bank/valutaváltó menü | A pay alapból kikapcsolható; banki műveletek fővároshoz köthetők. | Megváltozott |
| `/currency rates` | Aktuális árfolyamok. | Játékos vagy konzol | — | Nincs | Bank/valutaváltó menü | A pay alapból kikapcsolható; banki műveletek fővároshoz köthetők. | Megváltozott |

### `/daily`

Aliasok: `/napi`. Deployed státusz: **Megváltozott**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/daily` | Napi és heti küldetés állása. | Játékos | — | Nincs | — | — | Megváltozott |

### `/emlek`

Aliasok: `/emlekek`, `/memory`. Deployed státusz: **Új**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/emlek` | Emlékszilánk-egyenleg és beváltási lehetőségek. | Játékos | — | xp/talent/spec/lore | — | Argumentum nélkül és ismeretlen első argumentumnál is az állapotnézet fut. | Új |
| `/emlek xp` | Szilánk kaszt-XP-re. | Játékos | — | xp/talent/spec/lore | — | — | Új |
| `/emlek talent` | Szilánk bónusz talentpontra. | Játékos | — | xp/talent/spec/lore | — | — | Új |
| `/emlek spec` | Spec szintkapu korai feloldása. | Játékos | — | xp/talent/spec/lore | — | — | Új |
| `/emlek lore` | Véletlen emléktöredék. | Játékos | — | xp/talent/spec/lore | — | — | Új |

### `/events`

Aliasok: `/esemeny`, `/event`. Deployed státusz: **Megváltozott**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/events [season]` | Szezon-liga állása. | Játékos vagy konzol | — | jogosultságfüggő alparancs; blood-moon/caravan művelet; intro online játékos | Események/admin menü | Az ismeretlen első argumentum is a szezonállásra esik vissza. | Megváltozott |
| `/events status` | Minden aktív esemény és szezonállás. | Játékos vagy konzol | — | jogosultságfüggő alparancs; blood-moon/caravan művelet; intro online játékos | Események/admin menü | — | Új |
| `/events blood-moon`<br>Routing alias: `bloodmoon` | Vérhold állapota. | Játékos vagy konzol | — | jogosultságfüggő alparancs; blood-moon/caravan művelet; intro online játékos | Események/admin menü | — | Megváltozott |
| `/events blood-moon start`<br>Routing alias: `bloodmoon` | Vérhold kézi indítása. | Játékos vagy konzol | icesmp.admin.events | jogosultságfüggő alparancs; blood-moon/caravan művelet; intro online játékos | Események/admin menü | A blood-moon/caravan ismeretlen második argumentuma usage hibát ad. | Megváltozott |
| `/events blood-moon stop`<br>Routing alias: `bloodmoon` | Vérhold kézi leállítása. | Játékos vagy konzol | icesmp.admin.events | jogosultságfüggő alparancs; blood-moon/caravan művelet; intro online játékos | Események/admin menü | A blood-moon/caravan ismeretlen második argumentuma usage hibát ad. | Megváltozott |
| `/events caravan`<br>Routing alias: `karavan` | Kereskedő-karaván állapota. | Játékos vagy konzol | — | jogosultságfüggő alparancs; blood-moon/caravan művelet; intro online játékos | Események/admin menü | — | Megváltozott |
| `/events caravan arrive`<br>Routing alias: `karavan`, `start` | Karaván kézi érkeztetése. | Játékos vagy konzol | icesmp.admin.events | jogosultságfüggő alparancs; blood-moon/caravan művelet; intro online játékos | Események/admin menü | A blood-moon/caravan ismeretlen második argumentuma usage hibát ad. | Megváltozott |
| `/events caravan depart`<br>Routing alias: `karavan`, `stop` | Karaván kézi távoztatása. | Játékos vagy konzol | icesmp.admin.events | jogosultságfüggő alparancs; blood-moon/caravan művelet; intro online játékos | Események/admin menü | A blood-moon/caravan ismeretlen második argumentuma usage hibát ad. | Megváltozott |
| `/events worldboss`<br>Routing alias: `world-boss`, `boss` | Világboss megidézése. | Játékos vagy konzol | icesmp.admin.events | jogosultságfüggő alparancs; blood-moon/caravan művelet; intro online játékos | Események/admin menü | — | Megváltozott |
| `/events invasion`<br>Routing alias: `invazio` | Invázió indítása. | Játékos vagy konzol | icesmp.admin.events | jogosultságfüggő alparancs; blood-moon/caravan művelet; intro online játékos | Események/admin menü | — | Megváltozott |
| `/events ambient`<br>Routing alias: `hangulat` | Hangulatesemény. | Játékos vagy konzol | icesmp.admin.events | jogosultságfüggő alparancs; blood-moon/caravan művelet; intro online játékos | Események/admin menü | — | Megváltozott |
| `/events gathering`<br>Routing alias: `buff`, `gyujtes` | Gyűjtögető buff-ablak. | Játékos vagy konzol | icesmp.admin.events | jogosultságfüggő alparancs; blood-moon/caravan művelet; intro online játékos | Események/admin menü | — | Megváltozott |
| `/events treasure`<br>Routing alias: `kincs` | Kincs elrejtése. | Játékos vagy konzol | icesmp.admin.events | jogosultságfüggő alparancs; blood-moon/caravan művelet; intro online játékos | Események/admin menü | — | Megváltozott |
| `/events wild-hunt`<br>Routing alias: `wildhunt`, `hajsza` | Vad Hajsza indítása. | Játékos vagy konzol | icesmp.admin.events | jogosultságfüggő alparancs; blood-moon/caravan művelet; intro online játékos | Események/admin menü | — | Megváltozott |
| `/events abundance`<br>Routing alias: `boseg` | Bőség-idő indítása. | Játékos vagy konzol | icesmp.admin.events | jogosultságfüggő alparancs; blood-moon/caravan művelet; intro online játékos | Események/admin menü | — | Megváltozott |
| `/events challenge`<br>Routing alias: `kihivas` | Szerverkihívás indítása. | Játékos vagy konzol | icesmp.admin.events | jogosultságfüggő alparancs; blood-moon/caravan művelet; intro online játékos | Események/admin menü | — | Megváltozott |
| `/events escort`<br>Routing alias: `kiseret` | Kíséretesemény indítása. | Játékos vagy konzol | icesmp.admin.events | jogosultságfüggő alparancs; blood-moon/caravan művelet; intro online játékos | Események/admin menü | — | Megváltozott |
| `/events meteor` | Meteor indítása. | Játékos vagy konzol | icesmp.admin.events | jogosultságfüggő alparancs; blood-moon/caravan művelet; intro online játékos | Események/admin menü | — | Megváltozott |
| `/events stranger`<br>Routing alias: `idegen` | Rejtélyes Idegen kézi megidézése. | Játékos vagy konzol | icesmp.admin.events | jogosultságfüggő alparancs; blood-moon/caravan művelet; intro online játékos | Események/admin menü | — | Új |
| `/events corruption`<br>Routing alias: `rontas` | Rontás-góc kézi nyitása. | Játékos vagy konzol | icesmp.admin.events | jogosultságfüggő alparancs; blood-moon/caravan művelet; intro online játékos | Események/admin menü | — | Új |
| `/events archeology`<br>Routing alias: `regeszet` | Régészeti lelőhely kézi nyitása. | Játékos vagy konzol | icesmp.admin.events | jogosultságfüggő alparancs; blood-moon/caravan művelet; intro online játékos | Események/admin menü | — | Új |
| `/events cultists`<br>Routing alias: `kultistak` | Kultista esemény kézi indítása. | Játékos vagy konzol | icesmp.admin.events | jogosultságfüggő alparancs; blood-moon/caravan művelet; intro online játékos | Események/admin menü | — | Új |
| `/events spawnpoint add <world-boss\|escort\|caravan\|cultists\|any> [id]`<br>Routing alias: `spawnpont` | Eseményspawnpont rögzítése itt. | Játékos | icesmp.admin.events | jogosultságfüggő alparancs; blood-moon/caravan művelet; intro online játékos | Események/admin menü | — | Új |
| `/events spawnpoint remove <id>`<br>Routing alias: `spawnpont`, `torol` | Eseményspawnpont törlése. | Játékos vagy konzol | icesmp.admin.events | jogosultságfüggő alparancs; blood-moon/caravan művelet; intro online játékos | Események/admin menü | — | Új |
| `/events spawnpoint list`<br>Routing alias: `spawnpont`, `lista` | Eseményspawnpontok listája. | Játékos vagy konzol | icesmp.admin.events | jogosultságfüggő alparancs; blood-moon/caravan művelet; intro online játékos | Események/admin menü | — | Új |
| `/events intro [online-játékos]` | Intro újrajátszása. | Játékos vagy konzol; konzol csak célponttal | icesmp.admin.events | jogosultságfüggő alparancs; blood-moon/caravan művelet; intro online játékos | Események/admin menü | — | Megváltozott |

### `/exchangeboard`

Aliasok: `/arfolyamtabla`, `/ratesboard`. Deployed státusz: **Megváltozott**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/exchangeboard [place]` | Árfolyamtábla elhelyezése az aktuális helyen. | Játékos | icesmp.admin.exchangeboard | place/remove | — | Ismeretlen argumentum is place ágra esik. | Megváltozott |
| `/exchangeboard remove` | Legközelebbi tábla törlése 6 blokkon belül. | Játékos | icesmp.admin.exchangeboard | place/remove | — | — | Megváltozott |

### `/faction`

Aliasok: `/f`. Deployed státusz: **Megváltozott**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/faction` | Frakcióparancsok súgója. | Játékos vagy konzol | — | jog szerint szűrt alparancsok | Frakció menü | — | Megváltozott |
| `/faction join <frakció>` | Első frakcióválasztás vagy szabályozott váltás. | Játékos | — | frakciók | Frakció menü | — | Megváltozott |
| `/faction leave` | Kilépés a frakcióból a váltási szabályokkal. | Játékos | — | Nincs | Frakció menü | — | Megváltozott |
| `/faction set <játékos> <frakció>` | Online vagy cache-elt játékos adminbesorolása. | Játékos vagy konzol | icesmp.admin.faction | ismert játékos → frakciók | Frakció menü | — | Megváltozott |
| `/faction treasury` | Saját kassza, adminnak minden kassza megjelenítése. | Játékos | — | withdraw, ha király/admin | Frakció menü | — | Megváltozott |
| `/faction treasury withdraw <összeg>` | Kasszakivét a napi keret szerint. | Játékos | király vagy icesmp.admin.faction | withdraw | Frakció menü | — | Megváltozott |
| `/faction donate <összeg>` | Adomány a saját frakciókasszába. | Játékos | — | Nincs | Frakció menü | — | Megváltozott |
| `/faction king` | Királyok és választási állás. | Játékos vagy konzol | — | vote/tax; adminnak set/clear | Frakció menü | — | Megváltozott |
| `/faction king vote <játékos>` | Szavazat leadása. | Játékos | — | online játékosok | Frakció menü | — | Megváltozott |
| `/faction king tax <százalék>` | Saját királyság adókulcsa. | Játékos | király | tax | Frakció menü | — | Megváltozott |
| `/faction king set <frakció> <online-játékos>` | Király adminbeállítása. | Játékos vagy konzol | icesmp.admin.faction | frakció → online játékos | Frakció menü | — | Megváltozott |
| `/faction king clear <frakció>` | Király admin törlése. | Játékos vagy konzol | icesmp.admin.faction | frakció | Frakció menü | — | Megváltozott |
| `/faction raid <célfrakció> [terület]` | Raid meghirdetése. | Játékos | király | frakció → célterületek | Frakció menü | — | Megváltozott |
| `/faction raid join` | Belépés az aktív raidbe. | Játékos | — | join/status | Frakció menü | — | Megváltozott |
| `/faction raid status` | Raidállás. | Játékos | — | join/status | Frakció menü | — | Megváltozott |
| `/faction caravan send <összeg>` | Játékos-karaván indítása a kasszából. | Játékos | király vagy tanácstag | send | Frakció menü | — | Új |
| `/faction war` | Hadiablak állapota. | Játékos vagy konzol | — | adminnak start/stop | Frakció menü | — | Új |
| `/faction war start [perc]` | Hadiablak kézi indítása. | Játékos vagy konzol | icesmp.admin.war | start/stop | Frakció menü | — | Új |
| `/faction war stop` | Hadiablak kézi leállítása. | Játékos vagy konzol | icesmp.admin.war | start/stop | Frakció menü | — | Új |

### `/history`

Aliasok: nincs. Deployed státusz: **Új**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/history <játékos> [oldal]` | Teljes büntetési előzmény lapozva. | Játékos vagy konzol | icesmp.moderation.history | látható online játékosok | Moderációs GUI | — | Új |

### `/hud`

Aliasok: nincs. Deployed státusz: **Új**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/hud` | A saját HUD-szekciók állapotának listázása. | Játékos | — | toggle | — | Argumentum nélkül és ismeretlen első argumentumnál is az állapotlista fut. | Új |
| `/hud toggle <frakcio\|valuta\|kaszt\|eroforras\|esemeny\|csapat\|mind>` | Egy HUD-szekció vagy a teljes HUD ki-/bekapcsolása. | Játékos | — | a hét felsorolt szekció | — | — | Új |

### `/iceitem`

Aliasok: `/icegive`, `/iitem`. Deployed státusz: **Új**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/iceitem` | Jogosultság ellenőrzése után a támogatott itemtípusokat és kötelező argumentumokat mutató usage. | Játékos vagy konzol | icesmp.admin.item | unique/recept/relikvia/tervrajz/erszeny/dev | — | — | Új |
| `/iceitem unique <unique-id> [darab] [online-játékos]` | Regisztrált unique material hiteles példányának kiadása. | Játékos vagy konzol; konzolnál a darab és céljátékos is kötelező | icesmp.admin.item | unique registry-ID → darab → online játékos | — | A darab 1–2304 közé clampelődik; ami nem fér el, a cél lábához esik. | Új |
| `/iceitem recept <recept-id> [darab] [online-játékos]` | A recept eredményének példányonkénti, affix-rollos felépítése és kiadása. | Játékos vagy konzol; konzolnál a darab és céljátékos is kötelező | icesmp.admin.item | recept-ID → darab → online játékos | — | A darab 1–2304 közé clampelődik; buildhiba megszakítja a ciklust. | Új |
| `/iceitem relikvia <relikvia-id> [darab] [online-játékos]` | Regisztrált relikvia hiteles kiadása. | Játékos vagy konzol; konzolnál a darab és céljátékos is kötelező | icesmp.admin.item | relikvia-ID → darab → online játékos | — | A relikviakezelő saját átadási korlátai érvényesülnek. | Új |
| `/iceitem tervrajz <recept-id> [darab] [online-játékos]` | A megadott recepthez tartozó hiteles tervrajz kiadása. | Játékos vagy konzol; konzolnál a darab és céljátékos is kötelező | icesmp.admin.item | blueprint/recept-ID → darab → online játékos | — | Csak a receptkatalógusban létező ID fogadható el. | Új |
| `/iceitem erszeny <pozitív-érték> [darab] [online-játékos]` | Véletlen valutájú kopott erszények kiadása a megadott erszényértékkel. | Játékos vagy konzol; konzolnál a darab és céljátékos is kötelező | icesmp.admin.item | 10/25/50/100 javaslat → darab → online játékos | — | Az érték bármely pozitív long lehet; legfeljebb 64 erszényt ad ki. | Új |
| `/iceitem dev <bingulus-id> [darab] [online-játékos]` | A Csodálatos Bingulus fejlesztői tárgy kiadása. | Játékos vagy konzol; konzolnál a darab és céljátékos is kötelező | icesmp.admin.item | bingulus ID → online játékos | — | Mindig egy darabot ad, kizárólag a konfigurált tulajdonosnak. | Új |

### `/icesmp`

Aliasok: `/ismp`. Deployed státusz: **Megváltozott**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/icesmp` | Jogosultság-szűrt admin súgó. | Játékos vagy konzol | icesmp.admin.reload | reload, config; inspect csak külön inspect joggal | — | — | Megváltozott |
| `/icesmp reload` | Minden config és üzenet újratöltése, a reload hookok futtatásával. | Játékos vagy konzol | icesmp.admin.reload | reload | — | — | Megváltozott |
| `/icesmp config menu` | Kurált élő config-szerkesztő megnyitása. | Játékos | icesmp.admin.reload + icesmp.admin.config | config → menu | Config menü | A gyökérparancs előbb a reload jogot is ellenőrzi. | Új |
| `/icesmp config get <kulcs>` | Az összeolvasztott config aktuális értéke. | Játékos vagy konzol | icesmp.admin.reload + icesmp.admin.config | config-kulcsok | — | A gyökérparancs előbb a reload jogot is ellenőrzi. | Új |
| `/icesmp config set <kulcs> <érték...>` | Ingame override beállítása és azonnali validálása. | Játékos vagy konzol | icesmp.admin.reload + icesmp.admin.config | config-kulcs; ismert logikai értéknél true/false | Config menü | A gyökérparancs előbb a reload jogot is ellenőrzi. | Új |
| `/icesmp config unset <kulcs>` | Ingame override törlése. | Játékos vagy konzol | icesmp.admin.reload + icesmp.admin.config | override/config-kulcsok | Config menü | A gyökérparancs előbb a reload jogot is ellenőrzi. | Új |
| `/icesmp config list` | Az ingame override-ok listázása. | Játékos vagy konzol | icesmp.admin.reload + icesmp.admin.config | config → list | Config menü | A gyökérparancs előbb a reload jogot is ellenőrzi. | Új |
| `/icesmp config find <szövegrészlet>` | Kulcskeresés a teljes összeolvasztott configban. | Játékos vagy konzol | icesmp.admin.reload + icesmp.admin.config | config → find | — | A gyökérparancs előbb a reload jogot is ellenőrzi. | Új |
| `/icesmp inspect <név>` | Online vagy cache-elt offline játékos összesített adminriportja. | Játékos vagy konzol | icesmp.admin.reload + icesmp.admin.inspect | online és helyileg ismert nevek | — | Offline célpontnál csak a UUID-alapú tartós adatok érhetők el. | Új |

### `/invsee`

Aliasok: nincs. Deployed státusz: **Új**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/invsee <online-játékos> [read\|edit] [main\|ender]` | Online inventory vagy ender láda élő olvasása/szerkesztése. | Játékos | read: icesmp.moderation.inventory.read; edit: icesmp.moderation.inventory.edit | látható online játékosok → read/edit → main/ender | Invsee GUI | Csak online és a viewer számára látható célpont; edit módban escrow és reconnect-recovery védi a cserét. A parser minden nem `edit` második értéket readnek, minden nem `ender` harmadik értéket mainnek vesz; további argumentumot figyelmen kívül hagy. | Új |

### `/kem`

Aliasok: `/spy`. Deployed státusz: **Új**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/kem <célfrakció>` | Időzített kémálca. | Játékos | — | frakciók | — | LibsDisguises nélkül nem működik; raid és cooldown kapuzhatja. | Új |

### `/kick`

Aliasok: nincs. Deployed státusz: **Új**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/kick <online-játékos> [ok...]` | Az aktuális online session kirúgása és auditálása. | Játékos vagy konzol | icesmp.moderation.kick | jogosultság szerint látható online játékosok; időzített műveletnél időminták | Moderációs GUI | Csak online célpont. | Új |

### `/komp`

Aliasok: `/ferry`. Deployed státusz: **Új**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/komp` | Konfigurált kompjáratok listája. | Játékos | — | útvonal-ID-k | — | — | Új |
| `/komp <útvonal-id>` | Átkelés a közeli végpontról. | Játékos | — | útvonal-ID-k | — | Harc közben tiltott; hely- és díjfeltételek konfiguráltak. | Új |

### `/kronika`

Aliasok: `/chronicle`. Deployed státusz: **Új**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/kronika` | Utolsó Heti Krónika visszaolvasása. | Játékos vagy konzol | — | Nincs | — | — | Új |

### `/leaderboard`

Aliasok: `/lb`, `/rangsor`, `/top`. Deployed státusz: **Megváltozott**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/leaderboard [level\|wealth\|raidkills]`<br>Routing alias: `vagyon`, `raid`, `kills` | Ranglista GUI a választott kezdőkategóriával. | Játékos | — | level/wealth/raidkills | Ranglista | A vagyon a wealth, a raid és kills a raidkills kategóriára irányít. | Megváltozott |

### `/lore`

Aliasok: `/kodex`. Deployed státusz: **Új**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/lore <téma>`<br>Routing alias: `red`, `piros`, `perinfernicitas`, `blue`, `kek`, `cryghaliris`, `neutral`, `semleges`, `ryanora`, `caldestera`, `o-caldestera`, `ocaldestera`, `gyokerek`, `dark`, `sotet`, `thanaopolis`, `mortengrad`, `kitaszitott`, `eletfa`, `elet-fa`, `karhozat`, `doom`, `olethropyla`, `suttogas`, `whisper`, `torpok`, `melyseg-nepe`, `konyv`, `kronika-lore`, `korszak`, `folyo` | Kánon-kódexlap chatben. | Játékos vagy konzol | — | lang/fagy/menedek/radicora/kitaszitottak/fa/kapu/suttogok/melyseg/korszakok/bokic | — | Aliasfeloldás: red/piros/perinfernicitas→lang; blue/kek/cryghaliris→fagy; neutral/semleges/ryanora/caldestera→menedek; o-caldestera/ocaldestera/gyokerek→radicora; dark/sotet/thanaopolis/mortengrad/kitaszitott→kitaszitottak; eletfa/elet-fa→fa; karhozat/doom/olethropyla→kapu; suttogas/whisper→suttogok; torpok/melyseg-nepe→melyseg; konyv/kronika-lore/korszak→korszakok; folyo→bokic. Ismeretlen téma usage-listát ad. | Új |

### `/market`

Aliasok: `/ah`, `/piac`. Deployed státusz: **Megváltozott**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/market [browse]` | Piactér GUI. | Játékos | — | alparancs; valuta; buyout: minta | Piactér | Csak játékos; ismeretlen alparancs súgót ad. | Megváltozott |
| `/market sell <ár> [valuta]` | Kézben tartott tárgy fix áras listázása. | Játékos | — | alparancs; valuta; buyout: minta | Piactér | Csak játékos; az élő licites aukció nem vonható vissza. | Megváltozott |
| `/market auction <kikiáltási-ár> [óra] [valuta] [buyout:<ár>\|bo:<ár>]` | Aukció indítása. | Játékos | — | alparancs; valuta; buyout: minta | Piactér | Csak játékos; az élő licites aukció nem vonható vissza. | Megváltozott |
| `/market ereklye` | Ereklye/unique börzeszűrő. | Játékos | — | alparancs; valuta; buyout: minta | Piactér | Csak játékos; az élő licites aukció nem vonható vissza. | Új |
| `/market claim` | Megnyert vagy visszajáró tárgyak átvétele. | Játékos | — | alparancs; valuta; buyout: minta | Piactér | Csak játékos; az élő licites aukció nem vonható vissza. | Megváltozott |
| `/market cancel` | Minden visszavonható saját tétel visszavonása. | Játékos | — | alparancs; valuta; buyout: minta | Piactér | Csak játékos; az élő licites aukció nem vonható vissza. | Megváltozott |
| `/market search <szöveg...>` | Szűrt piactér megnyitása. | Játékos | — | alparancs; valuta; buyout: minta | Piactér | Csak játékos; az élő licites aukció nem vonható vissza. | Megváltozott |
| `/market stats` | Aktív tételek és friss forgalom összesítője. | Játékos | — | alparancs; valuta; buyout: minta | — | Csak játékos; az élő licites aukció nem vonható vissza. | Új |

### `/menu`

Aliasok: `/hub`, `/m`. Deployed státusz: **Megváltozott**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/menu` | Központi, szerep- és jogosultságfüggő parancsmenü. | Játékos | — | Nincs | Főmenü | — | Megváltozott |

### `/moderation`

Aliasok: `/mod`. Deployed státusz: **Új**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/moderation [online-játékos]` | Moderációs játékoslista vagy közvetlen célpontműveleti GUI. | Játékos | icesmp.moderation.gui | a viewer számára látható online játékosok | Moderációs GUI | A gombok külön-külön is ellenőrzik a művelet saját jogát. | Új |

### `/msg`

Aliasok: nincs. Deployed státusz: **Új**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/msg <játékos> <üzenet...>` | Privát üzenet küldése. | Játékos vagy konzol | icesmp.message | látható online játékosok | — | A három külön root ugyanarra a szolgáltatásra mutat; nem descriptor-aliasok. | Új |

### `/mute`

Aliasok: nincs. Deployed státusz: **Új**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/mute <játékos> [időtartam\|végleges] [ok...]` | Automatikus, ideiglenes vagy végleges némítás. | Játékos vagy konzol | icesmp.moderation.mute | jogosultság szerint látható online játékosok; időzített műveletnél időminták | Moderációs GUI | Időtartam nélkül — vagy ha a második token nem időformátum — az eszkalációs idő lép életbe. Elfogadott időegység: s, m/p, h, d/n, w vagy suffix nélküli perc, maximum 365 nap; 0/permanent/vegleges/végleges tartós. Nincs aktív /mute list ág. | Új |

### `/npcbind`

Aliasok: `/npckotes`. Deployed státusz: **Megváltozott**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/npcbind` | NPC-kötési használat és elérhető kötéstípusok. | Játékos vagy konzol | icesmp.admin.npc | NPC-nevek → kötéstípus → quest/bolt ID | — | A command kötés a kattintó játékos jogosultságait nem kerüli meg. | Megváltozott |
| `/npcbind list` | Minden NPC-kötés listája. | Játékos vagy konzol | icesmp.admin.npc | NPC-nevek → kötéstípus → quest/bolt ID | — | A command kötés a kattintó játékos jogosultságait nem kerüli meg. | Megváltozott |
| `/npcbind <npc> quest <quest-id>` | Quest-adó kötés. | Játékos vagy konzol | icesmp.admin.npc | NPC-nevek → kötéstípus → quest/bolt ID | — | A command kötés a kattintó játékos jogosultságait nem kerüli meg. | Megváltozott |
| `/npcbind <npc> shop <bolt-id>` | Bolt kötés. | Játékos vagy konzol | icesmp.admin.npc | NPC-nevek → kötéstípus → quest/bolt ID | — | A command kötés a kattintó játékos jogosultságait nem kerüli meg. | Megváltozott |
| `/npcbind <npc> bank` | Bankmenü kötés. | Játékos vagy konzol | icesmp.admin.npc | NPC-nevek → kötéstípus → quest/bolt ID | — | A command kötés a kattintó játékos jogosultságait nem kerüli meg. | Megváltozott |
| `/npcbind <npc> exchange` | Valutaváltó kötés. | Játékos vagy konzol | icesmp.admin.npc | NPC-nevek → kötéstípus → quest/bolt ID | — | A command kötés a kattintó játékos jogosultságait nem kerüli meg. | Megváltozott |
| `/npcbind <npc> faction` | Frakciómenü kötés. | Játékos vagy konzol | icesmp.admin.npc | NPC-nevek → kötéstípus → quest/bolt ID | — | A command kötés a kattintó játékos jogosultságait nem kerüli meg. | Új |
| `/npcbind <npc> command <parancs...>` | A kattintó saját jogával futó parancskötés. | Játékos vagy konzol | icesmp.admin.npc | NPC-nevek → kötéstípus → quest/bolt ID | — | A command kötés a kattintó játékos jogosultságait nem kerüli meg. | Új |
| `/npcbind <npc> clear` | Kötés törlése. | Játékos vagy konzol | icesmp.admin.npc | NPC-nevek → kötéstípus → quest/bolt ID | — | A command kötés a kattintó játékos jogosultságait nem kerüli meg. | Megváltozott |

### `/offlinetp`

Aliasok: nincs. Deployed státusz: **Új**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/offlinetp <játékos>` | Teleport a tartósan mentett utolsó kijelentkezési helyre. | Játékos | icesmp.moderation.offlinetp | Nincs | Moderációs GUI | A mentett világ UUID-jének és nevének is illeszkednie kell; nem tölt világot szinkron. | Új |

### `/parbaj`

Aliasok: `/duel`. Deployed státusz: **Új**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/parbaj` | Becsület-párbaj súgója. | Játékos | — | kihiv/elfogad/elutasit | Bounty menü | — | Új |
| `/parbaj kihiv <online-játékos>`<br>Routing alias: `challenge` | Becsület-párbaj kihívás. | Játékos | — | kihiv/elfogad/elutasit; kihívásnál online nevek | Bounty menü | — | Új |
| `/parbaj elfogad`<br>Routing alias: `accept` | Kihívás elfogadása. | Játékos | — | kihiv/elfogad/elutasit; kihívásnál online nevek | Bounty menü | — | Új |
| `/parbaj elutasit`<br>Routing alias: `decline` | Kihívás elutasítása. | Játékos | — | kihiv/elfogad/elutasit; kihívásnál online nevek | Bounty menü | — | Új |

### `/parkour`

Aliasok: `/palya`, `/trial`. Deployed státusz: **Megváltozott**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/parkour [list]` | Pályák listája. | Játékos | — | list/start; adminnak setstart/setfinish/remove | — | Az ismeretlen első argumentum is a pályalistára esik vissza. | Megváltozott |
| `/parkour start <pálya-id>` | Időmérős futás indítása. | Játékos | — | pálya-ID-k | — | — | Megváltozott |
| `/parkour setstart <id> [név]` | Startpont beállítása. | Játékos | icesmp.admin.parkour | pálya-ID-k | — | — | Megváltozott |
| `/parkour setfinish <id> [sugár] [jutalom]` | Célpont, elérési sugár és jutalom beállítása. | Játékos | icesmp.admin.parkour | pálya-ID-k | — | — | Megváltozott |
| `/parkour remove <id>` | Pálya törlése. | Játékos | icesmp.admin.parkour | pálya-ID-k | — | — | Megváltozott |

### `/party`

Aliasok: `/p`, `/parti`. Deployed státusz: **Megváltozott**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/party` | Csapattaglista, ha van csapatod; különben súgó. | Játékos | — | alparancs; online játékos/tag a pozíció szerint | Party menü | — | Megváltozott |
| `/party invite <online-játékos>` | Meghívás. | Játékos | — | alparancs; online játékos/tag a pozíció szerint | Party menü | — | Megváltozott |
| `/party accept` | Meghívás elfogadása. | Játékos | — | alparancs; online játékos/tag a pozíció szerint | Party menü | — | Megváltozott |
| `/party decline` | Meghívás elutasítása. | Játékos | — | alparancs; online játékos/tag a pozíció szerint | Party menü | — | Megváltozott |
| `/party leave` | Kilépés. | Játékos | — | alparancs; online játékos/tag a pozíció szerint | Party menü | — | Megváltozott |
| `/party disband` | Csapat feloszlatása (vezető). | Játékos | — | alparancs; online játékos/tag a pozíció szerint | Party menü | — | Megváltozott |
| `/party kick <tag>` | Tag kirúgása (vezető). | Játékos | — | alparancs; online játékos/tag a pozíció szerint | Party menü | — | Megváltozott |
| `/party promote <tag>` | Vezetés átadása. | Játékos | — | alparancs; online játékos/tag a pozíció szerint | Party menü | — | Megváltozott |
| `/party list` | Taglista. | Játékos | — | alparancs; online játékos/tag a pozíció szerint | Party menü | — | Megváltozott |
| `/party chat <üzenet...>` | Csapatchat. | Játékos | — | alparancs; online játékos/tag a pozíció szerint | Party menü | — | Megváltozott |
| `/party c <üzenet...>` | Csapatchat rövid alparanccsal. | Játékos | — | alparancs; online játékos/tag a pozíció szerint | Party menü | — | Megváltozott |
| `/party <üzenet...>` | Minden fel nem ismert első szó csapatchat-üzenetként fut; így az aliasos /p <üzenet> gyors chat. | Játékos | — | alparancs; online játékos/tag a pozíció szerint | Party menü | — | Megváltozott |
| `/party help` | Súgó. | Játékos | — | alparancs; online játékos/tag a pozíció szerint | Party menü | — | Megváltozott |

### `/pet`

Aliasok: `/companion`, `/tars`. Deployed státusz: **Megváltozott**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/pet [menu]` | Társvezérlő GUI. | Játékos | — | menu/item/summon/dismiss/name/stance/info | Társ GUI | — | Megváltozott |
| `/pet info` | Társ neve, szintje és XP-je. | Játékos | — | állásmódnál aktiv/passziv/marad | Társ GUI | Ismeretlen alparancs az info nézetre esik vissza. | Megváltozott |
| `/pet item` | Befogóeszköz kérése az engedélyezett kasztnak. | Játékos | — | állásmódnál aktiv/passziv/marad | Társ GUI | Ismeretlen alparancs az info nézetre esik vissza. | Megváltozott |
| `/pet summon` | Társ idézése. | Játékos | — | állásmódnál aktiv/passziv/marad | Társ GUI | Ismeretlen alparancs az info nézetre esik vissza. | Megváltozott |
| `/pet dismiss` | Aktív társ elküldése. | Játékos | — | állásmódnál aktiv/passziv/marad | Társ GUI | Ismeretlen alparancs az info nézetre esik vissza. | Megváltozott |
| `/pet name <név>` | Társ átnevezése. | Játékos | — | állásmódnál aktiv/passziv/marad | Társ GUI | Ismeretlen alparancs az info nézetre esik vissza. | Megváltozott |
| `/pet stance <aktiv\|passziv\|marad>`<br>Routing alias: `active`, `passive`, `stay` | Támadó, passzív vagy helyben maradó állásmód. | Játékos | — | állásmódnál aktiv/passziv/marad | Társ GUI | Az active/passive/stay értékek is elfogadottak. | Új |

### `/profession`

Aliasok: `/prof`, `/szakma`. Deployed státusz: **Megváltozott**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/profession` | Szakmaparancsok súgója. | Játékos vagy konzol | — | jogosultság szerint szűrt alparancsok | — | — | Megváltozott |
| `/profession join <szakma>` | Elsődleges gyűjtögető/készítő szakma választása. | Játékos | — | elsődleges szakma-ID-k | Karakterlap / szakmaválasztó / receptkönyv | — | Megváltozott |
| `/profession info` | Saját szakmák és szintek. | Játékos | — | Nincs | Karakterlap / szakmaválasztó / receptkönyv | — | Megváltozott |
| `/profession list` | Szakmák kategóriánként. | Játékos vagy konzol | — | Nincs | Karakterlap / szakmaválasztó / receptkönyv | — | Megváltozott |
| `/profession recipes`<br>Routing alias: `receptek`, `book` | Tanult/zárolt receptkönyv. | Játékos | — | Nincs | Karakterlap / szakmaválasztó / receptkönyv | — | Megváltozott |
| `/profession blueprint <online-játékos> <recept-id>`<br>Routing alias: `tervrajz` | Tervrajz adminátadása. | Játékos vagy konzol | icesmp.admin.profession | online játékos → blueprint recept | Karakterlap / szakmaválasztó / receptkönyv | — | Megváltozott |
| `/profession set <online-játékos> <szakma>` | Szakma adminbeállítása. | Játékos vagy konzol | icesmp.admin.profession | online játékos → szakma | Karakterlap / szakmaválasztó / receptkönyv | — | Megváltozott |
| `/profession clear <online-játékos> <gathering\|crafting>` | Elsődleges szakmaslot törlése. | Játékos vagy konzol | icesmp.admin.profession | online játékos → slot | Karakterlap / szakmaválasztó / receptkönyv | — | Megváltozott |
| `/profession addxp <online-játékos> <szakma> <mennyiség>` | Szakma-XP hozzáadása. | Játékos vagy konzol | icesmp.admin.profession | online játékos → szakma | Karakterlap / szakmaválasztó / receptkönyv | — | Megváltozott |

### `/profile`

Aliasok: `/char`, `/karakter`, `/status`. Deployed státusz: **Változatlan**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/profile` | Karakterlap megnyitása. | Játékos | — | Nincs | Karakterlap | — | Változatlan |

### `/punishments`

Aliasok: nincs. Deployed státusz: **Új**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/punishments [játékos]` | Minden aktív büntetés vagy egy ismert célpont aktív tételei. | Játékos vagy konzol | icesmp.moderation.history | látható online játékosok | Moderációs GUI | — | Új |

### `/quest`

Aliasok: `/kuldetes`, `/quests`. Deployed státusz: **Megváltozott**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/quest` | Játékos- és jogosultságfüggő quest-súgó. | Játékos vagy konzol | — | alparancs/quest-ID/objektívatípus/mező/online játékos a pozíció szerint | — | A builder csak admin-készítette questet szerkeszt; config-questet nem. | Megváltozott |
| `/quest log`<br>Routing alias: `gui`, `naplo`, `napló` | Küldetésnapló GUI. | Játékos | — | alparancs/quest-ID/objektívatípus/mező/online játékos a pozíció szerint | Küldetésnapló | A builder csak admin-készítette questet szerkeszt; config-questet nem. | Megváltozott |
| `/quest list` | Felvehető küldetések. | Játékos vagy konzol | — | alparancs/quest-ID/objektívatípus/mező/online játékos a pozíció szerint | — | A builder csak admin-készítette questet szerkeszt; config-questet nem. | Megváltozott |
| `/quest info` | Aktív küldetések és haladás. | Játékos | — | alparancs/quest-ID/objektívatípus/mező/online játékos a pozíció szerint | Küldetésnapló | A builder csak admin-készítette questet szerkeszt; config-questet nem. | Megváltozott |
| `/quest accept <quest-id>` | Küldetés felvétele. | Játékos | — | alparancs/quest-ID/objektívatípus/mező/online játékos a pozíció szerint | Küldetésnapló | A builder csak admin-készítette questet szerkeszt; config-questet nem. | Megváltozott |
| `/quest talk <npc-név>` | NPC-plugin nélküli beszélgetés/átadás tartalékút. | Játékos | — | alparancs/quest-ID/objektívatípus/mező/online játékos a pozíció szerint | — | A builder csak admin-készítette questet szerkeszt; config-questet nem. | Új |
| `/quest abandon <quest-id>` | Aktív küldetés eldobása. | Játékos | — | alparancs/quest-ID/objektívatípus/mező/online játékos a pozíció szerint | Küldetésnapló | A builder csak admin-készítette questet szerkeszt; config-questet nem. | Megváltozott |
| `/quest complete <online-játékos> <quest-id>` | Quest adminlezárása. | Játékos vagy konzol | icesmp.admin.quest | alparancs/quest-ID/objektívatípus/mező/online játékos a pozíció szerint | — | A builder csak admin-készítette questet szerkeszt; config-questet nem. | Megváltozott |
| `/quest admin create <id> <objektíva> <darab> <név...>` | Custom quest létrehozása. | Játékos vagy konzol | icesmp.admin.quest | alparancs/quest-ID/objektívatípus/mező/online játékos a pozíció szerint | Quest builder | A builder csak admin-készítette questet szerkeszt; config-questet nem. | Megváltozott |
| `/quest admin addobjective <id> <objektíva> <darab> [leírás...]` | Objektíva hozzáadása. | Játékos vagy konzol | icesmp.admin.quest | alparancs/quest-ID/objektívatípus/mező/online játékos a pozíció szerint | Quest builder | A builder csak admin-készítette questet szerkeszt; config-questet nem. | Megváltozott |
| `/quest admin set <id> <mező> <érték...>` | Szerkeszthető questmező beállítása. | Játékos vagy konzol | icesmp.admin.quest | alparancs/quest-ID/objektívatípus/mező/online játékos a pozíció szerint | Quest builder | A builder csak admin-készítette questet szerkeszt; config-questet nem. | Megváltozott |
| `/quest admin delete <id>` | Custom quest törlése. | Játékos vagy konzol | icesmp.admin.quest | alparancs/quest-ID/objektívatípus/mező/online játékos a pozíció szerint | Quest builder | A builder csak admin-készítette questet szerkeszt; config-questet nem. | Megváltozott |
| `/quest admin info <id>` | Custom quest részletei. | Játékos vagy konzol | icesmp.admin.quest | alparancs/quest-ID/objektívatípus/mező/online játékos a pozíció szerint | Quest builder | A builder csak admin-készítette questet szerkeszt; config-questet nem. | Megváltozott |
| `/quest admin list` | Custom questek listája. | Játékos vagy konzol | icesmp.admin.quest | alparancs/quest-ID/objektívatípus/mező/online játékos a pozíció szerint | Quest builder | A builder csak admin-készítette questet szerkeszt; config-questet nem. | Megváltozott |
| `/quest admin builder <id>` | Létrehozó vagy szerkesztő GUI. | Játékos | icesmp.admin.quest | alparancs/quest-ID/objektívatípus/mező/online játékos a pozíció szerint | Quest builder | A builder csak admin-készítette questet szerkeszt; config-questet nem. | Megváltozott |

### `/relic`

Aliasok: `/relics`, `/relikvia`. Deployed státusz: **Megváltozott**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/relic` | Relikviaparancsok súgója. | Játékos vagy konzol | — | list/give | — | — | Megváltozott |
| `/relic list` | Regisztrált relikvia-ID-k. | Játékos vagy konzol | — | list/give | Relikvia menü | — | Megváltozott |
| `/relic give <online-játékos> <relikvia-id> [mennyiség]` | Relikvia adminátadása. | Játékos vagy konzol | icesmp.admin.relic | online játékos → relikvia ID | — | — | Megváltozott |

### `/reply`

Aliasok: `/r`. Deployed státusz: **Új**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/reply <üzenet...>`<br>Routing alias: `r` | Válasz az utolsó ténylegesen kézbesített privát beszélgetésre. | Játékos vagy konzol | icesmp.message | Nincs | — | Nincs célpontjavaslat; a state csak sikeres kézbesítés után frissül. | Új |

### `/report`

Aliasok: `/bejelent`. Deployed státusz: **Új**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/report <név> <ok...>` | Játékosbejelentés rögzítése. | Játékos | — | online játékosok | Moderációs GUI: reports csak adminnak | Az indok legalább három szó; ugyanaz a játékos legfeljebb percenként egyszer jelenthet. | Új |

### `/reports`

Aliasok: nincs. Deployed státusz: **Új**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/reports` | Nyitott bejelentések listája. | Játékos vagy konzol | icesmp.admin.moderation | resolve, all | Moderációs GUI | — | Új |
| `/reports all` | Az utolsó legfeljebb húsz bejelentés, lezártakkal. | Játékos vagy konzol | icesmp.admin.moderation | all | Moderációs GUI | — | Új |
| `/reports resolve <id>` | Nyitott bejelentés lezárása. | Játékos vagy konzol | icesmp.admin.moderation | nyitott report ID-k | Moderációs GUI | — | Új |

### `/sinner`

Aliasok: nincs. Deployed státusz: **Megváltozott**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/sinner <online-játékos> set` | A célpont bűnpontjának legalább a bűnös küszöbre állítása. | Játékos vagy konzol | icesmp.admin.sinner | online játékos → set/clear/add/status | — | A tényleges state-művelet a cél saját schedulerén fut. | Megváltozott |
| `/sinner <online-játékos> clear` | A célpont bűnpontjainak törlése. | Játékos vagy konzol | icesmp.admin.sinner | online játékos → set/clear/add/status | — | A tényleges state-művelet a cél saját schedulerén fut. | Megváltozott |
| `/sinner <online-játékos> add` | Egy bűnpont hozzáadása a célponthoz. | Játékos vagy konzol | icesmp.admin.sinner | online játékos → set/clear/add/status | — | A tényleges state-művelet a cél saját schedulerén fut. | Megváltozott |
| `/sinner <online-játékos> status` | A célpont aktuális bűnállapotának lekérdezése. | Játékos vagy konzol | icesmp.admin.sinner | online játékos → set/clear/add/status | — | A tényleges state-művelet a cél saját schedulerén fut. | Megváltozott |

### `/sit`

Aliasok: nincs. Deployed státusz: **Új**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/sit` | Leülés a támogatott blokkra, illetve ülés közben felállás. | Játékos | icesmp.sit | fel | — | Csak ülés; nincs lay, crawl, stacking vagy player/NPC sitting. | Új |
| `/sit fel` | Kifejezett felállás. | Játékos | icesmp.sit | fel | — | — | Új |

### `/socialspy`

Aliasok: nincs. Deployed státusz: **Új**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/socialspy` | A saját tartós SocialSpy állapot ki-/bekapcsolása. | Játékos | icesmp.moderation.socialspy | Nincs | — | — | Új |

### `/soulforge`

Aliasok: `/lelekkovacs`. Deployed státusz: **Új**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/soulforge` | Nekromanta lélekkovács állapota. | Játékos | — | fejleszt | — | — | Új |
| `/soulforge fejleszt <elet\|sebzes\|letszam>`<br>Routing alias: `élet`, `sebzés`, `létszám` | Minionfejlesztési ág rangemelése. | Játékos | — | elet/sebzes/letszam | — | — | Új |

### `/souls`

Aliasok: `/lelek`, `/soul`. Deployed státusz: **Megváltozott**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/souls` | Saját lélekszilánk-egyenleg. | Játékos | — | champion | — | — | Megváltozott |
| `/souls champion` | Nekromanta bajnokidézés. | Játékos | — | champion | — | — | Megváltozott |

### `/spec`

Aliasok: `/specializacio`, `/specialization`. Deployed státusz: **Megváltozott**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/spec` | Specializációs súgó. | Játékos vagy konzol | — | list/choose/info/respec; adminnak reset | — | — | Megváltozott |
| `/spec list` | Elérhető specializációk. | Játékos | — | list/choose/info/respec; adminnak reset | Specializáció GUI | — | Megváltozott |
| `/spec choose <specializáció-id>` | Specializáció kiválasztása. | Játékos | — | választható ID-k | Specializáció GUI | — | Megváltozott |
| `/spec info` | Saját specializációállapot. | Játékos | — | Nincs | Specializáció GUI | — | Megváltozott |
| `/spec respec <class\|profession>` | Saját specializáció visszaváltása. | Játékos | — | class/profession | Specializáció GUI | — | Megváltozott |
| `/spec reset <online-játékos>` | Specializáció adminresetje. | Játékos vagy konzol | icesmp.admin.spec | online játékos | Specializáció GUI | — | Megváltozott |

### `/spell`

Aliasok: `/mastery`, `/mesterseg`, `/spells`. Deployed státusz: **Megváltozott**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/spell [info]` | Spell-mesterség állapota. | Játékos | — | info/upgrade | — | Minden, nem pontosan `upgrade <spell-id>` alakú hívás az információs nézetet adja. | Megváltozott |
| `/spell upgrade <spell-id>` | Spell-mesterség fejlesztése valutáért. | Játékos | — | feloldott spell-ID-k | — | — | Megváltozott |

### `/spellbook`

Aliasok: `/konyv`, `/sb`, `/varazskonyv`. Deployed státusz: **Megváltozott**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/spellbook` | Lapozható és szűrhető varázskönyv. | Játékos | — | Nincs | Varázskönyv | — | Megváltozott |

### `/stats`

Aliasok: nincs. Deployed státusz: **Új**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/stats [név]` | Saját vagy ismert játékos statisztikaprofilja. | Játékos; konzol csak névvel | — | online játékosok | — | — | Új |

### `/suttogas`

Aliasok: `/sutt`. Deployed státusz: **Új**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/suttogas <üzenet...>` | Suttogó/Kitaszított titkos csatorna. | Játékos | — | vád | — | — | Új |
| `/suttogas vád <online-játékos>`<br>Routing alias: `vad`, `accuse` | Tanú-tokenes, eredményt el nem áruló vád. | Játékos | — | vád → online játékos | — | — | Új |

### `/szakmacel`

Aliasok: `/weeklygoal`. Deployed státusz: **Új**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/szakmacel` | Szakma heti közös céljának állása. | Játékos | — | Nincs | — | — | Új |

### `/talent`

Aliasok: `/talentfa`, `/talents`. Deployed státusz: **Megváltozott**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/talent [list]` | Saját talentállapot. | Játékos | — | list/spend | Talent-fa | — | Megváltozott |
| `/talent spend <class\|profession> <talent-id>` | Talentpont elköltése. | Játékos | — | class/profession → elérhető talentek | Talent-fa | — | Megváltozott |

### `/tanacs`

Aliasok: `/council`. Deployed státusz: **Új**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/tanacs [info]` | Vének Tanácsa heti állása. | Játékos | — | info/szavaz/vasarhet; szavazásnál online játékos | — | Az ismeretlen első argumentum is az információs nézetre esik vissza. | Új |
| `/tanacs szavaz <online-játékos>` | Heti tanácsi szavazat. | Játékos | — | info/szavaz/vasarhet; szavazásnál online játékos | — | — | Új |
| `/tanacs vasarhet` | Tanácstagi Vásár-hét indítása. | Játékos | — | info/szavaz/vasarhet; szavazásnál online játékos | — | — | Új |

### `/tell`

Aliasok: nincs. Deployed státusz: **Új**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/tell <játékos> <üzenet...>` | Privát üzenet küldése. | Játékos vagy konzol | icesmp.message | látható online játékosok | — | A három külön root ugyanarra a szolgáltatásra mutat; nem descriptor-aliasok. | Új |

### `/tempban`

Aliasok: nincs. Deployed státusz: **Új**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/tempban <játékos> <időtartam> [ok...]` | Ideiglenes kitiltás. | Játékos vagy konzol | icesmp.moderation.ban | jogosultság szerint látható online játékosok; időzített műveletnél időminták | Moderációs GUI | Pozitív időtartam kötelező; s, m/p, h, d/n, w vagy suffix nélküli perc, maximum 365 nap. A tabban látható `végleges` itt érvénytelen, mert ezt a route 0 időtartamként elutasítja. | Új |

### `/territory`

Aliasok: `/terulet`. Deployed státusz: **Megváltozott**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/territory` | Territórium-admin súgó. | Játékos vagy konzol | icesmp.admin.territory | alparancs, típus/frakció/zóna-ID a pozíció szerint | — | Világot módosító ágaknál játékos és aktuális pozíció kell. | Megváltozott |
| `/territory pos`<br>Routing alias: `point` | Poligonpont felvétele az aktuális pozíción. | Játékos | icesmp.admin.territory | alparancs, típus/frakció/zóna-ID a pozíció szerint | — | Világot módosító ágaknál játékos és aktuális pozíció kell. | Megváltozott |
| `/territory wand`<br>Routing alias: `palca` | Territórium-kijelölő pálca. | Játékos | icesmp.admin.territory | alparancs, típus/frakció/zóna-ID a pozíció szerint | — | Világot módosító ágaknál játékos és aktuális pozíció kell. | Új |
| `/territory undo` | Utolsó pufferpont visszavonása. | Játékos | icesmp.admin.territory | alparancs, típus/frakció/zóna-ID a pozíció szerint | — | Világot módosító ágaknál játékos és aktuális pozíció kell. | Megváltozott |
| `/territory clearpoints`<br>Routing alias: `clear` | Minden pufferpont törlése. | Játékos | icesmp.admin.territory | alparancs, típus/frakció/zóna-ID a pozíció szerint | — | Világot módosító ágaknál játékos és aktuális pozíció kell. | Megváltozott |
| `/territory points` | Pufferpontok listája. | Játékos | icesmp.admin.territory | alparancs, típus/frakció/zóna-ID a pozíció szerint | — | Világot módosító ágaknál játékos és aktuális pozíció kell. | Megváltozott |
| `/territory create <típus> <frakció> <id> [név...]` | Poligonzóna létrehozása. | Játékos | icesmp.admin.territory | alparancs, típus/frakció/zóna-ID a pozíció szerint | — | Világot módosító ágaknál játékos és aktuális pozíció kell. | Megváltozott |
| `/territory circle <típus> <frakció> <id> <sugár> [név...]` | Körzóna létrehozása. | Játékos | icesmp.admin.territory | alparancs, típus/frakció/zóna-ID a pozíció szerint | — | Világot módosító ágaknál játékos és aktuális pozíció kell. | Megváltozott |
| `/territory setcapital <frakció> <sugár> [név...]` | Főváros-körzóna létrehozása. | Játékos | icesmp.admin.territory | alparancs, típus/frakció/zóna-ID a pozíció szerint | — | Világot módosító ágaknál játékos és aktuális pozíció kell. | Megváltozott |
| `/territory setspawn <frakció>` | Királyságspawn beállítása. | Játékos | icesmp.admin.territory | alparancs, típus/frakció/zóna-ID a pozíció szerint | — | Világot módosító ágaknál játékos és aktuális pozíció kell. | Új |
| `/territory rename <id> <új név...>` | Zóna átnevezése. | Játékos vagy konzol | icesmp.admin.territory | alparancs, típus/frakció/zóna-ID a pozíció szerint | — | Világot módosító ágaknál játékos és aktuális pozíció kell. | Megváltozott |
| `/territory resize <id> <sugár>` | Körzóna sugarának módosítása. | Játékos vagy konzol | icesmp.admin.territory | alparancs, típus/frakció/zóna-ID a pozíció szerint | — | Világot módosító ágaknál játékos és aktuális pozíció kell. | Megváltozott |
| `/territory settype <id> <típus>` | Zónatípus módosítása. | Játékos vagy konzol | icesmp.admin.territory | alparancs, típus/frakció/zóna-ID a pozíció szerint | — | Világot módosító ágaknál játékos és aktuális pozíció kell. | Megváltozott |
| `/territory sety <id> <minY\|~> <maxY\|~>` | Magassági sáv módosítása. | Játékos vagy konzol | icesmp.admin.territory | alparancs, típus/frakció/zóna-ID a pozíció szerint | — | Világot módosító ágaknál játékos és aktuális pozíció kell. | Megváltozott |
| `/territory remove <id>` | Zóna törlése. | Játékos vagy konzol | icesmp.admin.territory | alparancs, típus/frakció/zóna-ID a pozíció szerint | — | Világot módosító ágaknál játékos és aktuális pozíció kell. | Megváltozott |
| `/territory list` | Zónák listája. | Játékos vagy konzol | icesmp.admin.territory | alparancs, típus/frakció/zóna-ID a pozíció szerint | — | Világot módosító ágaknál játékos és aktuális pozíció kell. | Megváltozott |
| `/territory info` | Aktuális pozíció zónája. | Játékos | icesmp.admin.territory | alparancs, típus/frakció/zóna-ID a pozíció szerint | — | Világot módosító ágaknál játékos és aktuális pozíció kell. | Megváltozott |
| `/territory show [id]` | Puffer/aktuális/megadott határ kirajzolása. | Játékos | icesmp.admin.territory | alparancs, típus/frakció/zóna-ID a pozíció szerint | — | Világot módosító ágaknál játékos és aktuális pozíció kell. | Megváltozott |
| `/territory tp <id>`<br>Routing alias: `teleport` | Teleport a zónaközépponthoz. | Játékos | icesmp.admin.territory | alparancs, típus/frakció/zóna-ID a pozíció szerint | — | Világot módosító ágaknál játékos és aktuális pozíció kell. | Megváltozott |
| `/territory dungeonchest [tábla]` | Nézett tároló dungeon loot-táblához kötése/törlése. | Játékos | icesmp.admin.territory | alparancs, típus/frakció/zóna-ID a pozíció szerint | — | Világot módosító ágaknál játékos és aktuális pozíció kell. | Új |
| `/territory dungeonboss <zóna-id> [tábla]` | Dungeon boss spawn rögzítése. | Játékos | icesmp.admin.territory | alparancs, típus/frakció/zóna-ID a pozíció szerint | — | Világot módosító ágaknál játékos és aktuális pozíció kell. | Új |
| `/territory dungeonboss clear <zóna-id>` | Dungeon boss spawn törlése. | Játékos | icesmp.admin.territory | alparancs, típus/frakció/zóna-ID a pozíció szerint | — | Világot módosító ágaknál játékos és aktuális pozíció kell. | Új |

### `/unban`

Aliasok: nincs. Deployed státusz: **Új**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/unban <játékos> [ok...]` | Aktív kitiltás feloldása. | Játékos vagy konzol | icesmp.moderation.ban | jogosultság szerint látható online játékosok | Moderációs GUI | — | Új |

### `/unmute`

Aliasok: nincs. Deployed státusz: **Új**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/unmute <játékos> [ok...]` | Aktív némítás feloldása. | Játékos vagy konzol | icesmp.moderation.mute | jogosultság szerint látható online játékosok | Moderációs GUI | — | Új |

### `/vanish`

Aliasok: `/v`. Deployed státusz: **Új**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/vanish [online-játékos]` | Saját vagy célpont tartós vanish állapotának váltása. | Játékos vagy konzol; konzol csak célponttal | icesmp.moderation.vanish | jogosultság szerint látható online játékosok | Moderációs GUI | — | Új |

### `/w`

Aliasok: nincs. Deployed státusz: **Új**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/w <játékos> <üzenet...>` | Privát üzenet küldése. | Játékos vagy konzol | icesmp.message | látható online játékosok | — | A három külön root ugyanarra a szolgáltatásra mutat; nem descriptor-aliasok. | Új |

### `/warn`

Aliasok: nincs. Deployed státusz: **Új**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/warn <játékos> [ok...]` | Figyelmeztetés kiadása. | Játékos vagy konzol | icesmp.moderation.warn | jogosultság szerint látható online játékosok; időzített műveletnél időminták | Moderációs GUI | Offline, de ismert célpont is használható. | Új |

## Bizonyított eltérések a régi leírásoktól

- A `/class givecatalyst` aktív route-ja `icesmp.admin.job` jogot kér; nem játékos-önkiszolgáló út.
- Az aktív `/mute` a közös moderációs action handlert használja. A forrásban maradt régi `MuteCommand` nincs bekötve, ezért `/mute list` nincs.
- A `/bank withdraw` parser a `dark` értéket is elfogadja, de a beépített usage és tab csak `red`, `blue`, `neutral` értéket mutat.
- `/events chronicle` nincs a tényleges dispatchben, hiába utal rá egy forráskomment.
- `/msg`, `/tell` és `/w` három külön root regisztráció ugyanazzal a handlerrel; csak `/reply` valódi root aliasa az `/r`.

## Forrásbizonyíték

- regisztráció: `src/main/java/hu/taliann/icesmp/core/IceSMPCore.java`;
- route-ok: `src/main/java/hu/taliann/icesmp/commands/` és alcsomagjai;
- deployed összevetés: a csatolt JAR bytecode-inventoryja (`commands.json`, `commands.csv`) és osztályhash-ek;
- gépi, soronként feldolgozható leltár: `docs/releases/evidence/source_interface_inventory.json` (a PR-ba rendezéskor a végleges evidence-könyvtárba másolandó).

## Gépileg ellenőrzött dokumentációs azonosítók

Az alábbi egyedi jelölők a dokumentációs inventory teljes root-, route- és aliaslefedettségét teszik géppel ellenőrizhetővé.

<!-- icesmp-doc-id: command.achievements -->
<!-- icesmp-doc-id: alias.achievements.ach -->
<!-- icesmp-doc-id: alias.achievements.eleresek -->
<!-- icesmp-doc-id: command.adomany -->
<!-- icesmp-doc-id: alias.adomany.adomanylada -->
<!-- icesmp-doc-id: alias.adomany.donate -->
<!-- icesmp-doc-id: command.afk -->
<!-- icesmp-doc-id: command.ban -->
<!-- icesmp-doc-id: command.bank -->
<!-- icesmp-doc-id: alias.bank.vault -->
<!-- icesmp-doc-id: alias.bank.wallet -->
<!-- icesmp-doc-id: command.bestiarium -->
<!-- icesmp-doc-id: alias.bestiarium.bestiary -->
<!-- icesmp-doc-id: alias.bestiarium.lajstrom -->
<!-- icesmp-doc-id: command.bounty -->
<!-- icesmp-doc-id: alias.bounty.fejvadasz -->
<!-- icesmp-doc-id: alias.bounty.korozes -->
<!-- icesmp-doc-id: command.ceh -->
<!-- icesmp-doc-id: alias.ceh.gild -->
<!-- icesmp-doc-id: alias.ceh.guild -->
<!-- icesmp-doc-id: command.claim -->
<!-- icesmp-doc-id: alias.claim.birtok -->
<!-- icesmp-doc-id: command.class -->
<!-- icesmp-doc-id: alias.class.job -->
<!-- icesmp-doc-id: alias.class.kaszt -->
<!-- icesmp-doc-id: command.crate -->
<!-- icesmp-doc-id: alias.crate.crates -->
<!-- icesmp-doc-id: alias.crate.ladak -->
<!-- icesmp-doc-id: command.currency -->
<!-- icesmp-doc-id: alias.currency.eco -->
<!-- icesmp-doc-id: alias.currency.money -->
<!-- icesmp-doc-id: command.daily -->
<!-- icesmp-doc-id: alias.daily.napi -->
<!-- icesmp-doc-id: command.emlek -->
<!-- icesmp-doc-id: alias.emlek.emlekek -->
<!-- icesmp-doc-id: alias.emlek.memory -->
<!-- icesmp-doc-id: command.events -->
<!-- icesmp-doc-id: alias.events.esemeny -->
<!-- icesmp-doc-id: alias.events.event -->
<!-- icesmp-doc-id: command.exchangeboard -->
<!-- icesmp-doc-id: alias.exchangeboard.arfolyamtabla -->
<!-- icesmp-doc-id: alias.exchangeboard.ratesboard -->
<!-- icesmp-doc-id: command.faction -->
<!-- icesmp-doc-id: alias.faction.f -->
<!-- icesmp-doc-id: command.history -->
<!-- icesmp-doc-id: command.hud -->
<!-- icesmp-doc-id: command.iceitem -->
<!-- icesmp-doc-id: alias.iceitem.icegive -->
<!-- icesmp-doc-id: alias.iceitem.iitem -->
<!-- icesmp-doc-id: command.icesmp -->
<!-- icesmp-doc-id: alias.icesmp.ismp -->
<!-- icesmp-doc-id: command.invsee -->
<!-- icesmp-doc-id: command.kem -->
<!-- icesmp-doc-id: alias.kem.spy -->
<!-- icesmp-doc-id: command.kick -->
<!-- icesmp-doc-id: command.komp -->
<!-- icesmp-doc-id: alias.komp.ferry -->
<!-- icesmp-doc-id: command.kronika -->
<!-- icesmp-doc-id: alias.kronika.chronicle -->
<!-- icesmp-doc-id: command.leaderboard -->
<!-- icesmp-doc-id: alias.leaderboard.lb -->
<!-- icesmp-doc-id: alias.leaderboard.rangsor -->
<!-- icesmp-doc-id: alias.leaderboard.top -->
<!-- icesmp-doc-id: command.lore -->
<!-- icesmp-doc-id: alias.lore.kodex -->
<!-- icesmp-doc-id: command.market -->
<!-- icesmp-doc-id: alias.market.ah -->
<!-- icesmp-doc-id: alias.market.piac -->
<!-- icesmp-doc-id: command.menu -->
<!-- icesmp-doc-id: alias.menu.hub -->
<!-- icesmp-doc-id: alias.menu.m -->
<!-- icesmp-doc-id: command.moderation -->
<!-- icesmp-doc-id: alias.moderation.mod -->
<!-- icesmp-doc-id: command.msg -->
<!-- icesmp-doc-id: command.mute -->
<!-- icesmp-doc-id: command.npcbind -->
<!-- icesmp-doc-id: alias.npcbind.npckotes -->
<!-- icesmp-doc-id: command.offlinetp -->
<!-- icesmp-doc-id: command.parbaj -->
<!-- icesmp-doc-id: alias.parbaj.duel -->
<!-- icesmp-doc-id: command.parkour -->
<!-- icesmp-doc-id: alias.parkour.palya -->
<!-- icesmp-doc-id: alias.parkour.trial -->
<!-- icesmp-doc-id: command.party -->
<!-- icesmp-doc-id: alias.party.p -->
<!-- icesmp-doc-id: alias.party.parti -->
<!-- icesmp-doc-id: command.pet -->
<!-- icesmp-doc-id: alias.pet.companion -->
<!-- icesmp-doc-id: alias.pet.tars -->
<!-- icesmp-doc-id: command.profession -->
<!-- icesmp-doc-id: alias.profession.prof -->
<!-- icesmp-doc-id: alias.profession.szakma -->
<!-- icesmp-doc-id: command.profile -->
<!-- icesmp-doc-id: alias.profile.char -->
<!-- icesmp-doc-id: alias.profile.karakter -->
<!-- icesmp-doc-id: alias.profile.status -->
<!-- icesmp-doc-id: command.punishments -->
<!-- icesmp-doc-id: command.quest -->
<!-- icesmp-doc-id: alias.quest.kuldetes -->
<!-- icesmp-doc-id: alias.quest.quests -->
<!-- icesmp-doc-id: command.relic -->
<!-- icesmp-doc-id: alias.relic.relics -->
<!-- icesmp-doc-id: alias.relic.relikvia -->
<!-- icesmp-doc-id: command.reply -->
<!-- icesmp-doc-id: alias.reply.r -->
<!-- icesmp-doc-id: command.report -->
<!-- icesmp-doc-id: alias.report.bejelent -->
<!-- icesmp-doc-id: command.reports -->
<!-- icesmp-doc-id: command.sinner -->
<!-- icesmp-doc-id: command.sit -->
<!-- icesmp-doc-id: command.socialspy -->
<!-- icesmp-doc-id: command.soulforge -->
<!-- icesmp-doc-id: alias.soulforge.lelekkovacs -->
<!-- icesmp-doc-id: command.souls -->
<!-- icesmp-doc-id: alias.souls.lelek -->
<!-- icesmp-doc-id: alias.souls.soul -->
<!-- icesmp-doc-id: command.spec -->
<!-- icesmp-doc-id: alias.spec.specializacio -->
<!-- icesmp-doc-id: alias.spec.specialization -->
<!-- icesmp-doc-id: command.spell -->
<!-- icesmp-doc-id: alias.spell.mastery -->
<!-- icesmp-doc-id: alias.spell.mesterseg -->
<!-- icesmp-doc-id: alias.spell.spells -->
<!-- icesmp-doc-id: command.spellbook -->
<!-- icesmp-doc-id: alias.spellbook.konyv -->
<!-- icesmp-doc-id: alias.spellbook.sb -->
<!-- icesmp-doc-id: alias.spellbook.varazskonyv -->
<!-- icesmp-doc-id: command.stats -->
<!-- icesmp-doc-id: command.suttogas -->
<!-- icesmp-doc-id: alias.suttogas.sutt -->
<!-- icesmp-doc-id: command.szakmacel -->
<!-- icesmp-doc-id: alias.szakmacel.weeklygoal -->
<!-- icesmp-doc-id: command.talent -->
<!-- icesmp-doc-id: alias.talent.talentfa -->
<!-- icesmp-doc-id: alias.talent.talents -->
<!-- icesmp-doc-id: command.tanacs -->
<!-- icesmp-doc-id: alias.tanacs.council -->
<!-- icesmp-doc-id: command.tell -->
<!-- icesmp-doc-id: command.tempban -->
<!-- icesmp-doc-id: command.territory -->
<!-- icesmp-doc-id: alias.territory.terulet -->
<!-- icesmp-doc-id: command.unban -->
<!-- icesmp-doc-id: command.unmute -->
<!-- icesmp-doc-id: command.vanish -->
<!-- icesmp-doc-id: alias.vanish.v -->
<!-- icesmp-doc-id: command.w -->
<!-- icesmp-doc-id: command.warn -->
<!-- icesmp-doc-id: route.icesmp.root-3d9c831e41 -->
<!-- icesmp-doc-id: route.icesmp.reload-59cdd77521 -->
<!-- icesmp-doc-id: route.icesmp.config-menu-2342f17ab0 -->
<!-- icesmp-doc-id: route.icesmp.config-get-kulcs-ddfd01586c -->
<!-- icesmp-doc-id: route.icesmp.config-set-kulcs-rt-k-2048ff1985 -->
<!-- icesmp-doc-id: route.icesmp.config-unset-kulcs-66a43b357e -->
<!-- icesmp-doc-id: route.icesmp.config-list-1dbb2c6483 -->
<!-- icesmp-doc-id: route.icesmp.config-find-sz-vegr-szlet-9769e38c69 -->
<!-- icesmp-doc-id: route.icesmp.inspect-n-v-cf976d4f92 -->
<!-- icesmp-doc-id: route.invsee.online-j-t-kos-read-edit-main-ender-8fd4819785 -->
<!-- icesmp-doc-id: route.hud.root-98fc1cf7c6 -->
<!-- icesmp-doc-id: route.hud.toggle-frakcio-valuta-kaszt-eroforras-esemeny-csapat-mind-098d489766 -->
<!-- icesmp-doc-id: route.stats.n-v-b12560a71c -->
<!-- icesmp-doc-id: route.sit.root-be684d6e26 -->
<!-- icesmp-doc-id: route.sit.fel-97e382b436 -->
<!-- icesmp-doc-id: route.afk.root-5475cc764f -->
<!-- icesmp-doc-id: route.crate.root-4df4fe3c03 -->
<!-- icesmp-doc-id: route.crate.buy-l-da-id-darab-bc58b9f983 -->
<!-- icesmp-doc-id: route.crate.info-l-da-id-9ff0385057 -->
<!-- icesmp-doc-id: route.crate.preview-l-da-id-b56ef1b2d2 -->
<!-- icesmp-doc-id: route.crate.set-l-da-id-9e3e673970 -->
<!-- icesmp-doc-id: route.crate.remove-2c7a16650f -->
<!-- icesmp-doc-id: route.crate.give-online-j-t-kos-l-da-id-darab-eb82d50ae8 -->
<!-- icesmp-doc-id: route.crate.list-aa44f4dfea -->
<!-- icesmp-doc-id: route.crate.stats-j-t-kos-uuid-l-da-id-6f820eec2b -->
<!-- icesmp-doc-id: route.crate.resetstats-j-t-kos-uuid-l-da-id-all-6cafe1d747 -->
<!-- icesmp-doc-id: route.crate.status-f84c129c2f -->
<!-- icesmp-doc-id: route.report.n-v-ok-fbb91ba7da -->
<!-- icesmp-doc-id: route.reports.root-420499c21e -->
<!-- icesmp-doc-id: route.reports.all-3358168940 -->
<!-- icesmp-doc-id: route.reports.resolve-id-5e4903abb8 -->
<!-- icesmp-doc-id: route.warn.j-t-kos-ok-73731e594b -->
<!-- icesmp-doc-id: route.kick.online-j-t-kos-ok-05104518c2 -->
<!-- icesmp-doc-id: route.mute.j-t-kos-id-tartam-v-gleges-ok-5f83de69c5 -->
<!-- icesmp-doc-id: route.ban.j-t-kos-ok-d58e60fdc2 -->
<!-- icesmp-doc-id: route.tempban.j-t-kos-id-tartam-ok-e05bb07e0d -->
<!-- icesmp-doc-id: route.unmute.j-t-kos-ok-4cf10ebd4d -->
<!-- icesmp-doc-id: route.unban.j-t-kos-ok-0538618e15 -->
<!-- icesmp-doc-id: route.history.j-t-kos-oldal-28b7b11d63 -->
<!-- icesmp-doc-id: route.punishments.j-t-kos-774de3965f -->
<!-- icesmp-doc-id: route.moderation.online-j-t-kos-6a68bc313b -->
<!-- icesmp-doc-id: route.socialspy.root-f752d1a522 -->
<!-- icesmp-doc-id: route.vanish.online-j-t-kos-025d0452a8 -->
<!-- icesmp-doc-id: route.offlinetp.j-t-kos-1fcc8178cc -->
<!-- icesmp-doc-id: route.msg.j-t-kos-zenet-59f5bb82b9 -->
<!-- icesmp-doc-id: route.tell.j-t-kos-zenet-92351ac64b -->
<!-- icesmp-doc-id: route.w.j-t-kos-zenet-f5a5ab9bc2 -->
<!-- icesmp-doc-id: route.reply.zenet-612b6f0ac3 -->
<!-- icesmp-doc-id: route-alias.reply.zenet-612b6f0ac3.r-454349e4 -->
<!-- icesmp-doc-id: route.currency.root-035bfb2f68 -->
<!-- icesmp-doc-id: route.currency.balance-currency-73fb29b82a -->
<!-- icesmp-doc-id: route.currency.pay-online-j-t-kos-sszeg-currency-466cf70e4d -->
<!-- icesmp-doc-id: route.currency.set-online-j-t-kos-sszeg-currency-589e786299 -->
<!-- icesmp-doc-id: route.currency.exchange-sszeg-honnan-hov-3120de7963 -->
<!-- icesmp-doc-id: route.currency.rates-dbef3411ec -->
<!-- icesmp-doc-id: route.bank.root-5546d57526 -->
<!-- icesmp-doc-id: route.bank.balance-0ce79a966f -->
<!-- icesmp-doc-id: route.bank.deposit-d3026e7f77 -->
<!-- icesmp-doc-id: route.bank.withdraw-red-blue-neutral-dark-sszeg-837b26fa49 -->
<!-- icesmp-doc-id: route.faction.root-0e119be40e -->
<!-- icesmp-doc-id: route.faction.join-frakci-3aa58cfbd1 -->
<!-- icesmp-doc-id: route.faction.leave-dd49a29a9e -->
<!-- icesmp-doc-id: route.faction.set-j-t-kos-frakci-681dc23a49 -->
<!-- icesmp-doc-id: route.faction.treasury-daa12fc76e -->
<!-- icesmp-doc-id: route.faction.treasury-withdraw-sszeg-4c84aba263 -->
<!-- icesmp-doc-id: route.faction.donate-sszeg-f22a0cacaf -->
<!-- icesmp-doc-id: route.faction.king-cb0eb0b838 -->
<!-- icesmp-doc-id: route.faction.king-vote-j-t-kos-3a5ae01125 -->
<!-- icesmp-doc-id: route.faction.king-tax-sz-zal-k-af0668c552 -->
<!-- icesmp-doc-id: route.faction.king-set-frakci-online-j-t-kos-31f370ac59 -->
<!-- icesmp-doc-id: route.faction.king-clear-frakci-f809a896f3 -->
<!-- icesmp-doc-id: route.faction.raid-c-lfrakci-ter-let-6fc1e21fb6 -->
<!-- icesmp-doc-id: route.faction.raid-join-1e2256d245 -->
<!-- icesmp-doc-id: route.faction.raid-status-f7f0ed89e6 -->
<!-- icesmp-doc-id: route.faction.caravan-send-sszeg-19f36c48e1 -->
<!-- icesmp-doc-id: route.faction.war-c78150450b -->
<!-- icesmp-doc-id: route.faction.war-start-perc-de690421fb -->
<!-- icesmp-doc-id: route.faction.war-stop-23b6a56d72 -->
<!-- icesmp-doc-id: route.class.root-4bc3de9620 -->
<!-- icesmp-doc-id: route.class.addxp-online-j-t-kos-mennyis-g-4492d1559e -->
<!-- icesmp-doc-id: route.class.setxp-online-j-t-kos-mennyis-g-051bd470b0 -->
<!-- icesmp-doc-id: route.class.status-online-j-t-kos-07f230c02d -->
<!-- icesmp-doc-id: route.class.unlockspell-online-j-t-kos-spell-id-91cd823b26 -->
<!-- icesmp-doc-id: route.class.givecatalyst-online-j-t-kos-9b2c72e248 -->
<!-- icesmp-doc-id: route.class.listspells-8ae1b5d52f -->
<!-- icesmp-doc-id: route.class.admin-resetcd-online-j-t-kos-6a335f757f -->
<!-- icesmp-doc-id: route.class.admin-unlockallskills-online-j-t-kos-c16e8031f8 -->
<!-- icesmp-doc-id: route.class.admin-resetskills-online-j-t-kos-d53fd5bc5c -->
<!-- icesmp-doc-id: route.class.admin-resetclass-online-j-t-kos-012226e88e -->
<!-- icesmp-doc-id: route.menu.root-8b07263efa -->
<!-- icesmp-doc-id: route.achievements.root-4ddb61bbff -->
<!-- icesmp-doc-id: route.leaderboard.level-wealth-raidkills-ccd3ef3f77 -->
<!-- icesmp-doc-id: route-alias.leaderboard.level-wealth-raidkills-ccd3ef3f77.vagyon-7f1c4815 -->
<!-- icesmp-doc-id: route-alias.leaderboard.level-wealth-raidkills-ccd3ef3f77.raid-263dae9a -->
<!-- icesmp-doc-id: route-alias.leaderboard.level-wealth-raidkills-ccd3ef3f77.kills-a52ff550 -->
<!-- icesmp-doc-id: route.profile.root-55d6850288 -->
<!-- icesmp-doc-id: route.sinner.online-j-t-kos-set-4487ebd0b7 -->
<!-- icesmp-doc-id: route.sinner.online-j-t-kos-clear-baa6a50757 -->
<!-- icesmp-doc-id: route.sinner.online-j-t-kos-add-ea7bdc87ce -->
<!-- icesmp-doc-id: route.sinner.online-j-t-kos-status-8febd3a6d4 -->
<!-- icesmp-doc-id: route.bounty.root-930183e5f0 -->
<!-- icesmp-doc-id: route.relic.root-c5c7ae3318 -->
<!-- icesmp-doc-id: route.relic.list-c45f611cc0 -->
<!-- icesmp-doc-id: route.relic.give-online-j-t-kos-relikvia-id-mennyis-g-8b05b3b354 -->
<!-- icesmp-doc-id: route.parkour.list-89ff8ef94c -->
<!-- icesmp-doc-id: route.parkour.start-p-lya-id-e2efe6915c -->
<!-- icesmp-doc-id: route.parkour.setstart-id-n-v-873b963232 -->
<!-- icesmp-doc-id: route.parkour.setfinish-id-sug-r-jutalom-e12f0390ad -->
<!-- icesmp-doc-id: route.parkour.remove-id-3bb2785e27 -->
<!-- icesmp-doc-id: route.daily.root-8ad2b380cb -->
<!-- icesmp-doc-id: route.pet.menu-0a4aca2b61 -->
<!-- icesmp-doc-id: route.pet.info-5b47360ae5 -->
<!-- icesmp-doc-id: route.pet.item-ef593759b5 -->
<!-- icesmp-doc-id: route.pet.summon-b4246998f6 -->
<!-- icesmp-doc-id: route.pet.dismiss-0764e57f33 -->
<!-- icesmp-doc-id: route.pet.name-n-v-1bea63e82f -->
<!-- icesmp-doc-id: route.pet.stance-aktiv-passziv-marad-fb3ceee1b8 -->
<!-- icesmp-doc-id: route-alias.pet.stance-aktiv-passziv-marad-fb3ceee1b8.active-96879611 -->
<!-- icesmp-doc-id: route-alias.pet.stance-aktiv-passziv-marad-fb3ceee1b8.passive-a7f1d7bc -->
<!-- icesmp-doc-id: route-alias.pet.stance-aktiv-passziv-marad-fb3ceee1b8.stay-39be1528 -->
<!-- icesmp-doc-id: route.profession.root-9ff8b16ee7 -->
<!-- icesmp-doc-id: route.profession.join-szakma-7eabe1c640 -->
<!-- icesmp-doc-id: route.profession.info-903dd2a3ac -->
<!-- icesmp-doc-id: route.profession.list-f390d34b26 -->
<!-- icesmp-doc-id: route.profession.recipes-d99cf16c6c -->
<!-- icesmp-doc-id: route-alias.profession.recipes-d99cf16c6c.receptek-326be994 -->
<!-- icesmp-doc-id: route-alias.profession.recipes-d99cf16c6c.book-92719fe0 -->
<!-- icesmp-doc-id: route.profession.blueprint-online-j-t-kos-recept-id-75d1da1621 -->
<!-- icesmp-doc-id: route-alias.profession.blueprint-online-j-t-kos-recept-id-75d1da1621.tervrajz-9069daad -->
<!-- icesmp-doc-id: route.profession.set-online-j-t-kos-szakma-b4ba6c62ba -->
<!-- icesmp-doc-id: route.profession.clear-online-j-t-kos-gathering-crafting-94f838cfa8 -->
<!-- icesmp-doc-id: route.profession.addxp-online-j-t-kos-szakma-mennyis-g-34c6d265e3 -->
<!-- icesmp-doc-id: route.spec.root-987236570c -->
<!-- icesmp-doc-id: route.spec.list-3a48d599f5 -->
<!-- icesmp-doc-id: route.spec.choose-specializ-ci-id-eb820838cd -->
<!-- icesmp-doc-id: route.spec.info-2f86e46064 -->
<!-- icesmp-doc-id: route.spec.respec-class-profession-d6b17a0059 -->
<!-- icesmp-doc-id: route.spec.reset-online-j-t-kos-e785a7cc0e -->
<!-- icesmp-doc-id: route.talent.list-1240393f51 -->
<!-- icesmp-doc-id: route.talent.spend-class-profession-talent-id-4d443e368d -->
<!-- icesmp-doc-id: route.territory.root-551f2787da -->
<!-- icesmp-doc-id: route.territory.pos-83500aed80 -->
<!-- icesmp-doc-id: route-alias.territory.pos-83500aed80.point-251fecd5 -->
<!-- icesmp-doc-id: route.territory.wand-de21264e40 -->
<!-- icesmp-doc-id: route-alias.territory.wand-de21264e40.palca-0c18f304 -->
<!-- icesmp-doc-id: route.territory.undo-d88a000549 -->
<!-- icesmp-doc-id: route.territory.clearpoints-a3c52199a3 -->
<!-- icesmp-doc-id: route-alias.territory.clearpoints-a3c52199a3.clear-913a4cb9 -->
<!-- icesmp-doc-id: route.territory.points-e068457b4c -->
<!-- icesmp-doc-id: route.territory.create-t-pus-frakci-id-n-v-f315256212 -->
<!-- icesmp-doc-id: route.territory.circle-t-pus-frakci-id-sug-r-n-v-1f1b505ae2 -->
<!-- icesmp-doc-id: route.territory.setcapital-frakci-sug-r-n-v-d7c17b656b -->
<!-- icesmp-doc-id: route.territory.setspawn-frakci-3b9d0276a8 -->
<!-- icesmp-doc-id: route.territory.rename-id-j-n-v-0e55b1e6f3 -->
<!-- icesmp-doc-id: route.territory.resize-id-sug-r-a7bc47bae0 -->
<!-- icesmp-doc-id: route.territory.settype-id-t-pus-ef4b1e7f15 -->
<!-- icesmp-doc-id: route.territory.sety-id-min-y-max-y-2fd5f53085 -->
<!-- icesmp-doc-id: route.territory.remove-id-be464443d2 -->
<!-- icesmp-doc-id: route.territory.list-8b41a6ee36 -->
<!-- icesmp-doc-id: route.territory.info-bc573dcb2e -->
<!-- icesmp-doc-id: route.territory.show-id-302bacf618 -->
<!-- icesmp-doc-id: route.territory.tp-id-809cb14024 -->
<!-- icesmp-doc-id: route-alias.territory.tp-id-809cb14024.teleport-e99f7ff1 -->
<!-- icesmp-doc-id: route.territory.dungeonchest-t-bla-2ac1095770 -->
<!-- icesmp-doc-id: route.territory.dungeonboss-z-na-id-t-bla-0775bbfe37 -->
<!-- icesmp-doc-id: route.territory.dungeonboss-clear-z-na-id-126c83f468 -->
<!-- icesmp-doc-id: route.quest.root-2330b6d04b -->
<!-- icesmp-doc-id: route.quest.log-6d94f9ac8a -->
<!-- icesmp-doc-id: route-alias.quest.log-6d94f9ac8a.gui-04700787 -->
<!-- icesmp-doc-id: route-alias.quest.log-6d94f9ac8a.naplo-282c9fad -->
<!-- icesmp-doc-id: route-alias.quest.log-6d94f9ac8a.napl-4e4f85c3 -->
<!-- icesmp-doc-id: route.quest.list-d10bd003cc -->
<!-- icesmp-doc-id: route.quest.info-d76d7067e7 -->
<!-- icesmp-doc-id: route.quest.accept-quest-id-ebcd4fef74 -->
<!-- icesmp-doc-id: route.quest.talk-npc-n-v-36432687d5 -->
<!-- icesmp-doc-id: route.quest.abandon-quest-id-c70615d52c -->
<!-- icesmp-doc-id: route.quest.complete-online-j-t-kos-quest-id-341a175f72 -->
<!-- icesmp-doc-id: route.quest.admin-create-id-objekt-va-darab-n-v-54102deb8d -->
<!-- icesmp-doc-id: route.quest.admin-addobjective-id-objekt-va-darab-le-r-s-eb03e45589 -->
<!-- icesmp-doc-id: route.quest.admin-set-id-mez-rt-k-83b7969b58 -->
<!-- icesmp-doc-id: route.quest.admin-delete-id-e0f41067f7 -->
<!-- icesmp-doc-id: route.quest.admin-info-id-9bb4774bc5 -->
<!-- icesmp-doc-id: route.quest.admin-list-5dc546862b -->
<!-- icesmp-doc-id: route.quest.admin-builder-id-170544ece4 -->
<!-- icesmp-doc-id: route.market.browse-0289c12800 -->
<!-- icesmp-doc-id: route.market.sell-r-valuta-57dd501253 -->
<!-- icesmp-doc-id: route.market.auction-kiki-lt-si-r-ra-valuta-buyout-r-bo-r-9e5a92f7db -->
<!-- icesmp-doc-id: route.market.ereklye-13c3460bb9 -->
<!-- icesmp-doc-id: route.market.claim-f558873257 -->
<!-- icesmp-doc-id: route.market.cancel-041b753cec -->
<!-- icesmp-doc-id: route.market.search-sz-veg-18b2b3d8f4 -->
<!-- icesmp-doc-id: route.market.stats-fcdaa5edd9 -->
<!-- icesmp-doc-id: route.adomany.root-896218f922 -->
<!-- icesmp-doc-id: route.adomany.add-b01aecc6b5 -->
<!-- icesmp-doc-id: route.party.root-ddd9549572 -->
<!-- icesmp-doc-id: route.party.invite-online-j-t-kos-391290d946 -->
<!-- icesmp-doc-id: route.party.accept-6f9c636cbc -->
<!-- icesmp-doc-id: route.party.decline-8e1aef6b0a -->
<!-- icesmp-doc-id: route.party.leave-94df1da4cb -->
<!-- icesmp-doc-id: route.party.disband-a0bbc2eb5a -->
<!-- icesmp-doc-id: route.party.kick-tag-1f43396265 -->
<!-- icesmp-doc-id: route.party.promote-tag-a40ba54bf3 -->
<!-- icesmp-doc-id: route.party.list-a76808d4f9 -->
<!-- icesmp-doc-id: route.party.chat-zenet-ea615e7445 -->
<!-- icesmp-doc-id: route.party.c-zenet-aba9f8fcf6 -->
<!-- icesmp-doc-id: route.party.zenet-aa13d37028 -->
<!-- icesmp-doc-id: route.party.help-82e4e4b65f -->
<!-- icesmp-doc-id: route.ceh.root-2a787591e2 -->
<!-- icesmp-doc-id: route.ceh.letrehoz-n-v-e159c0c9de -->
<!-- icesmp-doc-id: route-alias.ceh.letrehoz-n-v-e159c0c9de.create-fa8847b0 -->
<!-- icesmp-doc-id: route.ceh.meghiv-online-j-t-kos-643986349e -->
<!-- icesmp-doc-id: route-alias.ceh.meghiv-online-j-t-kos-643986349e.invite-5014f9af -->
<!-- icesmp-doc-id: route.ceh.elfogad-6f392ee845 -->
<!-- icesmp-doc-id: route-alias.ceh.elfogad-6f392ee845.accept-c125d039 -->
<!-- icesmp-doc-id: route.ceh.elhagy-238de74a27 -->
<!-- icesmp-doc-id: route-alias.ceh.elhagy-238de74a27.leave-af193190 -->
<!-- icesmp-doc-id: route.ceh.kirug-ismert-j-t-kos-f3118ea29e -->
<!-- icesmp-doc-id: route-alias.ceh.kirug-ismert-j-t-kos-f3118ea29e.kick-0db10f2c -->
<!-- icesmp-doc-id: route.ceh.befizet-sszeg-9367a8fe72 -->
<!-- icesmp-doc-id: route-alias.ceh.befizet-sszeg-9367a8fe72.deposit-c3b9fb78 -->
<!-- icesmp-doc-id: route.ceh.info-b76de437dd -->
<!-- icesmp-doc-id: route.ceh.lista-5ae7861e0e -->
<!-- icesmp-doc-id: route-alias.ceh.lista-5ae7861e0e.list-a330395c -->
<!-- icesmp-doc-id: route.bestiarium.root-86cedece7b -->
<!-- icesmp-doc-id: route.soulforge.root-173a0e18b3 -->
<!-- icesmp-doc-id: route.soulforge.fejleszt-elet-sebzes-letszam-e24f7f80ec -->
<!-- icesmp-doc-id: route-alias.soulforge.fejleszt-elet-sebzes-letszam-e24f7f80ec.let-978d3f9e -->
<!-- icesmp-doc-id: route-alias.soulforge.fejleszt-elet-sebzes-letszam-e24f7f80ec.sebz-s-994e83f7 -->
<!-- icesmp-doc-id: route-alias.soulforge.fejleszt-elet-sebzes-letszam-e24f7f80ec.l-tsz-m-cbd2def0 -->
<!-- icesmp-doc-id: route.parbaj.root-ad3b3b75ac -->
<!-- icesmp-doc-id: route.parbaj.kihiv-online-j-t-kos-77340a6a59 -->
<!-- icesmp-doc-id: route-alias.parbaj.kihiv-online-j-t-kos-77340a6a59.challenge-2dd00bd7 -->
<!-- icesmp-doc-id: route.parbaj.elfogad-8ed448f0cf -->
<!-- icesmp-doc-id: route-alias.parbaj.elfogad-8ed448f0cf.accept-c125d039 -->
<!-- icesmp-doc-id: route.parbaj.elutasit-7ac397cfb7 -->
<!-- icesmp-doc-id: route-alias.parbaj.elutasit-7ac397cfb7.decline-fd5f8cbe -->
<!-- icesmp-doc-id: route.kem.c-lfrakci-91c0c8e276 -->
<!-- icesmp-doc-id: route.szakmacel.root-5b9277c302 -->
<!-- icesmp-doc-id: route.claim.claim-20ce366db5 -->
<!-- icesmp-doc-id: route.claim.unclaim-921e28c61a -->
<!-- icesmp-doc-id: route.claim.info-5761497c96 -->
<!-- icesmp-doc-id: route.claim.list-dc01b5c04b -->
<!-- icesmp-doc-id: route.claim.trust-online-j-t-kos-b9a427c2cf -->
<!-- icesmp-doc-id: route.claim.untrust-online-j-t-kos-b0b336ee22 -->
<!-- icesmp-doc-id: route.claim.trustgui-6d7c53398e -->
<!-- icesmp-doc-id: route.claim.show-577edd5549 -->
<!-- icesmp-doc-id: route.claim.pos1-b0e536ddfc -->
<!-- icesmp-doc-id: route.claim.pos2-4b996c6d87 -->
<!-- icesmp-doc-id: route.claim.wand-852c619412 -->
<!-- icesmp-doc-id: route-alias.claim.wand-852c619412.palca-0c18f304 -->
<!-- icesmp-doc-id: route.claim.area-197d175442 -->
<!-- icesmp-doc-id: route.claim.extend-up-down-42379080c0 -->
<!-- icesmp-doc-id: route.claim.admin-unclaim-3a49df98f3 -->
<!-- icesmp-doc-id: route.claim.help-92f62de5b6 -->
<!-- icesmp-doc-id: route.events.season-0484dad8c1 -->
<!-- icesmp-doc-id: route.events.status-3f316413cd -->
<!-- icesmp-doc-id: route.events.blood-moon-22be7052e8 -->
<!-- icesmp-doc-id: route-alias.events.blood-moon-22be7052e8.bloodmoon-f05aee90 -->
<!-- icesmp-doc-id: route.events.blood-moon-start-ab72f4e2a5 -->
<!-- icesmp-doc-id: route-alias.events.blood-moon-start-ab72f4e2a5.bloodmoon-f05aee90 -->
<!-- icesmp-doc-id: route.events.blood-moon-stop-c363352a8d -->
<!-- icesmp-doc-id: route-alias.events.blood-moon-stop-c363352a8d.bloodmoon-f05aee90 -->
<!-- icesmp-doc-id: route.events.caravan-a8813234aa -->
<!-- icesmp-doc-id: route-alias.events.caravan-a8813234aa.karavan-d92937d2 -->
<!-- icesmp-doc-id: route.events.caravan-arrive-a5dc4d50fc -->
<!-- icesmp-doc-id: route-alias.events.caravan-arrive-a5dc4d50fc.karavan-d92937d2 -->
<!-- icesmp-doc-id: route-alias.events.caravan-arrive-a5dc4d50fc.start-cced28c6 -->
<!-- icesmp-doc-id: route.events.caravan-depart-9193099e2e -->
<!-- icesmp-doc-id: route-alias.events.caravan-depart-9193099e2e.karavan-d92937d2 -->
<!-- icesmp-doc-id: route-alias.events.caravan-depart-9193099e2e.stop-6c45cb72 -->
<!-- icesmp-doc-id: route.events.worldboss-b1d9efaf9c -->
<!-- icesmp-doc-id: route-alias.events.worldboss-b1d9efaf9c.world-boss-33862e12 -->
<!-- icesmp-doc-id: route-alias.events.worldboss-b1d9efaf9c.boss-a5e7c002 -->
<!-- icesmp-doc-id: route.events.invasion-ad9a636b3a -->
<!-- icesmp-doc-id: route-alias.events.invasion-ad9a636b3a.invazio-9f7fa9d7 -->
<!-- icesmp-doc-id: route.events.ambient-4875fc8cc4 -->
<!-- icesmp-doc-id: route-alias.events.ambient-4875fc8cc4.hangulat-77d7303f -->
<!-- icesmp-doc-id: route.events.gathering-4d330f218c -->
<!-- icesmp-doc-id: route-alias.events.gathering-4d330f218c.buff-048b41bf -->
<!-- icesmp-doc-id: route-alias.events.gathering-4d330f218c.gyujtes-02ee978a -->
<!-- icesmp-doc-id: route.events.treasure-eb1b8c1835 -->
<!-- icesmp-doc-id: route-alias.events.treasure-eb1b8c1835.kincs-a54cd157 -->
<!-- icesmp-doc-id: route.events.wild-hunt-33733e2193 -->
<!-- icesmp-doc-id: route-alias.events.wild-hunt-33733e2193.wildhunt-972cdfce -->
<!-- icesmp-doc-id: route-alias.events.wild-hunt-33733e2193.hajsza-7abed88e -->
<!-- icesmp-doc-id: route.events.abundance-b4d9893c3e -->
<!-- icesmp-doc-id: route-alias.events.abundance-b4d9893c3e.boseg-0e3fff0a -->
<!-- icesmp-doc-id: route.events.challenge-cc41714193 -->
<!-- icesmp-doc-id: route-alias.events.challenge-cc41714193.kihivas-1ab58bc8 -->
<!-- icesmp-doc-id: route.events.escort-7bf8c23aa4 -->
<!-- icesmp-doc-id: route-alias.events.escort-7bf8c23aa4.kiseret-ca97308f -->
<!-- icesmp-doc-id: route.events.meteor-8144d5c9a3 -->
<!-- icesmp-doc-id: route.events.stranger-8113ee3373 -->
<!-- icesmp-doc-id: route-alias.events.stranger-8113ee3373.idegen-d9f72b44 -->
<!-- icesmp-doc-id: route.events.corruption-31120a4dec -->
<!-- icesmp-doc-id: route-alias.events.corruption-31120a4dec.rontas-b768f13b -->
<!-- icesmp-doc-id: route.events.archeology-59c0c86dd5 -->
<!-- icesmp-doc-id: route-alias.events.archeology-59c0c86dd5.regeszet-e40487bd -->
<!-- icesmp-doc-id: route.events.cultists-835d9785cd -->
<!-- icesmp-doc-id: route-alias.events.cultists-835d9785cd.kultistak-613a2e18 -->
<!-- icesmp-doc-id: route.events.spawnpoint-add-world-boss-escort-caravan-cultists-any-id-8274b4ad60 -->
<!-- icesmp-doc-id: route-alias.events.spawnpoint-add-world-boss-escort-caravan-cultists-any-id-8274b4ad60.spawnpont-f9cb59a1 -->
<!-- icesmp-doc-id: route.events.spawnpoint-remove-id-636b60fe54 -->
<!-- icesmp-doc-id: route-alias.events.spawnpoint-remove-id-636b60fe54.spawnpont-f9cb59a1 -->
<!-- icesmp-doc-id: route-alias.events.spawnpoint-remove-id-636b60fe54.torol-3bf98227 -->
<!-- icesmp-doc-id: route.events.spawnpoint-list-79877fb1e0 -->
<!-- icesmp-doc-id: route-alias.events.spawnpoint-list-79877fb1e0.spawnpont-f9cb59a1 -->
<!-- icesmp-doc-id: route-alias.events.spawnpoint-list-79877fb1e0.lista-0296c43c -->
<!-- icesmp-doc-id: route.events.intro-online-j-t-kos-88febc6e32 -->
<!-- icesmp-doc-id: route.komp.root-1969bea642 -->
<!-- icesmp-doc-id: route.komp.tvonal-id-c3bea2b90e -->
<!-- icesmp-doc-id: route.tanacs.info-98e9cb2be3 -->
<!-- icesmp-doc-id: route.tanacs.szavaz-online-j-t-kos-39363bc937 -->
<!-- icesmp-doc-id: route.tanacs.vasarhet-2b790f1d02 -->
<!-- icesmp-doc-id: route.emlek.root-7a3664a5ca -->
<!-- icesmp-doc-id: route.emlek.xp-0a1396db40 -->
<!-- icesmp-doc-id: route.emlek.talent-c777d563f2 -->
<!-- icesmp-doc-id: route.emlek.spec-495f15ff26 -->
<!-- icesmp-doc-id: route.emlek.lore-3021e093e9 -->
<!-- icesmp-doc-id: route.suttogas.zenet-5ab5968bcd -->
<!-- icesmp-doc-id: route.suttogas.v-d-online-j-t-kos-cd4a9b4db2 -->
<!-- icesmp-doc-id: route-alias.suttogas.v-d-online-j-t-kos-cd4a9b4db2.vad-651bfad0 -->
<!-- icesmp-doc-id: route-alias.suttogas.v-d-online-j-t-kos-cd4a9b4db2.accuse-603ec1f3 -->
<!-- icesmp-doc-id: route.lore.t-ma-e42a542b71 -->
<!-- icesmp-doc-id: route-alias.lore.t-ma-e42a542b71.red-b1f51a51 -->
<!-- icesmp-doc-id: route-alias.lore.t-ma-e42a542b71.piros-ef14443b -->
<!-- icesmp-doc-id: route-alias.lore.t-ma-e42a542b71.perinfernicitas-15766dc7 -->
<!-- icesmp-doc-id: route-alias.lore.t-ma-e42a542b71.blue-16477688 -->
<!-- icesmp-doc-id: route-alias.lore.t-ma-e42a542b71.kek-b794385f -->
<!-- icesmp-doc-id: route-alias.lore.t-ma-e42a542b71.cryghaliris-4e5bfdac -->
<!-- icesmp-doc-id: route-alias.lore.t-ma-e42a542b71.neutral-7e2372f4 -->
<!-- icesmp-doc-id: route-alias.lore.t-ma-e42a542b71.semleges-49399cab -->
<!-- icesmp-doc-id: route-alias.lore.t-ma-e42a542b71.ryanora-d6567440 -->
<!-- icesmp-doc-id: route-alias.lore.t-ma-e42a542b71.caldestera-16f23f3d -->
<!-- icesmp-doc-id: route-alias.lore.t-ma-e42a542b71.o-caldestera-c62b7f98 -->
<!-- icesmp-doc-id: route-alias.lore.t-ma-e42a542b71.ocaldestera-acef24c1 -->
<!-- icesmp-doc-id: route-alias.lore.t-ma-e42a542b71.gyokerek-fc33e873 -->
<!-- icesmp-doc-id: route-alias.lore.t-ma-e42a542b71.dark-e6bb5689 -->
<!-- icesmp-doc-id: route-alias.lore.t-ma-e42a542b71.sotet-c5ee87e0 -->
<!-- icesmp-doc-id: route-alias.lore.t-ma-e42a542b71.thanaopolis-b7ceb138 -->
<!-- icesmp-doc-id: route-alias.lore.t-ma-e42a542b71.mortengrad-f0de9006 -->
<!-- icesmp-doc-id: route-alias.lore.t-ma-e42a542b71.kitaszitott-12211f29 -->
<!-- icesmp-doc-id: route-alias.lore.t-ma-e42a542b71.eletfa-d1b9537e -->
<!-- icesmp-doc-id: route-alias.lore.t-ma-e42a542b71.elet-fa-fad4b021 -->
<!-- icesmp-doc-id: route-alias.lore.t-ma-e42a542b71.karhozat-d4bde78b -->
<!-- icesmp-doc-id: route-alias.lore.t-ma-e42a542b71.doom-910ecd3e -->
<!-- icesmp-doc-id: route-alias.lore.t-ma-e42a542b71.olethropyla-8d139239 -->
<!-- icesmp-doc-id: route-alias.lore.t-ma-e42a542b71.suttogas-157ec9e3 -->
<!-- icesmp-doc-id: route-alias.lore.t-ma-e42a542b71.whisper-ba795559 -->
<!-- icesmp-doc-id: route-alias.lore.t-ma-e42a542b71.torpok-4cefc26e -->
<!-- icesmp-doc-id: route-alias.lore.t-ma-e42a542b71.melyseg-nepe-ced305f2 -->
<!-- icesmp-doc-id: route-alias.lore.t-ma-e42a542b71.konyv-b13b3be4 -->
<!-- icesmp-doc-id: route-alias.lore.t-ma-e42a542b71.kronika-lore-ee554773 -->
<!-- icesmp-doc-id: route-alias.lore.t-ma-e42a542b71.korszak-46a05f95 -->
<!-- icesmp-doc-id: route-alias.lore.t-ma-e42a542b71.folyo-13e86b39 -->
<!-- icesmp-doc-id: route.kronika.root-7e4a313de0 -->
<!-- icesmp-doc-id: route.iceitem.root-77ef588c2d -->
<!-- icesmp-doc-id: route.iceitem.unique-unique-id-darab-online-j-t-kos-aba63b8dfc -->
<!-- icesmp-doc-id: route.iceitem.recept-recept-id-darab-online-j-t-kos-44157e0fe6 -->
<!-- icesmp-doc-id: route.iceitem.relikvia-relikvia-id-darab-online-j-t-kos-54553a15d7 -->
<!-- icesmp-doc-id: route.iceitem.tervrajz-recept-id-darab-online-j-t-kos-4c749ad1a2 -->
<!-- icesmp-doc-id: route.iceitem.erszeny-pozit-v-rt-k-darab-online-j-t-kos-be048fb800 -->
<!-- icesmp-doc-id: route.iceitem.dev-bingulus-id-darab-online-j-t-kos-dadc3c7c7d -->
<!-- icesmp-doc-id: route.souls.root-b3b82d6cd9 -->
<!-- icesmp-doc-id: route.souls.champion-f81b808b7b -->
<!-- icesmp-doc-id: route.spell.info-a7e028c4a3 -->
<!-- icesmp-doc-id: route.spell.upgrade-spell-id-7725da645e -->
<!-- icesmp-doc-id: route.spellbook.root-250358ba15 -->
<!-- icesmp-doc-id: route.exchangeboard.place-dd99f9a6f9 -->
<!-- icesmp-doc-id: route.exchangeboard.remove-238e179878 -->
<!-- icesmp-doc-id: route.npcbind.root-2e255ce8c8 -->
<!-- icesmp-doc-id: route.npcbind.list-973f13be25 -->
<!-- icesmp-doc-id: route.npcbind.npc-quest-quest-id-314dad3eb6 -->
<!-- icesmp-doc-id: route.npcbind.npc-shop-bolt-id-2ea19e1d0e -->
<!-- icesmp-doc-id: route.npcbind.npc-bank-1ffdb9725f -->
<!-- icesmp-doc-id: route.npcbind.npc-exchange-51ad41d49d -->
<!-- icesmp-doc-id: route.npcbind.npc-faction-8ddac3b81f -->
<!-- icesmp-doc-id: route.npcbind.npc-command-parancs-fe58b86b80 -->
<!-- icesmp-doc-id: route.npcbind.npc-clear-6a78aae506 -->
