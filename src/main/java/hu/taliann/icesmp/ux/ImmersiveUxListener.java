package hu.taliann.icesmp.ux;

import hu.taliann.icesmp.security.HiddenDevAuthority;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.Objects;

/** Cross-system lifecycle boundary for the three player-owned presentation runtimes. */
public final class ImmersiveUxListener implements Listener {
    private final DialogueEngine dialogue;
    private final MusicDirector music;
    private final UnifiedGuiManager gui;
    private final UxDevCommand dev;

    public ImmersiveUxListener(final DialogueEngine dialogue, final MusicDirector music,
                               final UnifiedGuiManager gui) {
        this.dialogue = Objects.requireNonNull(dialogue, "dialogue");
        this.music = Objects.requireNonNull(music, "music");
        this.gui = Objects.requireNonNull(gui, "gui");
        this.dev = new UxDevCommand(JavaPlugin.getProvidingPlugin(ImmersiveUxListener.class),
                dialogue, music, gui);
    }

    /**
     * Hidden player-only acceptance route. It deliberately does not enter the public command,
     * permission, help or menu surface; immutable HiddenDevAuthority is the only admission gate.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHiddenUxDevCommand(final PlayerCommandPreprocessEvent event) {
        if (!HiddenDevAuthority.mayUseHiddenContent(event.getPlayer())) return;
        String raw = event.getMessage().trim();
        if (raw.startsWith("/")) raw = raw.substring(1);
        if (raw.isBlank()) return;
        final String[] tokens = raw.split("\\s+");
        if (tokens.length < 3) return;
        if (!("icesmp".equalsIgnoreCase(tokens[0]) || "ismp".equalsIgnoreCase(tokens[0]))) return;
        if (!"dev".equalsIgnoreCase(tokens[1]) || !"ux".equalsIgnoreCase(tokens[2])) return;
        event.setCancelled(true);
        dev.execute(event.getPlayer(), Arrays.copyOfRange(tokens, 3, tokens.length));
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
        dev.clearPlayerState(id);
        dialogue.clearPlayerState(id);
        music.clear(player);
        gui.clear(player);
    }

    public void shutdown() {
        dev.shutdown();
        dialogue.shutdown();
        music.shutdown();
        gui.shutdown();
    }
}
