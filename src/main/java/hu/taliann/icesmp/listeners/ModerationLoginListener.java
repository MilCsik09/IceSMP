package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.ModerationManager;
import hu.taliann.icesmp.moderation.PunishmentRecord;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

/** Async login gate backed only by immutable/synchronized ledger reads. */
public final class ModerationLoginListener implements Listener {
    private final ModerationManager manager;
    private final MessageManager messages;

    public ModerationLoginListener(final ModerationManager manager, final MessageManager messages) {
        this.manager = manager;
        this.messages = messages;
    }

    @EventHandler
    public void onPreLogin(final AsyncPlayerPreLoginEvent event) {
        final PunishmentRecord ban = manager.activeBan(event.getUniqueId()).orElse(null);
        if (ban == null) {
            return;
        }
        final String remaining = ban.expiresAtMillis() == null ? "végleges"
                : ModerationManager.formatRemaining(ban.expiresAtMillis());
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, messages.getComponent(
                "moderation.ban-login", "&cKi vagy tiltva az IceSMP szerverről.\n&7Időtartam: &f%s\n&7Ok: &f%s",
                remaining, ban.reason().isBlank() ? "Nincs megadva" : ban.reason()));
    }
}
