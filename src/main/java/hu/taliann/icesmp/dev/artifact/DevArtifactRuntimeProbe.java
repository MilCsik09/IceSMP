package hu.taliann.icesmp.dev.artifact;

import hu.taliann.icesmp.items.DevItemFactory;
import hu.taliann.icesmp.managers.DevItemManager;
import hu.taliann.icesmp.security.HiddenDevAuthority;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** Explicit CI-only probe: no player authority, issuance or gameplay mutation is granted by this flag. */
public final class DevArtifactRuntimeProbe {
    private static final String PROPERTY = "icesmp.dev-artifact-runtime";
    private static final AtomicBoolean STARTED = new AtomicBoolean();
    private static final AtomicBoolean PASSED = new AtomicBoolean();
    private DevArtifactRuntimeProbe() {}

    public static void maybeRun(final JavaPlugin plugin, final DevItemManager manager) {
        if (!Boolean.getBoolean(PROPERTY) || !STARTED.compareAndSet(false, true)) return;
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
            try {
                final DevItemManager.Diagnostics diagnostics = manager.diagnostics();
                check(diagnostics.ready() && diagnostics.healthy() && diagnostics.scheduled(), "lifecycle readiness");
                check(diagnostics.registeredArtifacts() == 2 && diagnostics.persistedArtifacts() >= 2, "shared registration");
                final DevArtifactState authority = manager.state(WorldWeaverArtifactBehavior.ID);
                check(authority.owner().equals(HiddenDevAuthority.PRIMARY_DEVELOPER), "fixed developer authority");
                check(!WorldWeaverArtifactBehavior.definition(() -> false).policySource().current().enabled(), "disabled definition");
                final DevItemFactory factory = manager.itemFactory();
                for (final DevArtifactPresentation.ModelState state : DevArtifactPresentation.ModelState.values()) {
                    final ItemStack item = factory.create(WorldWeaverArtifactBehavior.definition(() -> false),
                            authority.owner(), authority.instanceId(), state);
                    check(factory.itemIdOf(item).equals(WorldWeaverArtifactBehavior.ID), "item identity");
                    check(factory.ownerOf(item).equals(authority.owner()) && factory.instanceOf(item).equals(authority.instanceId()), "marker identity");
                    check(item.getMaxStackSize() == 1 && item.getItemMeta().getItemModel() != null, "modern artifact components");
                    check(item.isSimilar(ItemStack.deserializeBytes(item.serializeAsBytes())), "exact item byte round trip");
                }
                final DevArtifactState bingulus = manager.state(DevItemFactory.BINGULUS_ID);
                final ItemStack exact = factory.createBingulus(bingulus.owner(), bingulus.instanceId());
                final String payload = Base64.getEncoder().encodeToString(exact.serializeAsBytes());
                final Map<String, Object> legacy = new LinkedHashMap<>();
                legacy.put("owner", bingulus.owner().toString());
                legacy.put("instance", bingulus.instanceId().toString());
                legacy.put("issued", true);
                legacy.put("progress-millis", 599_123L);
                legacy.put("pending", Map.of("rarity", "epikus", "entry", "exact-fixture", "item", exact));
                legacy.put("pity", Map.of("since-rare", 23, "since-epic", 145, "since-legendary", 998));
                final Map<String, DevArtifactState> migrated = DevArtifactStateCodec.decode(Map.of("bingulus", legacy),
                        value -> Base64.getEncoder().encodeToString(((ItemStack) value).serializeAsBytes()));
                final var pending = DevArtifactStateCodec.map(migrated.get(DevItemFactory.BINGULUS_ID).behaviorState(), "pending");
                check(payload.equals(pending.get("item")), "legacy exact item migration");
                final YamlConfiguration yaml = new YamlConfiguration();
                DevArtifactStateCodec.encode(migrated).forEach(yaml::set);
                final YamlConfiguration reloaded = new YamlConfiguration();
                reloaded.loadFromString(yaml.saveToString());
                check(reloaded.getInt("schema-version") == 2, "Bukkit YAML schema");
                check(reloaded.getString("artifacts.csodalatos_bingulus.behavior-state.pending.item").equals(payload), "Bukkit YAML pending payload");
                check(!authority.matches(UUID.randomUUID(), authority.owner(), authority.instanceId()), "foreign marker authority");
                PASSED.set(true);
                plugin.getLogger().info("ICESMP_DEV_ARTIFACT_RUNTIME_PROBE_PASS platform=" + Bukkit.getServer().getName());
            } catch (final Throwable failure) {
                plugin.getLogger().severe("ICESMP_DEV_ARTIFACT_RUNTIME_PROBE_FAIL type=" + failure.getClass().getSimpleName());
            } finally { Bukkit.shutdown(); }
        }, 40L);
    }
    public static void storageClosed(final JavaPlugin plugin, final DevItemManager manager, final boolean durable) {
        if (!Boolean.getBoolean(PROPERTY)) return;
        final DevItemManager.Diagnostics diagnostics = manager.diagnostics();
        if (PASSED.get() && durable && diagnostics.shuttingDown() && !diagnostics.scheduled() && !diagnostics.ready()) {
            plugin.getLogger().info("ICESMP_DEV_ARTIFACT_RUNTIME_SHUTDOWN_PASS");
        } else plugin.getLogger().severe("ICESMP_DEV_ARTIFACT_RUNTIME_PROBE_FAIL type=ShutdownDurability");
    }
    private static void check(final boolean condition, final String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
