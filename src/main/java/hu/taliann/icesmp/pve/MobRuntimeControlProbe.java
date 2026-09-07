package hu.taliann.icesmp.pve;

import hu.taliann.icesmp.pve.MobRuntimeControlLedger.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Mob;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Explicit isolated-server CI fixture; no developer authority, artifact issuance or gameplay gate is enabled. */
public final class MobRuntimeControlProbe {
    private static final String PROPERTY = "icesmp.pve-control-runtime";
    private record Choice(String template, String shield, String target, long telegraph) { }
    private record AllyChoice(String template, String ability, long telegraph) { }
    private record FixtureChunk(UUID world, int x, int z) { }
    private final JavaPlugin plugin;
    private final MobAbilityRuntime runtime;
    private final AuthoredCreatureSpawnService spawns;
    private final MobTemplateRegistry templates;
    private final MobAbilityRegistry abilities;
    private final hu.taliann.icesmp.managers.AchievementManager achievements;
    private final UUID fixtureOwner = UUID.randomUUID();
    private final AtomicBoolean finished = new AtomicBoolean();
    private volatile UUID entityId;
    private volatile UUID allyCasterId;
    private volatile UUID summonerId;
    private volatile Set<UUID> createdChildren = Set.of();
    private volatile FixtureChunk fixtureChunk;
    private MobRuntimeControlProbe(JavaPlugin plugin, MobAbilityRuntime runtime, AuthoredCreatureSpawnService spawns,
            MobTemplateRegistry templates, MobAbilityRegistry abilities, hu.taliann.icesmp.managers.AchievementManager achievements) {
        this.plugin = plugin; this.runtime = runtime; this.spawns = spawns; this.templates = templates; this.abilities = abilities; this.achievements = achievements;
    }
    public static void maybeRun(JavaPlugin plugin, MobAbilityRuntime runtime, AuthoredCreatureSpawnService spawns,
            MobTemplateRegistry templates, MobAbilityRegistry abilities, hu.taliann.icesmp.managers.AchievementManager achievements) {
        if (!Boolean.getBoolean(PROPERTY)) return;
        final var probe = new MobRuntimeControlProbe(plugin, runtime, spawns, templates, abilities, achievements);
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> probe.begin(), 40);
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> probe.finish(false, "TIMEOUT"), 600);
    }
    private Choice choice() {
        for (final var template : templates.all().values().stream().sorted(Comparator.comparing(MobTemplate::mobId)).toList()) {
            final var selected = MobAbilityRuntime.effectiveDefinitions(new EffectiveMobProjection(template.mobId(), template.rank(), Optional.of(template.archetype()),
                    template.abilityIdsFor(template.rank()), template.behavior(), Set.of()), abilities::require);
            final var shield = selected.stream().filter(a -> a.kind() == MobAbilityDefinition.Kind.SHIELD && a.targetRule() == MobAbilityDefinition.TargetRule.SELF
                    && a.conditions().isEmpty() && a.actions().isEmpty()).findFirst();
            final var target = selected.stream().filter(a -> a.targetRule() == MobAbilityDefinition.TargetRule.CURRENT_TARGET && a.conditions().isEmpty()).findFirst();
            if (shield.isPresent() && target.isPresent()) return new Choice(template.mobId(), shield.get().abilityId(), target.get().abilityId(), shield.get().telegraphTicks());
        }
        throw new IllegalStateException("NATIVE_FIXTURE_CONTENT_UNAVAILABLE");
    }
    private AllyChoice allyChoice() {
        for (final var template : templates.all().values().stream().sorted(Comparator.comparing(MobTemplate::mobId)).toList()) {
            final var selected = MobAbilityRuntime.effectiveDefinitions(new EffectiveMobProjection(template.mobId(), template.rank(), Optional.of(template.archetype()),
                    template.abilityIdsFor(template.rank()), template.behavior(), Set.of()), abilities::require);
            final var ability = selected.stream().filter(a -> a.kind() == MobAbilityDefinition.Kind.ALLY_BUFF
                    && a.targetRule() == MobAbilityDefinition.TargetRule.SELF && a.conditions().isEmpty()).findFirst();
            if (ability.isPresent()) return new AllyChoice(template.mobId(), ability.get().abilityId(), ability.get().telegraphTicks());
        }
        throw new IllegalStateException("NATIVE_ALLY_FIXTURE_UNAVAILABLE");
    }
    private AllyChoice summonChoice() {
        for (final var template : templates.all().values().stream().sorted(Comparator.comparing(MobTemplate::mobId)).toList()) {
            final var selected = MobAbilityRuntime.effectiveDefinitions(new EffectiveMobProjection(template.mobId(), template.rank(), Optional.of(template.archetype()),
                    template.abilityIdsFor(template.rank()), template.behavior(), Set.of()), abilities::require);
            final var ability = selected.stream().filter(a -> a.kind() == MobAbilityDefinition.Kind.COMPOSITE
                    && !a.actions().isEmpty() && a.actions().stream().allMatch(action -> action.type() == MobTechniqueAction.Type.SUMMON_TEMPLATE)
                    && a.targetRule() == MobAbilityDefinition.TargetRule.SELF
                    && a.conditions().stream().allMatch(condition -> condition.type() == MobTechniqueCondition.Type.HEALTH_BELOW)
                    && a.maxSummons() > 0).findFirst();
            if (ability.isPresent()) return new AllyChoice(template.mobId(), ability.get().abilityId(), ability.get().telegraphTicks());
        }
        throw new IllegalStateException("NATIVE_SUMMON_FIXTURE_UNAVAILABLE");
    }
    private void begin() {
        try {
            check(Bukkit.isGlobalTickThread() && Bukkit.getOnlinePlayers().isEmpty(), "ISOLATED_GLOBAL_OWNER_REQUIRED");
            final var world = Bukkit.getWorlds().getFirst(); final var point = world.getSpawnLocation(); final var choice = choice();
            final UUID worldId = world.getUID(); final int x = (point.getBlockX() & ~15) + 8, y = point.getBlockY() + 4, z = (point.getBlockZ() & ~15) + 8;
            // Test setup creates one loaded fixture chunk. Runtime actions themselves never request a chunk load.
            world.getChunkAtAsync(x >> 4, z >> 4).thenRun(() -> Bukkit.getGlobalRegionScheduler().execute(plugin, () -> {
                final var current = Bukkit.getWorld(worldId);
                if (current == null) { finish(false, "WORLD_UNAVAILABLE"); return; }
                Bukkit.getRegionScheduler().execute(plugin, current, x >> 4, z >> 4, () -> spawn(worldId, x, y, z, choice, 0));
            })).exceptionally(failure -> { finish(false, "FIXTURE_CHUNK_FAILURE"); return null; });
        } catch (Throwable failure) { failed(failure); }
    }
    private void spawn(UUID worldId, int x, int y, int z, Choice choice, int attempt) {
        if (finished.get()) return;
        try {
            final var world = Bukkit.getWorld(worldId);
            check(world != null, "FIXTURE_WORLD_REQUIRED");
            check(Bukkit.isOwnedByCurrentRegion(world, x >> 4, z >> 4), "FIXTURE_REGION_REQUIRED");
            // A completed async load is not a lease: the empty server may unload before this continuation.
            // Only this explicitly enabled CI fixture loads/pins a chunk, and releases its own ticket below.
            if (fixtureChunk == null) {
                check(world.addPluginChunkTicket(x >> 4, z >> 4, plugin), "FIXTURE_TICKET_ALREADY_OWNED");
                fixtureChunk = new FixtureChunk(worldId, x >> 4, z >> 4);
            }
            // Folia ticket admission and full/entity-loaded publication occur in separate region ticks.
            if (!world.isChunkLoaded(x >> 4, z >> 4) || !world.getChunkAt(x >> 4, z >> 4).isEntitiesLoaded()) {
                check(attempt < 100, "FIXTURE_CHUNK_UNAVAILABLE");
                Bukkit.getRegionScheduler().runDelayed(plugin, world, x >> 4, z >> 4,
                        task -> spawn(worldId, x, y, z, choice, attempt + 1), 2);
                return;
            }
            final double height = Math.max(world.getMinHeight() + 3, Math.min(world.getMaxHeight() - 3, y));
            final var request = AuthoredCreatureSpawnService.Request.template("runtime_control_probe", fixtureOwner.toString(), "probe",
                    choice.template(), 10, AuthoredCreatureSpawnService.RewardOwner.NONE, true, 1, 1, 400).summonedBy(fixtureOwner);
            final Mob mob = spawns.spawn(new Location(world, x + 0.5, height, z + 0.5), request);
            check(mob != null && Bukkit.isOwnedByCurrentRegion(mob), "FIXTURE_SPAWN_FAILED"); entityId = mob.getUniqueId();
            mob.setAI(false); mob.setGravity(false); mob.setInvulnerable(true);
            later(entityId, 3, () -> cast(choice));
        } catch (Throwable failure) { failed(failure); }
    }
    private Mob mob() {
        return mob(entityId);
    }
    private Mob mob(UUID id) {
        final var entity = Bukkit.getEntity(id);
        check(entity instanceof Mob && Bukkit.isOwnedByCurrentRegion(entity), "ENTITY_OWNER_REQUIRED");
        final Mob mob = (Mob) entity; check(mob.isValid() && !mob.isDead(), "FIXTURE_ENTITY_RETIRED"); return mob;
    }
    private Request request(Mob mob, Kind kind, String ability) {
        return new Request(UUID.randomUUID(), kind, ability, runtime.controlView(mob).orElseThrow().stamp());
    }
    private void denied(Mob mob, Kind kind, String ability) {
        try { runtime.control(mob, request(mob, kind, ability), () -> { }); throw new IllegalStateException("NATIVE_DENIAL_BYPASSED"); }
        catch (MobRuntimeControlLedger.Rejected expected) { check(expected.code().equals("CONTROL_UNAVAILABLE"), "UNEXPECTED_CONTROL_DENIAL"); }
    }
    private void cast(Choice choice) {
        try {
            final Mob mob = mob(); final CanonicalMobProfile canonical = runtime.canonicalProfile(mob);
            check(runtime.activeAbilityIds(mob).containsAll(List.of(choice.shield(), choice.target())), "NATIVE_KIT_MISMATCH");
            check(AuthoredCreatureSpawnService.rewardOwner(mob) == AuthoredCreatureSpawnService.RewardOwner.NONE, "FIXTURE_REWARD_OWNER");
            check(AuthoredCreatureSpawnService.summonOrigin(mob).orElseThrow().equals(fixtureOwner), "FIXTURE_CANONICAL_SUMMON_ORIGIN");
            check(hu.taliann.icesmp.integrity.BukkitRewardSources.causal(mob).contains(new hu.taliann.icesmp.integrity.RewardSource.Entity(fixtureOwner)),
                    "FIXTURE_PROVIDER_CAUSAL_ORIGIN");
            denied(mob, Kind.FORCE_ABILITY, choice.target());
            denied(mob, Kind.FORCE_ABILITY, "runtime_probe_not_in_kit");
            runtime.pause(mob); denied(mob, Kind.FORCE_ABILITY, choice.shield()); runtime.resume(mob);
            mob.removePotionEffect(PotionEffectType.RESISTANCE);
            final var request = request(mob, Kind.FORCE_ABILITY, choice.shield());
            final var accepted = runtime.control(mob, request, () -> check(Bukkit.isOwnedByCurrentRegion(mob), "FINAL_OWNER_REQUIRED"));
            check(runtime.control(mob, request, () -> { throw new IllegalStateException("DUPLICATE_CAST_ADMISSION"); }).equals(accepted), "DUPLICATE_ACCEPTANCE_CHANGED");
            final String cooldown = cooldowns(mob);
            runtime.control(mob, request(mob, Kind.REFRESH, ""), () -> { });
            check(cooldowns(mob).equals(cooldown) && runtime.canonicalProfile(mob).equals(canonical), "REFRESH_RESET_COOLDOWN_OR_CANONICAL_PROFILE");
            denied(mob, Kind.FORCE_ABILITY, choice.shield());
            later(entityId, choice.telegraph() + 2, () -> executed(choice, canonical));
        } catch (Throwable failure) { failed(failure); }
    }
    private String cooldowns(Mob mob) {
        final String summary = runtime.activeStateSummary(mob); final int offset = summary.indexOf(",ready=");
        check(offset >= 0, "NATIVE_COOLDOWN_INSPECTION_UNAVAILABLE"); return summary.substring(offset);
    }
    private void executed(Choice choice, CanonicalMobProfile canonical) {
        try {
            final Mob mob = mob(); check(mob.hasPotionEffect(PotionEffectType.RESISTANCE), "NATIVE_CAST_DID_NOT_EXECUTE");
            runtime.pause(mob); final String cooldown = cooldowns(mob);
            runtime.control(mob, request(mob, Kind.REFRESH, ""), () -> { });
            check(cooldowns(mob).equals(cooldown), "PAUSED_REFRESH_RESET_COOLDOWN");
            denied(mob, Kind.FORCE_ABILITY, choice.shield());
            later(entityId, 40, () -> cooled(choice, canonical));
        } catch (Throwable failure) { failed(failure); }
    }
    private void cooled(Choice choice, CanonicalMobProfile canonical) {
        try {
            final Mob mob = mob(); runtime.resume(mob); denied(mob, Kind.FORCE_ABILITY, choice.shield());
            check(runtime.canonicalProfile(mob).equals(canonical), "CONTROL_CHANGED_CANONICAL_IDENTITY");
            runtime.pause(mob); mob.removePotionEffect(PotionEffectType.STRENGTH);
            final AllyChoice ally = allyChoice();
            final var request = AuthoredCreatureSpawnService.Request.template("runtime_control_probe", fixtureOwner.toString(), "ally_probe",
                    ally.template(), 10, AuthoredCreatureSpawnService.RewardOwner.NONE, true, 1, 1, 400).summonedBy(fixtureOwner);
            final Mob caster = spawns.spawn(mob.getLocation(), request);
            check(caster != null, "ALLY_FIXTURE_SPAWN_FAILED"); allyCasterId = caster.getUniqueId();
            caster.setAI(false); caster.setGravity(false); caster.setInvulnerable(true);
            later(allyCasterId, 3, () -> allyCast(ally));
        } catch (Throwable failure) { failed(failure); }
    }
    private void allyCast(AllyChoice choice) {
        try {
            final Mob caster = mob(allyCasterId);
            runtime.control(caster, request(caster, Kind.FORCE_ABILITY, choice.ability()), () -> { });
            later(allyCasterId, choice.telegraph() + 5, this::allyExecuted);
        } catch (Throwable failure) { failed(failure); }
    }
    private void allyExecuted() {
        try {
            final Mob caster = mob(allyCasterId); runtime.pause(caster);
            final UUID target = entityId;
            final var handle = Bukkit.getEntity(target); check(handle != null, "ALLY_TARGET_RETIRED");
            handle.getScheduler().run(plugin, task -> {
                try {
                    final Mob ally = mob(target);
                    check(ally.hasPotionEffect(PotionEffectType.STRENGTH), "NATIVE_EFFECT_GATE_DID_NOT_EXECUTE");
                    final AllyChoice choice = summonChoice();
                    final var request = AuthoredCreatureSpawnService.Request.template("runtime_control_probe", fixtureOwner.toString(), "summon_probe",
                            choice.template(), 10, AuthoredCreatureSpawnService.RewardOwner.NONE, true, 1, 1, 400).summonedBy(fixtureOwner);
                    final Mob summoner = spawns.spawn(ally.getLocation(), request);
                    check(summoner != null, "SUMMON_FIXTURE_SPAWN_FAILED"); summonerId = summoner.getUniqueId();
                    summoner.setAI(false); summoner.setGravity(false); summoner.setInvulnerable(true);
                    later(summonerId, 3, () -> summonCast(choice));
                } catch (Throwable failure) { failed(failure); }
            }, () -> finish(false, "ALLY_TARGET_RETIRED"));
        } catch (Throwable failure) { failed(failure); }
    }
    private void summonCast(AllyChoice choice) {
        try {
            final Mob summoner = mob(summonerId);
            final double threshold = abilities.require(choice.ability()).conditions().stream().mapToDouble(MobTechniqueCondition::value).min().orElse(1.0D);
            final var maximum = summoner.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            check(maximum != null && maximum.getValue() > 1.0D, "SUMMON_FIXTURE_HEALTH_UNAVAILABLE");
            summoner.setHealth(Math.max(1.0D, maximum.getValue() * threshold * 0.8D));
            runtime.control(summoner, request(summoner, Kind.FORCE_ABILITY, choice.ability()), () -> { });
            later(summonerId, choice.telegraph() + 5, this::summonExecuted);
        } catch (Throwable failure) { failed(failure); }
    }
    private void summonExecuted() {
        try {
            final Mob summoner = mob(summonerId); runtime.pause(summoner);
            final Set<UUID> children = new HashSet<>();
            for (final var entity : summoner.getNearbyEntities(6, 8, 6)) {
                if (!(entity instanceof Mob child) || !Bukkit.isOwnedByCurrentRegion(child)
                        || !AuthoredCreatureSpawnService.summonOrigin(child).filter(summonerId::equals).isPresent()) continue;
                children.add(child.getUniqueId()); createdChildren = Set.copyOf(children);
                check(!child.isPersistent(), "SUMMON_RESTART_GHOST");
                check(hu.taliann.icesmp.integrity.BukkitRewardSources.causal(child).contains(new hu.taliann.icesmp.integrity.RewardSource.Entity(summonerId)),
                        "SUMMON_NATIVE_ORIGIN_MISSING");
                spawns.detach(child); child.remove();
            }
            check(!children.isEmpty() && children.size() <= 8, "NATIVE_CREATION_GATE_DID_NOT_EXECUTE");
            spawns.cleanupSummons(summonerId); spawns.cleanupSummons(fixtureOwner);
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
                if (Bukkit.getEntity(entityId) != null || Bukkit.getEntity(allyCasterId) != null || Bukkit.getEntity(summonerId) != null
                        || createdChildren.stream().anyMatch(id -> Bukkit.getEntity(id) != null)) finish(false, "NATIVE_LIFECYCLE");
                else verifyRewardSettlement();
            }, 5);
        } catch (Throwable failure) { failed(failure); }
    }

    /** Actual manager delivery from a server owner into native profile/economy IO; no connected player is synthesized. */
    private void verifyRewardSettlement() {
        if (finished.get()) return;
        try {
            check(Bukkit.getOnlinePlayers().isEmpty(), "PLAYERS_PRESENT");
            final var authority = hu.taliann.icesmp.playerprofile.application.PlayerProfileAuthority.current();
            final var store = new hu.taliann.icesmp.playerprofile.application.PlayerProfileAchievementStore();
            final var pending = new hu.taliann.icesmp.playerprofile.application.PlayerProfileAchievementStore.PendingReward(
                    "bestiary:mobs:1", hu.taliann.icesmp.playerprofile.application.PlayerProfileAchievementStore.RewardKind.CURRENCY, 1, "neutral");
            authority.repository().load(fixtureOwner).thenCompose(snapshot -> {
                check(!finished.get(), "PROBE_RETIRED");
                return store.recordBestiaryWithRewards(fixtureOwner, "mobs", "native_fixture", Map.of(1, pending),
                        hu.taliann.icesmp.integrity.RewardContext.recipientOnly(hu.taliann.icesmp.integrity.RewardChannel.BESTIARY, fixtureOwner));
            }).whenComplete((admitted, failure) -> {
                if (failure != null) { failed(failure); return; }
                if (finished.get()) return;
                Bukkit.getGlobalRegionScheduler().execute(plugin, () -> {
                    if (finished.get()) return;
                    try {
                        check(admitted.record().created() && admitted.pending().equals(List.of(pending)), "NATIVE_MILESTONE_ADMISSION");
                        achievements.settlePendingReward(fixtureOwner, pending).thenCompose(settled -> {
                            check(Boolean.TRUE.equals(settled), "NATIVE_REWARD_SETTLEMENT");
                            check(!finished.get(), "PROBE_RETIRED");
                            return achievements.settlePendingReward(fixtureOwner, pending);
                        }).whenComplete((replayed, deliveryFailure) -> {
                            if (deliveryFailure != null) { failed(deliveryFailure); return; }
                            if (finished.get()) return;
                            try {
                                final var economy = new hu.taliann.icesmp.playerprofile.application.PlayerProfileEconomyStore();
                                check(Boolean.FALSE.equals(replayed) && store.rewardSettled(fixtureOwner, pending.receiptId())
                                        && store.pendingRewards(fixtureOwner).isEmpty()
                                        && economy.readCached(fixtureOwner).milli(hu.taliann.icesmp.data.CurrencyType.NEUTRAL) == 1000,
                                        "NATIVE_REWARD_REPLAY");
                                plugin.getLogger().info("ICESMP_KNOWLEDGE_REWARD_RUNTIME_PROBE_PASS scope=offline_profile_currency_settlement");
                                verifyQuestStatisticsSettlement();
                            } catch (Throwable invalid) { failed(invalid); }
                        });
                    } catch (Throwable invalid) { failed(invalid); }
                });
            });
        } catch (Throwable failure) { failed(failure); }
    }
    private void verifyQuestStatisticsSettlement() {
        if (finished.get()) return;
        Bukkit.getGlobalRegionScheduler().execute(plugin, () -> {
            if (finished.get()) return;
            try {
                final var quests = new hu.taliann.icesmp.playerprofile.application.PlayerProfileQuestStore();
                final var statistics = new hu.taliann.icesmp.playerprofile.application.PlayerProfileStatisticsStore();
                quests.accept(fixtureOwner, "native_fixture").thenCompose(accepted -> {
                    check(Boolean.TRUE.equals(accepted) && !finished.get(), "NATIVE_QUEST_ACCEPTANCE");
                    return quests.complete(fixtureOwner, "native_fixture", System.currentTimeMillis(), 0);
                }).thenCompose(receipt -> {
                    check(receipt.committed() && !finished.get(), "NATIVE_QUEST_ENTITLEMENT");
                    return quests.settleReward(fixtureOwner, receipt.receiptId()).thenCompose(settled -> {
                        check(Boolean.TRUE.equals(settled) && !finished.get(), "NATIVE_QUEST_SETTLEMENT");
                        return quests.settleReward(fixtureOwner, receipt.receiptId());
                    });
                }).whenComplete((replayed, failure) -> {
                    if (failure != null) { failed(failure); return; }
                    if (finished.get()) return;
                    try {
                        check(Boolean.FALSE.equals(replayed) && quests.pendingRewards(fixtureOwner).isEmpty()
                                && statistics.read(fixtureOwner, "quests-completed") == 1, "NATIVE_QUEST_STATISTICS_REPLAY");
                        plugin.getLogger().info("ICESMP_QUEST_STATISTICS_RUNTIME_PROBE_PASS scope=offline_profile_atomic_settlement");
                        finish(true, "NATIVE_LIFECYCLE_AND_REWARD");
                    } catch (Throwable invalid) { failed(invalid); }
                });
            } catch (Throwable invalid) { failed(invalid); }
        });
    }
    private void later(UUID id, long ticks, Runnable action) {
        final var entity = Bukkit.getEntity(id);
        check(entity != null && Bukkit.isOwnedByCurrentRegion(entity), "SCHEDULER_OWNER_REQUIRED");
        check(entity.getScheduler().runDelayed(plugin, task -> action.run(), () -> finish(false, "OWNER_RETIRED"), ticks) != null, "OWNER_REJECTED");
    }
    private void finish(boolean success, String code) {
        if (!finished.compareAndSet(false, true)) return;
        spawns.cleanupSummons(fixtureOwner);
        if (summonerId != null) spawns.cleanupSummons(summonerId);
        createdChildren.forEach(id -> hu.taliann.icesmp.utils.TransientEntities.removeById(plugin, id));
        final FixtureChunk fixture = fixtureChunk;
        if (fixture != null) {
            final var world = Bukkit.getWorld(fixture.world());
            if (world != null) {
                Bukkit.getRegionScheduler().execute(plugin, world, fixture.x(), fixture.z(), () -> {
                    final var current = Bukkit.getWorld(fixture.world());
                    final boolean released = current != null && Bukkit.isOwnedByCurrentRegion(current, fixture.x(), fixture.z())
                            && current.removePluginChunkTicket(fixture.x(), fixture.z(), plugin);
                    report(success && released, released ? code : "FIXTURE_TICKET_RELEASE_FAILED");
                });
                return;
            }
        }
        report(success, code);
    }
    private void report(boolean success, String code) {
        Bukkit.getGlobalRegionScheduler().execute(plugin, () -> {
            if (success) plugin.getLogger().info("ICESMP_PVE_CONTROL_RUNTIME_PROBE_PASS platform=" + Bukkit.getServer().getName());
            else plugin.getLogger().severe("ICESMP_PVE_CONTROL_RUNTIME_PROBE_FAIL code=" + code);
            Bukkit.shutdown();
        });
    }
    private void failed(Throwable failure) {
        final String code = failure.getMessage();
        finish(false, code != null && code.matches("[A-Z_]{1,80}") ? code : failure.getClass().getSimpleName());
    }
    private static void check(boolean condition, String code) { if (!condition) throw new IllegalStateException(code); }
}
