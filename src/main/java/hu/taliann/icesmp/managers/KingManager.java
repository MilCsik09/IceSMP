package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.storage.PersistentStore;

import hu.taliann.icesmp.storage.YamlStore;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Faction kings and elections.
 * Every non-excluded faction elects a king by simple plurality: members vote
 * with /faction king vote; whoever reaches the configured minimum vote count
 * and leads the tally is crowned. Votes reset when the configured term
 * expires, starting a fresh election. The king commands the faction treasury
 * (withdraw) and may declare raids.
 */
public final class KingManager implements PersistentStore {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final FactionManager factionManager;
    private final MessageManager messageManager;
    private final File storageFile;
    private final Map<FactionType, UUID> kings = new ConcurrentHashMap<>();
    private final Map<FactionType, Map<UUID, UUID>> votes = new ConcurrentHashMap<>();
    private final Map<FactionType, Long> electionStart = new ConcurrentHashMap<>();

    public KingManager(final JavaPlugin plugin, final ConfigManager configManager,
                       final FactionManager factionManager, final MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.factionManager = factionManager;
        this.messageManager = messageManager;
        this.storageFile = new File(plugin.getDataFolder(), "kings.yml");
        plugin.getDataFolder().mkdirs();
    }

    public void load() {
        kings.clear();
        votes.clear();
        electionStart.clear();

        if (!storageFile.exists()) {
            return;
        }

        try {
            final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(storageFile);
            final ConfigurationSection kingsSection = yaml.getConfigurationSection("kings");
            if (kingsSection == null) {
                return;
            }

            for (final String factionKey : kingsSection.getKeys(false)) {
                final FactionType faction = FactionType.fromInput(factionKey);
                if (faction == null) {
                    continue;
                }

                final String rawKing = kingsSection.getString(factionKey + ".king");
                if (rawKing != null && !rawKing.isBlank()) {
                    try {
                        kings.put(faction, UUID.fromString(rawKing));
                    } catch (final IllegalArgumentException ignored) {
                        // Skip malformed UUIDs.
                    }
                }

                electionStart.put(faction, kingsSection.getLong(factionKey + ".election-start", System.currentTimeMillis()));

                final ConfigurationSection votesSection = kingsSection.getConfigurationSection(factionKey + ".votes");
                if (votesSection != null) {
                    final Map<UUID, UUID> factionVotes = new ConcurrentHashMap<>();
                    for (final String voterKey : votesSection.getKeys(false)) {
                        try {
                            factionVotes.put(UUID.fromString(voterKey), UUID.fromString(votesSection.getString(voterKey, "")));
                        } catch (final IllegalArgumentException ignored) {
                            // Skip malformed entries.
                        }
                    }
                    votes.put(faction, factionVotes);
                }
            }

            plugin.getLogger().info("Loaded faction kings and election votes.");
        } catch (final Exception exception) {
            plugin.getLogger().severe("Failed to load kings.yml: " + exception.getMessage());
        }
    }

    public void save() {
        try {
            final YamlConfiguration yaml = new YamlConfiguration();
            for (final FactionType faction : FactionType.values()) {
                final String basePath = "kings." + faction.name();
                final UUID king = kings.get(faction);
                if (king != null) {
                    yaml.set(basePath + ".king", king.toString());
                }
                yaml.set(basePath + ".election-start", electionStart.getOrDefault(faction, System.currentTimeMillis()));
                for (final Map.Entry<UUID, UUID> vote : votes.getOrDefault(faction, Map.of()).entrySet()) {
                    yaml.set(basePath + ".votes." + vote.getKey(), vote.getValue().toString());
                }
            }

            YamlStore.saveAtomic(storageFile, yaml);
        } catch (final IOException exception) {
            plugin.getLogger().severe("Failed to save kings.yml: " + exception.getMessage());
        }
    }

    public UUID getKing(final FactionType faction) {
        return faction == null ? null : kings.get(faction);
    }

    /**
     * Checks whether the player is the crowned king of their own faction.
     *
     * @param player the player
     * @return true if the player wears the crown
     */
    public boolean isKing(final Player player) {
        if (player == null) {
            return false;
        }

        final FactionType faction = factionManager.getFaction(player.getUniqueId());
        return player.getUniqueId().equals(kings.get(faction));
    }

    public boolean isFactionExcluded(final FactionType faction) {
        final List<String> excluded = configManager.getStringList("factions.kings.excluded");
        final List<String> effective = excluded.isEmpty() ? List.of("NEUTRAL") : excluded;
        return faction == null || effective.stream().anyMatch(name -> name.equalsIgnoreCase(faction.name()));
    }

    /**
     * Casts (or changes) the voter's vote for a same-faction candidate.
     * Expired terms reset the ballot before counting; reaching the configured
     * minimum and leading the tally crowns the candidate immediately.
     *
     * @param voter the voting player
     * @param candidate the candidate's UUID
     * @return true if the vote was accepted
     */
    public boolean vote(final Player voter, final UUID candidate) {
        final FactionType faction = factionManager.getFaction(voter.getUniqueId());
        if (isFactionExcluded(faction) || candidate == null) {
            return false;
        }

        if (factionManager.getFaction(candidate) != faction) {
            return false;
        }

        resetExpiredTerm(faction);
        votes.computeIfAbsent(faction, key -> new ConcurrentHashMap<>()).put(voter.getUniqueId(), candidate);
        recount(faction);
        save();
        return true;
    }

    /**
     * Gets the current vote tally of a faction (candidate -> votes).
     *
     * @param faction the faction
     * @return tally map
     */
    public Map<UUID, Integer> getTally(final FactionType faction) {
        final Map<UUID, Integer> tally = new HashMap<>();
        for (final UUID candidate : votes.getOrDefault(faction, Map.of()).values()) {
            tally.merge(candidate, 1, Integer::sum);
        }
        return tally;
    }

    /**
     * Admin override: crowns (or with null, dethrones) a king directly.
     *
     * @param faction the faction
     * @param king the new king, or null to clear the throne
     */
    public void setKing(final FactionType faction, final UUID king) {
        if (faction == null) {
            return;
        }

        if (king == null) {
            kings.remove(faction);
        } else {
            kings.put(faction, king);
        }
        votes.remove(faction);
        electionStart.put(faction, System.currentTimeMillis());
        save();
    }

    private void resetExpiredTerm(final FactionType faction) {
        final long termDays = Math.max(1L, configManager.getLong("factions.kings.term-days", 7L));
        final long start = electionStart.computeIfAbsent(faction, key -> System.currentTimeMillis());
        if (System.currentTimeMillis() - start > termDays * 24L * 60L * 60L * 1000L) {
            votes.remove(faction);
            electionStart.put(faction, System.currentTimeMillis());
        }
    }

    private void recount(final FactionType faction) {
        final int minVotes = Math.max(1, configManager.getInt("factions.kings.min-votes", 2));
        UUID leader = null;
        int leaderVotes = 0;
        for (final Map.Entry<UUID, Integer> entry : getTally(faction).entrySet()) {
            // Csak AKTUÁLIS frakciótag koronázható — a frakcióváltás után bent ragadt
            // szavazatok ne ültethessenek idegen "királyt" a trónra.
            if (factionManager.getFaction(entry.getKey()) != faction) {
                continue;
            }
            if (entry.getValue() > leaderVotes) {
                leader = entry.getKey();
                leaderVotes = entry.getValue();
            }
        }

        if (leader == null || leaderVotes < minVotes || leader.equals(kings.get(faction))) {
            return;
        }

        kings.put(faction, leader);

        // Folia: the server-wide broadcast and the (potentially blocking) offline-name lookup
        // must not run on the voting player's region thread — hop to the global region scheduler.
        final UUID crowned = leader;
        plugin.getServer().getGlobalRegionScheduler().run(plugin, task -> {
            final String kingName = Bukkit.getOfflinePlayer(crowned).getName();
            Bukkit.getServer().broadcast(messageManager.getMessage(
                    "faction-king-crowned",
                    "<gold>👑 {faction} új uralkodót választott: <white>{king}</white>!</gold>",
                    Map.of(
                            "faction", faction.getDisplayName(),
                            "king", kingName == null ? crowned.toString().substring(0, 8) : kingName
                    )
            ));
        });
    }
}
