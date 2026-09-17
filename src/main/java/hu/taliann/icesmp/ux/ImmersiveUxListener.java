package hu.taliann.icesmp.ux;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Objects;

/** Cross-system lifecycle boundary for the three player-owned presentation runtimes. */
public final class ImmersiveUxListener implements Listener {
    private final DialogueEngine dialogue;
    private final MusicDirector music;
    private final UnifiedGuiManager gui;

    public ImmersiveUxListener(final DialogueEngine dialogue, final MusicDirector music,
                               final UnifiedGuiManager gui) {
        this.dialogue = Objects.requireNonNull(dialogue, "dialogue");
        this.music = Objects.requireNonNull(music, "music");
        this.gui = Objects.requireNonNull(gui, "gui");
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) { clear(event.getPlayer().getUniqueId(), event.getPlayer()); }

    @EventHandler
    public void onKick(final PlayerKickEvent event) { clear(event.getPlayer().getUniqueId(), event.getPlayer()); }

    @EventHandler
    public void onDeath(final PlayerDeathEvent event) { clear(event.getEntity().getUniqueId(), event.getEntity()); }

    @EventHandler
    public void onWorldChange(final PlayerChangedWorldEvent event) {
        clear(event.getPlayer().getUniqueId(), event.getPlayer());
    }

    private void clear(final java.util.UUID id, final org.bukkit.entity.Player player) {
        dialogue.clearPlayerState(id);
        music.clear(player);
        gui.clear(player);
    }

    public void shutdown() {
        dialogue.shutdown();
        music.shutdown();
        gui.shutdown();
    }
}
