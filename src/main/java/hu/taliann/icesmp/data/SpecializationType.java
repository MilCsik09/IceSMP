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
    PHANTOM("phantom", "<dark_aqua>Fantom</dark_aqua>", JobType.ASSASSIN, null, false),
    FERAL("feral", "<dark_green>Vadőr</dark_green>", JobType.DRUID, null, false),
    LUNAR("lunar", "<blue>Holdjós</blue>", JobType.DRUID, null, false),
    HOLY("holy", "<yellow>Szentlélek</yellow>", JobType.PALADIN, null, false),
    RETRIBUTION("retribution", "<gold>Megtorló</gold>", JobType.PALADIN, null, false),
    BLOOD("blood", "<dark_red>Vérlovag</dark_red>", JobType.DEATH_KNIGHT, null, false),
    FROST("frost", "<aqua>Fagylovag</aqua>", JobType.DEATH_KNIGHT, null, false),
    ELEMENTAL("elemental", "<blue>Elemi</blue>", JobType.SHAMAN, null, false),
    ENHANCEMENT("enhancement", "<gold>Erősítő</gold>", JobType.SHAMAN, null, false),
    WINDWALKER("windwalker", "<aqua>Szélfutó</aqua>", JobType.MONK, null, false),
    BREWMASTER("brewmaster", "<gold>Sörfőző</gold>", JobType.MONK, null, false),
    DISCIPLINE("discipline", "<white>Fegyelem</white>", JobType.PRIEST, null, false),
    SHADOW("shadow", "<dark_purple>Árnyék</dark_purple>", JobType.PRIEST, null, false),
    AFFLICTION("affliction", "<dark_green>Átok</dark_green>", JobType.WARLOCK, null, false),
    DESTRUCTION("destruction", "<red>Pusztítás</red>", JobType.WARLOCK, null, false),
    HAVOC("havoc", "<light_purple>Tombolás</light_purple>", JobType.DEMON_HUNTER, null, false),
    VENGEANCE("vengeance", "<dark_red>Bosszú</dark_red>", JobType.DEMON_HUNTER, null, false),
    DEVASTATION("devastation", "<gold>Perzselés</gold>", JobType.EVOKER, null, false),
    PRESERVATION("preservation", "<aqua>Megőrzés</aqua>", JobType.EVOKER, null, false),
    // Hiányzó-szerep specek (a fantáziából hiányzó szerep): tank/heal néhány kasztnak.
    IRONBARK("ironbark", "<dark_green>Védelmező</dark_green>", JobType.DRUID, null, false),
    RESTORATION("restoration", "<green>Helyreállító</green>", JobType.DRUID, null, false),
    TIDAL("tidal", "<aqua>Hullámhívó</aqua>", JobType.SHAMAN, null, false),
    MISTWEAVER("mistweaver", "<aqua>Ködszövő</aqua>", JobType.MONK, null, false),
    PROTECTION("protection", "<gold>Védő</gold>", JobType.PALADIN, null, false);

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
