# IceSMP – Ötlettár

Ötletek és tervek a fantasy "királyságos" SMP élményhez. A todo.md nyers jegyzeteire és a
már megépített rendszerekre (frakciók, kasztok, szakmák, relikviák, dinamikus gazdaság,
mob szintezés) épül.

## 1. Világ és fővárosok

- **Az Élet Fája (spawn):** a semleges frakció "fővárosa", egy óriási, kézzel épített
  világfa. Itt van a valutaváltó, a piactér és a szakma-oktatók. Harc tiltva (safe zone).
- **Két királyi főváros (Piros / Kék):** 2000–3000 blokkra a spawntól, ellentétes
  irányban. A mob szintezés miatt az út odáig önmagában is progresszió (~Lvl 2–3 mobok).
- **A Sötét romváros:** az összeomlott királyság. Messzebb (5000+ blokk), magas szintű
  mobokkal körülvéve — ide csak felkészülve éri meg elindulni. Itt érhető el a
  Nekromanta kaszt-quest és a Sötét frakcióba lépés.
- **Távolság-gyűrűk:** a mob szintezésre építve 1000 blokkonként "zónák" saját
  loot-asztallal: ritkább anyagok csak külső gyűrűkben (pl. ancient debris spawn-bónusz),
  így a veszély és a jutalom együtt skálázódik.

## 2. Frakció-mechanikák

- ✅ **Királyok és választás (KÉSZ):** /faction king vote alapú választás (min. szavazat +
  listavezetés = korona, ciklusonként újraindul); a király raidet hirdet és a kasszából
  vehet ki. Hátra van: adókulcs-állítás királyként.
- ✅ **Állampolgári kötelesség (KÉSZ):** időszakos adó a frakciókasszába a saját valuta
  banki egyenlegéből (`factions.tax.*` config, semlegesek mentesítve) — money sink a
  dinamikus árfolyamhoz. Kassza: `/faction treasury`, adomány: `/faction donate`.
- ✅ **Bűn- és száműzetés-rendszer (KÉSZ, részben):** sin_count számláló, gyilkosság = +1 bűn,
  a küszöbnél (alapból 4) automatikus száműzetés a Sötét frakcióba örök paktummal.
  Hátra van: lopás/árulás detektálás, és a "vezeklés" quest-lánc (quest-keretrendszer kell).
- ✅ **Frakció-reputáció (KÉSZ):** configból állítható ALLY/NEUTRAL/ENEMY viszony-mátrix
  (`factions.relations`), raid alatt a hadviselők automatikusan ENEMY-k. A viszony a
  piaci árat módosítja (`FactionRelationManager`): ellenségtől drágább (a felár elég =
  money sink), szövetségestől olcsóbb. A `/market` GUI a tényleges, módosított árat fizetteti.

## 3. Raid és háború

- ✅ **Raid eventek (KÉSZ, alap):** csak király hirdetheti (/faction raid), raid alatt a
  hadviselők közti ölés nem bűn, hanem pont. Hátra van: 10v10 limit, aréna/terület-kötés.
- ✅ **Hadizsákmány (KÉSZ, alap):** a győztes a vesztes kasszájának configolható %-át kapja.
  Hátra van: győztes-buff (pl. +10% szakma XP 2 napig).
- ✅ **Ostromgépek (KÉSZ):** craftolható "Ostromágyú" (TNT_MINECART recept: vasblokk-keret +
  TNT + tűzpor), ami CSAK aktív raid alatt sül el (`SiegeWeaponFactory` + `SiegeWeaponListener`).
  Jobb katt = terep-barát robbanás a célzott pontra (sebzi az entitásokat, kíméli a terepet);
  raiden kívül csak figyelmeztet. Money sink és kovács-tartalom egyben.

## 4. Relikviák és legendás tárgyak

- ✅ **4 frakció – 4 elytra relikvia (KÉSZ):** Főnix-szárny, Zúzmara-szárny, Vándorszél,
  Csontszárny — frakcióhoz és tulajdonoshoz kötött effektekkel (ElytraRelicListener).
- ✅ **Fegyver-relikviák PvP gazdacseréje (KÉSZ):** a droppolt fegyver-relikviák
  (relics.weapon-relics) a gyilkosé lesznek; a passzívak védettek.
- ✅ **Rituálé-oltárok (KÉSZ):** a relikviák megidézhetők egy configolt oltár-blokk
  SHIFT+jobb kattjával, áldozati tárgyak feláldozásával (`RitualManager` + `RitualListener`,
  `rituals.<relicId>` config: altar-block + sacrifice lista). A relikvia-egyediség szabálya
  érvényes (élő tulajdonossal nem idézhető újra). Alapból a 4 elytra-relikvia oltára él.

## 5. Kasztok és progresszió

- ✅ **Kaszt-questek (KÉSZ, alap):** 4 bevezető kaszt-próba quest + a Nekromanta spec
  quest-kapus beavatása (Sötét Beavatás, VISIT_TERRITORY a romvárosban). Hátra van:
  NPC-s/parkour jellegű egyedi próbapályák.
- **Specializációk max szinten:** Varázsló → Elementalista / Gyógyító;
  Harcos → Berserker / Védelmező; Íjász → Beast Master (idézhető farkas!) / Mesterlövész;
  Orgyilkos → Méregkeverő / Fantom; Nekromanta → Lélekidéző / Pestisúr.
- **Ultimate képességek:** kasztonként 1 nagy cooldownos (30–60 perc) látványos ulti,
  pl. Nekromanta: 3 csontváz-szolga idézése 60 mp-re.
- ✅ **Skill-fa GUI (KÉSZ):** a Job GUI Képesség-fa gombja a kaszt + spec spelljeit
  mutatja feloldási szint szerint, állapot-jelzéssel.

## 6. Gazdaság (a dinamikus árfolyamra építve)

- **Money sinkek:** adó, raid-nevezési díj, rituálé-anyagok, NPC szolgáltatások —
  ezek nélkül a kínálat csak nő, és minden valuta elinflálódik.
- ✅ **Piaci tábla / aukciósház (KÉSZ, alap):** /market sell + GUI-vásárlás banki
  egyenlegből, eladási díjjal (money sink). Hátra van: lapozás, keresés, fizikai piactábla.
- ✅ **Árfolyam-kijelző tábla (KÉSZ):** `/exchangeboard place` paranccsal lerakható
  TextDisplay hologram-tábla, ami a valuták élő, kínálat-alapú értékét mutatja és a
  világesemény-tick frissíti (`ExchangeBoardManager`, Folia-biztos region-scheduling,
  restart-túléléssel). "Tőzsdei" hangulat a fővárosokban.
- ✅ **Heti gazdasági esemény (KÉSZ):** véletlen kereslet-sokk (configolható szorzó és
  időtartam), broadcast-tel és restart-túléléssel.
- ✅ **Sötét valuta = lélekkő (KÉSZ):** magas szintű skálázott mobok eséllyel DARK tokent
  dobnak (currency.soul-drop config) — a külső gyűrűk grindje gazdaságilag is megéri.

## 7. Események és világesemények

- ✅ **Első belépés "videó" (KÉSZ, alap):** első belépéskor időzített cím-szekvencia
  (`IntroManager`, configból szerkeszthető sorok), egyszer fut, admin `/events intro` újrajátssza.
  Hátra van: kamera-utaztatás/spectator-útvonal.
- ✅ **Világ-bossok (KÉSZ):** időnként boss-szörny spawnol egy véletlen játékos közelében
  (`WorldBossManager`); a legyőző frakciója kasszát + liga-pontot kap, a slayer buffot.
- ✅ **Vérhold-éjszaka (KÉSZ):** ritka éjjel a skálázott mobok +N szintet kapnak és a
  lélekkő-drop felszorzódik (`BloodMoonManager`, broadcast-tel).
- ✅ **Szezonális liga (KÉSZ, alap):** a frakciók pontot gyűjtenek raid- és boss-győzelmekből
  (`SeasonManager`); a szezon végén a bajnok kasszája jutalmat kap, a pontok resetelnek.
  Hátra van: szezonális kozmetikai relikvia-jutalom.

## 7.5 WoW-stílusú pet/idéző rendszer továbbfejlesztése

Az alap idéző-keretrendszer (MinionManager, zombihorda, csontíjászok, pandaőrség, vad falka)
már él — a World of Warcraft class/profession irányt ezek mélyítenék el:

- ✅ **Pet parancsok (KÉSZ):** lopakodás + jobb katt a saját minionon → állásmód-váltás
  (Támadás / Passzív / Maradj), action bar visszajelzéssel.
- ⏳ **Megnevezett, fejlődő pet:** a Vadmester állandó, szintet lépő társa (még nincs;
  perzisztens companion entitás kell hozzá).
- ✅ **Nekromanta lélek-erőforrás (KÉSZ):** a Nekromanta-spec játékosok ellenség-ölésenként
  lélekszilánkot kapnak (`SoulShardManager` + `SoulShardListener`, PDC-ben tárolva), amiből
  a `/souls champion` paranccsal megerősített Wither-csontváz bajnok idézhető (configolható
  ár/élet/élettartam, az idézés-limitet tisztelve). `/souls` mutatja az egyenleget.
- ✅ **Idézés-limitek (KÉSZ):** max N aktív minion / játékos (`pets.max-active`), a túl-idézés
  blokkolva, a pakk a szabad helyekre korlátozódik.
- ✅ **Specializáció-szinergiák (KÉSZ, alap):** a gazda kaszt "max-health" talentjei +HP-t
  adnak a minionoknak (`pets.talent-health-share`). Hátra van: több attribútum-átöröklés.

## 8. Technikai ötletek (következő lépések)

- ✅ Frakció-kassza és adórendszer (`FactionTreasuryManager`) — KÉSZ.
- ✅ Quest-keretrendszer (QuestManager, 6 objective-típus, láncok, jutalmak) — KÉSZ;
  a vezeklés-lánc (penance_1..3) megtöri a sötét paktumot.
- Hologram-API integráció az árfolyam-táblákhoz (vagy TextDisplay entity, az 1.19.4+
  display entity-k Folián is jól működnek).
- A 4 elytra relikvia a meglévő relic framework triggereivel (RelicTrigger bővítés:
  GLIDE_START, TAKE_DAMAGE) implementálható.
- ✅ Világesemények (vérhold, világboss, szezon, intro) — KÉSZ (7. szekció).
