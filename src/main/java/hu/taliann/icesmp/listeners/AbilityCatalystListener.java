package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.items.CatalystItemFactory;
import hu.taliann.icesmp.managers.JobManager;
import hu.taliann.icesmp.managers.SpellRegistry;
import hu.taliann.icesmp.spells.Spell;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Interaction handler for the class-themed Ability Catalyst item:
 * right click casts the selected spell, sneak + arm swing cycles spells with a
 * class-specific sound. Owns the cast pipeline (cost check, cooldowns with PDC
 * persistence for cooldowns >= 60s) and the per-session spell state cleanup.
 */
public final class AbilityCatalystListener implements Listener {

    private final JobManager jobManager;
    private final SpellRegistry spellRegistry;
    private final CatalystItemFactory catalystItemFactory;
    private final MessageManager messageManager;
    private final NamespacedKey selectedSpellIndexKey;
    private final Map<String, NamespacedKey> longCooldownKeys = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Long>> spellCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cycleDebounce = new ConcurrentHashMap<>();
    private final Map<UUID, Long> castDebounce = new ConcurrentHashMap<>();

    public AbilityCatalystListener(final JavaPlugin plugin, final JobManager jobManager,
                                   final SpellRegistry spellRegistry, final CatalystItemFactory catalystItemFactory,
                                   final MessageManager messageManager) {
        this.jobManager = jobManager;
        this.spellRegistry = spellRegistry;
        this.catalystItemFactory = catalystItemFactory;
        this.messageManager = messageManager;
        this.selectedSpellIndexKey = new NamespacedKey(plugin, "selected_spell_index");
    }

    @EventHandler
    public void onPlayerInteract(final PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        final Player player = event.getPlayer();
        final ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (!catalystItemFactory.isCatalyst(mainHand)) {
            return;
        }

        final Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        // Block vanilla item/block behavior (e.g. the goat horn blast) but keep event flow for reliable cast handling.
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);

        final long now = System.currentTimeMillis();
        final long lastCastInteract = castDebounce.getOrDefault(player.getUniqueId(), 0L);
        if (now - lastCastInteract < 120L) {
            return;
        }
        castDebounce.put(player.getUniqueId(), now);

        castSelectedSpell(player);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerAnimation(final PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }

        final Player player = event.getPlayer();
        if (!player.isSneaking()) {
            return;
        }

        if (!catalystItemFactory.isCatalyst(player.getInventory().getItemInMainHand())) {
            return;
        }

        final long now = System.currentTimeMillis();
        final long lastCycle = cycleDebounce.getOrDefault(player.getUniqueId(), 0L);
        if (now - lastCycle < 120L) {
            return;
        }

        cycleDebounce.put(player.getUniqueId(), now);
        cycleSpell(player);
    }

    private void cycleSpell(final Player player) {
        final List<String> unlocked = resolveUnlockedSpellIds(player);
        if (unlocked.isEmpty()) {
            player.sendActionBar(messageManager.getMessage("catalyst.no-spells", "<red>Nincs elérhető képesség.</red>"));
            return;
        }

        final PersistentDataContainer pdc = player.getPersistentDataContainer();
        final int currentIndex = pdc.getOrDefault(selectedSpellIndexKey, PersistentDataType.INTEGER, -1);
        final int nextIndex = (currentIndex + 1) % unlocked.size();
        pdc.set(selectedSpellIndexKey, PersistentDataType.INTEGER, nextIndex);

        final Spell selected = spellRegistry.getById(unlocked.get(nextIndex));
        if (selected == null) {
            player.sendActionBar(messageManager.getMessage("catalyst.current-spell-unknown", "<gray>Aktuális képesség: Ismeretlen</gray>"));
            return;
        }

        player.sendActionBar(messageManager.getMessage(
                "catalyst.current-spell",
                "<gray>Aktuális képesség: <gold>{spell}</gold> <dark_gray>({cost} {resource})</dark_gray></gray>",
                Map.of(
                        "spell", selected.getName(),
                        "cost", String.valueOf(selected.getCostAmount()),
                        "resource", resolveResourceName(selected)
                )
        ));
        catalystItemFactory.playCycleSound(player, jobManager.getPrimaryJob(player));
    }

    private void castSelectedSpell(final Player player) {
        final List<String> unlocked = resolveUnlockedSpellIds(player);
        if (unlocked.isEmpty()) {
            player.sendActionBar(messageManager.getMessage("catalyst.no-unlocked", "<red>Még nem oldottál fel egyetlen képességet sem.</red>"));
            return;
        }

        final Spell selected = resolveSelectedSpell(player, unlocked);
        if (selected == null) {
            player.sendActionBar(messageManager.getMessage("catalyst.invalid-selection", "<red>A kiválasztott képesség nem érhető el.</red>"));
            return;
        }

        final long now = System.currentTimeMillis();
        final long remainingMs = getRemainingCooldown(player, selected, now);
        if (remainingMs > 0L) {
            final long remainingSeconds = (long) Math.ceil(remainingMs / 1000.0D);
            player.sendActionBar(messageManager.getMessage(
                    "catalyst.cooldown",
                    "<red>Várj még {seconds} mp-et!</red>",
                    Map.of("seconds", String.valueOf(remainingSeconds))
            ));
            return;
        }

        if (!selected.canCast(player)) {
            player.sendActionBar(messageManager.getMessage("catalyst.not-ready", "<red>Most nem tudod használni ezt a képességet.</red>"));
            return;
        }

        if (!selected.hasRequiredCost(player)) {
            player.sendActionBar(messageManager.getMessage(
                    "catalyst.no-cost",
                    "<red>Nincs elég {resource}! Szükséges: {amount}</red>",
                    Map.of(
                            "resource", resolveResourceName(selected),
                            "amount", String.valueOf(selected.getCostAmount())
                    )
            ));
            return;
        }

        selected.consumeCost(player);
        selected.execute(player);
        putCooldown(player, selected, now);
    }

    private String resolveResourceName(final Spell spell) {
        return switch (spell.getCostType()) {
            case HUNGER -> messageManager.get("system.resources.hunger", "éhség");
            case XP -> messageManager.get("system.resources.xp", "XP");
            case HEALTH -> messageManager.get("system.resources.health", "élet");
        };
    }

    private Spell resolveSelectedSpell(final Player player, final List<String> unlocked) {
        final PersistentDataContainer pdc = player.getPersistentDataContainer();
        int index = pdc.getOrDefault(selectedSpellIndexKey, PersistentDataType.INTEGER, 0);
        if (index < 0 || index >= unlocked.size()) {
            index = 0;
            pdc.set(selectedSpellIndexKey, PersistentDataType.INTEGER, 0);
        }

        return spellRegistry.getById(unlocked.get(index));
    }

    private long getRemainingCooldown(final Player player, final Spell spell, final long now) {
        final Long lastCast = getLastCast(player, spell);
        if (lastCast == null) {
            return 0L;
        }

        final long cooldownMs = Math.max(0, spell.getCooldown()) * 1000L;
        final long delayMs = Math.max(0, spell.getCooldownDelay()) * 1000L;
        return Math.max(0L, (lastCast + delayMs + cooldownMs) - now);
    }

    private void putCooldown(final Player player, final Spell spell, final long now) {
        if (isPersistentCooldown(spell)) {
            player.getPersistentDataContainer().set(resolveLongCooldownKey(spell.getId()), PersistentDataType.LONG, now);
            return;
        }

        spellCooldowns.computeIfAbsent(player.getUniqueId(), key -> new ConcurrentHashMap<>()).put(spell.getId(), now);
    }

    private List<String> resolveUnlockedSpellIds(final Player player) {
        return jobManager.getUnlockedSpellIds(player).stream()
                .filter(spellId -> spellRegistry.getById(spellId) != null)
                .toList();
    }

    public void cleanup(final UUID playerId) {
        if (playerId == null) {
            return;
        }

        spellCooldowns.remove(playerId);
        cycleDebounce.remove(playerId);
        castDebounce.remove(playerId);
    }

    public void clearPlayerState(final UUID playerId) {
        cleanup(playerId);
    }

    public void resetCooldowns(final Player player) {
        if (player == null) {
            return;
        }

        spellCooldowns.remove(player.getUniqueId());

        final PersistentDataContainer pdc = player.getPersistentDataContainer();
        for (final NamespacedKey key : pdc.getKeys()) {
            if (!"icesmp".equalsIgnoreCase(key.getNamespace())) {
                continue;
            }
            if (key.getKey().startsWith("cd_")) {
                pdc.remove(key);
            }
        }
    }

    public void resetAllSpellState(final Player player) {
        if (player == null) {
            return;
        }

        resetCooldowns(player);
        player.getPersistentDataContainer().remove(selectedSpellIndexKey);
    }

    private Long getLastCast(final Player player, final Spell spell) {
        if (isPersistentCooldown(spell)) {
            return player.getPersistentDataContainer().get(resolveLongCooldownKey(spell.getId()), PersistentDataType.LONG);
        }

        final Map<String, Long> bySpell = spellCooldowns.get(player.getUniqueId());
        if (bySpell == null) {
            return null;
        }

        return bySpell.get(spell.getId());
    }

    private boolean isPersistentCooldown(final Spell spell) {
        return spell.getCooldown() >= 60;
    }

    private NamespacedKey resolveLongCooldownKey(final String spellId) {
        final String normalized = spellId == null
                ? "unknown"
                : spellId.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        return longCooldownKeys.computeIfAbsent(normalized, id -> new NamespacedKey("icesmp", "cd_" + id));
    }
}
