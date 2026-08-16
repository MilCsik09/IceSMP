package hu.taliann.icesmp.gui;

/** Human-facing, gameplay-specific descriptions for admin config icons. */
public final class ConfigMenuHelp {

    private ConfigMenuHelp() {
    }

    public static String describe(final String key, final String label) {
        if (key.startsWith("factions.passives.")) {
            return passiveDescription(key);
        }
        if (key.startsWith("dev-items.csodalatos_bingulus.rarity-weights.")) {
            final String rarity = key.substring(key.lastIndexOf('.') + 1).replace('_', ' ');
            return "A Bingulus jutalomsorsolásában a(z) " + rarity
                    + " ritkaság relatív súlya. Nem százalék: a többi súlyhoz viszonyítva nő vagy csökken az esély.";
        }
        if (key.startsWith("dev-items.csodalatos_bingulus.pity.")) {
            final String rarity = key.split("\\.")[3];
            return "Ennyi sikertelen Bingulus-sorsolás után garantál legalább "
                    + rarity + " ritkaságú jutalmat. Kisebb érték gyakoribb garanciát jelent.";
        }
        if (key.startsWith("territory.protection.regen.")) {
            return blockRegenDescription(key);
        }
        if (key.startsWith("territory.protection.rules.")
                && key.endsWith(".allow-explosions")) {
            final String zone = key.substring("territory.protection.rules.".length(),
                    key.length() - ".allow-explosions".length());
            return "A(z) " + zone + " zónában akkor dönt a maradandó robbanáskár engedélyezéséről, "
                    + "amikor az adott zóna blokkregenerációja ki van kapcsolva. Bekapcsolva vanilla kár, kikapcsolva teljes védelem.";
        }

        return switch (key) {
            case "health.enabled" -> "Bekapcsolja a kasztok eltérő maximális életerő-profilját és annak karbantartását. Kikapcsolva a játékosok vanilla életerőn maradnak.";
            case "health.display.normalize" -> "Csak resource-pack nélküli fallbackhez normalizálja a vanilla szívsor hosszát. A packos survival HUD mindig a valódi current/max HP-t és százalékot mutatja.";
            case "health.ooc-regen.enabled" -> "Engedélyezi a plugin saját, harcon kívüli életerő-regenerációját a combat tag lejárása után.";
            case "health.ooc-regen.delay-seconds" -> "Sebzés vagy harci állapot után ennyi másodpercet vár a saját HP-regeneráció megkezdéséig.";
            case "health.ooc-regen.percent-per-tick" -> "A saját regeneráció egy ciklusban a maximális HP ekkora százalékát tölti vissza. Nagy érték nagyon gyors gyógyulást okoz.";
            case "health.ooc-regen.min-food" -> "Legalább ennyi food level szükséges a harcon kívüli regenerációhoz. 0 esetén éhezve is működhet.";
            case "health.scale-heals" -> "A fix gyógyításokat a célpont maximális HP-jához arányosítja, hogy a magas HP-s kasztok ne gyógyuljanak aránytalanul keveset.";
            case "health.scale-heals-cap" -> "A maximális szorzó, ameddig a maxHP-alapú gyógyításskálázás növelheti egy heal erejét.";

            case "factions.tax.enabled" -> "Engedélyezi az időszakos állampolgári adóbeszedést a nem mentesített frakciók tagjaitól.";
            case "factions.tax.rate-percent" -> "A játékos saját frakcióvalutás bankegyenlegének ennyi százalékát próbálja beszedni minden adóciklusban.";
            case "factions.tax.minimum-amount" -> "Beszedésenként legalább ennyi adó keletkezik akkor is, ha a százalékos összeg kisebb. Fedezet hiányában hátralék lesz.";
            case "factions.tax.max-arrears" -> "Egy játékos frakciónként felhalmozódó adóhátralékának felső határa. 0 a hátralék kikapcsolására használható a runtime szabály szerint.";
            case "factions.tax.evasion-strikes" -> "Ennyi egymást követő, teljesen sikertelen plafonon ragadt beszedés után jár adócsalási bűnpont. 0 kikapcsolja a strike-büntetést.";
            case "factions.tax.interval-minutes" -> "Két automatikus adóbeszedés közti idő percben. Módosításkor az adóütemező élőben újraindul az új periódussal.";
            case "ferry.enabled" -> "Globálisan engedélyezi a konfigurált kompútvonalak használatát és díjfizetését.";
            case "ferry.default-fee" -> "Azoknak a kompútvonalaknak az alap viteldíja, amelyeknél nincs külön útvonal-specifikus ár.";
            case "factions.council.enabled" -> "Engedélyezi a NEUTRAL Vének Tanácsának választását és gazdasági jogosultságait.";
            case "factions.council.withdraw-daily-cap" -> "A teljes Tanács közös napi kasszakivételi kerete, nem tanácstagonkénti limit.";
            case "factions.council.market-week-minutes" -> "A Tanács által kihirdetett Vásár-hét kedvezményes időablakának hossza percben.";

            case "motd.enabled" -> "Bekapcsolja az IceSMP natív szerverlista-MOTD és eseményfüggő variáns rendszerét.";
            case "motd.selection-mode" -> "A MOTD-variáns kiválasztása: time esetén időablak szerint, random esetén véletlenszerűen történik.";
            case "motd.rotation-seconds" -> "Random vagy rotációs módban ennyi másodpercenként válthat másik MOTD-variánsra.";
            case "motd.exclude-vanished-from-online-count" -> "A vanish állapotú stafftagokat kihagyja a szerverlistán kijelzett online játékosszámból.";
            case "motd.icons.mode" -> "A szerverlista ikonforrása: nincs ikon, alapikon, variánshoz kötött ikon vagy véletlen ikon.";

            case "world-events.spawn-rules-enabled" -> "A közös esemény-spawn védelem főkapcsolója. Bekapcsolva az események figyelembe veszik a claim, territory, WorldGuard és vízszabályokat.";
            case "world-events.orchestration.enabled" -> "Megakadályozza, hogy természetes sorsolásból egyszerre több nagy PvE esemény induljon. Az admin force indítás továbbra is átmehet.";
            case "world-events.blood-moon.enabled" -> "Engedélyezi a Vérhold természetes indulását és a hozzá kapcsolódó mob-/passzív felülírásokat.";
            case "world-events.blood-moon.chance-percent" -> "A Vérhold esélye egy jogosult eseményellenőrzési próbán. Nem percenkénti közvetlen esély, hanem a manager próbájára vonatkozik.";
            case "world-events.world-boss.enabled" -> "Engedélyezi a világboss természetes eseményét, spawnját és jutalmazását.";
            case "world-events.world-boss.chance-percent" -> "A világboss indulási esélye egy jogosult eseményellenőrzési próbán.";
            case "world-events.invasion.enabled" -> "Engedélyezi a frakcióterületet támadó inváziós esemény természetes indulását.";
            case "wild-hunt.enabled" -> "Engedélyezi a Vad Hajsza eseményt és annak személyes résztvevői jutalmait.";
            case "meteor.enabled" -> "Engedélyezi a meteor-esemény természetes sorsolását és világba helyezett becsapódását.";
            case "treasure-events.enabled" -> "Engedélyezi az elrejtett kincs eseményt és a megtalálói jutalomablakot.";
            case "ambient-events.enabled" -> "Engedélyezi a kisebb, hangulati világeseményeket, amelyek nem foglalják a nagy eseménykaput.";

            case "territory.doom-gate.sin-exempt" -> "Bekapcsolva a Kárhozat Kapuja zónában elkövetett játékosölés nem ad bűnpontot.";
            case "territory.doom-gate.entry-grace-seconds" -> "Belépés után ennyi másodperc PvP-védelem jár a Kapuban. Aki támad, a saját védelmét azonnal elveszíti.";
            case "territory.mob-rules.doom-gate.bonus-levels" -> "Ennyi extra mob-szint adódik a Doom Gate területén létrejövő mobokra a többi skálázás fölé.";
            case "territory.mob-rules.doom-gate.no-daylight-burn" -> "Megakadályozza, hogy a Kapuban spawnolt nappal égő mobok a napfénytől meggyulladjanak.";
            case "territory.mob-rules.doom-gate.no-zombification" -> "Megakadályozza a Kapu mobjainak dimenzió miatti piglin/hoglin zombifikációját.";
            case "nether-portal.allow-creation" -> "Engedélyezi vagy tiltja az új vanilla Nether-portálok meggyújtását. A már kialakított lore-kapu kezelése ettől elkülönülhet.";
            case "mob-scaling.zone-ramp.enabled" -> "Bekapcsolja a biztonságos zónák peremétől távolodva növekvő mob-szint rámpát.";
            case "mob-scaling.zone-ramp.blocks-per-level" -> "Ennyi blokkonként nő egy szinttel a zónaperemtől számolt mob-bónusz. Kisebb érték gyorsabb nehezedést jelent.";

            case "kill-rewards.afk-block" -> "Megakadályozza, hogy AFK-jelölt áldozat vagy AFK-farmolás után kill-jutalom járjon.";
            case "kill-rewards.exclude-spawner-mobs" -> "A spawnerből származó mobokat kizárja a plugin XP-, pénz- és extra loot jutalmaiból.";
            case "kill-rewards.exclude-minions" -> "A játékosokhoz tartozó minionok megölését kizárja a kill-jutalom rendszerből.";
            case "kill-rewards.require-survival" -> "Csak survival módban lévő gyilkos kaphat plugin által kezelt ölési jutalmat.";

            case "factions.war-window.enabled" -> "Engedélyezi az időzített RED–BLUE hadiablakot, amelyben a két frakció közti ölés szentesített és nem bűn.";
            case "factions.war-window.points-per-kill" -> "Ennyi szezonpontot kap a gyilkos frakciója egy érvényes hadiablakos ölésért.";
            case "factions.war-window.daily-point-cap" -> "Egy játékos naponta legfeljebb ennyi hadiablak-pontot termelhet a frakciójának.";
            case "factions.war-window.per-victim-cooldown-minutes" -> "Ugyanaz az áldozat ennyi percig nem ad újabb hadiablak-pontot ugyanannak a gyilkosnak.";

            case "factions.whisper.enabled" -> "Engedélyezi a Suttogó státuszt, a gyanúgyűjtést, leleplezést és kapcsolódó kedvezményeket.";
            case "factions.whisper.suspicion-threshold" -> "A gyanúpont, amelynél a Suttogó lelepleződik és a büntetési folyamat elindul.";
            case "factions.whisper.betrayal-suspicion" -> "Ennyi gyanút ad egy árulásként kezelt Suttogó-akció.";
            case "factions.whisper.accuse-suspicion" -> "Ennyi gyanút ad a tanú-vád vagy kapcsolódó feljelentési művelet.";
            case "factions.whisper.decay-minutes" -> "Ennyi percenként csillapodik a felhalmozott gyanú a manager szabályai szerint.";
            case "factions.whisper.exposure-sins" -> "Lelepleződéskor ennyi bűnpont kerül a Suttogóra.";
            case "factions.whisper.expose-broadcast" -> "Bekapcsolva a Suttogó lelepleződése szerver- vagy közösségi üzenetben is megjelenik.";
            case "factions.whisper.night-undead-truce" -> "Éjszaka esélyalapú élőhalott-békét ad az aktív Suttogónak, a magasabb precedenciájú harci kivételek nélkül.";
            case "cultists.whisper-loot-rolls" -> "Ennyi külön kultista-loot sorsolást kap a jogosult Suttogó az esemény jutalmazásakor.";
            case "factions.whisper.blackmarket-discount-percent" -> "A Suttogó feketepiaci vásárlásainak százalékos árkedvezménye.";

            case "factions.food-duty.enabled" -> "Engedélyezi a frakció saját ételéhez kötött honvágy-kötelezettséget és elmulasztási debuffot.";
            case "factions.food-duty.grace-hours" -> "Frakcióválasztás vagy utolsó megfelelő étkezés után ennyi óráig nem jár honvágy-büntetés.";
            case "factions.food-duty.check-minutes" -> "Ennyi percenként ellenőrzi a rendszer a frakció-élelmezési kötelezettséget.";
            case "factions.food-duty.debuff-seconds" -> "Egy sikertelen ellenőrzéskor adott honvágy/éhség debuff időtartama másodpercben.";

            case "signature.csakany.bonus-drop-chance" -> "A signature csákány extra nyersanyagdobásának 0–1 közötti esélye. 0.25 = 25%.";
            case "signature.horgaszbot.bonus-drop-chance" -> "A signature horgászbot extra fogásának 0–1 közötti esélye. 0.25 = 25%.";
            case "signature.bankbetet.value" -> "A signature bankbetét beváltásakor jóváírt valuta névértéke.";
            case "signature.szarvas.cooldown-seconds" -> "A Szellemszarvas signature képesség két használata közti cooldown másodpercben.";
            case "signature.agyar.damage-mult" -> "Az Agyar signature támadás kimenő sebzésének szorzója. 1.5 = 50%-kal nagyobb sebzés.";
            case "signature.jegvert.damage-mult" -> "A Jégvért viselőjére érkező releváns sebzés megtartott része. 0.8 = 20% csökkentés.";
            case "itemization.stats.ability-power-percent-per-point" -> "Egy érvényes, felszerelt Itemization 2.0 tárgy egy Képességerő pontja ennyi százalékkal növeli a cast nagyságát.";
            case "itemization.stats.ability-power-max-percent" -> "A felszerelt Itemization 2.0 tárgyakból összesen kapható Képességerő százalékos plafonja.";
            case "itemization.loot.enabled" -> "A combat loot generikus random-affix gear sorát releváns forrásnál authored Itemization 2.0 tárgyra cseréli.";
            case "itemization.loot.max-personalization-multiplier" -> "A level, class, spec, build, slot és forrás együtt sem emelheti egy jelölt súlyát e szorzó fölé; legfeljebb 1.5.";
            case "itemization.loot.history-window" -> "Az utolsó ennyi authored combat drop rarity/slot/family/template adata vesz részt a soft-diverzitásban. Profilban marad reconnect és restart után is.";
            case "itemization.loot.repeated-template-penalty" -> "Minden közelmúltbeli azonos template enyhén osztja az újabb példány súlyát; nem tiltja ki a tárgyat.";
            case "itemization.loot.repeated-category-penalty" -> "Minden közelmúltbeli azonos rarity, slot vagy family enyhén csökkenti a kategória súlyát.";
            case "itemization.loot.unseen-category-boost" -> "A diverzitási ablakban nem látott rarity, slot vagy family kategóriánként kapott enyhe súlybónusz; nem garantál Mitikust.";

            case "relics.enabled" -> "Globálisan engedélyezi a relikvia-regisztert, relikviaparancsokat, triggerek és tulajdonkezelés aktív használatát.";
            case "relics.inactivity.expiry-days" -> "Ennyi inaktív nap után szabadulhat fel egy normál, birtokolt relikvia a konfigurált lejárási szabály szerint.";
            case "relics.inactivity.lost-expiry-days" -> "Ennyi nap után szabadul fel a halálkor elveszettként jelölt passzív relikvia, ha a tulajdonos nem idézi vissza.";
            case "relics.passive-death.mode" -> "A passzív relikvia halálkori sorsa: reclaim = elveszettként visszaidézhető, keep = megmarad, drop = tárgyként kiesik.";
            case "relics.wings.faction-locked-pickup" -> "Csak a megfelelő frakció tagja veheti fel a frakcióhoz kötött szárnyrelikviát.";
            case "relics.pvp-transfer.enabled" -> "Engedélyezi, hogy a támogatott fegyverrelikvia PvP-halálkor a győzteshez kerüljön.";

            case "dev-items.csodalatos_bingulus.auto-restore" -> "Ha a Bingulus eltűnik vagy érvénytelen helyre kerül, a rendszer automatikusan visszaállítja a tulajdonoshoz.";
            case "dev-items.csodalatos_bingulus.reward-interval-seconds" -> "Ennyi másodpercenként próbál a Bingulus új jutalmat adni a jogosult tulajdonosnak.";

            case "memory-shards.xp-amount" -> "Egy XP-beváltáskor ennyi kaszt-XP-t ad az Emlékszilánk-rendszer.";
            case "memory-shards.costs.xp" -> "Ennyi Emlékszilánk fogy egy XP-csomag beváltásakor.";
            case "memory-shards.costs.talent" -> "Ennyi Emlékszilánk fogy egy talentpont beváltásakor.";
            case "memory-shards.costs.spec" -> "Ennyi Emlékszilánk szükséges a konfigurált specializációs kapu vagy emlékbeváltás teljesítéséhez.";

            case "world-events.season.enabled" -> "Engedélyezi a frakciók közti szezonpont-ligát, lejárást és szezonzáró folyamatot.";
            case "world-events.season.length-days" -> "Egy új szezon teljes hossza napokban. Folyamatban lévő szezon lejárati számítására is hat a manager szabályai szerint.";
            case "world-events.season-finale.top2-window-hours" -> "A szezonzárás utolsó, két vezető frakcióra fókuszáló nagydöntő-ablakának hossza órában.";
            case "world-events.season-finale.top2-point-multiplier" -> "A nagydöntőben a vezető két frakció jogosult pontforrásaira alkalmazott szorzó.";
            case "community-goals.season-points" -> "Ennyi szezonpont jár egy közösségi cél sikeres teljesítéséért.";
            case "corruption.season-points" -> "Ennyi szezonpont jár egy rontásgóc szabályos megtisztításáért.";
            case "honor-duel.season-points" -> "Ennyi szezonpont jár különböző frakciójú felek érvényes becsületpárbajának győztes frakciójához.";
            case "spy.season-points" -> "Ennyi szezonpont jár egy valódi idegen területen befejezett kémküldetésért.";

            case "corruption.enabled" -> "Engedélyezi a terjedő rontásgóc eseményt, a korrupt mobokat és a megtisztítási folyamatot.";
            case "corruption.interval-minutes" -> "Két természetes rontásnyitási próba közti minimum idő percben.";
            case "corruption.chance-percent" -> "Egy jogosult rontásnyitási próbán a góc létrejöttének százalékos esélye.";
            case "corruption.mob-cap" -> "Egy aktív rontásgóc által egyszerre fenntartható korrupt mobok maximális száma.";
            case "corruption.purge-kills-required" -> "Ennyi jogosult korrupt mob megölése szükséges a góc megtisztításához.";
            case "corruption.dark-bias.chance-percent" -> "Annak esélye, hogy a rontás a DARK terület peremét részesítse előnyben a normál helyválasztás helyett.";
            case "corruption.dark-bias.min-edge-distance" -> "DARK-peremhez torzított spawn esetén legalább ennyi blokk távolságot tart a határtól.";
            case "corruption.dark-bias.max-edge-distance" -> "DARK-peremhez torzított spawn esetén legfeljebb ennyi blokk távolságot enged a határtól.";

            case "dark-undead.enabled" -> "Engedélyezi a DARK területek plugin által fenntartott ambient élőhalott népességét.";
            case "dark-undead.scope" -> "capital esetén csak DARK fővárosban, all esetén minden megfelelő DARK zónában tartható fenn a népesség.";
            case "dark-undead.max-population" -> "Az egyszerre élő, plugin által jelölt DARK ambient undead maximális száma.";
            case "dark-undead.spawn-interval-seconds" -> "Két népességpótlási próbálkozás közti idő másodpercben.";
            case "dark-undead.min-level" -> "A plugin által létrehozott DARK ambient undead legalacsonyabb alap-szintje.";
            case "dark-undead.max-level" -> "A plugin által létrehozott DARK ambient undead legmagasabb alap-szintje.";
            case "dark-undead.lifespan-seconds" -> "Ennyi másodperc után takarítható el egy ambient undead, ha addig nem halt meg más okból.";
            case "rare-variant.chance-percent" -> "Annak százalékos esélye, hogy egy támogatott mob ritka variánsként jöjjön létre.";
            case "rare-variant.xp-multiplier" -> "A ritka variáns után járó kaszt-/mob-XP szorzója.";
            case "rare-variant.soul-chance-multiplier" -> "A ritka variáns lélekszilánk- vagy lélekkődobási esélyére alkalmazott szorzó.";

            case "guilds.enabled" -> "Engedélyezi a frakción belüli céhek létrehozását, tagságát, XP-jét és parancsait.";
            case "guilds.create-cost" -> "Egy új céh alapításakor levont frakcióvalutás költség.";
            case "guilds.base-max-members" -> "Az új vagy alacsony szintű céh alap taglétszám-korlátja.";
            case "guilds.max-members-cap" -> "A céh fejlődéssel sem lépheti túl ezt az abszolút taglétszámot.";
            case "guilds.xp-per-quest" -> "Ennyi céh-XP jár egy tag jogosult questteljesítéséért.";
            case "profession-weekly.enabled" -> "Engedélyezi a szakmához kötött heti közös célt és annak jutalmazását.";
            case "profession-weekly.reward-xp" -> "A heti szakmai cél jogosult résztvevőinek adott szakma-XP jutalom.";
            case "profession-weekly.min-contribution" -> "Legalább ennyi személyes hozzájárulás szükséges a heti jutalom átvételéhez.";

            case "honor-duel.enabled" -> "Engedélyezi a beleegyezéses becsületpárbajt és a hozzá tartozó bűn-/pontkivételeket.";
            case "honor-duel.window-seconds" -> "A párbaj elfogadása után ennyi másodpercig érvényes a szabályos elégtételi ablak.";
            case "honor-duel.weekly-limit" -> "Egy játékos hetente legfeljebb ennyi jutalmazott vagy bűnkezelő párbajt teljesíthet.";
            case "spy.enabled" -> "Engedélyezi a rövid kémálcát és az idegen területi felderítő küldetést.";
            case "spy.duration-seconds" -> "Ennyi másodpercig tart egy sikeresen aktivált kémálca.";
            case "spy.cooldown-minutes" -> "Két kémálca-aktiválás közti várakozási idő percben.";

            case "sit.enabled" -> "Globálisan engedélyezi az IceSMP natív, ülés-only rendszerét.";
            case "sit.click-to-sit" -> "Engedélyezi, hogy megfelelő blokkra kattintva a játékos parancs nélkül leüljön.";
            case "sit.empty-hand-only" -> "Kattintásos ülés csak üres főkézzel indítható, így nem zavarja a tárgyhasználatot.";
            case "sit.max-click-distance" -> "A kattintásos ülés legnagyobb engedett távolsága blokkban.";
            case "sit.allow-unsafe-locations" -> "Engedélyezi az ülésbiztonsági ellenőrzés által veszélyesnek ítélt helyeken való leülést is.";
            case "sit.stand-up.damage" -> "Sebzés elszenvedésekor automatikusan felállítja az ülő játékost.";
            case "sit.stand-up.sneak" -> "Lopakodás gomb használatakor automatikusan felállítja az ülő játékost.";
            case "sit.stand-up.block-break" -> "Blokktörés megkezdésekor automatikusan felállítja az ülő játékost.";

            case "market.allow-relic-listing" -> "Engedélyezi, hogy relikviának felismert tárgyat a játékosok a piactéren listázzanak.";
            case "market.relic-auction.recommended-min-bid" -> "Relikviaaukciónál megjelenített vagy validált ajánlott minimum licitösszeg.";
            case "city-guards.enabled" -> "Engedélyezi a konfigurált városi őrök spawnját és waypoint-alapú járőrözését.";
            case "city-guards.step-seconds" -> "Ennyi másodpercenként indít a manager egy új őrmozgási lépést.";
            case "city-guards.day-step-blocks" -> "Nappal egy járőrlépésben legfeljebb ennyi blokkot halad az őr a következő waypoint felé.";
            case "city-guards.night-step-blocks" -> "Éjjel egy járőrlépésben legfeljebb ennyi blokkot halad az őr; nagyobb érték gyorsabb riadótempó.";

            case "party.enabled" -> "Globálisan engedélyezi a party létrehozást, meghívást, közös célzást és kapcsolódó megosztásokat.";
            case "party.max-size" -> "Egy party maximális taglétszáma a vezetővel együtt.";
            case "party.invite-expire-seconds" -> "Ennyi másodperc után jár le egy meg nem válaszolt partymeghívó.";
            case "party.share-radius" -> "Ekkora blokksugáron belül számít közelinek egy párttag az XP- és személyesloot-megosztáshoz.";
            case "party.xp-share" -> "Bekapcsolva a közeli párttagok közt fejenként oszlik meg a támogatott mobölés XP-je.";
            case "party.personal-loot" -> "Bekapcsolva a támogatott események minden közeli jogosult párttagnak külön jutalmat adnak.";
            case "party.block-friendly-fire" -> "Megakadályozza, hogy párttagok közvetlenül vagy lövedékkel sebezzék egymást.";
            case "party.hud-enabled" -> "Megjeleníti a közeli party tagjait és életállapotát a HUD oldalsávján.";

            case "claims.enabled" -> "Globálisan engedélyezi a natív 3D claim létrehozást, védelmet és parancsokat.";
            case "claims.quick-size" -> "A /claim gyorsfoglalás négyzetének oldalhossza blokkban.";
            case "claims.default-height" -> "Új claim létrehozási Y-szintje fölött ennyi blokk tartozik alapból a védett térfogathoz.";
            case "claims.default-depth" -> "Új claim létrehozási Y-szintje alatt ennyi blokk tartozik alapból a védett térfogathoz.";
            case "claims.free-columns" -> "Játékosonként összesen ennyi claim-oszlop foglalható díjmentesen.";
            case "claims.column-cost" -> "Az ingyenes keret feletti minden új 1×1 oszlop egyszeri, elégő frakcióvalutás ára.";
            case "claims.max-columns-per-player" -> "Egy játékos összes claimjének együttes maximális alapterülete oszlopban.";
            case "claims.area-max-columns" -> "Egy pos1/pos2 kijelölésből létrehozható claim maximális alapterülete oszlopban.";
            case "claims.y-extend-step" -> "Egy magasítás vagy mélyítés művelet ennyi blokkal növeli a claim függőleges sávját.";
            case "claims.y-extend-cost-per-column" -> "A függőleges bővítés ára: claim oszlopszám × ez az érték minden lépésnél.";
            case "claims.protect-containers" -> "Idegen játékos nem nyithatja meg a claimen belüli ládát, hordót, kemencét és más konténert.";
            case "claims.protect-explosions" -> "A claimet érő robbanások blokklistáját védi; aktív BlockRegen mellett látványosan visszagyógyítja a claimblokkot.";
            case "claims.protect-fire" -> "Megakadályozza a claimelt blokkok meggyulladását, leégését és a tűz rájuk terjedését.";
            case "claims.protect-terrain" -> "Megakadályozza a külső folyadék- és dugattyúműveleteket, amelyek idegen claimhatárt kereszteznének.";
            case "claims.block-in-protected-zone" -> "Megtiltja a claim létrehozását a védett territory-típusokban, például fővárosban.";
            case "claims.block-in-territory" -> "Bekapcsolva még a normál frakcióterritóriumban sem lehet személyes claimet létrehozni.";
            case "claims.block-in-protected-region" -> "Megtiltja a claim létrehozását WorldGuard vagy más támogatott védett régióban.";
            case "claims.raid-lootable" -> "Engedélyezi, hogy aktív raid regisztrált támadói az ellenséges claim konténereit megnyissák, de ne törjék.";
            case "claims.border.show-seconds" -> "A /claim show határ-részecskéit ennyi másodpercig frissíti.";
            case "claims.border.radius" -> "A /claim show a játékos körül ekkora chunk-sugárban keres megjelenítendő claimhatárokat.";
            case "claims.border.enter-notice" -> "Claimhatár átlépésekor action baron jelzi, hogy saját, idegen vagy vad területre érkezett a játékos.";

            case "chat.format-enabled" -> "Engedélyezi az IceSMP natív LuckPerms-prefix/suffix és frakciószín alapú chat-formázását.";
            case "chat.name-faction-color" -> "A chatben a beszélő nevét az explicit frakciójának színére festi; vendégnél az alap színt használja.";
            case "spell-vfx.enabled" -> "Bekapcsolja a formázott, palettázott spell-részecskéket. Kikapcsolva a könnyebb legacy puff visszajelzés fut.";
            case "spell-vfx.max-points" -> "Egy spell-VFX geometria legfeljebb ennyi részecskepontot rajzolhat. Kisebb érték csökkenti a látványt és a terhelést.";

            case "donation-chest.enabled" -> "Engedélyezi a közös, ingyenes adományláda használatát és GUI-ját.";
            case "donation-chest.max-items" -> "A közös adományláda egyszerre tárolható élő tételeinek teljes szerver-szintű plafonja.";
            case "donation-chest.max-per-player" -> "Egy adományozó egyszerre legfeljebb ennyi még el nem vitt tételt tarthat a ládában. 0 korlátlan.";

            default -> label + " beállítása közvetlenül a hozzá tartozó IceSMP-rendszer működését módosítja. "
                    + "Az alapérték és az aktív override külön látható az ikonon.";
        };
    }

    private static String passiveDescription(final String key) {
        if (key.equals("factions.passives.enabled")) {
            return "A teljes frakciópasszív-rendszer főkapcsolója. Kikapcsolva egyik frakció sem kapja meg a felsorolt védelem- vagy mobbéke-előnyeit.";
        }
        if (key.endsWith(".enabled")) {
            return "Az adott frakció vagy passzív alrendszer külön kapcsolója. A globális passzív főkapcsolónak is aktívnak kell lennie.";
        }
        if (key.endsWith("-damage-multiplier") || key.endsWith(".damage-multiplier")
                || key.endsWith(".duration-multiplier")) {
            return "A releváns bejövő sebzés vagy hatásidő megtartott része. 0 = teljes immunitás, 0.5 = felezés, 1 = vanilla, 1 fölött sebezhetőség.";
        }
        if (key.endsWith("natural-exhaustion-save-chance")
                || key.endsWith("target-cancel-chance")) {
            return "0–1 közötti esély. 0.25 = 25%. Minden jogosult eseménynél külön sorsolás történik.";
        }
        if (key.endsWith("retaliation-seconds")) {
            return "Provokáció után ennyi másodpercig marad aktív a játékos–mob páros megtorlási állapota, amely felülírja a békét.";
        }
        if (key.endsWith("alert-nearby-radius")) {
            return "Provokációkor ekkora blokksugáron belül riaszthatók a közeli, nem markerelt ambient undead társak.";
        }
        if (key.endsWith("break-on-damage")) {
            return "Bekapcsolva a játékos által okozott sebzés azonnal megtöri a spontán mobbékét és megtorlási ablakot indít.";
        }
        if (key.endsWith("ignore-stare-aggro")) {
            return "Megakadályozza az Enderman puszta szemkontaktusból induló spontán agresszióját; az ütés és scripted célzás továbbra is működik.";
        }
        if (key.endsWith("affect-icesmp-fire-magic")) {
            return "Meghatározza, hogy a RED tűzvédelme az IceSMP TUZ iskolájú spellsebzésre is alkalmazódjon-e.";
        }
        if (key.endsWith("affect-scripted-combat-fire")) {
            return "Bekapcsolva a RED tűzpasszív a boss-, event- és más scripted harci tűzre is hat; kikapcsolva ezek magasabb precedenciájúak.";
        }
        if (key.endsWith("disabled-during-blood-moon")) {
            return "Bekapcsolva a Vérhold idején az adott undead-béke teljesen kikapcsol, és a Vérhold harci szabálya nyer.";
        }
        return "Az adott frakciópasszív részletes viselkedését szabályozza a documented precedenciasorrend és harci kivételek mellett.";
    }

    private static String blockRegenDescription(final String key) {
        return switch (key) {
            case "territory.protection.regen.enabled" -> "A látványosan kirobbanó, drop nélküli blokkok pillanatkép-alapú visszaépítésének főkapcsolója. Kikapcsolva a zónák saját explosion-szabálya dönt.";
            case "territory.protection.regen.delay-seconds" -> "A robbanás vagy mobrombolás után ennyi másodpercig marad nyitva a kráter, mielőtt a blokkok visszaépítése megkezdődik.";
            case "territory.protection.regen.restore-interval-ticks" -> "Két visszaépítési menet közti idő Minecraft tickben. 20 tick = 1 másodperc. Élő módosításkor egy tick alatt átáll az új ütemre.";
            case "territory.protection.regen.blocks-per-pass" -> "Egy visszaépítési menetben legfeljebb ennyi esedékes blokk kerül vissza. Az interval értékkel együtt adja a blokk/másodperc tempót.";
            case "territory.protection.regen.support-grace-seconds" -> "A támaszt igénylő blokk, például homok vagy fáklya legfeljebb ennyi ideig vár a tartóblokkra, utána a sor beragadásának elkerülésére mindenképp próbál visszaépülni.";
            case "territory.protection.regen.max-recaptures" -> "Ugyanaz a blokkpozíció egy recapture ablakban legfeljebb ennyiszer kerülhet újra regenerációs sorba. A végtelen fizikai hurkokat fogja meg.";
            case "territory.protection.regen.recapture-window-seconds" -> "Az az időablak, amelyen belül ugyanazon blokk újrarombolásait a max-recaptures számláló összeadja.";
            case "territory.protection.regen.physics-shield-enabled" -> "Védi a nyitott krátert és a frissen visszaépült blokkokat a folyadék-, gravitációs és blokkfizikai lánckároktól.";
            case "territory.protection.regen.physics-shield-seconds" -> "A visszaépített blokk ennyi másodpercig marad fizikai pajzs alatt. 0 esetén a visszaépítés után azonnal újra él a vanilla fizika.";
            case "territory.protection.regen.player-break.siege-enabled" -> "Aktív raid célzónájában engedi a regisztrált harcosnak a védett, nem tile-entity blokk drop nélküli bontását és későbbi visszaépítését.";
            case "territory.protection.regen.player-break.siege-delay-seconds" -> "Ostrom alatt kézzel lebontott blokk ennyi másodperc után válik visszaépíthetővé.";
            case "territory.protection.regen.player-break.always-enabled" -> "Ostromon kívül is engedi a védett zónák nem tile-entity blokkjainak drop nélküli, ideiglenes bontását. Jelentősen gyengíti a safe-zone védelmet.";
            case "territory.protection.regen.player-break.always-delay-seconds" -> "Az always kézi rombolással lebontott blokk visszaépítési késleltetése másodpercben.";
            case "territory.protection.regen.restore-effects-enabled" -> "Minden visszahelyezett blokknál anyaghoz illő lerakáshangot és kis porfelhőt játszik le.";
            case "territory.protection.regen.tile-entity-explode" -> "Bekapcsolva láda, shulker, tábla, fej, zászló, spawner és más TileState teljes struktúra/NBT pillanatképpel kirobbanhat és pontosan visszatérhet. Kikapcsolva sértetlen marad.";
            case "territory.protection.regen.debris-enabled" -> "Engedélyezi a blokkok kliensoldalról is látható falling-block törmelékmását. A valódi blokk állapotát ettől függetlenül a journal védi.";
            case "territory.protection.regen.debris-percent" -> "A sikeresen regenerációra fogott blokkok ekkora százaléka kap repülő törmelék-entitást. A blokk-visszaépítésre nincs hatással.";
            case "territory.protection.regen.debris-lifetime-seconds" -> "A repülő törmelék legfeljebb ennyi másodpercig marad entitás, majd blokkporrá válik és eltűnik.";
            case "territory.protection.regen.debris-launch-power" -> "A robbanás középpontjától kifelé mutató alap radiális kezdősebesség. A vízszintes és függőleges szorzók ezt külön tovább módosítják.";
            case "territory.protection.regen.debris-horizontal-multiplier" -> "Csak a törmelék X/Z irányú radiális sebességét szorozza. 0 = nincs kifelé repülés, 1 = eredeti, 2 = kétszeres vízszintes lökés.";
            case "territory.protection.regen.debris-vertical-multiplier" -> "A radiális Y-komponenst és a természetes 0.35–0.55-ös emelést együtt szorozza. 0 lapos röppályát, nagy érték magas ívet ad.";
            case "territory.protection.regen.debris-horizontal-spread" -> "Véletlen, körkörös X/Z szórást ad minden törmelékhez. 0 teljesen radiális, nagyobb érték kaotikusabb oldalirányú szétszóródás.";
            case "territory.protection.regen.debris-extra-upward-velocity" -> "Fix extra felfelé irányuló kezdősebességet ad a már kiszámolt röppályához. A vertical multiplier után kerül hozzáadásra.";
            case "territory.protection.regen.debris-gravity-enabled" -> "Bekapcsolva a törmelék falling blockként ívben visszaesik; kikapcsolva megtartja a kezdősebességét a lifetime lejártáig.";
            default -> {
                if (key.startsWith("territory.protection.regen.zones.")) {
                    final String zone = key.substring("territory.protection.regen.zones.".length());
                    yield "Meghatározza, hogy a(z) " + zone + " területen a robbanás és támogatott mobrombolás látványosan megtörténjen-e, majd a blokkok visszagyógyuljanak. Kikapcsolva a zóna allow-explosions szabálya dönt.";
                }
                yield "A BlockRegeneration működésének egy részletét szabályozza; az ikonon látható alapérték és tartomány szerint azonnal alkalmazódik.";
            }
        };
    }
}
