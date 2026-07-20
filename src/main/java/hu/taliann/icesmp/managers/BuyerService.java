package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.Map;

/**
 * Felvásárló NPC ("felvasarlo") — kiszámítható, NAPI KERETES jövedelem-csap: a játékos a
 * KÉZBEN tartott terményt/nyersanyagot jobb-kattal eladja fix, alacsony egységáron
 * (economy.yml {@code buyer.prices.<MATERIAL>}). A napi keret ({@code buyer.daily-cap},
 * player-PDC-ben követve) fékezi az auto-farm inflációt — a piac (játékos-játékos)
 * marad a jobb ár, a Felvásárló a biztos alap. Egyedi (PDC-s) tárgyat SOSEM vesz meg,
 * hogy unique anyag/signature item ne váljon pénzzé nyomott áron.
 *
 * <p>Folia: a FancyNpcs interact-hook a játékos saját régió-szálán hívja — az
 * inventory-írás és a PDC ott biztonságos. Minden kulcs élőben olvasódik (buyer.*).
 */
public final class BuyerService {

    private final ConfigManager configManager;
    private final CurrencyManager currencyManager;
    private final hu.taliann.icesmp.managers.FactionManager factionManager;
    private final MessageManager messageManager;
    private final NamespacedKey soldDayKey;
    private final NamespacedKey soldValueKey;

    public BuyerService(final JavaPlugin plugin, final ConfigManager configManager,
                        final CurrencyManager currencyManager,
                        final hu.taliann.icesmp.managers.FactionManager factionManager,
                        final MessageManager messageManager) {
        this.configManager = configManager;
        this.currencyManager = currencyManager;
        this.factionManager = factionManager;
        this.messageManager = messageManager;
        this.soldDayKey = new NamespacedKey(plugin, "buyer_sold_day");
        this.soldValueKey = new NamespacedKey(plugin, "buyer_sold_value");
    }

    /** A Felvásárló-NPC neve (a FancyNpcs interact-hook erre szűr, kisbetűsen). */
    public String npcName() {
        return configManager.getString("buyer.npc-name", "felvasarlo").toLowerCase(Locale.ROOT);
    }

    public boolean isEnabled() {
        return configManager.getBoolean("buyer.enabled", true);
    }

    /** A kézben tartott stack felvásárlása (a játékos saját régió-szálán fut). */
    public void handle(final Player player) {
        if (!isEnabled()) {
            return;
        }
        final ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            player.sendMessage(messageManager.getMessage("buyer-empty-hand",
                    "<gray>🪙 „Mutasd, mit hoztál — a kezedben tartsd, úgy alkuszunk.”</gray>"));
            return;
        }
        // Egyedi (PDC-adatos) tárgyat nem veszünk: unique anyag, signature, relikvia,
        // nevesített loot — azok a piacon/rendszereken érnek, nem nyomott felvásárlói áron.
        if (hand.hasItemMeta() && !hand.getItemMeta().getPersistentDataContainer().isEmpty()) {
            player.sendMessage(messageManager.getMessage("buyer-special-item",
                    "<gray>🪙 „Ez különleges holmi — ilyet én nem veszek. Vidd a piacra!”</gray>"));
            return;
        }
        final double unitPrice = configManager.getDouble(
                "buyer.prices." + hand.getType().name(), -1.0D);
        if (unitPrice <= 0.0D) {
            player.sendMessage(messageManager.getMessage("buyer-not-buying",
                    "<gray>🪙 „Ilyesmire most nincs vevőm. A táblámon van, mit keresek.”</gray>"));
            return;
        }

        // Napi keret (PDC): a nap fordulásakor nullázódik; ami belefér, azt veszi meg.
        final long today = System.currentTimeMillis() / 86_400_000L;
        final long storedDay = player.getPersistentDataContainer()
                .getOrDefault(soldDayKey, PersistentDataType.LONG, -1L);
        double soldToday = storedDay == today ? player.getPersistentDataContainer()
                .getOrDefault(soldValueKey, PersistentDataType.DOUBLE, 0.0D) : 0.0D;
        final double dailyCap = Math.max(0.0D, configManager.getDouble("buyer.daily-cap", 250.0D));
        final double remaining = dailyCap - soldToday;
        if (remaining < unitPrice) {
            player.sendMessage(messageManager.getMessage("buyer-cap-reached",
                    "<gray>🪙 „Mára kimerült a kasszám feléd — gyere vissza holnap!”</gray>"));
            return;
        }
        final int sellable = Math.min(hand.getAmount(), (int) Math.floor(remaining / unitPrice));
        final double value = Math.floor(sellable * unitPrice * 100.0D) / 100.0D;
        hand.setAmount(hand.getAmount() - sellable);
        final CurrencyType currency = CurrencyType.fromFactionType(
                factionManager.getFaction(player.getUniqueId()));
        currencyManager.addToBalance(player.getUniqueId(), currency, value);
        player.getPersistentDataContainer().set(soldDayKey, PersistentDataType.LONG, today);
        player.getPersistentDataContainer().set(soldValueKey, PersistentDataType.DOUBLE, soldToday + value);
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_TRADE, 0.8F, 1.1F);
        player.sendMessage(messageManager.getMessage("buyer-sold",
                "<gold>🪙 Eladva: <white>{amount}× {item}</white> — <white>+{value}</white> a számládon. <gray>(Mai keretedből maradt: {left})</gray></gold>",
                Map.of("amount", String.valueOf(sellable),
                        "item", hand.getType().name().toLowerCase(Locale.ROOT).replace('_', ' '),
                        "value", currencyManager.formatBalance(value),
                        "left", currencyManager.formatBalance(Math.max(0.0D, remaining - value)))));
    }
}
