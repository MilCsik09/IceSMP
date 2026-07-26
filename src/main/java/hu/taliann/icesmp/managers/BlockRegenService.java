package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.storage.BlockRegenJournal;
import hu.taliann.icesmp.storage.PersistentStore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * "A világ visszagyógyul" — rombolás/robbanás után a blokkok pontosan az eredeti
 * állapotukba épülnek vissza, drop nélkül (nincs dupe-út). A védett zónák robbanásai
 * és az ostrom-rombolás így látványosan megtörténhetnek anélkül, hogy maradandó kárt
 * vagy zsákmányt adnának.
 *
 * Tile-entity blokkot csak bekapcsolt NBT-pillanatképpel veszünk fel; egyébként a hívó
 * köteles érintetlenül hagyni.
 *
 * A várólista írás-előre naplón (BlockRegenJournal) keresztül perzisztens: a pillanatkép
 * a konténer kiürítése ELŐTT kerül tartósan lemezre, és a rekord csak a sikeres
 * visszaépítés véglegesítése után törlődik. Így sem összeomlás, sem leállás nem vihet el
 * ládatartalmat, és nem marad örök lyuk a városfalban.
 */
public final class BlockRegenService implements PersistentStore {

    /** Kiosztott, de le nem futott régió-task felszabadítása (kirakott világ, leállás). */
    private static final long IN_FLIGHT_TIMEOUT_MILLIS = 60_000L;
    /** Halasztás, ha a pozíció még foglalt (élőlény) vagy nincs támasza. */
    private static final long RETRY_MILLIS = 1_000L;
    /** Halasztás, amíg a rekord világa nincs betöltve — a rekordot SOSEM dobjuk el. */
    private static final long WORLD_WAIT_MILLIS = 30_000L;

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final BlockRegenJournal journal;
    private final Queue<BlockRegenJournal.Record> queue = new ConcurrentLinkedQueue<>();
    /** id → kiosztás ideje: a régió-taskra adott rekord nem osztható ki újra. */
    private final Map<Long, Long> inFlight = new ConcurrentHashMap<>();
    /** id → újrapróbálás ideje: a halasztott rekord nem foglalja a menet-kvótát. */
    private final Map<Long, Long> retryAfter = new ConcurrentHashMap<>();
    /** Világonként egyszer naplózunk hiányzó világot — különben a tick elárasztja a logot. */
    private final java.util.Set<String> missingWorldLogged = ConcurrentHashMap.newKeySet();
    /**
     * Már visszaépült rekordok. A világ-mutáció NEM ismételhető: napló-hiba után csak a
     * véglegesítést próbáljuk újra. Enélkül a `commit` bukása a TELJES régió-taskot osztotta
     * ki újra, amely feltétel nélkül újra lerakta az eredeti NBT-t — írásvédett data-könyvtár
     * vagy tele lemez mellett a láda másodpercenként újratöltődött (korlátlan tárgy-duplikáció).
     * Ugyanez zárja a késve lefutó, beragadt régió-taskot is: az nem írja át másodszor a blokkot.
     */
    private final java.util.Set<Long> restored = ConcurrentHashMap.newKeySet();

    public BlockRegenService(final JavaPlugin plugin, final ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.journal = new BlockRegenJournal(plugin.getDataFolder(), plugin.getLogger());
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
        return capture(block, delayMillis, true);
    }

    /** A kézi (ostrom-)bontás loopGuarded=false-szal hívja: a szándékos újra-bontás nem hurok. */
    public boolean capture(final Block block, final long delayMillis, final boolean loopGuarded) {
        // TNT sosem kerül a sorba: lánc-robbanásban elfogy, visszaépítve ingyen-TNT +
        // végtelen robbanás-hurok lenne. (A listában marad, tehát a lánc él.)
        if (block.getType() == org.bukkit.Material.TNT) {
            return false;
        }
        if (isPending(block)) {
            return true; // már sorban áll (pl. robbanás + fizika-esemény dupla-jelzése)
        }
        if (loopGuarded && isRecaptureLooping(block)) {
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
            // Írás-előre napló: a pillanatkép ELŐBB tartósan (fsync-kelve) lemezre kerül,
            // és CSAK utána ürül a konténer. A kiürítés után a tartalom egyetlen példánya
            // a rekord, ezért napló-hiba esetén inkább nem robban a láda, mint hogy a
            // tartalma nyomtalanul elvesszen.
            final BlockRegenJournal.Record tileRecord = newRecord(block, extra, delayMillis);
            if (!journal.appendPending(tileRecord, true)) {
                return false;
            }
            // Dupla ládánál CSAK a saját fél ürülhet — a getInventory() a közös
            // inventoryt adná, és a túlélő fél tartalma is elveszne.
            // getState(false): az ÉLŐ állapotot ürítjük, nem egy pillanatkép-másolatot —
            // különben a robbanás a valódi tartalmat szórná ki (dupe a visszaépítéssel).
            if (block.getState(false) instanceof org.bukkit.block.Chest chest) {
                chest.getBlockInventory().clear();
            } else if (block.getState(false) instanceof org.bukkit.inventory.InventoryHolder holder) {
                holder.getInventory().clear();
            }
            enqueue(tileRecord, block);
            return true;
        }
        // Sima blokknál a napló-bejegyzés hozzáfűzése elég (fsync nélkül): a blokk-adat
        // nem megsemmisülő tartalom, egy elveszett jelölés legfeljebb ismételt
        // visszaépítést okoz, a robbanás forró útja pedig nem áll meg lemez-szinkronra.
        final BlockRegenJournal.Record record = newRecord(block, null, delayMillis);
        if (!journal.appendPending(record, false)) {
            return false;
        }
        enqueue(record, block);
        return true;
    }

    private BlockRegenJournal.Record newRecord(final Block block, final String extra, final long delayMillis) {
        return new BlockRegenJournal.Record(journal.nextId(), block.getWorld().getName(),
                block.getX(), block.getY(), block.getZ(), block.getBlockData().getAsString(),
                extra, System.currentTimeMillis() + delayMillis);
    }

    private void enqueue(final BlockRegenJournal.Record record, final Block block) {
        queue.add(record);
        pendingShield.add(posKey(block));
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
    /** A frissen VISSZAÉPÍTETT blokk időzített pajzsa (fáklya-védelem a fal záródásáig). */
    public boolean isRestoredShielded(final Block block) {
        if (physicsShield.isEmpty() || !isShieldEnabled()) {
            return false; // gyors-út: pajzs nélkül a sűrű physics-event ára nulla
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

    /**
     * Kráter-pozíció (visszaépülésre vár): a folyadék BEfolyhat (természetes látvány),
     * de innen TOVÁBB nem terjedhet — a fal mögötti eredeti légterek nem ázhatnak el.
     */
    public boolean isCraterPos(final Block block) {
        return !pendingShield.isEmpty() && isShieldEnabled() && pendingShield.contains(posKey(block));
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
        final long[] entry = captureHistory.computeIfAbsent(posKey(block), k -> new long[]{0L, now});
        synchronized (entry) {
            if (now - entry[1] > windowMillis) {
                entry[0] = 0L;
                entry[1] = now;
            }
            entry[0]++;
            return entry[0] > maxRecaptures;
        }
    }

    /** Pozíció-alapú dedupe O(1)-ben — a pendingShield pontosan a sorban álló pozíciók halmaza. */
    public boolean isPending(final Block block) {
        return pendingShield.contains(posKey(block));
    }

    private static String posKey(final BlockRegenJournal.Record e) {
        return e.world() + ';' + e.x() + ';' + e.y() + ';' + e.z();
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
        // Lejárt pajzs/history bejegyzések periodikus seprése — a forró eseménykezelő
        // utakról kikerült minden takarítás.
        physicsShield.values().removeIf(until -> until <= now);
        final long historyWindow = Math.max(30L, configManager.getLong(
                "territory.protection.regen.recapture-window-seconds", 600L)) * 1000L;
        captureHistory.values().removeIf(v -> now - v[1] > historyWindow);
        retryAfter.values().removeIf(until -> until <= now);
        // A régió-task elmaradhat (kirakott világ, leállás alatti kiosztás); a visszaépítés
        // idempotens, ezért a beragadt kiosztás felszabadítása és újrapróbálása biztonságos.
        inFlight.values().removeIf(since -> now - since > IN_FLIGHT_TIMEOUT_MILLIS);
        final List<BlockRegenJournal.Record> due = new ArrayList<>();
        for (final BlockRegenJournal.Record e : queue) {
            if (e.restoreAt() <= now && !inFlight.containsKey(e.id()) && !retryAfter.containsKey(e.id())) {
                due.add(e);
                if (due.size() >= blocksPerPass()) {
                    break;
                }
            }
        }
        if (due.isEmpty()) {
            return;
        }
        // Alulról felfelé: a gravitációs blokk (homok, kavics) nem hullik ki a fal aljából.
        due.sort(Comparator.comparingInt(BlockRegenJournal.Record::y));
        for (final BlockRegenJournal.Record e : due) {
            final World world = Bukkit.getWorld(e.world());
            if (world == null) {
                // A világ nincs (még) betöltve: a rekord MARAD — eldobva a fal véglegesen
                // lyukas maradna, a ládatartalom pedig visszahozhatatlan lenne.
                retryAfter.put(e.id(), now + WORLD_WAIT_MILLIS);
                if (missingWorldLogged.add(e.world())) {
                    plugin.getLogger().warning("A(z) " + e.world() + " világ nincs betöltve — a "
                            + "blokk-visszaépítés vár rá (a rekordok megmaradnak).");
                }
                continue;
            }
            if (!missingWorldLogged.isEmpty()) {
                missingWorldLogged.remove(e.world()); // újratöltött világ újra naplózhat
            }
            final Location loc = new Location(world, e.x(), e.y(), e.z());
            inFlight.put(e.id(), now);
            journal.markApplying(e.id());
            Bukkit.getRegionScheduler().run(plugin, loc, task -> {
                // Már véglegesített rekord (pl. késve lefutó, beragadt task): nincs mit tenni.
                if (!queue.contains(e)) {
                    inFlight.remove(e.id());
                    retryAfter.remove(e.id());
                    return;
                }
                // Már visszaépült, csak a napló-véglegesítés maradt el: CSAK azt próbáljuk
                // újra — a világ-mutáció ismétlése konténernél tárgy-duplikáció lenne.
                if (restored.contains(e.id())) {
                    if (!commit(e)) {
                        defer(e);
                    }
                    return;
                }
                try {
                    final org.bukkit.block.data.BlockData data = Bukkit.createBlockData(e.blockData());
                    final Block target = world.getBlockAt(e.x(), e.y(), e.z());
                    // Befalazás-védelem: élőlényre (játékosra!) sosem építünk rá —
                    // amíg valaki a pozícióban áll, a blokk várakozik.
                    if (!target.getLocation().toCenterLocation().getNearbyLivingEntities(0.9D).isEmpty()) {
                        defer(e);
                        return;
                    }
                    // Támasz-ellenőrzés: gravitációs blokk csak szilárd alapra, rátett
                    // blokk (fáklya, tábla, gomb…) csak létező támaszra kerül vissza —
                    // különben a következő fizika-frissítés leejtené/lepattintaná.
                    // Amíg nincs támasz, halasztjuk; a grace lejárta után mindenképp
                    // visszakerül (a sor nem ragadhat be körkörös függésen).
                    if (now - e.restoreAt() <= supportGraceMillis()) {
                        final boolean unsupported = data.getMaterial().hasGravity()
                                ? !target.getRelative(org.bukkit.block.BlockFace.DOWN).isSolid()
                                : !data.isSupported(loc);
                        if (unsupported) {
                            defer(e);
                            return;
                        }
                    }
                    // Mindig felülírunk: a világ PONTOSAN a rombolás előtti állapotba tér
                    // vissza (a közben odarakott blokk drop nélkül tűnik el — hadszíntér).
                    target.setBlockData(data, false);
                    restoreExtra(target, e.extra());
                    restored.add(e.id());
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
                    // A rekord CSAK a sikeres visszaépítés ÉS a napló véglegesítő írása
                    // után kerül ki a sorból: e kettő között bármikor megszakadhat a
                    // folyamat, a replay ilyenkor idempotensen újrafuttatja.
                    if (!commit(e)) {
                        defer(e);
                    }
                } catch (final IllegalArgumentException invalid) {
                    // Érvénytelenné vált blockdata (pl. verzióváltás): ez a rekord SOSEM
                    // épülhet vissza, ezért véglegesítjük — különben örökké újrapróbálná.
                    // A tile-entity pillanatképe ilyenkor elveszik: a log NEVEZZE MEG a
                    // helyet, hogy az admin kézzel pótolhassa a láda tartalmát.
                    plugin.getLogger().warning("Visszaépíthetetlen blokk-adat kihagyva ("
                            + e.blockData() + " @ " + e.world() + " " + e.x() + "," + e.y() + "," + e.z()
                            + (e.extra() == null ? "" : "; a tárolt konténer-tartalom ELVESZIK")
                            + "): " + invalid.getMessage());
                    if (!commit(e)) {
                        defer(e);
                    }
                }
            });
        }
    }

    /**
     * Véglegesítés: a napló APPLIED-jelölése után a rekord kikerül a sorból. Napló-hiba
     * esetén false — a rekord marad, a visszaépítés (idempotens) újrapróbálódik.
     */
    private boolean commit(final BlockRegenJournal.Record record) {
        if (!journal.markApplied(record)) {
            return false;
        }
        queue.remove(record);
        pendingShield.remove(posKey(record));
        inFlight.remove(record.id());
        retryAfter.remove(record.id());
        restored.remove(record.id());
        return true;
    }

    /** Halasztás: a retryAfter ELŐBB áll be, különben a tick azonnal újra kiosztaná. */
    private void defer(final BlockRegenJournal.Record record) {
        retryAfter.put(record.id(), System.currentTimeMillis() + RETRY_MILLIS);
        inFlight.remove(record.id());
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
    }

    /**
     * A napló replay-e: a félbehagyott (APPLYING) rekord ugyanúgy sorba kerül, mint a
     * PENDING — a visszaépítés idempotens, ezért az újrafutás ugyanabba az állapotba visz.
     */
    @Override
    public void load() {
        queue.clear();
        pendingShield.clear();
        inFlight.clear();
        retryAfter.clear();
        restored.clear();
        // /icesmp reload után a hiányzó világ újra figyelmeztethet (különben egyszer szólt,
        // aztán soha).
        missingWorldLogged.clear();
        for (final BlockRegenJournal.Record record : journal.loadAll()) {
            queue.add(record);
            pendingShield.add(posKey(record));
        }
    }

    /**
     * Checkpoint + napló-rotáció. Leállításkor ez a drain: MINDEN élő rekord (a már
     * kiosztott, de le nem futott visszaépítések is) a checkpointba kerül, mert a sorból
     * csak a véglegesített rekord tűnik el.
     */
    @Override
    public void save() {
        try {
            // A sort MAGÁT adjuk át: a pillanatkép a napló zárolása alatt készül.
            journal.checkpoint(queue);
        } catch (final java.io.IOException e) {
            plugin.getLogger().severe("block-regen checkpoint mentési hiba: " + e);
        }
    }
}
