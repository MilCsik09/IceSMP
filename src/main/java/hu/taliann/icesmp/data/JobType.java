package hu.taliann.icesmp.data;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

public enum JobType {
    WIZARD("wizard", "<dark_purple>Var\u00e1zsl\u00f3</dark_purple>"),
    WARRIOR("warrior", "<red>Harcos</red>");

    private final String id;
    private final Component displayName;

    JobType(final String id, final String displayName) {
        this.id = id;
        this.displayName = MiniMessage.miniMessage().deserialize(displayName);
    }

    public String getId() {
        return id;
    }

    public Component getDisplayName() {
        return displayName;
    }

    public static JobType fromId(final String id) {
        if (id == null || id.isBlank()) {
            return null;
        }

        for (final JobType jobType : values()) {
            if (jobType.id.equalsIgnoreCase(id) || jobType.name().equalsIgnoreCase(id)) {
                return jobType;
            }
        }

        return null;
    }
}


