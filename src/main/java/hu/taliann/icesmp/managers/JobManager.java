package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.classspec.application.ClassSpecProfileGateway;
import hu.taliann.icesmp.classspec.application.ProfileMutationResult;
import hu.taliann.icesmp.classspec.domain.SpellGrantLedger;
import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.data.JobType;
import hu.taliann.icesmp.session.PlayerStateCleanup;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Predicate;

/** Profile v2-backed class progression and explicit spell provenance. */
public final class JobManager implements PlayerStateCleanup {
    public static final int MAX_JOB_LEVEL = 50;
    public static final String SOURCE_BASE = "BASE";
    public static final String SOURCE_BASE_PREFIX = "BASE:";
    public static final String SOURCE_ADMIN = "ADMIN";
    public static final String SOURCE_SPEC_PREFIX = "SPEC:";
    public static final String SOURCE_TALENT_PREFIX = "TALENT:";
    public static final String SOURCE_QUEST_PREFIX = "QUEST:";

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final FactionManager factionManager;
    private final NamespacedKey jobPrimaryKey;
    private final NamespacedKey jobPrimaryXpKey;
    private final NamespacedKey unlockedSpellsKey;
    private final NamespacedKey spellGrantsKey;
    private final NamespacedKey legacySecondaryKey;
    private final NamespacedKey legacySecondaryXpKey;
    private volatile FactionManager factionManagerRef;
    private volatile ClassSpecProfileGateway profileGateway;
    private java.util.function.Consumer<Player> xpChangeHook;

    public JobManager(final JavaPlugin plugin, final ConfigManager configManager,
                      final MessageManager messageManager, final FactionManager factionManager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.configManager = Objects.requireNonNull(configManager, "configManager");
        this.messageManager = Objects.requireNonNull(messageManager, "messageManager");
        this.factionManager = Objects.requireNonNull(factionManager, "factionManager");
        this.jobPrimaryKey = new NamespacedKey(plugin, "job_primary");
        this.jobPrimaryXpKey = new NamespacedKey(plugin, "job_primary_xp");
        this.unlockedSpellsKey = new NamespacedKey(plugin, "unlocked_spells");
        this.spellGrantsKey = new NamespacedKey(plugin, "spell_grants");
        this.legacySecondaryKey = new NamespacedKey(plugin, "job_secondary");
        this.legacySecondaryXpKey = new NamespacedKey(plugin, "job_secondary_xp");
    }

    public void setProfileGateway(final ClassSpecProfileGateway gateway) {
        this.profileGateway = Objects.requireNonNull(gateway, "profileGateway");
    }

    public void setFactionManager(final FactionManager manager) { this.factionManagerRef = manager; }

    private ClassSpecProfileGateway gateway() {
        final ClassSpecProfileGateway gateway = profileGateway;
        if (gateway == null) throw new IllegalStateException("Profile v2 gateway is not initialized");
        return gateway;
    }

    public boolean hasPrimaryJob(final Player player) { return getPrimaryJob(player) != null; }

    public JobType getPrimaryJob(final Player player) {
        final ClassSpecProfileGateway gateway = profileGateway;
        if (gateway == null || !gateway.isSessionReady(player.getUniqueId())) return null;
        return JobType.fromId(gateway.currentProfile(player.getUniqueId())
                .map(profile -> profile.primaryClassId()).orElse(null));
    }

    public int getXp(final Player player) {
        final ClassSpecProfileGateway gateway = profileGateway;
        if (gateway == null || !gateway.isSessionReady(player.getUniqueId())) return 0;
        return gateway.currentProfile(player.getUniqueId()).map(profile -> profile.classExperience()).orElse(0);
    }

    public int getPrimaryLevel(final Player player) {
        final ClassSpecProfileGateway gateway = profileGateway;
        if (gateway == null || !gateway.isSessionReady(player.getUniqueId())) return 0;
        return gateway.currentProfile(player.getUniqueId()).map(profile -> profile.classLevel()).orElse(0);
    }

    public boolean canSelectPrimary(final Player player, final JobType job) {
        if (job == null || !meetsFactionRequirement(player, job)) return false;
        final ClassSpecProfileGateway gateway = profileGateway;
        return gateway != null && gateway.isSessionReady(player.getUniqueId())
                && gateway.currentProfile(player.getUniqueId())
                .map(profile -> profile.primaryClassId().isEmpty()).orElse(false);
    }

    public boolean meetsFactionRequirement(final Player player, final JobType job) {
        final FactionType required = job == null ? null : job.getRequiredFaction();
        return required == null || factionManager.isMember(player.getUniqueId(), required);
    }

    /** Legacy synchronous class mutation is intentionally unsupported in greenfield Profile v2. */
    public boolean setPrimaryJob(final Player player, final JobType job) { return false; }

    public CompletionStage<Boolean> setPrimaryJobV2(final Player player, final JobType job) {
        Objects.requireNonNull(player, "player");
        if (!canSelectPrimary(player, job)) return CompletableFuture.completedFuture(false);
        if (job == JobType.DEATH_KNIGHT
                && configManager.getBoolean("classes.death-knight.dark-only", false)) {
            final FactionManager factions = factionManagerRef;
            if (factions != null && !factions.isMember(player.getUniqueId(), FactionType.DARK)) {
                return CompletableFuture.completedFuture(false);
            }
        }
        final String operationId = "class-assign:" + player.getUniqueId() + ':' + job.getId();
        return gateway().assignClass(player.getUniqueId(),
                new ClassSpecProfileGateway.ClassAssignmentRequest(job.getId(), 1, 0, operationId))
                .thenCompose(result -> {
                    if (!result.committed() && result.status() != ProfileMutationResult.Status.NO_CHANGE) {
                        return CompletableFuture.completedFuture(false);
                    }
                    return schedulePlayer(player, () -> {
                        mirrorClassState(player);
                        applyAutoUnlocks(player);
                        AdvancementService.award(player, "root");
                        AdvancementService.award(player, "first_class");
                    }).thenApply(ignored -> true);
                });
    }

    /** Compatibility entry point retained for callers; level already lives in Profile v2. */
    public CompletionStage<Boolean> mirrorPrimaryLevelV2(final Player player) {
        return CompletableFuture.completedFuture(profileGateway != null
                && profileGateway.isSessionReady(player.getUniqueId()));
    }

    /** Legacy synchronous XP mutation is intentionally unsupported. */
    public boolean addXpToJob(final Player player, final int amount) { return false; }
    /** Legacy synchronous XP mutation is intentionally unsupported. */
    public boolean setXp(final Player player, final int xp) { return false; }

    public CompletionStage<Boolean> addXpToJobV2(final Player player, final int amount,
                                                  final String operationId) {
        if (amount <= 0 || !hasPrimaryJob(player)) return CompletableFuture.completedFuture(false);
        return mutateXp(player, ClassSpecProfileGateway.ClassExperienceRequest.Mode.ADD,
                amount, operationId);
    }

    public CompletionStage<Boolean> setXpV2(final Player player, final int xp,
                                             final String operationId) {
        if (xp < 0 || !hasPrimaryJob(player)) return CompletableFuture.completedFuture(false);
        return mutateXp(player, ClassSpecProfileGateway.ClassExperienceRequest.Mode.SET,
                xp, operationId);
    }

    private CompletionStage<Boolean> mutateXp(final Player player,
                                               final ClassSpecProfileGateway.ClassExperienceRequest.Mode mode,
                                               final int value, final String operationId) {
        final int baseXp = Math.max(1, configManager.getInt("classes.leveling.base-xp", 100));
        final int increment = Math.max(0, configManager.getInt("classes.leveling.increment-per-level", 20));
        return gateway().mutateClassExperience(player.getUniqueId(),
                new ClassSpecProfileGateway.ClassExperienceRequest(mode, value, baseXp, increment, operationId))
                .thenCompose(result -> {
                    if (!result.committed() && result.status() != ProfileMutationResult.Status.NO_CHANGE) {
                        return CompletableFuture.completedFuture(false);
                    }
                    return schedulePlayer(player, () -> {
                        mirrorClassState(player);
                        applyAutoUnlocks(player);
                        if (getPrimaryLevel(player) >= MAX_JOB_LEVEL) AdvancementService.award(player, "class_max");
                        final java.util.function.Consumer<Player> hook = xpChangeHook;
                        if (hook != null) hook.accept(player);
                    }).thenApply(ignored -> true);
                });
    }

    public void setXpChangeHook(final java.util.function.Consumer<Player> hook) { xpChangeHook = hook; }

    public void applyAutoUnlocks(final Player player) {
        final JobType job = getPrimaryJob(player);
        if (job == null || configManager.getConfiguration() == null) return;
        final ConfigurationSection unlocks = configManager.getConfiguration()
                .getConfigurationSection("classes." + job.getId() + ".spell-unlocks");
        if (unlocks == null) return;
        final int level = getPrimaryLevel(player);
        for (final String spellId : unlocks.getKeys(false)) {
            final int required = unlocks.getInt(spellId, Integer.MAX_VALUE);
            if (level >= required && unlockSpell(player, spellId, SOURCE_BASE_PREFIX + job.getId())) {
                player.sendMessage(messageManager.getMessage("job-spell-auto-unlocked",
                        "&aÚj képesség feloldva: &e{spell} &7(szint {level})",
                        Map.of("spell", messageManager.get("spell." + spellId.toLowerCase(Locale.ROOT) + ".name",
                                spellId.toLowerCase(Locale.ROOT)), "level", String.valueOf(required))));
            }
        }
    }

    public List<String> getUnlockedSpellIds(final Player player) {
        return List.copyOf(readLedger(player).spellIds());
    }

    public boolean hasUnlockedSpell(final Player player, final String spellId) {
        if (spellId == null || spellId.isBlank()) return false;
        return readLedger(player).contains(spellId);
    }

    /** Replaces only the derived mirror; every spell receives explicit ADMIN provenance. */
    public void setUnlockedSpellIds(final Player player, final List<String> spellIds) {
        SpellGrantLedger ledger = SpellGrantLedger.empty();
        if (spellIds != null) {
            for (final String spellId : spellIds) {
                if (spellId != null && !spellId.isBlank()) ledger = ledger.add(spellId, SOURCE_ADMIN).ledger();
            }
        }
        writeLedger(player, ledger);
    }

    /** Unqualified grants are admin grants, never inferred legacy grants. */
    public boolean unlockSpell(final Player player, final String spellId) {
        return unlockSpell(player, spellId, SOURCE_ADMIN);
    }

    public boolean unlockSpell(final Player player, final String spellId, final String source) {
        final SpellGrantLedger.Mutation mutation = readLedger(player).add(spellId, source);
        if (mutation.changed()) writeLedger(player, mutation.ledger());
        return mutation.spellLockChanged();
    }

    public boolean revokeGrant(final Player player, final String spellId, final String source) {
        final SpellGrantLedger.Mutation mutation = readLedger(player).remove(spellId, source);
        if (mutation.changed()) writeLedger(player, mutation.ledger());
        return mutation.spellLockChanged();
    }

    public List<String> revokeGrantsFrom(final Player player, final Predicate<String> sourceMatches) {
        final SpellGrantLedger.RevokeResult result = readLedger(player).revokeSources(sourceMatches);
        if (result.changed()) writeLedger(player, result.ledger());
        return result.lockedSpellIds();
    }

    public void clearSpellGrants(final Player player) { writeLedger(player, SpellGrantLedger.empty()); }

    public Set<String> getGrantSources(final Player player, final String spellId) {
        if (spellId == null || spellId.isBlank()) return Set.of();
        return readLedger(player).sources(spellId);
    }

    /** No backfill exists in greenfield mode; malformed or source-less grants fail closed. */
    public void backfillSpellGrants(final Player player) {
        readLedger(player);
    }

    /** Runtime cleanup after an already durable admin class reset. */
    public void resetClass(final Player player) {
        revokeGrantsFrom(player, source -> source.startsWith(SOURCE_BASE_PREFIX)
                || source.startsWith(SOURCE_SPEC_PREFIX));
        final PersistentDataContainer pdc = player.getPersistentDataContainer();
        pdc.remove(jobPrimaryKey);
        pdc.remove(jobPrimaryXpKey);
        pdc.remove(legacySecondaryKey);
        pdc.remove(legacySecondaryXpKey);
    }

    private SpellGrantLedger readLedger(final Player player) {
        final String raw = player.getPersistentDataContainer().get(spellGrantsKey, PersistentDataType.STRING);
        try {
            return SpellGrantLedger.parse(raw);
        } catch (final IllegalArgumentException corrupt) {
            final ClassSpecProfileGateway gateway = profileGateway;
            if (gateway != null) gateway.blockSession(player.getUniqueId(),
                    "Corrupt explicit spell provenance ledger: " + corrupt.getMessage());
            throw corrupt;
        }
    }

    private void writeLedger(final Player player, final SpellGrantLedger ledger) {
        final PersistentDataContainer pdc = player.getPersistentDataContainer();
        final String serialized = ledger.serialize();
        if (serialized.isEmpty()) {
            pdc.remove(spellGrantsKey);
            pdc.remove(unlockedSpellsKey);
        } else {
            pdc.set(spellGrantsKey, PersistentDataType.STRING, serialized);
            pdc.set(unlockedSpellsKey, PersistentDataType.STRING, String.join(",", ledger.spellIds()));
        }
    }

    private void mirrorClassState(final Player player) {
        final PersistentDataContainer pdc = player.getPersistentDataContainer();
        final var profile = gateway().currentProfile(player.getUniqueId()).orElse(null);
        if (profile == null || profile.primaryClassId().isEmpty()) {
            pdc.remove(jobPrimaryKey);
            pdc.remove(jobPrimaryXpKey);
            return;
        }
        pdc.set(jobPrimaryKey, PersistentDataType.STRING, profile.primaryClassId());
        pdc.set(jobPrimaryXpKey, PersistentDataType.INTEGER, profile.classExperience());
    }

    private CompletionStage<Void> schedulePlayer(final Player player, final Runnable work) {
        final CompletableFuture<Void> result = new CompletableFuture<>();
        player.getScheduler().run(plugin, task -> {
            try { work.run(); result.complete(null); }
            catch (final Throwable failure) { result.completeExceptionally(failure); }
        }, () -> result.completeExceptionally(new IllegalStateException("Player scheduler rejected Profile v2 effect")));
        return result;
    }

    public void cleanup(final UUID playerId) { }
    public void clearPlayerState(final UUID playerId) { cleanup(playerId); }
}
