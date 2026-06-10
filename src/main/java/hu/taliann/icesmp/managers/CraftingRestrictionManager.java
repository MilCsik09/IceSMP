package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.CraftingRule;
import hu.taliann.icesmp.data.JobType;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manager for job/level based crafting restrictions.
 * Rules are loaded from the 'crafting-restrictions' config section and matched
 * against crafting and smithing results.
 */
public final class CraftingRestrictionManager {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final JobManager jobManager;
    private final List<CraftingRule> rules = new ArrayList<>();
    private final Map<UUID, Long> lastNotifyMillis = new ConcurrentHashMap<>();

    private boolean enabled;
    private long notifyCooldownMillis;

    public CraftingRestrictionManager(final JavaPlugin plugin, final ConfigManager configManager, final JobManager jobManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.jobManager = jobManager;
    }

    public void load() {
        rules.clear();
        lastNotifyMillis.clear();
        enabled = configManager.getBoolean("crafting-restrictions.enabled", true);
        notifyCooldownMillis = Math.max(0L, configManager.getLong("crafting-restrictions.notify-cooldown-seconds", 3L)) * 1000L;

        if (!enabled) {
            plugin.getLogger().info("Crafting restrictions are disabled in config.");
            return;
        }

        final ConfigurationSection configuration = configManager.getConfiguration();
        final ConfigurationSection rulesSection = configuration == null ? null : configuration.getConfigurationSection("crafting-restrictions.rules");
        if (rulesSection == null) {
            plugin.getLogger().warning("Missing config section 'crafting-restrictions.rules'; no crafting restrictions active.");
            return;
        }

        for (final String ruleId : rulesSection.getKeys(false)) {
            final ConfigurationSection ruleSection = rulesSection.getConfigurationSection(ruleId);
            if (ruleSection == null) {
                continue;
            }

            final Set<Material> materials = EnumSet.noneOf(Material.class);
            for (final String rawMaterial : ruleSection.getStringList("materials")) {
                final Material material = Material.matchMaterial(rawMaterial);
                if (material == null) {
                    plugin.getLogger().warning("Unknown material '" + rawMaterial + "' in crafting rule '" + ruleId + "'.");
                    continue;
                }
                materials.add(material);
            }

            if (materials.isEmpty()) {
                plugin.getLogger().warning("Crafting rule '" + ruleId + "' has no valid materials; skipping.");
                continue;
            }

            final String rawJob = ruleSection.getString("required-job", "ANY");
            JobType requiredJob = null;
            if (rawJob != null && !"ANY".equalsIgnoreCase(rawJob.trim())) {
                requiredJob = JobType.fromId(rawJob.trim());
                if (requiredJob == null) {
                    plugin.getLogger().warning("Unknown job '" + rawJob + "' in crafting rule '" + ruleId + "'; skipping.");
                    continue;
                }
            }

            final int requiredLevel = Math.max(1, ruleSection.getInt("required-level", 1));
            rules.add(new CraftingRule(ruleId.toLowerCase(Locale.ROOT), materials, requiredJob, requiredLevel));
        }

        plugin.getLogger().info("Loaded " + rules.size() + " crafting restriction rule(s).");
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Finds the rule that blocks the player from crafting the given material.
     *
     * @param player the crafting player
     * @param material the result material
     * @return the violated rule, or null if crafting is allowed
     */
    public CraftingRule findViolatedRule(final Player player, final Material material) {
        if (!enabled || player == null || material == null) {
            return null;
        }

        for (final CraftingRule rule : rules) {
            if (!rule.appliesTo(material)) {
                continue;
            }

            return meetsRequirement(player, rule) ? null : rule;
        }

        return null;
    }

    private boolean meetsRequirement(final Player player, final CraftingRule rule) {
        final JobType requiredJob = rule.requiredJob();
        if (requiredJob == null) {
            return hasJobAtLevel(player, true, rule.requiredLevel()) || hasJobAtLevel(player, false, rule.requiredLevel());
        }

        if (jobManager.hasPrimaryJob(player)
                && jobManager.getPrimaryJob(player) == requiredJob
                && jobManager.getPrimaryLevel(player) >= rule.requiredLevel()) {
            return true;
        }

        return jobManager.hasSecondaryJob(player)
                && jobManager.getSecondaryJob(player) == requiredJob
                && jobManager.getSecondaryLevel(player) >= rule.requiredLevel();
    }

    private boolean hasJobAtLevel(final Player player, final boolean primary, final int requiredLevel) {
        return jobManager.hasJob(player, primary) && jobManager.getLevel(player, primary) >= requiredLevel;
    }

    /**
     * Checks whether the player should receive a restriction message now,
     * applying a short cooldown to avoid spamming during inventory interactions.
     *
     * @param playerId the player UUID
     * @return true if a message should be sent
     */
    public boolean shouldNotify(final UUID playerId) {
        final long now = System.currentTimeMillis();
        final Long lastNotify = lastNotifyMillis.get(playerId);
        if (lastNotify != null && (now - lastNotify) < notifyCooldownMillis) {
            return false;
        }

        lastNotifyMillis.put(playerId, now);
        return true;
    }

    public void cleanup(final UUID playerId) {
        lastNotifyMillis.remove(playerId);
    }

    public void clearPlayerState(final UUID playerId) {
        cleanup(playerId);
    }
}
