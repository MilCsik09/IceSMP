package hu.taliann.icesmp.items;

import hu.taliann.icesmp.listeners.FactionFoodListener;
import hu.taliann.icesmp.managers.ConfigManager;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Consumable;
import io.papermc.paper.datacomponent.item.FoodProperties;
import io.papermc.paper.datacomponent.item.consumable.ConsumeEffect;
import io.papermc.paper.datacomponent.item.consumable.ItemUseAnimation;
import net.kyori.adventure.key.Key;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;

/**
 * P7 — data-component réteg (1.20.5+). Deklaratív item-viselkedés a listener-kód helyett:
 * {@code itemStack.setData(DataComponentTypes.X, builder)}. Az API @Experimental, de éles-stabil.
 *
 * <p>FONTOS sorrend-invariáns: a {@code setData(...)} UTÁN a {@code setItemMeta(...)} TÖRLI a
 * beállított komponenst (a meta-round-trip nem hordozza). Ezért a data-komponenseket MINDIG
 * a meta-műveletek UTÁN, utolsóként kell alkalmazni (a buildResult a végén hívja).
 *
 * <p>Az identitás-PDC (signature_item, unique-id…) NEM váltható ki komponenssel — az marad.
 */
@SuppressWarnings("UnstableApiUsage")
public final class ItemDataFactory {

    /** A CONSUMABLE-migrált ételek jelölője: a FactionFoodListener a legacy-buffot ez alapján hagyja ki. */
    public static final NamespacedKey FOOD_V2_KEY = NamespacedKey.fromString("icesmp:food_v2");

    private static final Key EAT_SOUND = Key.key("minecraft:entity.generic.eat");
    private static final Key DRINK_SOUND = Key.key("minecraft:entity.generic.drink");

    private ItemDataFactory() {
    }

    /**
     * Általános fogyaszthatóság (étel VAGY ital): evés-animáció, hang, idő, opcionális
     * effektek. Új tartalomhoz és migrációhoz egyaránt. Az effekteket a hívó adja (a
     * konfigból), így a craft-idő tükrözi az élő configot.
     */
    public static void applyConsumable(final ItemStack item, final ItemUseAnimation animation, final Key sound,
                                       final float seconds, final boolean particles, final List<PotionEffect> effects) {
        final Consumable.Builder builder = Consumable.consumable()
                .animation(animation)
                .sound(sound)
                .consumeSeconds(seconds)
                .hasConsumeParticles(particles);
        if (effects != null && !effects.isEmpty()) {
            builder.addEffect(ConsumeEffect.applyStatusEffects(List.copyOf(effects), 1.0F));
        }
        item.setData(DataComponentTypes.CONSUMABLE, builder.build());
    }

    /** Táplálkozási érték: bármely item ehetővé tétele (új ételekhez). */
    public static void applyFood(final ItemStack item, final int nutrition, final float saturation,
                                 final boolean canAlwaysEat) {
        item.setData(DataComponentTypes.FOOD, FoodProperties.food()
                .nutrition(Math.max(0, nutrition))
                .saturation(Math.max(0.0F, saturation))
                .canAlwaysEat(canAlwaysEat)
                .build());
    }

    /**
     * A K6 signature-ételek buff-migrációja: a fix-effektű ételek (Pisztráng, Rántotta,
     * Pörkölt, Vadlakoma, Lepény, Hamulakoma, Hamukenyér) buffja a CONSUMABLE-be kerül
     * (craft-időben olvasott config-időtartammal), és jelölőt kap ({@link #FOOD_V2_KEY}),
     * hogy a FactionFoodListener a saját legacy-buffját kihagyja rájuk. A Süti NEM migrál
     * (felfelé lökés + partikel — nem potion-effekt), az a listenerben marad.
     *
     * @return true, ha az étel migrálható signature (a hívó ekkor tudja, hogy komponens került rá)
     */
    public static boolean applySignatureFoodConsumable(final ItemStack item, final String signature,
                                                       final ConfigManager config) {
        final List<PotionEffect> effects = signatureFoodEffects(signature, config);
        if (effects.isEmpty()) {
            return false;
        }
        // Jelölő a metán ELŐSZÖR (setItemMeta), a komponens UTOLSÓnak (setData) — különben a
        // setItemMeta törölné a CONSUMABLE-t.
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(FOOD_V2_KEY, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        applyConsumable(item, ItemUseAnimation.EAT, EAT_SOUND, 1.6F, true, effects);
        return true;
    }

    private static List<PotionEffect> signatureFoodEffects(final String sig, final ConfigManager config) {
        final List<PotionEffect> effects = new ArrayList<>();
        if (sig == null) {
            return effects;
        }
        switch (sig) {
            case FactionFoodListener.PISZTRANG -> add(effects, PotionEffectType.ABSORPTION,
                    config.getInt("factions.food-duty.pisztrang-buff-seconds", 60), 0);
            case FactionFoodListener.RANTOTTA -> add(effects, PotionEffectType.FIRE_RESISTANCE,
                    config.getInt("factions.food-duty.rantotta-buff-seconds", 60), 0);
            case FactionFoodListener.PORKOLT -> add(effects, PotionEffectType.STRENGTH,
                    config.getInt("factions.food-duty.porkolt-buff-seconds", 45), 0);
            case FactionFoodListener.VADLAKOMA -> {
                final int seconds = config.getInt("factions.food-duty.vadlakoma-buff-seconds", 45);
                add(effects, PotionEffectType.SPEED, seconds, 0);
                add(effects, PotionEffectType.FIRE_RESISTANCE, seconds, 0);
            }
            case FactionFoodListener.LEPENY -> {
                final int seconds = config.getInt("factions.food-duty.lepeny-buff-seconds", 60);
                add(effects, PotionEffectType.LUCK, seconds, 0);
                add(effects, PotionEffectType.SPEED, seconds, 0);
            }
            case FactionFoodListener.HAMULAKOMA -> {
                final int seconds = config.getInt("factions.food-duty.hamulakoma-buff-seconds", 60);
                add(effects, PotionEffectType.ABSORPTION, seconds, 0);
                add(effects, PotionEffectType.NIGHT_VISION, seconds, 0);
            }
            case FactionFoodListener.HAMUKENYER -> add(effects, PotionEffectType.NIGHT_VISION,
                    config.getInt("factions.food-duty.hamukenyer-buff-seconds", 60), 0);
            default -> { }
        }
        return effects;
    }

    private static void add(final List<PotionEffect> effects, final PotionEffectType type,
                            final int seconds, final int amplifier) {
        effects.add(new PotionEffect(type, Math.max(1, seconds) * 20, amplifier, true, true, true));
    }
}
