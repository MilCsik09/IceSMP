package hu.taliann.icesmp.itemization;

import hu.taliann.icesmp.data.JobType;
import hu.taliann.icesmp.data.SpecializationType;
import hu.taliann.icesmp.managers.JobManager;
import hu.taliann.icesmp.managers.SpecializationManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Owner-thread adapter over the immutable class-to-family authority. */
public final class EquipmentProficiencyService {
    private static volatile EquipmentProficiencyService activeInstance;

    public enum ActivityStatus {
        ACTIVE,
        NOT_MANAGED,
        INVALID_IDENTITY,
        SLOT_MISMATCH,
        DUPLICATE_UUID,
        PROFILE_NOT_READY,
        RESTRICTED,
        SUPPRESSED
    }

    public record Activity(ActivityStatus status, ItemIdentityService.Inspection inspection,
                           ItemTemplate.Slot equippedSlot,
                           EquipmentProficiencyPolicy.Decision decision) {
        public boolean activeCanonical() {
            return status == ActivityStatus.ACTIVE;
        }

        /** BASIC/NOT_MANAGED keeps vanilla policy; canonical contributes only while ACTIVE. */
        public boolean allowsGameplayContribution() {
            return status == ActivityStatus.ACTIVE || status == ActivityStatus.NOT_MANAGED;
        }
    }

    private record Equipped(ItemStack item, ItemTemplate.Slot slot) { }

    private final JavaPlugin plugin;
    private final JobManager jobs;
    private final ItemIdentityService identities;
    private final MessageManager messages;
    private volatile SpecializationManager specializations;

    public EquipmentProficiencyService(final JavaPlugin plugin, final JobManager jobs,
                                       final ItemIdentityService identities,
                                       final MessageManager messages) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.jobs = Objects.requireNonNull(jobs, "jobs");
        this.identities = Objects.requireNonNull(identities, "identities");
        this.messages = Objects.requireNonNull(messages, "messages");
        activeInstance = this;
    }

    public void setSpecializationManager(final SpecializationManager specializations) {
        this.specializations = Objects.requireNonNull(specializations, "specializations");
    }

    public EquipmentProficiencyPolicy.Decision decision(final Player player,
                                                         final ItemTemplate template) {
        if (player == null || template == null) {
            return EquipmentProficiencyPolicy.decide(null, null, template);
        }
        final JobType job = jobs.getPrimaryJob(player);
        final SpecializationManager manager = specializations;
        final SpecializationType specialization = manager == null
                ? null : manager.getClassSpecialization(player);
        return EquipmentProficiencyPolicy.decide(job, specialization, template);
    }

    public boolean profileReady(final Player player) {
        return player != null && jobs.isProfileReady(player);
    }

    /** Template-level admission only; runtime gameplay consumers must use {@link #isActive}. */
    public boolean canUse(final Player player, final ItemTemplate template) {
        return template != null && profileReady(player) && decision(player, template).allowed();
    }

    /**
     * Pure activity state machine used by the runtime adapter and behavioral regressions.
     * NOT_MANAGED is deliberately separate from canonical ACTIVE so BASIC survival gear keeps
     * vanilla behavior without inheriting any MMO proficiency gate.
     */
    public static ActivityStatus decideActivity(final ItemIdentityService.Status identityStatus,
                                                final boolean slotMatches,
                                                final boolean duplicateUuid,
                                                final boolean profileReady,
                                                final boolean restrictionsAllowed,
                                                final boolean suppressed) {
        if (identityStatus == ItemIdentityService.Status.NOT_MANAGED) {
            return ActivityStatus.NOT_MANAGED;
        }
        if (identityStatus != ItemIdentityService.Status.VALID) {
            return ActivityStatus.INVALID_IDENTITY;
        }
        if (!slotMatches) return ActivityStatus.SLOT_MISMATCH;
        if (duplicateUuid) return ActivityStatus.DUPLICATE_UUID;
        if (!profileReady) return ActivityStatus.PROFILE_NOT_READY;
        if (!restrictionsAllowed) return ActivityStatus.RESTRICTED;
        if (suppressed) return ActivityStatus.SUPPRESSED;
        return ActivityStatus.ACTIVE;
    }

    public Activity inspectActive(final Player player, final ItemStack item,
                                  final ItemTemplate.Slot equippedSlot) {
        final ItemIdentityService.Inspection inspection = identities.inspect(item);
        if (inspection.status() == ItemIdentityService.Status.NOT_MANAGED) {
            return new Activity(ActivityStatus.NOT_MANAGED, inspection, equippedSlot, null);
        }
        if (inspection.status() != ItemIdentityService.Status.VALID) {
            return new Activity(ActivityStatus.INVALID_IDENTITY, inspection, equippedSlot, null);
        }
        final ItemTemplate template = inspection.template();
        final EquipmentProficiencyPolicy.Decision policy = decision(player, template);
        final ActivityStatus status = decideActivity(inspection.status(),
                fits(template.slot(), equippedSlot),
                isDuplicateEquippedUuid(player, inspection.instance().itemId()),
                profileReady(player), policy.allowed(), identities.isEquipmentSuppressed(item));
        return new Activity(status, inspection, equippedSlot, policy);
    }

    public boolean isActive(final Player player, final ItemStack item,
                            final ItemTemplate.Slot equippedSlot) {
        return inspectActive(player, item, equippedSlot).activeCanonical();
    }

    /** Shared guard for legacy/BASIC-aware consumers such as curses and old affixes. */
    public static boolean allowsGameplayContribution(final Player player, final ItemStack item,
                                                     final ItemTemplate.Slot equippedSlot) {
        final EquipmentProficiencyService service = activeInstance;
        return service != null
                && service.inspectActive(player, item, equippedSlot).allowsGameplayContribution();
    }

    /** Shared guard for canonical-only consumers such as Itemization loot build tags. */
    public static boolean isCanonicalActive(final Player player, final ItemStack item,
                                            final ItemTemplate.Slot equippedSlot) {
        final EquipmentProficiencyService service = activeInstance;
        return service != null && service.isActive(player, item, equippedSlot);
    }

    public net.kyori.adventure.text.Component denialMessage(
            final EquipmentProficiencyPolicy.Decision decision) {
        if (decision == null || decision.allowed()) return net.kyori.adventure.text.Component.empty();
        return switch (decision.status()) {
            case NO_CLASS -> messages.getMessage("equipment.proficiency.no-class",
                    "&cAz authored MMORPG felszereléshez előbb válassz kasztot.");
            case WRONG_ARMOR_FAMILY -> messages.getComponent("equipment.proficiency.wrong-family",
                    "&cA kasztod nem tud %s felszerelést viselni.",
                    decision.requiredFamily() == null ? "ilyen" : decision.requiredFamily().displayName());
            case CLASS_RESTRICTED -> messages.getMessage("equipment.proficiency.class-restricted",
                    "&cEzt a felszerelést a kasztod nem használhatja.");
            case SPECIALIZATION_RESTRICTED -> messages.getMessage("equipment.proficiency.spec-restricted",
                    "&cEzt a felszerelést az aktív specializációd nem használhatja.");
            default -> messages.getMessage("equipment.proficiency.invalid",
                    "&cEz a canonical felszerelés jelenleg nem aktiválható.");
        };
    }

    /**
     * Owner-thread reconciliation of the complete physical equipment projection. No polling is
     * introduced: callers are the existing equip/class/spec/login/respawn mutation hooks.
     */
    public void reconcile(final Player player) {
        if (player == null || !player.isOnline()) return;
        final Set<UUID> duplicateIds = duplicateEquippedIds(player);
        reconcileSlot(player, player.getInventory().getItemInMainHand(),
                ItemTemplate.Slot.MAIN_HAND, duplicateIds);
        reconcileSlot(player, player.getInventory().getItemInOffHand(),
                ItemTemplate.Slot.OFF_HAND, duplicateIds);
        reconcileSlot(player, player.getInventory().getHelmet(), ItemTemplate.Slot.HEAD, duplicateIds);
        reconcileSlot(player, player.getInventory().getChestplate(), ItemTemplate.Slot.CHEST, duplicateIds);
        reconcileSlot(player, player.getInventory().getLeggings(), ItemTemplate.Slot.LEGS, duplicateIds);
        reconcileSlot(player, player.getInventory().getBoots(), ItemTemplate.Slot.FEET, duplicateIds);
        hu.taliann.icesmp.pve.EquippedCombatPowerService.refreshAfterMutation(player);
    }

    public void reconcileNextTick(final Player player) {
        if (player == null) return;
        try {
            player.getScheduler().runDelayed(plugin, task -> reconcile(player), null, 1L);
        } catch (final RuntimeException ignored) {
            // A rejected owner task cannot safely touch the inventory; consumers remain fail-closed.
        }
    }

    public static void reconcileAfterClassChange(final Player player) {
        final EquipmentProficiencyService service = activeInstance;
        if (service != null) service.reconcileNextTick(player);
    }

    private void reconcileSlot(final Player player, final ItemStack item,
                               final ItemTemplate.Slot slot, final Set<UUID> duplicateIds) {
        final ItemIdentityService.Inspection inspection = identities.inspect(item);
        if (inspection.status() == ItemIdentityService.Status.NOT_MANAGED) return;
        if (inspection.status() != ItemIdentityService.Status.VALID) {
            identities.suppressManagedInvalid(item);
            return;
        }
        final EquipmentProficiencyPolicy.Decision policy = decision(player, inspection.template());
        final ActivityStatus eligibility = decideActivity(inspection.status(),
                fits(inspection.template().slot(), slot),
                duplicateIds.contains(inspection.instance().itemId()),
                profileReady(player), policy.allowed(), false);
        identities.setEquipmentSuppressed(item, inspection.template(), inspection.instance(),
                eligibility != ActivityStatus.ACTIVE);
    }

    private boolean isDuplicateEquippedUuid(final Player player, final UUID itemId) {
        if (player == null || itemId == null) return false;
        int count = 0;
        for (final Equipped equipped : equipped(player)) {
            final ItemIdentityService.Inspection inspection = identities.inspect(equipped.item());
            if (inspection.status() == ItemIdentityService.Status.VALID
                    && itemId.equals(inspection.instance().itemId()) && ++count > 1) {
                return true;
            }
        }
        return false;
    }

    private Set<UUID> duplicateEquippedIds(final Player player) {
        final java.util.LinkedHashMap<UUID, Integer> counts = new java.util.LinkedHashMap<>();
        for (final Equipped equipped : equipped(player)) {
            final ItemIdentityService.Inspection inspection = identities.inspect(equipped.item());
            if (inspection.status() == ItemIdentityService.Status.VALID) {
                counts.merge(inspection.instance().itemId(), 1, Integer::sum);
            }
        }
        final LinkedHashSet<UUID> duplicate = new LinkedHashSet<>();
        counts.forEach((id, count) -> {
            if (count > 1) duplicate.add(id);
        });
        return Set.copyOf(duplicate);
    }

    private static java.util.List<Equipped> equipped(final Player player) {
        return java.util.List.of(
                new Equipped(player.getInventory().getItemInMainHand(), ItemTemplate.Slot.MAIN_HAND),
                new Equipped(player.getInventory().getItemInOffHand(), ItemTemplate.Slot.OFF_HAND),
                new Equipped(player.getInventory().getHelmet(), ItemTemplate.Slot.HEAD),
                new Equipped(player.getInventory().getChestplate(), ItemTemplate.Slot.CHEST),
                new Equipped(player.getInventory().getLeggings(), ItemTemplate.Slot.LEGS),
                new Equipped(player.getInventory().getBoots(), ItemTemplate.Slot.FEET));
    }

    public static boolean fits(final ItemTemplate.Slot authored, final ItemTemplate.Slot equipped) {
        return authored == equipped || (equipped == ItemTemplate.Slot.MAIN_HAND
                && authored == ItemTemplate.Slot.TWO_HAND);
    }
}
