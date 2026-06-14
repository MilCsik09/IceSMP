package hu.taliann.icesmp.data;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

/**
 * Profession specializations (2 per profession), mirroring the class
 * specialization system: available once the profession reaches the configured level.
 */
public enum ProfessionSpecializationType {
    // Bányász
    PROSPECTOR("prospector", "<yellow>Aranyásó</yellow>", ProfessionType.MINER),
    EXCAVATOR("excavator", "<gray>Vájármester</gray>", ProfessionType.MINER),
    // Gyógynövényész
    BOTANIST("botanist", "<green>Botanikus</green>", ProfessionType.HERBALIST),
    NATURALIST("naturalist", "<dark_green>Természetbúvár</dark_green>", ProfessionType.HERBALIST),
    // Favágó
    FORESTER("forester", "<dark_green>Erdész</dark_green>", ProfessionType.LUMBERJACK),
    CARPENTER("carpenter", "<gold>Ácsmester</gold>", ProfessionType.LUMBERJACK),
    // Kovács
    WEAPONSMITH("weaponsmith", "<red>Fegyverkovács</red>", ProfessionType.ARMORER),
    ARMORSMITH("armorsmith", "<gold>Páncélkovács</gold>", ProfessionType.ARMORER),
    // Alkimista
    POTION_MASTER("potion_master", "<light_purple>Főzetmester</light_purple>", ProfessionType.ALCHEMIST),
    TRANSMUTER("transmuter", "<dark_purple>Transzmutátor</dark_purple>", ProfessionType.ALCHEMIST),
    // Bűvölő
    RUNEKEEPER("runekeeper", "<aqua>Rúnamester</aqua>", ProfessionType.ENCHANTER),
    ARCANIST("arcanist", "<dark_aqua>Arkanista</dark_aqua>", ProfessionType.ENCHANTER),
    // Halász
    ANGLER("angler", "<aqua>Horgászmester</aqua>", ProfessionType.FISHERMAN),
    TREASURE_HUNTER("treasure_hunter", "<light_purple>Kincsvadász</light_purple>", ProfessionType.FISHERMAN),
    // Szakács
    CHEF("chef", "<red>Séf</red>", ProfessionType.COOK),
    BUTCHER("butcher", "<dark_red>Hentes</dark_red>", ProfessionType.COOK);

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
