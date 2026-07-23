package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.session.PlayerStateCleanup;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.data.JobType;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class JobManager implements PlayerStateCleanup {

    public static final int MAX_JOB_LEVEL = 50;

    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final FactionManager factionManager;
    private java.util.function.Consumer<Player> xpChangeHook;
    private final NamespacedKey jobPrimaryKey;
    private final NamespacedKey jobPrimaryXpKey;
    private final NamespacedKey unlockedSpellsKey;
    // A megszűnt másodlagos-kaszt rendszer PDC-kulcsai: már semmi sem olvassa őket, de a
    // resetClass letakarítja a régi játékosokról maradt bejegyzéseket.
    private final NamespacedKey legacySecondaryKey;
    private final NamespacedKey legacySecondaryXpKey;

    public JobManager(final JavaPlugin plugin, final ConfigManager configManager,
                      final MessageManager messageManager, final FactionManager factionManager) {
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.factionManager = factionManager;
        this.jobPrimaryKey = new NamespacedKey(plugin, "job_primary");
        this.jobPrimaryXpKey = new NamespacedKey(plugin, "job_primary_xp");
        this.unlockedSpellsKey = new NamespacedKey(plugin, "unlocked_spells");
        this.legacySecondaryKey = new NamespacedKey(plugin, "job_secondary");
        this.legacySecondaryXpKey = new NamespacedKey(plugin, "job_secondary_xp");
    }

    public boolean hasPrimaryJob(final Player player) {
        return player.getPersistentDataContainer().has(jobPrimaryKey, PersistentDataType.STRING);
    }

    private volatile hu.taliann.icesmp.managers.FactionManager factionManagerRef;

    public void setFactionManager(final hu.taliann.icesmp.managers.FactionManager factionManager) {
        this.factionManagerRef = factionManager;
    }

    public JobType getPrimaryJob(final Player player) {
        final String rawPrimary = player.getPersistentDataContainer().get(jobPrimaryKey, PersistentDataType.STRING);
        return JobType.fromId(rawPrimary);
    }

    public int getXp(final Player player) {
        return player.getPersistentDataContainer().getOrDefault(jobPrimaryXpKey, PersistentDataType.INTEGER, 0);
    }

    public int getPrimaryLevel(final Player player) {
        return getLevel(getXp(player));
    }

    public boolean canSelectPrimary(final Player player, final JobType job) {
        return job != null && !hasPrimaryJob(player) && meetsFactionRequirement(player, job);
    }

    /**
     * Checks whether the player satisfies the faction requirement of a class
     * (e.g. the Necromancer is only available to the Dark faction).
     *
     * @param player the player to check
     * @param job the class to select
     * @return true if the class has no faction requirement or the player is in the required faction
     */
    public boolean meetsFactionRequirement(final Player player, final JobType job) {
        final FactionType requiredFaction = job == null ? null : job.getRequiredFaction();
        if (requiredFaction == null) {
            return true;
        }

        return factionManager.getFaction(player.getUniqueId()) == requiredFaction;
    }

    public boolean setPrimaryJob(final Player player, final JobType job) {
        // Kapcsolható mód: DK csak Kitaszítottnak — itt (és nem csak a GUI-ban)
        // ellenőrizve, hogy jövőbeli hívási út se kerülhesse meg.
        if (job == JobType.DEATH_KNIGHT
                && configManager.getBoolean("classes.death-knight.dark-only", false)) {
            final hu.taliann.icesmp.managers.FactionManager factions = this.factionManagerRef;
            if (factions != null && factions.getFaction(player.getUniqueId())
                    != hu.taliann.icesmp.data.FactionType.DARK) {
                return false;
            }
        }
        if (!canSelectPrimary(player, job)) {
            return false;
        }

        final PersistentDataContainer pdc = player.getPersistentDataContainer();
        pdc.set(jobPrimaryKey, PersistentDataType.STRING, job.getId());
        pdc.set(jobPrimaryXpKey, PersistentDataType.INTEGER, 0);
        applyAutoUnlocks(player);
        AdvancementService.award(player, "root");
        AdvancementService.award(player, "first_class");
        return true;
    }

    public boolean addXpToJob(final Player player, final int amount) {
        if (amount <= 0 || !hasPrimaryJob(player)) {
            return false;
        }

        return setXp(player, Math.max(0, getXp(player) + amount));
    }

    public boolean setXp(final Player player, final int xp) {
        if (!hasPrimaryJob(player)) {
            return false;
        }

        final PersistentDataContainer pdc = player.getPersistentDataContainer();
        pdc.set(jobPrimaryXpKey, PersistentDataType.INTEGER, Math.max(0, xp));
        applyAutoUnlocks(player);
        if (xpChangeHook != null) {
            xpChangeHook.accept(player);
        }
        return true;
    }

    /**
     * Registers a hook invoked after every XP change (setter injection breaks the
     * JobManager &lt;-&gt; SpecializationManager dependency cycle). Used for
     * specialization spell unlocks.
     *
     * @param hook the callback receiving the affected player
     */
    public void setXpChangeHook(final java.util.function.Consumer<Player> hook) {
        this.xpChangeHook = hook;
    }

    /**
     * Unlocks every config-mapped spell of the (primary) class whose required level
     * has been reached. Mapping lives under 'classes.&lt;jobId&gt;.spell-unlocks' in config.yml.
     *
     * @param player the player to check
     */
    public void applyAutoUnlocks(final Player player) {
        final JobType job = getPrimaryJob(player);
        if (job == null || configManager.getConfiguration() == null) {
            return;
        }

        final ConfigurationSection unlockSection = configManager.getConfiguration()
                .getConfigurationSection("classes." + job.getId() + ".spell-unlocks");
        if (unlockSection == null) {
            return;
        }

        final int level = getPrimaryLevel(player);
        for (final String spellId : unlockSection.getKeys(false)) {
            final int requiredLevel = unlockSection.getInt(spellId, Integer.MAX_VALUE);
            if (level < requiredLevel || hasUnlockedSpell(player, spellId)) {
                continue;
            }

            if (unlockSpell(player, spellId)) {
                player.sendMessage(messageManager.getMessage(
                        "job-spell-auto-unlocked",
                        "&aÚj képesség feloldva: &e{spell} &7(szint {level})",
                        Map.of("spell", spellId.toLowerCase(Locale.ROOT), "level", String.valueOf(requiredLevel))
                ));
            }
        }
    }


    public List<String> getUnlockedSpellIds(final Player player) {
        final String raw = player.getPersistentDataContainer().get(unlockedSpellsKey, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return List.of();
        }

        final Set<String> uniqueIds = new LinkedHashSet<>();
        for (final String token : raw.split(",")) {
            final String id = token.trim().toLowerCase();
            if (!id.isEmpty()) {
                uniqueIds.add(id);
            }
        }

        return List.copyOf(uniqueIds);
    }

    public boolean hasUnlockedSpell(final Player player, final String spellId) {
        if (spellId == null || spellId.isBlank()) {
            return false;
        }

        return getUnlockedSpellIds(player).contains(spellId.toLowerCase());
    }

    public void setUnlockedSpellIds(final Player player, final List<String> spellIds) {
        final PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (spellIds == null || spellIds.isEmpty()) {
            pdc.remove(unlockedSpellsKey);
            return;
        }

        final Set<String> uniqueIds = new LinkedHashSet<>();
        for (final String spellId : spellIds) {
            if (spellId == null || spellId.isBlank()) {
                continue;
            }
            uniqueIds.add(spellId.trim().toLowerCase());
        }

        if (uniqueIds.isEmpty()) {
            pdc.remove(unlockedSpellsKey);
            return;
        }

        pdc.set(unlockedSpellsKey, PersistentDataType.STRING, String.join(",", uniqueIds));
    }

    public boolean unlockSpell(final Player player, final String spellId) {
        if (spellId == null || spellId.isBlank()) {
            return false;
        }

        final List<String> unlocked = new ArrayList<>(getUnlockedSpellIds(player));
        final String normalized = spellId.trim().toLowerCase();
        if (unlocked.contains(normalized)) {
            return false;
        }

        unlocked.add(normalized);
        setUnlockedSpellIds(player, unlocked);
        return true;
    }

    /**
     * Admin reset: wipes the player's class choice entirely — the class, its XP/level and all
     * unlocked spells — putting them back to the "no class chosen" state so a fresh class can be picked.
     * (The class specialization is stored separately; the caller should also reset it.) Writes the
     * player's PDC, so it must run on the player's own region thread (Folia).
     *
     * @param player the player whose class to reset
     */
    public void resetClass(final Player player) {
        final PersistentDataContainer pdc = player.getPersistentDataContainer();
        pdc.remove(jobPrimaryKey);
        pdc.remove(jobPrimaryXpKey);
        pdc.remove(unlockedSpellsKey);
        // Legacy tisztítás: a megszűnt másodlagos-kaszt bejegyzések eltávolítása régi játékosokról.
        pdc.remove(legacySecondaryKey);
        pdc.remove(legacySecondaryXpKey);
    }

    public void cleanup(final java.util.UUID playerId) {
        // Intentionally empty: Job data is persisted in PDC and must survive reconnects.
    }

    public void clearPlayerState(final java.util.UUID playerId) {
        cleanup(playerId);
    }

    private int getLevel(final int xp) {
        final int baseXp = Math.max(1, configManager.getInt("classes.leveling.base-xp", 100));
        final int increment = Math.max(0, configManager.getInt("classes.leveling.increment-per-level", 20));

        int level = 1;
        int remaining = Math.max(0, xp);
        while (level < MAX_JOB_LEVEL) {
            // Progressive curve: level n -> n+1 costs base-xp + (n-1) * increment XP.
            final int levelCost = baseXp + ((level - 1) * increment);
            if (remaining < levelCost) {
                break;
            }
            remaining -= levelCost;
            level++;
        }

        return level;
    }
}

