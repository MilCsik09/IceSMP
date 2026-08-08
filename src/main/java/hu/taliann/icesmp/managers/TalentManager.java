package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.JobType;
import hu.taliann.icesmp.data.ProfessionType;
import hu.taliann.icesmp.data.SpecializationType;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Config-driven talent system with two point pools:
 * the class pool earns points from class levels, the profession pool from
 * profession levels. Talent definitions live under 'talents.class.definitions'
 * and 'talents.profession.definitions' in config.yml.
 *
 * Supported effects:
 * - 'max-health', 'movement-speed', 'attack-damage': applied as player attribute
 *   modifiers (re-applied idempotently on join and on talent spend)
 * - 'class-xp-bonus', 'profession-xp-bonus': percent bonuses queried by the XP listeners
 */
public final class TalentManager {

    private static final String EFFECT_MAX_HEALTH = "max-health";
    private static final String EFFECT_MOVEMENT_SPEED = "movement-speed";
    private static final String EFFECT_ATTACK_DAMAGE = "attack-damage";

    private final ConfigManager configManager;
    private final JobManager jobManager;
    private final ProfessionManager professionManager;
    private final SpecializationManager specializationManager;
    private final JavaPlugin plugin;
    private final hu.taliann.icesmp.playerprofile.application.PlayerProfileTalentStore talentStore =
            new hu.taliann.icesmp.playerprofile.application.PlayerProfileTalentStore();
    private final NamespacedKey healthModifierKey;
    private final NamespacedKey speedModifierKey;
    private final NamespacedKey damageModifierKey;

    public TalentManager(final JavaPlugin plugin, final ConfigManager configManager,
                         final JobManager jobManager, final ProfessionManager professionManager,
                         final SpecializationManager specializationManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.jobManager = jobManager;
        this.professionManager = professionManager;
        this.specializationManager = specializationManager;
        this.healthModifierKey = new NamespacedKey(plugin, "talent_max_health");
        this.speedModifierKey = new NamespacedKey(plugin, "talent_movement_speed");
        this.damageModifierKey = new NamespacedKey(plugin, "talent_attack_damage");
    }

    public ConfigurationSection getDefinitions(final boolean classPool) {
        if (configManager.getConfiguration() == null) {
            return null;
        }

        return configManager.getConfiguration()
                .getConfigurationSection("talents." + (classPool ? "class" : "profession") + ".definitions");
    }

    /**
     * Gets the talent ranks of a player in the given pool.
     *
     * @param player the player
     * @param classPool true for the class pool, false for the profession pool
     * @return talent id to rank map (insertion ordered)
     */
    public Map<String, Integer> getRanks(final Player player, final boolean classPool) {
        return talentStore.state(player.getUniqueId(), pool(classPool)).ranks();
    }

    public int getRank(final Player player, final boolean classPool, final String talentId) {
        if (talentId == null || talentId.isBlank()) {
            return 0;
        }

        return getRanks(player, classPool).getOrDefault(talentId.toLowerCase(Locale.ROOT), 0);
    }

    public int getEarnedPoints(final Player player, final boolean classPool) {
        if (classPool) {
            final int perLevels = Math.max(1, configManager.getInt("talents.class.points-per-levels", 5));
            int totalLevels = 0;
            if (jobManager.hasPrimaryJob(player)) {
                totalLevels += jobManager.getPrimaryLevel(player);
            }
            return totalLevels / perLevels + getBonusPoints(player, true);
        }

        // Profession pool: every profession contributes its levels above 1
        // (unlearned professions sit at level 1 with 0 XP, contributing nothing).
        final int perLevels = Math.max(1, configManager.getInt("talents.profession.points-per-levels", 10));
        int totalLevels = 0;
        for (final ProfessionType professionType : ProfessionType.values()) {
            totalLevels += Math.max(0, professionManager.getLevel(player, professionType) - 1);
        }
        return totalLevels / perLevels + getBonusPoints(player, false);
    }

    /** K8: Emlékszilánk-beváltásból származó extra pontok az adott poolban. */
    public int getBonusPoints(final Player player, final boolean classPool) {
        return talentStore.state(player.getUniqueId(), pool(classPool)).bonus();
    }

    /**
     * K8: extra talentpont jóváírása (Emlékszilánk-beváltás — "visszaemlékezés").
     * A játékos saját régió-szálán hívandó (PDC-írás).
     *
     * @param player the player
     * @param classPool true = kaszt-pool, false = szakma-pool
     * @param amount hány pont (≥1)
     */
    public java.util.concurrent.CompletionStage<Integer> grantBonusPoints(
            final Player player, final boolean classPool, final int amount) {
        if (amount <= 0) return java.util.concurrent.CompletableFuture.completedFuture(
                getBonusPoints(player, classPool));
        return talentStore.grantBonus(player.getUniqueId(), pool(classPool), amount);
    }

    /**
     * Checks whether a talent is available to the player. WoW-style gating via
     * optional definition keys: 'requires-job' (either class slot),
     * 'requires-spec' (current class specialization) and 'requires-profession'
     * (actively practiced profession).
     *
     * @param player the player
     * @param classPool true for the class pool, false for the profession pool
     * @param talentId the talent definition id
     * @return true if the player meets every requirement of the talent
     */
    public boolean isAvailable(final Player player, final boolean classPool, final String talentId) {
        final ConfigurationSection definitions = getDefinitions(classPool);
        if (definitions == null || talentId == null) {
            return false;
        }

        final ConfigurationSection talentSection = definitions.getConfigurationSection(talentId.toLowerCase(Locale.ROOT));
        return talentSection != null && meetsRequirements(player, talentSection);
    }

    /**
     * Whether the talent is not only visible (job/spec/profession requirements
     * met) but also UNLOCKED in the tree: its prerequisite talent
     * ('requires-talent') already has at least one rank. Used by the talent-tree
     * GUI to distinguish locked nodes from spendable ones.
     *
     * @param player the player
     * @param classPool true for the class pool, false for the profession pool
     * @param talentId the talent id
     * @return true if visible and the parent prerequisite is satisfied
     */
    public boolean isUnlocked(final Player player, final boolean classPool, final String talentId) {
        final ConfigurationSection definitions = getDefinitions(classPool);
        if (definitions == null || talentId == null) {
            return false;
        }

        final ConfigurationSection talentSection = definitions.getConfigurationSection(talentId.toLowerCase(Locale.ROOT));
        return talentSection != null
                && meetsRequirements(player, talentSection)
                && treeGatesMet(player, classPool, talentSection);
    }

    /**
     * The talent-tree gates beyond the job/spec/profession requirements:
     * <ul>
     *   <li><b>requires-talent</b>: the parent talent must already have a rank;</li>
     *   <li><b>excludes</b>: no mutually-exclusive sibling may be ranked (branch choice);</li>
     *   <li><b>requires-spent</b>: a capstone needs N points already spent in the pool.</li>
     * </ul>
     */
    private boolean treeGatesMet(final Player player, final boolean classPool,
                                 final ConfigurationSection talentSection) {
        return treeGatesMet(player, classPool, talentSection, getRanks(player, classPool));
    }

    private boolean treeGatesMet(final Player player, final boolean classPool,
                                 final ConfigurationSection talentSection,
                                 final Map<String, Integer> ranks) {
        final String parentId = talentSection.getString("requires-talent");
        if (parentId != null && !parentId.isBlank()
                && ranks.getOrDefault(parentId.trim().toLowerCase(Locale.ROOT), 0) <= 0) {
            return false;
        }
        for (final String excluded : talentSection.getStringList("excludes")) {
            if (ranks.getOrDefault(excluded.trim().toLowerCase(Locale.ROOT), 0) > 0) return false;
        }
        final int requiresSpent = Math.max(0, talentSection.getInt("requires-spent", 0));
        return requiresSpent <= 0 || spentPoints(player, classPool, ranks) >= requiresSpent;
    }

    private boolean meetsRequirements(final Player player, final ConfigurationSection talentSection) {
        final String requiredJobId = talentSection.getString("requires-job");
        if (requiredJobId != null && !requiredJobId.isBlank()) {
            final JobType requiredJob = JobType.fromId(requiredJobId);
            final boolean hasJob = requiredJob != null && jobManager.getPrimaryJob(player) == requiredJob;
            if (!hasJob) {
                return false;
            }
        }

        final String requiredSpecId = talentSection.getString("requires-spec");
        if (requiredSpecId != null && !requiredSpecId.isBlank()) {
            final SpecializationType requiredSpec = SpecializationType.fromId(requiredSpecId);
            if (requiredSpec == null || specializationManager.getClassSpecialization(player) != requiredSpec) {
                return false;
            }
        }

        final String requiredProfessionId = talentSection.getString("requires-profession");
        if (requiredProfessionId != null && !requiredProfessionId.isBlank()) {
            final ProfessionType requiredProfession = ProfessionType.fromId(requiredProfessionId);
            if (requiredProfession == null || !professionManager.hasProfession(player, requiredProfession)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Removes every ranked talent whose requirements the player no longer meets
     * (e.g. after a specialization respec) and re-applies the attribute effects —
     * the refunded points become spendable again.
     *
     * @param player the player
     * @param classPool true for the class pool, false for the profession pool
     * @return the number of refunded points
     */
    public java.util.concurrent.CompletionStage<Integer> refundUnavailableTalents(
            final Player player, final boolean classPool) {
        final ConfigurationSection definitions = getDefinitions(classPool);
        return talentStore.transact(player.getUniqueId(), pool(classPool), "refund", state -> {
            final Map<String, Integer> ranks = new LinkedHashMap<>(state.ranks());
            hu.taliann.icesmp.classspec.domain.SpellGrantLedger ledger = state.spellLedger();
            int refunded = 0;
            final var iterator = ranks.entrySet().iterator();
            while (iterator.hasNext()) {
                final Map.Entry<String, Integer> entry = iterator.next();
                if (isAvailable(player, classPool, entry.getKey())) continue;
                refunded = Math.addExact(refunded, entry.getValue());
                final ConfigurationSection section = definitions == null ? null
                        : definitions.getConfigurationSection(entry.getKey());
                final String grantsSpell = section == null ? null : section.getString("grants-spell");
                if (grantsSpell != null && !grantsSpell.isBlank()) {
                    ledger = ledger.remove(grantsSpell,
                            JobManager.SOURCE_TALENT_PREFIX + entry.getKey()).ledger();
                }
                iterator.remove();
            }
            if (refunded == 0) {
                return hu.taliann.icesmp.playerprofile.application.PlayerProfileTalentStore.Decision
                        .unchanged(state, 0);
            }
            return hu.taliann.icesmp.playerprofile.application.PlayerProfileTalentStore.Decision
                    .changed(ranks, state.bonus(), ledger, refunded);
        }).thenCompose(refunded -> schedulePlayer(player, () -> applyAttributeTalents(player))
                .thenApply(ignored -> refunded));
    }

    /**
     * Points spent on talents the player CURRENTLY qualifies for. Talents whose
     * requirements have lapsed (e.g. after a respec, before they are physically
     * refunded) are excluded, so they neither inflate the spent count nor satisfy a
     * capstone's {@code requires-spent} gate with "ghost" points — making both the
     * available-point math and the gate deterministic regardless of refund timing.
     */
    public int getSpentPoints(final Player player, final boolean classPool) {
        return spentPoints(player, classPool, getRanks(player, classPool));
    }

    private int spentPoints(final Player player, final boolean classPool,
                            final Map<String, Integer> ranks) {
        int spent = 0;
        for (final Map.Entry<String, Integer> entry : ranks.entrySet()) {
            if (isAvailable(player, classPool, entry.getKey())) {
                spent = Math.addExact(spent, entry.getValue());
            }
        }
        return spent;
    }

    public int getAvailablePoints(final Player player, final boolean classPool) {
        return Math.max(0, getEarnedPoints(player, classPool) - getSpentPoints(player, classPool));
    }

    /**
     * Spends one talent point on the given talent if a point is available and
     * the talent has not reached its max rank. Attribute effects are re-applied.
     *
     * @param player the player
     * @param classPool true for the class pool, false for the profession pool
     * @param talentId the talent to rank up
     * @return true if the point was spent
     */
    public java.util.concurrent.CompletionStage<Boolean> spendPoint(
            final Player player, final boolean classPool, final String talentId) {
        final ConfigurationSection definitions = getDefinitions(classPool);
        if (definitions == null || talentId == null) {
            return java.util.concurrent.CompletableFuture.completedFuture(false);
        }
        final String normalizedId = talentId.toLowerCase(Locale.ROOT);
        final ConfigurationSection talentSection = definitions.getConfigurationSection(normalizedId);
        if (talentSection == null) return java.util.concurrent.CompletableFuture.completedFuture(false);
        final int maxRank = Math.max(1, talentSection.getInt("max-rank", 1));
        final String grantsSpell = talentSection.getString("grants-spell");
        final boolean capstone = talentSection.getInt("requires-spent", 0) > 0;
        return talentStore.transact(player.getUniqueId(), pool(classPool), "spend", state -> {
            if (!meetsRequirements(player, talentSection)
                    || !treeGatesMet(player, classPool, talentSection, state.ranks())) {
                return hu.taliann.icesmp.playerprofile.application.PlayerProfileTalentStore.Decision
                        .unchanged(state, new SpendCommit(false, capstone));
            }
            final int available = Math.max(0,
                    Math.addExact(earnedBasePoints(player, classPool), state.bonus())
                            - spentPoints(player, classPool, state.ranks()));
            final int currentRank = state.ranks().getOrDefault(normalizedId, 0);
            if (available <= 0 || currentRank >= maxRank) {
                return hu.taliann.icesmp.playerprofile.application.PlayerProfileTalentStore.Decision
                        .unchanged(state, new SpendCommit(false, capstone));
            }
            final Map<String, Integer> ranks = new LinkedHashMap<>(state.ranks());
            ranks.put(normalizedId, currentRank + 1);
            hu.taliann.icesmp.classspec.domain.SpellGrantLedger ledger = state.spellLedger();
            if (currentRank == 0 && grantsSpell != null && !grantsSpell.isBlank()) {
                ledger = ledger.add(grantsSpell,
                        JobManager.SOURCE_TALENT_PREFIX + normalizedId).ledger();
            }
            return hu.taliann.icesmp.playerprofile.application.PlayerProfileTalentStore.Decision
                    .changed(ranks, state.bonus(), ledger, new SpendCommit(true, capstone));
        }).thenCompose(commit -> {
            if (!commit.changed()) return java.util.concurrent.CompletableFuture.completedFuture(false);
            return schedulePlayer(player, () -> {
                applyAttributeTalents(player);
                if (commit.capstone()) AdvancementService.award(player, "capstone");
            }).thenApply(ignored -> true);
        });
    }

    private record SpendCommit(boolean changed, boolean capstone) { }

    /**
     * Sums the effect contributions of every ranked talent across both pools.
     *
     * @param player the player
     * @param effect the effect identifier (e.g. 'max-health')
     * @return the total effect value
     */
    public double getEffectTotal(final Player player, final String effect) {
        double total = 0.0D;
        total += getPoolEffectTotal(player, true, effect);
        total += getPoolEffectTotal(player, false, effect);
        return total;
    }

    /**
     * Re-applies the attribute-based talent effects as player attribute modifiers.
     * Idempotent: existing talent modifiers are removed first, so it is safe on join.
     *
     * @param player the player to update
     */
    public void applyAttributeTalents(final Player player) {
        applyModifier(player, Attribute.MAX_HEALTH, healthModifierKey, getEffectTotal(player, EFFECT_MAX_HEALTH));
        applyModifier(player, Attribute.MOVEMENT_SPEED, speedModifierKey, getEffectTotal(player, EFFECT_MOVEMENT_SPEED));
        applyModifier(player, Attribute.ATTACK_DAMAGE, damageModifierKey, getEffectTotal(player, EFFECT_ATTACK_DAMAGE));
    }

    private void applyModifier(final Player player, final Attribute attribute, final NamespacedKey key, final double amount) {
        final AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }

        for (final AttributeModifier modifier : instance.getModifiers()) {
            if (key.equals(modifier.getKey())) {
                instance.removeModifier(modifier);
            }
        }

        if (amount != 0.0D) {
            instance.addModifier(new AttributeModifier(key, amount, AttributeModifier.Operation.ADD_NUMBER));
        }
    }

    private double getPoolEffectTotal(final Player player, final boolean classPool, final String effect) {
        final ConfigurationSection definitions = getDefinitions(classPool);
        if (definitions == null) {
            return 0.0D;
        }

        double total = 0.0D;
        for (final Map.Entry<String, Integer> entry : getRanks(player, classPool).entrySet()) {
            final ConfigurationSection talentSection = definitions.getConfigurationSection(entry.getKey());
            if (talentSection == null || !effect.equalsIgnoreCase(talentSection.getString("effect", ""))) {
                continue;
            }

            // Talents whose requirements lapsed (e.g. after a respec) stop contributing.
            if (!meetsRequirements(player, talentSection)) {
                continue;
            }

            total += entry.getValue() * talentSection.getDouble("per-rank", 0.0D);
        }

        return total;
    }

    public void runOnOwnerThread(final Player player, final Runnable action) {
        player.getScheduler().run(plugin, ignored -> action.run(), null);
    }

    private java.util.concurrent.CompletionStage<Void> schedulePlayer(
            final Player player, final Runnable action) {
        final java.util.concurrent.CompletableFuture<Void> result =
                new java.util.concurrent.CompletableFuture<>();
        player.getScheduler().run(plugin, ignored -> {
            try { action.run(); result.complete(null); }
            catch (final Throwable failure) { result.completeExceptionally(failure); }
        }, () -> result.completeExceptionally(
                new IllegalStateException("Player scheduler rejected talent effect")));
        return result;
    }

    private int earnedBasePoints(final Player player, final boolean classPool) {
        if (classPool) {
            final int perLevels = Math.max(1,
                    configManager.getInt("talents.class.points-per-levels", 5));
            return (jobManager.hasPrimaryJob(player) ? jobManager.getPrimaryLevel(player) : 0)
                    / perLevels;
        }
        final int perLevels = Math.max(1,
                configManager.getInt("talents.profession.points-per-levels", 10));
        int totalLevels = 0;
        for (final ProfessionType professionType : ProfessionType.values()) {
            totalLevels = Math.addExact(totalLevels,
                    Math.max(0, professionManager.getLevel(player, professionType) - 1));
        }
        return totalLevels / perLevels;
    }

    private static hu.taliann.icesmp.playerprofile.application.PlayerProfileTalentStore.Pool pool(
            final boolean classPool) {
        return classPool
                ? hu.taliann.icesmp.playerprofile.application.PlayerProfileTalentStore.Pool.CLASS
                : hu.taliann.icesmp.playerprofile.application.PlayerProfileTalentStore.Pool.PROFESSION;
    }

}
