package hu.taliann.icesmp.data;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

/**
 * Profession specializations, mirroring the class specialization system:
 * available once the profession reaches the configured level.
 */
public enum ProfessionSpecializationType {
    WEAPONSMITH("weaponsmith", "<red>Fegyverkovács</red>", ProfessionType.ARMORER),
    ARMORSMITH("armorsmith", "<gold>Páncélkovács</gold>", ProfessionType.ARMORER),
    PROSPECTOR("prospector", "<yellow>Aranyásó</yellow>", ProfessionType.MINER),
    EXCAVATOR("excavator", "<gray>Vájármester</gray>", ProfessionType.MINER),
    BOTANIST("botanist", "<green>Botanikus</green>", ProfessionType.FARMER),
    RANCHER("rancher", "<gold>Állattenyésztő</gold>", ProfessionType.FARMER),
    ANGLER("angler", "<aqua>Horgászmester</aqua>", ProfessionType.FISHERMAN),
    TREASURE_HUNTER("treasure_hunter", "<light_purple>Kincsvadász</light_purple>", ProfessionType.FISHERMAN);

    private final String id;
    private final Component displayName;
    private final ProfessionType parentProfession;

    ProfessionSpecializationType(final String id, final String displayName, final ProfessionType parentProfession) {
        this.id = id;
        this.displayName = MiniMessage.miniMessage().deserialize(displayName);
        this.parentProfession = parentProfession;
    }

    public String getId() {
        return id;
    }

    public Component getDisplayName() {
        return displayName;
    }

    public ProfessionType getParentProfession() {
        return parentProfession;
    }

    public static ProfessionSpecializationType fromId(final String id) {
        if (id == null || id.isBlank()) {
            return null;
        }

        for (final ProfessionSpecializationType specialization : values()) {
            if (specialization.id.equalsIgnoreCase(id) || specialization.name().equalsIgnoreCase(id)) {
                return specialization;
            }
        }

        return null;
    }
}
