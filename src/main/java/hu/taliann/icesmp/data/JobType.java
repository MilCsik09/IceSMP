package hu.taliann.icesmp.data;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

public enum JobType {
    WIZARD("wizard", "<dark_purple>Varázsló</dark_purple>", null),
    WARRIOR("warrior", "<red>Harcos</red>", null),
    ARCHER("archer", "<green>Íjász</green>", null),
    ASSASSIN("assassin", "<gray>Orgyilkos</gray>", null),
    DRUID("druid", "<dark_green>Druida</dark_green>", null),
    PALADIN("paladin", "<gold>Paplovag</gold>", null),
    DEATH_KNIGHT("death_knight", "<dark_red>Halállovag</dark_red>", null),
    SHAMAN("shaman", "<aqua>Sámán</aqua>", null),
    MONK("monk", "<green>Szerzetes</green>", null),
    PRIEST("priest", "<white>Pap</white>", null),
    WARLOCK("warlock", "<dark_purple>Boszorkánymester</dark_purple>", null),
    DEMON_HUNTER("demon_hunter", "<light_purple>Démonvadász</light_purple>", null),
    EVOKER("evoker", "<dark_aqua>Sárkányidéző</dark_aqua>", null);

    private final String id;
    private final Component displayName;
    private final FactionType requiredFaction;

    JobType(final String id, final String displayName, final FactionType requiredFaction) {
        this.id = id;
        this.displayName = MiniMessage.miniMessage().deserialize(displayName);
        this.requiredFaction = requiredFaction;
    }

    public String getId() {
        return id;
    }

    public Component getDisplayName() {
        return displayName;
    }

    /**
     * Gets the faction membership required to select this class.
     *
     * @return the required faction, or null if the class is available to everyone
     */
    public FactionType getRequiredFaction() {
        return requiredFaction;
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
