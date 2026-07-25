package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * D15 — Tábortűz-mesélés (lore: a kódex szájhagyomány-felülete). Tábortűz mellett
 * SNEAK+jobb-katt indítja a mesélést: ha a játékos hold-seconds ideig a tűz mellett
 * marad, kap egy véletlen sztori-sort (a kódex szájhagyománya) + kevés vanília-XP-t.
 * AFK-farm ellen: PDC-cooldown ({@code cd_campfire_story}) + alacsony összeg — a
 * jutalom csak SIKERES kitartás után jár, és csak akkor indul cooldown.
 *
 * <p>Folia: az interakció és a késleltetett ellenőrzés is a játékos SAJÁT
 * entity-schedulerén fut (a tábortűz kattintás-távolságban, régió-lokális).
 * Minden kulcs élőben olvasódik (campfire-story.*).
 */
public final class CampfireStoryListener implements Listener {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final hu.taliann.icesmp.managers.FactionManager factionManager;
    private final NamespacedKey cooldownKey;
    private final NamespacedKey pendingKey;

    /**
     * A mese kiválasztása. Kétféle készletből húz:
     * <ul>
     *   <li>a KÖZÖS {@link #STORIES} (a világ története, semleges hangon),</li>
     *   <li>a játékos SAJÁT frakciójának készlete — ugyanazok az események, de az ő elődeik
     *       hangján és elfogultságával.</li>
     * </ul>
     * A {@code campfire-story.faction-chance-percent} dönti el, milyen eséllyel jön a
     * frakciós változat (0 = mindig a közös). Frakció nélküli játékos mindig a közöset kapja.
     * Így a rendszeres mesélő a világ történetét NÉGY nézőpontból is megismerheti.
     */
    private net.kyori.adventure.text.Component pickStory(final org.bukkit.entity.Player player) {
        final hu.taliann.icesmp.data.FactionType faction = factionManager == null
                ? null : factionManager.getFaction(player.getUniqueId());
        final String[] factionPool = faction == null ? null : FACTION_STORIES.get(faction);
        final int factionChance = Math.max(0, Math.min(100,
                configManager.getInt("campfire-story.faction-chance-percent", 50)));
        if (factionPool != null && factionPool.length > 0
                && ThreadLocalRandom.current().nextInt(100) < factionChance) {
            final int index = ThreadLocalRandom.current().nextInt(factionPool.length);
            return messageManager.getMessage(
                    "campfire-story-" + faction.name().toLowerCase(java.util.Locale.ROOT) + "-" + (index + 1),
                    factionPool[index]);
        }
        final int index = ThreadLocalRandom.current().nextInt(STORIES.length);
        return messageManager.getMessage("campfire-story-" + (index + 1), STORIES[index]);
    }

    /**
     * Sztori-sor variánsok. A messages-kulcs soronkénti: {@code campfire-story-<index+1>} —
     * a tömb bővítése automatikusan új kulcsot ad, kód-módosítás nélkül. Ez az EGYIK
     * fő csatorna, amin a játékos a kódex elolvasása nélkül is megismeri a világ történetét,
     * ezért a készlet szándékosan bő és kánon-hű (Teremtés, Hasadás, a három birodalom,
     * a Hetedik Vérháború, a Felsők kora).
     */
    private static final String[] STORIES = {
            "<gray>🔥 „…és amikor a Fa első gyökere vizet ért, a jég megtanult énekelni. Így mesélik a régiek.”</gray>",
            "<gray>🔥 „A Vérháborúk előtt a két nép egy tűznél ült — mint most mi. A tűz emlékszik.”</gray>",
            "<gray>🔥 „Bokic vize sosem fagy be. Azt mondják, a folyó egy alvó szív ere.”</gray>",
            "<gray>🔥 „A Kapun túlról nem jött vissza senki. Csak a hamu — az minden tavasszal visszajön.”</gray>",
            "<gray>🔥 „A Számvevők mindent felírnak. De a tábortűz meséit sosem — azok a miénk.”</gray>",
            "<gray>🔥 „A Néma Királynő nem gonosz, fiam. Csak nagyon-nagyon régóta vár valakire.”</gray>",
            "<gray>🔥 „I. Zhoris lángmadarai? Á, azok nem madarak voltak. De ezt itt ne mondd hangosan.”</gray>",
            "<gray>🔥 „Glatziendorf falait nem kő tartja. Emlék tartja. Ezért nem dőlnek le soha.”</gray>",
            "<gray>🔥 „A Mélység Népe nem tűnt el, fiam. Csak elhallgatott. Az nem ugyanaz.”</gray>",
            "<gray>🔥 „Régen a két nép egy nyelvet beszélt. A szavak maradtak — csak a hangsúly lett fegyver.”</gray>",
            "<gray>🔥 „Az Idegen? Láttam egyszer. Vagy ő látott engem. Azóta se tudom, melyik a rosszabb.”</gray>",
            "<gray>🔥 „A Számvevők mindent tudnak a pénzedről. De hogy MIÉRT gyűjtöd — azt csak a tűz.”</gray>",
            "<gray>🔥 „Minden korszak úgy kezdődik, hogy valaki tüzet rak. Így, mint mi most.”</gray>",
            "<gray>🔥 „A sculk alatt nem üresség van. Fülek vannak. Ezért suttogunk a tűznél is.”</gray>",
            "<gray>🔥 „Nagyapám látta a Hetedik Vérháborút. Sosem beszélt róla. CSAK a tűznek, halkan.”</gray>",
            "<gray>🔥 „A Bokic egyszer kiöntött, és egy egész falut odébb rakott. Senki se halt meg. A folyó válogat.”</gray>",
            "<gray>🔥 „Az Arany Liga? Volt. Nincs. A Számvevők átvették a könyveiket, és a nevüket is kifizették.”</gray>",
            "<gray>🔥 „A jégsárkányok nem haltak ki, fiam. Alszanak. És Kallan tudja, hol.”</gray>",
            "<gray>🔥 „Thanaopolisban éjjel ne fütyülj. Nem babona. Csak udvariasság — valaki mindig hallgatózik.”</gray>",
            "<gray>🔥 „A Vándorünnep lepénye? Az igazi receptjét csak három szakács tudja, és nem beszélnek egymással.”</gray>",
            "<gray>🔥 „Volt egy uralkodó, I. Lineata, aki sosem vesztett csatát. Aztán egyszer nem jött vissza. A Könyvben üres a lapja.”</gray>",
            "<gray>🔥 „A meteorvas jó vas. De ha éjjel kovácsolod, néha visszakalapál.”</gray>",
            "<gray>🔥 „A Lapforduló Őre nem szörny. Őr. Az a kérdés, MITŐL őrzi a lapot… vagy KITŐL.”</gray>",
            "<gray>🔥 „A Kitaszítottak közt több a becsület, mint a fővárosban. Csak ott senki se írja fel.”</gray>",
            "<gray>🔥 „Az Első Csendről nem mesélünk. Ez a mese. Vége.”</gray>",
            "<gray>🔥 „A Fa egyik gyökere állítólag a tábortüzek alatt fut. Ezért melegszik át a történet is.”</gray>",
            "<gray>🔥 „Asterlayna nem lezuhant, fiam. Leszállt. A különbség egy szó, és ezen ment el az Első Háború.”</gray>",
            "<gray>🔥 „A csillag helyén kikelt egy mag. Abból lett a Fa. Aetrinita a neve — de ne mondd ki hangosan éhesen.”</gray>",
            "<gray>🔥 „A Fának négy gyermeke volt: Soleil a láng, Kallan a pikkely, Arkynn az erdő… és a negyedik.”</gray>",
            "<gray>🔥 „A negyedik gyermek nevét mindenki ismeri, és senki nem mondja ki. Ennyit a nevekről.”</gray>",
            "<gray>🔥 „Az első esztendőben megrepedt a Fa, és az emberiséget a világ két sarkába szórta. Ezt hívják Hasadásnak.”</gray>",
            "<gray>🔥 „Asterobourgh elbukott, és aznap kezdődött az időszámítás. Minden dátum egy sebtől számol.”</gray>",
            "<gray>🔥 „Miért fázik Északon és éget Délen? Mert a Hasadáskor a világ két végén kaptunk földet, nem otthont.”</gray>",
            "<gray>🔥 „Pyralingradot a tizennegyedik évben alapították. Glatziendorfot a száztizenhetedikben. Száz év a különbség, és még mindig egymást méregetik.”</gray>",
            "<gray>🔥 „Az ötszáznegyvenhetedik évben Caldestera letette a fegyvert. Örökre. Ezért nem viszel bele kardot ma sem.”</gray>",
            "<gray>🔥 „Az Armageddon-ultimátum után a Menedék semleges lett. Nem gyávaságból: hatodik háború után a semlegesség a legdrágább döntés.”</gray>",
            "<gray>🔥 „Ryanora és Caldestera nem királyság, fiam. Megegyezés. Azért törékenyebb — és azért tart ki mégis.”</gray>",
            "<gray>🔥 „A hatszázkilencvennyolcadik évben kettéhasadt az ég. Utána nem volt több koronás öregember.”</gray>",
            "<gray>🔥 „A Néma Királynő két mondatot mondott. Az elsőre felkeltek a holtak. A másodikra eltűnt a nemesség.”</gray>",
            "<gray>🔥 „Azt mondják, van egy harmadik mondat is. Ezért nem alszik jól, aki koronát visel.”</gray>",
            "<gray>🔥 „A Káoszkor nem véget ért, fiam. Csak megszoktuk. Ez a kettő nem ugyanaz.”</gray>",
            "<gray>🔥 „Kilencszázhetvennyolcban jöttetek meg a Fa alá. A Felsők. Azóta változik minden — jó irányba is.”</gray>",
            "<gray>🔥 „Tudod, miért bírja a te fajtád, amit mi nem? Mert nálatok a halál csak késés. Nálunk befejezés.”</gray>",
            "<gray>🔥 „A Lélekkapocs a Fa ajándéka: el nem dobod, el nem cserélik, kohóban se ég el. Ilyen ajándékot ma már nem adnak.”</gray>",
            "<gray>🔥 „A Kárhozat Kapuja nem ajtó. Seb. És a sebek nem szeretik, ha nyúlkálnak bennük.”</gray>",
            "<gray>🔥 „A rontás-gócok nem az égből esnek. Alulról nőnek, mint a gomba. Ezért nem lehet őket lebombázni.”</gray>",
            "<gray>🔥 „Radicora az Ó-Caldestera. Megkopott ott minden — de a Fa igaz követői ma is ott élnek.”</gray>",
            "<gray>🔥 „Négy hatalom, négy pénz, egy Fa. Számold össze: valami mindig kevés lesz.”</gray>",
            "<gray>🔥 „A Suttogók nem árulók. Csak korábban választottak, mint te. És rosszabbat.”</gray>",
            "<gray>🔥 „A vérhold nem baljós jel, fiam. Az a Királynő, ahogy megfordul álmában.”</gray>",
            "<gray>🔥 „Ha a Fa fényköréből kifelé indulsz, minden lépéssel öregebb szörnyekbe futsz. A Fa nem véd — csak ismer.”</gray>",
            "<gray>🔥 „Pyralingrad kohói sosem hűlnek ki. Egyszer kihűltek. Arról az évről nincs krónika.”</gray>",
            "<gray>🔥 „A Szellemszarvas nem hátas. Vendéglátó. Sose sarkantyúzd.”</gray>",
            "<gray>🔥 „Minden térképen van egy fehér folt. Nem azért, mert nem jártak ott. Azért, mert visszajöttek, és nem rajzolták be.”</gray>",
            "<gray>🔥 „Radicorát a gazdagok Ó-Caldesterának hívják. A Fa sehogy se hívja. A Fa csak tudja, hol van.”</gray>",
            "<gray>🔥 „Amikor a fél város elhajózott, a vének azt mondták: menjetek. A gyökér nem szalad a levél után.”</gray>",
            "<gray>🔥 „A pyralingradi vérfa nedve meleg. Egyszer megvágtam egyet. Azóta nem fázom. És nem alszom jól.”</gray>",
            "<gray>🔥 „A révész sosem kérdezi, hová mész. Csak azt, hogy visszafelé is vele jössz-e. Mondj igent.”</gray>",
            "<gray>🔥 „Olethropyla. Így írták a régiek a Kaput. Ne tanuld meg. Amit néven szólítasz, az visszaszól.”</gray>",
            "<gray>🔥 „A hadi-ablak előtt a kovácsok kétszer annyit dolgoznak. Utána a papok.”</gray>",
            "<gray>🔥 „Thanaopolis kriptái alatt van egy terem, ahol a Csontszámvevő számol. Nem pénzt, fiam. Neveket.”</gray>",
            "<gray>🔥 „A komp viteldíja nem a révésznek kell. A víznek. Kérdezd meg, mi történt, amikor egyszer nem fizették ki.”</gray>"
    };

    /**
     * Frakciónkénti mese-készletek: ugyanazok az események a SAJÁT népük hangján és
     * elfogultságával (a kánon forrása változatlanul a kódex). A messages-kulcs
     * {@code campfire-story-<frakció>-<index+1>}, tehát minden sor külön átírható.
     */
    private static final java.util.Map<hu.taliann.icesmp.data.FactionType, String[]> FACTION_STORIES =
            java.util.Map.of(
                    hu.taliann.icesmp.data.FactionType.RED, new String[] {
                            "<red>🔥 „A Fa négy gyermeket bocsátott a világra, fiam, de csak egy hozott meleget: Soleil. Nélküle a többi ma is vacogna.”</red>",
                            "<red>🔥 „Soleil a Főnixek Ura volt, s a mieink a lángmadarai hátán lettek urai a sivatagi szélnek. A jég azóta is gyalogol.”</red>",
                            "<red>🔥 „Az Első Háborúban Soleil haragja nem irigységből gyúlt ki, hanem a megmérgezett testvéréért. Ezt jegyezd meg jól.”</red>",
                            "<red>🔥 „A Hasadáskor a Fa a Vérszavannára szórt minket. Az északiak azt hiszik, minket vert meg jobban — nálunk semmi sem fagy meg.”</red>",
                            "<red>🔥 „Nem adtak alánk se vizet, se árnyékot, mégis a tizennegyedik évben állt Pyralingrad. Az északiak százig húzták, aztán még tizenhetet.”</red>",
                            "<red>🔥 „A falakat a szavanna fehér kövéből raktuk, a házakat akáciából. Vérfát csak úr ültethetett — a nedvében főnix-csepp kering.”</red>",
                            "<red>🔥 „Fogd a kezedbe a Paralsot télen. Mindig meleg marad, fiam: a réz elárulja, kinek a pénze.”</red>",
                            "<red>🔥 „Hét háborút vívtunk a jéggel. A hetedikről nálunk így szól a szó: hat fiú ment el egy házból, és egy jött vissza.”</red>",
                            "<red>🔥 „A hatodik után Caldestera letette a fegyvert, örökre. Mi nem. Aki a homokban leteszi a lándzsát, azt a homok temeti be.”</red>",
                            "<red>🔥 „Az ostrom-számszeríjat két ember húzta fel, s a vasszálka egy egész sorral végzett. Nálunk minden ház az egyik embert magának mondja.”</red>",
                            "<red>🔥 „Zhorisról azt mondják, a lángmadara még akkor is a falak fölött körözött, amikor az urát már elvitte az átok.”</red>",
                            "<red>🔥 „A Lángnyelvét a Hetedik Vérháború napján kovácsolták a Vérszavanna legmélyén. Úgy mondják, a király meg se várta, hogy kihűljön.”</red>",
                            "<red>🔥 „A jégiek nem bátrak, fiam: számolnak. Először alkut kínálnak, aztán nyilat — és mindig tudják, melyik a drágább.”</red>",
                            "<red>🔥 „A Menedék nem békét választott, hanem hátat fordított. Aki nem ad vért, az mások vérén ül — így mondta apám.”</red>",
                            "<red>🔥 „A Kitaszítottakat ne gyűlöld, fiam — szánd. Egy szívdobbanás alatt üt át rajtuk a bélyeg, s attól fogva minden penge vadássza őket.”</red>",
                            "<red>🔥 „Hatszázkilencvennyolcban egy éjszaka alatt elfogyott minden urunk. Reggel égett a kohó, de nem volt, aki parancsot adjon.”</red>",
                            "<red>🔥 „Kilencszázhetvennyolcban jöttetek a Fa alól. Az első Felső, akit láttam, mezítláb kelt át a lávamezőn — és nevetett.”</red>",
                            "<red>🔥 „Ha a mi zászlónk alá állsz, a láng nem harap. De ne hidd, hogy szeret: csak azt kíméli, aki nem hátrál.”</red>",
                            "<red>🔥 „S most a szégyenünk, fiam: a Kaput MI akartuk elvenni. A jég csak elállta az utat. A hetedik háborút nem ők kezdték.”</red>",
                            "<red>🔥 „A Paralsod értékét nem a mi kohónk szavatolja, hanem egy caldesterai pecsét. Ezt a piacon ne mondd ki hangosan.”</red>",
                            "<red>🔥 „Az Ultimátum napján a mi követünk is fegyver nélkül ül a Menedék asztalához, és eszik. Gyávaság csak akkor, ha ők teszik.”</red>",
                            "<red>🔥 „Az északi hadjáraton a mieink a jégiek halát ették, másképp a fagy megmérgezte a vérüket. Erről a krónikáink egy szót sem írnak.”</red>"
                    },
                    hu.taliann.icesmp.data.FactionType.BLUE, new String[] {
                            "<aqua>🔥 „Asterlayna nem melegben ért földet — nagyapám szerint a jég fogta fel a csillagot. Délen ezt persze másképp mesélik.”</aqua>",
                            "<aqua>🔥 „Az Első Háborúban Kallan a megmérgezett testvéréért emelt fegyvert, és a Fa mégis megtorolta. Ez a legrégebbi sebünk.”</aqua>",
                            "<aqua>🔥 „Az első esztendőben a Fa északra terelt minket. Nem büntetés, nem ajándék — csak irány. Cryghaliris a többit maga tette hozzá.”</aqua>",
                            "<aqua>🔥 „És ne hidd, hogy ártatlanul jöttünk. A Fa azt hagyta a tövében, aki nem emelt kezet senkire. Mi nem maradtunk ott.”</aqua>",
                            "<aqua>🔥 „Rajtad nem fog a fagy, fiam. Ez nem erő: azt jelenti, semmi nem szól, mikor kellene megállnod.”</aqua>",
                            "<aqua>🔥 „Száztizenhét év kellett, míg falat mertünk húzni. Fenyő, kő, gyapjú — ennyi volt Glatziendorf, és ennyi ma is.”</aqua>",
                            "<aqua>🔥 „Pisztráng nélkül ne indulj a jégmezőkre. A mező aurája a legkeményebb ember vérét is megmérgezi — ezt tanuld meg elsőnek.”</aqua>",
                            "<aqua>🔥 „A Hópihér-veret jégből és ezüstből van, s hideg marad a markodban. A déli pénz melegít — az olyan pénz hízeleg.”</aqua>",
                            "<aqua>🔥 „A jégmezők sárkányai kicsik és rosszkedvűek. Kallan öröksége nem a nagyság, fiam, hanem a hidegvér.”</aqua>",
                            "<aqua>🔥 „A kantárról csak ennyit: kemény bőr, és olyan mágia, amiről a kovács nem beszél. Szelídítésnek hívjuk. Nem az.”</aqua>",
                            "<aqua>🔥 „Hét háborút vívtunk a Lánggal — Perinfernicitas a rendes nevük. Azt mondják, a negyedikben a fagy több fiút vitt, mint a csata.”</aqua>",
                            "<aqua>🔥 „Hatszázkilencvennyolcban a Kapunál elvágtuk a Láng útját. Kötelességnek tanítjuk, és van is benne igazság.”</aqua>",
                            "<aqua>🔥 „Az öregek másképp mondják: mi is a Kaput akartuk, csak lassabban. Nálunk a lassúság erény — így hívjuk a kapzsiságot.”</aqua>",
                            "<aqua>🔥 „V. Miinust a nép sárkánykirálynak mondja, pedig csak örökös volt. Azt mondják, ő maga soha nem javította ki senkinek.”</aqua>",
                            "<aqua>🔥 „V. Miinus haragját igazságosnak tanítják az iskolában. A családunkban más szó járta rá, és azt nem mondom ki.”</aqua>",
                            "<aqua>🔥 „A Néma Királynő második mondata éjjelén kiürült a tanácsterem. Reggelre a hó belepte a lépcsőt, s nem söpörte le senki.”</aqua>",
                            "<aqua>🔥 „A Káoszkor első telén — azt mondják — a holtak nem a falnak estek: a kapunál álltak és vártak. Túlságosan hasonlítottak ránk.”</aqua>",
                            "<aqua>🔥 „A Lángról annyit: gyorsan gyúl, gyorsan hamvad. Mi nehéz vasat hordunk és kivárunk — ezért vagyunk még itt.”</aqua>",
                            "<aqua>🔥 „Caldestera nem semleges, csak jól számol. Aki mindkét oldalnak ad, mindkét oldaltól kap. Ezt nem békének hívják.”</aqua>",
                            "<aqua>🔥 „Nálunk azt mondják, a Kitaszított nem szörny: ember, aki belül nem bírta ki a telet. Ezért mesélek neked — hogy te kibírd.”</aqua>",
                            "<aqua>🔥 „Kilencszázhetvennyolcban megjöttetek. Nekünk nem csoda vagytok, fiam, hanem kivárás, ami végre beérett.”</aqua>",
                            "<aqua>🔥 „Azt tartják nálunk: az emlékszilánk nem kincs, hanem tartozás — aki emléket talál, keresse meg a nevet is hozzá.”</aqua>"
                    },
                    hu.taliann.icesmp.data.FactionType.NEUTRAL, new String[] {
                            "<gold>🔥 „Nagyapám úgy mesélte: a csillag nem esett le, csak elvétette a lépést. Egy hibás sorból lett ez az egész világ.”</gold>",
                            "<gold>🔥 „A Fának négy gyermeke volt, s a miénk, Arkynn, volt a legcsendesebb — a mérget mégis ő kapta elsőnek. Aki nem szól, azt fosztják ki.”</gold>",
                            "<gold>🔥 „Az erejüktől megfosztottak királyának hívják Arkynnt. Kérdezd meg magadtól: ki más vállalt volna ilyen népet?”</gold>",
                            "<gold>🔥 „Asterobourgh-ban az arany hadakozott a szabadsággal, és egyik sem győzött — helyettük a Fa repedt meg. Ennyit a győzelmekről.”</gold>",
                            "<gold>🔥 „A Bokic két partja a miénk maradt, Ryanora földje, mert nem emeltünk kezet senkire. Nem érdem volt az, fiam — csak félelem.”</gold>",
                            "<gold>🔥 „Tölgy, tégla, kvarc — ebből rakták a várost, a Vasművek Akadémiájának mértékére. Nem falnak: hogy a könyveknek legyen hol állniuk.”</gold>",
                            "<gold>🔥 „A szarvasunk sebes lábú, mégsem harcra való. Nézd meg jól: a mi jelképünk nem üt, hanem visz.”</gold>",
                            "<gold>🔥 „A Creutzért a világ minden sarkában elfogadják. Ez a mi seregünk: sosem vonul ki, mégis mindenhová eljut.”</gold>",
                            "<gold>🔥 „Egy smaragdkő betét a Szövetség pecsétjével többet ér ezer kardnál — nagyapám szerint azért, mert élesíteni sem kell.”</gold>",
                            "<gold>🔥 „Nálunk így mondják: a hamut mindig a semlegesekre hagyják. A mi szekereink hordták el a holtakat, kétfelől.”</gold>",
                            "<gold>🔥 „Azt mondják, a hatodik háború után a Bokic három napig nem vitt tiszta vizet. Nagyapám azóta nem evett halat.”</gold>",
                            "<gold>🔥 „Ötszáznegyvenhétben a vének kimondták az Armageddon-ultimátumot. Nem bátorság volt az, hanem elszámolás — és mi fizettünk.”</gold>",
                            "<gold>🔥 „A vének nevét nem jegyezte fel a krónika, csak a döntésüket. Ők akarták így; szebb végrendeletet nem láttam.”</gold>",
                            "<gold>🔥 „Két nagy birodalom? Két gyerek egy asztalnál, aki nem tud kibékülni — és mi vagyunk a dajka, aki a tányért elmossa.”</gold>",
                            "<gold>🔥 „Az átok minden koronát sírba vitt, minket meg kihagyott — nem volt kit elvinnie. Megkönnyebbültünk, fiam, és ezt szégyellem.”</gold>",
                            "<gold>🔥 „Az Ultimátum Napján a jég és a láng követe egy asztalnál eszik nálunk, fegyver nélkül. Hogy ki fizeti a lakomát, azt ne kérdezd.”</gold>",
                            "<gold>🔥 „Ti a Fa alatt ébredtek, emlék nélkül — és az első, amit a világ kér tőletek, egy komp viteldíja. Sajnálom, fiam.”</gold>",
                            "<gold>🔥 „Radicorában nyitod ki a szemed, Caldesterában döntesz a sorsodról. A gyökér életet ad, a város számlát.”</gold>",
                            "<gold>🔥 „Fél Caldestera azért kelt át a szoroson, mert a Fa hallgatását nem bocsátotta meg — a vagyont is átvitte. Erről nem mesélünk.”</gold>",
                            "<gold>🔥 „Radicorát nem elpusztították, fiam. Otthagytuk — s ezt a szót egyetlen könyvünkbe sem írták bele.”</gold>",
                            "<gold>🔥 „A Botera-negyed hátsó pultjain Csontveret forog, s a penge sétapálcába rejtve jön be a falak közé. A Vámház olykor másfelé néz.”</gold>",
                            "<gold>🔥 „Adót mi nem fizetünk — ne hidd, hogy kegy: mi szedjük. Aki nem bírja, azt a Számvevők bűnösként írják fel, s a bűnös száműzött lesz.”</gold>"
                    },
                    hu.taliann.icesmp.data.FactionType.DARK, new String[] {
                            "<dark_purple>🔥 „Kezdetben egy csillag zuhant, s a helyén Fa nőtt. Szép mese, fiam — csak arról hallgat, mi volt ott a csillag előtt.”</dark_purple>",
                            "<dark_purple>🔥 „Eleftheria a szabadság gyermeke volt, gesztenyebarna hajú, mint az anyaföld. Ezt a fővárosokban már nem tanítják.”</dark_purple>",
                            "<dark_purple>🔥 „Valami megérintette a szívét, és a Fa nem gyógyította — átengedte a sötétnek. Ennyi a bűne, fiam, ennyi.”</dark_purple>",
                            "<dark_purple>🔥 „Egy novícius papnő szóra bírt egy ötszáz éve halott lelket, és a Lelki Béke Rendje kínhalálra ítélte érte.”</dark_purple>",
                            "<dark_purple>🔥 „Eleftheria Flygadhornak hívták. Míg a kínzóvas a húsába mart, megreccsent a Fa — ez az a hang, amit ők elhallgatnak.”</dark_purple>",
                            "<dark_purple>🔥 „Nem mi építettük ezt a várost. Büszke főváros volt, csak épp a Káoszkor útjában állt — mi a romjaiba költöztünk.”</dark_purple>",
                            "<dark_purple>🔥 „Mortengradnak hívták, míg állt — s a nevét nem a hódítók törölték le, fiam. Mi hagytuk elveszni, mert kimondani nem bírtuk.”</dark_purple>",
                            "<dark_purple>🔥 „Ma Thanaopolisnak hívjuk, a Holtak Városának. Az élőhalottak úgy járják, mint egykor a polgárok — s minket békén hagynak.”</dark_purple>",
                            "<dark_purple>🔥 „A kincstárunkban a Csontszámvevő könyvel: halott urak koronáiból veri a Csontveretünket. Élő bank ezt sosem jegyzi.”</dark_purple>",
                            "<dark_purple>🔥 „A sorvadás rajtunk nem fog. Ne ajándéknak vedd, fiam: annak a jele, hogy a halál már névről ismer minket.”</dark_purple>",
                            "<dark_purple>🔥 „Hét vérháborút számolnak a krónikák, s egyikben sem volt a mi nevünk. A hetedik mégis minket szült.”</dark_purple>",
                            "<dark_purple>🔥 „Itt mindenki a maga útján érkezett, fiam: egyiket rítuson kapták, a mást a hátralékai adták el. Vérdíj mindkettőn.”</dark_purple>",
                            "<dark_purple>🔥 „Nem ő hívott minket, fiam — mi mentünk hozzá. Fél-álomban fekszik két világ küszöbén, s nem ült le mellé soha senki.”</dark_purple>",
                            "<dark_purple>🔥 „Azt sem mondjuk ki: a Királynő nekünk soha nem válaszolt. Az oltárnál mi beszélünk, ő hallgat — így megy ez régóta.”</dark_purple>",
                            "<dark_purple>🔥 „Elmondom, amit itthon nem mondunk: nem a pribékek kaptak el mindenkit. Némelyiket a saját testvére adta fel.”</dark_purple>",
                            "<dark_purple>🔥 „Van, aki kulcsot hord a nyakában egy házhoz, amit régen lebontottak. Honvágyunk nincs — csak kulcsunk.”</dark_purple>",
                            "<dark_purple>🔥 „A Jégmezők népe szerződést köt, aztán hódít, és mindkettőt diplomáciának hívja. Nagyapám tárgyalt velük egyszer.”</dark_purple>",
                            "<dark_purple>🔥 „A Vérszavanna népe gyarmatosítani akarta a Kárhozat Kapuját. Aztán csodálkoztak, hogy a lángjukra felébredt valaki.”</dark_purple>",
                            "<dark_purple>🔥 „Caldestera letette a fegyvert, és tiszta kezet mutat. A Botera-negyed hátsó pultján mégis a mi Csontveretünk csörög.”</dark_purple>",
                            "<dark_purple>🔥 „Amit ők a Királynő átkának hívnak, mi számadásnak. Egyetlen éjszaka volt, két könyvben — s a nemesség nem jött vissza.”</dark_purple>",
                            "<dark_purple>🔥 „Kilencszázhetvennyolcban jöttetek meg, emlék nélkül, s a Fa értetek nyúlt. Hozzánk a fénye sosem ér el.”</dark_purple>",
                            "<dark_purple>🔥 „A Királyok Átka a te fejeden nem fog, ezért trónt már csak ti emelhettek. Gondold meg, kinek a székére ülsz vissza.”</dark_purple>"
                    }
            );


    public CampfireStoryListener(final JavaPlugin plugin, final ConfigManager configManager,
                                 final MessageManager messageManager,
                                 final hu.taliann.icesmp.managers.FactionManager factionManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.factionManager = factionManager;
        this.cooldownKey = new NamespacedKey(plugin, "cd_campfire_story");
        this.pendingKey = new NamespacedKey(plugin, "campfire_story_pending");
    }

    @EventHandler(ignoreCancelled = true)
    public void onCampfireUse(final PlayerInteractEvent event) {
        if (!event.getAction().isRightClick() || event.getClickedBlock() == null) {
            return;
        }
        final Material type = event.getClickedBlock().getType();
        if (type != Material.CAMPFIRE && type != Material.SOUL_CAMPFIRE) {
            return;
        }
        final Player player = event.getPlayer();
        if (!player.isSneaking() || !configManager.getBoolean("campfire-story.enabled", true)) {
            return;
        }
        final long now = System.currentTimeMillis();
        final long cooldownMillis = Math.max(0L,
                configManager.getLong("campfire-story.cooldown-minutes", 60L)) * 60_000L;
        final Long lastStory = player.getPersistentDataContainer().get(cooldownKey, PersistentDataType.LONG);
        if (lastStory != null && now - lastStory < cooldownMillis) {
            return; // Csendben: a tábortűz vanília-interakcióját nem zavarjuk üzenettel.
        }
        // Egyszerre csak egy futó mesélés (dupla kattra ne induljon két időzítő).
        final Long pending = player.getPersistentDataContainer().get(pendingKey, PersistentDataType.LONG);
        if (pending != null && pending > now) {
            return;
        }
        final long holdSeconds = Math.max(2L, configManager.getLong("campfire-story.hold-seconds", 6L));
        player.getPersistentDataContainer().set(pendingKey, PersistentDataType.LONG, now + holdSeconds * 1000L + 2000L);

        final Location fire = event.getClickedBlock().getLocation().add(0.5D, 0.5D, 0.5D);
        player.sendActionBar(messageManager.getMessage("campfire-story-start",
                "<gray>🔥 Leülsz a tűzhöz… maradj mellette, és hallgasd a mesét.</gray>"));

        // A játékos SAJÁT schedulerén ér véget — ha kilépett/elment, nincs jutalom.
        player.getScheduler().runDelayed(plugin, task -> {
            player.getPersistentDataContainer().remove(pendingKey);
            final double radius = Math.max(1.5D, configManager.getDouble("campfire-story.radius", 3.5D));
            if (!player.getWorld().equals(fire.getWorld())
                    || player.getLocation().distanceSquared(fire) > radius * radius) {
                player.sendActionBar(messageManager.getMessage("campfire-story-left",
                        "<gray>🔥 A mese félbeszakadt — elhagytad a tüzet.</gray>"));
                return;
            }
            // Siker: sztori-sor + kis XP + hangulat; a cooldown CSAK most indul.
            player.getPersistentDataContainer().set(cooldownKey, PersistentDataType.LONG, System.currentTimeMillis());
            final List<String> custom = configManager.getStringList("campfire-story.stories");
            if (!custom.isEmpty()) {
                player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                        .deserialize(custom.get(ThreadLocalRandom.current().nextInt(custom.size()))));
            } else {
                player.sendMessage(pickStory(player));
            }
            final int xp = Math.max(0, configManager.getInt("campfire-story.xp-reward", 8));
            if (xp > 0) {
                player.giveExp(xp);
            }
            hu.taliann.icesmp.utils.ParticleUtil.spawn(player.getWorld(), Particle.SOUL_FIRE_FLAME,
                    fire, 14, 0.4D, 0.5D, 0.4D, 0.01D);
            player.playSound(fire, Sound.AMBIENT_CAVE, 0.3F, 1.4F);
            // Kis közösségi lökés: a tűz körül ülő TÖBBI játékos is látja a mesét (régió-lokális kör).
            for (final org.bukkit.entity.Entity nearby : player.getNearbyEntities(radius, radius, radius)) {
                if (nearby instanceof Player listener && org.bukkit.Bukkit.isOwnedByCurrentRegion(listener)) {
                    listener.sendActionBar(messageManager.getMessage("campfire-story-nearby",
                            "<gray>🔥 {player} mesél a tűznél…</gray>",
                            java.util.Map.of("player", player.getName())));
                }
            }
        }, null, holdSeconds * 20L);
    }
}
