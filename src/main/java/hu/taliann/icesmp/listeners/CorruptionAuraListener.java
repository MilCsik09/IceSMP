package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.CorruptionManager;
import hu.taliann.icesmp.utils.MessageManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

/**
 * P4e — a rontás-góc mag-aurája: aki az aktív korrupt zóna MAGJÁHOZ közel áll
 * ({@link CorruptionManager#isInAura}), az {@code icesmp:rontas} környezeti
 * damage-type-tól szenved enyhe, ismétlődő sebzést — a tisztításnak (mag-törés)
 * így tétje van: gyorsan kell, vagy gyógyítással. Nem letális szándékkal: a
 * sebzés és a sugár configból hangolható, {@code corruption.aura.enabled} kikapcsolható.
 *
 * <p>A halál-üzenetet szerver-oldalról írjuk felül: a saját damage-type message-id-jét
 * a kliens nem fordítaná.
 *
 * <p>Folia: a {@link #tick()} a globális schedulerről iterál, de MINDEN játékos-érintés
 * (hely-olvasás, sebzés) a játékos SAJÁT régió-szálán, scheduler-hoppal fut — a
 * ClassHealthService OOC-regen mintája.
 */
public final class CorruptionAuraListener implements Listener {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final CorruptionManager corruptionManager;
    private final MessageManager messageManager;

    public CorruptionAuraListener(final JavaPlugin plugin, final ConfigManager configManager,
                                  final CorruptionManager corruptionManager, final MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.corruptionManager = corruptionManager;
        this.messageManager = messageManager;
    }

    /** A world-events ütemezőről hívott periodikus driver. */
    public void tick() {
        if (!corruptionManager.isActive() || !configManager.getBoolean("corruption.aura.enabled", true)) {
            return;
        }
        final double damage = Math.max(0.0D, configManager.getDouble("corruption.aura.damage", 1.0D));
        if (damage <= 0.0D) {
            return;
        }
        for (final Player player : Bukkit.getOnlinePlayers()) {
            player.getScheduler().run(plugin, task -> {
                if (player.isDead() || player.getGameMode() == GameMode.CREATIVE
                        || player.getGameMode() == GameMode.SPECTATOR) {
                    return;
                }
                if (!corruptionManager.isInAura(player.getLocation())) {
                    return;
                }
                final DamageType type = resolveType();
                if (type != null) {
                    player.damage(damage, DamageSource.builder(type).build());
                } else {
                    player.damage(damage);
                }
            }, null);
        }
    }

    @EventHandler
    public void onAuraDeath(final PlayerDeathEvent event) {
        final EntityDamageEvent last = event.getEntity().getLastDamageCause();
        if (last == null || !isRontas(last.getDamageSource())) {
            return;
        }
        event.deathMessage(messageManager.getMessage("corruption-aura-death",
                "<dark_gray>{victim} testét felemésztette a rontás.</dark_gray>",
                Map.of("victim", event.getEntity().getName())));
    }

    private static boolean isRontas(final DamageSource source) {
        if (source == null) {
            return false;
        }
        final NamespacedKey key = source.getDamageType().getKey();
        return "icesmp".equals(key.getNamespace()) && "rontas".equals(key.getKey());
    }

    /** A rontás damage-type a registryből, vagy null (bootstrap-hiba → vanília fallback). */
    private static DamageType resolveType() {
        try {
            return io.papermc.paper.registry.RegistryAccess.registryAccess()
                    .getRegistry(io.papermc.paper.registry.RegistryKey.DAMAGE_TYPE)
                    .get(NamespacedKey.fromString("icesmp:rontas"));
        } catch (final Exception exception) {
            return null;
        }
    }
}
