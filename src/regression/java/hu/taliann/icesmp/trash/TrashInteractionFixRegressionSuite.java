package hu.taliann.icesmp.trash;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.*;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.persistence.*;
import org.bukkit.plugin.java.JavaPlugin;
import io.papermc.paper.event.player.PlayerStopUsingItemEvent;
import io.papermc.paper.threadedregions.scheduler.*;

/** Real event handlers and catalog transitions; only owner I/O and scheduling are controlled doubles. */
public final class TrashInteractionFixRegressionSuite {
    private static int assertions;
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message); assertions++;
    }
    public static void main(String[] args) throws Exception {
        // Item-only materials have no block type; this fixture has no server registry.
        Field blockType = Material.class.getDeclaredField("blockType");
        blockType.setAccessible(true);
        for (Material material : List.of(Material.BRUSH, Material.STICK, Material.PAPER))
            blockType.set(material, (java.util.function.Supplier<org.bukkit.block.BlockType>) () -> null);

        final EventHandler registration = TrashArchaeologyListener.class.getMethod("onInteract", PlayerInteractEvent.class)
                .getAnnotation(EventHandler.class);
        check(!registration.ignoreCancelled(), "predicted no-op air events must reach inspection");
        check(registration.priority() == org.bukkit.event.EventPriority.LOWEST, "inspection must suppress native item use first");
        for (EquipmentSlot brush : List.of(EquipmentSlot.HAND, EquipmentSlot.OFF_HAND)) {
            for (Action action : List.of(Action.RIGHT_CLICK_AIR, Action.RIGHT_CLICK_BLOCK)) {
                Fixture f = new Fixture(brush);
                PlayerInteractEvent first = f.click(action, EquipmentSlot.HAND);
                first.setCancelled(true);
                f.listener.onInteract(first);
                check(f.using == null, "inspection started native world-brushing ticks");
                check(first.useItemInHand() == Event.Result.DENY && first.useInteractedBlock() == Event.Result.DENY,
                        "inspection allowed eating, placement or block use");
                final Tick initial = f.tick;
                f.listener.onInteract(f.click(action, EquipmentSlot.OFF_HAND));
                check(f.tick == initial, "paired hand packet restarted inspection");
                f.hold(29);
                check(f.inspections == 0 && !initial.cancelled, "inspection completed before 30 ticks");
                f.hold(3);
                check(f.inspections == 1 && !initial.cancelled, "held input after 30 ticks did not complete exactly once");
                check(f.bridge.shownHand == (brush == EquipmentSlot.HAND ? EquipmentSlot.OFF_HAND : EquipmentSlot.HAND),
                        "tooltip was sent to the wrong canonical slot");
                check(f.main.getAmount() == 1 && f.off.getAmount() == 1, "inspection mutated inventory");
                f.hold(40);
                check(f.inspections == 1, "continued hold restarted completed inspection");
                f.advance(9);
                check(initial.cancelled, "released completed gesture leaked a task");
            }
            Fixture stopped = new Fixture(brush);
            stopped.listener.onInteract(stopped.click(Action.RIGHT_CLICK_AIR, EquipmentSlot.HAND));
            stopped.hold(12);
            stopped.listener.onStopUsing(new PlayerStopUsingItemEvent(stopped.player,
                    brush == EquipmentSlot.HAND ? stopped.main : stopped.off, 12));
            stopped.advance(30);
            check(stopped.inspections == 0 && stopped.tick.cancelled, "early release awarded an inspection");
            Fixture released = new Fixture(brush);
            released.listener.onInteract(released.click(Action.RIGHT_CLICK_AIR, EquipmentSlot.HAND));
            released.hold(28);
            released.advance(30);
            check(released.inspections == 0 && released.tick.cancelled, "idle timeout awarded an inspection without a final pulse");
            Fixture single = new Fixture(brush);
            single.listener.onInteract(single.click(Action.RIGHT_CLICK_AIR, EquipmentSlot.HAND));
            single.advance(40);
            check(single.inspections == 0 && single.tick.cancelled, "single click counted as holding");
            Fixture changed = new Fixture(brush);
            changed.listener.onInteract(changed.click(Action.RIGHT_CLICK_AIR, EquipmentSlot.HAND));
            if (brush == EquipmentSlot.HAND) changed.off = new TestItem(Material.PAPER, 1);
            else changed.main = new TestItem(Material.PAPER, 1);
            changed.advance(30);
            check(changed.inspections == 0 && changed.tick.cancelled, "swapped target was analysed");
        }
        catalogEvidence();
        vendorAbsenceRequiresReceipt();
        protectsSitesWithoutBlockingTheirBrush();
        System.out.println("Trash interaction fix regression suite passed. assertions=" + assertions);
    }

    private static void catalogEvidence() throws Exception {
        final TrashCatalog catalog = new TrashCatalog(() -> {
            try { return Files.newInputStream(Path.of("src/main/resources/content/trash/catalog.yml")); }
            catch (Exception e) { throw new RuntimeException(e); }
        }, java.util.logging.Logger.getLogger("audit-regression"));
        catalog.load();
        final Set<String> authored = new HashSet<>();
        int stories = 0, clues = 0;
        for (TrashDefinition definition : catalog.snapshot().values()) {
            check(definition.archaeology() != null, "missing authored physical evidence");
            var evaluation = TrashArchaeologyFactEngine.evaluate(definition, Optional.empty(), 50).orElseThrow();
            if (definition.internalKind() == TrashKind.STORY) {
                stories++;
                check(definition.archaeology().facts().size() >= 2, "story has fewer than two authored facts");
                for (String text : definition.archaeology().facts()) check(authored.add(text), "reused story fact");
            }
            if (!definition.archaeology().discrepancy().isBlank()) clues++;
            check(evaluation.facts().size() <= 8, "unbounded tooltip facts");
        }
        check(stories == 75 && authored.size() == 150 && clues == 25, "authored coverage mismatch");
        check(catalog.require("lila_fenyu_uvegszilank").archaeology().family().equals("glass"), "glass is organic");
        check(catalog.require("faek").archaeology().family().equals("wood"), "technical flint carrier leaked");
        check(catalog.require("regi_kotes").archaeology().family().equals("textile"), "technical paper carrier leaked");
        for (int seed = 0; seed < 25; seed++) {
            var definitions = new ArrayList<>(catalog.snapshot().values());
            Collections.shuffle(definitions, new Random(seed));
            var profile = TrashArchaeologyProfileStore.Profile.empty();
            for (TrashDefinition definition : definitions) {
                var evidence = TrashArchaeologyFactEngine.evaluate(definition, Optional.empty(), profile.level()).orElseThrow().evidence();
                profile = TrashArchaeologyProfileStore.advance(profile, evidence).profile();
                check(TrashArchaeologyProfileStore.advance(profile, evidence).awardedInsight() == 0, "duplicate fact farm");
            }
            check(profile.unlocked(), "catalog order prevented discovery");
        }
    }

    private static void protectsSitesWithoutBlockingTheirBrush() throws Exception {
        final Fixture f = new Fixture(EquipmentSlot.OFF_HAND);
        final var manager = allocate(hu.taliann.icesmp.managers.ArcheologyManager.class);
        final UUID siteId = UUID.randomUUID();
        final NamespacedKey key = new NamespacedKey("icesmp", "archeology_site");
        final Class<?> recordType = Class.forName("hu.taliann.icesmp.managers.ArcheologyManager$SiteRecord");
        final Constructor<?> constructor = recordType.getDeclaredConstructors()[0]; constructor.setAccessible(true);
        final Object record = constructor.newInstance(siteId, f.id, 0, 64, 0, "minecraft:stone");
        final Map<Object,Object> tags = new HashMap<>(); tags.put(key, siteId.toString());
        final PersistentDataContainer pdc = proxy(PersistentDataContainer.class, (m,a) -> m.equals("get") ? tags.get(a[0]) : null);
        final org.bukkit.block.BrushableBlock brushable = proxy(org.bukkit.block.BrushableBlock.class,
                (m,a) -> m.equals("getPersistentDataContainer") ? pdc : null);
        final Object[] state = {brushable}; final int[] writes = {0};
        final World[] world = {null};
        final org.bukkit.block.Block block = proxy(org.bukkit.block.Block.class, (m,a) -> switch (m) {
            case "getWorld" -> world[0]; case "getX", "getZ" -> 0; case "getY" -> 64;
            case "getType" -> Material.SUSPICIOUS_SAND; case "getState" -> state[0];
            case "setBlockData" -> { writes[0]++; yield null; } default -> null;
        });
        world[0] = proxy(World.class, (m,a) -> switch (m) {
            case "getUID" -> f.id; case "getBlockAt" -> block; default -> null;
        });
        set(manager, "activeSite", record); set(manager, "siteKey", key);
        set(manager, "site", new Location(world[0], 0,64,0));
        var protection = new hu.taliann.icesmp.listeners.TemporaryBlockProtection(manager::protects, manager::allowsBrushing);
        for (Material to : List.of(Material.SUSPICIOUS_SAND, Material.SAND, Material.STONE)) {
            var data = proxy(org.bukkit.block.data.BlockData.class, (m,a) -> m.equals("getMaterial") ? to : null);
            var change = new org.bukkit.event.entity.EntityChangeBlockEvent(f.player, block, data);
            protection.onEntityChange(change);
            check(change.isCancelled() == (to == Material.STONE), "site protection vetoed brushing or admitted foreign mutation");
        }
        var sand = proxy(org.bukkit.block.data.BlockData.class, (m,a) -> m.equals("getMaterial") ? Material.SAND : null);
        tags.put(key, UUID.randomUUID().toString());
        var wrongStamp = new org.bukkit.event.entity.EntityChangeBlockEvent(f.player, block, sand);
        protection.onEntityChange(wrongStamp);
        check(wrongStamp.isCancelled(), "foreign brushable stamp bypassed protection");
        tags.put(key, siteId.toString());
        manager.handleExcavated(f.player, new Location(world[0],1,64,0), brushable);
        check(get(manager, "activeSite") == record, "adjacent excavation closed active site");
        var broken = new org.bukkit.event.block.BlockBreakEvent(block, f.player);
        protection.onBreak(broken);
        check(broken.isCancelled(), "active site could be harvested as a block");

        final Field serverField = Bukkit.class.getDeclaredField("server"); serverField.setAccessible(true);
        final Object previousServer = serverField.get(null);
        final Path directory = Files.createTempDirectory("site-restore-regression");
        final Path journal = directory.resolve("site.yml");
        final var scheduler = proxy(io.papermc.paper.threadedregions.scheduler.RegionScheduler.class, (m,a) -> {
            if (m.equals("run")) { Tick task = new Tick((Consumer<ScheduledTask>) a[a.length - 1]); task.run(); return task.task; }
            return null;
        });
        final var server = proxy(org.bukkit.Server.class, (m,a) -> switch(m) {
            case "getWorld" -> world[0]; case "getRegionScheduler" -> scheduler;
            case "createBlockData" -> sand; default -> null;
        });
        final var plugin = allocate(TestPlugin.class);
        Field pluginServer = JavaPlugin.class.getDeclaredField("server"); pluginServer.setAccessible(true); pluginServer.set(plugin, server);
        set(manager, "plugin", plugin); set(manager, "journal", journal.toFile());
        set(manager, "restoring", new java.util.concurrent.atomic.AtomicBoolean());
        Method restore = manager.getClass().getDeclaredMethod("restoreViaRegion", boolean.class); restore.setAccessible(true);
        try {
            serverField.set(null, server);
            state[0] = proxy(org.bukkit.block.BlockState.class, (m,a) -> null);
            restore.invoke(manager, false);
            check(writes[0] == 0 && get(manager, "activeSite") == null, "foreign block was overwritten or site stayed locked");
            check(Files.exists(journal), "site closure was not persisted");
            set(manager, "activeSite", record); state[0] = brushable;
            restore.invoke(manager, false);
            check(writes[0] == 1 && get(manager, "activeSite") == null, "owned brushable was not restored and closed");
        } finally {
            serverField.set(null, previousServer);
            try (var files = Files.walk(directory)) {
                for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
            }
        }
    }

    private static void vendorAbsenceRequiresReceipt() throws Exception {
        TrashVendorService vendor = allocate(TrashVendorService.class);
        NamespacedKey receipt = new NamespacedKey("icesmp", "trash_vendor_removed");
        set(vendor, "removalReceipt", receipt);
        Map<Object,Object> persisted = new HashMap<>(); int[] saves = {0};
        PersistentDataContainer pdc = proxy(PersistentDataContainer.class, (m,a) -> switch(m) {
            case "get" -> persisted.get(a[0]);
            default -> null;
        });
        PlayerInventory inventory = proxy(PlayerInventory.class, (m,a) -> m.equals("getSize") ? 41 : null);
        Player player = proxy(Player.class, (m,a) -> switch(m) {
            case "getInventory" -> inventory;
            case "getPersistentDataContainer" -> pdc;
            case "saveData" -> { saves[0]++; yield null; }
            default -> null;
        });
        var sale = new TrashRecyclePool.SaleTransaction(UUID.randomUUID(), UUID.randomUUID(),
                TrashRecyclePool.SaleStage.BUDGET_RESERVED, "test", new TestItem(Material.STICK, 1),
                1,1,0,"test",2,1,0,1,List.of());
        Method remove = TrashVendorService.class.getDeclaredMethod("removeSoldUnits", Player.class, TrashRecyclePool.SaleTransaction.class);
        remove.setAccessible(true);
        try { remove.invoke(vendor, player, sale); throw new AssertionError("missing source was accepted"); }
        catch (InvocationTargetException rejected) { check(rejected.getCause() instanceof IllegalStateException, "wrong refusal"); }
        check(saves[0] == 0, "absence fabricated removal receipt");
        persisted.put(receipt, sale.operationId().toString());
        remove.invoke(vendor, player, sale);
        check(saves[0] == 1, "recovery advanced without re-acknowledging durable inventory");
    }

    private static final class Fixture {
        TestItem main, off;
        EquipmentSlot using;
        Tick tick;
        int inspections;
        final UUID id = UUID.randomUUID();
        final TestBridge bridge = new TestBridge();
        final Player player;
        final TrashArchaeologyListener listener;
        Fixture(EquipmentSlot brushHand) throws Exception {
            main = new TestItem(brushHand == EquipmentSlot.HAND ? Material.BRUSH : Material.STICK, 1);
            off = new TestItem(brushHand == EquipmentSlot.OFF_HAND ? Material.BRUSH : Material.STICK, 1);
            World world = proxy(World.class, (m,a) -> m.equals("getUID") ? id : null);
            PlayerInventory inventory = proxy(PlayerInventory.class, (m,a) -> switch(m) {
                case "getItemInMainHand" -> main; case "getItemInOffHand" -> off; case "getHeldItemSlot" -> 0; default -> null;
            });
            EntityScheduler scheduler = proxy(EntityScheduler.class, (m,a) -> {
                if (m.equals("runAtFixedRate")) { tick = new Tick((Consumer<ScheduledTask>)a[1]); return tick.task; }
                if (m.equals("run")) { Tick immediate = new Tick((Consumer<ScheduledTask>)a[1]); immediate.run(); return immediate.task; }
                return null;
            });
            player = proxy(Player.class, (m,a) -> switch(m) {
                case "getUniqueId" -> id; case "getInventory" -> inventory; case "getScheduler" -> scheduler;
                case "isOnline" -> true; case "isDead" -> false; case "getWorld" -> world;
                case "getLocation", "getEyeLocation" -> new Location(world, 0, 64, 0);
                case "startUsingItem" -> { using = (EquipmentSlot)a[0]; yield null; }
                default -> null;
            });
            listener = new TrashArchaeologyListener(allocate(TestPlugin.class), (owner,snapshot) -> {
                inspections++;
                return CompletableFuture.completedFuture(new TrashArchaeologyService.Result(true, "test", 0,
                        List.of(new TrashArchaeologyFactEngine.Fact("test", TrashArchaeologyFactEngine.Category.MATERIAL,
                                0,0,false,0,"Megfigyelés.")), TrashArchaeologyProfileStore.Profile.empty(), Set.of(),0,false));
            }, bridge, new TrashRuntimeTelemetry());
        }
        PlayerInteractEvent click(Action action, EquipmentSlot hand) {
            return new PlayerInteractEvent(player, action, hand == EquipmentSlot.HAND ? main : off, null, BlockFace.UP, hand);
        }
        int age;
        void hold(int count) {
            for (int i = 0; i < count; i++) {
                advance(1);
                if (++age % 4 == 0) listener.onInteract(click(Action.RIGHT_CLICK_AIR, EquipmentSlot.HAND));
            }
        }
        void advance(int count) { for (int i=0; i<count; i++) if (tick != null) tick.run(); }
    }
    public static final class TestPlugin extends JavaPlugin { }
    private static final class Tick {
        boolean cancelled;
        final Consumer<ScheduledTask> callback;
        final ScheduledTask task;
        Tick(Consumer<ScheduledTask> callback) {
            this.callback = callback;
            task = (ScheduledTask) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{ScheduledTask.class}, (p,m,a) -> {
                if (m.getName().equals("cancel")) { cancelled = true; return m.getReturnType().getEnumConstants()[0]; }
                return null;
            });
        }
        void run() { if (!cancelled) callback.accept(task); }
    }
    private static final class TestBridge implements ArchaeologyTooltipBridge {
        EquipmentSlot shownHand;
        public boolean available() { return true; }
        public boolean show(Player p, ItemStack i, List<String> facts) { throw new AssertionError("hand was discarded"); }
        public boolean show(Player p, EquipmentSlot hand, ItemStack i, List<String> facts) { shownHand = hand; return true; }
        public void clear(Player p) { }
        public void clearPlayerState(UUID id) { }
        public void shutdown() { }
    }
    private static final class TestItem extends ItemStack {
        private final Material material; private final int amount;
        TestItem(Material material, int amount) { super(); this.material = material; this.amount = amount; }
        @Override public Material getType() { return material; }
        @Override public int getAmount() { return amount; }
        @Override public boolean hasItemMeta() { return false; }
        @Override public ItemStack clone() { return new TestItem(material, amount); }
        @Override public boolean isSimilar(ItemStack other) { return other != null && material == other.getType(); }
    }
    private static <T> T proxy(Class<T> type, BiFunction<String,Object[],Object> handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class[]{type}, (p,m,a) -> {
            if (m.getDeclaringClass() == Object.class) return switch(m.getName()) {
                case "equals" -> p == a[0]; case "hashCode" -> System.identityHashCode(p);
                case "toString" -> "controlled " + type.getSimpleName(); default -> null;
            };
            return handler.apply(m.getName(), a);
        }));
    }
    private static <T> T allocate(Class<T> type) throws Exception {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
        return type.cast(((sun.misc.Unsafe)field.get(null)).allocateInstance(type));
    }
    private static Object get(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(owner);
    }
    private static void set(Object owner, String name, Object value) throws Exception {
        Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true); field.set(owner, value);
    }
}
