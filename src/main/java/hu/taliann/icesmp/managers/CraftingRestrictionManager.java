package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.session.PlayerStateCleanup;

import hu.taliann.icesmp.data.CraftingRule;
import hu.taliann.icesmp.data.JobType;
import hu.taliann.icesmp.data.ProfessionType;
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
 * Manager for job/profession level based crafting restrictions.
 * Rules are loaded from the 'crafting-restrictions' config section and matched
 * against crafting and smithing results. Every requirement present on a rule
 * (class and/or profession) must be satisfied.
 */
public final class CraftingRestrictionManager implements PlayerStateCleanup {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final JobManager jobManager;
    private final ProfessionManager professionManager;
    // Reload (clear+add sorozat) közben craft-eventek iterálják régió-szálról — COW véd.
    private final List<CraftingRule> rules = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final Map<UUID, Long> lastNotifyMillis = new ConcurrentHashMap<>();

    private volatile boolean enabled;
    private volatile long notifyCooldownMillis;

    public CraftingRestrictionManager(final JavaPlugin plugin, final ConfigManager configManager,
                                      final JobManager jobManager, final ProfessionManager professionManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.jobManager = jobManager;
        this.professionManager = professionManager;
    }

    public void load() {
        rules.clear();
        lastNotifyMillis.clear();
        enabled = configManager.getBoolean("crafting-restrictions.enabled", true);
        notifyCooldownMillis = Math.max(0L, configManager.getLong("crafting-restrictions.notify-cooldown-seconds", 3L)) * 1000L;

        if (!enabled) {
            hu.taliann.icesmp.utils.StartupLog.info(plugin.getLogger(), configManager, "Crafting restrictions are disabled in config.");
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

            final boolean hasJobRequirement = ruleSection.contains("required-job") || ruleSection.contains("required-level");
            JobType requiredJob = null;
            if (hasJobRequirement) {
                final String rawJob = ruleSection.getString("required-job", "ANY");
                if (rawJob != null && !"ANY".equalsIgnoreCase(rawJob.trim())) {
                    requiredJob = JobType.fromId(rawJob.trim());
                    if (requiredJob == null) {
                        plugin.getLogger().warning("Unknown job '" + rawJob + "' in crafting rule '" + ruleId + "'; skipping.");
                        continue;
                    }
                }
            }
            final int requiredJobLevel = Math.max(1, ruleSection.getInt("required-level", 1));

            ProfessionType requiredProfession = null;
            if (ruleSection.contains("required-profession")) {
                final String rawProfession = ruleSection.getString("required-profession", "");
                requiredProfession = ProfessionType.fromId(rawProfession);
                if (requiredProfession == null) {
                    plugin.getLogger().warning("Unknown profession '" + rawProfession + "' in crafting rule '" + ruleId + "'; skipping.");
                    continue;
                }
            }
            final int requiredProfessionLevel = Math.max(1, ruleSection.getInt("required-profession-level", 1));

            if (!hasJobRequirement && requiredProfession == null) {
                plugin.getLogger().warning("Crafting rule '" + ruleId + "' has no requirements; skipping.");
                continue;
            }

            rules.add(new CraftingRule(
                    ruleId.toLowerCase(Locale.ROOT),
                    materials,
                    hasJobRequirement,
                    requiredJob,
                    requiredJobLevel,
                    requiredProfession,
                    requiredProfessionLevel
            ));
        }

        hu.taliann.icesmp.utils.StartupLog.info(plugin.getLogger(), configManager, "Loaded " + rules.size() + " crafting restriction rule(s).");
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Finds the rule that blocks the player from crafting the given material.
     *
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

    /**
     * Checks whether the player fails the profession part of a rule
     * (used by the listener to pick the right feedback message).
     */
    public boolean failsProfessionRequirement(final Player player, final CraftingRule rule) {
        return rule.requiredProfession() != null && !meetsProfessionRequirement(player, rule);
    }

    private boolean meetsRequirement(final Player player, final CraftingRule rule) {
        if (rule.hasJobRequirement() && !meetsJobRequirement(player, rule)) {
            return false;
        }

        return rule.requiredProfession() == null || meetsProfessionRequirement(player, rule);
    }

    private boolean meetsJobRequirement(final Player player, final CraftingRule rule) {
        final JobType requiredJob = rule.requiredJob();
        if (requiredJob == null) {
            return hasJobAtLevel(player, rule.requiredJobLevel());
        }

        return jobManager.hasPrimaryJob(player)
                && jobManager.getPrimaryJob(player) == requiredJob
                && jobManager.getPrimaryLevel(player) >= rule.requiredJobLevel();
    }

    private boolean meetsProfessionRequirement(final Player player, final CraftingRule rule) {
        return professionManager.hasProfession(player, rule.requiredProfession())
                && professionManager.getLevel(player, rule.requiredProfession()) >= rule.requiredProfessionLevel();
    }

    private boolean hasJobAtLevel(final Player player, final int requiredLevel) {
        return jobManager.hasPrimaryJob(player) && jobManager.getPrimaryLevel(player) >= requiredLevel;
    }

    /**
     * Checks whether the player should receive a restriction message now,
     * applying a short cooldown to avoid spamming during inventory interactions.
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
