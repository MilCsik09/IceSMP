package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.storage.PersistentStore;
import hu.taliann.icesmp.storage.YamlStore;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/** Periodic Bankárszövetség chronicle plus durable extraordinary issues. */
public final class ChronicleManager implements PersistentStore {
    private static final List<String> OPENERS = List.of(
            "&6&l— A Bankárszövetség Krónikája —&r &7Feljegyeztetett, ahogy láttuk:",
            "&6&l— A Bankárszövetség Krónikája —&r &7A számok nem hazudnak. A krónikás néha.",
            "&6&l— A Bankárszövetség Krónikája —&r &7Új lap fordul a Korszakok Könyvében:",
            "&6&l— A Bankárszövetség Krónikája —&r &7Amit a Creutzér látott, azt mi lejegyeztük:",
            "&6&l— A Bankárszövetség Krónikája —&r &7Tinta, vér és kamat — e héten is volt mindhárom:",
            "&6&l— A Bankárszövetség Krónikája —&r &7A Fa gyűrűt nő, a Könyv lapot — mi pedig írunk:",
            "&6&l— A Bankárszövetség Krónikája —&r &7Hét nap hírei, három tollvonásban:",
            "&6&l— A Bankárszövetség Krónikája —&r &7Ki emelkedett, ki bukott — a tinta tudja:",
            "&6&l— A Bankárszövetség Krónikája —&r &7A karaván port kavar, a hír megmarad:",
            "&6&l— A Bankárszövetség Krónikája —&r &7Olvasd figyelmesen — rólad is szólhat:");
    private static final List<String> CLOSERS = List.of(
            "&8A krónika hiteléért a Számvevők felelnek. Többnyire.",
            "&8Aki nevét e lapon látja, tartozik nekünk egy itallal.",
            "&8A következő lapig — vigyázz a lelkedre és a számládra.",
            "&8Feljegyezve Caldesterában, a Botera-negyed árnyékában.",
            "&8Ha tévedtünk volna: a helyesbítés díja három Creutzér.",
            "&8A Könyv emlékezik. Mi csak körmölünk.",
            "&8E lapot az Idegen is olvassa. Legalábbis reméljük, hogy csak olvassa.",
            "&8A jövő heti számot a jelen tettei írják. Rajta.",
            "&8Sajtóhibáért felelősséget a Néma Királynő vállal.");
    private static volatile ChronicleManager active;

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final StatsManager statsManager;
    private final SeasonManager seasonManager;
    private final MessageManager messageManager;
    private final File storageFile;
    private final Set<String> extraordinaryReceipts = new LinkedHashSet<>();
    private volatile long lastPublishedAt;
    private volatile List<String> lastIssue = List.of();

    public ChronicleManager(final JavaPlugin plugin, final ConfigManager configManager,
                            final StatsManager statsManager, final SeasonManager seasonManager,
                            final MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.statsManager = statsManager;
        this.seasonManager = seasonManager;
        this.messageManager = messageManager;
        this.storageFile = new File(plugin.getDataFolder(), "chronicle.yml");
        plugin.getDataFolder().mkdirs();
        active = this;
    }

    public static ChronicleManager current() { return active; }

    @Override
    public synchronized void load() {
        lastPublishedAt = 0L;
        lastIssue = List.of();
        extraordinaryReceipts.clear();
        if (!storageFile.exists()) return;
        final YamlConfiguration yaml = YamlStore.loadTracked(storageFile, plugin.getLogger());
        lastPublishedAt = yaml.getLong("last-published", 0L);
        lastIssue = List.copyOf(yaml.getStringList("last-issue"));
        extraordinaryReceipts.addAll(yaml.getStringList("extraordinary-receipts"));
    }

    @Override
    public synchronized void save() {
        try {
            final YamlConfiguration yaml = new YamlConfiguration();
            yaml.set("last-published", lastPublishedAt);
            yaml.set("last-issue", lastIssue);
            yaml.set("extraordinary-receipts", List.copyOf(extraordinaryReceipts));
            YamlStore.saveAtomic(storageFile, yaml);
        } catch (final IOException exception) {
            plugin.getLogger().severe("Failed to save chronicle.yml: " + exception.getMessage());
            throw new java.io.UncheckedIOException("Failed to save chronicle.yml", exception);
        }
    }

    public List<String> getLastIssue() { return lastIssue; }

    public void tick() {
        if (!configManager.getBoolean("chronicle.enabled", true)) return;
        final long intervalMillis = Math.max(1L, configManager.getLong("chronicle.interval-days", 7L)) * 86_400_000L;
        final long now = System.currentTimeMillis();
        if (lastPublishedAt == 0L) {
            lastPublishedAt = now;
            save();
            return;
        }
        if (now - lastPublishedAt >= intervalMillis) publish();
    }

    public synchronized void publish() {
        lastPublishedAt = System.currentTimeMillis();
        final List<String> lines = new ArrayList<>();
        lines.add(OPENERS.get(ThreadLocalRandom.current().nextInt(OPENERS.size())));
        final List<FactionType> order = new ArrayList<>(List.of(FactionType.values()));
        order.sort((a, b) -> Integer.compare(seasonManager.getPoints(b), seasonManager.getPoints(a)));
        final StringBuilder standings = new StringBuilder("&e⚔ Liga-állás: ");
        for (int i = 0; i < order.size(); i++) {
            final FactionType faction = order.get(i);
            standings.append(i == 0 ? "&6" : "&7").append(faction.getDisplayName())
                    .append(" &f").append(seasonManager.getPoints(faction));
            if (i < order.size() - 1) standings.append(" &8| ");
        }
        lines.add(standings.toString());
        appendTop(lines, StatsManager.Category.LEVEL, "&b✦ A leghatalmasabbak (szint): ");
        appendTop(lines, StatsManager.Category.WEALTH, "&a✦ A leggazdagabbak (a Számvevők szerint): ");
        appendTop(lines, StatsManager.Category.RAID_KILLS, "&c✦ A háború bajnokai (raid-ölések): ");
        lines.add(CLOSERS.get(ThreadLocalRandom.current().nextInt(CLOSERS.size())));
        lastIssue = List.copyOf(lines);
        save();
        broadcastIssue(lines);
    }

    /** Durable one-shot issue used by history-defining world events such as Olethropyla opening. */
    public synchronized boolean publishExtraordinaryOnce(final String receiptId, final List<String> issueLines) {
        if (receiptId == null || receiptId.isBlank() || issueLines == null || issueLines.isEmpty()) return false;
        if (extraordinaryReceipts.contains(receiptId)) return true;
        final List<String> previousIssue = lastIssue;
        final long previousPublished = lastPublishedAt;
        extraordinaryReceipts.add(receiptId);
        lastPublishedAt = System.currentTimeMillis();
        lastIssue = List.copyOf(issueLines);
        try {
            save();
        } catch (final RuntimeException failure) {
            extraordinaryReceipts.remove(receiptId);
            lastIssue = previousIssue;
            lastPublishedAt = previousPublished;
            throw failure;
        }
        broadcastIssue(lastIssue);
        return true;
    }

    private void broadcastIssue(final List<String> lines) {
        net.kyori.adventure.text.Component issue = net.kyori.adventure.text.Component.empty();
        for (final String line : lines) {
            issue = issue.append(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                    .legacyAmpersand().deserialize(line)).append(net.kyori.adventure.text.Component.newline());
        }
        issue = issue.append(messageManager.getMessage("chronicle-footer",
                "<gray>(Visszaolvasható bármikor: <white>/kronika</white>)</gray>"));
        Bukkit.getServer().broadcast(issue);
    }

    private void appendTop(final List<String> lines, final StatsManager.Category category, final String prefix) {
        final List<StatsManager.Entry> top = statsManager.top(category, 3);
        if (top.isEmpty()) return;
        final StringBuilder row = new StringBuilder(prefix);
        for (int i = 0; i < top.size(); i++) {
            final StatsManager.Entry entry = top.get(i);
            final String value = switch (category) {
                case LEVEL -> String.valueOf(entry.level());
                case WEALTH -> String.format(java.util.Locale.ROOT, "%.0f", entry.wealth());
                case RAID_KILLS -> String.valueOf(entry.raidKills());
            };
            row.append("&f").append(entry.name()).append(" &7(").append(value).append(")");
            if (i < top.size() - 1) row.append("&8, ");
        }
        lines.add(row.toString());
    }
}
