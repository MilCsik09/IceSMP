package hu.taliann.icesmp.spells;

import hu.taliann.icesmp.managers.TotemManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Casts a persistent Shaman totem: a placed entity that pulses its effect to nearby entities for a
 * few seconds (see {@link TotemManager}). Bespoke (needs the manager), so it is registered from
 * {@code IceSMPCore.registerSpells()} rather than the data-driven {@code SpellCatalog} pools.
 */
public final class ShamanTotemSpell extends BaseSpell {

    private final TotemManager totemManager;
    private final TotemManager.TotemType type;

    public ShamanTotemSpell(final MessageManager messageManager, final TotemManager totemManager,
                            final TotemManager.TotemType type, final int cooldown,
                            final SpellCostType costType, final int costAmount) {
        super(messageManager, type.id(), type.displayName(), cooldown, costType, costAmount);
        this.totemManager = totemManager;
        this.type = type;
    }

    @Override
    public void execute(final Player player) {
        totemManager.placeTotem(player, type);
    }

    @Override
    public List<String> describe() {
        return List.of(type.description());
    }
}
