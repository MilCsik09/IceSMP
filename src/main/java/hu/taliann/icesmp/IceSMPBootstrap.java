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
 * glint + anvil-szabályok hordozója); a rástampelés a canonical ItemIdentityService
 * render/migration projection része, ezért acquisition-path szerint nem térhet el.
 *
 * <p>FIGYELEM: a bootstrap a config-rendszer előtt fut, ezért itt nincs config-kapu —
 * a regisztráció önmagában ártalmatlan: a saját enchantok NINCSENEK a
 * #minecraft:in_enchanting_table tagben, így az enchant-asztalról EGYÁLTALÁN nem
 * jönnek — a megszerzés útja kizárólag a signature craft / a tekercs-receptek
 * (a stamp itemenként kapcsolható).
 */
@SuppressWarnings({"UnstableApiUsage", "unused"})
public final class IceSMPBootstrap implements PluginBootstrap {

    @Override
    public void bootstrap(final @NonNull BootstrapContext context) {
        // Saját damage-type MINDEN varázslat-iskolának (tűz/fagy/szent/árnyék/természet/
        // vihar/káosz + ősmágia): a spell-sebzés iskolánként megkülönböztethető, ellenállás
        // (Rúnavért generikus + iskola-counter enchantok) és saját halál-üzenet adható rá.
        // A spell→iskola besorolás configból jön (spells.yml spell-schools) — a spellek a
        // SpellDamageUtil-on át ütnek; a message-id kliens-oldalon nem fordul, a magyar
        // halál-üzenetet a SpellDamageListener írja felül.
        // A regisztráció a compose eseményen fut (nem a régi freeze-en): az 1.21.11-es
        // szerver registry-providere már csak a compose horgot szolgálja ki.
        // A custom DamageType-ok executable gameplay authorityk. Registry-compose
        // hiba esetén a bootstrap fail-closed: nincs vanilla spell-damage fallback.
        context.getLifecycleManager().registerEventHandler(RegistryEvents.DAMAGE_TYPE.compose().newHandler(event -> {
            try {
                for (final hu.taliann.icesmp.data.SpellSchool school : hu.taliann.icesmp.data.SpellSchool.values()) {
                    event.registry().register(
                            io.papermc.paper.registry.keys.DamageTypeKeys.create(
                                    net.kyori.adventure.key.Key.key("icesmp", school.getTypeId())),
                            builder -> builder
                                    .messageId("death.attack.icesmp_" + school.getTypeId())
                                    .damageScaling(org.bukkit.damage.DamageScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER)
                                    .exhaustion(0.1F));
                }
                // Környezeti (nem-varázslat) damage-type-ok: nincs causing-entity, saját
                // magyar halál-üzenettel (szerver-oldalról írjuk felül). Fogyasztó KÖTELEZŐ
                // (nincs dísz-regisztráció): icesmp:rontas → a rontás-zóna mag-aurája
                // (CorruptionAuraListener). A messageId a kliensen nem fordul, a listener írja.
                event.registry().register(
                        io.papermc.paper.registry.keys.DamageTypeKeys.create(
                                net.kyori.adventure.key.Key.key("icesmp", "rontas")),
                        builder -> builder
                                .messageId("death.attack.icesmp_rontas")
                                .damageScaling(org.bukkit.damage.DamageScaling.NEVER)
                                .exhaustion(0.0F));
            } catch (final Throwable throwable) {
                context.getLogger().error("Damage-type regisztráció hiba; az IceSMP bootstrap fail-closed: "
                        + throwable, throwable);
                throw new IllegalStateException("IceSMP custom DamageType registry compose failed", throwable);
            }
        }));

        context.getLifecycleManager().registerEventHandler(RegistryEvents.ENCHANTMENT.compose().newHandler(event -> {
            try {
            // Kulcsok: hu.taliann.icesmp.items.SignatureEnchantKeys.BY_SIGNATURE — a runtime
            // (ProfessionRecipeBookListener) ugyanezekkel a kulcsokkal keresi vissza őket.
            register(event, "jegfog", "Jégfog", NamedTextColor.AQUA,
                    ItemTypeTagKeys.ENCHANTABLE_BOW, EquipmentSlotGroup.MAINHAND);
            register(event, "vihartuz", "Vihartűz", NamedTextColor.GOLD,
                    ItemTypeTagKeys.ENCHANTABLE_CROSSBOW, EquipmentSlotGroup.MAINHAND);
            register(event, "verszomj", "Vérszomj", NamedTextColor.RED,
                    ItemTypeTagKeys.ENCHANTABLE_SHARP_WEAPON, EquipmentSlotGroup.MAINHAND);
            register(event, "fagypancel", "Fagypáncél", NamedTextColor.AQUA,
                    ItemTypeTagKeys.ENCHANTABLE_CHEST_ARMOR, EquipmentSlotGroup.CHEST);
            register(event, "fonixtoll", "Főnixtoll", NamedTextColor.GOLD,
                    ItemTypeTagKeys.ENCHANTABLE_CHEST_ARMOR, EquipmentSlotGroup.CHEST);
            register(event, "erc_erzek", "Érc-érzék", NamedTextColor.GREEN,
                    ItemTypeTagKeys.ENCHANTABLE_MINING, EquipmentSlotGroup.MAINHAND);
            register(event, "bokic_kegye", "Bokic Kegye", NamedTextColor.AQUA,
                    ItemTypeTagKeys.ENCHANTABLE_FISHING, EquipmentSlotGroup.MAINHAND);
            // Rúnavért — a mágia-sebzés (icesmp:magia) countere: páncél-enchant, szintenként
            // csökkenti a spell-sebzést (a számokat a SpellDamageListener + config adja).
            // Megszerzés: rúnaírnok-recept (Rúnavért-tekercs, enchantelt könyv) + üllő;
            // két azonos szintű könyv üllőn a vanília szabály szerint léphet szintet.
            event.registry().register(
                    EnchantmentKeys.create(net.kyori.adventure.key.Key.key("icesmp", "runavert")),
                    builder -> builder
                            .description(Component.text("Rúnavért", NamedTextColor.LIGHT_PURPLE))
                            .supportedItems(event.getOrCreateTag(ItemTypeTagKeys.ENCHANTABLE_ARMOR))
                            .anvilCost(4)
                            .maxLevel(3)
                            .weight(1)
                            .minimumCost(EnchantmentRegistryEntry.EnchantmentCost.of(15, 10))
                            .maximumCost(EnchantmentRegistryEntry.EnchantmentCost.of(45, 10))
                            .activeSlots(EquipmentSlotGroup.ARMOR));
            // Iskola-counter enchantok a maradék iskolákra (a Fagypáncél/Főnixtoll
            // párja) — mind mellvért-enchant, tekercs-recepttel szerezhető; egy
            // páncélon EGY iskola-counter élhet (üllő-őr: SchoolCounterAnvilListener).
            register(event, "ej_fatyol", "Éj-fátyol", NamedTextColor.DARK_PURPLE,
                    ItemTypeTagKeys.ENCHANTABLE_CHEST_ARMOR, EquipmentSlotGroup.CHEST);
            register(event, "arnyuzo", "Árnyűző", NamedTextColor.YELLOW,
                    ItemTypeTagKeys.ENCHANTABLE_CHEST_ARMOR, EquipmentSlotGroup.CHEST);
            register(event, "meregfojto", "Méregfojtó", NamedTextColor.DARK_GREEN,
                    ItemTypeTagKeys.ENCHANTABLE_CHEST_ARMOR, EquipmentSlotGroup.CHEST);
            register(event, "viharfogo", "Viharfogó", NamedTextColor.DARK_AQUA,
                    ItemTypeTagKeys.ENCHANTABLE_CHEST_ARMOR, EquipmentSlotGroup.CHEST);
            register(event, "kaosz_zabla", "Káosz-zabla", NamedTextColor.DARK_RED,
                    ItemTypeTagKeys.ENCHANTABLE_CHEST_ARMOR, EquipmentSlotGroup.CHEST);
            } catch (final Throwable throwable) {
                context.getLogger().error("Enchant-regisztráció hiba; az IceSMP bootstrap fail-closed: "
                        + throwable, throwable);
                throw new IllegalStateException("IceSMP custom enchant registry compose failed", throwable);
            }
        }));

        discoverDatapack(context);
    }

    /**
     * A jarból szállított datapack felderítése (haladás-fa + toast-bejegyzések).
     *
     * <p><b>Miért itt és miért így:</b> a plugin-oldali advancementek korábban futásidőben,
     * a {@code Bukkit.getUnsafe().loadAdvancement(...)} úton kerültek a szerver-registrybe.
     * Az a metódus {@code @Deprecated} — MC/Paper-bumpnál ez az első törési pont. A
     * {@code DATAPACK_DISCOVERY} lifecycle-event a támogatott út: a jarban lévő
     * {@code /datapack} könyvtárat rendes datapackként ismerteti meg a szerverrel, így a fa
     * a kóddal együtt verziózódik és nem kell registry-mutáció.
     *
     * <p>Fail-soft: ha a felderítés bármiért elbukik (jar-URI, világ-betöltési sorrend),
     * csak logolunk — az {@code AdvancementService} enable-időben észreveszi, hogy a
     * bejegyzések nincsenek ott, és a régi (deprecated) úton pótolja őket. A játékmenet
     * egyik esetben sem sérül.
     */
    private static void discoverDatapack(final BootstrapContext context) {
        context.getLifecycleManager().registerEventHandler(
                io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents.DATAPACK_DISCOVERY,
                event -> {
                    try {
                        final java.net.URI jar = context.getPluginSource().toUri();
                        final java.net.URI packUri = java.net.URI.create("jar:" + jar + "!/datapack");
                        event.registrar().discoverPack(packUri, "icesmp", configurer -> configurer
                                .title(Component.text("IceSMP", NamedTextColor.AQUA))
                                .autoEnableOnServerStart(true));
                    } catch (final Throwable throwable) {
                        context.getLogger().warn(
                                "Az IceSMP datapack (haladás-fa) felderítése nem sikerult: {} — "
                                        + "a fa a tartalek uton tolt be.", throwable.toString());
                    }
                });
    }

    /**
     * Egy signature-enchant regisztrálása. Enchant-asztalról nem szerezhető (nem tagja
     * az in_enchanting_table tagnek); a megszerzés útja a signature craft. A leírás a
     * magyar lore-név — ezt látja a játékos a tooltipben.
     */
    private static void register(final io.papermc.paper.registry.event.RegistryComposeEvent<org.bukkit.enchantments.Enchantment, EnchantmentRegistryEntry.Builder> event,
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
