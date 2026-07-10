package hu.taliann.icesmp.spells;

import hu.taliann.icesmp.utils.MessageManager;

public abstract class BaseSpell implements Spell {

    private final String id;
    private final String defaultName;
    private final int cooldown;
    private final SpellCostType costType;
    private final int costAmount;
    protected final MessageManager messageManager;

    protected BaseSpell(final MessageManager messageManager, final String id, final String defaultName, final int cooldown,
                        final SpellCostType costType, final int costAmount) {
        this.messageManager = messageManager;
        this.id = id;
        this.defaultName = defaultName;
        this.cooldown = cooldown;
        this.costType = costType;
        this.costAmount = costAmount;
    }

    @Override
    public String getId() {
        return id;
    }

    /**
     * Exposes the untranslated fallback name so subclasses can rebuild themselves (e.g. the
     * config-driven balance-override copy in {@link hu.taliann.icesmp.spells.ConfiguredSpell})
     * without re-resolving it through the {@link MessageManager}.
     */
    protected String getDefaultName() {
        return defaultName;
    }

    @Override
    public String getName() {
        if (messageManager == null) {
            return defaultName;
        }
        return messageManager.get("spell." + id + ".name", defaultName);
    }

    @Override
    public int getCooldown() {
        return cooldown;
    }

    @Override
    public SpellCostType getCostType() {
        return costType;
    }

    @Override
    public int getCostAmount() {
        return costAmount;
    }

    protected net.kyori.adventure.text.Component resolveMessage(final String key, final String fallback) {
        if (messageManager == null) {
            return net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(fallback);
        }
        return messageManager.getMessage(key, fallback);
    }

    protected net.kyori.adventure.text.Component resolveMessage(
            final String key,
            final String fallback,
            final java.util.Map<String, String> placeholders
    ) {
        if (messageManager == null) {
            String resolved = fallback;
            for (final java.util.Map.Entry<String, String> entry : placeholders.entrySet()) {
                resolved = resolved.replace("{" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
            }
            return net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(resolved);
        }
        return messageManager.getMessage(key, fallback, placeholders);
    }
}

