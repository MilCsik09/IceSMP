package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.utils.MessageManager;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * D9 — Énekmondó (lore: balladák az Elveszett Uralkodókról és a Vérháborúkról —
 * kódex-függelékek). A fővárosi bárd NPC a HÉT top-játékosairól "énekel": a
 * StatsManager top-1 szint/vagyon/raid adataiból sablon-variánsokkal épített,
 * hetente újraszülető ballada. A variáns-választás a hét sorszámából determinisztikus
 * — a dal egész héten ugyanaz ("a héten ezt éneklik a fogadóban"), hétfőnként fordul.
 *
 * <p>Bekötés: a FancyNpcs interact-hook (IceSMPCore) a {@code bard.npc-name} nevű
 * NPC-re jobb-kattkor hívja a {@link #sing}-et — a hívás a játékos saját régió-szálán
 * fut, a küldés biztonságos. A production contract ezért FancyNpcs-t kötelezővé teszi
 * (nincs bárd-NPC). Minden kulcs élőben olvasódik (bard.*).
 */
public final class BardManager {

    private final ConfigManager configManager;
    private final StatsManager statsManager;
    private final MessageManager messageManager;

    /**
     * Krónika-versszakok: a bárd MINDEN héten mást mesél a világ történetéből (a heti index
     * választ), így a rendszeres játékos a kódex elolvasása nélkül, hétről hétre megismeri a
     * teljes idővonalat — a Teremtéstől a Felsők koráig. A messages-kulcs soronkénti
     * ({@code bard-chronicle-<index+1>}), tehát a készlet bővítése kód-módosítás nélkül él.
     */
    private static final String[] CHRONICLE_VERSES = {
            "<gold>🎵 „Hallgassátok: hajdan egy csillag szállt le, Asterlayna volt a neve — s ahol földet ért, ott kelt ki a Fa.”</gold>",
            "<gold>🎵 „Aetrinitának hívták a Fát, s négy gyermeket nevelt: lángot, pikkelyt, erdőt… és csendet.”</gold>",
            "<gold>🎵 „Soleil volt a láng, Kallan a pikkely, Arkynn az erdő ura — s a negyediket ma nem nevezem meg.”</gold>",
            "<gold>🎵 „Az első esztendőben megrepedt a Fa, és Asterobourgh elbukott — ezt hívják Hasadásnak, s innen számol minden év.”</gold>",
            "<gold>🎵 „A népet a világ két sarkába szórták: egyiket a jég vette, másikat a láng. Így lett a testvérből határ.”</gold>",
            "<gold>🎵 „Tizennégyben Pyralingrad kőfala nőtt, száztizenhétben Glatziendorf jege csillant — s a két város azóta méregeti egymást.”</gold>",
            "<gold>🎵 „Ötszáznegyvenhétben, hat háború után, Caldestera letette a fegyvert örökre — s azóta a Menedékbe kardot nem viszünk.”</gold>",
            "<gold>🎵 „Ryanora és Caldestera nem korona, hanem kézfogás. Ezért törik könnyebben — és ezért tart mégis.”</gold>",
            "<gold>🎵 „Hatszázkilencvennyolcban kettéhasadt az ég, s felébredt, aki a mélyben várt. Az a Káoszkor első napja volt.”</gold>",
            "<gold>🎵 „A Néma Királynő két mondatot szólt: az elsőre felkeltek a holtak, a másodikra elfogyott a nemesség. Harmadikat még nem mondott.”</gold>",
            "<gold>🎵 „Négy uralkodó nevét őrzi a lajstrom: Zhoris a lángmadarak ura, Miinus a sárkánykirály örököse, s a két hadvezér, Benedictus és Lineata.”</gold>",
            "<gold>🎵 „Zhoris köpenye főnixtollból szőtt, Miinus haragja acélba vert — s a fegyver máig öl, de a kéz, mely tartotta, nincs többé.”</gold>",
            "<gold>🎵 „Benedictus és Lineata büszke seregei ma is róják az utakat — csak lassabban, és nem parancsra.”</gold>",
            "<gold>🎵 „Kilencszázhetvennyolcban új nép jött a Fa alá: a Felsők. Ti vagytok azok — s a halál nálatok csak késés.”</gold>",
            "<gold>🎵 „A Fa ajándékot ad, ha először nyúlsz az erő felé: tárgyat, amit eldobni nem tudsz. Lélekkapocs a neve.”</gold>",
            "<gold>🎵 „A Kárhozat Kapuja nem ajtó, hanem seb — és a sebbe nem nyúl az, aki élni akar.”</gold>",
            "<gold>🎵 „Radicora az Ó-Caldestera: megkopott házai közt a Fa igaz követői élnek — ott nyitja szemét minden új Felső.”</gold>",
            "<gold>🎵 „Thanaopolist a régi világ Mortengradnak hívta — a nevet ma már csak a vének receptjei őrzik.”</gold>",
            "<gold>🎵 „A vérhold nem baljós jel, jó népek: az Ő fordulása álmában. Ennyi elég, hogy a világ megbillenjen.”</gold>",
            "<gold>🎵 „A Suttogók nem messze élnek. Nappal veled esznek, éjjel másra hallgatnak — s nem mindig tudják, mikor döntöttek.”</gold>",
    };

    /** Nyitány-variánsok (messages-kulcs: bard-opener-1..3). */
    private static final String[] OPENERS = {
            "<light_purple>🎵 „Gyertek közelebb, halljátok hát — a hét balladáját húzom!”</light_purple>",
            "<light_purple>🎵 „Régi húrok, friss nevek — íme a hét éneke!”</light_purple>",
            "<light_purple>🎵 „A fogadó füstjén át is fénylik e dal — hallgassátok!”</light_purple>",
            "<light_purple>🎵 „Pénzért énekelek, de ez a dal ingyen van — annyira igaz!”</light_purple>",
            "<light_purple>🎵 „Csend legyen ott hátul! A hét hősei ma este arcot kapnak!”</light_purple>",
            "<light_purple>🎵 „Az Idegen tanított egy dalt egyszer… de azt nem ma. Ma ezt halljátok!”</light_purple>",
            "<light_purple>🎵 „Hét nap, hét hír, egy húr — kezdjük!”</light_purple>",
            "<light_purple>🎵 „A Számvevők számolnak, én mesélek. Ők pontosabbak. Én igazabb.”</light_purple>"
    };
    /** Szint-hős variánsok ({player}/{value}). */
    private static final String[] LEVEL_VERSES = {
            "<aqua>🎵 „{player} nevét zengi a szél, a {value}. szint magasán jár — az Elveszett Uralkodók is bólintanának!”</aqua>",
            "<aqua>🎵 „Ki ér fel hozzá? Senki ma még — {player}, a {value}. szint vándora!”</aqua>",
            "<aqua>🎵 „Volt egyszer egy vándor, ki nem állt meg soha… {player} a neve, s {value} szint a nyoma.”</aqua>",
            "<aqua>🎵 „A Fa lombja közt új név zizeg: {player}, a {value}. szint vándora!”</aqua>",
            "<aqua>🎵 „Hegyet mászik, mélybe száll — {player} a {value}. szinten jár!”</aqua>",
            "<aqua>🎵 „Kérdezték a hegyet: ki a legnagyobb? A hegy csak ennyit szólt: {player}. ({value}. szint!)”</aqua>",
            "<aqua>🎵 „A Könyv lapján friss tinta ragyog — {player} a {value}. szintre hágott!”</aqua>",
            "<aqua>🎵 „Se jég, se láng nem állította meg — {player}, a {value}. szint ura, íme!”</aqua>"
    };
    /** Vagyon-hős variánsok. */
    private static final String[] WEALTH_VERSES = {
            "<green>🎵 „S az arany? Az arany {player} zsebében csörög — {value} érmét számoltak a Számvevők, s elpirultak!”</green>",
            "<green>🎵 „A Bankárszövetség kedvence, {player} — {value} fénylik a könyvekben a neve mellett!”</green>",
            "<green>🎵 „Ócska garas nem hull nyomában — {player} kincse {value}, így szól a fáma!”</green>",
            "<green>🎵 „Caldestera kapuja aranytól ragyog — {player} számláján {value} csillog!”</green>",
            "<green>🎵 „Kérdezd a Számvevőt, ki a leggazdagabb — {player}, súgja, és {value}-t mutat!”</green>",
            "<green>🎵 „A Creutzér oda gurul, ahol szeretik — {player}hoz gurult {value}-nyi belőle!”</green>",
            "<green>🎵 „Van, ki kincset ás, van, ki kincset ír — {player} számláján {value} a hír!”</green>",
            "<green>🎵 „A piac zaja közt egy név zeng tisztán: {player} — és {value} csengő érme!”</green>"
    };
    /** Raid-hős variánsok. */
    private static final String[] RAID_VERSES = {
            "<red>🎵 „S ha dobszó szól, {player} pajzsa dörren — {value} ellenfél hullt már előtte a hadszíntéren!”</red>",
            "<red>🎵 „A Vérháborúk visszhangja él: {player} kardján {value} győzelem fénye ég!”</red>",
            "<red>🎵 „Ne állj útjába, ha kürt rivall — {player} mögött {value} elesett rivális!”</red>",
            "<red>🎵 „Dob se kell, hogy féljenek tőle — {player} nevét {value} csata őrzi!”</red>",
            "<red>🎵 „A Vérszavanna pora issza a hírt: {player} már {value} győzelmet írt!”</red>",
            "<red>🎵 „Pajzsok törnek, kürtök szólnak — {player} {value} diadalt számol!”</red>",
            "<red>🎵 „A hadszíntér tudja a nevét jól: {player} — {value} győzelem szól!”</red>",
            "<red>🎵 „Nem a kard teszi a hőst, hanem a kéz — {player} keze {value} csatát idéz!”</red>"
    };
    /** Zárás-variánsok. */
    private static final String[] CLOSERS = {
            "<light_purple>🎵 „…s ha jövő héten más nevet hoz a szél — gyere vissza, s meghallod, kiét!”</light_purple>",
            "<light_purple>🎵 „Ennyi a dal — a többit írjátok ti, odakint!”</light_purple>",
            "<light_purple>🎵 „A húr elpattan, a hír marad. Jó utat, vándor!”</light_purple>",
            "<light_purple>🎵 „S ha egyszer rólad szól a dal — ne feledd, ki énekelte először!”</light_purple>",
            "<light_purple>🎵 „A Könyv lapjai közt egy dallam is elfér. Ez volt az.”</light_purple>",
            "<light_purple>🎵 „Ha nem tetszett, a panaszt a Számvevőknél lehet leadni. Három Creutzér.”</light_purple>",
            "<light_purple>🎵 „A dal elszáll. A tett marad. Menjetek, tegyetek!”</light_purple>",
            "<light_purple>🎵 „…és ha az Idegen kérdezi, ki énekelt: nem tudjátok. Higgyétek el, így jobb.”</light_purple>"
    };

    public BardManager(final ConfigManager configManager, final StatsManager statsManager,
                       final MessageManager messageManager) {
        this.configManager = configManager;
        this.statsManager = statsManager;
        this.messageManager = messageManager;
    }

    /** A bárd-NPC neve (a FancyNpcs interact-hook erre szűr, kisbetűsen). */
    public String npcName() {
        return configManager.getString("bard.npc-name", "enekmondo").toLowerCase(Locale.ROOT);
    }

    public boolean isEnabled() {
        return configManager.getBoolean("bard.enabled", true);
    }

    /** A hét sorszáma (epoch-hét) — a variáns-sorsolás determinisztikus magja. */
    private static long weekIndex() {
        return System.currentTimeMillis() / (7L * 24L * 60L * 60L * 1000L);
    }

    private static String pick(final String[] variants, final long seed, final int salt) {
        return variants[(int) Math.floorMod(seed + salt, variants.length)];
    }

    /**
     * A heti ballada eléneklése a kattintó játékosnak (a hívó a játékos saját
     * régió-szálán fut — FancyNpcs interact-hook). A messages-kulcsok
     * (bard-opener-N, bard-verse-level-N…) felülírhatók.
     */
    public void sing(final Player player) {
        if (!isEnabled()) {
            return;
        }
        final long week = weekIndex();
        final List<Component> song = new ArrayList<>();
        song.add(verse("bard-opener", OPENERS, week, 0, null, null));
        // Heti krónika-versszak: a világ történetének egy szelete, hetente más. Ez a
        // sztori-átadás fő csatornája a hősök dicsérete mellett.
        song.add(verse("bard-chronicle", CHRONICLE_VERSES, week, 7, null, null));

        final List<StatsManager.Entry> topLevel = statsManager.top(StatsManager.Category.LEVEL, 1);
        if (!topLevel.isEmpty()) {
            song.add(verse("bard-verse-level", LEVEL_VERSES, week, 1,
                    topLevel.get(0).name(), String.valueOf(topLevel.get(0).level())));
        }
        final List<StatsManager.Entry> topWealth = statsManager.top(StatsManager.Category.WEALTH, 1);
        if (!topWealth.isEmpty()) {
            song.add(verse("bard-verse-wealth", WEALTH_VERSES, week, 2,
                    topWealth.get(0).name(), String.format(Locale.ROOT, "%.0f", topWealth.get(0).wealth())));
        }
        final List<StatsManager.Entry> topRaid = statsManager.top(StatsManager.Category.RAID_KILLS, 1);
        if (!topRaid.isEmpty() && topRaid.get(0).raidKills() > 0) {
            song.add(verse("bard-verse-raid", RAID_VERSES, week, 3,
                    topRaid.get(0).name(), String.valueOf(topRaid.get(0).raidKills())));
        }
        if (song.size() == 1) {
            song.add(messageManager.getMessage("bard-no-heroes",
                    "<gray>🎵 „Csend van még e héten, vándor — de a Könyv lapja üres, nem vak. Írj rá te!”</gray>"));
        }
        song.add(verse("bard-closer", CLOSERS, week, 4, null, null));

        for (final Component line : song) {
            player.sendMessage(line);
        }
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_YES, 0.6F, 1.2F);
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_GUITAR, 0.8F, 1.1F);
    }

    private Component verse(final String keyPrefix, final String[] variants, final long week,
                            final int salt, final String playerName, final String value) {
        final int index = (int) Math.floorMod(week + salt, variants.length);
        final String fallback = variants[index];
        final Map<String, String> placeholders = playerName == null
                ? Map.of() : Map.of("player", playerName, "value", value);
        return messageManager.getMessage(keyPrefix + "-" + (index + 1), fallback, placeholders);
    }
}
