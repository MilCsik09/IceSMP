package hu.taliann.icesmp.managers;

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

public final class JobManager {

    public static final int MAX_JOB_LEVEL = 50;

    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final FactionManager factionManager;
    private final NamespacedKey jobPrimaryKey;
    private final NamespacedKey jobPrimaryXpKey;
    private final NamespacedKey jobSecondaryKey;
    private final NamespacedKey jobSecondaryXpKey;
    private final NamespacedKey unlockedSpellsKey;

    public JobManager(final JavaPlugin plugin, final ConfigManager configManager,
                      final MessageManager messageManager, final FactionManager factionManager) {
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.factionManager = factionManager;
        this.jobPrimaryKey = new NamespacedKey(plugin, "job_primary");
        this.jobPrimaryXpKey = new NamespacedKey(plugin, "job_primary_xp");
        this.jobSecondaryKey = new NamespacedKey(plugin, "job_secondary");
        this.jobSecondaryXpKey = new NamespacedKey(plugin, "job_secondary_xp");
        this.unlockedSpellsKey = new NamespacedKey(plugin, "unlocked_spells");
    }

    public boolean hasPrimaryJob(final Player player) {
        return player.getPersistentDataContainer().has(jobPrimaryKey, PersistentDataType.STRING);
    }

    public boolean hasSecondaryJob(final Player player) {
        return player.getPersistentDataContainer().has(jobSecondaryKey, PersistentDataType.STRING);
    }

    public boolean isPrimaryJobAtMaxLevel(final Player player) {
        final int primaryXp = player.getPersistentDataContainer().getOrDefault(jobPrimaryXpKey, PersistentDataType.INTEGER, 0);
        return getLevel(primaryXp) >= MAX_JOB_LEVEL;
    }

    public JobType getPrimaryJob(final Player player) {
        final String rawPrimary = player.getPersistentDataContainer().get(jobPrimaryKey, PersistentDataType.STRING);
        return JobType.fromId(rawPrimary);
    }

    public JobType getSecondaryJob(final Player player) {
        final String rawSecondary = player.getPersistentDataContainer().get(jobSecondaryKey, PersistentDataType.STRING);
        return JobType.fromId(rawSecondary);
    }

    public boolean hasJob(final Player player, final boolean primary) {
        return primary ? hasPrimaryJob(player) : hasSecondaryJob(player);
    }

    public int getXp(final Player player, final boolean primary) {
        final NamespacedKey xpKey = primary ? jobPrimaryXpKey : jobSecondaryXpKey;
        return player.getPersistentDataContainer().getOrDefault(xpKey, PersistentDataType.INTEGER, 0);
    }

    public int getPrimaryLevel(final Player player) {
        return getLevel(getXp(player, true));
    }

    public int getSecondaryLevel(final Player player) {
        return getLevel(getXp(player, false));
    }

    public int getLevel(final Player player, final boolean primary) {
        return getLevel(getXp(player, primary));
    }

    public boolean canSelectPrimary(final Player player, final JobType job) {
        if (job == null || hasPrimaryJob(player) || !meetsFactionRequirement(player, job)) {
            return false;
        }

        final JobType secondary = getSecondaryJob(player);
        return secondary == null || secondary != job;
    }

    public boolean canSelectSecondary(final Player player, final JobType job) {
        if (job == null || !hasPrimaryJob(player) || hasSecondaryJob(player) || !isPrimaryJobAtMaxLevel(player)
                || !meetsFactionRequirement(player, job)) {
            return false;
        }

        final JobType primary = getPrimaryJob(player);
        return primary == null || primary != job;
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
        if (!canSelectPrimary(player, job)) {
            return false;
        }

        final PersistentDataContainer pdc = player.getPersistentDataContainer();
        pdc.set(jobPrimaryKey, PersistentDataType.STRING, job.getId());
        pdc.set(jobPrimaryXpKey, PersistentDataType.INTEGER, 0);
        applyAutoUnlocks(player, true);
        return true;
    }

    public void addXp(final Player player, final boolean toPrimary, final int amount) {
        addXpToJob(player, toPrimary, amount);
    }

    public boolean addXpToJob(final Player player, final boolean toPrimary, final int amount) {
        if (amount <= 0 || !hasJob(player, toPrimary)) {
            return false;
        }

        final int nextXp = Math.max(0, getXp(player, toPrimary) + amount);
        return setXp(player, toPrimary, nextXp);
    }

    public boolean setXp(final Player player, final boolean toPrimary, final int xp) {
        if (!hasJob(player, toPrimary)) {
            return false;
        }

        final PersistentDataContainer pdc = player.getPersistentDataContainer();
        final NamespacedKey xpKey = toPrimary ? jobPrimaryXpKey : jobSecondaryXpKey;
        pdc.set(xpKey, PersistentDataType.INTEGER, Math.max(0, xp));
        applyAutoUnlocks(player, toPrimary);
        return true;
    }

    /**
     * Unlocks every config-mapped spell of the given job slot whose required level
     * has been reached. Mapping lives under 'classes.&lt;jobId&gt;.spell-unlocks' in config.yml.
     *
     * @param player the player to check
     * @param primary true for the primary slot, false for the secondary
     */
    public void applyAutoUnlocks(final Player player, final boolean primary) {
        final JobType job = primary ? getPrimaryJob(player) : getSecondaryJob(player);
        if (job == null || configManager.getConfiguration() == null) {
            return;
        }

        final ConfigurationSection unlockSection = configManager.getConfiguration()
                .getConfigurationSection("classes." + job.getId() + ".spell-unlocks");
        if (unlockSection == null) {
            return;
        }

        final int level = getLevel(player, primary);
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

    public boolean setSecondaryJob(final Player player, final JobType job) {
        if (!canSelectSecondary(player, job)) {
            return false;
        }

        final PersistentDataContainer pdc = player.getPersistentDataContainer();

        pdc.set(jobSecondaryKey, PersistentDataType.STRING, job.getId());
        pdc.set(jobSecondaryXpKey, PersistentDataType.INTEGER, 0);
        applyAutoUnlocks(player, false);
        return true;
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

    public void cleanup(final java.util.UUID playerId) {
        // Intentionally empty: Job data is persisted in PDC and must survive reconnects.
    }

    public void clearPlayerState(final java.util.UUID playerId) {
        cleanup(playerId);
    }

    private int getLevel(final int xp) {
        return Math.min(MAX_JOB_LEVEL, (Math.max(0, xp) / 100) + 1);
    }
}

