package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.storage.PersistentStore;
import hu.taliann.icesmp.storage.YamlStore;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A Vének Tanácsa (gameplay-audit 10. lelet, tulaj-jóváhagyással): a Menedéknek
 * nincs királya (az Armageddon-ultimátum tiltja a fegyvert) — helyette hetente
 * választott, legfeljebb {@code seats} fős TANÁCS gyakorolja a gazdasági jogokat:
 * kassza-kivét (saját, kisebb napi kerettel), karaván-indítás és a békés
 * zászlóshajó, a Vásár-hét (Creutzér piaci díj-kedvezmény ablak). Raidet a tanács
 * NEM hirdethet. Szavazás: {@code /tanacs szavaz <játékos>} — hetente egy szavazat
 * (átszavazható), a hét fordulóján a szavazatok nullázódnak. A tanács a MOSTANI
 * hét szavazatainak élő top-{@code seats} listája (legalább 1 szavazat kell).
 * Perzisztencia: council.yml (szavazó → jelölt + hét-kulcs).
 */
public final class CouncilManager implements PersistentStore {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final FactionManager factionManager;
    private final MessageManager messageManager;
    private final File storageFile;

    /** Szavazó → jelölt (a futó hét szavazatai; konkurens — bármely régió-szál írhatja). */
    private final Map<UUID, UUID> votes = new ConcurrentHashMap<>();
    private volatile long weekKey = -1L;

    public CouncilManager(final JavaPlugin plugin, final ConfigManager configManager,
                          final FactionManager factionManager, final MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.factionManager = factionManager;
        this.messageManager = messageManager;
        this.storageFile = new File(plugin.getDataFolder(), "council.yml");
        plugin.getDataFolder().mkdirs();
    }

    public boolean isEnabled() {
        return configManager.getBoolean("factions.council.enabled", true);
    }

    private static long currentWeek() {
        return java.time.LocalDate.now().toEpochDay() / 7L;
    }

    /** Hét-forduló: a szavazatok nullázása (a world-events tick hívja). */
    public void tick() {
        final long week = currentWeek();
        if (week != weekKey) {
            weekKey = week;
            if (!votes.isEmpty()) {
                votes.clear();
                Bukkit.getServer().broadcast(messageManager.getMessage(
                        "council-week-reset",
                        "<gold>⚖ Új hét — a Menedék tanács-választása újraindult! Szavazz: <white>/tanacs szavaz <játékos></white></gold>"));
            }
            save();
        }
    }

    /**
     * Szavazat leadása (a hívó régió-szálán). A szavazó és a jelölt is NEUTRAL kell
     * legyen; hetente egy szavazat, ami átszavazással felülíródik.
     *
     * @return üzenet-kulcs hibára, vagy null sikerre
     */
    public String vote(final Player voter, final Player target) {
        if (!isEnabled()) {
            return "council-disabled";
        }
        if (!factionManager.isMember(voter.getUniqueId(), FactionType.NEUTRAL)) {
            return "council-neutral-only";
        }
        if (!factionManager.isMember(target.getUniqueId(), FactionType.NEUTRAL)) {
            return "council-target-not-neutral";
        }
        votes.put(voter.getUniqueId(), target.getUniqueId());
        save();
        return null;
    }

    /** A futó hét tanácsa: a legtöbb szavazatot kapott NEUTRAL játékosok (top-seats). */
    public List<UUID> councillors() {
        if (!isEnabled()) {
            return List.of();
        }
        final Map<UUID, Integer> tally = new ConcurrentHashMap<>();
        for (final Map.Entry<UUID, UUID> vote : votes.entrySet()) {
            if (factionManager.isMember(vote.getKey(), FactionType.NEUTRAL)
                    && factionManager.isMember(vote.getValue(), FactionType.NEUTRAL)) {
                tally.merge(vote.getValue(), 1, Integer::sum);
            }
        }
        final int seats = Math.max(1, configManager.getInt("factions.council.seats", 3));
        return tally.entrySet().stream()
                .filter(entry -> factionManager.isMember(entry.getKey(), FactionType.NEUTRAL))
                .sorted(Comparator.<Map.Entry<UUID, Integer>>comparingInt(Map.Entry::getValue).reversed()
                        .thenComparing(entry -> entry.getKey().toString()))
                .limit(seats)
                .map(Map.Entry::getKey)
                .toList();
    }

    /** Tanácstag-e a játékos (a jog-kapuk hívják — konkurens olvasás). */
    public boolean isCouncillor(final UUID playerId) {
        return playerId != null && councillors().contains(playerId);
    }

    /** A jelölt kapott szavazatai (info-kijelzés). */
    public int votesFor(final UUID playerId) {
        int count = 0;
        for (final Map.Entry<UUID, UUID> vote : votes.entrySet()) {
            if (vote.getValue().equals(playerId)
                    && factionManager.isMember(vote.getKey(), FactionType.NEUTRAL)
                    && factionManager.isMember(playerId, FactionType.NEUTRAL)) {
                count++;
            }
        }
        return count;
    }

    /** A faction switch invalidates both the player's ballot and candidacy immediately. */
    public synchronized void onMembershipChange(final UUID playerId) {
        if (playerId == null) {
            return;
        }
        final boolean removedVote = votes.remove(playerId) != null;
        final boolean removedCandidacy = votes.entrySet().removeIf(
                entry -> entry.getValue().equals(playerId));
        if (removedVote || removedCandidacy) {
            save();
        }
    }

    @Override
    public synchronized void load() {
        votes.clear();
        weekKey = currentWeek();
        if (!storageFile.exists()) {
            return;
        }
        try {
            final YamlConfiguration yaml = hu.taliann.icesmp.storage.YamlStore.loadTracked(storageFile, plugin.getLogger());
            // Csak a FUTÓ hét szavazatai élnek túl egy restartot.
            if (yaml.getLong("week", -1L) != weekKey) {
                return;
            }
            final ConfigurationSection section = yaml.getConfigurationSection("votes");
            if (section != null) {
                for (final String voter : section.getKeys(false)) {
                    try {
                        votes.put(UUID.fromString(voter), UUID.fromString(section.getString(voter, "")));
                    } catch (final IllegalArgumentException ignored) {
                        // Sérült sor — kihagyjuk.
                    }
                }
            }
        } catch (final Exception exception) {
            plugin.getLogger().severe("Nem sikerült betölteni a council.yml-t: " + exception.getMessage());
        }
    }

    @Override
    public synchronized void save() {
        try {
            final YamlConfiguration yaml = new YamlConfiguration();
            yaml.set("week", weekKey);
            for (final Map.Entry<UUID, UUID> entry : votes.entrySet()) {
                yaml.set("votes." + entry.getKey(), entry.getValue().toString());
            }
            YamlStore.saveAtomic(storageFile, yaml);
        } catch (final IOException exception) {
            plugin.getLogger().severe("Nem sikerült menteni a council.yml-t: " + exception.getMessage());
        }
    }

    /** Az info-kijelzéshez: név-lista a tanácsról (offline nevekkel). */
    public List<String> councillorNames() {
        final List<String> names = new ArrayList<>();
        for (final UUID id : councillors()) {
            final String name = Bukkit.getOfflinePlayer(id).getName();
            names.add((name == null ? id.toString().substring(0, 8) : name) + " (" + votesFor(id) + " szavazat)");
        }
        return names;
    }
}
