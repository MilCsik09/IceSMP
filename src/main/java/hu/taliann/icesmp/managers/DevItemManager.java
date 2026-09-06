package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.dev.artifact.*;
import hu.taliann.icesmp.items.BlueprintItemFactory;
import hu.taliann.icesmp.items.DevItemFactory;
import hu.taliann.icesmp.items.UniqueMaterialFactory;
import hu.taliann.icesmp.listeners.ProfessionRecipeBookListener;
import hu.taliann.icesmp.session.PlayerStateCleanup;
import hu.taliann.icesmp.storage.PersistentStore;
import hu.taliann.icesmp.storage.YamlStore;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** The single identity, recovery and persistence authority for registered developer artifacts. */
public final class DevItemManager implements PersistentStore, PlayerStateCleanup {
    private static final boolean WORLD_WEAVER_ENABLED = false;
    private static final class Entry {
        final DevArtifactRegistration registration;
        final AtomicBoolean tickQueued = new AtomicBoolean();
        volatile boolean quarantined;
        volatile long nextTick;
        volatile DevArtifactPresentation.ModelState model = DevArtifactPresentation.ModelState.IDLE;
        Entry(final DevArtifactRegistration registration) { this.registration = registration; }
        DevArtifactDefinition definition() { return registration.definition(); }
        DevArtifactBehavior behavior() { return registration.behavior(); }
        DevArtifactPolicy policy() { return definition().policySource().current(); }
    }
    private record InputStamp(long tick, DevArtifactInteraction.Kind kind, UUID entity) {}
    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final DevItemFactory itemFactory;
    private final File stateFile;
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final ArtifactSessionFence sessions = new ArtifactSessionFence();
    private final Map<UUID, InputStamp> inputs = new ConcurrentHashMap<>();
    private final AtomicBoolean storageWarning = new AtomicBoolean();
    private final ThreadPoolExecutor io;
    private volatile DevArtifactLedger ledger;
    private volatile ScheduledTask tickTask;
    private volatile boolean ready;
    private volatile boolean shuttingDown;
    private final AtomicBoolean closing = new AtomicBoolean();

    public DevItemManager(final JavaPlugin plugin, final ConfigManager configManager,
            final MessageManager messageManager, final UniqueMaterialFactory uniqueMaterials,
            final ProfessionRecipeCatalog recipeCatalog, final BlueprintItemFactory blueprintFactory,
            final ProfessionRecipeBookListener recipeBuilder) {
        this.plugin = plugin;
        this.configManager = configManager;
        itemFactory = new DevItemFactory(plugin, configManager);
        stateFile = new File(plugin.getDataFolder(), "dev-items-state.yml");
        io = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(128), runnable -> {
            final Thread worker = new Thread(runnable, "IceSMP-DEV-artifact-storage");
            worker.setDaemon(false);
            return worker;
        }, new ThreadPoolExecutor.AbortPolicy());
        register(new DevArtifactRegistration(BingulusRewardBehavior.definition(configManager),
                new BingulusRewardBehavior(plugin, configManager, messageManager, uniqueMaterials,
                        recipeCatalog, blueprintFactory, recipeBuilder)));
        register(new DevArtifactRegistration(WorldWeaverArtifactBehavior.definition(() -> WORLD_WEAVER_ENABLED),
                new WorldWeaverArtifactBehavior(interaction -> ArtifactInteractionResult.UNAVAILABLE)));
    }

    public synchronized void register(final DevArtifactRegistration registration) {
        if (ledger != null || entries.size() >= 32 || entries.containsKey(registration.definition().id())) {
            throw new IllegalStateException("Artifact registration closed, duplicate or capacity reached");
        }
        entries.put(registration.definition().id(), new Entry(registration));
    }
    public DevItemFactory itemFactory() { return itemFactory; }
    public UUID ownerUuid() { return state(DevItemFactory.BINGULUS_ID).owner(); }
    public boolean isOwner(final Player player) { return player != null && ownerUuid().equals(player.getUniqueId()); }
    public DevArtifactState state(final String id) {
        final DevArtifactLedger current = ledger;
        return current == null ? null : current.state(id);
    }
    private UUID configuredOwner(final Entry entry) {
        return entry.definition().ownerPolicy().resolve(configManager::getString);
    }
    private boolean healthy() {
        return ledger != null && ledger.healthy() && !YamlStore.isLoadFailed(stateFile) && !YamlStore.hasWriteFailure(stateFile);
    }
    private void storageFailed() {
        ready = false;
        if (storageWarning.compareAndSet(false, true)) {
            plugin.getLogger().severe("DEV artifact storage unavailable; issuance and behavior are suspended.");
        }
    }
    private void quarantine(final Entry entry) {
        if (!entry.quarantined) {
            entry.quarantined = true;
            plugin.getLogger().warning("A DEV artifact adapter was quarantined; recovery requires a validated restart.");
        }
        try { entry.behavior().onUnavailable(); } catch (final RuntimeException ignored) { /* Quarantine is terminal. */ }
    }
    private void guarded(final Entry entry, final Runnable action) {
        if (entry.quarantined) return;
        try { action.run(); } catch (final RuntimeException failure) { quarantine(entry); }
    }

    @Override public void load() {
        if (ledger != null) throw new IllegalStateException("Artifact store already loaded");
        final Map<String, DevArtifactState> loaded = new LinkedHashMap<>();
        if (stateFile.exists()) {
            final YamlConfiguration yaml = YamlStore.loadTracked(stateFile, plugin.getLogger());
            try {
                loaded.putAll(DevArtifactStateCodec.decode(yamlMap(yaml), value -> {
                    if (!(value instanceof ItemStack item) || item.getType().isAir() || item.getAmount() <= 0) {
                        throw new IllegalArgumentException("Invalid legacy pending reward");
                    }
                    return Base64.getEncoder().encodeToString(item.serializeAsBytes());
                }));
            } catch (final RuntimeException invalid) {
                YamlStore.failCorrupt(stateFile, plugin.getLogger(), "Invalid DEV artifact state schema or behavior data");
            }
        }
        try {
            for (final Entry entry : entries.values()) {
                final String id = entry.definition().id();
                loaded.putIfAbsent(id, new DevArtifactState(configuredOwner(entry), UUID.randomUUID(), false, 0,
                        entry.behavior().initialState()));
                if (entry.definition().ownerPolicy() instanceof FixedArtifactOwner
                        && !loaded.get(id).owner().equals(configuredOwner(entry))) {
                    throw new IllegalArgumentException("Fixed artifact authority differs from persisted owner");
                }
                entry.behavior().validateState(loaded.get(id));
                entry.behavior().loadBehaviorState(loaded.get(id).behaviorState());
            }
        } catch (final RuntimeException invalid) {
            YamlStore.failCorrupt(stateFile, plugin.getLogger(), "Invalid registered DEV artifact state");
        }
        if (loaded.size() > 32) YamlStore.failCorrupt(stateFile, plugin.getLogger(), "DEV artifact capacity exceeded");
        ledger = new DevArtifactLedger(loaded, io, snapshot -> {
            final YamlConfiguration yaml = new YamlConfiguration();
            DevArtifactStateCodec.encode(snapshot).forEach(yaml::set);
            YamlStore.saveAtomic(stateFile, yaml);
        });
    }
    private static Map<String, Object> yamlMap(final ConfigurationSection section) {
        final Map<String, Object> result = new LinkedHashMap<>();
        section.getValues(false).forEach((key, value) -> result.put(key,
                value instanceof ConfigurationSection child ? yamlMap(child) : value));
        return result;
    }
    public void start() {
        if (ledger == null || shuttingDown || tickTask != null) throw new IllegalStateException("Artifact lifecycle unavailable");
        ledger.save(false).whenComplete((saved, failure) -> {
            if (failure != null) { storageFailed(); return; }
            if (shuttingDown) return;
            ready = true;
            DevArtifactRuntimeProbe.maybeRun(plugin, this);
            Bukkit.getGlobalRegionScheduler().run(plugin, task -> refreshOnlineOwner());
        });
        tickTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task -> queueTicks(), 20L, 20L);
    }
    public void refreshOnlineOwner() {
        if (!ready || shuttingDown || !healthy()) return;
        for (final Entry entry : entries.values()) guarded(entry, () -> {
            final DevArtifactState before = state(entry.definition().id());
            final UUID configured = configuredOwner(entry);
            if (!before.owner().equals(configured)) {
                if (ledger.pending(entry.definition().id())) return;
                entry.behavior().onUnavailable();
                ledger.commit(entry.definition().id(), before.revision(), current -> current.next(configured,
                        current.instanceId(), current.issued(), current.behaviorState())).whenComplete((saved, failure) -> {
                    if (failure != null) { if (!healthy()) storageFailed(); return; }
                    clearPlayerState(before.owner());
                    refreshOnlineItems();
                });
            }
            entry.behavior().onConfigurationReload();
        });
        refreshOnlineItems();
    }
    private void refreshOnlineItems() {
        if (!ready || shuttingDown) return;
        for (final Player locator : Bukkit.getOnlinePlayers()) {
            final UUID id = locator.getUniqueId();
            schedulePlayer(id, player -> {
                openSessionIfOwner(id);
                cleanForeignItems(player);
                for (final Entry entry : entries.values()) guarded(entry, () -> {
                    if (id.equals(state(entry.definition().id()).owner())) ensureItem(player, entry, false, true, null);
                });
            }, () -> {});
        }
    }
    private void openSessionIfOwner(final UUID id) {
        if (entries.keySet().stream().anyMatch(key -> id.equals(state(key).owner()))) {
            sessions.ensure(id);
        }
    }
    public void handleJoin(final Player player) {
        requireOwnerThread(player);
        clearPlayerState(player.getUniqueId());
        if (ledger == null) return;
        openSessionIfOwner(player.getUniqueId());
        cleanForeignItems(player);
        if (!ready) return;
        for (final Entry entry : entries.values()) guarded(entry, () -> {
            if (player.getUniqueId().equals(state(entry.definition().id()).owner())) ensureItem(player, entry, false, true, null);
        });
    }
    public void handleRespawn(final Player player) { handleJoin(player); }
    @Override public void clearPlayerState(final UUID id) {
        sessions.close(id);
        inputs.remove(id);
        for (final Entry entry : entries.values()) {
            final DevArtifactState state = state(entry.definition().id());
            if (state != null && id.equals(state.owner())) entry.behavior().onUnavailable();
        }
    }
    private void queueTicks() {
        if (!ready || shuttingDown || !healthy()) return;
        final long now = System.currentTimeMillis();
        for (final Entry entry : entries.values()) guarded(entry, () -> {
            if (!entry.policy().enabled() || now < entry.nextTick || !entry.tickQueued.compareAndSet(false, true)) return;
            entry.nextTick = now + entry.policy().checkIntervalTicks() * 50L;
            final DevArtifactState before = state(entry.definition().id());
            final DevArtifactContext context = context(entry, before);
            if (context == null) { entry.behavior().onUnavailable(); entry.tickQueued.set(false); return; }
            dispatchOwner(context, player -> {
                try {
                    guarded(entry, () -> {
                        if (ensureItem(player, entry, false, false, null)) entry.behavior().tick(context(entry, state(entry.definition().id())), now);
                        else entry.behavior().onUnavailable();
                    });
                } finally { entry.tickQueued.set(false); }
            }, () -> { entry.behavior().onUnavailable(); entry.tickQueued.set(false); }, false);
        });
    }
    private DevArtifactContext context(final Entry entry, final DevArtifactState value) {
        final Long session = sessions.current(value.owner());
        return session == null ? null : new DevArtifactContext(this, entry.definition().id(), value.owner(), value.instanceId(), session);
    }
    public boolean contextValid(final DevArtifactContext context) {
        final Entry entry = entries.get(context.artifactId());
        final DevArtifactState current = state(context.artifactId());
        return ready && !shuttingDown && healthy() && context.manager() == this && entry != null
                && !entry.quarantined && entry.policy().enabled() && current != null && !ledger.pending(context.artifactId())
                && sessions.matches(context.owner(), context.session())
                && current.owner().equals(context.owner()) && current.instanceId().equals(context.instanceId());
    }
    private Player identityPlayer(final DevArtifactContext context) {
        if (!contextValid(context)) return null;
        final Player player = Bukkit.getPlayer(context.owner());
        if (player == null || !Bukkit.isOwnedByCurrentRegion(player)) return null;
        return player.isOnline() && !player.isDead() ? player : null;
    }
    public Player contextPlayer(final DevArtifactContext context) {
        final Player player = identityPlayer(context);
        if (player == null) return null;
        final Entry entry = entries.get(context.artifactId());
        final DevArtifactState state = context.state();
        if (entry.policy().mainHandOnly()
                && !matches(entry, player.getInventory().getItemInMainHand(), state, context.owner())) return null;
        int copies = 0;
        for (final ItemStack item : player.getInventory().getContents()) {
            if (context.artifactId().equals(itemFactory.itemIdOf(item))) {
                if (!matches(entry, item, state, context.owner())) return null;
                copies++;
            }
        }
        final ItemStack cursor = player.getItemOnCursor();
        if (context.artifactId().equals(itemFactory.itemIdOf(cursor))) {
            if (!matches(entry, cursor, state, context.owner())) return null;
            copies++;
        }
        return copies == 1 ? player : null;
    }
    public boolean updateBehavior(final DevArtifactContext context, final long revision, final Map<String, Object> state) {
        if (contextPlayer(context) == null) return false;
        final Entry entry = entries.get(context.artifactId());
        entry.behavior().validateState(context.state().next(context.owner(), context.instanceId(), context.state().issued(), state));
        final boolean updated = ledger.updateVolatile(context.artifactId(), revision, state);
        if (updated) entry.behavior().loadBehaviorState(state);
        return updated;
    }
    public CompletionStage<DevArtifactState> commitBehavior(final DevArtifactContext context, final long revision,
                                                            final Map<String, Object> behavior) {
        if (contextPlayer(context) == null) return CompletableFuture.failedFuture(new IllegalStateException("Stale artifact context"));
        final Entry entry = entries.get(context.artifactId());
        entry.behavior().validateState(context.state().next(context.owner(), context.instanceId(), context.state().issued(), behavior));
        final CompletionStage<DevArtifactState> commit = ledger.commit(context.artifactId(), revision,
                current -> current.next(current.owner(), current.instanceId(), current.issued(), behavior));
        commit.whenComplete((saved, failure) -> { if (failure != null && !healthy()) storageFailed(); });
        return commit.thenApply(saved -> {
            try { entry.behavior().loadBehaviorState(saved.behaviorState()); }
            catch (final RuntimeException failure) { quarantine(entry); throw failure; }
            return saved;
        });
    }
    public boolean renderState(final DevArtifactContext context, final DevArtifactPresentation.ModelState model) {
        final Player player = contextPlayer(context);
        if (player == null) return false;
        final Entry entry = entries.get(context.artifactId());
        final ItemStack replacement = itemFactory.create(entry.definition(), context.owner(), context.instanceId(), model);
        final ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (context.artifactId().equals(itemFactory.itemIdOf(contents[slot]))) {
                player.getInventory().setItem(slot, replacement);
            }
        }
        if (context.artifactId().equals(itemFactory.itemIdOf(player.getItemOnCursor()))) player.setItemOnCursor(replacement);
        entry.model = model;
        return true;
    }
    public void compensateUnstartedBehavior(final DevArtifactContext context, final DevArtifactState claimed,
                                            final Map<String, Object> beforeBehavior) {
        final Entry entry = entries.get(context.artifactId());
        if (entry == null || context.manager() != this || !healthy()) return;
        final DevArtifactState current = state(context.artifactId());
        if (!current.equals(claimed)) return;
        entry.behavior().validateState(current.next(current.owner(), current.instanceId(), current.issued(), beforeBehavior));
        ledger.commit(context.artifactId(), claimed.revision(), value -> value.next(value.owner(), value.instanceId(),
                value.issued(), beforeBehavior)).whenComplete((saved, failure) -> {
            if (failure != null && !healthy()) storageFailed();
        });
    }
    public void onOwner(final DevArtifactContext context, final Consumer<Player> action, final Runnable unavailable) {
        dispatchOwner(context, player -> guarded(entries.get(context.artifactId()), () -> action.accept(player)), unavailable, true);
    }
    private void dispatchOwner(final DevArtifactContext context, final Consumer<Player> action,
                               final Runnable unavailable, final boolean requireItem) {
        if (!contextValid(context)) { unavailable.run(); return; }
        schedulePlayer(context.owner(), player -> {
            if ((requireItem ? contextPlayer(context) : identityPlayer(context)) != null) action.accept(player);
            else unavailable.run();
        }, unavailable);
    }
    private void schedulePlayer(final UUID id, final Consumer<Player> action, final Runnable unavailable) {
        final Player locator = Bukkit.getPlayer(id);
        if (locator == null || shuttingDown) { unavailable.run(); return; }
        final AtomicBoolean completed = new AtomicBoolean();
        final Runnable retire = () -> { if (completed.compareAndSet(false, true)) unavailable.run(); };
        try {
            final ScheduledTask task = locator.getScheduler().run(plugin, scheduled -> {
                if (!completed.compareAndSet(false, true)) return;
                final Player player = Bukkit.getPlayer(id);
                if (player == null || !Bukkit.isOwnedByCurrentRegion(player) || !player.isOnline() || shuttingDown) {
                    unavailable.run(); return;
                }
                action.accept(player);
            }, retire);
            if (task == null) retire.run();
        } catch (final RuntimeException failure) { retire.run(); }
    }
    private static void requireOwnerThread(final Player player) {
        if (!Bukkit.isOwnedByCurrentRegion(player)) throw new IllegalStateException("Foreign player artifact access");
    }
    public void giveToOwner(final Player player, final BiConsumer<Player, Boolean> completion) {
        requireOwnerThread(player);
        final Entry entry = entries.get(DevItemFactory.BINGULUS_ID);
        if (!isOwner(player) || !ready || shuttingDown || !healthy() || entry.quarantined) {
            completion.accept(player, false); return;
        }
        openSessionIfOwner(player.getUniqueId());
        ensureItem(player, entry, true, true, completion);
    }
    private boolean ensureItem(final Player player, final Entry entry, final boolean explicit,
                               final boolean refresh, final BiConsumer<Player, Boolean> completion) {
        requireOwnerThread(player);
        final String id = entry.definition().id();
        final DevArtifactState before = state(id);
        final DevArtifactContext context = context(entry, before);
        if (!before.owner().equals(player.getUniqueId()) || context == null || !contextValid(context) || ledger.pending(id) || player.isDead()) {
            if (completion != null) completion.accept(player, false);
            return false;
        }
        int valid = 0;
        int slotFound = -1;
        final ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (!id.equals(itemFactory.itemIdOf(contents[slot]))) continue;
            if (matches(entry, contents[slot], before, player.getUniqueId())
                    && !(entry.policy().mainHandOnly() && slot == 40)) { valid++; slotFound = slot; }
            else player.getInventory().setItem(slot, null);
        }
        final ItemStack cursor = player.getItemOnCursor();
        if (id.equals(itemFactory.itemIdOf(cursor))) {
            if (matches(entry, cursor, before, player.getUniqueId())) valid++;
            else player.setItemOnCursor(new ItemStack(Material.AIR));
        }
        for (int slot = 0; slot < player.getEnderChest().getSize(); slot++) {
            if (id.equals(itemFactory.itemIdOf(player.getEnderChest().getItem(slot)))) player.getEnderChest().setItem(slot, null);
        }
        if (valid == 1) {
            if (refresh && slotFound >= 0) player.getInventory().setItem(slotFound, itemFactory.create(entry.definition(), before.owner(), before.instanceId(), entry.model));
            if (completion != null) completion.accept(player, true);
            return true;
        }
        if (valid > 1) removeItems(player, id);
        final DevArtifactPolicy policy = entry.policy();
        if ((!explicit && !(policy.autoRestore() && (before.issued() || policy.issueOnJoin())))
                || player.getInventory().firstEmpty() < 0) {
            if (completion != null) completion.accept(player, false);
            return false;
        }
        ledger.commit(id, before.revision(), current -> current.next(current.owner(), UUID.randomUUID(), true,
                current.behaviorState())).whenComplete((issued, failure) -> {
            if (failure != null) { if (!healthy()) storageFailed(); return; }
            final DevArtifactContext issuedContext = new DevArtifactContext(this, id, issued.owner(), issued.instanceId(), context.session());
            dispatchOwner(issuedContext, online -> guarded(entry, () -> {
                removeItems(online, id);
                final int slot = online.getInventory().firstEmpty();
                if (slot >= 0) {
                    online.getInventory().setItem(slot, itemFactory.create(entry.definition(), issued.owner(), issued.instanceId(), entry.model));
                    if (before.issued()) entry.behavior().onRecovered(issuedContext); else entry.behavior().onIssued(issuedContext);
                }
                if (completion != null) completion.accept(online, slot >= 0);
            }), () -> {}, false);
        });
        return false;
    }
    private boolean matches(final Entry entry, final ItemStack item, final DevArtifactState state, final UUID actor) {
        return item != null && item.getAmount() == 1 && state.matches(actor, itemFactory.ownerOf(item), itemFactory.instanceOf(item))
                && item.isSimilar(itemFactory.create(entry.definition(), state.owner(), state.instanceId(), entry.model));
    }
    private void cleanForeignItems(final Player player) {
        requireOwnerThread(player);
        for (final ItemStack item : player.getInventory().getContents()) {
            final String id = itemFactory.itemIdOf(item);
            if (id != null && (!entries.containsKey(id) || !player.getUniqueId().equals(state(id).owner()))) removeItems(player, id);
        }
        final String cursor = itemFactory.itemIdOf(player.getItemOnCursor());
        if (cursor != null && (!entries.containsKey(cursor) || !player.getUniqueId().equals(state(cursor).owner()))) removeItems(player, cursor);
        for (int slot = 0; slot < player.getEnderChest().getSize(); slot++) {
            if (itemFactory.isDevItem(player.getEnderChest().getItem(slot))) player.getEnderChest().setItem(slot, null);
        }
    }
    private void removeItems(final Player player, final String id) {
        final ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (id.equals(itemFactory.itemIdOf(contents[slot]))) player.getInventory().setItem(slot, null);
        }
        if (id.equals(itemFactory.itemIdOf(player.getItemOnCursor()))) player.setItemOnCursor(new ItemStack(Material.AIR));
    }
    public boolean mainHandOnly(final ItemStack item) {
        final Entry entry = entries.get(itemFactory.itemIdOf(item));
        return entry == null || entry.policy().mainHandOnly();
    }
    public ArtifactInteractionResult interact(final Player player, final DevArtifactInteraction.Kind kind,
                                             final UUID entityId, final DevArtifactInteraction.BlockPosition block) {
        requireOwnerThread(player);
        final ItemStack held = player.getInventory().getItemInMainHand();
        final Entry entry = entries.get(itemFactory.itemIdOf(held));
        if (entry == null || !ensureItem(player, entry, false, false, null)
                || !matches(entry, held, state(entry.definition().id()), player.getUniqueId())) {
            cleanForeignItems(player);
            return ArtifactInteractionResult.AUTHORITY_REJECTED;
        }
        final InputStamp input = new InputStamp(Bukkit.getCurrentTick(), kind, entityId);
        if (input.equals(inputs.put(player.getUniqueId(), input))) return ArtifactInteractionResult.IGNORED;
        try {
            return entry.behavior().onInteract(new DevArtifactInteraction(context(entry, state(entry.definition().id())),
                    kind, player.isSneaking(), entityId, block));
        } catch (final RuntimeException failure) { quarantine(entry); return ArtifactInteractionResult.UNAVAILABLE; }
    }
    public record Diagnostics(boolean ready, boolean healthy, boolean shuttingDown, boolean scheduled,
                              int registeredArtifacts, int persistedArtifacts) {}
    public Diagnostics diagnostics() {
        return new Diagnostics(ready, healthy(), shuttingDown, tickTask != null, entries.size(),
                ledger == null ? 0 : ledger.snapshot().size());
    }
    public Map<String, DevArtifactState> stateSnapshot() { return ledger == null ? Map.of() : ledger.snapshot(); }
    @Override public void save() {
        if (shuttingDown) { finishStorage(); return; }
        if (!healthy()) { storageFailed(); return; }
        ledger.save(false).whenComplete((saved, failure) -> { if (failure != null) storageFailed(); });
    }
    private void finishStorage() {
        if (!closing.compareAndSet(false, true)) return;
        if (!healthy()) { storageFailed(); io.shutdown(); DevArtifactRuntimeProbe.storageClosed(plugin, this, false); return; }
        ledger.save(true).whenComplete((saved, failure) -> {
            if (failure != null) storageFailed();
            DevArtifactRuntimeProbe.storageClosed(plugin, this, failure == null);
        });
        io.shutdown();
    }
    public synchronized void shutdown() {
        shuttingDown = true;
        ready = false;
        if (tickTask != null) { tickTask.cancel(); tickTask = null; }
        sessions.clear();
        inputs.clear();
        for (final Entry entry : entries.values()) guarded(entry, () -> entry.behavior().shutdown());
        finishStorage();
    }
}
