package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Llama;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * B6 — játékos-indított karaván (szállítmány-rablás): a király a frakciókasszából
 * rakományt indít; a szállítmány egy KIHIRDETETT őrzőponton áll egy védési ablakig
 * (v1: nincs entitás-pathfinding — a spec Folia-buktatóját így kerüljük el; a
 * „mozgást" a narratív broadcastok adják). Ha az ablak végéig túléli → a kassza a
 * rakományt SZORZÓVAL kapja vissza (profit); ha ellenséges játékos leöli → a rakomány
 * a rabló frakció kasszájába megy; saját/azonos frakciós ölésnél a rakomány elvész
 * (grief-fék). Frakciónként cooldown. Folia: a spawn/despawn a helyszín régió-
 * schedulerén fut; az állapot volatile/synchronized (a tick a global schedulerről jön).
 */
public final class PlayerCaravanManager {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final FactionTreasuryManager treasuryManager;
    private final FactionManager factionManager;
    private final EventSpawnGuard eventSpawnGuard;
    private final MessageManager messageManager;

    private volatile FactionType senderFaction;
    private volatile double cargo;
    private volatile long windowEndMillis;
    private volatile UUID convoyId;
    private volatile Location site;
    private final Map<FactionType, Long> cooldownUntil = new java.util.concurrent.ConcurrentHashMap<>();

    public PlayerCaravanManager(final JavaPlugin plugin, final ConfigManager configManager,
                                final FactionTreasuryManager treasuryManager,
                                final FactionManager factionManager,
                                final EventSpawnGuard eventSpawnGuard,
                                final MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.treasuryManager = treasuryManager;
        this.factionManager = factionManager;
        this.eventSpawnGuard = eventSpawnGuard;
        this.messageManager = messageManager;
    }

    public boolean isActive() {
        return convoyId != null;
    }

    public boolean isConvoy(final UUID entityId) {
        final UUID current = convoyId;
        return current != null && current.equals(entityId);
    }

    /** Indítás (a hívó a király, a parancs ellenőrzi); hibakulcs vagy null. */
    public synchronized String send(final Player king, final double amount) {
        if (!configManager.getBoolean("player-caravan.enabled", true)) {
            return "pcaravan-disabled";
        }
        if (isActive()) {
            return "pcaravan-busy";
        }
        final FactionType faction = factionManager.getFaction(king.getUniqueId());
        if (faction == null) {
            return "pcaravan-no-faction";
        }
        final long now = System.currentTimeMillis();
        if (cooldownUntil.getOrDefault(faction, 0L) > now) {
            return "pcaravan-cooldown";
        }
        final double min = configManager.getDouble("player-caravan.min-cargo", 100.0D);
        final double max = configManager.getDouble("player-caravan.max-cargo", 1000.0D);
        if (amount < min || amount > max) {
            return "pcaravan-bad-amount";
        }
        if (!treasuryManager.withdraw(faction, amount)) {
            return "pcaravan-poor";
        }
        // Őrzőpont sorsolása a küldő játékos körül (vadonban, guard-mátrixszal szűrve).
        final World world = king.getWorld();
        final int radius = Math.max(64, configManager.getInt("player-caravan.site-radius", 300));
        Location chosen = null;
        for (int attempt = 0; attempt < 12; attempt++) {
            final int x = king.getLocation().getBlockX() + ThreadLocalRandom.current().nextInt(-radius, radius + 1);
            final int z = king.getLocation().getBlockZ() + ThreadLocalRandom.current().nextInt(-radius, radius + 1);
            final Location candidate = new Location(world, x + 0.5, 0, z + 0.5);
            if (!eventSpawnGuard.isBlocked("player-caravan", candidate)) {
                chosen = candidate;
                break;
            }
        }
        if (chosen == null) {
            treasuryManager.deposit(faction, amount); // visszatérítés — nem találtunk helyet
            return "pcaravan-no-site";
        }
        senderFaction = faction;
        cargo = amount;
        site = chosen;
        windowEndMillis = now + Math.max(60, configManager.getInt("player-caravan.window-seconds", 300)) * 1000L;
        cooldownUntil.put(faction, now + Math.max(1, configManager.getInt("player-caravan.cooldown-minutes", 90)) * 60_000L);
        convoyId = new UUID(0L, 0L); // placeholder, a spawn cseréli — az isActive() már igaz

        final Location target = chosen;
        world.getRegionScheduler().run(plugin, target, task -> {
            final int y = world.getHighestBlockYAt(target.getBlockX(), target.getBlockZ()) + 1;
            target.setY(y);
            final Llama llama = world.spawn(target, Llama.class, mob -> {
                EventSpawnGuard.prepare(mob);
                mob.customName(net.kyori.adventure.text.Component.text(
                        "🐫 Szállítmány — " + faction.getDisplayName()));
                mob.setCustomNameVisible(true);
                mob.setAI(false);
                final double hp = Math.max(20.0D, configManager.getDouble("player-caravan.convoy-hp", 120.0D));
                mob.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(hp);
                mob.setHealth(hp);
            });
            convoyId = llama.getUniqueId();
        });

        Bukkit.getServer().broadcast(messageManager.getMessage("pcaravan-sent",
                "<gold>🐫 A(z) <white>{faction}</white> szállítmányt indított (<white>{cargo}</white> értékben)! Őrzőpont: <white>{x} / {z}</white> környéke — {minutes} percig védhető VAGY rabolható!</gold>",
                Map.of("faction", faction.getDisplayName(), "cargo", String.valueOf(amount),
                        "x", String.valueOf(chosen.getBlockX()), "z", String.valueOf(chosen.getBlockZ()),
                        "minutes", String.valueOf(configManager.getInt("player-caravan.window-seconds", 300) / 60))));
        return null;
    }

    /** A world-events tick hívja: ablak-lejárat = sikeres célba érés. */
    public void tick() {
        if (!isActive() || System.currentTimeMillis() < windowEndMillis) {
            return;
        }
        final FactionType faction = senderFaction;
        final double amount = cargo;
        final double multiplier = Math.max(1.0D, configManager.getDouble("player-caravan.success-multiplier", 1.25D));
        finishAndDespawn();
        if (faction != null) {
            treasuryManager.deposit(faction, amount * multiplier);
            Bukkit.getServer().broadcast(messageManager.getMessage("pcaravan-arrived",
                    "<gold>🐫 A(z) <white>{faction}</white> szállítmánya CÉLBA ÉRT — a kassza <white>{reward}</white>-t kap (a kereskedők bőkezűek)!</gold>",
                    Map.of("faction", faction.getDisplayName(),
                            "reward", String.valueOf(amount * multiplier))));
        }
    }

    /** A kill-listener hívja, ha a konvoj meghalt; a killer régió-szálán fut. */
    public synchronized void onConvoyKilled(final Player killer) {
        if (!isActive()) {
            return;
        }
        final FactionType sender = senderFaction;
        final double amount = cargo;
        convoyId = null;
        senderFaction = null;
        site = null;
        final FactionType robber = killer == null ? null : factionManager.getFaction(killer.getUniqueId());
        if (robber != null && robber != sender) {
            treasuryManager.deposit(robber, amount);
            Bukkit.getServer().broadcast(messageManager.getMessage("pcaravan-robbed",
                    "<red>🐫 A(z) <white>{sender}</white> szállítmányát KIRABOLTA a(z) <white>{robber}</white> — a rakomány ({cargo}) az övék!</red>",
                    Map.of("sender", sender == null ? "?" : sender.getDisplayName(),
                            "robber", robber.getDisplayName(), "cargo", String.valueOf(amount))));
        } else {
            Bukkit.getServer().broadcast(messageManager.getMessage("pcaravan-lost",
                    "<red>🐫 A(z) <white>{sender}</white> szállítmánya odaveszett — a rakomány a porba hullt.</red>",
                    Map.of("sender", sender == null ? "?" : sender.getDisplayName())));
        }
    }

    /** Konvoj-despawn a saját régió-szálán + állapot-nullázás (ablak-lejáratkor). */
    private synchronized void finishAndDespawn() {
        final UUID id = convoyId;
        convoyId = null;
        final Location where = site;
        site = null;
        if (id == null || where == null) {
            return;
        }
        where.getWorld().getRegionScheduler().run(plugin, where, task -> {
            final org.bukkit.entity.Entity entity = Bukkit.getEntity(id);
            if (entity != null) {
                entity.remove();
            }
        });
    }

    /** Leállításkor: aktív szállítmány visszatérítése (ne vesszen kasszapénz restartkor). */
    public void shutdown() {
        final FactionType faction = senderFaction;
        final double amount = cargo;
        if (isActive() && faction != null) {
            treasuryManager.deposit(faction, amount);
        }
        finishAndDespawn();
    }
}
