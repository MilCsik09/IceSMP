package hu.taliann.icesmp.managers;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.UUID;

/** Explicit isolated-server fixture for the native lifecycle used by the limited WW Event provider. */
public final class GatheringWindowRuntimeProbe {
    private GatheringWindowRuntimeProbe() { }
    public static void install(JavaPlugin plugin, GatheringBuffManager events) {
        if (!Boolean.getBoolean("icesmp.pve-control-runtime")) return;
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
            try {
                require(Bukkit.isGlobalTickThread() && Bukkit.getOnlinePlayers().isEmpty(), "ISOLATED_GLOBAL_OWNER_REQUIRED");
                require(events.activeWindow() == null && !events.available().isEmpty(), "EVENT_FIXTURE_UNAVAILABLE");
                int tested = 0;
                for (final var kind : events.available()) {
                    final UUID first = UUID.randomUUID(), replacement = UUID.randomUUID();
                    require(events.startControlled(first, kind, true), "START_FAILED");
                    require(events.activeWindow().instanceId().equals(first) && events.activeWindow().sandbox(), "INSTANCE_POLICY_NOT_ATOMIC");
                    require(events.xpMultiplier() == 1 && events.bonusDropChance(kind) == 0, "SANDBOX_REWARD_LEAK");
                    require(!events.stopExpected(replacement, true) && events.activeWindow().instanceId().equals(first), "STALE_STOP_REPLACED_INSTANCE");
                    require(events.stopExpected(first, true) && events.activeWindow() == null, "EXPECTED_STOP_FAILED");
                    require(events.startControlled(replacement, kind, true), "REPLACEMENT_START_FAILED");
                    require(!events.stopExpected(first, true) && events.activeWindow().instanceId().equals(replacement), "ABA_STOP_BYPASSED");
                    require(events.stopExpected(replacement, true), "REPLACEMENT_CLEANUP_FAILED"); tested++;
                }
                plugin.getLogger().info("ICESMP_EVENT_SANDBOX_RUNTIME_PROBE_PASS kinds=" + tested + " scope=native_instance_lifecycle_reward_suppression players=0");
            } catch (Throwable failure) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "ICESMP_EVENT_SANDBOX_RUNTIME_PROBE_FAIL", failure); Bukkit.shutdown();
            }
        }, 40);
    }
    private static void require(boolean condition, String code) { if (!condition) throw new IllegalStateException(code); }
}
