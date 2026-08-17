package hu.taliann.icesmp.itemization;

import hu.taliann.icesmp.data.JobType;
import hu.taliann.icesmp.data.SpecializationType;
import hu.taliann.icesmp.pve.EquippedCombatPowerModel;
import org.bukkit.Material;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure Equipment 2.0 authority, balance and anti-bypass regression gate. */
public final class EquipmentDomainRegressionSuite {
    private static int assertions;

    private EquipmentDomainRegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        allThirteenClassesHaveOneFamilyAndFiftyTwoDecisions();
        specializationsNeverChangeFamily();
        schemaAndRestrictionPrecedenceFailClosed();
        vanillaBasicArmorRemainsClassAgnostic();
        referenceBuildsRemainComparable();
        familyAwareLootPrefersWithoutEliminatingTradeDrops();
        runtimeConsumersHaveFailClosedGates();
        System.out.println("Equipment 2.0 regression suite passed: " + assertions + " assertions.");
    }

    private static void allThirteenClassesHaveOneFamilyAndFiftyTwoDecisions() {
        check(EquipmentProficiencyPolicy.classFamilies().size() == 13,
                "exactly thirteen class mappings are canonical");
        for (final JobType job : JobType.values()) {
            final ArmorFamily own = EquipmentProficiencyPolicy.familyOf(job);
            check(own != null, job + " has one family");
            for (final ArmorFamily candidate : ArmorFamily.values()) {
                final var decision = EquipmentProficiencyPolicy.decide(job, null,
                        armor("proficiency_" + job.getId() + '_' + candidate.id(), candidate,
                                Map.of("max_health", 2.0D), Set.of()));
                check(decision.allowed() == (candidate == own),
                        job + " -> " + candidate + " proficiency decision");
            }
        }
    }

    private static void specializationsNeverChangeFamily() {
        for (final SpecializationType specialization : SpecializationType.values()) {
            final JobType parent = specialization.getParentJob();
            final ArmorFamily family = EquipmentProficiencyPolicy.familyOf(parent);
            final var decision = EquipmentProficiencyPolicy.decide(parent, specialization,
                    armor("spec_" + specialization.getId(), family,
                            Map.of("max_health", 2.0D), Set.of()));
            check(decision.allowed(), specialization + " retains the parent class family");
        }
    }

    private static void schemaAndRestrictionPrecedenceFailClosed() {
        expectFailure(() -> template("missing_family", ItemTemplate.Family.ARMOR,
                        null, ItemTemplate.Slot.CHEST, Map.of("armor", 1.0D), Set.of()),
                "armor slot without family is rejected");
        expectFailure(() -> template("family_on_weapon", ItemTemplate.Family.WEAPON,
                        ArmorFamily.CLOTH, ItemTemplate.Slot.MAIN_HAND,
                        Map.of("ability_power", 1.0D), Set.of()),
                "armor family on weapon is rejected");
        final ItemTemplate warriorPlate = armor("warrior_plate", ArmorFamily.PLATE,
                Map.of("armor", 3.0D), Set.of(JobType.WARRIOR.getId()));
        check(EquipmentProficiencyPolicy.decide(JobType.PALADIN, null, warriorPlate).status()
                        == EquipmentProficiencyPolicy.Status.CLASS_RESTRICTED,
                "explicit class restriction has priority over family proficiency");
        check(EquipmentProficiencyPolicy.decide(null, null, warriorPlate).status()
                        == EquipmentProficiencyPolicy.Status.NO_CLASS,
                "pre-class canonical armor fails closed");
        final ItemTemplate weapon = template("open_weapon", ItemTemplate.Family.WEAPON,
                null, ItemTemplate.Slot.MAIN_HAND, Map.of("attack_damage", 1.0D), Set.of());
        check(EquipmentProficiencyPolicy.decide(null, null, weapon).allowed(),
                "unrestricted non-armor does not inherit armor proficiency");
    }

    private static void vanillaBasicArmorRemainsClassAgnostic() {
        for (final JobType ignored : JobType.values()) {
            check(ItemTransformationPolicy.isBasicSurvivalGear(Material.IRON_CHESTPLATE),
                    "iron armor remains BASIC survival gear");
            check(ItemTransformationPolicy.isBasicSurvivalGear(Material.DIAMOND_LEGGINGS),
                    "diamond armor remains BASIC survival gear");
            check(ItemTransformationPolicy.isBasicSurvivalGear(Material.NETHERITE_BOOTS),
                    "netherite armor remains BASIC survival gear");
        }
    }

    private static void referenceBuildsRemainComparable() {
        final Map<ArmorFamily, ArmorFamilyProfile> profiles = Map.of(
                ArmorFamily.CLOTH, new ArmorFamilyProfile(ArmorFamily.CLOTH, 0.70D,
                        0.35D, 0.25D, 0.40D, Set.of("ability_power"), Set.of("armor_toughness")),
                ArmorFamily.LEATHER, new ArmorFamilyProfile(ArmorFamily.LEATHER, 0.90D,
                        0.40D, 0.25D, 0.35D, Set.of("movement_speed"), Set.of("armor_toughness")),
                ArmorFamily.MAIL, new ArmorFamilyProfile(ArmorFamily.MAIL, 1.10D,
                        0.33D, 0.40D, 0.27D, Set.of("armor"), Set.of()),
                ArmorFamily.PLATE, new ArmorFamilyProfile(ArmorFamily.PLATE, 1.35D,
                        0.25D, 0.60D, 0.15D, Set.of("max_health"), Set.of("movement_speed")));
        final List<ItemTemplate> references = List.of(
                armor("cloth_caster", ArmorFamily.CLOTH, Map.of("ability_power", 8.0D), Set.of()),
                armor("cloth_healer", ArmorFamily.CLOTH,
                        Map.of("ability_power", 5.0D, "max_health", 5.0D), Set.of()),
                armor("leather_melee", ArmorFamily.LEATHER,
                        Map.of("attack_damage", 2.0D, "movement_speed", 0.010D), Set.of()),
                armor("leather_mobility", ArmorFamily.LEATHER,
                        Map.of("attack_speed", 0.15D, "movement_speed", 0.020D), Set.of()),
                armor("mail_ranged", ArmorFamily.MAIL,
                        Map.of("ability_power", 3.0D, "armor", 2.0D), Set.of()),
                armor("mail_hybrid", ArmorFamily.MAIL,
                        Map.of("ability_power", 2.0D, "max_health", 4.0D, "armor", 2.0D), Set.of()),
                armor("plate_tank", ArmorFamily.PLATE,
                        Map.of("max_health", 6.0D, "armor", 4.0D), Set.of()),
                armor("plate_damage", ArmorFamily.PLATE,
                        Map.of("attack_damage", 2.0D, "armor", 3.0D), Set.of()));
        final ArrayList<Double> powers = new ArrayList<>();
        for (final ItemTemplate reference : references) {
            final ArmorFamilyProfile profile = profiles.get(reference.armorFamily());
            final EquipmentBudgetModel.Budget budget = EquipmentBudgetModel.midpoint(reference, profile);
            check(Double.isFinite(budget.normalizedTotal()) && budget.normalizedTotal() > 0.0D,
                    reference.templateId() + " has a finite normalized budget");
            final double ratio = budget.normalizedTotal() / budget.expectedTierBudget();
            powers.add(EquippedCombatPowerModel.estimate(30, List.of(
                    new EquippedCombatPowerModel.GearSignal(reference.slot(), reference.itemLevel(),
                            reference.rarity(), reference.baseDamage(), reference.baseArmor(),
                            reference.fixedStats(), Map.of(), 0, 0, "",
                            profile.baseArmorCoefficient(), ratio))));
        }
        final double minimum = powers.stream().mapToDouble(Double::doubleValue).min().orElseThrow();
        final double maximum = powers.stream().mapToDouble(Double::doubleValue).max().orElseThrow();
        check(maximum / minimum < 1.75D,
                "same-tier family reference builds stay within a bounded power band");
    }

    private static void familyAwareLootPrefersWithoutEliminatingTradeDrops() {
        final ItemTemplate own = armor("own_cloth", ArmorFamily.CLOTH,
                Map.of("ability_power", 4.0D), Set.of());
        final ItemTemplate trade = armor("trade_plate", ArmorFamily.PLATE,
                Map.of("armor", 4.0D), Set.of());
        final BuildAwareLootService service = new BuildAwareLootService();
        final BuildAwareLootService.Context context = new BuildAwareLootService.Context(
                30, JobType.WIZARD.getId(), "", Set.of(), ItemTemplate.Slot.CHEST,
                Set.of("regression"));
        final var tuning = BuildAwareLootService.Tuning.defaults();
        final var diversity = LootDiversityState.empty();
        final var ownWeight = service.weight(own, context, diversity, tuning);
        final var tradeWeight = service.weight(trade, context, diversity, tuning);
        check(ownWeight.personalizationMultiplier() > tradeWeight.personalizationMultiplier(),
                "own ArmorFamily is a stronger relevance signal");
        check(ownWeight.personalizationMultiplier() <= 1.5D,
                "ArmorFamily relevance obeys the existing 1.5x cap");
        check(tradeWeight.weight() > 0.0D,
                "other-family trade loot remains possible");
        int ownDrops = 0;
        int tradeDrops = 0;
        final java.util.Random random = new java.util.Random(2_026_081_7L);
        for (int sample = 0; sample < 20_000; sample++) {
            final ItemTemplate selected = service.select(List.of(own, trade), context,
                    diversity, tuning, random.nextDouble()).orElseThrow().template();
            if (selected == own) ownDrops++; else tradeDrops++;
        }
        check(ownDrops > tradeDrops && tradeDrops > 0,
                "seeded loot statistics prefer own family without deterministic personal loot");
    }

    private static void runtimeConsumersHaveFailClosedGates() throws Exception {
        final String combatPower = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/pve/EquippedCombatPowerService.java"));
        final String signature = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/listeners/SignatureItemListener.java"));
        final String runes = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/listeners/RuneEffectListener.java"));
        final String lifecycle = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/listeners/EquipmentProficiencyListener.java"));
        final String market = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/managers/MarketManager.java"));
        final String marketGui = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/gui/MarketGUI.java"));
        check(combatPower.contains("proficiency.canUse(player, template)"),
                "CombatPower suppresses incompatible canonical gear");
        check(signature.contains("proficiency.isActive(player, item, slot)"),
                "Signature effects use the proficiency authority");
        check(runes.contains("proficiency.isActive(player, item, slot)"),
                "rune effects use the proficiency authority");
        for (final String route : List.of("InventoryClickEvent", "InventoryDragEvent",
                "BlockDispenseArmorEvent", "EntityEquipmentChangedEvent", "PlayerJoinEvent",
                "PlayerRespawnEvent")) {
            check(lifecycle.contains(route), route + " is covered by equip reconciliation");
        }
        check(lifecycle.contains("setEquipmentSuppressed"),
                "post-mutation bypasses are suppressed before reconciliation");
        check(market.contains("ArmorFamily armorFamily")
                        && market.contains("filter.armorFamily()"),
                "market metadata and structured filters expose ArmorFamily");
        for (final String familyFilter : List.of("@cloth", "@leather", "@mail", "@plate")) {
            check(marketGui.contains(familyFilter), familyFilter + " market filter is available");
        }
    }

    private static ItemTemplate armor(final String id, final ArmorFamily family,
                                      final Map<String, Double> fixed,
                                      final Set<String> classRestrictions) {
        return template(id, ItemTemplate.Family.ARMOR, family, ItemTemplate.Slot.CHEST,
                fixed, classRestrictions);
    }

    private static ItemTemplate template(final String id, final ItemTemplate.Family itemFamily,
                                         final ArmorFamily armorFamily, final ItemTemplate.Slot slot,
                                         final Map<String, Double> fixed,
                                         final Set<String> classRestrictions) {
        return new ItemTemplate(id, ItemTemplate.CURRENT_SCHEMA, 1, id, List.of(),
                ItemRarity.RARE, 30, itemFamily, armorFamily, "", slot,
                "LEATHER_CHESTPLATE", "", "", 0, classRestrictions, Set.of(), Set.of(),
                0.0D, 0.0D, fixed, Map.of(), 0, "", "", ItemTemplate.BindPolicy.NONE,
                ItemTemplate.TradePolicy.TRADEABLE, ItemTemplate.SalvagePolicy.MATERIALS,
                Set.of("regression"), Set.of(), Set.of(), Set.of(), Map.of(), List.of(), Map.of());
    }

    private static void expectFailure(final Runnable action, final String message) {
        assertions++;
        try {
            action.run();
        } catch (final IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError(message);
    }

    private static void check(final boolean condition, final String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
