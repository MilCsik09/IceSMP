package hu.taliann.icesmp.itemization;

import hu.taliann.icesmp.items.ItemDataFactory;
import hu.taliann.icesmp.managers.ProfessionRecipeCatalog;
import hu.taliann.icesmp.pve.CreatureSpeciesPolicy;
import hu.taliann.icesmp.pve.CreatureSpeciesRegistry;
import hu.taliann.icesmp.pve.MobAbilityDefinition;
import hu.taliann.icesmp.pve.MobAbilityRegistry;
import hu.taliann.icesmp.pve.MobRank;
import hu.taliann.icesmp.pve.MobTemplateRegistry;
import hu.taliann.icesmp.pve.AuthoredCreatureSpawnService;
import hu.taliann.icesmp.pve.MobAbilityRuntime;
import hu.taliann.icesmp.pve.CombatTelemetry;
import hu.taliann.icesmp.managers.MobScalingManager;
import hu.taliann.icesmp.managers.EventSpawnGuard;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Opt-in Paper 1.21.11 source-integrity probe. The probe is inert in production and runs only
 * when the dedicated CI JVM property is present. Registry-dependent assertions intentionally run
 * on a real Paper server instead of a standalone JVM fixture.
 */
@SuppressWarnings("UnstableApiUsage")
public final class PaperSourceIntegrityRuntimeProbe {
    public static final String PROPERTY = "icesmp.source-integrity-runtime";
    public static final String AUTHORED_PVE_PROPERTY = "icesmp.authored-pve-runtime";
    public static final String PASS_MARKER = "ICESMP_SOURCE_INTEGRITY_RUNTIME_PROBE_PASS";

    private PaperSourceIntegrityRuntimeProbe() { }

    private static final class RuntimeEquipmentAdapter implements EquipmentRehomeTransaction.Adapter {
        private final Inventory inventory;
        private final AtomicReference<ItemStack> equipped;

        private RuntimeEquipmentAdapter(final Inventory inventory,
                                        final AtomicReference<ItemStack> equipped) {
            this.inventory = inventory;
            this.equipped = equipped;
        }

        @Override public ItemStack equipped() { return equipped.get(); }
        @Override public ItemStack[] storageContents() { return inventory.getStorageContents(); }
        @Override public void setEquipped(final ItemStack item) { equipped.set(item); }
        @Override public Map<Integer, ItemStack> addToStorage(final ItemStack item) {
            return inventory.addItem(item);
        }
        @Override public void restoreStorage(final ItemStack[] snapshot) {
            inventory.setStorageContents(snapshot);
        }
    }

    public static void maybeRun(final JavaPlugin plugin, final Object assembledCore) {
        final boolean authoredPve = Boolean.getBoolean(AUTHORED_PVE_PROPERTY);
        if (!Boolean.getBoolean(PROPERTY) && !authoredPve) return;
        final CreatureSpeciesRegistry creatureSpecies = readField(assembledCore,
                "creatureSpeciesRegistry", CreatureSpeciesRegistry.class);
        final MobAbilityRegistry mobAbilities = readField(assembledCore,
                "mobAbilityRegistry", MobAbilityRegistry.class);
        if (authoredPve) {
            final MobTemplateRegistry mobTemplates = readField(assembledCore,
                    "mobTemplateRegistry", MobTemplateRegistry.class);
            final AuthoredCreatureSpawnService authoredSpawns = readField(assembledCore,
                    "authoredCreatureSpawns", AuthoredCreatureSpawnService.class);
            final MobScalingManager mobScaling = readField(assembledCore,
                    "mobScalingManager", MobScalingManager.class);
            final MobAbilityRuntime mobAbilityRuntime = readField(assembledCore,
                    "mobAbilityRuntime", MobAbilityRuntime.class);
            plugin.getLogger().info("ICESMP_AUTHORED_PVE_RUNTIME_PROBE_ARMED");
            try {
                startAuthoredPveRuntimeProof(plugin, mobTemplates, mobAbilities,
                        authoredSpawns, creatureSpecies, mobScaling, mobAbilityRuntime);
            } catch (final Throwable failure) {
                plugin.getLogger().severe("ICESMP_SOURCE_INTEGRITY_RUNTIME_PROBE_FAIL: " + failure);
                failure.printStackTrace();
                Bukkit.shutdown();
            }
            return;
        }
        final ItemIdentityService identity = readField(assembledCore,
                "itemIdentityService", ItemIdentityService.class);
        final ItemTemplateRegistry templates = readField(assembledCore,
                "itemTemplateRegistry", ItemTemplateRegistry.class);
        final ProfessionRecipeCatalog catalog = readField(assembledCore,
                "professionRecipeCatalog", ProfessionRecipeCatalog.class);
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
            try {
                writeVanillaRuntimeBenchmark();
                verifyAttributeComponentSemantics();
                verifyEquipmentRuntimeStates(identity);
                verifyRp2ProductionPresentation(identity);
                verifyActualInventoryAtomicity();
                verifyMutationPhysicalState(identity);
                verifyCatalogPositiveLoad(identity, catalog, templates);
                verifyCreatureRuntime(creatureSpecies, mobAbilities);
                verifyCommandRuntime();
                plugin.getLogger().info(PASS_MARKER);
            } catch (final Throwable failure) {
                plugin.getLogger().severe("ICESMP_SOURCE_INTEGRITY_RUNTIME_PROBE_FAIL: " + failure);
                failure.printStackTrace();
            } finally {
                Bukkit.shutdown();
            }
        }, 1L);
    }

    /** Representative real-Paper dispatch proof for the permission-filtered admin surface. */
    private static void verifyCommandRuntime() throws Exception {
        final java.util.ArrayList<String> deniedMessages = new java.util.ArrayList<>();
        final CommandSender denied = probeSender(false, deniedMessages);
        check(dispatchRegisteredCommand(denied, "icesmp reload status"),
                "permission-denied /icesmp command did not route through Paper");
        check(!deniedMessages.isEmpty(), "permission-denied /icesmp command produced no feedback");

        final java.util.ArrayList<String> allowedMessages = new java.util.ArrayList<>();
        final CommandSender allowed = probeSender(true, allowedMessages);
        for (final String command : List.of(
                "icesmp",
                "icesmp reload status",
                "icesmp inspect config resource-pack.enabled",
                "icesmp config get resource-pack.enabled",
                "icesmp config menu",
                "icesmp invalid-subcommand",
                "ismp reload status")) {
            check(dispatchRegisteredCommand(allowed, command),
                    "Paper command dispatch rejected: /" + command);
        }
        check(allowedMessages.size() >= 7,
                "representative help/nested/invalid/player-only/alias commands produced incomplete feedback");
        final List<String> roots = commandCompletions(allowed, "icesmp ");
        check(roots.containsAll(List.of("reload", "config", "inspect", "client")),
                "permission-allowed root completion omitted /icesmp domains: " + roots);
        final List<String> configActions = commandCompletions(allowed, "icesmp config ");
        check(configActions.containsAll(List.of("menu", "get", "set", "unset", "list", "find")),
                "Paper trailing-space completion did not enter config domain: " + configActions);
        final List<String> deniedRoots = commandCompletions(denied, "icesmp ");
        check(deniedRoots.stream().noneMatch(List.of("reload", "config", "inspect", "client")::contains),
                "permission-denied root completion leaked /icesmp domains: " + deniedRoots);
        final hu.taliann.icesmp.managers.ConfigManager manager =
                hu.taliann.icesmp.managers.ConfigManager.current();
        check(manager != null, "ConfigManager runtime singleton unavailable");
        final hu.taliann.icesmp.managers.ConfigManager.ConfigSnapshot before = manager.snapshot();
        final JavaPlugin owner = JavaPlugin.getProvidingPlugin(PaperSourceIntegrityRuntimeProbe.class);
        final java.nio.file.Path general = owner.getDataFolder().toPath().resolve("config/general.yml");
        final byte[] original = java.nio.file.Files.readAllBytes(general);
        try {
            java.nio.file.Files.writeString(general, "invalid: [\n", java.nio.charset.StandardCharsets.UTF_8);
            check(dispatchRegisteredCommand(allowed, "icesmp reload operator"),
                    "invalid operator reload did not route through Paper");
            check(manager.snapshot() == before,
                    "invalid operator reload replaced the previously published snapshot");
        } finally {
            java.nio.file.Files.write(general, original);
        }
        Bukkit.getLogger().info("ICESMP_CONFIG_COMMAND_RUNTIME_PROBE_PASS");
    }

    private static boolean dispatchRegisteredCommand(final CommandSender sender, final String line) {
        final String[] parts = line.trim().split("\\s+");
        final org.bukkit.command.Command command = Bukkit.getCommandMap().getCommand(parts[0]);
        if (command == null) return false;
        return command.execute(sender, parts[0], java.util.Arrays.copyOfRange(parts, 1, parts.length));
    }

    private static List<String> commandCompletions(final CommandSender sender, final String line) {
        final List<String> completions = Bukkit.getCommandMap().tabComplete(sender, line);
        return completions == null ? List.of() : completions;
    }

    private static CommandSender probeSender(final boolean permitted, final List<String> messages) {
        return (CommandSender) java.lang.reflect.Proxy.newProxyInstance(
                PaperSourceIntegrityRuntimeProbe.class.getClassLoader(),
                new Class<?>[]{CommandSender.class},
                (proxy, method, args) -> {
                    final String name = method.getName();
                    if ("hasPermission".equals(name) || "isPermissionSet".equals(name)
                            || "isOp".equals(name)) return permitted;
                    if ("getName".equals(name)) return permitted ? "CI_ALLOWED_CONSOLE" : "CI_DENIED_CONSOLE";
                    if ("name".equals(name)) return net.kyori.adventure.text.Component.text(
                            permitted ? "CI_ALLOWED_CONSOLE" : "CI_DENIED_CONSOLE");
                    if ("getServer".equals(name)) return Bukkit.getServer();
                    if ("getEffectivePermissions".equals(name)) return java.util.Set.of();
                    if ("spigot".equals(name)) return new CommandSender.Spigot();
                    if ("sendMessage".equals(name) && args != null) {
                        for (final Object value : args) messages.add(String.valueOf(value));
                        return null;
                    }
                    if ("toString".equals(name)) return permitted ? "CI_ALLOWED_CONSOLE" : "CI_DENIED_CONSOLE";
                    if ("hashCode".equals(name)) return System.identityHashCode(proxy);
                    if ("equals".equals(name)) return proxy == (args == null ? null : args[0]);
                    final Class<?> type = method.getReturnType();
                    if (type == boolean.class) return false;
                    if (type == int.class || type == short.class || type == byte.class) return 0;
                    if (type == long.class) return 0L;
                    if (type == float.class) return 0.0F;
                    if (type == double.class) return 0.0D;
                    if (type == char.class) return '\0';
                    return null;
                });
    }

    /** Spawns authored roles/carrier variants and waits for common-runtime techniques to execute. */
    private static void startAuthoredPveRuntimeProof(final JavaPlugin plugin,
                                                     final MobTemplateRegistry templates,
                                                     final MobAbilityRegistry abilities,
                                                     final AuthoredCreatureSpawnService spawns,
                                                     final CreatureSpeciesRegistry species,
                                                     final MobScalingManager scaling,
                                                     final MobAbilityRuntime runtime) {
        final org.bukkit.World world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().getFirst();
        check(world != null, "authored PvE runtime world unavailable");
        hu.taliann.icesmp.pve.AuthoredPveContentValidator.validate(templates, abilities);
        plugin.getLogger().info("ICESMP_AUTHORED_PVE_RUNTIME_PROBE_VALIDATED");
        final org.bukkit.Location at = world.getSpawnLocation().clone().add(0.5D, 2.0D, 0.5D);
        final int probeChunkX = at.getBlockX() >> 4;
        final int probeChunkZ = at.getBlockZ() >> 4;
        Bukkit.getGlobalRegionScheduler().run(plugin, global -> {
            world.setTime(6000L);
            world.setStorm(false);
            world.setThundering(false);
            plugin.getServer().getRegionScheduler().runDelayed(plugin, at, task -> {
            world.setChunkForceLoaded(probeChunkX, probeChunkZ, true);
            plugin.getLogger().info("ICESMP_AUTHORED_PVE_RUNTIME_PROBE_REGION_START");
            final java.util.ArrayList<Mob> spawned = new java.util.ArrayList<>();
            final java.util.ArrayList<Mob> controls = new java.util.ArrayList<>();
            try {
                final Mob worldBoss = spawns.spawn(at.clone(),
                        AuthoredCreatureSpawnService.Request.template(
                                "runtime_probe", "runtime:world_boss", "boss", "ring_warden", 75,
                                AuthoredCreatureSpawnService.RewardOwner.NONE, true,
                                1.2D, 1.0D, 240L));
                plugin.getLogger().info("ICESMP_AUTHORED_PVE_RUNTIME_PROBE_WORLD_BOSS");
                final Mob champion = spawns.spawn(at.clone(),
                        AuthoredCreatureSpawnService.Request.template(
                                "runtime_probe", "runtime:invasion", "champion",
                                "invasion_chaos_champion", 30,
                                AuthoredCreatureSpawnService.RewardOwner.NONE, true,
                                1.0D, 1.0D, 240L));
                plugin.getLogger().info("ICESMP_AUTHORED_PVE_RUNTIME_PROBE_CHAMPION");
                final Mob prologue = spawns.spawn(at.clone(),
                        AuthoredCreatureSpawnService.Request.template(
                                "runtime_probe", "runtime:prologue", "boss",
                                "prologue_finale_boss", 55,
                                AuthoredCreatureSpawnService.RewardOwner.NONE, true,
                                1.1D, 1.0D, 240L));
                plugin.getLogger().info("ICESMP_AUTHORED_PVE_RUNTIME_PROBE_PROLOGUE");
                check(worldBoss != null && champion != null && prologue != null,
                        "authored PvE runtime spawn returned null");
                spawned.add(worldBoss); spawned.add(champion); spawned.add(prologue);
                final Location daylightSpot = at.clone().add(-3.0D, 0.0D, 0.0D);
                daylightSpot.setY(world.getHighestBlockYAt(daylightSpot.getBlockX(),
                        daylightSpot.getBlockZ()) + 2.0D);
                final Mob daytimeUndead = spawns.spawn(daylightSpot,
                        AuthoredCreatureSpawnService.Request.template(
                                "runtime_probe", "runtime:natural", "day_undead",
                                "sunscarred_wayfarer", 18,
                                AuthoredCreatureSpawnService.RewardOwner.NONE, true,
                                1.0D, 1.0D, 240L));
                final Mob nightUndead = spawns.spawn(at.clone().add(-5.0D, 0.0D, 0.0D),
                        AuthoredCreatureSpawnService.Request.template(
                                "runtime_probe", "runtime:natural", "night_undead",
                                "gallows_runner", 18,
                                AuthoredCreatureSpawnService.RewardOwner.NONE, true,
                                1.0D, 1.0D, 240L));
                final Mob skeletonVariant = spawns.spawn(at.clone().add(-7.0D, 0.0D, 0.0D),
                        AuthoredCreatureSpawnService.Request.template(
                                "runtime_probe", "runtime:natural", "defender",
                                "barrow_bulwark", 22,
                                AuthoredCreatureSpawnService.RewardOwner.NONE, true,
                                1.0D, 1.0D, 240L));
                final Mob spiderVariant = spawns.spawn(at.clone().add(-9.0D, 0.0D, 0.0D),
                        AuthoredCreatureSpawnService.Request.template(
                                "runtime_probe", "runtime:natural", "controller",
                                "moss_trapper", 20,
                                AuthoredCreatureSpawnService.RewardOwner.NONE, true,
                                1.0D, 1.0D, 240L));
                final Mob casterVariant = spawns.spawn(at.clone().add(-11.0D, 0.0D, 0.0D),
                        AuthoredCreatureSpawnService.Request.template(
                                "runtime_probe", "runtime:natural", "caster",
                                "mire_hexer", 24,
                                AuthoredCreatureSpawnService.RewardOwner.NONE, true,
                                1.0D, 1.0D, 240L));
                check(daytimeUndead != null && nightUndead != null && skeletonVariant != null
                                && spiderVariant != null && casterVariant != null,
                        "representative carrier variant spawn returned null");
                spawned.add(daytimeUndead); spawned.add(nightUndead); spawned.add(skeletonVariant);
                spawned.add(spiderVariant); spawned.add(casterVariant);
                daytimeUndead.getPersistentDataContainer().remove(new NamespacedKey(
                        "icesmp", EventSpawnGuard.EVENT_NO_BURN_KEY));
                nightUndead.getPersistentDataContainer().remove(new NamespacedKey(
                        "icesmp", EventSpawnGuard.EVENT_NO_BURN_KEY));
                scaling.reconcileTerritoryProtection(daytimeUndead);
                scaling.reconcileTerritoryProtection(nightUndead);
                check(daytimeUndead.getEquipment() != null,
                        "daytime undead lacks equipment carrier");
                daytimeUndead.getEquipment().setHelmet(null);
                check(scaling.hasAuthoredDaylightProtection(daytimeUndead),
                        "day-capable authored undead lacks authored daylight source");
                check(!scaling.hasAuthoredDaylightProtection(nightUndead),
                        "night-only undead received permanent authored daylight protection");
                check(world.getHighestBlockYAt(daylightSpot.getBlockX(), daylightSpot.getBlockZ())
                                < daytimeUndead.getLocation().getBlockY(),
                        "daylight undead probe location is not open sky");
                plugin.getLogger().info("ICESMP_AUTHORED_PVE_RUNTIME_PROBE_VARIANTS");
                controls.add((Mob) world.spawn(at.clone().add(3.0D, 0.0D, 0.0D),
                        EntityType.COW.getEntityClass().asSubclass(Mob.class)));
                controls.add((Mob) world.spawn(at.clone().add(5.0D, 0.0D, 0.0D),
                        EntityType.ZOMBIE.getEntityClass().asSubclass(Mob.class)));
                controls.add((Mob) world.spawn(at.clone().add(7.0D, 0.0D, 0.0D),
                        EntityType.SKELETON.getEntityClass().asSubclass(Mob.class)));
                controls.forEach(mob -> mob.setAI(false));
                plugin.getLogger().info("ICESMP_AUTHORED_PVE_RUNTIME_PROBE_CONTROLS");
                check("ring_warden".equals(hu.taliann.icesmp.managers.MobScalingManager
                                .templateIdOf(worldBoss))
                                && "invasion_chaos_champion".equals(
                                hu.taliann.icesmp.managers.MobScalingManager.templateIdOf(champion))
                                && "prologue_finale_boss".equals(
                                hu.taliann.icesmp.managers.MobScalingManager.templateIdOf(prologue)),
                        "authored runtime templates were not attached");
                boolean duplicateModifierRejected = false;
                try {
                    spawns.applyParticipantModifier(worldBoss, 1.01D, 1.0D,
                            "runtime:duplicate");
                } catch (final IllegalStateException expected) {
                    duplicateModifierRejected = true;
                }
                check(duplicateModifierRejected,
                        "encounter participant modifier accepted a duplicate application");
                plugin.getLogger().info("ICESMP_AUTHORED_PVE_RUNTIME_PROBE_PROVENANCE");
                spawns.pause(prologue);
                plugin.getServer().getRegionScheduler().runDelayed(plugin, at,
                        resume -> spawns.resume(prologue), 60L);
                final var maximumHealth = worldBoss.getAttribute(Attribute.MAX_HEALTH);
                check(maximumHealth != null && maximumHealth.getValue() > 0.0D,
                        "authored world boss lacks canonical maximum health");
                plugin.getServer().getRegionScheduler().runDelayed(plugin, at, exercise -> {
                    try {
                        check(Bukkit.isOwnedByCurrentRegion(worldBoss),
                                "runtime boss left its bounded probe region before threshold exercise");
                        plugin.getLogger().info("ICESMP_AUTHORED_PVE_RUNTIME_PROBE_ABILITIES "
                                + runtime.activeAbilityIds(worldBoss));
                        plugin.getLogger().info("ICESMP_AUTHORED_PVE_RUNTIME_PROBE_STATE "
                                + runtime.activeStateSummary(worldBoss));
                        worldBoss.setHealth(maximumHealth.getValue() * 0.40D);
                        worldBoss.damage(maximumHealth.getValue() * 0.20D);
                        worldBoss.damage(1.0D);
                        check(runtime.triggerTechnique(worldBoss, "ring_lock",
                                        MobAbilityDefinition.Trigger.ON_TIMER),
                                "typed timer trigger rejected the attached ring_lock technique");
                        plugin.getLogger().info("ICESMP_AUTHORED_PVE_RUNTIME_PROBE_THRESHOLD_ARMED");
                    } catch (final Throwable failure) {
                        plugin.getLogger().severe("ICESMP_SOURCE_INTEGRITY_RUNTIME_PROBE_FAIL: " + failure);
                        failure.printStackTrace();
                        world.setChunkForceLoaded(probeChunkX, probeChunkZ, false);
                        Bukkit.shutdown();
                    }
                }, 5L);
                plugin.getServer().getRegionScheduler().runDelayed(plugin, at, verify -> {
                    try {
                        final Map<String, Long> telemetry = CombatTelemetry.snapshot();
                        plugin.getLogger().info("ICESMP_AUTHORED_PVE_RUNTIME_PROBE_TELEMETRY " + telemetry);
                        check(telemetry.getOrDefault("technique_execute:ring_lock", 0L) > 0L,
                                "real authored world-boss technique did not execute through MobAbilityRuntime");
                        check(telemetry.getOrDefault("boss_phase_transition:summon_frozen_adds", 0L) == 1L,
                                "health threshold was not one-shot in the common runtime");
                        plugin.getLogger().info("ICESMP_AUTHORED_PVE_RUNTIME_PROBE_DAYLIGHT_STATE "
                                + "valid=" + daytimeUndead.isValid()
                                + ",dead=" + daytimeUndead.isDead()
                                + ",health=" + daytimeUndead.getHealth()
                                + ",fire=" + daytimeUndead.getFireTicks()
                                + ",helmet=" + (daytimeUndead.getEquipment() == null ? "none"
                                : daytimeUndead.getEquipment().getHelmet())
                                + ",ai=" + daytimeUndead.hasAI()
                                + ",abilities=" + runtime.activeAbilityIds(daytimeUndead));
                        check(daytimeUndead.isValid() && !daytimeUndead.isDead(),
                                "open-sky noon undead did not survive the proof window");
                        check(daytimeUndead.getFireTicks() <= 0,
                                "authored daylight protection left the noon undead burning");
                        check(hasNoHelmet(daytimeUndead),
                                "authored daylight protection used an equipment workaround");
                        check(daytimeUndead.hasAI(),
                                "day-capable authored undead lost vanilla combat AI");
                        check(!runtime.activeAbilityIds(daytimeUndead).isEmpty(),
                                "day-capable authored undead lost its canonical technique kit");
                        check(!scaling.hasAuthoredDaylightProtection(nightUndead),
                                "night-only variant gained authored daylight protection");
                        check(controls.stream().allMatch(control -> scaling.getLevel(control) > 0),
                                "Cow/Zombie/Skeleton controls lack canonical stable levels");
                        check(species.profile(EntityType.COW).disposition()
                                        == CreatureSpeciesPolicy.Disposition.PASSIVE
                                        && species.profile(EntityType.ZOMBIE).disposition()
                                        == CreatureSpeciesPolicy.Disposition.HOSTILE
                                        && species.profile(EntityType.SKELETON).disposition()
                                        == CreatureSpeciesPolicy.Disposition.HOSTILE
                                        && controls.getFirst().getTarget() == null,
                                "#138 passive/hostile control policy regressed");
                        writeAuthoredPveRuntimeReport(spawned, controls, telemetry, spawns, scaling,
                                daytimeUndead, nightUndead);
                        plugin.getLogger().info(PASS_MARKER);
                    } catch (final Throwable failure) {
                        plugin.getLogger().severe("ICESMP_SOURCE_INTEGRITY_RUNTIME_PROBE_FAIL: " + failure);
                        failure.printStackTrace();
                    } finally {
                        spawned.forEach(mob -> mob.getScheduler().run(plugin,
                                remove -> { if (mob.isValid()) mob.remove(); }, null));
                        controls.forEach(mob -> mob.getScheduler().run(plugin,
                                remove -> { if (mob.isValid()) mob.remove(); }, null));
                        world.setChunkForceLoaded(probeChunkX, probeChunkZ, false);
                        Bukkit.shutdown();
                    }
                }, 120L);
                plugin.getLogger().info("ICESMP_AUTHORED_PVE_RUNTIME_PROBE_VERIFY_SCHEDULED");
            } catch (final Throwable failure) {
                spawned.forEach(mob -> mob.getScheduler().run(plugin,
                        remove -> { if (mob.isValid()) mob.remove(); }, null));
                controls.forEach(mob -> mob.getScheduler().run(plugin,
                        remove -> { if (mob.isValid()) mob.remove(); }, null));
                world.setChunkForceLoaded(probeChunkX, probeChunkZ, false);
                plugin.getLogger().severe("ICESMP_SOURCE_INTEGRITY_RUNTIME_PROBE_FAIL: " + failure);
                failure.printStackTrace();
                Bukkit.shutdown();
            }
            }, 1L);
        });
    }

    private static void writeAuthoredPveRuntimeReport(final List<Mob> mobs,
                                                      final List<Mob> controls,
                                                      final Map<String, Long> telemetry,
                                                      final AuthoredCreatureSpawnService spawns,
                                                      final MobScalingManager scaling,
                                                      final Mob daytimeUndead,
                                                      final Mob nightUndead)
            throws java.io.IOException {
        final Path output = Path.of(System.getProperty("icesmp.combat-evidence-dir",
                "../build/reports/combat-foundation")).toAbsolutePath().normalize()
                .resolve("authored-pve-runtime-report.json");
        Files.createDirectories(output.getParent());
        final String templates = mobs.stream().map(
                hu.taliann.icesmp.managers.MobScalingManager::templateIdOf)
                .map(value -> "\"" + value + "\"")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
        Files.writeString(output, "{\n  \"schema\": 1,\n"
                + "  \"runtime\": \"Paper 1.21.11\",\n"
                + "  \"templates\": " + templates + ",\n"
                + "  \"controls\": [\"COW:PASSIVE\",\"ZOMBIE:HOSTILE\",\"SKELETON:HOSTILE\"],\n"
                + "  \"control_levels\": [" + controls.stream().map(scaling::getLevel)
                .map(String::valueOf).collect(java.util.stream.Collectors.joining(",")) + "],\n"
                + "  \"passive_control_initial_target\": false,\n"
                + "  \"common_runtime\": \"MobAbilityRuntime\",\n"
                + "  \"daylight_undead\": {\"open_sky_noon\":true,\"no_helmet\":"
                + hasNoHelmet(daytimeUndead)
                + ",\"no_fire\":" + (daytimeUndead.getFireTicks() <= 0)
                + ",\"combat_ready\":" + (daytimeUndead.hasAI()
                && !scaling.hasAuthoredDaylightProtection(nightUndead)) + "},\n"
                + "  \"night_only_authored_protection\": "
                + scaling.hasAuthoredDaylightProtection(nightUndead) + ",\n"
                + "  \"signature_executions\": "
                + telemetry.getOrDefault("technique_execute:ring_lock", 0L) + ",\n"
                + "  \"threshold_transitions\": "
                + telemetry.getOrDefault("boss_phase_transition:summon_frozen_adds", 0L) + ",\n"
                + "  \"world_boss_stat_provenance\": \""
                + String.valueOf(spawns.statProvenance(mobs.getFirst())) + "\",\n"
                + "  \"duplicate_participant_modifier_rejected\": true,\n"
                + "  \"pause_resume_exercised\": true,\n"
                + "  \"status\": \"PAPER_RUNTIME_PROVED\"\n}\n");
    }

    /** Paper represents an empty equipment slot as either null or an AIR ItemStack. */
    private static boolean hasNoHelmet(final Mob mob) {
        if (mob == null || mob.getEquipment() == null) return false;
        final org.bukkit.inventory.ItemStack helmet = mob.getEquipment().getHelmet();
        return helmet == null || helmet.getType().isAir();
    }

    /**
     * Real Paper 1.21.11 denominator for every authored balance report. Values come from fresh
     * runtime ItemStacks and their backing ATTRIBUTE_MODIFIERS component, never from a wiki table.
     */
    private static void writeVanillaRuntimeBenchmark() throws java.io.IOException {
        final List<Material> materials = List.of(
                Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE,
                Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS,
                Material.GOLDEN_HELMET, Material.GOLDEN_CHESTPLATE,
                Material.GOLDEN_LEGGINGS, Material.GOLDEN_BOOTS,
                Material.CHAINMAIL_HELMET, Material.CHAINMAIL_CHESTPLATE,
                Material.CHAINMAIL_LEGGINGS, Material.CHAINMAIL_BOOTS,
                Material.IRON_HELMET, Material.IRON_CHESTPLATE,
                Material.IRON_LEGGINGS, Material.IRON_BOOTS,
                Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE,
                Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS,
                Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE,
                Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS,
                Material.WOODEN_SWORD, Material.STONE_SWORD, Material.IRON_SWORD,
                Material.DIAMOND_SWORD, Material.NETHERITE_SWORD,
                Material.WOODEN_AXE, Material.STONE_AXE, Material.IRON_AXE,
                Material.DIAMOND_AXE, Material.NETHERITE_AXE,
                Material.BOW, Material.CROSSBOW, Material.TRIDENT, Material.SHIELD);
        final StringBuilder json = new StringBuilder(8_192)
                .append("{\n  \"schema\": 1,\n")
                .append("  \"runtime\": \"Paper 1.21.11\",\n")
                .append("  \"measurement\": \"fresh ItemStack backing ATTRIBUTE_MODIFIERS\",\n")
                .append("  \"player_base_attack_damage\": 1.0,\n")
                .append("  \"player_base_attack_speed\": 4.0,\n")
                .append("  \"items\": [\n");
        for (int index = 0; index < materials.size(); index++) {
            final Material material = materials.get(index);
            final ItemStack item = new ItemStack(material);
            final ItemAttributeModifiers data = item.getData(DataComponentTypes.ATTRIBUTE_MODIFIERS);
            if (index > 0) json.append(",\n");
            json.append("    {\"material\":\"").append(material.name()).append("\"")
                    .append(",\"has_attribute_component\":").append(data != null)
                    .append(",\"armor\":").append(number(sumAdditive(data, Attribute.ARMOR)))
                    .append(",\"armor_toughness\":").append(number(sumAdditive(data, Attribute.ARMOR_TOUGHNESS)))
                    .append(",\"knockback_resistance\":").append(number(sumAdditive(data, Attribute.KNOCKBACK_RESISTANCE)))
                    .append(",\"attack_damage_modifier\":").append(number(sumAdditive(data, Attribute.ATTACK_DAMAGE)))
                    .append(",\"attack_speed_modifier\":").append(number(sumAdditive(data, Attribute.ATTACK_SPEED)))
                    .append(",\"attack_knockback\":").append(number(sumAdditive(data, Attribute.ATTACK_KNOCKBACK)))
                    .append('}');
        }
        json.append("\n  ]\n}\n");
        final Path output = Path.of(System.getProperty("icesmp.combat-evidence-dir",
                "../build/reports/combat-foundation")).toAbsolutePath().normalize()
                .resolve("vanilla-runtime-benchmark.json");
        Files.createDirectories(output.getParent());
        Files.writeString(output, json.toString());
        check(Files.size(output) > 512L, "vanilla runtime benchmark evidence is unexpectedly empty");
    }

    private static String number(final double value) {
        return String.format(java.util.Locale.ROOT, "%.6f", value);
    }

    /** Real Paper EntityType inventory joined to the startup-published creature authority. */
    private static void verifyCreatureRuntime(final CreatureSpeciesRegistry registry,
                                              final MobAbilityRegistry abilities)
            throws java.io.IOException {
        final var expected = CreatureSpeciesRegistry.supportedLivingTypes();
        check(!expected.isEmpty() && expected.equals(registry.all().keySet()),
                "Paper living EntityType inventory and creature species matrix must match exactly");
        final CreatureSpeciesPolicy cow = registry.profile(EntityType.COW);
        final CreatureSpeciesPolicy rabbit = registry.profile(EntityType.RABBIT);
        final CreatureSpeciesPolicy goat = registry.profile(EntityType.GOAT);
        final CreatureSpeciesPolicy bee = registry.profile(EntityType.BEE);
        final CreatureSpeciesPolicy wolf = registry.profile(EntityType.WOLF);
        final CreatureSpeciesPolicy zombie = registry.profile(EntityType.ZOMBIE);
        final CreatureSpeciesPolicy skeleton = registry.profile(EntityType.SKELETON);
        check(cow.disposition() == CreatureSpeciesPolicy.Disposition.PASSIVE
                        && cow.levelEnabled() && cow.rankEnabled()
                        && cow.rewardProfile() == CreatureSpeciesPolicy.RewardProfile.VANILLA_ONLY
                        && cow.techniquesFor(MobRank.ELITE).containsAll(
                        List.of("headbutt", "short_charge", "defensive_stomp"))
                        && cow.socialPolicy().maximumAssistants() == 2,
                "Cow runtime profile lost passive level/rank/technique/social/reward projection");
        check(rabbit.disposition() == CreatureSpeciesPolicy.Disposition.PASSIVE
                        && rabbit.temperamentPolicy().fightPercent().values().stream()
                        .allMatch(percent -> percent == 0.0D),
                "Rabbit runtime profile must remain deterministic flee-first wildlife");
        check(goat.disposition() == CreatureSpeciesPolicy.Disposition.NEUTRAL
                        && goat.socialPolicy().relation() == CreatureSpeciesPolicy.SocialRelation.VANILLA,
                "Goat runtime profile must preserve vanilla territorial/ram authority");
        check(bee.disposition() == CreatureSpeciesPolicy.Disposition.NEUTRAL
                        && bee.socialPolicy().relation() == CreatureSpeciesPolicy.SocialRelation.VANILLA,
                "Bee runtime profile must preserve vanilla swarm authority");
        check(wolf.disposition() == CreatureSpeciesPolicy.Disposition.NEUTRAL
                        && wolf.tamePolicy() == CreatureSpeciesPolicy.TamePolicy.OWNER_SAFE,
                "Wolf runtime profile must preserve pack/tame owner safety");
        check(zombie.disposition() == CreatureSpeciesPolicy.Disposition.HOSTILE
                        && skeleton.disposition() == CreatureSpeciesPolicy.Disposition.HOSTILE,
                "Zombie/Skeleton hostile controls lost common creature projection");
        check(abilities.require("headbutt").kind() == MobAbilityDefinition.Kind.COMPOSITE
                        && abilities.require("panic_dash").triggers()
                        .contains(MobAbilityDefinition.Trigger.ON_PROVOKED),
                "composable physical technique runtime was not published");

        final StringBuilder json = new StringBuilder(24_000)
                .append("{\n  \"schema\": 1,\n")
                .append("  \"runtime\": \"Paper 1.21.11\",\n")
                .append("  \"entity_type_authority\": \"EntityType.values/isAlive/isSpawnable\",\n")
                .append("  \"supported_species_count\": ").append(expected.size()).append(",\n")
                .append("  \"inventory_exact\": true,\n")
                .append("  \"representative_profiles\": [\n");
        final List<EntityType> representatives = List.of(EntityType.COW, EntityType.RABBIT,
                EntityType.GOAT, EntityType.BEE, EntityType.WOLF,
                EntityType.ZOMBIE, EntityType.SKELETON);
        for (int index = 0; index < representatives.size(); index++) {
            final EntityType type = representatives.get(index);
            final CreatureSpeciesPolicy policy = registry.profile(type);
            if (index > 0) json.append(",\n");
            json.append("    {\"entity_type\":\"").append(type.name())
                    .append("\",\"disposition\":\"").append(policy.disposition())
                    .append("\",\"level_enabled\":").append(policy.levelEnabled())
                    .append(",\"rank_enabled\":").append(policy.rankEnabled())
                    .append(",\"reward_profile\":\"").append(policy.rewardProfile())
                    .append("\",\"normal_techniques\":")
                    .append(stringList(policy.techniquesFor(MobRank.NORMAL)))
                    .append(",\"elite_techniques\":")
                    .append(stringList(policy.techniquesFor(MobRank.ELITE)))
                    .append('}');
        }
        json.append("\n  ],\n  \"status\": \"PAPER_RUNTIME_PROVED\"\n}\n");
        final Path output = Path.of(System.getProperty("icesmp.combat-evidence-dir",
                "../build/reports/combat-foundation")).toAbsolutePath().normalize()
                .resolve("creature-runtime-report.json");
        Files.createDirectories(output.getParent());
        Files.writeString(output, json.toString());
        check(Files.size(output) > 512L, "creature runtime evidence is unexpectedly empty");
    }

    private static String stringList(final List<String> values) {
        return values.stream().map(value -> "\"" + value + "\"")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }

    private static <T> T readField(final Object target, final String name, final Class<T> type) {
        try {
            final Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return type.cast(field.get(target));
        } catch (final ReflectiveOperationException failure) {
            throw new IllegalStateException("runtime probe cannot read assembled core field: " + name, failure);
        }
    }

    /**
     * Paper exposes the backing Material modifier value from getData when the valued component is
     * absent. The canonical writer must therefore install an explicit empty component for a zero
     * projection instead of relying on an empty ItemMeta modifier map.
     */
    private static void verifyAttributeComponentSemantics() {
        final ItemStack vanilla = new ItemStack(Material.IRON_SWORD);
        final var defaults = vanilla.getData(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        check(defaults != null && !defaults.modifiers().isEmpty(),
                "fresh vanilla sword must expose backing Material combat modifiers");

        ItemDataFactory.applyCanonicalAttributeModifiers(vanilla, List.of(), false);
        check(vanilla.hasData(DataComponentTypes.ATTRIBUTE_MODIFIERS),
                "zero-stat canonical projection must explicitly own ATTRIBUTE_MODIFIERS");
        final var canonical = vanilla.getData(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        check(canonical != null && canonical.modifiers().isEmpty(),
                "explicit empty canonical projection must suppress backing Material defaults");
    }

    /** Final P1-009 matrix on real Paper ItemStacks. */
    private static void verifyEquipmentRuntimeStates(final ItemIdentityService identity) {
        final ItemStack canonical = identity.create("glatziendorfi_jegvert",
                "runtime:equipment", "paper", null);
        final ItemIdentityService.Inspection inspection = identity.inspect(canonical);
        check(inspection.status() == ItemIdentityService.Status.VALID,
                "equipment probe canonical item must start VALID");
        check(canonical.hasData(DataComponentTypes.ATTRIBUTE_MODIFIERS),
                "canonical render must explicitly own ATTRIBUTE_MODIFIERS");
        final var activeData = canonical.getData(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        check(activeData != null && !activeData.modifiers().isEmpty(),
                "authored armor probe must expose canonical modifiers before suppression");
        final var activeModifiers = List.copyOf(activeData.modifiers());

        check(activeModifiers.stream().allMatch(entry ->
                        "icesmp".equals(entry.modifier().getKey().getNamespace())),
                "canonical component must contain only IceSMP-authored modifier keys");
        final double expectedArmor = projectedStat(inspection, "armor") + inspection.template().baseArmor();
        final double expectedToughness = projectedStat(inspection, "armor_toughness");
        check(close(sumAdditive(activeData, Attribute.ARMOR), expectedArmor),
                "canonical armor must equal authored projection exactly, without backing Material armor");
        check(close(sumAdditive(activeData, Attribute.ARMOR_TOUGHNESS), expectedToughness),
                "canonical toughness must equal authored projection exactly, without backing Material toughness");

        final ItemStack managedZeroProjection = identity.create("glatziendorfi_jegvert",
                "runtime:zero-projection", "paper", null);
        check(identity.inspect(managedZeroProjection).status() == ItemIdentityService.Status.VALID,
                "managed zero-projection control must start with valid canonical identity");
        ItemDataFactory.applyCanonicalAttributeModifiers(managedZeroProjection, List.of(), false);
        check(identity.inspect(managedZeroProjection).status() == ItemIdentityService.Status.VALID,
                "zeroing the gameplay projection must not rewrite canonical identity");
        final var zeroData = managedZeroProjection.getData(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        check(managedZeroProjection.hasData(DataComponentTypes.ATTRIBUTE_MODIFIERS)
                        && zeroData != null && zeroData.modifiers().isEmpty(),
                "managed canonical zero projection must not inherit NETHERITE_CHESTPLATE defaults");

        check(EquipmentProficiencyService.decideActivity(ItemIdentityService.Status.VALID,
                        true, false, true, false, true, false)
                        == EquipmentProficiencyService.ActivityStatus.RESTRICTED,
                "wrong-family/class restriction must be runtime-inert");
        identity.setEquipmentSuppressed(canonical, inspection.template(), inspection.instance(), true);
        check(identity.isEquipmentSuppressed(canonical),
                "restricted canonical item must carry runtime suppression state");
        final var restricted = canonical.getData(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        check(canonical.hasData(DataComponentTypes.ATTRIBUTE_MODIFIERS)
                        && restricted != null && restricted.modifiers().isEmpty(),
                "wrong-family/restricted canonical item must have zero effective modifiers");
        check(EquipmentProficiencyService.decideActivity(ItemIdentityService.Status.VALID,
                        true, false, true, true, true, true)
                        == EquipmentProficiencyService.ActivityStatus.SUPPRESSED,
                "suppression marker must remain authoritative until reconciliation reactivates");
        check(EquipmentProficiencyService.decideActivity(ItemIdentityService.Status.VALID,
                        true, false, true, true, false, false)
                        == EquipmentProficiencyService.ActivityStatus.UNDER_LEVEL,
                "underlevel canonical equipment must be runtime-inert");
        check(!new EquipmentProficiencyService.LevelDecision(19, 20).allowed()
                        && new EquipmentProficiencyService.LevelDecision(20, 20).allowed(),
                "level boundary must deny requirement-1 and allow exact requirement");

        identity.setEquipmentSuppressed(canonical, inspection.template(), inspection.instance(), false);
        check(!identity.isEquipmentSuppressed(canonical),
                "valid reconciliation must remove the transient suppression marker");
        final var reactivated = canonical.getData(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        check(reactivated != null && activeModifiers.equals(List.copyOf(reactivated.modifiers())),
                "reactivation must restore exactly the canonical modifier projection");
        check(identity.inspect(canonical).status() == ItemIdentityService.Status.VALID,
                "suppression/reactivation must not rewrite canonical identity/checksum state");

        final ItemStack invalid = canonical.clone();
        invalid.setAmount(2);
        check(identity.inspect(invalid).status() == ItemIdentityService.Status.TEMPLATE_MISMATCH,
                "managed-invalid runtime fixture must be rejected by canonical identity");
        check(EquipmentProficiencyService.decideActivity(ItemIdentityService.Status.TEMPLATE_MISMATCH,
                        true, false, true, true, true, false)
                        == EquipmentProficiencyService.ActivityStatus.INVALID_IDENTITY,
                "managed-invalid canonical item must fail closed before proficiency contribution");
        identity.suppressManagedInvalid(invalid);
        final var invalidData = invalid.getData(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        check(identity.isEquipmentSuppressed(invalid)
                        && invalid.hasData(DataComponentTypes.ATTRIBUTE_MODIFIERS)
                        && invalidData != null && invalidData.modifiers().isEmpty(),
                "managed-invalid canonical item must be physically attribute-inert");

        final ItemStack basic = new ItemStack(Material.IRON_SWORD);
        check(EquipmentProficiencyService.decideActivity(ItemIdentityService.Status.NOT_MANAGED,
                        true, false, false, false, false, false)
                        == EquipmentProficiencyService.ActivityStatus.NOT_MANAGED,
                "BASIC/not-managed item must stay outside the MMO activity gate");
        final var basicDefaults = basic.getData(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        check(basicDefaults != null && !basicDefaults.modifiers().isEmpty(),
                "BASIC vanilla control must retain backing Material attribute behavior");
    }

    private static double projectedStat(final ItemIdentityService.Inspection inspection,
                                        final String statId) {
        double value = inspection.template().fixedStatsAt(inspection.instance().ascension().stageId())
                .getOrDefault(statId, 0.0D);
        final ItemInstance.Roll roll = inspection.instance().rolls().get(statId);
        if (roll != null) value += roll.value();
        return value;
    }

    private static void verifyRp2ProductionPresentation(final ItemIdentityService identity) {
        final Properties production = new Properties();
        try (var input = PaperSourceIntegrityRuntimeProbe.class.getClassLoader()
                .getResourceAsStream("equipment-rp2-production.properties")) {
            check(input != null, "RP2 production runtime index must be packaged");
            production.load(input);
        } catch (final java.io.IOException failure) {
            throw new IllegalStateException("RP2 production runtime index cannot be read", failure);
        }
        final int count = Integer.parseInt(production.getProperty("binding.count", "-1"));
        check(count == 160, "RP2 production runtime index must expose exactly 160 pieces");
        for (int index = 0; index < count; index++) {
            final String model = production.getProperty("binding." + index + ".item-model");
            final String equipment = production.getProperty("binding." + index + ".equipment-asset");
            final String templateId = model.substring("icesmp:".length());
            final ItemStack item = identity.create(templateId, "runtime:rp2-production", "paper", null);
            check(identity.inspect(item).status() == ItemIdentityService.Status.VALID,
                    "RP2 production canonical identity must remain VALID: " + templateId);
            check(model.equals(String.valueOf(item.getData(DataComponentTypes.ITEM_MODEL))),
                    "RP2 production inventory model mismatch: " + templateId);
            final var equippable = item.getData(DataComponentTypes.EQUIPPABLE);
            check(equippable != null && equipment.equals(String.valueOf(equippable.assetId())),
                    "RP2 production worn asset mismatch: " + templateId);
            final var attributes = item.getData(DataComponentTypes.ATTRIBUTE_MODIFIERS);
            check(attributes != null && !attributes.modifiers().isEmpty(),
                    "RP2 presentation must not change canonical attributes: " + templateId);
        }

        final ItemStack legacy = identity.create("csillagfatyol_mellvert",
                "runtime:rp2-legacy", "paper", null);
        final var before = identity.inspect(legacy);
        final var current = legacy.getData(DataComponentTypes.EQUIPPABLE);
        final var vanilla = new ItemStack(legacy.getType()).getData(DataComponentTypes.EQUIPPABLE);
        check(before.status() == ItemIdentityService.Status.VALID && current != null && vanilla != null,
                "RP2 legacy refresh fixture must start as valid equippable canonical armor");
        legacy.setData(DataComponentTypes.EQUIPPABLE,
                current.toBuilder().assetId(vanilla.assetId()).build());
        identity.refreshPresentation(legacy, before.template(), before.instance());
        final var after = identity.inspect(legacy);
        check(after.status() == ItemIdentityService.Status.VALID
                        && before.instance().equals(after.instance()),
                "RP2 visual refresh must preserve UUID, rolls, runes, reroll, ascension and provenance");
        check("icesmp:rp2/csillagfatyol".equals(String.valueOf(
                        legacy.getData(DataComponentTypes.EQUIPPABLE).assetId())),
                "RP2 legacy refresh must converge on the final custom worn binding");
    }

    private static double sumAdditive(final ItemAttributeModifiers data, final Attribute attribute) {
        if (data == null) return 0.0D;
        return data.modifiers().stream()
                .filter(entry -> attribute.equals(entry.attribute()))
                .map(ItemAttributeModifiers.Entry::modifier)
                .filter(modifier -> modifier.getOperation() == AttributeModifier.Operation.ADD_NUMBER)
                .mapToDouble(AttributeModifier::getAmount)
                .sum();
    }

    private static boolean close(final double actual, final double expected) {
        return Math.abs(actual - expected) <= 0.000_001D;
    }

    private static void verifyActualInventoryAtomicity() {
        final ItemStack rune = new ItemStack(Material.AMETHYST_SHARD, 64);
        final Inventory trigger = fullStorageWithPartialRune();
        final Map<Integer, ItemStack> triggerLeftovers = trigger.addItem(rune.clone());
        check(trigger.getItem(0) != null && trigger.getItem(0).getAmount() == 64,
                "Paper Inventory.addItem must reproduce the partial merge trigger");
        check(triggerLeftovers.values().stream().mapToInt(ItemStack::getAmount).sum() == 63,
                "Paper Inventory.addItem trigger must leave 63 after merging one item");

        final Inventory protectedInventory = fullStorageWithPartialRune();
        final AtomicReference<ItemStack> cursor = new AtomicReference<>(rune.clone());
        final ItemStack[] before = cloneContents(protectedInventory.getStorageContents());
        final boolean applied = AtomicCursorRehome.rehome(adapter(protectedInventory, cursor,
                new AtomicInteger(), false), cursor.get());
        check(!applied, "atomic rehome must reject partial-stack plus otherwise-full inventory");
        check(Arrays.deepEquals(serialize(before), serialize(protectedInventory.getStorageContents())),
                "failed atomic rehome must preserve exact storage state");
        check(cursor.get() != null && cursor.get().getAmount() == 64,
                "failed atomic rehome must preserve exact cursor state");

        final Inventory enough = Bukkit.createInventory(null, 36);
        for (int slot = 0; slot < 35; slot++) enough.setItem(slot, new ItemStack(Material.COBBLESTONE, 64));
        final AtomicReference<ItemStack> enoughCursor = new AtomicReference<>(rune.clone());
        final AtomicInteger successPersists = new AtomicInteger();
        check(AtomicCursorRehome.rehome(adapter(enough, enoughCursor, successPersists, false),
                        enoughCursor.get()),
                "exactly sufficient inventory must accept full cursor rehome");
        check(enoughCursor.get() == null && successPersists.get() == 1,
                "successful cursor rehome must clear cursor after one durable save");

        final Inventory rollback = Bukkit.createInventory(null, 36);
        for (int slot = 0; slot < 35; slot++) rollback.setItem(slot, new ItemStack(Material.COBBLESTONE, 64));
        final ItemStack[] rollbackBefore = cloneContents(rollback.getStorageContents());
        final AtomicReference<ItemStack> rollbackCursor = new AtomicReference<>(rune.clone());
        final AtomicInteger rollbackPersists = new AtomicInteger();
        check(!AtomicCursorRehome.rehome(adapter(rollback, rollbackCursor, rollbackPersists, true),
                        rollbackCursor.get()),
                "persistence exception must fail cursor rehome");
        check(Arrays.deepEquals(serialize(rollbackBefore), serialize(rollback.getStorageContents())),
                "persistence exception must restore exact storage snapshot");
        check(rollbackCursor.get() != null && rollbackCursor.get().getAmount() == 64,
                "persistence exception must restore cursor snapshot");
        check(rollbackPersists.get() == 2,
                "persistence exception must attempt a second durable rollback save");

        final Inventory full = Bukkit.createInventory(null, 36);
        for (int slot = 0; slot < 36; slot++) full.setItem(slot, new ItemStack(Material.COBBLESTONE, 64));
        final AtomicReference<ItemStack> fullEquipped = new AtomicReference<>(
                new ItemStack(Material.NETHERITE_CHESTPLATE));
        final byte[] fullEquippedBefore = fullEquipped.get().serializeAsBytes();
        final ItemStack[] fullBefore = cloneContents(full.getStorageContents());
        check(!EquipmentRehomeTransaction.rehome(equipmentAdapter(full, fullEquipped)),
                "full inventory must reject equipment rehome without dropping the denied item");
        check(Arrays.equals(fullEquippedBefore, fullEquipped.get().serializeAsBytes())
                        && Arrays.deepEquals(serialize(fullBefore), serialize(full.getStorageContents())),
                "failed full-inventory equipment rehome must conserve exact equipped/storage state");

        final Inventory available = Bukkit.createInventory(null, 36);
        for (int slot = 0; slot < 35; slot++) {
            available.setItem(slot, new ItemStack(Material.COBBLESTONE, 64));
        }
        final AtomicReference<ItemStack> movable = new AtomicReference<>(
                new ItemStack(Material.NETHERITE_CHESTPLATE));
        check(EquipmentRehomeTransaction.rehome(equipmentAdapter(available, movable))
                        && movable.get() == null,
                "one free storage slot must rehome denied equipment exactly once");
    }

    private static EquipmentRehomeTransaction.Adapter equipmentAdapter(
            final Inventory inventory, final AtomicReference<ItemStack> equipped) {
        return new RuntimeEquipmentAdapter(inventory, equipped);
    }

    private static AtomicCursorRehome.Adapter adapter(final Inventory inventory,
                                                       final AtomicReference<ItemStack> cursor,
                                                       final AtomicInteger persists,
                                                       final boolean failFirstPersist) {
        return new AtomicCursorRehome.Adapter() {
            @Override public ItemStack[] storageContents() { return inventory.getStorageContents(); }
            @Override public Map<Integer, ItemStack> add(final ItemStack stack) { return inventory.addItem(stack); }
            @Override public void restoreStorage(final ItemStack[] snapshot) { inventory.setStorageContents(snapshot); }
            @Override public ItemStack cursor() { return cursor.get(); }
            @Override public void setCursor(final ItemStack stack) { cursor.set(stack); }
            @Override public void persist() {
                final int call = persists.incrementAndGet();
                if (failFirstPersist && call == 1) throw new IllegalStateException("simulated saveData failure");
            }
        };
    }

    private static Inventory fullStorageWithPartialRune() {
        final Inventory inventory = Bukkit.createInventory(null, 36);
        inventory.setItem(0, new ItemStack(Material.AMETHYST_SHARD, 63));
        for (int slot = 1; slot < 36; slot++) inventory.setItem(slot, new ItemStack(Material.COBBLESTONE, 64));
        return inventory;
    }

    private static void verifyMutationPhysicalState(final ItemIdentityService identity) {
        final ItemStack previous = identity.create("glatziendorfi_jegvert",
                "runtime:probe", "paper", null);
        final ItemIdentityService.Inspection inspection = identity.inspect(previous);
        check(inspection.status() == ItemIdentityService.Status.VALID,
                "runtime probe canonical item must start VALID");
        final var oldMeta = previous.getItemMeta();
        check(oldMeta instanceof Damageable,
                "runtime probe template must use a damageable backing material");
        final Damageable damageable = (Damageable) oldMeta;
        damageable.setDamage(Math.min(17, Math.max(1, previous.getType().getMaxDurability() - 1)));
        final int expectedDamage = damageable.getDamage();
        oldMeta.setUnbreakable(true);
        oldMeta.lore(List.of(net.kyori.adventure.text.Component.text("STALE_RUNTIME_PROBE_LORE")));
        previous.setItemMeta(oldMeta);

        final ItemStack fresh = identity.render(inspection.template(), inspection.instance());
        final ItemStack preserved = CanonicalPhysicalState.preserve(previous, fresh);
        final var preservedMeta = preserved.getItemMeta();
        check(preservedMeta instanceof Damageable
                        && ((Damageable) preservedMeta).getDamage() == expectedDamage,
                "mutation render must preserve exact physical durability damage");
        check(!preservedMeta.isUnbreakable(),
                "mutation render must not launder non-authoritative unbreakable metadata");
        check(preservedMeta.lore() == null || preservedMeta.lore().stream()
                        .noneMatch(line -> line.toString().contains("STALE_RUNTIME_PROBE_LORE")),
                "mutation render must rebuild authored lore instead of restoring stale meta");
        check(identity.inspect(preserved).status() == ItemIdentityService.Status.VALID,
                "mutation physical-state preservation must leave canonical checksum VALID");
    }

    private static void verifyCatalogPositiveLoad(final ItemIdentityService identity,
                                                  final ProfessionRecipeCatalog catalog,
                                                  final ItemTemplateRegistry templates) {
        final int count = catalog.allIds().size();
        final boolean longTerm = catalog.get("lte_fonixszovet_sisak") != null;
        final boolean professions2 = catalog.get("p2_fonixpihe_kopeny") != null;
        // The long-term 64-crafted target is total armor ownership. Six preserved crafted anchors
        // already belong to the previous 18 canonical recipes, so the cumulative authority is 76.
        final int expectedCanonical = longTerm ? 76 : professions2 ? 18 : 15;
        if (longTerm) {
            check(count >= 471, "long-term production catalog unexpectedly small: " + count);
        } else {
            final int expected = professions2 ? 407 : 392;
            check(count == expected, "production catalog effective recipe count mismatch: " + count);
        }
        int canonical = 0;
        for (final String id : catalog.allIds()) {
            final ProfessionRecipeCatalog.Recipe recipe = catalog.get(id);
            check(recipe != null && recipe.result() != null && !recipe.result().isAir(),
                    "every production recipe must resolve a non-AIR backing material: " + id);
            if (recipe.templateId() != null) canonical++;
        }
        check(canonical == expectedCanonical,
                "production catalog canonical recipe count mismatch: " + canonical);
        if (professions2) {
            for (final String id : List.of("p2_fonixpihe_kopeny", "p2_vadorzo_csizma", "p2_csontenyv_pancel")) {
                final ProfessionRecipeCatalog.Recipe recipe = catalog.get(id);
                check(recipe != null && recipe.templateId() != null && !recipe.result().isAir(),
                        "Professions 2.0 canonical recipe failed production load: " + id);
            }
        }
        if (longTerm) {
            final List<ItemTemplate> armor = templates.snapshot().values().stream()
                    .filter(ItemTemplate::isArmorFamilyEquipment).toList();
            check(armor.size() == 160,
                    "long-term production ItemTemplate parser must load exactly 160 armor: " + armor.size());
            for (final ArmorFamily family : ArmorFamily.values()) {
                check(armor.stream().filter(template -> template.armorFamily() == family).count() == 40L,
                        "long-term family must load exactly 40 armor: " + family);
                for (final ItemTemplate.Slot slot : List.of(ItemTemplate.Slot.HEAD, ItemTemplate.Slot.CHEST,
                        ItemTemplate.Slot.LEGS, ItemTemplate.Slot.FEET)) {
                    check(armor.stream().filter(template -> template.armorFamily() == family
                                    && template.slot() == slot).count() == 10L,
                            "long-term family/slot must load exactly 10 armor: " + family + '/' + slot);
                }
            }
            final java.util.Set<String> bands = armor.stream()
                    .map(template -> template.encounterMetadata().get("progression-band"))
                    .filter(java.util.Objects::nonNull)
                    .collect(java.util.stream.Collectors.toSet());
            check(bands.containsAll(java.util.Set.of("early", "mid", "high", "endgame")),
                    "Paper runtime must load armor samples for all four canonical bands");
            final List<ItemTemplate> combatItems = templates.snapshot().values().stream()
                    .filter(template -> template.family() == ItemTemplate.Family.WEAPON
                            || (template.slot() == ItemTemplate.Slot.OFF_HAND
                            && "SHIELD".equals(template.material())))
                    .toList();
            check(combatItems.size() == 25,
                    "Paper runtime must load exactly 25 existing weapon/off-hand templates");
            for (final ItemTemplate template : combatItems) {
                check(template.levelRequirement() == Math.max(1, template.itemLevel() - 4),
                        "combat item level requirement mismatch: " + template.templateId());
                final ItemStack item = identity.create(template.templateId(),
                        "runtime:combat-item", "paper", null);
                final ItemIdentityService.Inspection inspection = identity.inspect(item);
                final ItemAttributeModifiers attributes = item.getData(
                        DataComponentTypes.ATTRIBUTE_MODIFIERS);
                check(inspection.status() == ItemIdentityService.Status.VALID
                                && item.hasData(DataComponentTypes.ATTRIBUTE_MODIFIERS)
                                && attributes != null
                                && attributes.modifiers().stream().allMatch(entry ->
                                "icesmp".equals(entry.modifier().getKey().getNamespace())),
                        "combat item must be VALID and own its Paper attribute projection: "
                                + template.templateId());
            }
            final ProfessionRecipeCatalog.Recipe newOutput = catalog.get("lte_fonixszovet_sisak");
            check(newOutput != null && "fonixszovet_sisak".equals(newOutput.templateId()),
                    "new long-term canonical output must survive the production recipe parser");
        }
    }

    private static ItemStack[] cloneContents(final ItemStack[] source) {
        final ItemStack[] copy = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) copy[i] = source[i] == null ? null : source[i].clone();
        return copy;
    }

    private static byte[][] serialize(final ItemStack[] source) {
        final byte[][] encoded = new byte[source.length][];
        for (int i = 0; i < source.length; i++) {
            encoded[i] = source[i] == null ? new byte[0] : source[i].serializeAsBytes();
        }
        return encoded;
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }
}
