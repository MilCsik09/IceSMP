package hu.taliann.icesmp.gui;

/** Exact, gameplay-facing explanations for the operational admin config menus. */
public final class OperationalConfigHelp {

    private OperationalConfigHelp() {
    }

    public static String describe(final String key, final String fallbackLabel) {
        return switch (key) {
            // AFK
            case "afk.afk-after-seconds" -> "Ennyi másodperc valódi játékos-inaktivitás után jelöli a rendszer automatikusan AFK-nak. Az önkéntes /afk ettől függetlenül azonnal kapcsolható.";
            case "afk.block-rewards" -> "Bekapcsolva az AFK állapotot figyelő jutalomrendszerek nem fizetnek az inaktív játékosnak. Ez a farmolás elleni globális AFK-kapu.";

            // HUD és tablista
            case "hud.enabled" -> "A teljes IceSMP játékos-HUD főkapcsolója: az oldalsávot, esemény-bossbarokat és kapcsolódó élő kijelzéseket kapuzza. Kikapcsoláskor a meglévő HUD-elemek a következő frissítésben eltűnnek.";
            case "hud.icesmp-hud.enabled" -> "A saját resource-packes IceSMP HUD főkapcsolója. Csak sikeresen betöltött packnál aktív; különben a natív fallback marad meg.";
            case "hud.icesmp-hud.editor.enabled" -> "Productionben alapból kikapcsolt admin-kapu a /hud edit élő, játékosonként izolált előnézetéhez. A permissiont nem helyettesíti.";
            case "hud.icesmp-hud.layout.x-offset-pixels" -> "A teljes first-party HUD globális vízszintes eltolása pixelben. A komponensek saját relatív eltolását a /hud edit kezeli.";
            case "hud.icesmp-hud.layout.y-offset-pixels" -> "A teljes first-party HUD globális shaderes függőleges eltolása pixelben. Pozitív érték lefelé mozgat; tartományon kívül biztonságos alapérték lép életbe.";
            case "hud.icesmp-hud.layout.safe-margin-pixels" -> "Biztonsági távolság a képernyő jobb szélétől pixelben. Nagyobb érték balra húzza a teljes first-party HUD-ot.";
            case "hud.icesmp-hud.layout.scale" -> "A teljes first-party HUD globális mérete a buildkor generált shader-variánsok egyikén. A /hud edit komponensméretei ehhez képest relatívak.";
            case "hud.icesmp-hud.survival.refresh-ticks" -> "A HP, páncél, étel és levegő külön gyors frissítési periódusa. Kisebb érték simább, de több játékos-scheduler feladatot jelent; task-újraütemezéshez restart kell.";
            case "hud.icesmp-hud.survival.armor-maximum" -> "A survival mini-sáv 100%-os páncélértéke. A kiírt tényleges armor ezt meghaladhatja, de a sáv ilyenkor telített marad.";
            case "hud.icesmp-hud.survival.layout.x-offset-pixels" -> "A bottom-center survival panel vízszintes eltolása. Nem módosítja a külön jobb felső class HUD helyét.";
            case "hud.icesmp-hud.survival.layout.y-offset-pixels" -> "A bottom-center survival panel függőleges eltolása. Pozitív érték lefelé, negatív felfelé mozgatja.";
            case "hud.icesmp-hud.survival.layout.scale" -> "A survival panel külön méretszorzója; a class HUD globális és komponensméretétől független.";
            case "hud.sidebar-enabled" -> "Paperen az IceSMP scoreboard-oldalsávját, Folián a compact class bossbar fallbacket kapcsolja. A sikeresen betöltött first-party HUD automatikusan elrejti a fallbacket.";
            case "hud.tablist-enabled" -> "Az egyszerű, régi frakciószínű tabnév-fallback kapcsolója. Csak akkor van hatása, ha a natív tablist.enabled ki van kapcsolva.";
            case "hud.low-hp-vignette.enabled" -> "Engedélyezi az alacsony életerőnél megjelenő vörös képernyőszéli vészjelzést. Csak vizuális, nem módosít sebzést vagy maximális HP-t.";
            case "hud.low-hp-vignette.threshold-percent" -> "E HP-százalék alatt jelenik meg a vörös alacsony-életerő vignetta. Nagyobb érték korábban, kisebb érték csak kritikus állapotban figyelmeztet.";
            case "hud.refresh-ticks" -> "A HUD-oldalsáv teljes frissítési periódusa tickben. Kisebb érték gyorsabb kijelzést, de több scoreboard-munkát jelent; módosításkor a task élőben újraütemeződik.";
            case "hud.dynamic.combat-focus" -> "Harc közben elrejti a nem létfontosságú HUD-szekciókat, és csak a combat-visible listában szereplő részeket tartja meg. A lista továbbra is YAML-ban szerkeszthető.";
            case "hud.dynamic.combat-grace-seconds" -> "Az utolsó adott vagy kapott találat után ennyi másodpercig marad aktív a letisztított harci HUD-nézet.";
            case "hud.dynamic.rotating-line" -> "Engedélyezi az esemény-, szezon- és napi kihívás információit váltogató dinamikus HUD-sort.";
            case "hud.dynamic.rotation-seconds" -> "A dinamikus információs HUD-sor ennyi másodpercenként vált a következő elérhető információra.";
            case "hud.profile.enabled" -> "Találat után eseményvezérelten, periodikus világ-szkennelés nélkül mutatja a célpont feje alatt a HP-t, játékosnál pedig igény szerint a class resource-t.";
            case "hud.profile.lifetime-ticks" -> "Az utolsó találat után ennyi tickig követi a célpontot a HP/resource kijelzés; minden új találat újraindítja az időablakot.";
            case "hud.profile.show-player-resource" -> "A játékos célpontok HP-ja mellett megjeleníti az aktuális class-resource nevét és jelenlegi/maximális értékét. Mobokra nincs hatása.";
            case "hud.profile.scale" -> "A célpontot követő HP/resource szöveg méretszorzója. A képernyő-HUD méretétől és a resource packtól független.";
            case "hud.profile.height-offset" -> "A célpont saját magasságából számított HP/resource sor függőleges eltolása a nametag alatti finom pozicionáláshoz.";
            case "hud.profile.view-range" -> "A TextDisplay kliensoldali renderelési tartományának szorzója. Alap attacker-only láthatóság mellett sem teszi nyilvánossá az adatot.";
            case "tablist.enabled" -> "A natív IceSMP tablista teljes főkapcsolója: header/footer, formázott tabnevek, nametagek, rendezés és pingkijelzés csak ennek engedélyével működik.";
            case "tablist.refresh-ticks" -> "A natív tablista diffelt frissítési periódusa tickben. Kisebb érték gyorsabb név/ping frissítést, de több kliens- és scoreboard-munkát jelent; élőben újraütemeződik.";
            case "tablist.sweep-every-refresh" -> "Minden ennyiedik tablista-frissítés végez drágább teljes takarítást a kilépett vagy átrendeződött bejegyzéseken. Nagyobb érték ritkább söprést jelent.";
            case "tablist.header-footer.enabled" -> "Engedélyezi a tablista konfigurált fejlécét és láblécét, beleértve az animációs és online/ping tokeneket.";
            case "tablist.tab-names.enabled" -> "Engedélyezi a LuckPerms prefix/suffix és frakciószín alapján formázott neveket a tablistában.";
            case "tablist.nametags.enabled" -> "Engedélyezi a játékosok feje fölötti scoreboard-team prefixet, suffixet, névszínt és rangsorrendet.";
            case "tablist.nametags.war-colors" -> "Aktív raid alatt a szemben álló hadviselő fél tagjait piros relációs színnel jelöli a néző tablistájában és nametagjén.";
            case "tablist.playerlist-ping.enabled" -> "Megjeleníti a játékosok pingjét a tablista PLAYER_LIST pontszámoszlopában.";
            case "tablist.ping-colors.good" -> "E milliszekundumos ping alatt zöldnek számít a {ping} token. Az ok küszöbnél mindenképpen kisebbnek kell maradnia.";
            case "tablist.ping-colors.ok" -> "E milliszekundumos ping alatt sárga, fölötte piros a {ping} token. A good küszöb alatti érték továbbra is zöld.";

            // Petek és minionok
            case "pets.summon.night-only" -> "Bekapcsolva a rituális ghúl- és démonidézés csak éjszaka hajtható végre. A már aktív társakat nem távolítja el.";
            case "pets.summon.bonus-levels" -> "Ennyi extra szintet kap a nehezebb, rituáléval idézett társ az alap companion-szintje fölé.";
            case "pets.summon.tier2-level" -> "E companion-szinttől használja a rituális társ a második fejlődési formáját vagy tierjét.";
            case "pets.summon.tier3-level" -> "E companion-szinttől használja a rituális társ a harmadik, legerősebb fejlődési formáját vagy tierjét.";
            case "pets.summon.heart-drop-chance" -> "Egy jogosult élőhalott ölésének 0–1 közötti esélye Nyughatatlan Szív dobására. 0.03 háromszázalékos esélyt jelent.";
            case "pets.summon.seal-drop-chance" -> "Egy jogosult boszorka vagy illager ölésének 0–1 közötti esélye Démon-pecsét dobására. 0.06 hatszázalékos esélyt jelent.";
            case "pets.stable.maximum" -> "A Vadmester Profile v2-ben tárolt Istállójának férőhelye. A GUI legfeljebb kilenc helyet jelenít meg, a befogás pedig commitkor is ellenőrzi a plafont.";
            case "pets.max-active" -> "A spell- és shard-rendszer rövid életű minionjainak egyidejű plafonja; a kiválasztott tartós companion nem ezt a keretet használja.";
            case "pets.talent-health-share" -> "A gazda maximális-életerő talentbónuszának ekkora 0–1 közötti hányada kerül át a társ maximális HP-jára.";
            case "pets.equipment.drop-chance" -> "Jogosult szörnyölésenként ekkora 0–1 közötti eséllyel esik Társvért, amíg a játékos társa még nincs felszerelve.";
            case "pets.equipment.armor-bonus" -> "A Társvért felszerelése ennyi közvetlen armor attribútumbónuszt ad a companionnak.";
            case "pets.equipment.health-bonus" -> "A Társvért felszerelése ennyi maximális életerőt ad a companionnak az egyéb szint- és talentbónuszok fölött.";
            case "pets.companion.max-level" -> "A névvel és XP-vel fejlődő companion elérhető maximális szintje. A már magasabb mentett szint kezelését a betöltési validáció korlátozza.";
            case "pets.companion.death-respawn-seconds" -> "A társ halála után ennyi másodpercig nem idézhető újra. Ez a halál játékmeneti tétjének időablaka.";
            case "pets.companion.base-xp" -> "Az első companion-szintlépés alap XP-igénye, amelyre a szintenkénti növekmény ráépül.";
            case "pets.companion.increment-per-level" -> "Minden következő companion-szint ennyivel növeli a következő szinthez szükséges XP-t.";
            case "pets.companion.xp-per-kill" -> "Egy jogosult, gazda vagy társ által teljesített ölés ennyi companion XP-t ad.";
            case "pets.companion.health-per-level" -> "A companion minden megszerzett szintje ennyi maximális életerőt ad a társ alapértékéhez.";
            case "pets.companion.damage-per-level" -> "A companion minden megszerzett szintje ennyi sebzést ad a plugin által vezérelt támadáshoz.";
            case "pets.companion.follow-distance" -> "Ha a társ ennél messzebb kerül a gazdától, a követőrendszer visszateleportálja. Ez a végső lemaradásvédelem.";
            case "pets.companion.follow-start-distance" -> "E távolság fölött kezdi a companion gyalog vagy pathfinderrel követni a gazdát; a teleportküszöbnél kisebbnek érdemes maradnia.";
            case "pets.companion.spawn-search-radius" -> "Idézéskor legfeljebb ekkora vízszintes sugárban keres a rendszer már betöltött, Folia-lokális, stabil és szabad állóhelyet.";
            case "pets.companion.spawn-vertical-range" -> "A játékos lábmagasságához képest ennyi blokkot keres felfelé és lefelé biztonságos companion-spawnhoz.";
            case "pets.companion.tick-ticks" -> "A companion követési és harci driverének periódusa tickben. Kisebb érték gyorsabb reakciót, de több entitásmunkát jelent; a task élőben újraütemeződik.";
            case "pets.companion.attack-damage-base" -> "A plugin által vezérelt companion-támadás alap sebzése, amelyhez a szintenkénti damage-per-level bónusz hozzáadódik.";
            case "pets.companion.attack-reach" -> "Ekkora blokktávolságból tekinti találónak a companion a pluginvezérelt közelharci támadását.";
            case "pets.companion.attack-cooldown-ticks" -> "Két pluginvezérelt companion-ütés között legalább ennyi ticknek kell eltelnie.";
            case "pets.companion.chase-speed" -> "A companion célpont felé történő pathfinderes üldözésének sebességszorzója. Túl nagy érték természetellenes mozgást okozhat.";
            case "pets.companion.aggro-range" -> "A gazda körül ilyen távolságból keres automatikusan ellenséges mobot a védelmező companion.";
            case "pets.companion.leash-range" -> "A companion elengedi azt a harci célpontot, amely ennél messzebb kerül. Az aggro-range értékénél nagyobbnak érdemes lennie.";

            // Piac és árfolyam
            case "currency.exchange-rate" -> "A fix valutaváltási árfolyam, amely csak kikapcsolt dinamikus árfolyam mellett használatos.";
            case "currency.exchange-fee-percent" -> "Minden valutaváltáskor levont százalékos díj. Dinamikus árfolyam mellett is érvényes pénznyelő és oda-vissza spekulációs fék.";
            case "currency.soul-drop.enabled" -> "Engedélyezi a Csontveretként használt lélekkő dropját a megfelelő magas szintű skálázott mobokból.";
            case "currency.soul-drop.min-mob-level" -> "Csak legalább ilyen skálázott szintű mob lehet jogosult lélekkő dobására.";
            case "currency.soul-drop.chance-percent" -> "Jogosult mob ölésenként a lélekkőcsomag százalékos dobási esélye.";
            case "currency.soul-drop.max-amount" -> "Egy sikeres lélekkő-drop legnagyobb sorsolható darabszáma.";
            case "currency.soul-drop.daily-cap" -> "Játékosonként naponta legfeljebb ennyi lélekkő eshet. 0 esetén nincs napi plafon.";
            case "currency.soul-drop.dark-undead-drops" -> "Bekapcsolva a DARK játékos élőhalott öléséből is kaphat lélekkövet; kikapcsolva a saját békés mobnépük farmolása nem fizet.";
            case "currency.economy-event.enabled" -> "Engedélyezi a véletlen, ideiglenes valutakeresleti sokkokat, amelyek egy valuta értékét átmenetileg módosítják.";
            case "currency.economy-event.check-interval-minutes" -> "Ennyi percenként fut a gazdasági sokk és konjunktúra közös sorsolási drivere. Módosításkor élőben újraütemeződik.";
            case "currency.economy-event.chance-percent" -> "Egy jogosult gazdasági ellenőrzés százalékos esélye új keresleti sokk indítására.";
            case "currency.economy-event.duration-hours" -> "Egy normál valutakeresleti sokk alap időtartama órában.";
            case "currency.economy-event.min-multiplier" -> "A pozitív gazdasági sokk legkisebb sorsolható valutaérték-szorzója.";
            case "currency.economy-event.max-multiplier" -> "A pozitív gazdasági sokk legnagyobb sorsolható valutaérték-szorzója; a minimumnál kisebb érték hibás tartományt ad.";
            case "currency.economy-event.panic-chance" -> "A létrejövő gazdasági esemény 0–1 közötti esélye arra, hogy pozitív kereslet helyett lefelé tartó piaci pánik legyen.";
            case "currency.economy-event.panic-min-multiplier" -> "Piaci pánik esetén a valutaérték legkisebb sorsolható szorzója. Egy alatti érték értékvesztést jelent.";
            case "currency.economy-event.panic-max-multiplier" -> "Piaci pánik esetén a valutaérték legnagyobb sorsolható szorzója. A minimum és 1 között érdemes tartani.";
            case "currency.market-boom.enabled" -> "Engedélyezi a ritka konjunktúra-időablakot, amely ideiglenesen csökkenti egy valuta piactéri eladási díját.";
            case "currency.market-boom.chance-percent" -> "Egy gazdasági ellenőrzés százalékos esélye új konjunktúra elindítására.";
            case "currency.market-boom.duration-minutes" -> "A konjunktúra kedvezményes piactéri díjának időtartama percben.";
            case "currency.market-boom.fee-percent" -> "Konjunktúra alatt a normál market.fee-percent helyett alkalmazott csökkentett eladási díj.";
            case "currency.dynamic-exchange.enabled" -> "Bekapcsolja a teljes szervervaluta-kínálatból számolt dinamikus árfolyamot; kikapcsolva a fix exchange-rate lép életbe.";
            case "currency.dynamic-exchange.reference-supply" -> "Az a referencia-kínálat, amelynél egy valuta kínálati korrekciója nagyjából semleges. A várható népességhez kell kalibrálni.";
            case "currency.dynamic-exchange.elasticity" -> "Meghatározza, milyen erősen reagáljon az árfolyam a valuta kínálatának változására. Nagyobb érték hevesebb kilengést ad.";
            case "currency.dynamic-exchange.min-multiplier" -> "A kínálatalapú árfolyamkorrekció alsó szorzókorlátja, amely megakadályozza a valuta teljes elértéktelenedését.";
            case "currency.dynamic-exchange.max-multiplier" -> "A kínálatalapú árfolyamkorrekció felső szorzókorlátja, amely megakadályozza a ritka valuta korlátlan felértékelődését.";
            case "currency.dynamic-exchange.daily-limit" -> "Egy játékos naponta legfeljebb ekkora forrásösszeget válthat át. 0 kikapcsolja a napi limitet.";
            case "market.max-listings-per-player" -> "Egy játékos egyszerre legfeljebb ennyi aktív piactéri hirdetést tarthat fenn.";
            case "market.fee-percent" -> "Sikeres piactéri eladáskor az eladó bevételéből elégetett százalékos díj; a gazdaság fő pénznyelője.";
            case "market.auction.default-duration-hours" -> "Ha az eladó nem ad meg időtartamot, az aukció ennyi órára indul.";
            case "market.auction.max-duration-hours" -> "Egy játékos által létrehozott aukció engedélyezett maximális időtartama órában.";
            case "market.auction.min-increment-percent" -> "Az új licitnek legalább ennyi százalékkal kell meghaladnia a jelenlegi vezető licitet.";
            case "market.auction.big-increment-percent" -> "A piactéri GUI jobb kattintásos gyors licitje ennyi százalékkal emel a jelenlegi összeg fölé.";

            // Moderáció
            case "moderation.enabled" -> "A natív némítás-, chatszűrés-, spamvédelem- és kapcsolódó moderációs kapuk főkapcsolója. A tartós büntetési nyilvántartást nem törli.";
            case "moderation.chat-filter.enabled" -> "Engedélyezi a konfigurált tiltott szavak kis- és nagybetűfüggetlen részszó-ellenőrzését a chatben.";
            case "moderation.chat-filter.mode" -> "CENSOR módban csak a talált tiltott szó csillagozódik; BLOCK módban a teljes üzenet kézbesítése elmarad.";
            case "moderation.spam.enabled" -> "Engedélyezi a túl gyors és rövid időn belül megismételt chatüzenetek blokkolását.";
            case "moderation.spam.min-interval-millis" -> "Két kézbesíthető chatüzenet között legalább ennyi ezredmásodpercnek kell eltelnie játékosonként.";
            case "moderation.spam.duplicate-window-seconds" -> "Ugyanazt az üzenetet ennyi másodpercen belül ismételve a spamvédelem blokkolja.";
            case "moderation.chat-log.enabled" -> "Engedélyezi a blokkolt, cenzúrázott vagy némítás miatt elutasított üzenetek moderációs naplózását.";
            case "moderation.vanish.exclude-from-online-count" -> "A vanish állapotú stafftagokat kihagyja a natív tablista és MOTD online játékosszámából.";
            case "moderation.vanish.allow-item-pickup" -> "Engedélyezi, hogy vanished admin tárgyat vegyen fel. Kikapcsolva a megfigyelés nem avatkozik bele a földi itemekbe.";
            case "moderation.vanish.allow-damage" -> "Engedélyezi, hogy vanished admin más entitást vagy játékost sebezzen. Kikapcsolva a támadási kísérlet blokkolódik.";
            case "moderation.vanish.allow-interaction" -> "Engedélyezi, hogy vanished admin blokkokkal és entitásokkal interakcióba lépjen. Kikapcsolva a megfigyelés passzív marad.";

            default -> "A(z) " + fallbackLabel + " működéséhez tartozó élő konfigurációs érték. A pontos kulcs az ikon tetején látható.";
        };
    }
}
