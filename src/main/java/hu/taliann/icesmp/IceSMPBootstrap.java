package hu.taliann.icesmp;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.registry.data.EnchantmentRegistryEntry;
import io.papermc.paper.registry.event.RegistryEvents;
import io.papermc.paper.registry.keys.EnchantmentKeys;
import io.papermc.paper.registry.keys.tags.ItemTypeTagKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.jspecify.annotations.NonNull;

/**
 * Bootstrap-szintű regisztrációk — a szerver-registry befagyása ELŐTT fut, itt lehet
 * a data-driven registrykbe (enchant, damage-type, banner-minta…) új bejegyzést tenni.
 *
 * <p><b>Signature-enchantok:</b> a frakció-signature itemek lore-hű, EGYEDI enchantokat
 * kapnak (Jégfog, Vihartűz, Vérszomj, Fagypáncél, Főnixtoll, Érc-érzék, Bokic Kegye).
 * A data-driven enchant-registry a kliensre szinkronizálódik, így a magyar nevük valódi
 * enchant-sorként renderelődik a tooltipben — kliens-mod és resource pack nélkül. A
 * tényleges perk-viselkedés a SignatureItemListenerben él (az enchant az identitás +
 * glint + anvil-szabályok hordozója); a craft-kori rástampelést a
 * {@code signature.custom-enchants} kulcs kapcsolja (crafting.yml, élőben olvasva).
 *
 * <p>FIGYELEM: a bootstrap a config-rendszer előtt fut, ezért itt nincs config-kapu —
 * a regisztráció önmagában ártalmatlan (weight=1 mellett az enchant-asztalról
 * gyakorlatilag sosem jön, a megszerzés útja a signature craft; a stamp kapcsolható).
 */
@SuppressWarnings({"UnstableApiUsage", "unused"})
public final class IceSMPBootstrap implements PluginBootstrap {

    @Override
    public void bootstrap(final @NonNull BootstrapContext context) {
        context.getLifecycleManager().registerEventHandler(RegistryEvents.ENCHANTMENT.freeze().newHandler(event -> {
            // Kulcsok: hu.taliann.icesmp.items.SignatureEnchantKeys.BY_SIGNATURE — a runtime
            // (ProfessionRecipeBookListener) ugyanezekkel a kulcsokkal keresi vissza őket.
            register(event, "jegfog", "Jégfog", NamedTextColor.AQUA,
                    ItemTypeTagKeys.ENCHANTABLE_BOW, EquipmentSlotGroup.MAINHAND);
            register(event, "vihartuz", "Vihartűz", NamedTextColor.GOLD,
                    ItemTypeTagKeys.ENCHANTABLE_CROSSBOW, EquipmentSlotGroup.MAINHAND);
            register(event, "verszomj", "Vérszomj", NamedTextColor.RED,
                    ItemTypeTagKeys.ENCHANTABLE_SWORD, EquipmentSlotGroup.MAINHAND);
            register(event, "fagypancel", "Fagypáncél", NamedTextColor.AQUA,
                    ItemTypeTagKeys.ENCHANTABLE_CHEST_ARMOR, EquipmentSlotGroup.CHEST);
            register(event, "fonixtoll", "Főnixtoll", NamedTextColor.GOLD,
                    ItemTypeTagKeys.ENCHANTABLE_CHEST_ARMOR, EquipmentSlotGroup.CHEST);
            register(event, "erc_erzek", "Érc-érzék", NamedTextColor.GREEN,
                    ItemTypeTagKeys.ENCHANTABLE_MINING, EquipmentSlotGroup.MAINHAND);
            register(event, "bokic_kegye", "Bokic Kegye", NamedTextColor.AQUA,
                    ItemTypeTagKeys.ENCHANTABLE_FISHING, EquipmentSlotGroup.MAINHAND);
        }));
    }

    /**
     * Egy signature-enchant regisztrálása. Szándékosan enchant-asztal-idegen: weight=1
     * (gyakorlatilag sosem sorsolódik), 1 a max szint, a megszerzés útja a signature
     * craft. A leírás a magyar lore-név — ezt látja a játékos a tooltipben.
     */
    private static void register(final io.papermc.paper.registry.event.RegistryFreezeEvent<org.bukkit.enchantments.Enchantment, EnchantmentRegistryEntry.Builder> event,
                                 final String id, final String displayName, final NamedTextColor color,
                                 final io.papermc.paper.registry.tag.TagKey<org.bukkit.inventory.ItemType> supportedTag,
                                 final EquipmentSlotGroup slotGroup) {
        event.registry().register(
                EnchantmentKeys.create(net.kyori.adventure.key.Key.key("icesmp", id)),
                builder -> builder
                        .description(Component.text(displayName, color))
                        .supportedItems(event.getOrCreateTag(supportedTag))
                        .anvilCost(2)
                        .maxLevel(1)
                        .weight(1)
                        .minimumCost(EnchantmentRegistryEntry.EnchantmentCost.of(20, 1))
                        .maximumCost(EnchantmentRegistryEntry.EnchantmentCost.of(50, 1))
                        .activeSlots(slotGroup));
    }
}
