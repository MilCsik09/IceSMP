# M) Bootstrap / Loader ötlettár

*A Paper bootstrap- és loader-szint kihasználásának terve. Ami már ÉL: 8 mágia-iskola
damage-type (spellek), 8 egyedi enchant (7 signature + Rúnavért) rider-effektekkel, a
loader dokumentált Maven-resolver képessége. A szabály mindenre: **lore-horgony + valódi
funkció kötelező** — se enchant, se registry-bejegyzés "csak mert jól néz ki" alapon.*

**Korlátok (fontos!):** a kliens hardcode-olja a bájital-effekteket (MobEffect), az
itemeket és a blokkokat — ezek bootstrapből SEM regisztrálhatók; arra a szerver-oldali
pszeudo-effekt minta marad (honvágy, doom-grace). A festmény/zene/trim/banner
regisztrálható, de a TEXTÚRA/HANG a resource packből jönne — ezek RP-függő tételek.

## Resource pack NÉLKÜL is működő ötletek

### M1. Iskolánkénti rúna-tekercsek (enchant-sor bővítés) `[TOP]`
**Munka:** 🟡 • **Érték:** ⭐⭐⭐
A Rúnavért generikus; iskolánkénti counter-tekercsek jöhetnek mellé: **Fagyűző Rúna**
(fagymágia), **Lángűző Rúna** (tűz), **Fényrúna** (árnyék ellen — a szent véd az árnyéktól!),
**Csendrúna** (káosz ellen). Rúnaírnok-receptek, iskola-alapanyagokkal (pl. Fagyűző =
packed ice + ametiszt). A motor kész (result.enchant + school-counter lookup) — enchant-
regisztráció + SpellSchool.resistEnchantId bővítés. Lore: a Mélység Népe rúna-öröksége.

### M2. Kárhozat-lehelet damage-type (zóna-DoT)
**Munka:** 🟡 • **Érték:** ⭐⭐
`icesmp:kapu_lehelete` damage-type: a Kárhozat-zóna közepén (a Kapu körül) lassú,
környezeti DoT tickel — a halál-üzenete: „X-et elnyelte a Kapu lehelete". A Rúnavért ez
ellen NEM véd (nem iskola) — csak a Főnixtoll/Fagypáncél kombó vagy a gyors láb. A zóna
közepe így valóban a legveszélyesebb pont (K7 mélyítés).

### M3. Csontszámvevő „adósság-érintése" damage-type
**Munka:** 🟢 • **Érték:** ⭐
Az adócsalás-leleplezésnél / a Számvevők büntetésénél tematikus damage-type
(`icesmp:szamvevo_erintese`) — pl. a plafonon ragadt hátralékos játékost belépéskor 1 HP
„emlékeztető" éri, saját halál-üzenettel (sosem halálos — csak dráma). Configgal.

### M4. Suttogó-jel enchant (rejtett átok)
**Munka:** 🟡 • **Érték:** ⭐⭐
A leleplezett Suttogó fegyverére kerülő átok-enchant („A Királynő Jele") — el nem
távolítható (curse-jelleg), kis árnyék-iskolás bónusz undead ellen, de viselése
azonosítja a Kitaszítottat. K9-mélyítés; lore: a paktum nyoma.

### M5. Loader: adatbázis-réteg előkészítés
**Munka:** 🔴 • **Érték:** ⭐⭐ (skálázásnál ⭐⭐⭐)
Ha a YAML-perzisztencia szűkös lesz (sok játékos, aukció/piac-történet), a loaderrel
runtime húzható HikariCP + SQLite/H2 driver — shadelés nélkül. Előfeltétel: a
PersistentStore interfész mögé SQL-backend. Csak akkor, ha tényleg kell.

### M6. Bootstrap-parancsok (LifecycleEvents.COMMANDS)
**Munka:** 🟢 • **Érték:** ⭐
A parancs-regisztráció átterelhető bootstrap-lifecycle-re (a reload-biztos, modern út).
A mostani kódból-regisztráció működik — csak akkor éri meg, ha a Paper deprecálja a
jelenlegi utat.

## Resource pack UTÁN élesíthető ötletek (RP-függők)

### M7. Frakció-banner-minták
Regisztrált banner-minta frakciónként (főnix, jégsárkány, szarvas, csont-korona) — a
fővárosok, raid-zászlók, király-jelvények vizuális nyelve. RP: minta-textúrák.

### M8. A Mélység Népe festményei
Painting-variánsok a kódex jeleneteivel (az Első Csend, a Hetedik Vérháború, a Fa) —
a lore a falakon, felfedezhető műkincsként (B42 régészet-szinergia). RP: képek.

### M9. Lore-zene (jukebox-song registry)
A „Suttogás dala", frakció-himnuszok lemezként — ritka loot / feketepiac-áru. RP: ogg.

### M10. Frakció-trimek
Armor-trim minta/anyag frakciónként (pl. Csontveret-trim a DARK-nak) — a frakció-identitás
a páncélon. RP: trim-textúrák.
