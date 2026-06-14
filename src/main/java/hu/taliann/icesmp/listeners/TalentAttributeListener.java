package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.TalentManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class TalentAttributeListener implements Listener {

    private final TalentManager talentManager;

    public TalentAttributeListener(final TalentManager talentManager) {
        this.talentManager = talentManager;
    }

    @EventHandler
    public void onJoin(final PlayerJoinEvent event) {
        talentManager.applyAttributeTalents(event.getPlayer());
    }
}
