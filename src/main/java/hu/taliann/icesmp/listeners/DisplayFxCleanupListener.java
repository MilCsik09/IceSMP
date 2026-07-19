package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.utils.DisplayFxUtil;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

/**
 * Söpri az esetlegesen bennragadt DisplayFx-entitásokat: chunk-load esemény a chunk régió-szálán
 * fut, így a {@link org.bukkit.Chunk#getEntities()} bejárás és remove Folia-biztos. A fx-entitások
 * {@code setPersistent(false)}-ek, tehát restart/chunk-unload után nem is mentődnek ki — ez a
 * listener csak a ritka, plugin-reload utáni maradékot takarítja (öv és nadrágtartó).
 */
public final class DisplayFxCleanupListener implements Listener {

    @EventHandler
    public void onChunkLoad(final ChunkLoadEvent event) {
        for (final Entity entity : event.getChunk().getEntities()) {
            if (entity.getScoreboardTags().contains(DisplayFxUtil.FX_TAG)) {
                entity.remove();
            }
        }
    }
}
