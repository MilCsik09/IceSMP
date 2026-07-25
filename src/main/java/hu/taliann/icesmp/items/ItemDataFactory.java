package hu.taliann.icesmp.items;

import hu.taliann.icesmp.listeners.FactionFoodListener;
import hu.taliann.icesmp.managers.ConfigManager;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Consumable;
import io.papermc.paper.datacomponent.item.FoodProperties;
import io.papermc.paper.datacomponent.item.consumable.ConsumeEffect;
import io.papermc.paper.datacomponent.item.consumable.ItemUseAnimation;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
     * ITEM_MODEL (1.21.4+) — string-alapú modell-id a modern RP-úthoz.
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

    /**
     * USE_COOLDOWN cooldown-CSOPORT: az item saját nevesített cooldown-csoportot kap, így a
     * {@code player.setCooldown(itemStack, ticks)} / {@code setCooldown(Key, ticks)} CSAK az
     * ehhez a csoporthoz tartozó itemeket sötétíti — NEM a vele azonos Materialú vanília itemeket.
     * (A pálca-katalizátorok közönséges Materialt használnak: enélkül a castolás a játékos
     * vanília kovakövét/kürtjét is cooldownra tette.) A {@code seconds} a natív-use auto-cooldownja;
     * a tényleges cooldownt a hívó adja setCooldownnal.
     */
    public static void applyUseCooldownGroup(final ItemStack item, final String groupId, final float seconds) {
        item.setData(DataComponentTypes.USE_COOLDOWN,
                io.papermc.paper.datacomponent.item.UseCooldown.useCooldown(Math.max(0.05F, seconds))
                        .cooldownGroup(Key.key(groupId.contains(":") ? groupId : "icesmp:" + groupId))
                        .build());
    }

    /**
     * TOOLTIP_DISPLAY — a vanília ATTRIBUTE_MODIFIERS tooltip-blokk elrejtése („When in Main Hand:
     * +X …"). Az affix-gear a stat-ot amúgy is SAJÁT lore-sorban mutatja (a számmal), így a vanília
     * blokk redundáns spam; elrejtése infó-veszteség nélkül tisztít.
     */
    public static void hideAttributeTooltip(final ItemStack item) {
        item.setData(DataComponentTypes.TOOLTIP_DISPLAY,
                io.papermc.paper.datacomponent.item.TooltipDisplay.tooltipDisplay()
                        .addHiddenComponents(DataComponentTypes.ATTRIBUTE_MODIFIERS)
                        .build());
    }

    /**
     * A vanília alap-attribútumok (kard sebzése/ütemé, páncél védelme) átmentése a metába, MIELŐTT
     * bármilyen egyedi módosítót adnánk hozzá. Az ELSŐ explicit módosító felülírja a tárgy teljes
     * {@code attribute_modifiers} komponensét — enélkül a vaskard elveszítené a +6 sebzését, és csak
     * a rátett bónusz maradna. Idempotens: ha már van explicit lista, nem nyúl hozzá.
     */
    public static void seedDefaultAttributeModifiers(final ItemStack item, final ItemMeta meta) {
        if (item == null || meta == null || meta.hasAttributeModifiers()) {
            return;
        }
        final com.google.common.collect.Multimap<Attribute, AttributeModifier> defaults =
                item.getType().getDefaultAttributeModifiers();
        if (defaults != null && !defaults.isEmpty()) {
            meta.setAttributeModifiers(com.google.common.collect.HashMultimap.create(defaults));
        }
    }

    /**
     * Explicit, determinisztikus attribútum-módosítók egy custom itemre (a random affix-roll-tól
     * függetlenül). Minden spec: {@code "<attribútum>:<érték>[:<slot>[:<művelet>]]"} — pl.
     * {@code "attack_damage:2"}, {@code "max_health:4:chest"}, {@code "movement_speed:0.03:feet:add"}.
     * A slot elhagyva a Materialból következik (kard→kéz, sisak→fej…); a művelet alapból add_number
     * (skálás: {@code base}=add_scalar, {@code mult}=multiply_scalar_1). Ismeretlen attribútum/slot/
     * szám a craftot NEM töri — az adott sort kihagyja. A stat SAJÁT lore-sorként jelenik meg (a
     * vanília tooltip-blokkot a hívó a {@link #hideAttributeTooltip} data-komponenssel rejti el,
     * a setItemMeta UTÁN).
     *
     * @return true, ha legalább egy módosító felkerült (ekkor a hívó hívja a hideAttributeTooltip-et)
     */
    public static boolean applyAttributeModifiers(final ItemStack item, final List<String> specs) {
        if (item == null || specs == null || specs.isEmpty()) {
            return false;
        }
        final ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        final List<Component> statLore = new ArrayList<>();
        int index = 0;
        boolean any = false;
        for (final String raw : specs) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            final String[] parts = raw.trim().split(":");
            if (parts.length < 2) {
                continue;
            }
            final Attribute attribute = ATTRIBUTES.get(parts[0].trim().toLowerCase(Locale.ROOT));
            if (attribute == null) {
                continue;
            }
            final double amount;
            try {
                amount = Double.parseDouble(parts[1].trim());
            } catch (final NumberFormatException ignored) {
                continue;
            }
            if (amount == 0.0D) {
                continue;
            }
            final EquipmentSlotGroup slot = parts.length >= 3 && !parts[2].isBlank()
                    ? SLOTS.getOrDefault(parts[2].trim().toLowerCase(Locale.ROOT), inferSlot(item.getType()))
                    : inferSlot(item.getType());
            final AttributeModifier.Operation operation = parts.length >= 4
                    ? OPERATIONS.getOrDefault(parts[3].trim().toLowerCase(Locale.ROOT), AttributeModifier.Operation.ADD_NUMBER)
                    : AttributeModifier.Operation.ADD_NUMBER;
            // A módosító-kulcs a Materialt IS hordozza: MC 1.21-ben az azonos id-jű módosítók
            // ugyanazon attribútumon NEM összegződnek, így két külön viselt darab (sisak+mellvért)
            // azonos statja csak egyszer számítana — a Material (=felszerelés-slot) egyedivé teszi.
            final NamespacedKey modifierKey = NamespacedKey.fromString("icesmp:attr_"
                    + item.getType().name().toLowerCase(Locale.ROOT) + "_"
                    + parts[0].trim().toLowerCase(Locale.ROOT) + "_" + index);
            if (modifierKey == null) {
                continue;
            }
            seedDefaultAttributeModifiers(item, meta);
            meta.addAttributeModifier(attribute, new AttributeModifier(modifierKey, amount, operation, slot));
            statLore.add(statLine(parts[0].trim().toLowerCase(Locale.ROOT), amount, operation));
            any = true;
            index++;
        }
        if (!any) {
            return false;
        }
        final List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lore.addAll(statLore);
        meta.lore(lore);
        item.setItemMeta(meta);
        return true;
    }

    private static final java.util.Map<String, Attribute> ATTRIBUTES = java.util.Map.ofEntries(
            java.util.Map.entry("attack_damage", Attribute.ATTACK_DAMAGE),
            java.util.Map.entry("attack_speed", Attribute.ATTACK_SPEED),
            java.util.Map.entry("attack_knockback", Attribute.ATTACK_KNOCKBACK),
            java.util.Map.entry("max_health", Attribute.MAX_HEALTH),
            java.util.Map.entry("armor", Attribute.ARMOR),
            java.util.Map.entry("armor_toughness", Attribute.ARMOR_TOUGHNESS),
            java.util.Map.entry("knockback_resistance", Attribute.KNOCKBACK_RESISTANCE),
            java.util.Map.entry("movement_speed", Attribute.MOVEMENT_SPEED),
            java.util.Map.entry("luck", Attribute.LUCK));

    private static final java.util.Map<String, EquipmentSlotGroup> SLOTS = java.util.Map.ofEntries(
            java.util.Map.entry("mainhand", EquipmentSlotGroup.MAINHAND),
            java.util.Map.entry("offhand", EquipmentSlotGroup.OFFHAND),
            java.util.Map.entry("hand", EquipmentSlotGroup.HAND),
            java.util.Map.entry("head", EquipmentSlotGroup.HEAD),
            java.util.Map.entry("chest", EquipmentSlotGroup.CHEST),
            java.util.Map.entry("legs", EquipmentSlotGroup.LEGS),
            java.util.Map.entry("feet", EquipmentSlotGroup.FEET),
            java.util.Map.entry("armor", EquipmentSlotGroup.ARMOR),
            java.util.Map.entry("body", EquipmentSlotGroup.BODY),
            java.util.Map.entry("any", EquipmentSlotGroup.ANY));

    private static final java.util.Map<String, AttributeModifier.Operation> OPERATIONS = java.util.Map.of(
            "add", AttributeModifier.Operation.ADD_NUMBER,
            "base", AttributeModifier.Operation.ADD_SCALAR,
            "mult", AttributeModifier.Operation.MULTIPLY_SCALAR_1);

    private static final java.util.Map<String, String> STAT_NAMES = java.util.Map.ofEntries(
            java.util.Map.entry("attack_damage", "Támadóerő"),
            java.util.Map.entry("attack_speed", "Támadási sebesség"),
            java.util.Map.entry("attack_knockback", "Ütés-visszalökés"),
            java.util.Map.entry("max_health", "Max. életerő"),
            java.util.Map.entry("armor", "Páncél"),
            java.util.Map.entry("armor_toughness", "Páncél-szívósság"),
            java.util.Map.entry("knockback_resistance", "Visszalökés-ellenállás"),
            java.util.Map.entry("movement_speed", "Sebesség"),
            java.util.Map.entry("luck", "Szerencse"));

    private static Component statLine(final String attrKey, final double amount, final AttributeModifier.Operation op) {
        final boolean positive = amount > 0.0D;
        // ADD_SCALAR és MULTIPLY_SCALAR_1 is törtben adja meg a szorzót (0.1 = +10%), ezért mindkettőt
        // százalékra váltjuk — különben a tooltip tizedére hazudná a tényleges hatást.
        final boolean percent = op != AttributeModifier.Operation.ADD_NUMBER;
        final double shown = percent ? amount * 100.0D : amount;
        String num = String.format(Locale.ROOT, "%.2f", Math.abs(shown));
        if (num.endsWith(".00")) {
            num = num.substring(0, num.length() - 3);
        } else if (num.endsWith("0")) {
            num = num.substring(0, num.length() - 1);
        }
        final String label = STAT_NAMES.getOrDefault(attrKey, attrKey);
        return Component.text("  " + (positive ? "+ " : "- ") + num + (percent ? "% " : " ") + label,
                        positive ? NamedTextColor.GRAY : NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false);
    }

    private static EquipmentSlotGroup inferSlot(final Material material) {
        final String name = material.name();
        if (name.endsWith("_HELMET") || name.equals("TURTLE_HELMET") || name.equals("CARVED_PUMPKIN")) {
            return EquipmentSlotGroup.HEAD;
        }
        if (name.endsWith("_CHESTPLATE") || name.equals("ELYTRA")) {
            return EquipmentSlotGroup.CHEST;
        }
        if (name.endsWith("_LEGGINGS")) {
            return EquipmentSlotGroup.LEGS;
        }
        if (name.endsWith("_BOOTS")) {
            return EquipmentSlotGroup.FEET;
        }
        if (name.endsWith("_HORSE_ARMOR") || name.equals("WOLF_ARMOR")) {
            return EquipmentSlotGroup.BODY;
        }
        if (name.endsWith("_SWORD") || name.endsWith("_AXE") || name.endsWith("_PICKAXE")
                || name.endsWith("_SHOVEL") || name.endsWith("_HOE") || name.equals("TRIDENT")
                || name.equals("BOW") || name.equals("CROSSBOW") || name.equals("MACE")
                || name.equals("FISHING_ROD")) {
            return EquipmentSlotGroup.MAINHAND;
        }
        if (name.equals("SHIELD")) {
            return EquipmentSlotGroup.HAND;
        }
        return EquipmentSlotGroup.ANY;
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
