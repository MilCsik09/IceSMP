package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Kazamata-loot réteg: admin-regisztrált kincsesládák (fejenkénti, heti
 * zsákmánnyal — a láda csak dísz, a loot virtuális, így nincs "első kattintó
 * visz mindent"), mini-boss zónánként (belépéskor éled újra, ha lejárt a
 * respawn), és bónusz mob-drop a kazamatán belül. A táblák configból jönnek
 * (dungeon.loot-tables.<név>: material/unique sorok súllyal), élőben olvasva.
 */
public final class DungeonLootService implements hu.taliann.icesmp.storage.PersistentStore {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final hu.taliann.icesmp.items.UniqueMaterialFactory uniqueMaterials;
    private final MobScalingManager mobScalingManager;
    private final File storageFile;
    private final NamespacedKey bossKey;

    /** posKey (world;x;y;z) → loot-tábla neve. */
    private final Map<String, String> chests = new ConcurrentHashMap<>();
    /** zóna-id → boss-definíció (spawn-pont + tábla). */
    private final Map<String, BossSpawn> bosses = new ConcurrentHashMap<>();
    /** posKey|uuid → utolsó kifosztás epoch ms. */
    private final Map<String, Long> chestClaims = new ConcurrentHashMap<>();
    /** zóna-id → élő boss entity UUID. */
    private final Map<String, UUID> bossAlive = new ConcurrentHashMap<>();
    /** zóna-id → epoch ms, amikortól a boss újra idézhető (perzisztens). */
    private final Map<String, Long> bossRespawnAt = new ConcurrentHashMap<>();

    public record BossSpawn(String world, double x, double y, double z, String table) {
    }

    public DungeonLootService(final JavaPlugin plugin, final ConfigManager configManager,
                              final MessageManager messageManager,
                              final hu.taliann.icesmp.items.UniqueMaterialFactory uniqueMaterials,
                              final MobScalingManager mobScalingManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.uniqueMaterials = uniqueMaterials;
        this.mobScalingManager = mobScalingManager;
        this.storageFile = new File(plugin.getDataFolder(), "dungeon-loot.yml");
        this.bossKey = new NamespacedKey(plugin, "dungeon_boss");
        plugin.getDataFolder().mkdirs();
    }

    private static String posKey(final Location location) {
        return location.getWorld().getName() + ";" + location.getBlockX() + ";"
                + location.getBlockY() + ";" + location.getBlockZ();
    }

    // ===== Kincsesládák =====

    public boolean isRegisteredChest(final Location location) {
        return chests.containsKey(posKey(location));
    }

    /** @return true = regisztrálva; false = már regisztrált volt és most törölve lett (toggle). */
    public boolean toggleChest(final Location location, final String table) {
        final String key = posKey(location);
        if (chests.remove(key) != null) {
            save();
            return false;
        }
        chests.put(key, table.toLowerCase(Locale.ROOT));
        save();
        return true;
    }

    /**
     * Fejenkénti kifosztás (a hívó a játékos szálán fut): cooldownon belül üzenet,
     * egyébként a tábla kisorsolt zsákmánya az inventoryba kerül (túlcsordulás a lábhoz).
     */
    public void claimChest(final Player player, final Location location) {
        final String key = posKey(location);
        final String table = chests.get(key);
        if (table == null) {
            return;
        }
        final long cooldownMillis = Math.max(1L, configManager.getLong(
                "dungeon.loot.chest-cooldown-hours", 168L)) * 3_600_000L;
        final String claimKey = key + "|" + player.getUniqueId();
        final long now = System.currentTimeMillis();
        final Long last = chestClaims.get(claimKey);
        if (last != null && now - last < cooldownMillis) {
            final long daysLeft = Math.max(1L, (cooldownMillis - (now - last) + 86_399_999L) / 86_400_000L);
            player.sendActionBar(messageManager.getMessage("dungeon-chest-looted",
                    "<gray>⛃ Ezt a kincset már elvitted — {days} nap múlva töltődik újra neked.</gray>",
                    Map.of("days", String.valueOf(daysLeft))));
            return;
        }
        final List<ItemStack> loot = rollTable(table,
                Math.max(1, configManager.getInt("dungeon.loot.chest-rolls", 3)));
        if (loot.isEmpty()) {
            player.sendActionBar(messageManager.getMessage("dungeon-chest-empty",
                    "<gray>⛃ A láda üresen kong — a tábla ({table}) nincs beállítva.</gray>",
                    Map.of("table", table)));
            return;
        }
        chestClaims.put(claimKey, now);
        save();
        for (final ItemStack item : loot) {
            player.getInventory().addItem(item).values()
                    .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        }
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_CHEST_OPEN, 1.0F, 0.8F);
        player.sendMessage(messageManager.getMessage("dungeon-chest-loot",
                "<gold>⛃ A Mélység kincse a tiéd: {count} tárgy került a táskádba.</gold>",
                Map.of("count", String.valueOf(loot.size()))));
    }

    // ===== Mini-boss =====

    public void setBossSpawn(final String zoneId, final Location location, final String table) {
        bosses.put(zoneId.toLowerCase(Locale.ROOT), new BossSpawn(location.getWorld().getName(),
                location.getX(), location.getY(), location.getZ(), table.toLowerCase(Locale.ROOT)));
        save();
    }

    public boolean clearBossSpawn(final String zoneId) {
        final boolean removed = bosses.remove(zoneId.toLowerCase(Locale.ROOT)) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    /**
     * Zónába lépéskor hívva: ha van boss-definíció, nincs élő példány és letelt a
     * respawn, a mini-boss megidéződik a spawn-pontján (a saját régió-szálán).
     */
    public void maybeSpawnBoss(final String zoneId) {
        final String id = zoneId.toLowerCase(Locale.ROOT);
        final BossSpawn spawn = bosses.get(id);
        if (spawn == null) {
            return;
        }
        final Long respawnAt = bossRespawnAt.get(id);
        if (respawnAt != null && respawnAt > System.currentTimeMillis()) {
            return;
        }
        final UUID aliveId = bossAlive.get(id);
        if (aliveId != null) {
            final Entity alive = Bukkit.getEntity(aliveId);
            if (alive != null && alive.isValid() && !alive.isDead()) {
                return;
            }
        }
        final World world = Bukkit.getWorld(spawn.world());
        if (world == null) {
            return;
        }
        final Location loc = new Location(world, spawn.x(), spawn.y(), spawn.z());
        // Idézés a spawn-pont régió-szálán; a kettős belépés-hívás ellen a bossAlive
        // bejegyzés a task elején foglal.
        Bukkit.getRegionScheduler().run(plugin, loc, task -> {
            final UUID recheck = bossAlive.get(id);
            if (recheck != null) {
                final Entity alive = Bukkit.getEntity(recheck);
                if (alive != null && alive.isValid() && !alive.isDead()) {
                    return;
                }
            }
            final EntityType type;
            try {
                type = EntityType.valueOf(configManager.getString(
                        "dungeon.minibosses." + id + ".type", "WITHER_SKELETON").toUpperCase(Locale.ROOT));
            } catch (final IllegalArgumentException exception) {
                return;
            }
            if (type.getEntityClass() == null || !Mob.class.isAssignableFrom(type.getEntityClass())) {
                return;
            }
            final Mob boss = (Mob) world.spawn(loc, type.getEntityClass().asSubclass(Mob.class));
            EventSpawnGuard.prepare(boss);
            boss.setPersistent(true);
            boss.setRemoveWhenFarAway(false);
            mobScalingManager.forceLevel(boss, Math.max(1,
                    configManager.getInt("dungeon.minibosses." + id + ".level", 12)));
            final double healthMult = Math.max(1.0D, configManager.getDouble(
                    "dungeon.minibosses." + id + ".health-multiplier", 4.0D));
            final AttributeInstance health = boss.getAttribute(Attribute.MAX_HEALTH);
            if (health != null) {
                health.setBaseValue(health.getBaseValue() * healthMult);
                boss.setHealth(health.getValue());
            }
            final String name = configManager.getString("dungeon.minibosses." + id + ".name",
                    "A Mélység Őrzője");
            boss.customName(net.kyori.adventure.text.Component.text(name,
                    net.kyori.adventure.text.format.NamedTextColor.DARK_RED));
            boss.setCustomNameVisible(true);
            boss.getPersistentDataContainer().set(bossKey, PersistentDataType.STRING, id);
            bossAlive.put(id, boss.getUniqueId());
        });
    }

    /**
     * Boss-halál (a boss régió-szálán, EntityDeathEvent): loot a halál helyére,
     * respawn-óra indul. @return true, ha ez kazamata-boss volt.
     */
    public boolean handleBossDeath(final LivingEntity dead, final List<ItemStack> drops) {
        final String id = dead.getPersistentDataContainer().get(bossKey, PersistentDataType.STRING);
        if (id == null) {
            return false;
        }
        bossAlive.remove(id);
        bossRespawnAt.put(id, System.currentTimeMillis() + Math.max(1L,
                configManager.getLong("dungeon.minibosses." + id + ".respawn-hours", 24L)) * 3_600_000L);
        final BossSpawn spawn = bosses.get(id);
        final String table = spawn != null ? spawn.table()
                : configManager.getString("dungeon.minibosses." + id + ".table", "kazamata-boss");
        drops.addAll(rollTable(table, Math.max(1, configManager.getInt("dungeon.loot.boss-rolls", 4))));
        save();
        return true;
    }

    // ===== Táblák =====

    /** Súlyozott sorsolás a dungeon.loot-tables.<név> táblából, {@code rolls} húzással. */
    public List<ItemStack> rollTable(final String table, final int rolls) {
        final List<ItemStack> result = new ArrayList<>();
        if (configManager.getConfiguration() == null) {
            return result;
        }
        final List<Map<?, ?>> entries = configManager.getConfiguration()
                .getMapList("dungeon.loot-tables." + table.toLowerCase(Locale.ROOT));
        if (entries.isEmpty()) {
            return result;
        }
        int total = 0;
        for (final Map<?, ?> entry : entries) {
            total += toInt(entry.get("weight"), 1);
        }
        for (int i = 0; i < rolls; i++) {
            int roll = ThreadLocalRandom.current().nextInt(Math.max(1, total));
            Map<?, ?> chosen = entries.get(0);
            for (final Map<?, ?> entry : entries) {
                roll -= toInt(entry.get("weight"), 1);
                if (roll < 0) {
                    chosen = entry;
                    break;
                }
            }
            final int min = toInt(chosen.get("min"), 1);
            final int max = Math.max(min, toInt(chosen.get("max"), min));
            final int amount = min + ThreadLocalRandom.current().nextInt(max - min + 1);
            final Object typeValue = chosen.get("type");
            final String type = (typeValue == null ? "material" : String.valueOf(typeValue)).toLowerCase(Locale.ROOT);
            if ("unique".equals(type)) {
                final ItemStack item = uniqueMaterials.create(String.valueOf(chosen.get("id")), amount);
                if (item != null) {
                    result.add(item);
                }
            } else {
                final org.bukkit.Material material = org.bukkit.Material.matchMaterial(
                        String.valueOf(chosen.get("item")).toUpperCase(Locale.ROOT));
                if (material != null && !material.isAir()) {
                    result.add(new ItemStack(material, amount));
                }
            }
        }
        return result;
    }

    private static int toInt(final Object value, final int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (final NumberFormatException exception) {
            return fallback;
        }
    }

    // ===== Perzisztencia =====

    @Override
    public void load() {
        chests.clear();
        bosses.clear();
        chestClaims.clear();
        bossRespawnAt.clear();
        if (!storageFile.exists()) {
            return;
        }
        final org.bukkit.configuration.file.YamlConfiguration yaml =
                hu.taliann.icesmp.storage.YamlStore.loadTracked(storageFile, plugin.getLogger());
        final var chestSection = yaml.getConfigurationSection("chests");
        if (chestSection != null) {
            for (final String key : chestSection.getKeys(false)) {
                chests.put(key.replace('_', ';'), chestSection.getString(key, "kazamata"));
            }
        }
        final var bossSection = yaml.getConfigurationSection("bosses");
        if (bossSection != null) {
            for (final String id : bossSection.getKeys(false)) {
                bosses.put(id, new BossSpawn(
                        bossSection.getString(id + ".world", "world"),
                        bossSection.getDouble(id + ".x"),
                        bossSection.getDouble(id + ".y"),
                        bossSection.getDouble(id + ".z"),
                        bossSection.getString(id + ".table", "kazamata-boss")));
                final long respawnAt = bossSection.getLong(id + ".respawn-at", 0L);
                if (respawnAt > 0L) {
                    bossRespawnAt.put(id, respawnAt);
                }
            }
        }
        final var claimSection = yaml.getConfigurationSection("claims");
        if (claimSection != null) {
            for (final String key : claimSection.getKeys(false)) {
                chestClaims.put(key.replace('_', ';'), claimSection.getLong(key));
            }
        }
    }

    @Override
    public synchronized void save() {
        final org.bukkit.configuration.file.YamlConfiguration yaml =
                new org.bukkit.configuration.file.YamlConfiguration();
        for (final Map.Entry<String, String> entry : chests.entrySet()) {
            yaml.set("chests." + entry.getKey().replace(';', '_'), entry.getValue());
        }
        for (final Map.Entry<String, BossSpawn> entry : bosses.entrySet()) {
            final String base = "bosses." + entry.getKey();
            yaml.set(base + ".world", entry.getValue().world());
            yaml.set(base + ".x", entry.getValue().x());
            yaml.set(base + ".y", entry.getValue().y());
            yaml.set(base + ".z", entry.getValue().z());
            yaml.set(base + ".table", entry.getValue().table());
            final Long respawnAt = bossRespawnAt.get(entry.getKey());
            if (respawnAt != null) {
                yaml.set(base + ".respawn-at", respawnAt);
            }
        }
        // A lejárt claim-bejegyzések mentéskor söprődnek (a fájl nem hízik korlátlanul).
        final long maxAge = Math.max(1L, configManager.getLong(
                "dungeon.loot.chest-cooldown-hours", 168L)) * 3_600_000L;
        final long now = System.currentTimeMillis();
        chestClaims.entrySet().removeIf(entry -> now - entry.getValue() > maxAge);
        for (final Map.Entry<String, Long> entry : chestClaims.entrySet()) {
            yaml.set("claims." + entry.getKey().replace(';', '_'), entry.getValue());
        }
        try {
            hu.taliann.icesmp.storage.YamlStore.saveAtomic(storageFile, yaml);
        } catch (final java.io.IOException exception) {
            plugin.getLogger().severe("Failed to save dungeon-loot.yml: " + exception.getMessage());
        }
    }
}
