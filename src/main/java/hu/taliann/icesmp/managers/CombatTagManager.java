package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Harc-jelölés (combat-tag): PvP-találatkor mindkét fél rövid időre jelölt lesz.
 * A jelölt játékos bemehet a védett zónába, de ott sem kap PvP-védelmet (a
 * vesztésre álló fél ne tudjon a fővárosba sétálva sebezhetetlenné válni), és a
 * komp sem viszi el. A jelölés a hadi-ablak/párbaj "volt-e tényleges harc"
 * feltétele is.
 */
public final class CombatTagManager implements hu.taliann.icesmp.session.PlayerStateCleanup {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    /** játékos → epoch ms, ameddig a jelölés él (lejárt bejegyzés = nem jelölt). */
    private final Map<UUID, Long> taggedUntil = new ConcurrentHashMap<>();

    public CombatTagManager(final JavaPlugin plugin, final ConfigManager configManager,
                            final MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
    }

    public boolean isEnabled() {
        return configManager.getBoolean("combat.tag-enabled", true);
    }

    public boolean isTagged(final UUID playerId) {
        final Long until = taggedUntil.get(playerId);
        if (until == null) {
            return false;
        }
        if (until <= System.currentTimeMillis()) {
            taggedUntil.remove(playerId);
            return false;
        }
        return true;
    }

    public long remainingSeconds(final UUID playerId) {
        final Long until = taggedUntil.get(playerId);
        return until == null ? 0L : Math.max(0L, (until - System.currentTimeMillis() + 999L) / 1000L);
    }

    /**
     * PvP-találatkor mindkét fél jelölése. A hívó a VICTIM régió-szálán fut
     * (damage-event) — az áldozat közvetlenül, a támadó scheduler-hoppal kap
     * értesítést, és csak az első jelöléskor (spam-fék).
     */
    public void tagBoth(final Player victim, final Player attacker) {
        if (!isEnabled()) {
            return;
        }
        final long until = System.currentTimeMillis()
                + Math.max(1L, configManager.getLong("combat.tag-seconds", 12L)) * 1000L;
        final boolean victimWasTagged = isTagged(victim.getUniqueId());
        final boolean attackerWasTagged = isTagged(attacker.getUniqueId());
        taggedUntil.put(victim.getUniqueId(), until);
        taggedUntil.put(attacker.getUniqueId(), until);
        recordPairContact(victim.getUniqueId(), attacker.getUniqueId());
        if (!victimWasTagged) {
            victim.sendActionBar(messageManager.getMessage("combat-tagged",
                    "<red>⚔ Harcban vagy — a zónák most nem védenek, és a komp sem indul.</red>"));
        }
        if (!attackerWasTagged) {
            attacker.getScheduler().run(plugin, task -> attacker.sendActionBar(messageManager.getMessage(
                    "combat-tagged",
                    "<red>⚔ Harcban vagy — a zónák most nem védenek, és a komp sem indul.</red>")), null);
        }
    }

    /**
     * Pár-szintű első kontaktus (rendezett UUID-pár → epoch ms): ebből számol a
     * hadi-ablak/párbaj "volt-e tényleges harc" feltétel — egyetlen (akár halálos)
     * ütés önmagában nem harc, a win-trade így nem termel pontot/bűn-tisztulást.
     */
    private final Map<String, Long> pairFirstContact = new ConcurrentHashMap<>();

    private static String pairKey(final UUID a, final UUID b) {
        return a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
    }

    public long pairFightSeconds(final UUID a, final UUID b) {
        final Long first = pairFirstContact.get(pairKey(a, b));
        return first == null ? 0L : Math.max(0L, (System.currentTimeMillis() - first) / 1000L);
    }

    public long minFightSeconds() {
        return Math.max(0L, configManager.getLong("combat.min-fight-seconds", 20L));
    }

    private void recordPairContact(final UUID a, final UUID b) {
        final long now = System.currentTimeMillis();
        if (pairFirstContact.size() > 512) {
            pairFirstContact.values().removeIf(stamp -> now - stamp > 600_000L);
        }
        pairFirstContact.putIfAbsent(pairKey(a, b), now);
    }

    @Override
    public void clearPlayerState(final UUID playerId) {
        if (playerId != null) {
            taggedUntil.remove(playerId);
            final String prefix = playerId.toString();
            pairFirstContact.keySet().removeIf(key -> key.contains(prefix));
        }
    }
}
