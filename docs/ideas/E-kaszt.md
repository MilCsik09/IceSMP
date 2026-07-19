# E) Kaszt- és specializáció-ötletek

[← Ötlettár-index](../IDEAS.md)

Jelölés minden tételnél: **Munka** (🟢 kicsi / 🟡 közepes / 🔴 nagy) • **Érték** (⭐–⭐⭐⭐).

## Kaszt → ötlet index

| Kaszt | Ötlet-id-k |
|---|---|
| 🧙 Varázsló | E1, E7 |
| ⚔️ Harcos | E3, E8 |
| 🏹 Íjász | E6, E9 |
| 🗡️ Orgyilkos | E10, E11, E26 |
| 🐻 Druida | E2, E12 |
| 🔆 Paplovag | E13, E14 |
| 💀 Halállovag | E15, E16, E17 |
| 🌊 Sámán | E18, E19, E20 |
| ☯️ Szerzetes | E4, E21 |
| ✝️ Pap | E22, E23, E24 |
| 😈 Boszorkánymester | E5, E25, E26 |
| 👁️ Démonvadász | E27, E28, E29 |
| 🐉 Sárkányidéző | E30, E31, E32 |

---

### E1. Nekromanta: lélek-kovácsolás

→ **Átkerült a lore-kiemelt válogatásba:** [L-lore-kiemelt.md](L-lore-kiemelt.md)

### E2. Druida: évszak-hangolódás
**Munka:** 🟢 • **Érték:** ⭐

**Mi ez:** A druida-formák (`DruidFormSpell`, stance-minta) kapjanak kis passzív bónuszt a világ-állapothoz igazodva.

**Hogyan működne:** A meglévő esemény-getterek (eső, éjszaka, world.yml Bőség-idő) alapján +5–10% szorzó a releváns spell-kategórián: esőben +8% gyógyítás-forma, éjjel +8% holdmágia (Holdjós), Bőség-idő alatt +10% természet-spellek. A szorzó cast pillanatában számolódik egy read-only segédfüggvényben (`classes.yml` → `druida.seasonal-bonus.*`), nincs külön állapot-tárolás.

**Miért jó:** a druida „érzi a világot" — passzív, gomb nélküli, mégis taktikai réteg (mikor gyógyíts, mikor vadássz).

**Építőkövek:** `DruidFormSpell` (stance-minta), meglévő esemény-getterek, `ConfiguredSpell` szorzó-hook.

**Buktatók:** több egyidejű bónusz (eső+éjjel+bőség) összeadva erőforrás-inflációt okozhat — összesített cap kell (pl. max +15%).

### E3. Harcos: pajzsfal állás
**Munka:** 🟡 • **Érték:** ⭐⭐

**Mi ez:** Új tank-állás-spell (stance-minta), ami a mögötte állókat védi.

**Hogyan működne:** `Pajzsfal` aktiválásakor (`BulwarkSpell`-mintájú stance-osztály) a Harcos mozgássebessége -30%, cserébe a MÖGÖTTE álló, a nézési irányból számolt ±60°-os szögtartományban lévő párttagok 15% sebzéscsökkentést kapnak. A célszűrés `SpellTargetingUtil.isAlly` (A2), a szög-számítás új geometriai segédfüggvény, ritkított frissítéssel (5 tick-enként, ne minden tickben). Fenntartás közben tick-enkénti kis Düh-fogyasztás (A3 Düh-profil), megszakad kifutásnál vagy gyors mozgásnál.

**Miért jó:** a Harcos tank-szerepét párt-védő dimenzióval bővíti — raidben/inváziónál konkrét pozicionálási taktikát ad („állj a Harcos mögé").

**Építőkövek:** `BulwarkSpell` (stance-minta), A2 isAlly targeting, ResourceManager Düh-profil (A3).

**Buktatók:** a szög+távolság-számítás minden párttagra drága lehet nagy csoportnál — ritkítás kötelező, ne minden tickben fusson.

### E4. Szerzetes: sörfőzde-mesterség
**Munka:** 🟢 • **Érték:** ⭐⭐

**Mi ez:** A Sörfőző spec (tank-szerep) kapjon exkluzív gazdasági szerepet: ő főzheti a D5 kocsma italait.

**Hogyan működne:** A recept-katalógusban (`profession-recipes.yml`) a kocsma-italok (D5) `class+spec` gate-tel készülnek — más kaszt/spec csak a piacon vásárolhatja meg készen. A Sörfőző passzív bónuszt kap saját főzött italára (pl. +20% buff-időtartam, ha ő fogyasztja — PDC „brewer" tag az itemen, összevetve a fogyasztó UUID-jével). A recept-lista 3-4 tematikus itallal bővül (buffos sör, gyors regen-ital).

**Miért jó:** a Sörfőző spec eddig csak harci identitás volt — ez gazdasági szerepet ad, a kocsma-készlet játékos-termelésű lesz (sink+kereslet egyben).

**Építőkövek:** D5 kocsma, `profession-recipes.yml` recept-gate, ItemFactory PDC-tag.

**Buktatók:** túl szigorú gate kevés Sörfőző-játékosnál szűk keresztmetszet lehet — érdemes a Szakácsnak is engedni egy gyengébb, buff nélküli alap-italt.

### E5. Boszorkánymester: lélek-alku
**Munka:** 🟡 • **Érték:** ⭐

**Mi ez:** Kockázat/jutalom mini-mechanika, ami a Lélekerő sötét „megalkuvás" jellegét adja.

**Hogyan működne:** `/soulpact` 5 percre aktivál: +15% spell-erő minden spellre, cserébe a HEALTH-cost (vér-mágia) spellek 25%-kal drágábbak (ResourceManager hibrid-listája), és halál esetén (ez alatt) extra bűn-pont a meglévő számlálóba. Önkéntes, 15 perc cooldown, HUD-on piros keret az Erő-csík körül, amíg aktív.

**Miért jó:** sűríti a warlock-identitást („alku a hatalomért"), összeköti a HP-alapú spelleket és a bűn-rendszert egy önkéntes, magas kockázatú döntési pillanatban.

**Építőkövek:** ResourceManager HEALTH-cost hibrid-lista, bűn-rendszer számláló, HudManager jelzés-keret.

**Buktatók:** PvE-halálnál is büntetni túl szigorú — érdemes csak PvP-halálra korlátozni, vagy csak harcban engedni aktiválni.

### E6. Íjász: sólyom-felderítő
**Munka:** 🟡 • **Érték:** ⭐

**Mi ez:** Rövid ideig köröző sólyom-társ, ami a felderítő-szerepet erősíti.

**Hogyan működne:** Egy spell-slot 15 mp-re szabadít fel egy ideiglenes, gazdátlan sólyom-entitást (saját entity-scheduleren, Folia), ami a caster feje fölött köröz és glowing-tag-et rak minden hosztilis célra, amit „lát" (raycast a sólyom pozíciójából — SpectralSight-minta madár-mozgással bővítve). Automatikus AI, nem manuális kamera; 90 mp cooldown, közepes Fókusz-költség.

**Miért jó:** az Íjász „felderítő vadász" identitása aktív, csapatban is hasznos toolt kap (raid-előkészítés) a tiszta DPS-rotáció mellé.

**Építőkövek:** SpectralSight-minta, entity-scheduler, A2 isHostile.

**Buktatók:** automatikus raycast minden ticknél sok egyidejű sólyomnál terhelést okoz — ritkított intervallum (5 tick) és max 1 aktív sólyom/player szükséges.

### E7. Varázsló: rúnaíró affinitás

→ **Átkerült a lore-kiemelt válogatásba:** [L-lore-kiemelt.md](L-lore-kiemelt.md)

### E8. Harcos: ostromtörő fegyelem
**Munka:** 🟢 • **Érték:** ⭐⭐

**Mi ez:** A Harcos legyen a „hadigépész" — B11 ostromgépeknél gyorsabb kezelés.

**Hogyan működne:** A `SiegeWeaponFactory` kezelés-eseményénél, ha a kezelő JobType Harcos, -30% újratöltési idő és +15% célzási szög-tolerancia (`classes.yml` → `harcos.siege-bonus.*`). Emellett passzív „Csatatéri fegyelem": raid-állapot alatt a Harcos Düh-regenje +20% (A3 Düh-profil szorzója).

**Miért jó:** a Harcos „frontvonal-parancsnok" fantasyje konkrét, mérhető haszonnal jár raidekben, és a B11 ostromgép-rendszernek is ad egy kaszt-okot a kezelésre.

**Építőkövek:** SiegeWeaponFactory (B11), ResourceManager Düh-profil (A3), `classes.yml`.

**Buktatók:** túl nagy bónusz raid-csapatok Harcos-monopóliumát okozhatja az ostromgépeknél — a szorzók maradjanak kényelmi jellegűek, ne kizáró erejűek.

### E9. Íjász: tegez-mesterség
**Munka:** 🟢 • **Érték:** ⭐⭐

**Mi ez:** A B37 nyíl-műhely speciális nyilai legyenek Íjász-kaszt-exkluzívak vagy jelentősen olcsóbbak neki.

**Hogyan működne:** A recept-katalógusban (`profession-recipes.yml`) a speciális nyíl-receptek (horgony/jelző/robbanó) „class-requirement: Íjász" gate-tel készülnek, más kasztnak csak a piacon elérhetők, drágábban. Emellett a Mesterlövész-spec mastery 5 fölött (`SpellMasteryManager`) egy célzott lövésnél 10% eséllyel nem fogyaszt nyilat a tegezből — apró, állandó DPS-nyereség, HUD-jelzéssel.

**Miért jó:** az íjász „profi vadász" identitása item-oldalon is testet ölt, a B37 fogyóeszköz-piac a saját kasztjának adja a legjobb megtérülést.

**Építőkövek:** B37 nyíl-műhely, `profession-recipes.yml` gate, SpellMasteryManager.

**Buktatók:** kaszt-gate a receptekben új validációs ág — figyelni kell, hogy a szakma-független recept-keret ne törjön el tőle.

### E10. Orgyilkos: kombó-pont felhalmozás
**Munka:** 🟡 • **Érték:** ⭐⭐⭐

**Mi ez:** Az Energia (A3: kicsi pool, gyors regen) mellé egy másodlagos, lassan gyűlő „Kombó-pont" (max 5) minden alap-találatra.

**Hogyan működne:** Sima fegyveres találat (nem spell) +1 kombó-pontot ad (max 5, action baren „●●●○○"), 8 mp inaktivitás után nullázódik, rövid életű player-állapotban tárolva (nem kell szerver-restart közt perzisztálni). A Méregkeverő/Fantom 2-3 kiemelt „finisher" spellje a ponttal skálázva sebez (pontonként +8%). A kombó-pont-számláló a `ConfiguredSpell` post-hitjébe kötött külön esemény, nem az A16 kombó-lánccal azonos infra.

**Miért jó:** az Orgyilkos „épülj fel, majd csattanj" rotáció-érzetet kap, ami mélyíti a gyors Energia-alapú harcstílust új erőforrás-pool bevezetése nélkül.

**Építőkövek:** ResourceManager Energia-profil (A3), ConfiguredSpell finisher-csoport, A16 kombó-infra (analógia, nem újrahasznosítás).

**Buktatók:** ha minden finisher skálázódik, két tengelyen mozog a balansz — érdemes 2-3 kulcs-spellre korlátozni.

### E11. Orgyilkos: végső árnyék (execute-forma)
**Munka:** 🟢 • **Érték:** ⭐⭐⭐

**Mi ez:** Ritka, látványos „signature moment" alacsony életerejű célponton.

**Hogyan működne:** Ha a célpont HP-ja 20% alatt van, a legdrágább single-target Orgyilkos-spell „Végső árnyék" branch-be vált: a caster egy pillanatra eltűnik (Invisibility 1 tick + dash-partikel), majd a célpont mögött jelenik meg villám-részecske csíkkal és egyedi hanggal, a sebzés +40%. Ugyanaz a cooldown, csak a threshold-alatti vizuális/sebzés-branch aktiválódik a `ConfiguredSpell` execute-hookjában.

**Miért jó:** az orgyilkos-fantasy csúcspontja — jó célpont-priorizálást jutalmaz látványos „kill-shot" pillanattal, PvP-adrenalin.

**Építőkövek:** ConfiguredSpell threshold-branch, ShadowstepSpell-szerű dash-vizuál, A2 targeting.

**Buktatók:** execute-mechanika PvP-ben frusztráló lehet az áldozatnak — szigorú (≤20%) küszöb, PvP-ben configból tiltható legyen.

### E12. Druida: alakváltás hang- és partikel-aláírás
**Munka:** 🟢 • **Érték:** ⭐⭐

**Mi ez:** A `DruidFormSpell` formaváltásai kapjanak forma-specifikus érzék-jelet.

**Hogyan működne:** Minden formánál (medve/farkas/cápa/stb.) egyedi hang (vanília hangkészletből komponálva) + rövid partikel-burst (levél/víz/szőr-témájú) váltáskor, és halk ambient-loop, amíg a forma aktív, ritka (kb. 1 mp-enkénti) frissítéssel a globalRegionScheduleren.

**Miért jó:** a Druida legnagyobb identitás-eleme (alakváltás) most néma — a hang/partikel réteg azonnal „élővé" teszi, számot nem módosít, balanszt nem érint.

**Építőkövek:** DruidFormSpell (stance-minta), LibsDisguises-híd, globalRegionScheduler ambient-loop.

**Buktatók:** folyamatos partikel-loop sok egyidejű druidánál Folián terhelés — kis radiusz és ritka frissítés kötelező.

### E13. Paplovag: eskü-töltés
**Munka:** 🟡 • **Érték:** ⭐⭐⭐

**Mi ez:** A Szent Erő kapjon tank-specifikus töltési forrást: blokkolt/elnyelt sebzés is tölti.

**Hogyan működne:** Amikor a Paplovag pajzzsal blokkol vagy `DevotionAuraSpell`-szerű reflect/absorb aktív rajta, a beérkező sebzés X%-a (pl. 30%) közvetlenül Szent Erővé alakul a passzív regen mellé — a ResourceManager gainOnEvent hookjának bővítése (A3 Düh-mintájának analógiája, itt bejövő sebzésre). Ez tölti fel gyorsabban a Megtorló-spec nagy burst-spelljét.

**Miért jó:** a Szent Erő végre a „szenvedés → erő" harcos-pap identitást tükrözi, nem generic mana-klón — a Védő tank-szerepét mechanikailag is jutalmazza.

**Építőkövek:** ResourceManager gainOnEvent (A3), BulwarkSpell/DevotionAuraSpell (pajzs-stance).

**Buktatók:** túl gyors felhalmozás burst-caster hibriddé teheti a Paplovagot tank helyett — % és cap playteszttel finomhangolandó.

### E14. Paplovag: eskü-pár (Szentlélek + Megtorló csapat-kombó)
**Munka:** 🟡 • **Érték:** ⭐⭐

**Mi ez:** Két Paplovag-spec (Szentlélek gyógyító, Megtorló DPS) párban különleges kombó-bónuszt ad egymásnak.

**Hogyan működne:** Ha a Szentlélek nagy heal-ultija egy Megtorlón sül el (A27 okos gyógyítás célzásával azonosítva), a Megtorló 6 mp-re +15% sebzést kap (rövid, nem perzisztált buff-tag). Fordítva: ha a Megtorló nagy burst-spellt cast, a közeli Szentlélek Szent Ereje +10 egységet kap azonnal. Mindkét irány a `ConfiguredSpell` post-cast hookjába épített, kaszt+spec-párra szűrt esemény.

**Miért jó:** a két spec izolált útjából duó-taktikát csinál, erősítve a raid-összeállítási döntéseket.

**Építőkövek:** A27 okos gyógyítás célzás-logika, ConfiguredSpell post-cast hook, A2 party/isAlly szűrés.

**Buktatók:** kaszt-spec-specifikus csapat-buffok „kötelező duó" metát kényszeríthetnek ki — a bónuszok maradjanak kicsik, rövid élettartamúak.

### E15. Halállovag: rúna-pecsét kombó
**Munka:** 🔴 • **Érték:** ⭐⭐⭐

**Mi ez:** A Runikus Erő ne homogén pool legyen, hanem 3 rúna-típus (Vér/Fagy/Halál) egyensúlyaként töltődjön.

**Hogyan működne:** Minden Halállovag-spell egy rúna-típust fogyaszt és termel (a `ConfiguredSpell` új `resourceProduce` mezővel bővül), a rúnák 0-3 stack-ig HUD-on 3 kis ikon. Egy meghatározott sorrend (pl. Vér→Fagy→Halál 6 mp-en belül) az A16 kombó-infra mintájára egy harmadik, kaszt-exkluzív láncot nyit: bónusz sebzés + rövid önheal.

**Miért jó:** a „rúnamester" fantasy mechanikailag is kifejeződik — mini-rotációs puzzle, ami a Vérlovag/Fagylovag eltérő rúna-prioritását is megkülönbözteti.

**Építőkövek:** ResourceManager Runikus Erő profil (A3), A16 kombó-infra (harmadik lánc), ConfiguredSpell resourceProduce bővítés.

**Buktatók:** 3-rétegű erőforrás UI-lag zsúfolt lehet a HUD oldalsávban — kompakt ikon-sor kell, ne új teljes sáv.

### E16. Halállovag: fagyott vér (Vérlovag + Fagylovag jelzés-kombó)
**Munka:** 🟡 • **Érték:** ⭐⭐

**Mi ez:** Ha egy Fagylovag megfagyaszt egy célpontot, a közeli Vérlovag tank-spelljei ellene extra sebzést kapnak.

**Hogyan működne:** A Fagylovag fagyasztó-spelljei (meglévő freeze-ticks mező) a célponton rövid „megfagyott" markert hagynak (region-lokális `Set<UUID>`). Amíg a marker aktív (kb. 5 mp), a Vérlovag közelharci spelljei ellene +20% sebzést kapnak. A marker törlése a freeze lejáratával egy időben, region-scheduleren.

**Miért jó:** a két spec (tank és DPS) most külön útként létezik — konkrét, párharc-szintű együttműködési okot ad, erősítve a „rúna-testvériség" fantasyt.

**Építőkövek:** freeze-ticks mező (ConfiguredSpell), region-lokális marker-minta, A2 isAlly.

**Buktatók:** entitás-markerek életciklusát gondosan zárni kell (freeze lejárta, mob halála, régióváltás) — a `PlayerStateCleanup`-mintát kell követni, nehogy szivárogjon.

### E17. Halállovag: fagy/vér vizuális aláírás
**Munka:** 🟢 • **Érték:** ⭐

**Mi ez:** A Halállovag spellek kapjanak egységes vér-és-fagy témájú részecske/hang-nyelvet.

**Hogyan működne:** Minden nagy spell becsapódásánál közös „signature" partikel-mix (vér-tónusú Dust + Snowflake) és mély, alacsony pitch-ű csont-kongás hang (vanília Wither Skeleton hang pitch-módosítva). A33 katalizátor-skin mellé ez a hallható/látható „aláírás", konfigurálható globális szorzóval a partikel-sűrűségre.

**Miért jó:** konzisztens érzék-nyelv erősíti a „rúnalovag" azonosságot minden castnál, megkülönböztetve a másik két közelharci kaszttól (Harcos, Paplovag).

**Építőkövek:** ConfiguredSpell particle/sound hook, A33 katalizátor-skin infra.

**Buktatók:** sok egyidejű Halállovag ugyanazon régión partikel-terhelést okozhat — sűrűség-cap kell nagy csatákban.

### E18. Sámán: totem-hálózat
**Munka:** 🟡 • **Érték:** ⭐⭐⭐

**Mi ez:** A `TotemManager` egy aktív totemje helyett 2-3 különböző elem-totem egyidejű állítása.

**Hogyan működne:** Player-onkénti aktív totem-lista bővül max 2 slotra (`classes.yml` → `sámán.totem-slots`), különböző elem-típusú totemek egyszerre állhatnak, de azonosból csak 1. A totemek hatótávolság-átfedésénél (region-lokális távolság-check a `TotemManager` saját tick-jén) „Konvergencia" bónusz (+10% a totem-hatásokra) aktiválódik jelzés-partikellel.

**Miért jó:** a Sámán most csak egy totemet vált — a „totem-kert" építése mélyíti az Elemi/Erősítő/Hullámhívó helyzet-alapú döntéseit (hova rakd a másodikat).

**Építőkövek:** TotemManager, ShamanTotemSpell (totem-minta), `classes.yml` config.

**Buktatók:** több aktív totem entitás régiónként extra Folia-terhelés — ritka totem-tick + szerver-szintű max egyidejű totem-limit is kell (nem csak player-szinten).

### E19. Sámán: szellemvezető (totem + pet szinergia)
**Munka:** 🟡 • **Érték:** ⭐⭐

**Mi ez:** A Sámán totemjei hatnak a közeli párttagok pet-jeire is, kaszt-exkluzív módon.

**Hogyan működne:** A totem AoE-célzási logikájának bővítése: ha egy pet tulajdonosa a Sámán párttagja, az aktív gyógyító/erősítő totem +50% erősséggel rá is kiterjed a sima AoE-hatás mellett (a totem-célzás egy pet-ellenőrző ágat kap).

**Miért jó:** a totem-rendszer most csak játékosokra hat — a pet-kiterjesztés a Sámán „természet szellemeivel beszél" fantasyjét erősíti, és a raidekben lévő pet-eknek is értéket ad.

**Építőkövek:** TotemManager AoE-célzás, PetManager, B4 pet-képességek iránnyal összefér.

**Buktatók:** pet-célzás új ág a totem targeting-ben — alaposan tesztelendő, hogy ne procoljon barátságtalan mobokra és ne törje az A2 isHostile logikát.

### E20. Sámán: elemi egyesülés (4 totem ulti)
**Munka:** 🟡 • **Érték:** ⭐⭐⭐

**Mi ez:** Ritka „signature moment", ha egyszerre mind a 4 elem-totem aktív a közelben (E18 totem-hálózat + több Sámán összjátéka).

**Hogyan működne:** Amikor egyszerre Tűz+Víz+Föld+Levegő totem is aktív látótávon (region-lokális check a `TotemManager` tick-jébe kötve), egyszeri „Elemi egyesülés" trigger sül el: nagy AoE-robbanás (mind a 4 elem partikel-mixe, mennydörgés-hang, rövid slow a körzet hosztilis céljain), 20 perces GLOBÁLIS cooldown-bélyeggel (nem player-alapú, hogy több Sámán ne spamelhesse egymás után).

**Miért jó:** a totem-rendszer csúcspontja, ritka és emlékezetes — jó ok arra, hogy egy raidben/inváziónál több Sámán is legyen, kooperatív „elemi zenekar" élménnyel.

**Építőkövek:** TotemManager tick, A10 telegraph-minta (partikel-jelzés).

**Buktatók:** a 4 totem egyidejű feltétele ritkán teljesül — playteszt kell, hogy valóban elérhető legyen, ne csak elméleti easter egg.

### E21. Szerzetes: csi-áramlás (Ködszövő + Szélfutó kombó)
**Munka:** 🟡 • **Érték:** ⭐⭐

**Mi ez:** A Ködszövő (gyógyító) és Szélfutó (DPS) Szerzetes-specek közötti csapat-kombó a Csi-erőforrásra építve.

**Hogyan működne:** Ha egy Ködszövő heal-spellje eltalál egy Szélfutót, a Szélfutó következő mozgás-alapú spellje (dash/gyorsulás) -50% Csi-költséggel sül el 4 mp-en belül (post-heal buff-tag, E14-mintájára). Fordítva: ha a Szélfutó kombó-finishert land-el, a közeli Ködszövő +15 Csi-t kap azonnal.

**Miért jó:** a Csi (gyors, harcias erőforrás) mindkét specnél központi — a synergy a „kolostori csapatmunka" fantasyt adja, gyógyító+DPS duó taktikai értékét emeli.

**Építőkövek:** ResourceManager Csi-profil (A3), ConfiguredSpell post-cast hook (E14-mintájára), A2 isAlly.

**Buktatók:** post-cast csapat-buffok inflálódhatnak, ha több pár fut egyszerre — cooldown/1-stack limit kell spellenként.

### E22. Pap: ima-visszhang
**Munka:** 🟡 • **Érték:** ⭐⭐⭐

**Mi ez:** Az A3-ban ígért „gyógyítóknál heal-visszatöltés" konkrét megvalósítása.

**Hogyan működne:** Minden sikeres Fegyelem-heal 15% eséllyel „kritikus gyógyítás" (+30% heal-mennyiség, opcionálisan arany szám az A8 sebzés-szám infrán), és kritikus találatnál a Mana-költség 50%-a azonnal visszatérül. Az esély a cast post-hookban görgetve, a mastery-szinttel (SpellMasteryManager) enyhén nő (+1%/rang, max +5%).

**Miért jó:** a nagy, lassan regeneráló Mana-pool healernél gyorsan kiürül — ez a „kegyelmi pillanatok" élményt adja, jutalmazva a folyamatos gyógyítást kiégés nélkül.

**Építőkövek:** ResourceManager Mana-profil + heal-refund hook (A3), SpellMasteryManager, A8 sebzés-szám infra (opcionális vizuál).

**Buktatók:** túl nagy Mana-visszatérítés feleslegessé teheti a többi healer-kasztot — % és eséllyel finomhangolandó.

### E23. Pap: fájdalom-megváltás (Fegyelem + Árnyék kombó)
**Munka:** 🟡 • **Érték:** ⭐⭐

**Mi ez:** Az Árnyék-spec DoT-jai a Fegyelem-spec heal-spelljeivel kombinálva „megváltást" engednek.

**Hogyan működne:** Ha egy Árnyék Pap DoT-tal jelöl egy ellenfelet, egy közeli Fegyelem Pap heal-spellje a jelölt ellenfélen (a DoT-tag egy speciális „megváltás" branch-et nyit, kivételként az A27 okos gyógyítás sima ellenséges-tiltása alól) elszívja a hátralévő DoT-sebzés 20%-át és azonnal párttagra healeli.

**Miért jó:** a Pap két specje (fény és árny) most ellentétes identitású — tematikus hidat ad köztük, két Pap egyidejű jelenlétét jutalmazza raidekben.

**Építőkövek:** DoT-tag infrastruktúra (ConfiguredSpell periodikus sebzés), A27 okos gyógyítás célzás-mintája, A2 isAlly.

**Buktatók:** DoT-elszívás + heal egy lépésben túlzott sustain-t adhat 2 Pap esetén — szigorú cooldown (pl. 12 mp) és kis % szükséges.

### E24. Pap: szent/árny hang-aláírás
**Munka:** 🟢 • **Érték:** ⭐

**Mi ez:** A Fegyelem és Árnyék spec vizuálisan/hallhatóan is elkülönüljön castoláskor.

**Hogyan működne:** Fegyelem-spellek halk kórus-hanggal (vanília hang pitch-emelve) + arany fény-gyűrű partikellel castolnak; Árnyék-spellek fojtott, mély zümmögéssel (Soul Sand/Wither hang lassítva) + lila füst-partikellel. A `ConfiguredSpell` particle/sound mezőin keresztül `classes.yml` → `pap.spec-flavor` kapcsolóval.

**Miért jó:** a castolás pillanata is elárulja a spec-identitást, erősítve a fény/sötét kettősség RPG-fantasyjét — olcsó, tisztán prezentációs réteg.

**Építőkövek:** ConfiguredSpell particle/sound hook, A33 katalizátor-skin iránnyal összhangban.

**Buktatók:** sok egyidejű Pap ugyanott (raidben) hang-kakofóniát okozhat — halkabb, rövidebb hang-mintákkal érdemes indulni.

### E25. Boszorkánymester: rituálé-oltár pakt

→ **Átkerült a lore-kiemelt válogatásba:** [L-lore-kiemelt.md](L-lore-kiemelt.md)

### E26. Orgyilkos/Boszorkánymester: rettegés-lánc
**Munka:** 🟡 • **Érték:** ⭐⭐

**Mi ez:** Kombó a Fantom (Orgyilkos, árnyék/félelem) és az Átok (Boszorkánymester, DoT/curse) spec között — két különböző kaszt specje között.

**Hogyan működne:** Ha egy Átok Boszorkánymester megátkoz egy célpontot (curse-tag, meglévő DoT-infra), és azt 6 mp-en belül egy Fantom Orgyilkos félelem-spellje is eltalálja, a célpont extra 3 mp Slowness+Weakness-t kap, és mindkét caster 4 mp-re +10% sebzés-buffot kap (post-cast hook, curse-tag olvasásával detektálva). PvP-ben az A1 CC-audit szabályai szerint rövidített időtartammal él.

**Miért jó:** a két „sötét" fantasyjű kaszt (árnyék-Orgyilkos, átok-Boszorkánymester) eddig nincs összekötve semmivel — tematikus, párharc-szintű ok az együttjátszásra, mindkét kaszt „rettegés-keltő" identitását erősítve.

**Építőkövek:** DoT/curse-tag infra, A1 CC-audit szabályok (PvP-korlátozás), post-cast hook (E14/E21-mintájára).

**Buktatók:** két külön kaszt közötti keresztre kötött buff nehezebben karbantartható — érdemes egy közös segédosztályba (pl. `CrossSpecSynergyRegistry`) szervezni, ne szóródjon szét a spell-osztályokban.

### E27. Démonvadász: bosszú-fúria
**Munka:** 🟡 • **Érték:** ⭐⭐⭐

**Mi ez:** A Fúria (A3: harcban töltődik) legyen kifejezetten elszenvedett sebzésből is töltődő.

**Hogyan működne:** A ResourceManager gainOnEvent hookja (E13-mintájára) Démonvadásznál a bejövő sebzés minden pontja után +0,5–1 Fúriát ad a sima kiosztott-sebzés-gain mellett, pool-limittel. A Tombolás (DPS) és Bosszú (tank) spec eltérő súllyal használja: Bosszúnál a defenzív spellek olcsóbbá válnak magas Fúriánál (sliding-scale cost-multiplier).

**Miért jó:** a démonvadász-fantasy lényege a fájdalomból nyert erő — ez szó szerint modellezi, jutalmazva a frontvonalban maradást a sebzés kerülgetése helyett.

**Építőkövek:** ResourceManager Fúria-profil + gainOnEvent bővítés (A3, E13-mintájára).

**Buktatók:** sebzésből töltődő erőforrás önromboló spirált indíthat el a balanszban — cap és lassú decay kell tétlenségnél, ne legyen ingyen-erőforrás AFK-farmolással.

### E28. Démonvadász: démon-forma
**Munka:** 🔴 • **Érték:** ⭐⭐⭐

**Mi ez:** Ritka, magas Fúriánál elérhető átmeneti átalakulás — a kaszt „signature moment"-je.

**Hogyan működne:** 100% Fúriánál egyszeri, teljes Fúriát elégető spell 8 mp-re aktiválja a formát: LibsDisguises-híd (a `DruidDisguise` mintájára egy demon-forma) szárny-effekttel, +20% mozgássebesség, minden spell -30% cooldown ez alatt. Cooldown magára a spellre 3 perc, hogy valódi ulti-jellegű maradjon.

**Miért jó:** a Fúria-menedzsment (E27) végpontja egy vizuálisan látványos, erős, de rövid burst-window — jó ok a tudatos Fúria-felhalmozásra csata előtt.

**Építőkövek:** LibsDisguises-híd (DruidDisguise mintája), ResourceManager Fúria-profil (E27), A10 telegraph-mintájú jelzés a csapattársaknak.

**Buktatók:** -30% cooldown minden spellre erős PvP burst-potenciál — kompenzációként a formában lévő játékos kaphat kis (10%) bejövő sebzés-növekedést.

### E29. Démonvadász: vadászösvény
**Munka:** 🟢 • **Érték:** ⭐⭐

**Mi ez:** Kaszt-exkluzív kapcsolat a bűn-rendszerrel (sinner-jelölés) — a Démonvadász „a bűnösök ellen" vadászik.

**Hogyan működne:** Ha a Démonvadász célpontja sinner-jelölésű játékos, minden spellje +10% sebzést kap ellene (a sinner-flag meglévő PDC/manager-állapot, csak egy targeting-feltétel a `ConfiguredSpell` damage-hookban). Emellett egy rövid hatótávú, cooldownos „kiszimatolás" spell (SpectralSight-minta, E6-hoz hasonló infra, sinner-szűréssel) glowing-tag-et rak a közeli sinnerekre.

**Miért jó:** az „igazságosztó" fantasy egy meglévő, alulhasznált rendszerhez (bűn-pont) kapcsolódik — értéket ad a sinner-mechanikának a DARK-passzíván túl is.

**Építőkövek:** bűn-rendszer (sinner-flag), SpectralSight-minta (E6), ConfiguredSpell damage-hook.

**Buktatók:** sinner-célzott bónusz griefinges zaklató célkövetést szülhet — a glowing-jelzés maradjon rövid hatótávú és cooldownos, ne állandó térkép-szintű tracking.

### E30. Sárkányidéző: eszencia-egyensúly
**Munka:** 🟡 • **Érték:** ⭐⭐⭐

**Mi ez:** Az Eszencia legyen kifejezetten két póluson (Tűz/Élet) mozgó skála, nem sima lineáris pool.

**Hogyan működne:** A ResourceManager Sárkányidéző-profilja egy -100..+100 belső skálát kap: Perzselés-spellek a Tűz-pólus felé, Megőrzés-spellek az Élet-pólus felé tolják. Minél inkább az egyik szélén áll a skála, annál olcsóbb (max -20%) az az irány, de a másik drágább (+20%) — dinamikus feszültség. Tétlenségnél lassan (pl. 2 pont/mp) visszaközelít a semleges nullához.

**Miért jó:** az egyetlen erőforrás a kaszt kettős természetét (perzselő pusztítás vs gyógyító megőrzés) fejezi ki mechanikailag — folyamatos döntést kényszerít ki.

**Építőkövek:** ResourceManager Eszencia-profil (A3-bővítés), ConfiguredSpell cost-multiplier hook.

**Buktatók:** dupla-skálás erőforrás UI-lag nehezebben olvasható — egyszerű, két végén színezett gradiens-csík kell, különben a rendszer nem érthető.

### E31. Sárkányidéző: sárkánylehelet
**Munka:** 🔴 • **Érték:** ⭐⭐⭐

**Mi ez:** A kaszt csúcs-pillanata — rövid, látványos „sárkány-forma" magas Eszencia-polaritásnál (E30-ra épül).

**Hogyan működne:** Ha az Eszencia-skála eléri a Tűz-pólus szélét (+100), egyszeri, 5 perces cooldownú spell aktiválható: 5 mp-es csatorna, kúp-alakú tűz-AoE folyamatos kibocsátással (nagy sebzés, a caster nem mozoghat közben), CMD-alapú „sárkányszárny" vizuál (A33 skin-irány) és hosszan elnyúló ordítás-hang. Stun/knockback megszakítja a csatornát.

**Miért jó:** az Eszencia-szélsőség mechanikája (E30) konkrét, emlékezetes jutalmat kap — a kaszt legritkább, legfantáziadúsabb pillanata raid/invázió közepén.

**Építőkövek:** E30 Eszencia-polaritás, A10 telegraph-minta (csatorna-jelzés társaknak), A33 katalizátor-skin.

**Buktatók:** mozdulatlan, csatornázott ulti PvP-ben könnyen megszakítható és „büntető" spellé válhat — elsősorban PvE/raid-kontextusban hangolandó, PvP-ben opcionálisan kikapcsolható.

### E32. Sárkányidéző: sárkánytojás-relikvia

→ **Átkerült a lore-kiemelt válogatásba:** [L-lore-kiemelt.md](L-lore-kiemelt.md)

