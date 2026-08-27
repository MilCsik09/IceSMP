package hu.taliann.icesmp.trash;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Source and authority gates for the Phase C hidden instance-history boundary. */
public final class TrashHistoryRegressionSuite {

    private static final Path ROOT = Path.of("src/main/java/hu/taliann/icesmp");
    private static final Path HISTORY_SERVICE = ROOT.resolve("trash/TrashHistoryService.java");
    private static final Path HISTORY_STORE = ROOT.resolve("trash/TrashHistoryStore.java");
    private static final Path HISTORY_LISTENER = ROOT.resolve("trash/TrashHistoryListener.java");
    private static final Path EVENT = ROOT.resolve("trash/TrashHistoryEvent.java");
    private static final Path FACTORY = ROOT.resolve("trash/TrashItemFactory.java");
    private static final Path VENDOR = ROOT.resolve("trash/TrashVendorService.java");
    private static final Path RECYCLE = ROOT.resolve("trash/TrashRecyclePool.java");
    private static final Path LOOT = ROOT.resolve("trash/TrashLootService.java");
    private static final Path CORE = ROOT.resolve("core/IceSMPCore.java");

    private TrashHistoryRegressionSuite() { }

    public static void main(final String[] args) throws Exception {
        preservesOpaquePhysicalAuthority();
        preservesSignificantEventSplitAndTransformation();
        preservesDurableBoundedHistory();
        preservesVendorRecycleTransactionOrder();
        preservesSignificantEventHooks();
        preservesNoGateRuntimeBoundary();
        System.out.println("Trash history regression suite passed.");
    }

    private static void preservesOpaquePhysicalAuthority() throws Exception {
        final String service = Files.readString(HISTORY_SERVICE);
        require(service, "new NamespacedKey(plugin, \"trash_instance\")", "opaque instance token");
        require(service, "new NamespacedKey(plugin, \"trash_history_revision\")",
                "physical history revision");
        require(service, "new NamespacedKey(plugin, \"trash_origin\")",
                "stack-equivalent creation provenance");
        require(service, "store.matches(instanceId, baseId, phase, revision)",
                "external history authority match");
        require(service, "stale vagy duplikált Trash history instance",
                "stale/duplicate rejection");
        require(service, "itemFactory.refreshPresentation(item)",
                "ITEM_MODEL restoration after PDC writes");
        check(!service.contains("trash_relic") && !service.contains("behavior"),
                "physical history service must not stamp hidden kind or behavior");
    }

    private static void preservesSignificantEventSplitAndTransformation() throws Exception {
        final String service = Files.readString(HISTORY_SERVICE);
        final int clone = service.indexOf("final ItemStack singleton = source.clone()",
                service.indexOf("transformMainHandOnSuccess"));
        final int singleton = service.indexOf("singleton.setAmount(1)", clone);
        final int transformInternal = service.indexOf("transformInternal(source, singleton", singleton);
        check(clone >= 0 && singleton > clone && transformInternal > singleton,
                "significant mutation must split exactly one unit inside the transaction");
        final int activate = service.indexOf("TrashHistoryEvent.ACTIVATED");
        final int applyPhase = service.indexOf("itemFactory.applyPhase(singleton", activate);
        final int transform = service.indexOf("store.transform(activated.instanceId()", applyPhase);
        check(activate >= 0 && applyPhase > activate && transform > applyPhase,
                "activation must split, project the authored phase, then advance history authority");
        require(Files.readString(FACTORY), "previousMeta.getPersistentDataContainer().copyTo",
                "phase transition PDC preservation");
        final int fullInventory = service.indexOf("source.getAmount() > 1"
                + " && player.getInventory().firstEmpty() < 0");
        final int commit = service.indexOf("store.transact", fullInventory);
        check(fullInventory >= 0 && commit > fullInventory,
                "full inventory must reject a required singleton split before history mutation");
        require(service, "splitForKing", "fresh king-held singleton split");

        final String events = Files.readString(EVENT);
        for (final String event : List.of("CREATED_FISHING", "CREATED_MOB_DROP", "CREATED_AMBIENT",
                "VENDOR_SOLD", "VENDOR_RECYCLED", "TRANSFORMED", "REPAIRED", "HELD_BY_KING",
                "OWNER_COUNT_MILESTONE", "PRESENT_AT_PLAYER_DEATH", "NETHER_TRANSIT",
                "WORLD_EVENT_PRESENT", "ACTIVATED")) {
            require(events, event, "significant history event " + event);
        }
    }

    private static void preservesDurableBoundedHistory() throws Exception {
        final String store = Files.readString(HISTORY_STORE);
        require(store, "implements PersistentStore", "central persistence lifecycle");
        require(store, "new File(plugin.getDataFolder(), \"trash-history.yml\")",
                "dedicated durable history authority");
        require(store, "MAX_EVENTS = 64", "bounded history event retention");
        require(store, "MAX_OWNERS = 64", "bounded owner retention");
        require(store, "while (events.size() > MAX_EVENTS) events.remove(0)",
                "oldest-event pruning");
        require(store, "YamlStore.saveAtomic", "atomic history persistence");
        require(store, "YamlStore.registerCriticalWrite(file)", "critical history write circuit");
        require(store, "YamlStore.failCorrupt", "fail-closed corrupt-store handling");
        require(store, "history.revision() == revision", "exact revision match");
        require(store, "public synchronized <T> T transact", "serialized durable transaction");
        require(store, "histories.putAll(historyBefore)", "history rollback on write failure");
        require(store, "vendor-operations.", "durable idempotent vendor receipts");

        final String core = Files.readString(CORE);
        final int historyStore = core.indexOf("trashHistoryStore,");
        final int recycleStore = core.indexOf("trashRecyclePool);", historyStore);
        check(historyStore >= 0 && recycleStore > historyStore,
                "history authority must load before exact recycle instances");
    }

    private static void preservesVendorRecycleTransactionOrder() throws Exception {
        final String vendor = Files.readString(VENDOR);
        require(vendor, "if (!itemFactory.isKnownItem(hand))",
                "malformed lifecycle rejection before value resolution");
        final int validate = vendor.indexOf("history.validateVendorSale");
        final int budget = vendor.indexOf("DailyBudget.tryConsumeDurablyOnOwnThread", validate);
        final int removed = vendor.indexOf("recyclePool.markItemRemoved", budget);
        final int commit = vendor.indexOf("recyclePool.commitRecycle", removed);
        check(validate >= 0 && budget > validate && removed > budget && commit > removed,
                "vendor must preflight, reserve budget, durably remove, then commit recycle");

        final String recycle = Files.readString(RECYCLE);
        require(recycle, "offerAll(final List<ItemStack> soldUnits)",
                "per-unit recycle input without token cloning");
        require(recycle, "YamlStore.registerCriticalWrite(file)", "critical recycle write circuit");
        check(!recycle.contains("offer(final ItemStack sold, final int amount)"),
                "recycle pool must not clone one instance token across an amount");
        final int itemRemoved = recycle.indexOf("current.stage() != SaleStage.ITEM_REMOVED");
        final int prepare = recycle.indexOf("history.prepareVendorUnits", itemRemoved);
        final int poolCommit = recycle.indexOf("SaleStage.POOL_COMMITTED", prepare);
        check(itemRemoved >= 0 && prepare > itemRemoved && poolCommit > prepare,
                "history units must only be prepared after durable item removal");
        final int removeFirst = recycle.indexOf("instances.removeFirst()",
                recycle.indexOf("Optional<ItemStack> take"));
        final int persist = recycle.indexOf("persistOrRestore", removeFirst);
        final int recycled = recycle.indexOf("history.recordRecycled", persist);
        check(removeFirst >= 0 && persist > removeFirst && recycled > persist,
                "pool removal must be durable before recycled history advances");

        final String loot = Files.readString(LOOT);
        final int identity = loot.indexOf("selector.select");
        final int exact = loot.indexOf("recyclePool.take", identity);
        check(identity >= 0 && exact > identity,
                "exact recycle substitution must remain behind the same base-identity roll");
        require(loot, "history.markOrigin(itemFactory.create", "fresh stack-equivalent origin marker");
    }

    private static void preservesSignificantEventHooks() throws Exception {
        final String listener = Files.readString(HISTORY_LISTENER);
        require(listener, "EntityPickupItemEvent", "bounded ownership observation");
        require(listener, "PlayerDeathEvent", "death-presence history hook");
        require(listener, "PlayerChangedWorldEvent", "Nether transit history hook");
        require(listener, "PlayerItemMendEvent", "repair history hook");
        require(listener, "event.getSlot()", "exact post-mending equipment slot");
        require(listener, "after.getDamage() < previousDamage",
                "cancelled or zero-result mending rejection");
        require(listener, "PrepareAnvilEvent", "anvil prepared-result repair hook");
        require(listener, "PrepareGrindstoneEvent", "grindstone prepared-result repair hook");
        require(listener, "after.getDamage() < before.getDamage()", "real durability delta gate");
        require(listener, "player.getScheduler().run(plugin",
                "post-transaction Folia repair commit");
        require(listener, "ItemSpawnEvent", "region-owned direct result-drop repair recovery");
        require(listener, "PlayerJoinEvent", "crash-safe pending repair recovery");
        check(!listener.contains("event.getItem().getItemStack()")
                        && !listener.contains("PlayerDropItemEvent"),
                "pickup/drop hooks must not dereference another entity from the player thread");
        check(!listener.contains("event.setCurrentItem(output)"),
                "result slot mutation must not race the vanilla click transaction");
        final String service = Files.readString(HISTORY_SERVICE);
        require(service, "new NamespacedKey(plugin, \"trash_repair_pending\")",
                "opaque prepared repair marker");
        require(service, "new NamespacedKey(plugin, \"trash_repair_before\")",
                "prepared repair durability precondition");
        require(service, "new NamespacedKey(plugin, \"trash_repair_actor\")",
                "durable prepared repair actor");
        require(service, "after.getDamage() >= repair.beforeDamage()",
                "prepared repair must prove an actual durability change");
        require(service, "completePreparedRepair", "exact prepared repair commit");
        require(listener, "kings.isKing(player)", "king milestone hook");
        check(!listener.contains("PlayerMoveEvent") && !listener.contains("runAtFixedRate"),
                "history must not scan inventories per tick or movement");
    }

    private static void preservesNoGateRuntimeBoundary() throws Exception {
        for (final Path path : List.of(LOOT, VENDOR, ROOT.resolve("trash/TrashAmbientManager.java"),
                ROOT.resolve("managers/ConfigManager.java"), ROOT.resolve("gui/ConfigMenuGUI.java"))) {
            check(!Files.readString(path).contains("trash-runtime"),
                    "Trash activation/operator gate leaked into " + path);
        }
        check(!Files.exists(Path.of("src/main/resources/config/trash-runtime.yml")),
                "Trash operator gate config must not exist");
    }

    private static void require(final String source, final String token, final String description) {
        check(source.contains(token), "missing " + description + ": " + token);
    }

    private static void require(final Path source, final String token, final String description)
            throws Exception {
        require(Files.readString(source), token, description);
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }
}
