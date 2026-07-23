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

    /**
     * ITEM_MODEL (1.21.4+) — string-alapú modell-id az integer-CMD helyett (a modern RP-út).
     * A pack az {@code assets/<ns>/items/<path>.json}-t szállítja; nincs vanília-modell-szerkesztés,
     * és az item Materialja független a megjelenéstől. Ismeretlen/üres kulcs → no-op.
     */
    public static void applyItemModel(final ItemStack item, final String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return;
        }
        final Key key = Key.key(modelId.contains(":") ? modelId : "icesmp:" + modelId);
        item.setData(DataComponentTypes.ITEM_MODEL, key);
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
     * Recept-vezérelt fogyaszthatóság (új ételek/italok kód nélkül): a
     * {@code result.consumable} YAML-blokkból épít FOOD (opcionális) + CONSUMABLE komponenst.
     * Kulcsok: {@code animation} (eat|drink), {@code seconds}, {@code nutrition},
     * {@code saturation}, {@code always-edible}, {@code effects} (lista: "TÍPUS:másodperc:szint").
     * A tápérték csak akkor kerül fel, ha a {@code nutrition} meg van adva (>=0).
     */
    public static void applyRecipeConsumable(final ItemStack item,
                                             final org.bukkit.configuration.ConfigurationSection section) {
        final boolean drink = "drink".equalsIgnoreCase(section.getString("animation", "eat"));
        final int nutrition = section.getInt("nutrition", -1);
        if (nutrition >= 0) {
            applyFood(item, nutrition, (float) section.getDouble("saturation", 0.0D),
                    section.getBoolean("always-edible", false));
        }
        final List<PotionEffect> effects = new ArrayList<>();
        for (final String token : section.getStringList("effects")) {
            final PotionEffect parsed = parseEffect(token);
            if (parsed != null) {
                effects.add(parsed);
            }
        }
        applyConsumable(item, drink ? ItemUseAnimation.DRINK : ItemUseAnimation.EAT,
                drink ? DRINK_SOUND : EAT_SOUND,
                (float) Math.max(0.1D, section.getDouble("seconds", drink ? 1.6D : 1.6D)),
                section.getBoolean("particles", true), effects);
    }

    /** "TÍPUS:másodperc:szint" → PotionEffect (a szint opcionális, default 0). Ismeretlen típus → null. */
    private static PotionEffect parseEffect(final String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        final String[] parts = token.split(":");
        final PotionEffectType type = org.bukkit.Registry.EFFECT.get(
                NamespacedKey.minecraft(parts[0].trim().toLowerCase(java.util.Locale.ROOT)));
        if (type == null) {
            return null;
        }
        int seconds = 30;
        int amplifier = 0;
        try {
            if (parts.length > 1) {
                seconds = Integer.parseInt(parts[1].trim());
            }
            if (parts.length > 2) {
                amplifier = Integer.parseInt(parts[2].trim());
            }
        } catch (final NumberFormatException ignored) {
            // Hibás szám → alapértékek maradnak (a recept-config hibája ne dobjon craftkor).
        }
        return new PotionEffect(type, Math.max(1, seconds) * 20, Math.max(0, amplifier), true, true, true);
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
