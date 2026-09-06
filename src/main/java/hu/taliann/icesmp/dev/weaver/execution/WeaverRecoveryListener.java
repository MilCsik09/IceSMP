package hu.taliann.icesmp.dev.weaver.execution;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import java.util.OptionalLong;

/** Event owners contribute immutable identifiers only; reconciliation resolves fresh state on its owner. */
public final class WeaverRecoveryListener implements Listener {
    private final WeaverRecoveryCoordinator recovery;
    public WeaverRecoveryListener(final WeaverRecoveryCoordinator recovery) { this.recovery = java.util.Objects.requireNonNull(recovery); }
    @EventHandler public void onWeaverPlayerAvailable(final PlayerJoinEvent event) { recovery.entityAvailable(event.getPlayer().getUniqueId()); }
    @EventHandler public void onWeaverEntitiesAvailable(final EntitiesLoadEvent event) {
        event.getEntities().forEach(entity -> recovery.entityAvailable(entity.getUniqueId()));
    }
    @EventHandler public void onWeaverChunkAvailable(final ChunkLoadEvent event) {
        final var chunk = event.getChunk();
        recovery.worldAvailable(chunk.getWorld().getUID(), OptionalLong.of(((long) chunk.getX() << 32) | (chunk.getZ() & 0xffffffffL)));
    }
    @EventHandler public void onWeaverWorldAvailable(final WorldLoadEvent event) { recovery.worldAvailable(event.getWorld().getUID(), OptionalLong.empty()); }
}
