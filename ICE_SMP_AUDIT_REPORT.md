# IceSMP Comprehensive Audit Report

**Scope reviewed:** `src/main/java`, `src/main/resources/messages.yml`, `src/main/resources/config.yml`, and `src/main/resources/paper-plugin.yml`.

**Build validation:** `.\gradlew.bat build` ✅

## Executive summary

Overall, the project is in a **good state architecturally**:

- Constructor injection is used consistently for plugin-managed services.
- Command handling is router-based and scalable through Paper's `BasicCommand` API.
- Player cleanup is centralized and covers most session-backed spell state.
- Messaging is already centralized through `MessageManager` for many code paths.

However, there are still several **real issues** worth addressing:

1. Some player-facing text is still hardcoded directly in gameplay code.
2. `messages.yml` and `config.yml` both contain message content, but only `messages.yml` is actually loaded by `MessageManager`.
3. Several spell implementations perform expensive, synchronous world scans on the main thread.
4. `HideSpell` stores armor state only in memory, which is unsafe if the server crashes before cleanup.
5. `ArmamentSpell` can silently lose the generated sword if the inventory is full.
6. `MetelytepoManager` has a small entity-state leak when frozen undead die or disappear before restoration.
7. A few PDC key patterns are inconsistent, using hardcoded namespaces where constructor-scoped keys would be clearer.
8. Some spell interactions are still ambiguous, especially `Hide` vs `InnerFocus`.

---

## 1. Architecture & Design Patterns

### What is already good

- `IceSMP` creates a single `IceSMPCore` instance and delegates lifecycle work to it.
- Managers are passed through constructors; there is no widespread static service locator pattern.
- Commands are organized into routers and subcommands instead of giant switch blocks.
- `Spell` already centralizes common cost helpers, and `SpellbookListener` owns the actual cast/cooldown pipeline.

### Issue 1.1 — `MessageManager` accepts `ConfigManager` but never uses it

**Severity:** Low / maintainability

**Where:** `MessageManager`

`MessageManager` is constructed with a `ConfigManager`, but the parameter is never referenced. That makes the dependency misleading and suggests either unfinished design or dead injection.

**Impact:**
- Confuses readers about the source of message defaults.
- Makes the constructor contract noisier than it needs to be.
- Hides whether `messages.yml` or `config.yml` is the intended source of truth.

**Recommended correction:** Remove the unused dependency, or actually use it if the plan is to layer message defaults from `config.yml`.

```java
public final class MessageManager {

    private final JavaPlugin plugin;
    private final File messagesFile;
    private YamlConfiguration messagesConfiguration;

    public MessageManager(final JavaPlugin plugin) {
        this.plugin = plugin;
        this.messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        load();
    }

    // ...existing code...
}
```

### Issue 1.2 — Base spell abstraction is only partly centralized

**Severity:** Low / design improvement

**Where:** `Spell`, `BaseSpell`, `SpellbookListener`

The project has the right direction: shared spell metadata lives in `BaseSpell`, and cost/cooldown logic is already centralized in `SpellbookListener` plus default methods on `Spell`. That said, some spells still override `canCast()` and `consumeCost()` in a way that bypasses the generic flow.

**Impact:**
- Different spells can behave slightly differently unless carefully reviewed.
- Future spells may accidentally reimplement the same checks.

**Recommended correction:** Keep `BaseSpell` as the immutable metadata holder, and move all common cast validation into a single template path if you want stricter consistency.

```java
public abstract class BaseSpell implements Spell {

    // metadata only
    // ...existing code...

    public final boolean canCastByDefault(final Player player) {
        return canCast(player) && hasRequiredCost(player);
    }
}
```

### Issue 1.3 — Command framework is scalable already

**Status:** Best practice

The admin command stack is reasonably well-designed:

- `JobCommand`, `CurrencyCommand`, `FactionCommand`, and `RelicCommand` all use subcommand delegation.
- Tab completion is implemented through Paper's `suggest(...)` API, which is the correct equivalent of a Bukkit `TabCompleter` in this setup.
- `JobAdminSubcommand` is already split out instead of being embedded in one huge handler.

**Verdict:** No architectural rewrite is needed here.

---

## 2. Performance & Thread Safety

### Issue 2.1 — `RainDanceSpell` and `SunDanceSpell` are very expensive on the main thread

**Severity:** High

**Where:** `RainDanceSpell`, `SunDanceSpell`

Both spells iterate a full cubic volume around the player:

- `RainDanceSpell`: radius 50  over **1,000,000** coordinate checks
- `SunDanceSpell`: radius 25  over **132,000** coordinate checks

Even with the sphere test, this is still expensive when executed synchronously in a live world. `SunDanceSpell` also does recipe lookup work on the first run.

`RainDanceSpell` is somewhat optimized because it checks `getType()` before `getBlockData()`, but the overall loop is still the bottleneck.

**Impact:**
- Visible lag spikes on the main server thread.
- Chunk access and block state loading can amplify the cost.
- Repeated casts can feel like server freezes.

**Recommended correction:** Process work in batches over multiple ticks, and precompute or cache the block candidates.

```java
final List<Block> candidates = new ArrayList<>();
for (int x = -radius; x <= radius; x++) {
    for (int y = -radius; y <= radius; y++) {
        for (int z = -radius; z <= radius; z++) {
            if ((x * x) + (y * y) + (z * z) > (radius * radius)) {
                continue;
            }
            candidates.add(center.getBlock().getRelative(x, y, z));
        }
    }
}

new BukkitRunnable() {
    private int index;

    @Override
    public void run() {
        for (int processed = 0; processed < 200 && index < candidates.size(); processed++, index++) {
            final Block block = candidates.get(index);
            // cheap type checks first, then expensive state/data access
            // ...existing logic...
        }

        if (index >= candidates.size()) {
            cancel();
        }
    }
}.runTaskTimer(plugin, 1L, 1L);
```

### Issue 2.2 — `SunDanceSpell` recipe cache initialization is not synchronized

**Severity:** Medium

**Where:** `SunDanceSpell`

`recipeCachePopulated` is a plain boolean and `RECIPE_CACHE` is populated lazily. In normal gameplay this is usually fine because casts are on the main thread, but the initialization is still not thread-safe and is harder to reason about than a one-time bootstrap cache.

**Impact:**
- Risk of duplicate initialization if the code path changes later.
- Harder to maintain if recipe loading is moved or reused elsewhere.

**Recommended correction:** Populate the cache once during plugin enable, or guard it with `AtomicBoolean`.

```java
private static final AtomicBoolean RECIPE_CACHE_READY = new AtomicBoolean(false);

private static void populateRecipeCache() {
    if (!RECIPE_CACHE_READY.compareAndSet(false, true)) {
        return;
    }

    final Map<Material, List<CookingRecipe<?>>> cache = new EnumMap<>(Material.class);
    // load recipes once
    // ...existing code...
}
```

### Issue 2.3 — Several scheduler tasks are safe, but some use recursive rescheduling

**Severity:** Low / acceptable with caution

**Where:** `LuckyStarSpell`, `AngryChickenSpell`, `WisplightSpell`

These tasks are generally canceled correctly through cleanup paths.

- `LuckyStarSpell` cancels its task when the player deactivates or runs out of XP.
- `AngryChickenSpell` cancels its repeating task when the projectile dies or hits something.
- `WisplightSpell` uses a delayed cleanup task to remove temporary light.

**Potential improvement:** Prefer a single repeating task or a tracked task handle instead of recursive rescheduling in `LuckyStarSpell` for easier lifecycle auditing.

---

## 3. Data Persistence & State Management

### Issue 3.1 — `HideSpell` can permanently lose armor if the server crashes before cleanup

**Severity:** High

**Where:** `HideSpell`

`HideSpell` removes the player's armor and stores the original armor array in a static in-memory map:

- If the player quits normally, `PlayerSessionCleanupListener` restores the armor.
- If the server crashes or the plugin is unloaded unexpectedly, that in-memory backup is lost.

This is the clearest "permanent loss" risk in the current codebase.

**Impact:**
- Original armor can disappear permanently after a crash.
- The state is only safe as long as the JVM and plugin memory survive.

**Recommended correction:** Persist the backup to PDC or a durable file and restore it on join if needed.

```java
private void storeArmorBackup(final Player player) {
    final PersistentDataContainer pdc = player.getPersistentDataContainer();
    pdc.set(HIDDEN_ARMOR_KEY, PersistentDataType.STRING, serializeArmor(player.getInventory().getArmorContents()));
}

private void restoreArmorBackup(final Player player) {
    final String serialized = player.getPersistentDataContainer().get(HIDDEN_ARMOR_KEY, PersistentDataType.STRING);
    if (serialized == null) {
        return;
    }

    player.getInventory().setArmorContents(deserializeArmor(serialized));
    player.getPersistentDataContainer().remove(HIDDEN_ARMOR_KEY);
}
```

### Issue 3.2 — `ArmamentSpell` can lose its generated sword if inventory is full

**Severity:** Medium

**Where:** `ArmamentSpell`

`player.getInventory().addItem(sword);` ignores the leftover map. If the inventory is full, the sword is not guaranteed to be retained or dropped.

**Impact:**
- The spell can silently fail to deliver its weapon.
- Player experience becomes inconsistent when inventory space is tight.

**Recommended correction:** Handle leftovers explicitly.

```java
final Map<Integer, ItemStack> leftovers = player.getInventory().addItem(sword);
if (!leftovers.isEmpty()) {
    leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
}
```

### Issue 3.3 — `ArmamentSpell` uses a static `NamespacedKey` field unnecessarily

**Severity:** Low / design smell

**Where:** `ArmamentSpell`

`staticArmamentTag` is a static field populated from the constructor. This is not needed because the key is already an instance field.

**Impact:**
- Adds hidden global state.
- Makes cleanup helpers rely on constructor order.
- Complicates reload semantics.

**Recommended correction:** Keep the key instance-scoped and pass it to cleanup helpers, or move all tag logic behind one instance method.

```java
public void cleanup(final Player player) {
    removeTaggedItems(player, armamentTag);
}

private void removeTaggedItems(final Player player, final NamespacedKey key) {
    // ...existing code...
}
```

### Issue 3.4 — `MetelytepoManager.freezeUndead(...)` leaks entries if the entity vanishes early

**Severity:** Medium

**Where:** `MetelytepoManager`

The method stores original movement speed in `frozenSpeed`, then schedules restoration. If the entity becomes invalid before the task runs, the code returns early and does not remove the UUID from the map.

There is a similar lifecycle risk with `abilityDamageBypass`: it is removed only when consumed or explicitly cleared for players, but no TTL cleanup exists for entities that never get hit again.

**Impact:**
- Gradual memory growth over long sessions.
- Hard-to-notice leak because the values are small and sparse.

**Recommended correction:** Always remove map entries in the scheduled task, even if the entity is invalid.

```java
new BukkitRunnable() {
    @Override
    public void run() {
        final UUID id = target.getUniqueId();
        final Double original = frozenSpeed.remove(id);

        if (!target.isValid()) {
            abilityDamageBypass.remove(id);
            return;
        }

        target.setAI(true);
        if (original != null) {
            final AttributeInstance speed = target.getAttribute(Attribute.MOVEMENT_SPEED);
            if (speed != null) {
                speed.setBaseValue(original);
            }
        }
    }
}.runTaskLater(plugin, ticks);
```

### Issue 3.5 — `RelicCooldownService` and most player-backed maps are handled correctly

**Status:** Best practice

This part of the codebase is mostly solid:

- `PlayerSessionCleanupListener` clears player-linked spell state on quit/kick.
- `RelicCooldownService.clearPlayer(...)` removes per-player cooldown entries.
- `DoubleJumpSpell`, `LuckyStarSpell`, `HideSpell`, and `InnerFocusSpell` all expose cleanup helpers.

**Verdict:** Good cleanup discipline overall, with the `MetelytepoManager` entity maps being the main exception.

### Issue 3.6 — `SpellbookListener` PDC cooldown keys are okay, but the namespace is hardcoded

**Severity:** Low

**Where:** `SpellbookListener`

`resolveLongCooldownKey(...)` uses `new NamespacedKey("icesmp", "cd_" + id)` rather than a plugin-scoped `NamespacedKey` created from the plugin instance. It works, but it is less uniform than the rest of the codebase.

**Recommended correction:** Inject a plugin-scoped key factory or store a `JavaPlugin` reference and use that everywhere.

```java
private final JavaPlugin plugin;

private NamespacedKey resolveLongCooldownKey(final String spellId) {
    return longCooldownKeys.computeIfAbsent(
            normalize(spellId),
            id -> new NamespacedKey(plugin, "cd_" + id)
    );
}
```

---

## 4. Mechanics & Edge Cases

### Issue 4.1 — `Hide` and `InnerFocus` can conflict in a confusing way

**Severity:** Medium

**Where:** `HideSpell`, `InnerFocusSpell`

`InnerFocusSpell` freezes walk speed to `0.0F`. `HideSpell` teleports, grants invisibility, and gives speed, but it does not know about `InnerFocus`.

If a player is under `InnerFocus` and then uses `Hide`, they remain immobile until the `InnerFocus` restore fires. That is not a data corruption bug, but it is a mechanic conflict that can feel broken.

**Impact:**
- Player expects mobility during hide, but movement remains blocked.
- Two independent timers can restore movement in a surprising order.

**Recommended correction:** Make the interaction explicit: either refuse to cast Hide while frozen, or clear the InnerFocus state first.

```java
@Override
public void execute(final Player player) {
    if (InnerFocusSpell.isFrozen(player)) {
        player.sendMessage(messageManager.getMessage("spell.hide-conflict", "<red>You cannot hide while Inner Focus is active.</red>"));
        return;
    }

    // ...existing hide logic...
}
```

### Issue 4.2 — `FriendshipSpell` can still target through walls in its fallback path

**Severity:** Medium

**Where:** `FriendshipSpell`, `SpellTargetingUtil`

`SpellTargetingUtil.rayTraceLivingEntity(...)` is good about filtering to living entities, but `FriendshipSpell` also has a fallback scan over nearby entities using only direction alignment and distance. That fallback does not verify line of sight.

**Impact:**
- Tameable entities can be affected through walls if they are nearby and aligned.
- Target selection feels inconsistent between ray-trace and fallback.

**Recommended correction:** Require line of sight in the fallback, or switch fully to a unified ray-trace path.

```java
if (!player.hasLineOfSight(tameable)) {
    continue;
}
```

### Issue 4.3 — `SunDanceSpell` furnace snapshot refresh is the right idea

**Status:** Best practice with caveat

The "next-tick force update" pattern in `SunDanceSpell` is a reasonable attempt to avoid live snapshot overrides after mutating furnace inventories. That part of the implementation is conceptually sound.

**Caveat:** It should be paired with a smaller search radius or batched processing to avoid the performance issue described earlier.

---

## 5. Localization & UX

### Issue 5.1 — Player-facing text is **not** 100% centralized in `messages.yml`

**Severity:** High

**Where:** Across spells, GUI classes, and listeners

Despite `MessageManager`, a lot of player-facing strings are still hardcoded in code. Examples include:

- `SunDanceSpell` status messages
- `RainDanceSpell` and `FeastSpell` lack message-based feedback entirely
- `AngryChickenSpell` sound-only feedback
- `ProfileGUI` and `JobGUI` titles/lore text
- `MetelytepoRelicListener` titles and some fallback messaging

This violates the target convention that player-facing text should come from `messages.yml`.

**Impact:**
- Inconsistent localization coverage.
- Harder to translate or tweak wording.
- Mixed legacy and MiniMessage formatting across the project.

**Recommended correction:** Route all visible text through `MessageManager` and add missing keys to `messages.yml`.

```java
player.sendMessage(messageManager.getMessage(
        "spell.sun-dance-start",
        "<gold>The sun's power flows into the furnaces!</gold>"
));
```

### Issue 5.2 — `config.yml` contains a large duplicate message section that the code does not use

**Severity:** Medium

**Where:** `src/main/resources/config.yml`

`config.yml` includes a very large `messages:` section, but the runtime message layer reads `messages.yml` instead. I found no code path that reads `config.yml` message keys directly.

**Impact:**
- Two competing sources of truth.
- Wasted maintenance effort.
- Drift between `config.yml` and `messages.yml` is already visible.

**Recommended correction:** Keep only one message source. The cleanest option is to let `messages.yml` own all player-facing text and remove the duplicate `messages:` section from `config.yml`.

### Issue 5.3 — Some feedback is good, but inconsistency remains

**Status:** Mixed / mostly good

Good examples:

- `SpellbookListener` gives clear cooldown, invalid-selection, and no-cost action-bar feedback.
- `MetelytepoRelicListener` uses sounds, particles, action bars, and titles for ability feedback.
- `CurrencyCommand` and `FactionCommand` provide structured usage/help output.

Less consistent examples:

- Cosmetic spells often only play sounds or do nothing beyond the gameplay effect.
- Some fallback texts are still hardcoded instead of localized.

**Verdict:** UX is functional, but not fully standardized.

---

## Final verdict

### Best practices that are already followed

- Constructor injection is used correctly and consistently.
- Core lifecycle orchestration is centralized in `IceSMPCore`.
- Command handling is modular and scales well.
- Most player-linked state is cleaned up on quit/kick and on plugin disable.
- PDC usage is generally consistent and modern.
- Craft safety listeners are in place for tagged custom items.

### Main risks to fix next

1. Move all player-facing strings into `messages.yml`.
2. Reduce or batch the large world iteration spells.
3. Persist or restore `HideSpell` armor backups safely.
4. Handle `ArmamentSpell` inventory leftovers.
5. Fix the small entity-state leak in `MetelytepoManager`.
6. Remove dead config duplication and unused injected dependencies.

If you want, I can turn this audit into a concrete refactor plan or start patching the highest-risk items first.
