package hu.taliann.icesmp.itemization;

import hu.taliann.icesmp.data.JobType;
import hu.taliann.icesmp.data.SpecializationType;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Pure class/spec/template decision authority shared by runtime and validators. */
public final class EquipmentProficiencyPolicy {

    public enum Status {
        ALLOWED,
        NOT_ARMOR_FAMILY_GEAR,
        NO_CLASS,
        WRONG_ARMOR_FAMILY,
        CLASS_RESTRICTED,
        SPECIALIZATION_RESTRICTED,
        INVALID_TEMPLATE
    }

    public record Decision(Status status, ArmorFamily requiredFamily, ArmorFamily classFamily) {
        public boolean allowed() {
            return status == Status.ALLOWED || status == Status.NOT_ARMOR_FAMILY_GEAR;
        }
    }

    private static final Map<JobType, ArmorFamily> CLASS_FAMILIES;

    static {
        final EnumMap<JobType, ArmorFamily> mapping = new EnumMap<>(JobType.class);
        put(mapping, ArmorFamily.CLOTH, JobType.PRIEST, JobType.WARLOCK, JobType.WIZARD);
        put(mapping, ArmorFamily.LEATHER, JobType.MONK, JobType.DEMON_HUNTER,
                JobType.DRUID, JobType.ASSASSIN);
        put(mapping, ArmorFamily.MAIL, JobType.ARCHER, JobType.SHAMAN, JobType.EVOKER);
        put(mapping, ArmorFamily.PLATE, JobType.WARRIOR, JobType.PALADIN, JobType.DEATH_KNIGHT);
        if (mapping.size() != JobType.values().length) {
            throw new ExceptionInInitializerError("every class must have exactly one armor family");
        }
        CLASS_FAMILIES = Map.copyOf(mapping);
    }

    private EquipmentProficiencyPolicy() {
    }

    public static ArmorFamily familyOf(final JobType job) {
        return job == null ? null : CLASS_FAMILIES.get(job);
    }

    public static Map<JobType, ArmorFamily> classFamilies() {
        return CLASS_FAMILIES;
    }

    public static Decision decide(final JobType job, final SpecializationType specialization,
                                  final ItemTemplate template) {
        if (template == null) return new Decision(Status.INVALID_TEMPLATE, null, familyOf(job));
        if (!template.classRestrictions().isEmpty()
                && (job == null || !template.classRestrictions().contains(job.getId()))) {
            return new Decision(job == null ? Status.NO_CLASS : Status.CLASS_RESTRICTED,
                    template.armorFamily(), familyOf(job));
        }
        if (!template.specializationRestrictions().isEmpty()
                && (specialization == null
                || !template.specializationRestrictions().contains(specialization.getId()))) {
            return new Decision(Status.SPECIALIZATION_RESTRICTED,
                    template.armorFamily(), familyOf(job));
        }
        if (!template.isArmorFamilyEquipment()) {
            return new Decision(Status.NOT_ARMOR_FAMILY_GEAR, null, familyOf(job));
        }
        if (job == null) return new Decision(Status.NO_CLASS, template.armorFamily(), null);
        final ArmorFamily classFamily = familyOf(job);
        return new Decision(classFamily == template.armorFamily()
                ? Status.ALLOWED : Status.WRONG_ARMOR_FAMILY, template.armorFamily(), classFamily);
    }

    private static void put(final Map<JobType, ArmorFamily> target, final ArmorFamily family,
                            final JobType... jobs) {
        for (final JobType job : jobs) {
            if (target.put(Objects.requireNonNull(job, "job"), family) != null) {
                throw new ExceptionInInitializerError("duplicate armor family mapping: " + job);
            }
        }
    }
}
