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
    private record FixtureChunk(UUID world, int x, int z) { }
    private final JavaPlugin plugin;
    private final MobAbilityRuntime runtime;
    private final AuthoredCreatureSpawnService spawns;
    private final MobTemplateRegistry templates;
    private final MobAbilityRegistry abilities;
    private final UUID fixtureOwner = UUID.randomUUID();
    private final AtomicBoolean finished = new AtomicBoolean();
    private volatile UUID entityId;
    private volatile FixtureChunk fixtureChunk;
    private MobRuntimeControlProbe(JavaPlugin plugin, MobAbilityRuntime runtime, AuthoredCreatureSpawnService spawns,
            MobTemplateRegistry templates, MobAbilityRegistry abilities) {
        this.plugin = plugin; this.runtime = runtime; this.spawns = spawns; this.templates = templates; this.abilities = abilities;
    }
    public static void maybeRun(JavaPlugin plugin, MobAbilityRuntime runtime, AuthoredCreatureSpawnService spawns,
            MobTemplateRegistry templates, MobAbilityRegistry abilities) {
        if (!Boolean.getBoolean(PROPERTY)) return;
        final var probe = new MobRuntimeControlProbe(plugin, runtime, spawns, templates, abilities);
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
    private void begin() {
        try {
            check(Bukkit.isGlobalTickThread() && Bukkit.getOnlinePlayers().isEmpty(), "ISOLATED_GLOBAL_OWNER_REQUIRED");
            final var world = Bukkit.getWorlds().getFirst(); final var point = world.getSpawnLocation(); final var choice = choice();
            final UUID worldId = world.getUID(); final int x = point.getBlockX(), y = point.getBlockY() + 4, z = point.getBlockZ();
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
        final var entity = Bukkit.getEntity(entityId);
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
            spawns.detach(mob); spawns.cleanupSummons(fixtureOwner);
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> finish(Bukkit.getEntity(entityId) == null, "NATIVE_LIFECYCLE"), 5);
        } catch (Throwable failure) { failed(failure); }
    }
    private void later(UUID id, long ticks, Runnable action) {
        final var entity = Bukkit.getEntity(id);
        check(entity != null && Bukkit.isOwnedByCurrentRegion(entity), "SCHEDULER_OWNER_REQUIRED");
        check(entity.getScheduler().runDelayed(plugin, task -> action.run(), () -> finish(false, "OWNER_RETIRED"), ticks) != null, "OWNER_REJECTED");
    }
    private void finish(boolean success, String code) {
        if (!finished.compareAndSet(false, true)) return;
        spawns.cleanupSummons(fixtureOwner);
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
