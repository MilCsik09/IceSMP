package hu.taliann.icesmp.trash;

import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.util.Vector;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

public final class TrashAuditRepairRegressionSuite {
    private static int assertions;
    private static TrashCatalog catalog;
    private static TrashItemFactory items;
    public static void main(String[] args) throws Exception {
        // Item-only PAPER needs no block registry in this isolated API fixture.
        final Field blockType = Material.class.getDeclaredField("blockType"); blockType.setAccessible(true);
        final Object previousBlockType = blockType.get(Material.PAPER);
        blockType.set(Material.PAPER, (java.util.function.Supplier<org.bukkit.block.BlockType>) () -> null);
        catalog = new TrashCatalog(() -> TrashAuditRepairRegressionSuite.class.getClassLoader().getResourceAsStream(TrashCatalog.RESOURCE), java.util.logging.Logger.getAnonymousLogger());
        catalog.load();
        items = allocate(TrashItemFactory.class);
        set(items, "catalog", catalog); set(items, "trashIdKey", key("trash_id")); set(items, "phaseKey", key("trash_phase"));
        final Field field = Bukkit.class.getDeclaredField("server"); field.setAccessible(true);
        final Object previous = field.get(null);
        field.set(null, proxy(Server.class, (m,a) -> m.equals("isOwnedByCurrentRegion") ? true : null));
        try { airUses(); healthOutcomes(); water(); coinCollect(); staleFacts(); rejectedPhysics(); bucketProtection(); fractureConflict(); }
        finally { field.set(null, previous); blockType.set(Material.PAPER, previousBlockType); }
        System.out.println("Trash audit repair regression passed. assertions=" + assertions);
    }
    private static void airUses() throws Exception {
        final TrashRelicRuntime relic = allocate(TrashRelicRuntime.class);
        set(relic, "catalog", catalog); set(relic, "items", items);
        final Set<UUID> armed = new HashSet<>(); set(relic, "effectVetoArmed", armed);
        final UUID id = UUID.randomUUID(); final ItemStack[] hand = {new TestItem("palackozott_nem")};
        final PlayerInventory inventory = proxy(PlayerInventory.class, (m,a) -> m.startsWith("getItemIn") ? hand[0] : null);
        final Player player = proxy(Player.class, (m,a) -> switch(m) {
            case "getUniqueId" -> id; case "getInventory" -> inventory; case "isOnline", "isValid" -> true; default -> null;
        });
        final TrashAnomalyRuntime anomaly = allocate(TrashAnomalyRuntime.class);
        final AtomicInteger calls = new AtomicInteger();
        final var activation = new TrashAnomalyActivationService(catalog, items, () -> true,
                (p,h,g,b,i,behavior,lifetime) -> { calls.incrementAndGet(); return TrashAnomalyActivationService.Result.CONTINUE; });
        activation.start(); set(anomaly, "activation", activation);
        for (final EquipmentSlot slot : List.of(EquipmentSlot.HAND, EquipmentSlot.OFF_HAND)) {
            hand[0] = new TestItem("palackozott_nem"); armed.clear();
            dispatch(relic, click(player, hand[0], slot));
            check(armed.contains(id), "air use did not arm the actual relic handler");
            armed.clear(); final var denied = click(player, hand[0], slot); denied.setCancelled(true); dispatch(relic, denied);
            check(armed.isEmpty(), "explicit cancellation activated a relic");
            hand[0] = new TestItem("ures_csontzacsko");
            final int before = calls.get(); dispatch(anomaly, click(player, hand[0], slot));
            check(calls.get() == before + 1, "air use did not enter native anomaly activation");
            final var brushDenied = click(player, hand[0], slot); brushDenied.setUseItemInHand(Event.Result.DENY); dispatch(anomaly, brushDenied);
            check(calls.get() == before + 1, "Brush item veto was ignored");
        }
    }
    private static PlayerInteractEvent click(Player player, ItemStack item, EquipmentSlot slot) {
        return new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR, item, null, BlockFace.SELF, slot);
    }
    private static void dispatch(Listener listener, PlayerInteractEvent event) throws Exception {
        final Method method = listener.getClass().getMethod("onUse", PlayerInteractEvent.class);
        final var annotation = method.getAnnotation(EventHandler.class);
        new RegisteredListener(listener, (l,e) -> {
            try { method.invoke(l, e); } catch (ReflectiveOperationException failure) { throw new org.bukkit.event.EventException(failure); }
        }, annotation.priority(), null, annotation.ignoreCancelled()).callEvent(event);
    }
    @SuppressWarnings("deprecation") private static void healthOutcomes() throws Exception {
        final TrashRelicRuntime runtime = allocate(TrashRelicRuntime.class);
        set(runtime,"catalog",catalog); set(runtime,"items",items);
        for (final int slot : List.of(0,12,40)) {
            final TestItem sword = new TestItem("a_kard_amely_minden_csatat_megnyer");
            final PlayerInventory inventory = proxy(PlayerInventory.class,(m,a)->switch(m) {
                case "getSize" -> 41; case "getItem" -> (int)a[0]==slot?sword:null; default -> null;
            });
            final Player player = proxy(Player.class,(m,a)->m.equals("getInventory")?inventory:null);
            check((int)invoke(runtime,"findSlot",new Class[]{Player.class,TrashRelicBehavior.class},player,
                    TrashRelicBehavior.A_KARD_AMELY_MINDEN_CSATAT_MEGNYER)==slot,"sword ignored an inventory/offhand slot");
            sword.tags.put(key("trash_phase"),catalog.require("a_kard_amely_minden_csatat_megnyer").successPhase());
            check((int)invoke(runtime,"findSlot",new Class[]{Player.class,TrashRelicBehavior.class},player,
                    TrashRelicBehavior.A_KARD_AMELY_MINDEN_CSATAT_MEGNYER)==-1,"spent sword can abandon again");
        }
        final double fraction = catalog.require("a_kard_amely_minden_csatat_megnyer").losingHealthFraction();
        check(fraction == 0.25D, "missing authored losing threshold");
        check(TrashRelicPolicy.losingCombat(8,3,20,fraction), "nonlethal low HP did not abandon sword");
        check(TrashRelicPolicy.losingCombat(4,1,20,fraction), "already low HP ignored");
        check(TrashRelicPolicy.losingCombat(15,5,40,fraction), "custom maximum HP ignored");
        check(!TrashRelicPolicy.losingCombat(8,2,20,fraction), "healthy holder abandoned");
        check(!TrashRelicPolicy.losingCombat(4,0,20,fraction), "zero damage abandoned");
        check(!TrashRelicPolicy.losingCombat(4,Double.NaN,20,fraction), "invalid damage abandoned");
        for (double reduction : List.of(0.0D,0.2D,0.65D)) {
            var modifiers = new EnumMap<EntityDamageEvent.DamageModifier,Double>(EntityDamageEvent.DamageModifier.class);
            modifiers.put(EntityDamageEvent.DamageModifier.BASE,40D); modifiers.put(EntityDamageEvent.DamageModifier.RESISTANCE,-40 * reduction);
            var functions = new EnumMap<EntityDamageEvent.DamageModifier,com.google.common.base.Function<? super Double,Double>>(EntityDamageEvent.DamageModifier.class);
            functions.put(EntityDamageEvent.DamageModifier.BASE,d -> 0D); functions.put(EntityDamageEvent.DamageModifier.RESISTANCE,d -> -reduction * d);
            final var event = new EntityDamageEvent(null, EntityDamageEvent.DamageCause.ENTITY_ATTACK,null,modifiers,functions);
            TrashRelicRuntime.leaveOneHealth(event,10D);
            check(Math.abs(10D - event.getFinalDamage() - 1D) < 1e-9, "bandage final damage did not leave 1 HP");
        }
        check(!catalog.require("fagyott_tintas_cetli").contextualText().isEmpty(), "cold ink contains no authored text");
        check(catalog.require("suttogo_cetli").contextualText().size() == 4, "whispers lack authored content");
    }
    private static void water() throws Exception {
        final TrashAnomalyRuntime runtime = allocate(TrashAnomalyRuntime.class);
        final int[] west = {0}, east = {2}; final Block[] center = {null}; final World[] world = {null}; final Vector[] velocity = {new Vector(0,0.07,0)};
        world[0] = proxy(World.class, (m,a) -> m.equals("getBlockAt") ? center[0] : null);
        center[0] = proxy(Block.class, (m,a) -> switch(m) {
            case "getX","getZ" -> 4; case "getWorld" -> world[0];
            case "getBlockData" -> proxy(Levelled.class,(n,b) -> n.equals("getLevel") ? 1 : null);
            case "getRelative" -> {
                final var face = (BlockFace) a[0];
                yield proxy(Block.class,(n,b) -> switch(n) {
                    case "getX","getZ" -> 4; case "getWorld" -> world[0];
                    case "getType" -> face == BlockFace.WEST || face == BlockFace.EAST ? Material.WATER : Material.STONE;
                    case "getBlockData" -> proxy(Levelled.class,(q,c) -> q.equals("getLevel") ? face == BlockFace.WEST ? west[0] : east[0] : null);
                    default -> null;
                });
            } default -> null;
        });
        final Item item = proxy(Item.class,(m,a) -> switch(m) {
            case "isInWater" -> true; case "getLocation" -> new Location(world[0],4,64,4); case "getVelocity" -> velocity[0];
            case "setVelocity" -> { velocity[0] = (Vector) a[0]; yield null; } default -> null;
        });
        invoke(runtime,"opposeWater",new Class[]{Item.class},item);
        check(velocity[0].getX() < 0 && velocity[0].getY() == 0.07D, "leaf failed to oppose eastbound flow");
        west[0] = 2; east[0] = 0; invoke(runtime,"opposeWater",new Class[]{Item.class},item);
        check(velocity[0].getX() > 0, "leaf failed to oppose westbound flow");
        west[0] = 1; east[0] = 1; velocity[0] = new Vector(); invoke(runtime,"opposeWater",new Class[]{Item.class},item);
        check(velocity[0].lengthSquared() == 0, "still water fabricated a direction");
    }
    private static void coinCollect() throws Exception {
        final TrashAnomalyRuntime runtime = allocate(TrashAnomalyRuntime.class);
        set(runtime,"activation",new TrashAnomalyActivationService(catalog,items,()->true,(p,h,g,b,i,behavior,l)->TrashAnomalyActivationService.Result.CONTINUE));
        final ItemStack coin = new TestItem("a_penztar_utolso_garasa");
        final Inventory top = proxy(Inventory.class,(m,a)->switch(m) { case "getStorageContents" -> new ItemStack[]{coin}; case "getSize" -> 27; default -> null; });
        final Player player = proxy(Player.class,(m,a)->null);
        final InventoryView view = proxy(InventoryView.class,(m,a)->switch(m) {
            case "getTopInventory" -> top; case "getPlayer" -> player; case "getCursor","getItem" -> coin;
            case "convertSlot" -> (int) a[0] - 27; default -> null;
        });
        final var event = new InventoryClickEvent(view,InventoryType.SlotType.CONTAINER,28,ClickType.DOUBLE_CLICK,InventoryAction.COLLECT_TO_CURSOR);
        runtime.onContainerClick(event);
        check(event.isCancelled(), "bottom-inventory double click collected last chest coin");
    }
    private static void staleFacts() throws Exception {
        final Path dir = Files.createTempDirectory("trash-fact-fence");
        final var store = new TrashHistoryStore(dir.resolve("history.yml").toFile(),dir.resolve("history.wal").toFile(),java.util.logging.Logger.getAnonymousLogger(),catalog); store.load();
        final TrashHistoryService history = allocate(TrashHistoryService.class);
        set(history,"catalog",catalog); set(history,"itemFactory",items); set(history,"store",store);
        for (String[] pair : List.of(new String[]{"instanceKey","trash_instance"},new String[]{"revisionKey","trash_history_revision"},new String[]{"originKey","trash_origin"},new String[]{"repairPendingKey","trash_repair_pending"},new String[]{"repairBeforeDamageKey","trash_repair_before"},new String[]{"repairActorKey","trash_repair_actor"})) set(history,pair[0],key(pair[1]));
        final var engine = new TrashArchaeologyFactEngine(catalog,items,history);
        final TestItem item = new TestItem("lyukas_vodor");
        check(engine.evaluate(item,30).isPresent(), "fresh item cannot be inspected");
        final UUID instance = UUID.randomUUID(), actor = UUID.randomUUID();
        store.transact(() -> store.createAndRecord(instance,"lyukas_vodor","base",TrashHistoryEvent.CREATED_AMBIENT,actor,""),null);
        item.tags.put(key("trash_instance"),instance.toString()); item.tags.put(key("trash_history_revision"),1L);
        check(engine.evaluate(item,30).isPresent(), "current tracked item cannot be inspected");
        store.transact(() -> store.record(instance,"lyukas_vodor","base",TrashHistoryEvent.ACTIVATED,actor,""),null);
        check(engine.evaluate(item,30).isEmpty(), "stale item evaluated as fresh");
        item.tags.put(key("trash_history_revision"),2L); item.tags.put(key("trash_repair_pending"),"incomplete");
        check(engine.evaluate(item,30).isEmpty(), "pending item evaluated as fresh");
        try(var paths=Files.walk(dir)) { for(Path path:paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path); }
    }
    private static void rejectedPhysics() throws Exception {
        final TrashAnomalyRuntime runtime = allocate(TrashAnomalyRuntime.class);
        final Set<UUID> active = ConcurrentHashMap.newKeySet(); final Map<UUID,AtomicInteger> counts = new ConcurrentHashMap<>();
        set(runtime,"activePhysics",active); set(runtime,"activeByWorld",counts); set(runtime,"physicsWorldByItem",new ConcurrentHashMap<>()); set(runtime,"physicsOriginY",new ConcurrentHashMap<>());
        final UUID id = UUID.randomUUID(), worldId = UUID.randomUUID();
        final World world = proxy(World.class,(m,a)->m.equals("getUID")?worldId:null);
        final var scheduler = proxy(io.papermc.paper.threadedregions.scheduler.EntityScheduler.class,(m,a)->null);
        final Item item = proxy(Item.class,(m,a)->switch(m) {case "getUniqueId" -> id; case "getWorld" -> world; case "getLocation" -> new Location(world,0,64,0); case "getScheduler" -> scheduler; default -> null;});
        invoke(runtime,"startPhysics",new Class[]{Item.class,TrashAnomalyBehavior.class},item,TrashAnomalyBehavior.BOKICNAK_ELLENTMONDO_LEVEL);
        check(active.isEmpty() && counts.values().stream().allMatch(c->c.get()==0), "null scheduler leaked physics reservation");
        final ItemDisplay display = proxy(ItemDisplay.class,(m,a)->switch(m) {
            case "getUniqueId" -> id; case "getScheduler" -> scheduler; default -> null;
        });
        final World tossWorld = proxy(World.class,(m,a)->m.equals("spawn")?display:null);
        final Player player = proxy(Player.class,(m,a)->m.equals("getEyeLocation")?new Location(tossWorld,0,64,0):null);
        final TossableObjectRuntime toss = new TossableObjectRuntime(null);
        check(!toss.toss(player,new TestItem("felrevert_garas"),TrashAnomalyBehavior.FELREVERT_GARAS), "null scheduler accepted toss");
        Field count = TossableObjectRuntime.class.getDeclaredField("activeCount"); count.setAccessible(true);
        Field reservations = TossableObjectRuntime.class.getDeclaredField("active"); reservations.setAccessible(true);
        check(((AtomicInteger)count.get(toss)).get()==0 && ((Set<?>)reservations.get(toss)).isEmpty(), "null scheduler leaked toss reservation");
    }
    private static void bucketProtection() {
        final Block protectedBlock = proxy(Block.class,(m,a)->null), safe = proxy(Block.class,(m,a)->null);
        final var protection = new hu.taliann.icesmp.listeners.TemporaryBlockProtection(b->b==protectedBlock);
        final var event = new PlayerBucketEmptyEvent(null,protectedBlock,safe,BlockFace.UP,Material.WATER_BUCKET,new TestItem("lyukas_vodor"),EquipmentSlot.HAND);
        protection.onBucketEmpty(event); check(event.isCancelled(), "bucket entered temporary aperture");
        final BlockState state = proxy(BlockState.class,(m,a)->m.equals("getBlock")?protectedBlock:null);
        final var fertilize = new org.bukkit.event.block.BlockFertilizeEvent(safe,null,new ArrayList<>(List.of(state)));
        protection.onFertilize(fertilize); check(fertilize.isCancelled(), "fertilizer entered temporary aperture");
    }
    public static final class TestPlugin extends org.bukkit.plugin.java.JavaPlugin {
        @Override public java.util.logging.Logger getLogger() { return java.util.logging.Logger.getAnonymousLogger(); }
    }
    private static void fractureConflict() throws Exception {
        final Path dir = Files.createTempDirectory("fracture-conflict");
        final UUID id = UUID.randomUUID(), worldId = UUID.randomUUID();
        final boolean[] restored = {false}, overwritten = {false};
        final org.bukkit.block.data.BlockData stone = proxy(org.bukkit.block.data.BlockData.class,(m,a)->m.equals("getAsString")?"minecraft:stone":null);
        final org.bukkit.block.data.BlockData dirt = proxy(org.bukkit.block.data.BlockData.class,(m,a)->m.equals("getAsString")?"minecraft:dirt":null);
        final Block foreign = proxy(Block.class,(m,a)->switch(m) {
            case "getType" -> Material.DIRT; case "getBlockData" -> dirt;
            case "setBlockData" -> { overwritten[0]=true; yield null; } default -> null;
        });
        final Block air = proxy(Block.class,(m,a)->switch(m) {
            case "getType" -> Material.AIR; case "isEmpty" -> true;
            case "setBlockData" -> { restored[0]=true; yield null; } default -> null;
        });
        final World world = proxy(World.class,(m,a)->m.equals("getBlockAt")? ((int)a[1]==64?foreign:air):null);
        final Field serverField = Bukkit.class.getDeclaredField("server"); serverField.setAccessible(true);
        final Object previous = serverField.get(null);
        serverField.set(null,proxy(Server.class,(m,a)->switch(m) { case "getWorld" -> world; case "createBlockData" -> stone; default -> null; }));
        try {
            final TrashSpatialFractureStore store = allocate(TrashSpatialFractureStore.class);
            set(store,"plugin",allocate(TestPlugin.class)); set(store,"file",dir.resolve("fractures.yml").toFile());
            final Map<UUID,Object> open = new LinkedHashMap<>(), conflicts = new LinkedHashMap<>();
            set(store,"open",open); set(store,"conflicts",conflicts);
            final Class<?> blockType = Class.forName(TrashSpatialFractureStore.class.getName()+"$BlockSnapshot");
            final Constructor<?> block = blockType.getDeclaredConstructor(int.class,int.class,int.class,String.class); block.setAccessible(true);
            final Class<?> fractureType = Class.forName(TrashSpatialFractureStore.class.getName()+"$Fracture");
            final Constructor<?> fracture = fractureType.getDeclaredConstructor(UUID.class,UUID.class,long.class,List.class); fracture.setAccessible(true);
            final Object record = fracture.newInstance(UUID.randomUUID(),worldId,System.currentTimeMillis()-400_000L,
                    List.of(block.newInstance(0,64,0,"minecraft:stone"),block.newInstance(0,65,0,"minecraft:stone")));
            open.put(id,record);
            invoke(store,"restoreNow",new Class[]{UUID.class,fractureType},id,record);
            check(open.isEmpty() && conflicts.containsKey(id),"foreign-block conflict kept active capacity forever");
            check(!overwritten[0] && restored[0],"conflict recovery overwrote foreign block or left owned air open");
            store.load();
            check(open.isEmpty() && conflicts.containsKey(id),"original conflict snapshots lost on disk reload");
        } finally {
            serverField.set(null,previous);
            try(var paths=Files.walk(dir)) { for(Path path:paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path); }
        }
    }
    private static NamespacedKey key(String name) { return new NamespacedKey("icesmp",name); }
    private static final class TestItem extends ItemStack {
        final Map<NamespacedKey,Object> tags = new HashMap<>();
        TestItem(String id) { super(); tags.put(key("trash_id"),id); tags.put(key("trash_phase"),"base"); }
        @Override public Material getType() { return Material.PAPER; }
        @Override public int getAmount() { return 1; }
        @Override public void setAmount(int amount) { if (amount != 1) throw new AssertionError("fixture only accepts singleton"); }
        @Override public boolean hasItemMeta() { return true; }
        @Override public ItemMeta getItemMeta() {
            var pdc = proxy(PersistentDataContainer.class,(m,a)->switch(m) { case "get" -> tags.get(a[0]); case "has" -> tags.containsKey(a[0]); default -> null; });
            return proxy(ItemMeta.class,(m,a)->m.equals("getPersistentDataContainer")?pdc:null);
        }
        @Override public ItemStack clone() { TestItem copy = new TestItem(tags.get(key("trash_id")).toString()); copy.tags.putAll(tags); return copy; }
        @Override public boolean equals(Object other) { return other instanceof TestItem item && tags.equals(item.tags); }
        @Override public int hashCode() { return tags.hashCode(); }
        @Override public boolean isSimilar(ItemStack other) { return equals(other); }
    }
    @SuppressWarnings("unchecked") private static <T>T proxy(Class<T> type,BiFunction<String,Object[],Object> body) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(),new Class[]{type},(p,m,a)->{
            if(m.getDeclaringClass()==Object.class) return switch(m.getName()){case "equals"->p==a[0];case "hashCode"->System.identityHashCode(p);default->type.getSimpleName();};
            final Object value=body.apply(m.getName(),a);
            if(value!=null || !m.getReturnType().isPrimitive() || m.getReturnType()==void.class) return value;
            if(m.getReturnType()==boolean.class) return false;
            if(m.getReturnType()==double.class) return 0D;
            if(m.getReturnType()==float.class) return 0F;
            if(m.getReturnType()==long.class) return 0L;
            return 0;
        });
    }
    private static <T>T allocate(Class<T> type)throws Exception {Field f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);return type.cast(((sun.misc.Unsafe)f.get(null)).allocateInstance(type));}
    private static void set(Object owner,String name,Object value)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);f.set(owner,value);}
    private static Object invoke(Object owner,String name,Class<?>[] types,Object...args)throws Exception{Method m=owner.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(owner,args);}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);assertions++;}
}
