package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.FactionManager;
import hu.taliann.icesmp.managers.SitManager;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileCooldownStore;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Campfire;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/** Campfire storytelling admitted by a real native sit and protected by a durable cooldown. */
public final class CampfireStoryListener implements Listener {

    private static final String COOLDOWN = "campfire.story.last-success";
    private static final BlockFace[] CARDINAL_DIRECTIONS = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };
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
    private final SitManager sitManager;
    private final PlayerProfileCooldownStore cooldowns = new PlayerProfileCooldownStore();
    /** A hold attempt has no value after disconnect/restart, therefore it is runtime-only. */
    private final Map<UUID, Long> pendingUntil = new ConcurrentHashMap<>();

    public CampfireStoryListener(final JavaPlugin plugin, final ConfigManager configManager,
                                 final MessageManager messageManager,
                                 final FactionManager factionManager,
                                 final SitManager sitManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.factionManager = factionManager;
        this.sitManager = sitManager;
    }

    /** Called only after SitManager has mounted the player on the requested support block. */
    public void onSuccessfulSit(final Player player, final Block seatBlock) {
        if (player == null || seatBlock == null
                || !configManager.getBoolean("campfire-story.enabled", true)) {
            return;
        }
        final BlockFace direction = findCampfireDirection(seatBlock);
        final UUID seatSessionId = sitManager.seatSessionId(player.getUniqueId(), seatBlock);
        if (direction == null || seatSessionId == null) {
            return;
        }

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
        if (now - last < cooldownMillis
                || pendingUntil.getOrDefault(player.getUniqueId(), 0L) > now) {
            return;
        }

        final long holdSeconds = Math.max(2L,
                configManager.getLong("campfire-story.hold-seconds", 6L));
        final UUID playerId = player.getUniqueId();
        pendingUntil.put(playerId, now + holdSeconds * 1000L + 2_000L);
        player.sendActionBar(messageManager.getMessage("campfire-story-start",
                "<gray>🔥 Leültél a tűzhöz… maradj a helyeden, és hallgasd a mesét.</gray>"));

        player.getScheduler().runDelayed(plugin, task -> {
            pendingUntil.remove(playerId);
            if (!stillEligible(player, seatBlock, direction, seatSessionId)) {
                player.sendActionBar(messageManager.getMessage("campfire-story-left",
                        "<gray>🔥 A mese félbeszakadt — felálltál, vagy megváltozott a tűzrakóhely.</gray>"));
                return;
            }
            final long completedAt = System.currentTimeMillis();
            cooldowns.reserve(playerId, PlayerProfileCooldownStore.Domain.QUEST,
                            COOLDOWN, completedAt - cooldownMillis, completedAt)
                    .whenComplete((accepted, failure) -> player.getScheduler().run(plugin, ownerTask -> {
                        if (failure != null || !Boolean.TRUE.equals(accepted)
                                || !stillEligible(player, seatBlock, direction, seatSessionId)) {
                            return;
                        }
                        final Block fireBlock = seatBlock.getRelative(direction, 2);
                        final double radius = Math.max(1.5D,
                                configManager.getDouble("campfire-story.radius", 3.5D));
                        deliverStory(player, fireBlock.getLocation().add(0.5D, 0.5D, 0.5D), radius);
                    }, () -> pendingUntil.remove(playerId)));
        }, () -> pendingUntil.remove(playerId), holdSeconds * 20L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(final PlayerQuitEvent event) {
        pendingUntil.remove(event.getPlayer().getUniqueId());
    }

    private boolean stillEligible(final Player player, final Block seatBlock,
                                  final BlockFace direction, final UUID seatSessionId) {
        return player.isOnline()
                && sitManager.isSittingOn(player.getUniqueId(), seatBlock, seatSessionId)
                && matchesArrangement(seatBlock, direction);
    }

    private BlockFace findCampfireDirection(final Block seatBlock) {
        for (final BlockFace direction : CARDINAL_DIRECTIONS) {
            if (matchesArrangement(seatBlock, direction)) {
                return direction;
            }
        }
        return null;
    }

    /**
     * Required top-down layout at the seat block's Y level:
     * seat -> one genuinely empty block -> one lit campfire.
     */
    private boolean matchesArrangement(final Block seatBlock, final BlockFace direction) {
        if (!Bukkit.isOwnedByCurrentRegion(seatBlock)) {
            return false;
        }
        final Block middle = seatBlock.getRelative(direction);
        final Block fire = seatBlock.getRelative(direction, 2);
        return Bukkit.isOwnedByCurrentRegion(middle)
                && Bukkit.isOwnedByCurrentRegion(fire)
                && middle.getType().isAir()
                && isLitCampfire(fire);
    }

    private boolean isLitCampfire(final Block block) {
        final Material type = block.getType();
        return (type == Material.CAMPFIRE || type == Material.SOUL_CAMPFIRE)
                && block.getBlockData() instanceof Campfire campfire
                && campfire.isLit();
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
                    && Bukkit.isOwnedByCurrentRegion(listener)) {
                listener.sendActionBar(messageManager.getMessage("campfire-story-nearby",
                        "<gray>🔥 {player} mesél a tűznél…</gray>",
                        Map.of("player", player.getName())));
            }
        }
    }
}
