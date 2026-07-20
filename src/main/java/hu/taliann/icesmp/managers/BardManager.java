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
 * fut, a küldés biztonságos. FancyNpcs nélkül a rendszer egyszerűen nem elérhető
 * (nincs bárd-NPC). Minden kulcs élőben olvasódik (bard.*).
 */
public final class BardManager {

    private final ConfigManager configManager;
    private final StatsManager statsManager;
    private final MessageManager messageManager;

    /** Nyitány-variánsok (messages-kulcs: bard-opener-1..3). */
    private static final String[] OPENERS = {
            "<light_purple>🎵 „Gyertek közelebb, halljátok hát — a hét balladáját húzom!”</light_purple>",
            "<light_purple>🎵 „Régi húrok, friss nevek — íme a hét éneke!”</light_purple>",
            "<light_purple>🎵 „A fogadó füstjén át is fénylik e dal — hallgassátok!”</light_purple>"
    };
    /** Szint-hős variánsok ({player}/{value}). */
    private static final String[] LEVEL_VERSES = {
            "<aqua>🎵 „{player} nevét zengi a szél, a {value}. szint magasán jár — az Elveszett Uralkodók is bólintanának!”</aqua>",
            "<aqua>🎵 „Ki ér fel hozzá? Senki ma még — {player}, a {value}. szint vándora!”</aqua>",
            "<aqua>🎵 „Volt egyszer egy vándor, ki nem állt meg soha… {player} a neve, s {value} szint a nyoma.”</aqua>"
    };
    /** Vagyon-hős variánsok. */
    private static final String[] WEALTH_VERSES = {
            "<green>🎵 „S az arany? Az arany {player} zsebében csörög — {value}-t számoltak a Számvevők, s elpirultak!”</green>",
            "<green>🎵 „A Bankárszövetség kedvence, {player} — {value} fénylik a könyvekben a neve mellett!”</green>",
            "<green>🎵 „Ócska garas nem hull nyomában — {player} kincse {value}, így szól a fáma!”</green>"
    };
    /** Raid-hős variánsok. */
    private static final String[] RAID_VERSES = {
            "<red>🎵 „S ha dobszó szól, {player} pajzsa dörren — {value} ellenfél hullt már előtte a hadszíntéren!”</red>",
            "<red>🎵 „A Vérháborúk visszhangja él: {player} kardján {value} győzelem fénye ég!”</red>",
            "<red>🎵 „Ne állj útjába, ha kürt rivall — {player} mögött {value} elesett rivális!”</red>"
    };
    /** Zárás-variánsok. */
    private static final String[] CLOSERS = {
            "<light_purple>🎵 „…s ha jövő héten más nevet hoz a szél — gyere vissza, s meghallod, kiét!”</light_purple>",
            "<light_purple>🎵 „Ennyi a dal — a többit írjátok ti, odakint!”</light_purple>",
            "<light_purple>🎵 „A húr elpattan, a hír marad. Jó utat, vándor!”</light_purple>"
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
