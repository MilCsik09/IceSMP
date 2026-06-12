package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.data.JobType;
import hu.taliann.icesmp.data.ProfessionSpecializationType;
import hu.taliann.icesmp.data.ProfessionType;
import hu.taliann.icesmp.data.SpecializationType;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.Map;

/**
 * Manager for class and profession specializations.
 * The primary class can specialize once it reaches the configured level
 * (the secondary class never specializes, per design). Specialization spell
 * unlocks live under 'specializations.&lt;specId&gt;.spell-unlocks' in config.yml
 * and are applied on selection and on every class XP change via the JobManager hook.
 */
public final class SpecializationManager {

    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final JobManager jobManager;
    private final ProfessionManager professionManager;
    private final FactionManager factionManager;
    private final MetelytepoManager metelytepoManager;
    private final QuestManager questManager;
    private final NamespacedKey classSpecKey;
    private final NamespacedKey professionSpecKey;

    public SpecializationManager(final JavaPlugin plugin, final ConfigManager configManager,
                                 final MessageManager messageManager, final JobManager jobManager,
                                 final ProfessionManager professionManager, final FactionManager factionManager,
                                 final MetelytepoManager metelytepoManager, final QuestManager questManager) {
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.jobManager = jobManager;
        this.professionManager = professionManager;
        this.factionManager = factionManager;
        this.metelytepoManager = metelytepoManager;
        this.questManager = questManager;
        this.classSpecKey = new NamespacedKey(plugin, "class_spec");
        this.professionSpecKey = new NamespacedKey(plugin, "profession_spec");
    }

    public int getRequiredClassLevel() {
        return Math.max(1, configManager.getInt("classes.specialization.required-level", 25));
    }

    public int getRequiredProfessionLevel() {
        return Math.max(1, configManager.getInt("professions.specialization.required-level", 25));
    }

    public SpecializationType getClassSpecialization(final Player player) {
        final String rawSpec = player.getPersistentDataContainer().get(classSpecKey, PersistentDataType.STRING);
        return SpecializationType.fromId(rawSpec);
    }

    public ProfessionSpecializationType getProfessionSpecialization(final Player player) {
        final String rawSpec = player.getPersistentDataContainer().get(professionSpecKey, PersistentDataType.STRING);
        return ProfessionSpecializationType.fromId(rawSpec);
    }

    /**
     * Checks whether a class specialization can be selected by the player:
     * matching primary class at the required level, no specialization yet,
     * and the spec's faction/sinner requirements satisfied
     * (Necromancer: Dark faction + permanent sinner mark).
     *
     * @param player the player
     * @param specialization the desired specialization
     * @return true if selectable
     */
    public boolean canSelectClassSpecialization(final Player player, final SpecializationType specialization) {
        if (specialization == null || getClassSpecialization(player) != null) {
            return false;
        }

        if (jobManager.getPrimaryJob(player) != specialization.getParentJob()
                || jobManager.getPrimaryLevel(player) < getRequiredClassLevel()) {
            return false;
        }

        final FactionType requiredFaction = specialization.getRequiredFaction();
        if (requiredFaction != null && factionManager.getFaction(player.getUniqueId()) != requiredFaction) {
            return false;
        }

        if (specialization.requiresSinner() && !metelytepoManager.isSinner(player)) {
            return false;
        }

        // Quest gate (e.g. the necromancer initiation ritual in the ruined city).
        final String requiredQuest = configManager.getString(
                "specializations." + specialization.getId() + ".required-quest", "");
        return requiredQuest.isBlank() || questManager.hasCompleted(player, requiredQuest);
    }

    public boolean selectClassSpecialization(final Player player, final SpecializationType specialization) {
        if (!canSelectClassSpecialization(player, specialization)) {
            return false;
        }

        player.getPersistentDataContainer().set(classSpecKey, PersistentDataType.STRING, specialization.getId());
        applyClassSpecializationUnlocks(player);
        return true;
    }

    public boolean canSelectProfessionSpecialization(final Player player, final ProfessionSpecializationType specialization) {
        if (specialization == null || getProfessionSpecialization(player) != null) {
            return false;
        }

        return professionManager.hasProfession(player, specialization.getParentProfession())
                && professionManager.getLevel(player, specialization.getParentProfession()) >= getRequiredProfessionLevel();
    }

    public boolean selectProfessionSpecialization(final Player player, final ProfessionSpecializationType specialization) {
        if (!canSelectProfessionSpecialization(player, specialization)) {
            return false;
        }

        player.getPersistentDataContainer().set(professionSpecKey, PersistentDataType.STRING, specialization.getId());
        return true;
    }

    /**
     * Admin operation: clears both the class and profession specialization of a player.
     *
     * @param player the player to reset
     */
    public void resetSpecializations(final Player player) {
        resetClassSpecialization(player);
        resetProfessionSpecialization(player);
    }

    public void resetClassSpecialization(final Player player) {
        player.getPersistentDataContainer().remove(classSpecKey);
    }

    public void resetProfessionSpecialization(final Player player) {
        player.getPersistentDataContainer().remove(professionSpecKey);
    }

    /**
     * Gets the respec price (paid in the player's own faction currency) for
     * dropping a chosen specialization via /spec respec.
     *
     * @return the configured respec cost
     */
    public double getRespecCost() {
        return Math.max(0.0D, configManager.getDouble("specializations.respec-cost", 100.0D));
    }

    /**
     * Unlocks every specialization spell whose required primary class level has
     * been reached. Wired into JobManager's XP change hook so admin XP commands
     * and kill XP both trigger it.
     *
     * @param player the player to check
     */
    public void applyClassSpecializationUnlocks(final Player player) {
        final SpecializationType specialization = getClassSpecialization(player);
        if (specialization == null || configManager.getConfiguration() == null) {
            return;
        }

        if (jobManager.getPrimaryJob(player) != specialization.getParentJob()) {
            return;
        }

        final ConfigurationSection unlockSection = configManager.getConfiguration()
                .getConfigurationSection("specializations." + specialization.getId() + ".spell-unlocks");
        if (unlockSection == null) {
            return;
        }

        final int level = jobManager.getPrimaryLevel(player);
        for (final String spellId : unlockSection.getKeys(false)) {
            final int requiredLevel = unlockSection.getInt(spellId, Integer.MAX_VALUE);
            if (level < requiredLevel || jobManager.hasUnlockedSpell(player, spellId)) {
                continue;
            }

            if (jobManager.unlockSpell(player, spellId)) {
                player.sendMessage(messageManager.getMessage(
                        "spec-spell-unlocked",
                        "&5Specializációs képesség feloldva: &e{spell} &7(szint {level})",
                        Map.of("spell", spellId.toLowerCase(Locale.ROOT), "level", String.valueOf(requiredLevel))
                ));
            }
        }
    }
}
