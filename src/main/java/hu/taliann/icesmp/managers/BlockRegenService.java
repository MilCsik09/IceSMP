package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.storage.PersistentStore;
import hu.taliann.icesmp.storage.YamlStore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * "A világ visszagyógyul" — rombolás/robbanás után a blokkok pontosan az eredeti
 * állapotukba épülnek vissza, drop nélkül (nincs dupe-út). A védett zónák robbanásai
 * és az ostrom-rombolás így látványosan megtörténhetnek anélkül, hogy maradandó kárt
 * vagy zsákmányt adnának.
 *
 * Tile-entity blokkot (láda, kemence, spawner…) SOSEM veszünk fel: azok tartalmát nem
 * pillanatképezzük, ezért azokat a hívó köteles érintetlenül hagyni.
 *
 * A várólista perzisztens (block-regen.yml): restart közben esedékessé váló
 * visszaépítés sem vész el — nem marad örök lyuk a városfalban.
 */
public final class BlockRegenService implements PersistentStore {



    private record Entry(String world, int x, int y, int z, String blockData, String extra, long restoreAt) {
    }

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final File storageFile;
    private final Queue<Entry> queue = new ConcurrentLinkedQueue<>();

    public BlockRegenService(final JavaPlugin plugin, final ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.storageFile = new File(plugin.getDataFolder(), "block-regen.yml");
    }

    public boolean isEnabled() {
        return configManager.getBoolean("territory.protection.regen.enabled", true);
    }

    /**
     * Zónánkénti regen-kapcsoló (territory.protection.regen.zones.<típus>): védett
     * zónákban alapból BE, frakcióföldön és a vadonban alapból KI.
     */
    public boolean isZoneRegenEnabled(final String zoneKey) {
        final boolean def = !"wilderness".equals(zoneKey) && !"faction".equals(zoneKey);
        return configManager.getBoolean("territory.protection.regen.zones." + zoneKey, def);
    }

    public long explosionDelayMillis() {
        return Math.max(5L, configManager.getLong("territory.protection.regen.delay-seconds", 180L)) * 1000L;
    }

    /** Hány tickenként fut a visszaépítő menet (indításkor olvasott kulcs). */
    public long restoreIntervalTicks() {
        return Math.max(1L, configManager.getLong("territory.protection.regen.restore-interval-ticks", 10L));
    }

    /** Menetenként ennyi blokk kerül vissza — ez adja a visszaépülés "tempóját". */
    public int blocksPerPass() {
        return Math.max(1, configManager.getInt("territory.protection.regen.blocks-per-pass", 3));
    }

    /** Ennyi mp várakozás után a támasz nélküli blokk is visszakerül (sor-beragadás ellen). */
    private long supportGraceMillis() {
        return Math.max(5L, configManager.getLong(
                "territory.protection.regen.support-grace-seconds", 120L)) * 1000L;
    }

    public boolean isSiegeBreakEnabled() {
        return configManager.getBoolean("territory.protection.regen.player-break.siege-enabled", true);
    }

    public long siegeBreakDelayMillis() {
        return Math.max(5L, configManager.getLong(
                "territory.protection.regen.player-break.siege-delay-seconds", 300L)) * 1000L;
    }

    public boolean isAlwaysBreakEnabled() {
        return configManager.getBoolean("territory.protection.regen.player-break.always-enabled", false);
    }

    public long alwaysBreakDelayMillis() {
        return Math.max(5L, configManager.getLong(
                "territory.protection.regen.player-break.always-delay-seconds", 120L)) * 1000L;
    }

    /**
     * Felveszi a blokkot a visszaépülési sorba a JELENLEGI állapotával. Tile-entity
     * blokkra false — azt a hívó ne engedje elpusztulni.
     */
    public boolean capture(final Block block, final long delayMillis) {
        // TNT sosem kerül a sorba: lánc-robbanásban elfogy, visszaépítve ingyen-TNT +
        // végtelen robbanás-hurok lenne. (A listában marad, tehát a lánc él.)
        if (block.getType() == org.bukkit.Material.TNT) {
            return false;
        }
        if (isQueued(block)) {
            return true; // már sorban áll (pl. robbanás + fizika-esemény dupla-jelzése)
        }
        if (isRecaptureLooping(block)) {
            return false; // valami folyton újrarombolja (pl. vízfolyás) — elengedjük
        }
        if (block.getState() instanceof TileState) {
            if (!isTileEntityExplodeEnabled()) {
                return false;
            }
            // Generikus NBT-út: 1x1x1 struktúra-pillanatkép — a blokk TELJES NBT-jét
            // viszi (láda/shulker-tartalom, tábla-szöveg, fej-textúra, zászló-minta,
            // spawner-beállítás, lektorna-könyv…), verziófüggetlen szerializálással.
            final String extra;
            try {
                final org.bukkit.structure.Structure snap = Bukkit.getStructureManager().createStructure();
                snap.fill(block.getLocation(), new org.bukkit.util.BlockVector(1, 1, 1), false);
                final java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                Bukkit.getStructureManager().saveStructure(bytes, snap);
                extra = "nbt:" + java.util.Base64.getEncoder().encodeToString(bytes.toByteArray());
            } catch (final java.io.IOException | RuntimeException ex) {
                plugin.getLogger().warning("Tile-entity pillanatkép hiba (" + block.getType() + "): " + ex);
                return false; // pillanatkép nélkül inkább rúna-védelem, mint adatvesztés
            }
            // A robbanás ne szórja ki a tartalmat: a pillanatkép UTÁN kiürítjük.
            // Dupla ládánál CSAK a saját fél ürülhet — a getInventory() a közös
            // inventoryt adná, és a túlélő fél tartalma is elveszne.
            if (block.getState() instanceof org.bukkit.block.Chest chest) {
                chest.getBlockInventory().clear();
            } else if (block.getState() instanceof org.bukkit.inventory.InventoryHolder holder) {
                holder.getInventory().clear();
            }
            queue.add(new Entry(block.getWorld().getName(), block.getX(), block.getY(), block.getZ(),
                    block.getBlockData().getAsString(), extra, System.currentTimeMillis() + delayMillis));
            pendingShield.add(posKey(block));
            return true;
        }
        queue.add(new Entry(block.getWorld().getName(), block.getX(), block.getY(), block.getZ(),
                block.getBlockData().getAsString(), null, System.currentTimeMillis() + delayMillis));
        pendingShield.add(posKey(block));
        return true;
    }

    /** pozíció → pajzs lejárta — a frissen visszaépített blokkot a fizika nem bánthatja. */
    private final java.util.Map<String, Long> physicsShield = new java.util.concurrent.ConcurrentHashMap<>();
    /** A sorban álló (kráter-) pozíciók gyors-lookup másolata — a pajzs rájuk is kiterjed. */
    private final java.util.Set<String> pendingShield = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** A pajzs-rendszer fő-kapcsolója — kikapcsolva a régi megoldás (hurok-fék) él. */
    private boolean isShieldEnabled() {
        return configManager.getBoolean("territory.protection.regen.physics-shield-enabled", true);
    }

    private static String posKey(final Block block) {
        return block.getWorld().getName() + ';' + block.getX() + ';' + block.getY() + ';' + block.getZ();
    }

    /** A visszaépített blokk fizika-pajzsot kap ennyi mp-re (0 = nincs pajzs). */
    private long physicsShieldMillis() {
        return Math.max(0L, configManager.getLong(
                "territory.protection.regen.physics-shield-seconds", 300L)) * 1000L;
    }

    /**
     * Igaz, ha a pozíció fizika-pajzs alatt áll: a rá ható fizika-eseményeket
     * (frissítés, folyadék-befolyás, fizika-törés) a listener cancel-eli — a
     * visszaépített fáklyát a víz el sem érheti, a homok nem eshet le.
     */
    public boolean isPhysicsShielded(final Block block) {
        if (physicsShield.isEmpty() && pendingShield.isEmpty()) {
            return false; // gyors-út: pajzs nélkül a sűrű physics-event ára nulla
        }
        if (!isShieldEnabled()) {
            return false;
        }
        // A kráter (visszaépülésre váró pozíció) is pajzsolt: a víz nem folyhat a
        // lyukba, a perem-homok nem omolhat be, mielőtt a fal visszaépül.
        if (pendingShield.contains(posKey(block))) {
            return true;
        }
        final Long until = physicsShield.get(posKey(block));
        if (until == null) {
            return false;
        }
        if (until <= System.currentTimeMillis()) {
            physicsShield.remove(posKey(block));
            return false;
        }
        return true;
    }

    /** pozíció → [felvételek száma, ablak kezdete] — az újrarombolási hurok féke. */
    private final java.util.Map<String, long[]> captureHistory = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Igaz, ha a pozíció rövid időn belül túl sokszor került a sorba — ilyenkor a
     * visszaépítés feladja (pl. fáklyát folyton elmos a víz), különben capture→restore→
     * rombolás→capture végtelen kör pörögne.
     */
    private boolean isRecaptureLooping(final Block block) {
        final long windowMillis = Math.max(30L, configManager.getLong(
                "territory.protection.regen.recapture-window-seconds", 600L)) * 1000L;
        final int maxRecaptures = Math.max(1, configManager.getInt(
                "territory.protection.regen.max-recaptures", 3));
        final long now = System.currentTimeMillis();
        captureHistory.values().removeIf(v -> now - v[1] > windowMillis);
        final String key = block.getWorld().getName() + ';' + block.getX() + ';' + block.getY() + ';' + block.getZ();
        final long[] entry = captureHistory.computeIfAbsent(key, k -> new long[]{0L, now});
        entry[0]++;
        return entry[0] > maxRecaptures;
    }

    /** Pozíció-alapú dedupe: ugyanaz a blokk nem kerülhet kétszer a sorba. */
    private boolean isQueued(final Block block) {
        final String w = block.getWorld().getName();
        for (final Entry e : queue) {
            if (e.x() == block.getX() && e.y() == block.getY() && e.z() == block.getZ()
                    && e.world().equals(w)) {
                return true;
            }
        }
        return false;
    }

    /** Tile-entity robbanás (NBT-pillanatképpel) — alapból KI, a rúna-védelem él. */
    public boolean isTileEntityExplodeEnabled() {
        return configManager.getBoolean("territory.protection.regen.tile-entity-explode", false);
    }

    /** A robbanást túlélő tile-entity "óvó rúnái" — látvány+hang, hogy ne tűnjön bugnak. */
    public void playWardEffect(final Block block) {
        final Location fx = block.getLocation().add(0.5D, 0.5D, 0.5D);
        block.getWorld().spawnParticle(org.bukkit.Particle.ENCHANT, fx, 25, 0.4D, 0.4D, 0.4D, 0.5D);
        block.getWorld().playSound(fx, org.bukkit.Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.7F, 1.6F);
    }

    /** Igaz, ha a blokk tile-entity — robbanás-listából eleve ki kell venni. */
    public static boolean isTileEntity(final Block block) {
        return block.getState() instanceof TileState;
    }

    /** A törmelék-entitások jelölése — landoláskor porladnak, sosem raknak le blokkot. */
    public static final String DEBRIS_TAG = "icesmp_debris";

    /**
     * Kozmetikai törmelék: a kirobbant blokk másolata FallingBlockként repül ki a
     * robbanás középpontjából — pattog/csúszik a vanília fizikával, majd pár másodperc
     * után porfelhővel eltűnik. Tisztán látvány, se blokk-lerakás, se drop.
     */
    public void spawnDebris(final Block block, final Location center) {
        if (!configManager.getBoolean("territory.protection.regen.debris-enabled", true)) {
            return;
        }
        // Csak a blokkok debris-percent %-a válik repülő törmelékké (látvány-sűrűség fék).
        final double percent = configManager.getDouble("territory.protection.regen.debris-percent", 100.0D);
        if (Math.random() * 100.0D >= percent) {
            return;
        }
        final Location from = block.getLocation().add(0.5D, 0.5D, 0.5D);
        final org.bukkit.util.Vector dir = from.toVector().subtract(center.toVector());
        if (dir.lengthSquared() < 0.01D) {
            dir.setY(1.0D);
        }
        final double power = configManager.getDouble("territory.protection.regen.debris-launch-power", 0.6D);
        final org.bukkit.entity.FallingBlock debris = block.getWorld().spawnFallingBlock(from, block.getBlockData());
        debris.setDropItem(false);
        debris.setCancelDrop(true);
        debris.addScoreboardTag(DEBRIS_TAG);
        debris.setVelocity(dir.normalize().multiply(power)
                .add(new org.bukkit.util.Vector(0.0D, 0.35D + Math.random() * 0.2D, 0.0D)));
        final long lifetimeTicks = Math.max(20L,
                configManager.getLong("territory.protection.regen.debris-lifetime-seconds", 4L) * 20L);
        debris.getScheduler().runDelayed(plugin, task -> {
            if (debris.isValid()) {
                debris.getWorld().spawnParticle(org.bukkit.Particle.BLOCK_CRUMBLE,
                        debris.getLocation(), 12, 0.2D, 0.2D, 0.2D, block.getBlockData());
                debris.remove();
            }
        }, null, lifetimeTicks);
    }

    /** A globál-tickről hívva: az esedékes blokkok visszaépítése (alulról felfelé). */
    public void tick() {
        final long now = System.currentTimeMillis();
        final List<Entry> due = new ArrayList<>();
        for (final Entry e : queue) {
            if (e.restoreAt() <= now) {
                due.add(e);
                if (due.size() >= blocksPerPass()) {
                    break;
                }
            }
        }
        if (due.isEmpty()) {
            return;
        }
        queue.removeAll(due);
        // Alulról felfelé: a gravitációs blokk (homok, kavics) nem hullik ki a fal aljából.
        due.sort(Comparator.comparingInt(Entry::y));
        for (final Entry e : due) {
            final World world = Bukkit.getWorld(e.world());
            if (world == null) {
                pendingShield.remove(e.world() + ';' + e.x() + ';' + e.y() + ';' + e.z());
                continue;
            }
            final Location loc = new Location(world, e.x(), e.y(), e.z());
            Bukkit.getRegionScheduler().run(plugin, loc, task -> {
                try {
                    final org.bukkit.block.data.BlockData data = Bukkit.createBlockData(e.blockData());
                    final Block target = world.getBlockAt(e.x(), e.y(), e.z());
                    // Támasz-ellenőrzés: gravitációs blokk csak szilárd alapra, rátett
                    // blokk (fáklya, tábla, gomb…) csak létező támaszra kerül vissza —
                    // különben a következő fizika-frissítés leejtené/lepattintaná.
                    // Amíg nincs támasz, a sor végére kerül; a grace lejárta után
                    // mindenképp visszakerül (a sor nem ragadhat be körkörös függésen).
                    if (now - e.restoreAt() <= supportGraceMillis()) {
                        final boolean unsupported = data.getMaterial().hasGravity()
                                ? !target.getRelative(org.bukkit.block.BlockFace.DOWN).isSolid()
                                : !data.isSupported(loc);
                        if (unsupported) {
                            queue.add(new Entry(e.world(), e.x(), e.y(), e.z(),
                                    e.blockData(), e.extra(), e.restoreAt()));
                            return;
                        }
                    }
                    // Mindig felülírunk: a világ PONTOSAN a rombolás előtti állapotba tér
                    // vissza (a közben odarakott blokk drop nélkül tűnik el — hadszíntér).
                    target.setBlockData(data, false);
                    restoreExtra(target, e.extra());
                    pendingShield.remove(posKey(target));
                    final long shield = physicsShieldMillis();
                    if (shield > 0L) {
                        physicsShield.put(posKey(target), System.currentTimeMillis() + shield);
                    }
                    if (configManager.getBoolean("territory.protection.regen.restore-effects-enabled", true)) {
                        // Anyag-hű "gyógyulás": a blokk saját lerakás-hangja + kis porfelhő.
                        final Location fx = loc.clone().add(0.5D, 0.5D, 0.5D);
                        world.playSound(fx, data.getSoundGroup().getPlaceSound(), 0.6F,
                                0.8F + (float) (Math.random() * 0.4D));
                        world.spawnParticle(org.bukkit.Particle.CLOUD, fx, 4, 0.25D, 0.25D, 0.25D, 0.01D);
                    }
                } catch (final IllegalArgumentException ignored) {
                    // Érvénytelenné vált blockdata (pl. verzióváltás) — kihagyjuk.
                    pendingShield.remove(e.world() + ';' + e.x() + ';' + e.y() + ';' + e.z());
                }
            });
        }
    }

    /** A tile-entity pillanatkép visszatöltése (konténer-tartalom / tábla-szöveg). */
    private void restoreExtra(final Block block, final String extra) {
        if (extra == null) {
            return;
        }
        if (extra.startsWith("nbt:")) {
            try {
                final org.bukkit.structure.Structure snap = Bukkit.getStructureManager().loadStructure(
                        new java.io.ByteArrayInputStream(java.util.Base64.getDecoder().decode(extra.substring(4))));
                snap.place(block.getLocation(), false, org.bukkit.block.structure.StructureRotation.NONE,
                        org.bukkit.block.structure.Mirror.NONE, 0, 1.0F, new java.util.Random());
            } catch (final java.io.IOException | RuntimeException ex) {
                plugin.getLogger().warning("Tile-entity visszaállítás hiba: " + ex);
            }
            return;
        }
        final org.bukkit.block.BlockState state = block.getState();
        if (extra.startsWith("inv:") && state instanceof org.bukkit.block.Container container) {
            container.getInventory().setContents(org.bukkit.inventory.ItemStack.deserializeItemsFromBytes(
                    java.util.Base64.getDecoder().decode(extra.substring(4))));
        } else if (extra.startsWith("sign:") && state instanceof org.bukkit.block.Sign sign) {
            final String[] parts = extra.substring(5).split("\u0001", -1);
            for (int i = 0; i < parts.length && i < 4; i++) {
                sign.getSide(org.bukkit.block.sign.Side.FRONT).line(i,
                        net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson()
                                .deserialize(parts[i]));
            }
            sign.update(true, false);
        }
    }

    @Override
    public void load() {
        queue.clear();
        pendingShield.clear();
        if (!storageFile.exists()) {
            return;
        }
        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(storageFile);
        for (final java.util.Map<?, ?> raw : yaml.getMapList("pending")) {
            try {
                queue.add(new Entry(String.valueOf(raw.get("world")),
                        ((Number) raw.get("x")).intValue(), ((Number) raw.get("y")).intValue(),
                        ((Number) raw.get("z")).intValue(), String.valueOf(raw.get("data")),
                        raw.get("extra") == null ? null : String.valueOf(raw.get("extra")),
                        ((Number) raw.get("at")).longValue()));

            } catch (final RuntimeException ignored) {
                // Sérült sor — a többi bejegyzés attól még betölt.
            }
        }
        for (final Entry e : queue) {
            pendingShield.add(e.world() + ';' + e.x() + ';' + e.y() + ';' + e.z());
        }
    }

    @Override
    public void save() {
        final YamlConfiguration yaml = new YamlConfiguration();
        final List<java.util.Map<String, Object>> out = new ArrayList<>();
        for (final Entry e : queue) {
            final java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("world", e.world());
            row.put("x", e.x());
            row.put("y", e.y());
            row.put("z", e.z());
            row.put("data", e.blockData());
            if (e.extra() != null) {
                row.put("extra", e.extra());
            }
            row.put("at", e.restoreAt());
            out.add(row);
        }
        yaml.set("pending", out);
        try {
            YamlStore.saveAtomic(storageFile, yaml);
        } catch (final java.io.IOException e) {
            plugin.getLogger().severe("block-regen.yml mentési hiba: " + e);
        }
    }
}
