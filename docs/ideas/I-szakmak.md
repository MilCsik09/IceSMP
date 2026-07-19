# I) Szakmák, gyűjtögetés és készítés

[← Ötlettár-index](../IDEAS.md)

Jelölés minden tételnél: **Munka** (🟢 kicsi / 🟡 közepes / 🔴 nagy) • **Érték** (⭐–⭐⭐⭐) • `[TOP]` = ajánlott következő kör.

A szakma-rendszer (2 fő szakma + Halász/Szakács mindenkinek, 25. szintű specializáció, recept-könyv, mestermunkák, raritás-létra — ld. `docs/player-guide/08-szakmak.md`) szilárd váz, de a napi ritmusa ma főleg „törd/gyűjtsd/craftold" — nincs benne meglepetés, közösségi súrlódás vagy hosszú távú cél. Az alábbi ötletek erre építenek: mélyítik a gyűjtögetést és a craftolást élményként, kereszt-szakma függőségeket adnak, és gazdasági szerepet a szakma-játékosnak. **Duplikáció-tilalom** — ezekre csak hivatkozunk, NEM ismételjük: A24 (recept-kereső), B26 (rúna-kovácsolás), B37 (nyíl-műhely), B42 (régészet), B46 (trófea-halak), E4 (sörfőzde), D5 (kocsma-italok).

---

## Szakma-mélyítés

### I1. Mestermű-esély — kritikus minőségű craft `[TOP]`
**Munka:** 🟡 • **Érték:** ⭐⭐⭐

**Mi ez:** Minden szakma-craftnál kis eséllyel a kimenet a raritás-létrán egy fokkal feljebb kerül (a szerencse a szakma-szinttel nő).
**Hogyan működne:** A `ProfessionRecipeManager` craft-végrehajtásába egy `mastercraft-chance` config-kulcs (`profession-recipes.yml`, szintenként skálázó, pl. `base + level*0.05%`, cap ~10%), ami az `ItemRarityService.roll(...)` hívás elé egy tier-bumpot told be (crafted tier → egy foka feljebb). Siker esetén action-bar/hang visszajelzés („✨ Mestermunka!"), az item neve elé extra jelző kerülhet. A specializáció (pl. Fegyverkovács) a saját item-családján (fegyverek) dupla eséllyel érvényesüljön.
**Miért jó:** A craftolás jelenleg determinisztikus — ez lottó-izgalmat visz bele minden gyártásnál, ami a szakma-XP grindnek külön motivációt ad, és a piacon „Mestermunka" címkés tárgyaknak felárat teremt.
**Építőkövek:** `ProfessionRecipeManager`, `ItemRarityService` (crafted tier), `ProfessionManager.getLevel`.
**Buktatók:** A bump ne kerülje meg a „szakma-craft sosem Ócska" szabályt; a plusz roll szinkron CraftItemEvent-en fusson, ne blokkoljon.

### I2. Ércfajta-mikrospecializáció 50. szinten
**Munka:** 🟡 • **Érték:** ⭐⭐

**Mi ez:** A meglévő 25. szintű szakma-specializáció (pl. Bányász → Vájármester) fölé egy MÁSODIK, szűkebb választás kerül 50. szinten: egy konkrét ércfajta (vas/arany/gyémánt/netherit) „fókusz", amin extra hozam/XP jár.
**Hogyan működne:** Új PDC-kulcs (`profession_focus_<type>`) a `ProfessionManager`-ben, GUI-választás a Specializáció menü aljában (csak 50. szinttől aktív gomb). A `ProfessionXpListener.onBlockBreak`-ben a fókusz-ércnél +XP% és kis eséllyel dupla drop (a fókuszon kívüli ércek változatlanok). Respec ugyanúgy frakcióvalutáért, mint a 25. szintű specializációnál.
**Miért jó:** Max szintű (50) szakma-játékosoknak ad egy utolsó döntést és okot a fókusz-erőforrás tudatos gyűjtésére — a végjátékban lévő gyűjtögetőknek konkrét célt ad, nem csak „még több XP".
**Építőkövek:** `ProfessionManager` (PDC), `ProfessionGUI`/Specializáció menü, `ProfessionXpListener`.
**Buktatók:** Ne írja felül a specializáció-bónuszt, csak összeadódjon vele; a GUI-gomb csak 50. szint felett jelenjen meg, alatta szürkén „zárolva".

### I3. Napi szakma-megrendelés NPC-től
**Munka:** 🟡 • **Érték:** ⭐⭐⭐

**Mi ez:** Naponta 1-2 NPC-megrendelés szakmánként („Hozz 32 vasat", „Craftolj 3 páncélt") extra XP-ért és kevés valutáért.
**Hogyan működne:** A meglévő napi quest-infrára épített szűk objektíva-generátor: éjfélkor (a `QuestManager` napi resetjéhez igazítva) a `ProfessionManager` aktív szakmáiból véletlen HAVE/CRAFT célt sorsol config-tábla alapján (`profession-orders.yml`: item-pool, mennyiség-tartomány, jutalom-formula szint szerint). A hírnök/céh-mester NPC (FancyNpcs-híd, TALK_TO_NPC minta) adja ki és váltja be; teljesítéskor item-levonás + jutalom. GUI-jelzés a `/profession` menüben.
**Miért jó:** Napi visszatérési okot ad a szakma-játékosoknak (a B1 heti megbízások napi párja), és a gyűjtögetést/craftolást célra irányítja ahelyett, hogy csak XP-farm legyen.
**Építőkövek:** `QuestManager` napi-reset minta, `ProfessionManager`, FancyNpcs-híd, `MessageManager`.
**Buktatók:** A pool ne kérjen boss-only vagy tervrajz-zárolt itemet; a napi reset globális scheduleren fusson, ne per-player logikával drágán.

### I4. Szerszám-karbantartás és -kopás
**Munka:** 🟡 • **Érték:** ⭐⭐

**Mi ez:** A szakma-szerszámok (csákány, fejsze, kovácsolóasztal-eszközök) egy láthatatlan „élesség" mutatót kapnak, ami a raritás-létrával kombinálva finomabb progressziót ad.
**Hogyan működne:** PDC-számláló a szerszámon (`tool_sharpness`, max érték a raritástól függ), ami minden használatnál csökken; alacsony élesség kis eséllyel csökkenti a hozamot/XP-t. Karbantartás: az adott szakma köztes alapanyagával (pl. Vasesszencia) „megélezhető" egy köszörűkőnél (I12 műhely-blokk) vagy a recept-könyvből egy szerviz-recepttel. A szerszám nem törik el, csak fokozatosan gyengül.
**Miért jó:** A meglévő raritás-affixek mellé egy fenntartási ciklust ad, ami a köztes alapanyagoknak (Vasesszencia stb.) állandó keresletet teremt — gazdasági sink finoman.
**Építőkövek:** `ItemRarityService` (raritás → max élesség), `ProfessionXpListener`, item-PDC.
**Buktatók:** Ne legyen büntető jellegű (lassú, észrevehető, de nem drámai hozam-csökkenés); a PDC-írás blokk-eseményenként hot-path, kerülni kell a felesleges NBT-műveletet.

### I5. Craft-sor / kötegelt gyártás
**Munka:** 🟢 • **Érték:** ⭐⭐

**Mi ez:** A recept-könyvből egyszerre N darab craftolható egy gombbal, ahelyett hogy kattintásonként egyet-egyet kellene.
**Hogyan működne:** A `ProfessionRecipeGUI` recept-csempéjén shift-katt = „mennyiség" anvil-input (a quest-builder chat-prompt mintájára), majd a `ProfessionRecipeManager` egy ciklusban végrehajtja a craft-logikát a hozzávaló-készlet erejéig (megáll, ha elfogy vagy elér a megadott számig). Egyetlen összesített üzenet a végén („12× Vaspáncél elkészült").
**Miért jó:** Alapanyag-tömeggyártásnál (pl. napi megrendeléshez, I3) megspórolja a sok kattintást — kis munka, nagy komfort-nyereség a rendszeres szakma-játékosoknak.
**Építőkövek:** `ProfessionRecipeGUI`, `ProfessionRecipeManager`, anvil-input minta (quest builder).
**Buktatók:** Az I1 mestermű-roll a ciklusban is helyesen fusson; a ciklus maradjon a hívó szálon, ne indítson extra scheduler-ugrást itemenként.

## Gyűjtögetés-élmény

### I6. Ritka nagy érc-ér esemény jelzéssel `[TOP]`
**Munka:** 🟡 • **Érték:** ⭐⭐⭐

**Mi ez:** Ritkán, véletlen helyen a világban egy szokatlanul nagy, tömör érc-lelőhely spawnol, ami szerver-szintű halk jelzést kap (nem pontos koordináta, csak irányított hír).
**Hogyan működne:** A kincs-esemény infrájának mintájára egy ütemezett/véletlen trigger (config: gyakoriság, méret-tartomány, érc-típus súlyok) egy kis barlang-régióban tömbszerűen generál érc-blokkokat (region-lokális blokk-írás, `getRegionScheduler`). Felfedezéskor (első blokk-törés a klaszterben) broadcast megy egy közelítő irányban/biome-ban („Nagy ércér jelenlétét érzik a Bányászok északon!") — a pontos hely megtalálása a felfedezés része. A klaszter kimerülésig bányászható, utána eltűnik a jelzés.
**Miért jó:** A sima gyűjtögetést időnkénti versenyfutássá/eseménnyé teszi (ki ér oda előbb), ami PvP-frakciós szerveren extra súrlódást és izgalmat ad, plusz löketet a Bányász-XP-nek.
**Építőkövek:** Kincs-esemény infra (ütemezés+broadcast minta), `ProfessionXpListener` (érc-XP), `TerritoryManager` (opcionális zóna-kizárás).
**Buktatók:** Blokk-generálás region-lokálisan és kis darabokban történjen (chunk-load-lag elkerülése); védett zónába (capital, protected) ne generálódjon.

### I7. Évszakos termények a Bőség-időhöz kötve

→ **Átkerült a lore-kiemelt válogatásba:** [L-lore-kiemelt.md](L-lore-kiemelt.md)

### I8. Fadöntés-fizika Favágónak
**Munka:** 🟡 • **Érték:** ⭐⭐

**Mi ez:** Magasabb Favágó-szinten az egész fatörzs egyszerre kidől egyetlen alsó blokk törésére, ahelyett hogy rönkönként kellene kattintani.
**Hogyan működne:** A `ProfessionXpListener.onBlockBreak` LOG-törésekor (ha a törő aktív Favágó és a szintje elér egy küszöböt) egy kis, felfelé irányuló flood-fill a fatörzs-oszlopon (max N blokk, region-lokális, azonos fa-típusra korlátozva) — minden talált rönköt egyszerre bont, egy összesített XP-jóváírással. Alacsonyabb szinten csak 2-3 blokk „lánc" dől, 25+ szinten a teljes törzs (levelek natural decay marad).
**Miért jó:** A Favágó (a legalacsonyabb XP/darab gyűjtögető a guide szerint) így nemcsak számban, hanem ÉLMÉNYBEN is jutalmat kap a szintlépésért — látványos, azonnal érezhető erő-fantázia, nem csak szám-emelkedés.
**Építőkövek:** `ProfessionXpListener.onBlockBreak`, `ProfessionManager.getLevel`.
**Buktatók:** A flood-fill mérete és a blokk-törés szinkron API-hívásai szigorúan korlátozottak legyenek (max blokkszám config-cap), különben egy nagy fánál TPS-tüskét okoz.

### I9. Fenntartható erdőgazdálkodás — csemete-bónusz
**Munka:** 🟢 • **Érték:** ⭐

**Mi ez:** Ha a Favágó a kivágott fa helyére csemetét ültet vissza, kis XP-bónuszt/egyszeri jutalmat kap.
**Hogyan működne:** `BlockPlaceEvent` figyeli a sapling-elhelyezést egy nemrég vágott famentes területen belül (rövid PDC-jelölt „recent-stump" lokáció-lista, per-chunk, pár perces TTL-lel). Sikeres ültetésnél kis extra Favágó-XP és/vagy egy „Erdész" számláló (bestiárium/statisztika-irányba) — a meglévő Erdész-specializáció bónusza mellé, nem helyette.
**Miért jó:** Olcsó tematikus flair, ami a Favágó szakmának egy „gondoskodó" dimenziót ad a puszta irtás mellé — kis munka, jó illeszkedés a meglévő Erdész-specializáció nevéhez.
**Építőkövek:** `ProfessionXpListener`, `ProfessionManager` (spec-ellenőrzés), rövid TTL-es helyi cache.
**Buktatók:** A „recent-stump" lista méretét/TTL-jét szigorúan korlátozni kell, nehogy memóriát szivárogtasson nagy farmoknál; AFK-spam ültetés-bontás ellen napi limit kell.

### I10. Bányász-radar — közeli érc jelzés
**Munka:** 🟡 • **Érték:** ⭐⭐

**Mi ez:** Magas szintű/specializált Bányász rövid hatótávon halvány jelzést kap a közeli (pár blokkon belüli, még nem látott) ércről.
**Hogyan működne:** Aktiválható képesség (cooldownos, item- vagy parancs-trigger, NEM passzív — hot-path performance miatt), ami a játékos körüli kis kockát (config-sugár, pl. 8 blokk) egyszer átvizsgálja a régió-szálon, és a talált ércblokkokra rövid ideig glowing/partikel-jelzést tesz (SpectralSight-mintára, E6 sólyom-ötlet hasonló elve). A Vájármester specializációnál hosszabb hatótáv/rövidebb cooldown.
**Miért jó:** A barlang-bányászás monoton „vakon kopácsolást" old fel egy aktívan használható, szakma-identitást erősítő eszközzel, ami a specializáció-választásnak is kézzelfogható különbséget ad.
**Építőkövek:** SpectralSight-minta (glowing-jelzés), `ProfessionManager` (spec-ellenőrzés), region-lokális blokkszkennelés.
**Buktatók:** Kizárólag trigger-alapú (nem tick-figyelt) legyen, és a szkennelt terület kicsi/region-lokális maradjon — passzív, folyamatos world-scan Folián TPS-gyilkos lenne.

## Készítés-élmény

### I11. Craft-minijáték — időzített kattintás bónusz-minőségért
**Munka:** 🟡 • **Érték:** ⭐⭐

**Mi ez:** Mestermunka-craftnál (15+ szintű receptek) egy opcionális mozgó-sáv minijáték: jó időzítéssel a raritás-roll felfelé tolódik.
**Hogyan működne:** A `ProfessionRecipeGUI`-ban a mestermunka-recept indításakor egy kis inventory-alapú „mozgó jelző" (slot-ról slotra ugró ikon egy soron, adott ütemben, a katt-időzítés dönt) — nem kötelező, „gyors craft" gomb kihagyja minigame nélkül alap eséllyel. A találat pontossága szorzóként megy az I1 mestermű-esélybe. Tisztán GUI-animáció, szerver-állapotot csak a végeredmény érint.
**Miért jó:** A craftolás passzív menü-kattintgatásból aktív, ügyességi pillanattá válik a legértékesebb tárgyaknál — variálja a szakma-élményt anélkül, hogy kötelezővé tenné.
**Építőkövek:** `ProfessionRecipeGUI`/`ProfessionRecipeHolder` GUI-minta, I1 mestermű-esély.
**Buktatók:** A GUI-animáció inventory-frissítése ne fusson feleslegesen gyakran (update-spam); mindig legyen „kihagyom" opció (A32 hozzáférhetőség-szemlélet).

### I12. Műhely-blokkok — claimre építhető szakma-állomás
**Munka:** 🟡 • **Érték:** ⭐⭐⭐

**Mi ez:** A claimre lerakható, szakma-specifikus „állomás" blokk (köszörűkő, kovács-üllő, alkimista-állvány), ami a közelében craftolóknak tartós bónuszt ad.
**Hogyan működne:** ItemFactory-tárgy (PDC-tag `workstation_type`), lehelyezéskor a claim-tulajdonos ellenőrzésével (`ClaimManager`) regisztrálódik egy blokk-koordinátás map-be. A `ProfessionRecipeManager` craft-végrehajtáskor megnézi, van-e a játékos N blokkon belül saját/párttag-claimjén megfelelő állomás (region-lokális, cache-elt lookup), és ha igen, kis XP/mestermű-esély bónuszt ad. Az I4 karbantartás-recept is csak köszörűkő közelében érhető el.
**Miért jó:** Fizikai okot ad a szakma-műhely megépítésére a claimen — a világban látható, presztízs-jellegű beruházás, ami a passzív craftolást helyhez köti (közösségi látogathatóság, „gyere hozzám craftolni" dinamika).
**Építőkövek:** `ProfessionRecipeManager`, `ClaimManager`, ItemFactory-minta, region-lokális blokk-lookup.
**Buktatók:** A közelség-ellenőrzés cache-elt legyen (ne fusson drága blokk-scan minden craftnál); a blokk eltávolításakor a regisztrációt törölni kell, különben „szellem-bónusz" marad.

### I13. Közös műhely a fővárosban — bérelt craft-pad
**Munka:** 🟢 • **Érték:** ⭐⭐

**Mi ez:** A fővárosban egy admin-épített „közös műhely" kis díjért mindenki számára elérhető I12-szintű bónuszt ad, saját claim/befektetés nélkül.
**Hogyan működne:** Kijelölt territórium-zóna (PROTECTED_CITY altípus vagy egyszerű koordináta-radius), amin belül a craft-végrehajtás automatikusan I12-bónuszt kap, DE minden használatért kis frakcióvaluta-díjat von le (a piaci tranzakció-díj mintájára, sink). Belépéskor/craftkor egyszeri figyelmeztető üzenet a díjról.
**Miért jó:** Azoknak is elérhetővé teszi a műhely-bónuszt, akik nem fektettek saját állomásba (I12) — közösségi találkozóhely (a D2 hirdetőtábla szomszédságában logikus), és állandó, kis pénz-nyelő.
**Építőkövek:** `TerritoryManager` (zóna), I12 bónusz-logika, valuta-levonás (Market/Bank minta).
**Buktatók:** A díj legyen elég alacsony, hogy ne tegye értelmetlenné a saját claim-műhelyt (I12) — a kettő egymás mellett éljen, ne helyettesítse egyik a másikat.

## Szakma-gazdaság

### I14. Szakma-címke az itemen — „Készítette: X"

→ **Átkerült a lore-kiemelt válogatásba:** [L-lore-kiemelt.md](L-lore-kiemelt.md)

### I15. Megrendelő-tábla — a D2 hirdetőtáblához kötve
**Munka:** 🟡 • **Érték:** ⭐⭐

**Mi ez:** A D2 városi hirdetőtábla egy dedikált „szakma-megrendelés" fület kap: játékosok konkrét craft-kéréseket adhatnak fel áras ajánlattal.
**Hogyan működne:** A D2 GUI-minta (`ExchangeBoardManager`) egy új hirdetés-típussal bővül: „kérek: 3× Legendás Kard, fizetek 200"-jellegű bejegyzés, a kérő pénze escrow-ba (MarketManager escrow-logika mintájára). Bármely megfelelő szakmájú/szintű játékos teljesítheti (item leadás a táblánál), a fizetés automatikusan átmegy, apró tranzakciós díjjal (sink). Lejárat X nap után, mint a sima hirdetéseknél.
**Miért jó:** A szakma-piacot a sima piaci listázás (fix ár) mellé egy KÉRÉS-vezérelt réteggel egészíti ki — az egyedi (raritás-rollos) tárgyaknál ez az egyetlen módja, hogy valaki konkrét minőséget rendeljen.
**Építőkövek:** `ExchangeBoardManager`, MarketManager escrow-minta, `ProfessionRecipeManager`.
**Buktatók:** Az escrow-zárolt pénz visszajárjon, ha a hirdetés lejár teljesítetlenül — ugyanaz a csapda, mint a piaci licitzárolásnál, külön teszt kell rá.

### I16. Szakma-céh heti közös cél

→ **Átkerült a lore-kiemelt válogatásba:** [L-lore-kiemelt.md](L-lore-kiemelt.md)

### I17. Fogyóeszköz szakma-buffok — köszörűkő, fenő olaj
**Munka:** 🟢 • **Érték:** ⭐⭐

**Mi ez:** Alkimista/Kovács craftolható, egyszer-használatos fogyóeszközök, amik rövid, NEM harci szakma-bónuszt adnak (pl. +XP% gyűjtögetésre, garantált minimum-raritás egy craftra).
**Hogyan működne:** ItemFactory-tárgy (jobb katt = elfogyasztás, cooldown), ami egy időzített PDC-flaget rak a játékosra (`buff_expiry` timestamp), amit a `ProfessionXpListener` és az I1 mestermű-roll ellenőriz felhasználáskor. A recept-katalógusban külön „fogyóeszköz" kategória.
**Miért jó:** Állandó keresletet teremt a Kovács/Alkimista termékeire a végfelhasználók felől (nem csak egymás közötti kereszt-szakma-forgalom) — tiszta gazdasági sink+kereslet pár, plusz variálja a napi grindet.
**Építőkövek:** ItemFactory-minta, `ProfessionXpListener`, `ProfessionRecipeCatalog` (új kategória).
**Buktatók:** A buff-időablak PDC-timestamp alapú legyen (nem tick-számláló), hogy szerver-restart ne törölje/hosszabbítsa meg véletlenül.

## Kereszt-szakma

### I18. Recept-lánc — a Kovács pengéjét az Alkimista élezi
**Munka:** 🟡 • **Érték:** ⭐⭐⭐

**Mi ez:** A Kovács által kovácsolt „nyers" fegyver egy Alkimista-recepttel (savas edzőfürdő) tovább finomítható — a végeredmény jobb, mint bármelyik szakma önmagában tudná.
**Hogyan működne:** Új recept-kategória: a Kovács „nyers penge" köztes tárgyat gyárt (PDC-tag `refinable`), amit az Alkimista főzőállvány-receptje bemenetként fogad (`ProfessionRecipeManager` bemenet-ellenőrzésbe egy PDC-tag-alapú feltétel kerül a sima Material mellé). A finomított végeredmény az I1 mestermű-eséllyel dupla eséllyel rollol, ha a két lépés 24 órán belül történt (a „frissesség"-bónusz, timestamp PDC-n).
**Miért jó:** Első valódi horizontális függőség két szakma között — két különböző játékos (vagy egy dupla-szakmás) együttműködésére ösztönöz, ami a piaci forgalmat és a közösségi kereskedést élénkíti.
**Építőkövek:** `ProfessionRecipeManager` (PDC-alapú bemenet-feltétel), I1 mestermű-esély, timestamp-PDC.
**Buktatók:** A köztes „nyers penge" ne legyen piacon eladható csali végtermékként (egyértelmű lore-jelzés, hogy félkész); a 24 órás ablak szerver-idő, ne kliens-oldali.

### I19. Recept-lánc — friss termény a Szakácsnak
**Munka:** 🟢 • **Érték:** ⭐⭐

**Mi ez:** A Gyógynövényész frissen betakarított terménye (pár percen belül) extra bónuszt ad a Szakács receptjéhez, ha ő maga vagy egy párttársa használja fel.
**Hogyan működne:** Betakarításkor a terményre egy rövid TTL-ű `harvested_at` PDC-timestamp kerül. A Szakács `onFurnaceExtract`/craft-eseményénél, ha a bemenő alapanyag timestamp-je friss (config: pl. 10 percen belül), a kimenő étel-buff (pl. I17-hez hasonló mechanika) erősebb, vagy egyszerűen extra Szakács-XP jár.
**Miért jó:** Kis, olcsó összekötés, ami tematikusan indokolja, miért éri meg a Gyógynövényésznek ÉS a Szakácsnak is egy csapatban/párban dolgozni — mikro-szintű kereszt-szakma szinergia nagy rendszertervezés nélkül.
**Építőkövek:** `ProfessionXpListener` (harvest + furnace hook), item-PDC timestamp.
**Buktatók:** A TTL rövid legyen, hogy ne lehessen tömegesen előre felhalmozott „friss" alapanyaggal kijátszani; a timestamp-ellenőrzés olcsó (egyetlen long-összehasonlítás).

### I20. Halász csali-kotyvalék — Alkimista kereszt-termék
**Munka:** 🟢 • **Érték:** ⭐

**Mi ez:** Az Alkimista főzhet speciális „csali-kotyvalékot", amit a Halász a horgászatnál használva megnöveli a ritka/trófea hal (B46) esélyét.
**Hogyan működne:** Új Alkimista-recept (a bájital-infrán, PDC-tag `bait_potion`), amit a Halász horgászbotra alkalmazva (jobb katt kombinálás vagy enchant-asztal-szerű GUI) egy időkorlátos `bait_active` PDC-flaget ad a boton. A `PlayerFishEvent`-ben ez a flag módosítja a loot-táblát a B46 trófea-eséllyel.
**Miért jó:** A halászat (mindenki által ismert másodlagos szakma) is bekerül a kereszt-szakma hálóba, és az Alkimistának egy niche, de tematikusan illő terméke lesz — apró, de a szakma-gazdaság szövetét sűríti.
**Építőkövek:** Alkimista bájital-recept, `PlayerFishEvent` (B46 loot-tábla módosítás), item-PDC.
**Buktatók:** Csak a B46 loot-táblára hasson, ne a sima halászat XP-jére (ne legyen öngerjesztő XP-spirál); a bot-kombinálás GUI-ja kövesse a meglévő enchant/reforge mintát.

## Progresszió

### I21. Szakma-presztízs — újrakezdés max szint után
**Munka:** 🟡 • **Érték:** ⭐⭐

**Mi ez:** 50. szintű szakma önkéntesen „presztízselhető": vissza 1. szintre, cserébe tartós, kicsi, additív bónusz (pl. +1% XP-hozam presztízs-szintenként, cap-pel).
**Hogyan működne:** A `ProfessionManager`-be egy `prestige_<type>` PDC-számláló, GUI-megerősítéssel (a respec mintájára, visszavonhatatlan figyelmeztetéssel). Presztízselés XP-t/szintet nullázza, de a `getLevel`/XP-számítás mellé egy állandó szorzót ad a presztízs-szinttől függően. A recept-könyv jelzi a presztízs-csillagokat a szakma neve mellett (vizuális presztízs, mint a B22 címek).
**Miért jó:** A max szintet elért, „kifogyott" szakma-játékosoknak ad okot a további grindre — a WoW-paragon és a B20 presztízs-irány szakma-oldali megfelelője, olcsóbb munkával, mert a meglévő XP/szint-plumbingra épül.
**Építőkövek:** `ProfessionManager` (PDC+szorzó), Specializáció/Recept-könyv GUI (jelzés).
**Buktatók:** A cap-et szigorúan kell tartani (ne legyen végtelenül összeadódó, kibillentő XP-szorzó); a recept-tudás megmaradása presztízselés után legyen egyértelmű, dokumentált szabály (javasolt: megmarad, csak a szint/XP nullázódik).

### I22. Ritka recept-lapok loot-forrásból

→ **Átkerült a lore-kiemelt válogatásba:** [L-lore-kiemelt.md](L-lore-kiemelt.md)

### I23. Szakma-mester NPC-k — edzés és felgyorsítás
**Munka:** 🟢 • **Érték:** ⭐

**Mi ez:** Fővárosi szakma-mester NPC-k (Bányász-mester, Kovács-mester stb.), akiknél kis frakcióvalutáért egyszeri XP-löketet vagy egy alacsonyabb szintű recept azonnali megtanulását lehet „megvenni".
**Hogyan működne:** FancyNpcs-híd + dialógus (A23 polish-minta), parancs-alapú vásárlás (`/profession train <típus>`), ár a `ProfessionManager.getLevel`-lel skálázó (minél magasabb a szint, annál drágább a löket — visszaszorítja a pénzért-szintezést kevés szintre). Napi limit a kihasználás ellen.
**Miért jó:** Kis pénz-nyelő (sink) és egy barátságos „gyorsítósáv" azoknak, akik lemaradtak egy szakmában (pl. szakma-váltás után) — nem helyettesíti a grindet, csak finoman gyorsítja.
**Építőkövek:** FancyNpcs-híd, `ProfessionManager`, valuta-levonás.
**Buktatók:** A napi limit szigorú legyen, különben a fizetős szintezés kiüresíti a gyűjtögetés/craftolás egész célját — csak kis, kiegészítő szerep maradjon.

### I24. Szakma-ranglista havi verseny
**Munka:** 🟢 • **Érték:** ⭐

**Mi ez:** Havi versenyciklus szakmánként: ki termelte/craftolta a legtöbbet — a `/leaderboard` (A22) egy szakma-specifikus kategóriája, havi resettel.
**Hogyan működne:** `ProfessionXpListener` eseményein egy havi (nem összesített) számláló, ami hónap váltáskor pillanatképet ment (a heti krónika B15 mintájára) és nullázódik. A hónap végi TOP 3 szakmánként broadcast + kis kozmetikai jutalom (B22 cím-jelölt: „Hónap Bányásza").
**Miért jó:** Az A22 ranglista-bővítés szakma-specifikus, időkorlátos verziója — havonta újra versenyezhető cél, ami a szakma-grindet közösségi látványossággá teszi olcsó munkával.
**Építőkövek:** `ProfessionXpListener`, StatsManager/leaderboard infra (A22), B22 cím-rendszer.
**Buktatók:** A havi számláló és az összesített szint-XP két külön dolog — ne keveredjenek össze, különben a végleges szint-progresszió torzul a versenyszámlálóval.

### I25. Napi „hozott alapanyag" bónusz-ablak
**Munka:** 🟢 • **Érték:** ⭐

**Mi ez:** Az első X gyűjtögetés/craft naponta minden szakmánál kicsivel több XP-t ad — lassan csökkenő görbével, hogy az AFK-farm ne legyen vonzóbb, mint a rendszeres, mértékkel játszó szokás.
**Hogyan működne:** `ProfessionXpListener`-ben egy napi, per-player-per-szakma esemény-számláló (nem PDC, elég memóriában napi reset-tel — a napi quest reset ugyanígy működik). Az első N esemény (config, pl. 30) teljes XP-t ad, utána fokozatosan csökkenő, de sosem nulla szorzó a nap hátralévő részére.
**Miért jó:** A „napi belépés + egy kis szakma-munka" szokást jutalmazza a maratoni egy-ülős AFK-farmolás helyett — enyhe anti-exploit réteg, ami közben nem büntető, csak lassító.
**Építőkövek:** `ProfessionXpListener`, napi memória-számláló (napi reset minta).
**Buktatók:** A számláló per-player memória-map legyen, és a `PlayerSessionCleanupListener`-ben takarítva, hogy ne szivárogjon; a csökkenő szorzó soha ne érjen nullára.

---

Összesen: **25 ötlet** (I1–I25).
