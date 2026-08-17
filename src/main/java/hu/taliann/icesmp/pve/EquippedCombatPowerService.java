package hu.taliann.icesmp.pve;

import hu.taliann.icesmp.itemization.ItemIdentityService;
import hu.taliann.icesmp.itemization.ItemInstance;
import hu.taliann.icesmp.itemization.ItemSetDefinition;
import hu.taliann.icesmp.itemization.ItemTemplate;
import hu.taliann.icesmp.itemization.ItemTemplateRegistry;
import hu.taliann.icesmp.itemization.EquipmentBudgetModel;
import hu.taliann.icesmp.itemization.EquipmentProficiencyService;
import hu.taliann.icesmp.managers.JobManager;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Owner-thread equipment sampler with an immutable cross-region encounter cache. */
public final class EquippedCombatPowerService implements Listener {
    private static volatile EquippedCombatPowerService activeInstance;

    private record Candidate(UUID itemId, String setId, EquippedCombatPowerModel.GearSignal signal) { }

    private final JavaPlugin plugin;
    private final ItemIdentityService identities;
    private final JobManager jobs;
    private final EquipmentProficiencyService proficiency;
    private final Map<UUID, Double> cache = new ConcurrentHashMap<>();
    private final Map<String, NamespacedKey> setModifierKeys;

    public EquippedCombatPowerService(final JavaPlugin plugin,
                                      final ItemIdentityService identities,
                                      final JobManager jobs,
                                      final EquipmentProficiencyService proficiency) {
        this.plugin = plugin;
        this.identities = identities;
        this.jobs = jobs;
        this.proficiency = proficiency;
        this.setModifierKeys = Map.of(
                "max_health", new NamespacedKey(plugin, "item_set_max_health"),
                "armor", new NamespacedKey(plugin, "item_set_armor"),
                "armor_toughness", new NamespacedKey(plugin, "item_set_armor_toughness"),
                "movement_speed", new NamespacedKey(plugin, "item_set_movement_speed"));
        activeInstance = this;
    }

    /** Safe from another region: only reads the last owner-thread projection. */
    public double powerOf(final UUID playerId, final double fallback) {
        final double safeFallback = Double.isFinite(fallback)
                ? Math.max(1.0D, Math.min(10_000.0D, fallback)) : 1.0D;
        final double value = cache.getOrDefault(playerId, safeFallback);
        return Double.isFinite(value) ? Math.max(1.0D, Math.min(10_000.0D, value)) : safeFallback;
    }

    public void refresh(final Player player) {
        if (player == null || !player.isOnline()) return;
        final ArrayList<Candidate> candidates = new ArrayList<>(6);
        add(candidates, player, player.getInventory().getItemInMainHand(), ItemTemplate.Slot.MAIN_HAND);
        add(candidates, player, player.getInventory().getItemInOffHand(), ItemTemplate.Slot.OFF_HAND);
        add(candidates, player, player.getInventory().getHelmet(), ItemTemplate.Slot.HEAD);
        add(candidates, player, player.getInventory().getChestplate(), ItemTemplate.Slot.CHEST);
        add(candidates, player, player.getInventory().getLeggings(), ItemTemplate.Slot.LEGS);
        add(candidates, player, player.getInventory().getBoots(), ItemTemplate.Slot.FEET);
        final HashMap<UUID, Integer> counts = new HashMap<>();
        for (final Candidate candidate : candidates) counts.merge(candidate.itemId(), 1, Integer::sum);
        final List<Candidate> uniqueCandidates = candidates.stream()
                .filter(candidate -> counts.getOrDefault(candidate.itemId(), 0) == 1).toList();
        final List<EquippedCombatPowerModel.GearSignal> unique = uniqueCandidates.stream()
                .map(Candidate::signal).toList();
        applySetAttributes(player, uniqueCandidates);
        cache.put(player.getUniqueId(), EquippedCombatPowerModel.estimate(
                jobs.getPrimaryLevel(player), unique));
    }

    /** Candidate admission is delegated to the shared active-equipment authority. */
    private void add(final List<Candidate> result, final Player player, final ItemStack item,
                     final ItemTemplate.Slot equippedSlot) {
        if (!proficiency.isActive(player, item, equippedSlot)) return;
        final ItemIdentityService.Inspection inspection = identities.inspect(item);
        if (inspection.status() != ItemIdentityService.Status.VALID) return;
        final ItemTemplate template = inspection.template();
        final ItemInstance instance = inspection.instance();
        final String stage = instance.ascension().stageId();
        final HashMap<String, Double> rolls = new HashMap<>();
        instance.rolls().forEach((id, roll) -> rolls.put(id, roll.value()));
        double armorCoefficient = 1.0D;
        double normalizedBudget = 0.0D;
        final ItemTemplateRegistry registry = ItemTemplateRegistry.current();
        if (template.isArmorFamilyEquipment() && registry != null) {
            final var profile = registry.requireFamilyProfile(template.armorFamily());
            final EquipmentBudgetModel.Budget budget = EquipmentBudgetModel.rolled(
                    template, profile, stage, rolls);
            armorCoefficient = profile.baseArmorCoefficient();
            normalizedBudget = budget.expectedTierBudget() <= 0.0D ? 0.0D
                    : budget.normalizedTotal() / budget.expectedTierBudget();
        }
        result.add(new Candidate(instance.itemId(), template.setId(),
                new EquippedCombatPowerModel.GearSignal(
                        template.slot(), instance.itemLevel(), template.rarity(), template.baseDamage(),
                        template.baseArmor(), template.fixedStatsAt(stage), rolls,
                        instance.runes().size(), template.signatureTierAt(stage), template.setId(),
                        armorCoefficient, normalizedBudget)));
    }

    /**
     * Applies only the Bukkit-attribute set stats. ability_power remains a cast-time canonical
     * stat consumer; both projections count the same ACTIVE, non-duplicate identities.
     */
    private void applySetAttributes(final Player player, final List<Candidate> unique) {
        final ItemTemplateRegistry registry = ItemTemplateRegistry.current();
        final Map<String, Integer> pieces = new LinkedHashMap<>();
        for (final Candidate candidate : unique) {
            if (!candidate.setId().isBlank()) pieces.merge(candidate.setId(), 1, Integer::sum);
        }
        final Map<String, Double> active = new LinkedHashMap<>();
        if (registry != null) {
            for (final Map.Entry<String, Integer> entry : pieces.entrySet()) {
                final ItemSetDefinition definition = registry.findSet(entry.getKey()).orElse(null);
                if (definition == null) continue;
                definition.activeStats(entry.getValue()).forEach((id, value) ->
                        active.merge(id, value, Double::sum));
            }
        }
        applyTransient(player, Attribute.MAX_HEALTH, "max_health", active.getOrDefault("max_health", 0.0D));
        applyTransient(player, Attribute.ARMOR, "armor", active.getOrDefault("armor", 0.0D));
        applyTransient(player, Attribute.ARMOR_TOUGHNESS, "armor_toughness",
                active.getOrDefault("armor_toughness", 0.0D));
        applyTransient(player, Attribute.MOVEMENT_SPEED, "movement_speed",
                active.getOrDefault("movement_speed", 0.0D));
        final AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null && player.getHealth() > maxHealth.getValue()) {
            player.setHealth(Math.max(0.01D, maxHealth.getValue()));
        }
    }

    private void applyTransient(final Player player, final Attribute attribute,
                                final String statId, final double rawAmount) {
        final AttributeInstance instance = player.getAttribute(attribute);
        final NamespacedKey key = setModifierKeys.get(statId);
        if (instance == null || key == null) return;
        final AttributeModifier existing = instance.getModifier(key);
        if (existing != null) instance.removeModifier(existing);
        if (!Double.isFinite(rawAmount) || rawAmount == 0.0D) return;
        final double amount = switch (statId) {
            case "max_health" -> Math.max(-1000.0D, Math.min(1000.0D, rawAmount));
            case "armor", "armor_toughness" -> Math.max(-100.0D, Math.min(100.0D, rawAmount));
            case "movement_speed" -> Math.max(-0.08D, Math.min(0.50D, rawAmount));
            default -> 0.0D;
        };
        if (amount == 0.0D) return;
        instance.addTransientModifier(new AttributeModifier(key, amount, AttributeModifier.Operation.ADD_NUMBER));
    }

    private void clearSetAttributes(final Player player) {
        if (player == null) return;
        for (final Map.Entry<String, NamespacedKey> entry : setModifierKeys.entrySet()) {
            final Attribute attribute = switch (entry.getKey()) {
                case "max_health" -> Attribute.MAX_HEALTH;
                case "armor" -> Attribute.ARMOR;
                case "armor_toughness" -> Attribute.ARMOR_TOUGHNESS;
                case "movement_speed" -> Attribute.MOVEMENT_SPEED;
                default -> null;
            };
            if (attribute == null) continue;
            final AttributeInstance instance = player.getAttribute(attribute);
            if (instance == null) continue;
            final AttributeModifier modifier = instance.getModifier(entry.getValue());
            if (modifier != null) instance.removeModifier(modifier);
        }
    }

    private void refreshNextTick(final Player player) {
        try {
            player.getScheduler().runDelayed(plugin, task -> refresh(player),
                    () -> cache.remove(player.getUniqueId()), 1L);
        } catch (final RuntimeException rejected) {
            cache.remove(player.getUniqueId());
        }
    }

    /** Explicit hook for plugin-owned inventory mutations that do not emit a Bukkit event. */
    public static void refreshAfterMutation(final Player player) {
        final EquippedCombatPowerService service = activeInstance;
        if (service != null) service.refreshNextTick(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(final PlayerJoinEvent event) { refreshNextTick(event.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(final PlayerRespawnEvent event) { refreshNextTick(event.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClick(final InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) refreshNextTick(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrag(final InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) refreshNextTick(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeld(final PlayerItemHeldEvent event) { refreshNextTick(event.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwap(final PlayerSwapHandItemsEvent event) { refreshNextTick(event.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(final PlayerDropItemEvent event) { refreshNextTick(event.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(final EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) refreshNextTick(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBreak(final PlayerItemBreakEvent event) { refreshNextTick(event.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(final PlayerDeathEvent event) {
        cache.remove(event.getPlayer().getUniqueId());
        clearSetAttributes(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(final PlayerQuitEvent event) {
        final UUID playerId = event.getPlayer().getUniqueId();
        cache.remove(playerId);
        clearSetAttributes(event.getPlayer());
    }
}
