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
public final class SeasonManager implements PersistentStore, org.bukkit.event.Listener {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final FactionTreasuryManager treasuryManager;
    private final FactionManager factionManager;
    private final File storageFile;
    private final Map<FactionType, Integer> points = new ConcurrentHashMap<>();
    /** J9 — fejezet-sorszám: a szezon = story-fejezet; váltáskor nő, perzisztens. */
    private volatile int seasonNumber = 1;
    /** G16 — nagydöntő-hétvége: bejelentés-flag (szezononként egyszer, volatilis). */
    private volatile boolean grandFinaleAnnounced;
    private final AtomicBoolean saveScheduled = new AtomicBoolean(false);
    /**
     * Gameplay-audit: a szezonzáráskor OFFLINE bajnok-tagok függő jutalma (perzisztens) —
     * belépéskor kapják meg a tárgy-jutalmat, ahogy a profession-weekly pending mintája.
     */
    private final java.util.Set<java.util.UUID> pendingChampionSpoils = ConcurrentHashMap.newKeySet();

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
            seasonNumber = Math.max(1, yaml.getInt("season.number", 1));
            final ConfigurationSection pointsSection = yaml.getConfigurationSection("season.points");
            if (pointsSection != null) {
                for (final String factionKey : pointsSection.getKeys(false)) {
                    final FactionType faction = FactionType.fromInput(factionKey);
                    if (faction != null) {
                        points.put(faction, Math.max(0, pointsSection.getInt(factionKey, 0)));
                    }
                }
            }
            pendingChampionSpoils.clear();
            for (final String uuid : yaml.getStringList("season.pending-champion-spoils")) {
                try {
                    pendingChampionSpoils.add(java.util.UUID.fromString(uuid));
                } catch (final IllegalArgumentException ignored) {
                    // Sérült bejegyzés — kihagyjuk.
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
            yaml.set("season.number", seasonNumber);
            for (final Map.Entry<FactionType, Integer> entry : points.entrySet()) {
                yaml.set("season.points." + entry.getKey().name(), entry.getValue());
            }
            if (!pendingChampionSpoils.isEmpty()) {
                yaml.set("season.pending-champion-spoils",
                        pendingChampionSpoils.stream().map(java.util.UUID::toString).toList());
            }

            YamlStore.saveAtomic(storageFile, yaml);
        } catch (final IOException exception) {
            plugin.getLogger().severe("Failed to save season.yml: " + exception.getMessage());
        }
    }

    public int getPoints(final FactionType faction) {
        return faction == null ? 0 : points.getOrDefault(faction, 0);
    }

    /**
     * G16 — a nagydöntő-ablak: a szezon utolsó `top2-window-hours` órája (alap 48 —
     * a záró hétvége). Ekkor a top2 frakció pont-jóváírásai duplán számítanak.
     */
    public boolean isGrandFinaleWindow() {
        if (!configManager.getBoolean("world-events.season-finale.top2-enabled", true)) {
            return false;
        }
        final long windowMillis = Math.max(1, configManager.getInt(
                "world-events.season-finale.top2-window-hours", 48)) * 3_600_000L;
        final long remaining = getSeasonEndMillis() - System.currentTimeMillis();
        return remaining > 0 && remaining <= windowMillis;
    }

    /** A liga-tábla első két helyezettje (pont szerint csökkenő). */
    public java.util.List<FactionType> topTwo() {
        return points.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(2)
                .map(Map.Entry::getKey)
                .toList();
    }

    /** G16 — az ablak nyíltakor egyszeri szerver-broadcast a párosítással. */
    private void announceGrandFinale() {
        if (grandFinaleAnnounced || !isGrandFinaleWindow()) {
            return;
        }
        grandFinaleAnnounced = true;
        final java.util.List<FactionType> top = topTwo();
        if (top.size() < 2) {
            return;
        }
        Bukkit.getServer().broadcast(messageManager.getMessage(
                "season-grand-finale",
                "<gold>🏟 NAGYDÖNTŐ! A krónikák ítélnek: <white>{first}</white> ⚔ <white>{second}</white> — a záró hétvégén a két éllovas minden liga-pontja DUPLÁN számít! Királyok, hirdessetek raidet!</gold>",
                Map.of("first", top.get(0).getDisplayName(), "second", top.get(1).getDisplayName())));
    }

    /** J9 — az aktuális fejezet (szezon) sorszáma; a quest `chapter:` mező erre szűr. */
    /** A futó szezon hányadik napja (1-től; a quests min/max-season-day kapuja használja). */
    public int getSeasonDay() {
        return (int) Math.max(1L, (System.currentTimeMillis() - seasonStart) / 86_400_000L + 1L);
    }

    public int getSeasonNumber() {
        return seasonNumber;
    }

    /** A futó szezon kezdő-bélyege — stabil szezon-azonosító (a hossz élő átírása sem mozdítja). */
    public long getSeasonStart() {
        return seasonStart;
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
        addPoints(faction, amount, "other");
    }

    /**
     * Awards league points from a named source. A tulaj-döntés szerinti aszimmetrikus
     * liga: a 2+1+1 frakció-felállásban mindenki a SAJÁT identitás-útján pontoz
     * erősebben — a forrás-súlyt a world-events.season.source-weights.&lt;forrás&gt;.&lt;frakció&gt;
     * mátrix adja (default 1.0; 0 = a forrás nem ér pontot annak a frakciónak).
     * A súly a NYERS pontra hat, a B33/G16 idő-szorzók UTÁNA jönnek.
     *
     * @param faction the scoring faction
     * @param amount the raw points
     * @param source the point source key (raid, world-boss, community, cleanse, duel, spy…)
     */
    public void addPoints(final FactionType faction, final int amount, final String source) {
        if (faction == null || amount <= 0
                || !configManager.getBoolean("world-events.season.enabled", true)) {
            return;
        }
        final double weight = Math.max(0.0D, configManager.getDouble(
                "world-events.season.source-weights." + source + "."
                        + faction.name().toLowerCase(java.util.Locale.ROOT), 1.0D));
        final int weighted = (int) Math.round(amount * weight);
        if (weighted <= 0) {
            return;
        }

        // B33 + G16: a két idő-szorzó NEM szorzódik össze (×4-es hógolyó lenne a záró
        // 48 órában) — a NAGYOBBIK érvényesül: végítélet-hét max ×2 VAGY top2-nagydöntő
        // ×2, együtt is legfeljebb ×2 (a forrás-súllyal együtt max ×3).
        final SeasonFinaleManager finaleRef = seasonFinale;
        double timeMultiplier = finaleRef == null ? 1.0D : Math.max(1.0D, finaleRef.leaguePointMultiplier());
        if (isGrandFinaleWindow() && topTwo().contains(faction)) {
            timeMultiplier = Math.max(timeMultiplier, Math.max(1.0D,
                    configManager.getDouble("world-events.season-finale.top2-point-multiplier", 2.0D)));
        }
        final int scaled = Math.max(weighted, (int) Math.round(weighted * timeMultiplier));
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
        if (!configManager.getBoolean("world-events.season.enabled", true)) {
            return;
        }
        announceGrandFinale();
        if (System.currentTimeMillis() < getSeasonEndMillis()) {
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
        // J9 — új fejezet nyílik: a fejezet-questek (chapter: N) ehhez a sorszámhoz kötődnek.
        seasonNumber++;
        grandFinaleAnnounced = false; // G16 — az új szezon nagydöntője újra hirdethető
        Bukkit.getServer().broadcast(messageManager.getMessage(
                "season-chapter-opened",
                "<gold>📖 Új fejezet nyílik a krónikában: <white>{chapter}. fejezet</white> — a régi fejezet küldetései lezárultak, újak várnak!</gold>",
                Map.of("chapter", String.valueOf(seasonNumber))));
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

        // Gameplay-audit: az OFFLINE bajnok-tagok se maradjanak ki — a tárgy-jutalmuk
        // függőbe kerül (perzisztens), belépéskor kapják meg (a buff/tűzijáték nem
        // időszerű már, az csak az ünneplés pillanatáé).
        for (final Map.Entry<java.util.UUID, FactionType> member : factionManager.getFactionAssignments().entrySet()) {
            if (member.getValue() == champion && Bukkit.getPlayer(member.getKey()) == null) {
                pendingChampionSpoils.add(member.getKey());
            }
        }

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

    /**
     * Gameplay-audit: a szezonzáráskor offline maradt bajnok-tag belépéskor kapja meg
     * a tárgy-jutalmát. A join-event a játékos saját régió-szálán fut — az inventory-írás
     * ott biztonságos.
     */
    @org.bukkit.event.EventHandler
    public void onJoin(final org.bukkit.event.player.PlayerJoinEvent event) {
        if (!pendingChampionSpoils.remove(event.getPlayer().getUniqueId())) {
            return;
        }
        giveRewardItems(event.getPlayer(), configManager.getStringList("world-events.season.champion-reward-items"));
        event.getPlayer().sendMessage(messageManager.getMessage(
                "season-champion-member-late",
                "<gold>🏆 A frakciód megnyerte az előző szezont — a győzelmi jutalmad megőriztük, fogadd!</gold>"
        ));
        requestSave();
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
