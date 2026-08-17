package hu.taliann.icesmp.itemization;

import hu.taliann.icesmp.data.JobType;
import hu.taliann.icesmp.data.SpecializationType;
import hu.taliann.icesmp.managers.JobManager;
import hu.taliann.icesmp.managers.SpecializationManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

/** Owner-thread adapter over the immutable class-to-family authority. */
public final class EquipmentProficiencyService {
    private static volatile EquipmentProficiencyService activeInstance;

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

    public boolean canUse(final Player player, final ItemTemplate template) {
        final EquipmentProficiencyPolicy.Decision decision = decision(player, template);
        if (decision.status() == EquipmentProficiencyPolicy.Status.NOT_ARMOR_FAMILY_GEAR) {
            return true;
        }
        return profileReady(player) && decision.allowed();
    }

    public boolean isActive(final Player player, final ItemStack item,
                            final ItemTemplate.Slot equippedSlot) {
        final ItemIdentityService.Inspection inspection = identities.inspect(item);
        if (inspection.status() != ItemIdentityService.Status.VALID) return false;
        final ItemTemplate template = inspection.template();
        if (!fits(template.slot(), equippedSlot)) return false;
        final EquipmentProficiencyPolicy.Decision decision = decision(player, template);
        if (decision.status() == EquipmentProficiencyPolicy.Status.NOT_ARMOR_FAMILY_GEAR) {
            return true;
        }
        return profileReady(player) && decision.allowed();
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

    public void reconcile(final Player player) {
        if (player == null || !player.isOnline()) return;
        reconcileSlot(player, player.getInventory().getHelmet(), ItemTemplate.Slot.HEAD);
        reconcileSlot(player, player.getInventory().getChestplate(), ItemTemplate.Slot.CHEST);
        reconcileSlot(player, player.getInventory().getLeggings(), ItemTemplate.Slot.LEGS);
        reconcileSlot(player, player.getInventory().getBoots(), ItemTemplate.Slot.FEET);
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
                               final ItemTemplate.Slot slot) {
        final ItemIdentityService.Inspection inspection = identities.inspect(item);
        if (inspection.status() != ItemIdentityService.Status.VALID
                || !inspection.template().isArmorFamilyEquipment()) return;
        final boolean allowed = profileReady(player)
                && fits(inspection.template().slot(), slot)
                && decision(player, inspection.template()).allowed();
        identities.setEquipmentSuppressed(item, inspection.template(), inspection.instance(), !allowed);
    }

    public static boolean fits(final ItemTemplate.Slot authored, final ItemTemplate.Slot equipped) {
        return authored == equipped || (equipped == ItemTemplate.Slot.MAIN_HAND
                && authored == ItemTemplate.Slot.TWO_HAND);
    }
}
