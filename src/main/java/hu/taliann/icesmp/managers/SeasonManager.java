package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.storage.PersistentStore;

import hu.taliann.icesmp.storage.YamlStore;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Seasonal league (ideas.md "Szezonális liga"): factions earn points from raid
 * victories and world boss kills over a configurable season. When the season
 * ends, the leading faction is crowned champion and its treasury receives the
 * season reward; points reset and a new season begins. State persists to
 * season.yml; expiry is checked on the global world-events tick.
 */
public final class SeasonManager implements PersistentStore {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final FactionTreasuryManager treasuryManager;
    private final FactionManager factionManager;
    private final File storageFile;
    private final Map<FactionType, Integer> points = new ConcurrentHashMap<>();
    private final AtomicBoolean saveScheduled = new AtomicBoolean(false);

    private volatile long seasonStart = System.currentTimeMillis();

    public SeasonManager(final JavaPlugin plugin, final ConfigManager configManager,
                         final MessageManager messageManager, final FactionTreasuryManager treasuryManager,
                         final FactionManager factionManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.treasuryManager = treasuryManager;
        this.factionManager = factionManager;
        this.storageFile = new File(plugin.getDataFolder(), "season.yml");
        plugin.getDataFolder().mkdirs();
    }

    public void load() {
        points.clear();
        seasonStart = System.currentTimeMillis();

        if (!storageFile.exists()) {
            save();
            return;
        }

        try {
            final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(storageFile);
            seasonStart = yaml.getLong("season.start", System.currentTimeMillis());
            final ConfigurationSection pointsSection = yaml.getConfigurationSection("season.points");
            if (pointsSection != null) {
                for (final String factionKey : pointsSection.getKeys(false)) {
                    final FactionType faction = FactionType.fromInput(factionKey);
                    if (faction != null) {
                        points.put(faction, Math.max(0, pointsSection.getInt(factionKey, 0)));
                    }
                }
            }
        } catch (final Exception exception) {
            plugin.getLogger().severe("Failed to load season.yml: " + exception.getMessage());
        }
    }

    public void save() {
        try {
            final YamlConfiguration yaml = new YamlConfiguration();
            yaml.set("season.start", seasonStart);
            for (final Map.Entry<FactionType, Integer> entry : points.entrySet()) {
                yaml.set("season.points." + entry.getKey().name(), entry.getValue());
            }

            YamlStore.saveAtomic(storageFile, yaml);
        } catch (final IOException exception) {
            plugin.getLogger().severe("Failed to save season.yml: " + exception.getMessage());
        }
    }

    public int getPoints(final FactionType faction) {
        return faction == null ? 0 : points.getOrDefault(faction, 0);
    }

    public long getSeasonEndMillis() {
        final long lengthDays = Math.max(1L, configManager.getLong("world-events.season.length-days", 60L));
        return seasonStart + (lengthDays * 24L * 60L * 60L * 1000L);
    }

    /**
     * Awards league points to a faction (raid victory, world boss kill...).
     *
     * @param faction the scoring faction
     * @param amount the points
     */
    public void addPoints(final FactionType faction, final int amount) {
        if (faction == null || amount <= 0
                || !configManager.getBoolean("world-events.season.enabled", true)) {
            return;
        }

        // B33: a végítélet-hét alatt minden pont-jóváírás lineárisan skálázódik a
        // finálé-maximumig (alapból dupláig az utolsó napon).
        final SeasonFinaleManager finaleRef = seasonFinale;
        final int scaled = finaleRef == null ? amount
                : Math.max(amount, (int) Math.round(amount * finaleRef.leaguePointMultiplier()));
        points.merge(faction, scaled, Integer::sum);
        requestSave();
    }

    /** B33: setter-injected finálé-eszkaláció (a finálé-manager később épül a DI-sorrendben). */
    private volatile SeasonFinaleManager seasonFinale;

    public void setSeasonFinale(final SeasonFinaleManager seasonFinale) {
        this.seasonFinale = seasonFinale;
    }

    /** D17: setter-injected korszakváltás-narrátor (a StatsManager később épül a DI-sorrendben). */
    private volatile SeasonStoryTeller storyTeller;

    public void setStoryTeller(final SeasonStoryTeller storyTeller) {
        this.storyTeller = storyTeller;
    }

    /** D3: setter-injected emlékmű-vésnök. */
    private volatile SeasonMonumentManager monumentManager;

    public void setMonumentManager(final SeasonMonumentManager monumentManager) {
        this.monumentManager = monumentManager;
    }

    /** Debounced async flush: point awards can burst (raid payouts), one write covers them all. */
    private void requestSave() {
        if (saveScheduled.compareAndSet(false, true)) {
            plugin.getServer().getAsyncScheduler().runDelayed(plugin, task -> {
                saveScheduled.set(false);
                save();
            }, 2L, TimeUnit.SECONDS);
        }
    }

    /** Periodic check on the global world-events tick: closes expired seasons. */
    public void tick() {
        if (!configManager.getBoolean("world-events.season.enabled", true)
                || System.currentTimeMillis() < getSeasonEndMillis()) {
            return;
        }

        FactionType champion = null;
        int best = 0;
        boolean tie = false;
        for (final Map.Entry<FactionType, Integer> entry : points.entrySet()) {
            if (entry.getValue() > best) {
                champion = entry.getKey();
                best = entry.getValue();
                tie = false;
            } else if (entry.getValue() == best && best > 0) {
                tie = true;
            }
        }

        // D17: a korszakváltás-narratíva a pont-reset ELŐTT gyűjti a statisztikát.
        final SeasonStoryTeller storyRef = storyTeller;
        if (champion == null || tie || best <= 0) {
            Bukkit.getServer().broadcast(messageManager.getMessage(
                    "season-ended-no-champion",
                    "<gold>🏁 A szezon véget ért bajnok nélkül — új szezon kezdődik!</gold>"
            ));
            if (storyRef != null) {
                storyRef.tellTransition(null);
            }
        } else {
            final double reward = Math.max(0.0D, configManager.getDouble("world-events.season.treasury-reward", 1000.0D));
            if (reward > 0.0D) {
                treasuryManager.deposit(champion, reward);
            }

            Bukkit.getServer().broadcast(messageManager.getMessage(
                    "season-ended",
                    "<gold>🏆 A szezon bajnoka: <white>{champion}</white> ({points} pont)! A frakciókassza <white>{reward}</white> jutalmat kap. Új szezon kezdődik!</gold>",
                    Map.of(
                            "champion", champion.getDisplayName(),
                            "points", String.valueOf(best),
                            "reward", String.valueOf(reward)
                    )
            ));

            // Member-facing spoils: the champion faction's online members get a victory buff,
            // configured item rewards and a celebratory firework — each on their own region thread.
            awardChampionMembers(champion);
            if (storyRef != null) {
                storyRef.tellTransition(champion);
            }
            // D3: a bajnok kőbe vésve — a pont-reset ELŐTT (a hős-toplista még érvényes).
            final SeasonMonumentManager monumentRef = monumentManager;
            if (monumentRef != null) {
                monumentRef.recordSeason(champion);
            }
        }

        points.clear();
        seasonStart = System.currentTimeMillis();
        save();
    }

    /**
     * Grants the champion faction's online members their season spoils: a
     * victory buff, any configured reward items, and a celebratory firework.
     * Runs from tick() on the global scheduler, so every player mutation and
     * the firework spawn hop to that player's own region thread (Folia).
     *
     * @param champion the winning faction
     */
    private void awardChampionMembers(final FactionType champion) {
        final int buffMinutes = Math.max(0, configManager.getInt("world-events.season.champion-buff-minutes", 30));
        final java.util.List<String> rewardItems = configManager.getStringList("world-events.season.champion-reward-items");
        final boolean firework = configManager.getBoolean("world-events.season.champion-firework", true);

        for (final Player online : Bukkit.getOnlinePlayers()) {
            if (factionManager.getFaction(online.getUniqueId()) != champion) {
                continue;
            }

            online.getScheduler().run(plugin, task -> {
                if (buffMinutes > 0) {
                    final int durationTicks = buffMinutes * 60 * 20;
                    online.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, durationTicks, 0, false, true, true));
                    online.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, durationTicks, 0, false, true, true));
                    online.addPotionEffect(new PotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE, durationTicks, 0, false, true, true));
                }

                giveRewardItems(online, rewardItems);

                if (firework) {
                    spawnCelebrationFirework(online);
                }

                online.sendMessage(messageManager.getMessage(
                        "season-champion-member",
                        "<gold>🏆 A frakciód lett a szezon bajnoka — fogadd a győzelmi jutalmadat!</gold>"
                ));
            }, null);
        }
    }

    /** Hands over the configured "MATERIAL:AMOUNT" reward items, dropping any overflow. */
    private void giveRewardItems(final Player player, final java.util.List<String> rewardItems) {
        for (final String entry : rewardItems) {
            final String[] parts = entry.split(":");
            final Material material = Material.matchMaterial(parts[0].trim());
            if (material == null || material.isAir()) {
                continue;
            }
            int amount = 1;
            if (parts.length >= 2) {
                try {
                    amount = Math.max(1, Integer.parseInt(parts[1].trim()));
                } catch (final NumberFormatException ignored) {
                    // Malformed amount: give one.
                }
            }
            final Map<Integer, ItemStack> leftovers = player.getInventory().addItem(new ItemStack(material, amount));
            leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        }
    }

    /** Spawns a short celebratory firework at the player (must run on the player's region thread). */
    private void spawnCelebrationFirework(final Player player) {
        final Firework firework = player.getWorld().spawn(player.getLocation(), Firework.class);
        final FireworkMeta meta = firework.getFireworkMeta();
        meta.addEffect(org.bukkit.FireworkEffect.builder()
                .withColor(Color.YELLOW, Color.WHITE)
                .withFade(Color.ORANGE)
                .with(org.bukkit.FireworkEffect.Type.BALL_LARGE)
                .trail(true)
                .build());
        meta.setPower(1);
        firework.setFireworkMeta(meta);
    }
}
