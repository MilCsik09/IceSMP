package hu.taliann.icesmp.spells;

import hu.taliann.icesmp.utils.MessageManager;

public abstract class BaseSpell implements Spell {

    /**
     * Balansz-forrás a statikus (kódolt) spellek cast-időbeni felülbírálásaihoz
     * (spell-balance.&lt;id&gt;.&lt;kulcs&gt;). Az IceSMPCore injektálja enable()-kor;
     * mivel a kiolvasás cast-időben történik, a /icesmp reload a statikus ÉS a deklaratív
     * spellek felülbírálására is azonnal hat (a ConfiguredSpell accessorai ugyanígy élőben
     * olvasnak). Restart csak a spell-REGISZTRÁCIÓHOZ kell, nem az értékekhez.
     */
    private static volatile hu.taliann.icesmp.managers.ConfigManager balanceSource;

    public static void setBalanceSource(final hu.taliann.icesmp.managers.ConfigManager configManager) {
        balanceSource = configManager;
    }

    protected static hu.taliann.icesmp.managers.ConfigManager balanceSource() {
        return balanceSource;
    }

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
        return balanceInt("cooldown", cooldown);
    }

    @Override
    public SpellCostType getCostType() {
        return costType;
    }

    @Override
    public int getCostAmount() {
        return balanceInt("cost-amount", costAmount);
    }

    /** A spell-balance.&lt;id&gt;.&lt;key&gt; felülbírálás, vagy a kódbeli default. */
    protected double balance(final String key, final double defaultValue) {
        final hu.taliann.icesmp.managers.ConfigManager source = balanceSource;
        return source == null ? defaultValue : source.getDouble("spell-balance." + id + "." + key, defaultValue);
    }

    /** Egész értékű balansz-felülbírálás (ticks, darabszám, cooldown…). */
    protected int balanceInt(final String key, final int defaultValue) {
        final hu.taliann.icesmp.managers.ConfigManager source = balanceSource;
        return source == null ? defaultValue : source.getInt("spell-balance." + id + "." + key, defaultValue);
    }

    /**
     * Static variant for the rare {@code static} helper methods (e.g. dodge-chance checks invoked
     * from listeners without a spell instance) that still need a cast-time balansz-felülbírálás.
     */
    protected static double balanceStatic(final String spellId, final String key, final double defaultValue) {
        final hu.taliann.icesmp.managers.ConfigManager source = balanceSource;
        return source == null ? defaultValue : source.getDouble("spell-balance." + spellId + "." + key, defaultValue);
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

