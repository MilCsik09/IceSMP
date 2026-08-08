package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.FactionManager;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileCooldownStore;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/** Campfire storytelling with a durable PlayerProfile cooldown and runtime hold state. */
public final class CampfireStoryListener implements Listener {

    private static final String COOLDOWN = "campfire.story.last-success";
    private static final String[] FALLBACK_STORIES = {
            "<gray>🔥 „A Vérháborúk előtt a két nép egy tűznél ült — mint most mi. A tűz emlékszik.”</gray>",
            "<gray>🔥 „A Néma Királynő két mondatot mondott. Az elsőre felkeltek a holtak. A másodikra eltűnt a nemesség.”</gray>",
            "<gray>🔥 „A Fa egyik gyökere állítólag a tábortüzek alatt fut. Ezért melegszik át a történet is.”</gray>",
            "<gray>🔥 „Minden korszak úgy kezdődik, hogy valaki tüzet rak. Így, mint mi most.”</gray>"
    };

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final FactionManager factionManager;
    private final PlayerProfileCooldownStore cooldowns = new PlayerProfileCooldownStore();
    /** A hold attempt has no value after disconnect/restart, therefore it is runtime-only. */
    private final Map<UUID, Long> pendingUntil = new ConcurrentHashMap<>();

    public CampfireStoryListener(final JavaPlugin plugin, final ConfigManager configManager,
                                 final MessageManager messageManager,
                                 final FactionManager factionManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.factionManager = factionManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onCampfireUse(final PlayerInteractEvent event) {
        if (!event.getAction().isRightClick() || event.getClickedBlock() == null) return;
        final Material type = event.getClickedBlock().getType();
        if (type != Material.CAMPFIRE && type != Material.SOUL_CAMPFIRE) return;
        final Player player = event.getPlayer();
        if (!player.isSneaking() || !configManager.getBoolean("campfire-story.enabled", true)) return;

        final long now = System.currentTimeMillis();
        final long cooldownMillis = Math.max(0L,
                configManager.getLong("campfire-story.cooldown-minutes", 60L)) * 60_000L;
        final long last;
        try {
            last = cooldowns.read(player.getUniqueId(),
                    PlayerProfileCooldownStore.Domain.QUEST, COOLDOWN);
        } catch (final RuntimeException profileNotReady) {
            return;
        }
        if (now - last < cooldownMillis) return;
        if (pendingUntil.getOrDefault(player.getUniqueId(), 0L) > now) return;

        final long holdSeconds = Math.max(2L,
                configManager.getLong("campfire-story.hold-seconds", 6L));
        pendingUntil.put(player.getUniqueId(), now + holdSeconds * 1000L + 2_000L);
        final Location fire = event.getClickedBlock().getLocation().add(0.5D, 0.5D, 0.5D);
        player.sendActionBar(messageManager.getMessage("campfire-story-start",
                "<gray>🔥 Leülsz a tűzhöz… maradj mellette, és hallgasd a mesét.</gray>"));

        player.getScheduler().runDelayed(plugin, task -> {
            pendingUntil.remove(player.getUniqueId());
            final double radius = Math.max(1.5D,
                    configManager.getDouble("campfire-story.radius", 3.5D));
            if (!player.getWorld().equals(fire.getWorld())
                    || player.getLocation().distanceSquared(fire) > radius * radius) {
                player.sendActionBar(messageManager.getMessage("campfire-story-left",
                        "<gray>🔥 A mese félbeszakadt — elhagytad a tüzet.</gray>"));
                return;
            }
            final long completedAt = System.currentTimeMillis();
            cooldowns.reserve(player.getUniqueId(), PlayerProfileCooldownStore.Domain.QUEST,
                            COOLDOWN, completedAt - cooldownMillis, completedAt)
                    .whenComplete((accepted, failure) -> player.getScheduler().run(plugin, ownerTask -> {
                        if (failure != null || !Boolean.TRUE.equals(accepted)) return;
                        deliverStory(player, fire, radius);
                    }, null));
        }, null, holdSeconds * 20L);
    }

    private void deliverStory(final Player player, final Location fire, final double radius) {
        final List<String> custom = configManager.getStringList("campfire-story.stories");
        final String fallback = FALLBACK_STORIES[ThreadLocalRandom.current()
                .nextInt(FALLBACK_STORIES.length)];
        final String raw;
        if (!custom.isEmpty()) raw = custom.get(ThreadLocalRandom.current().nextInt(custom.size()));
        else {
            final var faction = factionManager.getChosenFaction(player.getUniqueId()).orElse(null);
            final String key = faction == null ? "campfire-story-1"
                    : "campfire-story-" + faction.name().toLowerCase(java.util.Locale.ROOT) + "-1";
            player.sendMessage(messageManager.getMessage(key, fallback));
            raw = null;
        }
        if (raw != null) {
            player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                    .deserialize(raw));
        }
        final int xp = Math.max(0, configManager.getInt("campfire-story.xp-reward", 8));
        if (xp > 0) player.giveExp(xp);
        hu.taliann.icesmp.utils.ParticleUtil.spawn(player.getWorld(), Particle.SOUL_FIRE_FLAME,
                fire, 14, 0.4D, 0.5D, 0.4D, 0.01D);
        player.playSound(fire, Sound.AMBIENT_CAVE, 0.3F, 1.4F);
        for (final org.bukkit.entity.Entity nearby : player.getNearbyEntities(radius, radius, radius)) {
            if (nearby instanceof Player listener
                    && org.bukkit.Bukkit.isOwnedByCurrentRegion(listener)) {
                listener.sendActionBar(messageManager.getMessage("campfire-story-nearby",
                        "<gray>🔥 {player} mesél a tűznél…</gray>",
                        Map.of("player", player.getName())));
            }
        }
    }
}
