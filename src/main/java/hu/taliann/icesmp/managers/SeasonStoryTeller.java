package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.utils.MessageManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * D17 — Szezon-átvezető broadcast-történetek (lore: a korszak-átmenetek krónikás-hangú
 * narrálása — kódex VIII.). Szezonzáráskor a száraz pontszám-reset helyett rövid,
 * késleltetett, narratív üzenet-sorozat vezeti át a világot az új korszakba: nyitány
 * (title + chat), az előző szezon hősei (StatsManager top-1 szint/vagyon/raid), a bajnok
 * frakció megidézése, zárás. Minden sor sablon-variánsokból sorsolódik (messages-kulcsokkal
 * felülírható), így nem gépies az ismétlődés.
 *
 * <p>Folia: a hívás a SeasonManager tickjéről (globális scheduler) érkezik; a késleltetett
 * sorok a globális régió-scheduleren futnak, a broadcast/title szál-biztos Adventure-hívás.
 * Minden kulcs élőben olvasódik (world-events.season.transition-story.*).
 */
public final class SeasonStoryTeller {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final StatsManager statsManager;
    private final MessageManager messageManager;

    public SeasonStoryTeller(final JavaPlugin plugin, final ConfigManager configManager,
                             final StatsManager statsManager, final MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.statsManager = statsManager;
        this.messageManager = messageManager;
    }

    /** Nyitány-variánsok (messages-kulcs: season-story-opener-1..3). */
    private static final String[] OPENERS = {
            "<dark_aqua>📖 A Korszakok Könyvének lapja átfordult. A krónikások új fejezet fölé hajolnak…</dark_aqua>",
            "<dark_aqua>📖 A Fa gyűrűt növesztett — egy korszak a múlté, s a tinta még nedves az új lapon.</dark_aqua>",
            "<dark_aqua>📖 Elhalt a régi szezon utolsó kürtszava. A Számvevők lezárták a könyveket.</dark_aqua>",
            "<dark_aqua>📖 A Lapforduló Őre elhagyta a falakat — a korszak, amit őrzött, a múlté.</dark_aqua>",
            "<dark_aqua>📖 A tinta megszáradt. Ami történt, megtörtént — a Könyv pedig sosem felejt.</dark_aqua>",
            "<dark_aqua>📖 Egy korszaknyi kürtszó, kalapácsütés és suttogás ült el most egyetlen sóhajban.</dark_aqua>",
            "<dark_aqua>📖 A krónikások homokot szórtak a friss tintára. Ez a lap kész. A világ lapozott.</dark_aqua>",
            "<dark_aqua>📖 Így ér véget minden korszak: nem robajjal — egy lap halk zizzenésével.</dark_aqua>"
    };

    /** Zárás-variánsok (messages-kulcs: season-story-closer-1..3). */
    private static final String[] CLOSERS = {
            "<gold>📖 Új korszak kezdődik — a lap üres, és a tiétek. Írjátok jól!</gold>",
            "<gold>📖 A Királynő álma tovább gomolyog… de az új lap fehér. Ki mer elsőként írni rá?</gold>",
            "<gold>📖 Minden korszak azzal kezdődik, hogy valaki lépni mer. Az új szezon megnyílt!</gold>",
            "<gold>📖 A krónikások új tollat faragnak — vajon kinek a nevét koptatja majd el?</gold>",
            "<gold>📖 Hallod? A Könyv új lapja zizeg. Azt kérdezi: na és te, mit teszel ma?</gold>",
            "<gold>📖 Az új lap első szava mindig ugyanaz: „Egyszer…” — a többit ti írjátok.</gold>",
            "<gold>📖 Kovácsok, halászok, hadvezérek — a Könyv nem válogat. Csak a tétlent hagyja ki.</gold>",
            "<gold>📖 A Fa új gyűrűt kezd. Nőjetek vele!</gold>"
    };

    /**
     * A korszakváltás-narratíva kihirdetése (a SeasonManager szezonzárás-hookja hívja,
     * KÖZVETLENÜL a bajnok-broadcast után). A sorok line-delay-seconds ütemben,
     * késleltetve mennek ki; az első sor title-ként is megjelenik.
     *
     * @param champion a szezon bajnoka (null = nem volt bajnok)
     */
    public void tellTransition(final FactionType champion) {
        if (!configManager.getBoolean("world-events.season.transition-story.enabled", true)) {
            return;
        }
        final List<Component> lines = new ArrayList<>();

        final int openerIndex = ThreadLocalRandom.current().nextInt(OPENERS.length);
        lines.add(messageManager.getMessage("season-story-opener-" + (openerIndex + 1), OPENERS[openerIndex]));

        // Az előző szezon hősei (top-1 szint / vagyon / raid) — csak a létező adatok kerülnek be.
        final List<StatsManager.Entry> topLevel = statsManager.top(StatsManager.Category.LEVEL, 1);
        if (!topLevel.isEmpty()) {
            lines.add(messageManager.getMessage("season-story-hero-level",
                    "<aqua>✦ A letűnt korszak legnagyobb alakja <white>{player}</white> volt — nevét a krónikák megőrzik ({level}. szint).</aqua>",
                    Map.of("player", topLevel.get(0).name(), "level", String.valueOf(topLevel.get(0).level()))));
        }
        final List<StatsManager.Entry> topWealth = statsManager.top(StatsManager.Category.WEALTH, 1);
        if (!topWealth.isEmpty()) {
            lines.add(messageManager.getMessage("season-story-hero-wealth",
                    "<green>✦ A Számvevők szerint a leggazdagabb kéz <white>{player}</white>é volt ({wealth}) — vajon az új lapon is arany folyik az ujjai közt?</green>",
                    Map.of("player", topWealth.get(0).name(),
                            "wealth", String.format(Locale.ROOT, "%.0f", topWealth.get(0).wealth()))));
        }
        final List<StatsManager.Entry> topRaid = statsManager.top(StatsManager.Category.RAID_KILLS, 1);
        if (!topRaid.isEmpty() && topRaid.get(0).raidKills() > 0) {
            lines.add(messageManager.getMessage("season-story-hero-raid",
                    "<red>✦ A háború bajnokáról, <white>{player}</white>ról ({kills} raid-ölés) balladák születnek majd.</red>",
                    Map.of("player", topRaid.get(0).name(), "kills", String.valueOf(topRaid.get(0).raidKills()))));
        }

        if (champion != null) {
            lines.add(messageManager.getMessage("season-story-champion",
                    "<gold>✦ És a lap aljára vésve: a korszak koronáját a(z) <white>{champion}</white> viselte. A Könyv emlékezik.</gold>",
                    Map.of("champion", champion.getDisplayName())));
        } else {
            lines.add(messageManager.getMessage("season-story-no-champion",
                    "<gray>✦ Bajnok nélkül zárult a lap — a Könyv üres koronát rajzolt a margóra. Talán most…</gray>"));
        }

        final int closerIndex = ThreadLocalRandom.current().nextInt(CLOSERS.length);
        lines.add(messageManager.getMessage("season-story-closer-" + (closerIndex + 1), CLOSERS[closerIndex]));

        // Kihirdetés: az első sor title-ként is (intro-stílus), a többi késleltetett chat-sor.
        final long delayTicks = Math.max(1L,
                configManager.getLong("world-events.season.transition-story.line-delay-seconds", 4L)) * 20L;
        Bukkit.getServer().showTitle(net.kyori.adventure.title.Title.title(
                messageManager.getMessage("season-story-title", "<dark_aqua>📖 Lapforduló</dark_aqua>"),
                messageManager.getMessage("season-story-subtitle", "<gray>Új korszak kezdődik…</gray>")));
        for (int i = 0; i < lines.size(); i++) {
            final Component line = lines.get(i);
            if (i == 0) {
                Bukkit.getServer().broadcast(line);
                continue;
            }
            plugin.getServer().getGlobalRegionScheduler().runDelayed(plugin,
                    task -> Bukkit.getServer().broadcast(line), delayTicks * i);
        }
    }
}
