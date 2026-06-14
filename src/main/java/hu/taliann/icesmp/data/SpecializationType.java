package hu.taliann.icesmp.data;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

/**
 * Class specializations. A player may specialize the primary class once the
 * configured class level is reached. The Necromancer is the Wizard's dark
 * specialization: it requires the Dark faction and the permanent sinner mark.
 */
public enum SpecializationType {
    ELEMENTALIST("elementalist", "<aqua>Elementalista</aqua>", JobType.WIZARD, null, false),
    NECROMANCER("necromancer", "<dark_gray>Nekromanta</dark_gray>", JobType.WIZARD, FactionType.DARK, true),
    BERSERKER("berserker", "<dark_red>Berserker</dark_red>", JobType.WARRIOR, null, false),
    GUARDIAN("guardian", "<gold>Védelmező</gold>", JobType.WARRIOR, null, false),
    SHARPSHOOTER("sharpshooter", "<yellow>Mesterlövész</yellow>", JobType.ARCHER, null, false),
    BEAST_MASTER("beast_master", "<dark_green>Vadmester</dark_green>", JobType.ARCHER, null, false),
    POISONER("poisoner", "<green>Méregkeverő</green>", JobType.ASSASSIN, null, false),
    PHANTOM("phantom", "<dark_aqua>Fantom</dark_aqua>", JobType.ASSASSIN, null, false);

    private final String id;
    private final Component displayName;
    private final JobType parentJob;
    private final FactionType requiredFaction;
    private final boolean requiresSinner;

    SpecializationType(final String id, final String displayName, final JobType parentJob,
                       final FactionType requiredFaction, final boolean requiresSinner) {
        this.id = id;
        this.displayName = MiniMessage.miniMessage().deserialize(displayName);
        this.parentJob = parentJob;
        this.requiredFaction = requiredFaction;
        this.requiresSinner = requiresSinner;
    }

    public String getId() {
        return id;
    }

    public Component getDisplayName() {
        return displayName;
    }

    public JobType getParentJob() {
        return parentJob;
    }

    public FactionType getRequiredFaction() {
        return requiredFaction;
    }

    public boolean requiresSinner() {
        return requiresSinner;
    }

    public static SpecializationType fromId(final String id) {
        if (id == null || id.isBlank()) {
            return null;
        }

        for (final SpecializationType specialization : values()) {
            if (specialization.id.equalsIgnoreCase(id) || specialization.name().equalsIgnoreCase(id)) {
                return specialization;
            }
        }

        return null;
    }
}
