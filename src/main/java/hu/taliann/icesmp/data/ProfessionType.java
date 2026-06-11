package hu.taliann.icesmp.data;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

public enum ProfessionType {
    ARMORER("armorer", "<gold>Kovács</gold>"),
    MINER("miner", "<gray>Bányász</gray>"),
    FARMER("farmer", "<green>Földműves</green>"),
    FISHERMAN("fisherman", "<aqua>Halász</aqua>");

    private final String id;
    private final Component displayName;

    ProfessionType(final String id, final String displayName) {
        this.id = id;
        this.displayName = MiniMessage.miniMessage().deserialize(displayName);
    }

    public String getId() {
        return id;
    }

    public Component getDisplayName() {
        return displayName;
    }

    public static ProfessionType fromId(final String id) {
        if (id == null || id.isBlank()) {
            return null;
        }

        for (final ProfessionType professionType : values()) {
            if (professionType.id.equalsIgnoreCase(id) || professionType.name().equalsIgnoreCase(id)) {
                return professionType;
            }
        }

        return null;
    }
}
