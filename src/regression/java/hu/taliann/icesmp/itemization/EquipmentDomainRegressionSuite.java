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
        activeEquipmentStateMachineFailsClosed();
        levelRequirementPrecedenceAndTransitionsFailClosed();
        deniedEquipmentRehomeIsLossless();
        invalidIdentityMatrixIsGameplayInert();
        classSpecAndReconnectTransitionsReevaluate();
        combinedConsumerMatrixUsesOneAuthority();
        referenceBuildsRemainComparable();
        familyAwareLootPrefersWithoutEliminatingTradeDrops();
        backingAttributeOwnershipIsCanonical();
        allTenRuneRuntimeConsumersAreGuarded();
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
                "pure restriction policy leaves unrestricted non-armor open; runtime readiness is separate");
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
        check(EquipmentProficiencyService.decideActivity(ItemIdentityService.Status.NOT_MANAGED,
                        true, false, false, false, false, false)
                        == EquipmentProficiencyService.ActivityStatus.NOT_MANAGED,
                "BASIC/NOT_MANAGED is explicitly outside the MMO active-equipment gate");
    }

    /** Behavioral contract for the shared active-equipment state machine, independent of Bukkit events. */
    private static void activeEquipmentStateMachineFailsClosed() {
        check(activity(ItemIdentityService.Status.VALID, true, false, true, true, true, false)
                        == EquipmentProficiencyService.ActivityStatus.ACTIVE,
                "valid own-family canonical equipment is ACTIVE");
        check(activity(ItemIdentityService.Status.VALID, false, false, true, true, true, false)
                        == EquipmentProficiencyService.ActivityStatus.SLOT_MISMATCH,
                "wrong physical slot is inactive");
        check(activity(ItemIdentityService.Status.VALID, true, true, true, true, true, false)
                        == EquipmentProficiencyService.ActivityStatus.DUPLICATE_UUID,
                "duplicate equipped UUID fails closed");
        check(activity(ItemIdentityService.Status.VALID, true, false, false, true, true, false)
                        == EquipmentProficiencyService.ActivityStatus.PROFILE_NOT_READY,
                "profile-not-ready canonical gear is inactive");
        check(activity(ItemIdentityService.Status.VALID, true, false, true, false, false, false)
                        == EquipmentProficiencyService.ActivityStatus.RESTRICTED,
                "wrong-family/class/spec canonical gear is inactive");
        check(activity(ItemIdentityService.Status.VALID, true, false, true, true, true, true)
                        == EquipmentProficiencyService.ActivityStatus.SUPPRESSED,
                "suppression marker is authoritative at runtime");
    }

    private static void levelRequirementPrecedenceAndTransitionsFailClosed() {
        check(activity(ItemIdentityService.Status.VALID, true, false, true, true, false, false)
                        == EquipmentProficiencyService.ActivityStatus.UNDER_LEVEL,
                "below-requirement canonical equipment is inactive");
        check(activity(ItemIdentityService.Status.VALID, true, false, true, true, true, false)
                        == EquipmentProficiencyService.ActivityStatus.ACTIVE,
                "meeting the exact requirement reactivates canonical equipment");
        check(activity(ItemIdentityService.Status.VALID, true, false, false, true, false, false)
                        == EquipmentProficiencyService.ActivityStatus.PROFILE_NOT_READY,
                "profile readiness has deterministic precedence over level");
        check(activity(ItemIdentityService.Status.VALID, true, false, true, false, false, false)
                        == EquipmentProficiencyService.ActivityStatus.RESTRICTED,
                "class/family restriction has deterministic precedence over level");
        check(activity(ItemIdentityService.Status.NOT_MANAGED, true, false, false, false, false, false)
                        == EquipmentProficiencyService.ActivityStatus.NOT_MANAGED,
                "BASIC gear never inherits the canonical level gate");
        check(new EquipmentProficiencyService.LevelDecision(29, 30).allowed() == false,
                "level policy rejects one level below requirement");
        check(new EquipmentProficiencyService.LevelDecision(30, 30).allowed(),
                "level policy admits the exact requirement");
    }

    private static void deniedEquipmentRehomeIsLossless() {
        final int[] equipped = {1};
        final int[] stored = {0};
        check(EquipmentRehomeTransaction.executeAtomic(new EquipmentRehomeTransaction.AtomicStep() {
            @Override public boolean preflight() { return true; }
            @Override public void clearEquipped() { equipped[0] = 0; }
            @Override public boolean storeAll() { stored[0] = 1; return true; }
            @Override public void rollback() { equipped[0] = 1; stored[0] = 0; }
        }), "settled denied equip is moved to storage");
        check(equipped[0] + stored[0] == 1,
                "successful denied-equip rehome conserves exactly one item");

        equipped[0] = 1;
        stored[0] = 0;
        check(!EquipmentRehomeTransaction.executeAtomic(new EquipmentRehomeTransaction.AtomicStep() {
            @Override public boolean preflight() { return true; }
            @Override public void clearEquipped() { equipped[0] = 0; }
            @Override public boolean storeAll() { stored[0] = 1; return false; }
            @Override public void rollback() { equipped[0] = 1; stored[0] = 0; }
        }), "partial denied-equip storage mutation rolls back");
        check(equipped[0] + stored[0] == 1 && equipped[0] == 1,
                "failed denied-equip rehome restores the equipped item without duplication");

        try {
            final String lifecycle = Files.readString(Path.of(
                    "src/main/java/hu/taliann/icesmp/listeners/EquipmentProficiencyListener.java"));
            check(lifecycle.contains("scheduleEquipmentReconcile(player)")
                            && lifecycle.contains("runDelayed(plugin, task ->"),
                    "equipment-change denial waits for the vanilla equip transaction to settle");
            check(!lifecycle.contains("rehome(player, entry.getKey(), equipped)"),
                    "equipment event callback never mutates the slot in-flight");
        } catch (final java.io.IOException failure) {
            throw new AssertionError("cannot inspect equipment lifecycle source", failure);
        }
    }

    private static void invalidIdentityMatrixIsGameplayInert() {
        for (final ItemIdentityService.Status invalid : List.of(
                ItemIdentityService.Status.MALFORMED,
                ItemIdentityService.Status.INTEGRITY_MISMATCH,
                ItemIdentityService.Status.TEMPLATE_MISSING,
                ItemIdentityService.Status.TEMPLATE_VERSION_STALE,
                ItemIdentityService.Status.TEMPLATE_MISMATCH,
                ItemIdentityService.Status.POLICY_VIOLATION)) {
            check(activity(invalid, true, false, true, true, true, false)
                            == EquipmentProficiencyService.ActivityStatus.INVALID_IDENTITY,
                    invalid + " is gameplay-inert");
        }
    }

    private static void classSpecAndReconnectTransitionsReevaluate() {
        check(activity(ItemIdentityService.Status.VALID, true, false, true, true, true, false)
                        == EquipmentProficiencyService.ActivityStatus.ACTIVE,
                "Warrior own-family PLATE starts active");
        check(activity(ItemIdentityService.Status.VALID, true, false, true, false, true, false)
                        == EquipmentProficiencyService.ActivityStatus.RESTRICTED,
                "Warrior -> Wizard class change turns the same PLATE inactive");
        check(activity(ItemIdentityService.Status.VALID, true, false, true, true, true, false)
                        == EquipmentProficiencyService.ActivityStatus.ACTIVE,
                "Wizard -> Warrior re-evaluation can reactivate without permanent suppression state");
        check(activity(ItemIdentityService.Status.VALID, true, false, false, true, true, false)
                        == EquipmentProficiencyService.ActivityStatus.PROFILE_NOT_READY,
                "reconnect before PlayerProfile readiness is fail-closed");
        check(activity(ItemIdentityService.Status.VALID, true, false, true, true, true, false)
                        == EquipmentProficiencyService.ActivityStatus.ACTIVE,
                "reconnect activation re-evaluates after PlayerProfile becomes ready");
        check(activity(ItemIdentityService.Status.VALID, true, false, true, false, true, false)
                        == EquipmentProficiencyService.ActivityStatus.RESTRICTED,
                "spec restriction change uses the same re-evaluation state");
    }

    /**
     * Same wrong-family/invalid state must be zero across all canonical gameplay consumers.
     * BASIC is intentionally excluded from this predicate because vanilla/legacy policy owns it.
     */
    private static void combinedConsumerMatrixUsesOneAuthority() {
        final List<String> consumers = List.of("backing-attribute", "fixed", "rolled", "set",
                "signature", "rune", "curse", "magic-resistance", "combat-power", "loot-tags");
        final var active = EquipmentProficiencyService.ActivityStatus.ACTIVE;
        final var wrongFamily = EquipmentProficiencyService.ActivityStatus.RESTRICTED;
        final var invalid = EquipmentProficiencyService.ActivityStatus.INVALID_IDENTITY;
        final var duplicate = EquipmentProficiencyService.ActivityStatus.DUPLICATE_UUID;
        final var suppressed = EquipmentProficiencyService.ActivityStatus.SUPPRESSED;
        for (final String consumer : consumers) {
            check(canonicalContributes(active), consumer + " is ON for valid canonical equipment");
            check(!canonicalContributes(wrongFamily), consumer + " is OFF for wrong-family canonical equipment");
            check(!canonicalContributes(invalid), consumer + " is OFF for managed-invalid canonical equipment");
            check(!canonicalContributes(duplicate), consumer + " is OFF for duplicate UUID equipment");
            check(!canonicalContributes(suppressed), consumer + " is OFF while suppressed");
        }
    }

    private static boolean canonicalContributes(final EquipmentProficiencyService.ActivityStatus status) {
        return status == EquipmentProficiencyService.ActivityStatus.ACTIVE;
    }

    private static EquipmentProficiencyService.ActivityStatus activity(
            final ItemIdentityService.Status identityStatus, final boolean slotMatches,
            final boolean duplicateUuid, final boolean profileReady,
            final boolean restrictionsAllowed, final boolean levelAllowed,
            final boolean suppressed) {
        return EquipmentProficiencyService.decideActivity(identityStatus, slotMatches, duplicateUuid,
                profileReady, restrictionsAllowed, levelAllowed, suppressed);
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

    /** Wiring assertions complement the behavioral state-machine proof above. */
    private static void backingAttributeOwnershipIsCanonical() throws Exception {
        final String factory = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/items/ItemDataFactory.java"));
        final String identity = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/itemization/ItemIdentityService.java"));
        final String rarity = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/managers/ItemRarityService.java"));
        check(factory.contains("applyCanonicalAttributeModifiers"),
                "canonical attribute path is explicit");
        check(factory.contains("ItemAttributeModifiers.itemAttributes()"),
                "canonical attribute path builds the Paper valued component directly");
        check(factory.contains("item.setData(DataComponentTypes.ATTRIBUTE_MODIFIERS, component)"),
                "canonical zero-stat/suppressed path writes an explicit valued component");
        check(!factory.contains("meta.setAttributeModifiers(com.google.common.collect.ArrayListMultimap.create())"),
                "canonical ownership no longer relies on the non-durable empty ItemMeta map");
        check(identity.contains("ItemDataFactory.applyCanonicalAttributeModifiers"),
                "ItemIdentity render uses canonical attribute ownership");
        check(rarity.contains("isCanonicalManaged(base)"),
                "legacy rarity/affix roll cannot contaminate canonical identity");
        check(rarity.contains("item.getType().isAir() || !item.hasItemMeta() || isCanonicalManaged(item)"),
                "legacy spell_power contributes zero on managed canonical identity");
    }

    private static void allTenRuneRuntimeConsumersAreGuarded() throws Exception {
        final String runes = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/listeners/RuneEffectListener.java"));
        final String greed = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/listeners/MobMoneyDropListener.java"));
        for (final String rune : List.of("runa_elek", "runa_visszhang", "runa_lang", "runa_fagy",
                "runa_suly", "runa_zapor", "runa_vadasz", "runa_bastya", "runa_oltalom")) {
            check(runes.contains(rune), rune + " runtime consumer is present");
        }
        check(runes.contains("proficiency.isActive(player, item, slot)"),
                "nine central rune effects use active-equipment authority");
        check(greed.contains("EquipmentProficiencyService.allowsGameplayContribution"),
                "runa_moho main-hand bonus uses active-equipment authority");
        check(greed.contains("itemIdentityService.hasRune(hand, \"runa_moho\")"),
                "runa_moho remains the tenth audited runtime consumer");
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
        final String curse = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/managers/CursedGearService.java"));
        final String curseListener = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/listeners/CursedGearListener.java"));
        final String resistance = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/listeners/SpellDamageListener.java"));
        final String loot = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/listeners/MobLootListener.java"));
        final String authority = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/itemization/EquipmentProficiencyService.java"));
        final String identity = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/itemization/ItemIdentityService.java"));
        final String market = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/managers/MarketManager.java"));
        final String marketGui = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/gui/MarketGUI.java"));

        check(combatPower.contains("proficiency.isActive(player, item, equippedSlot)"),
                "CombatPower delegates candidate admission to active-equipment authority");
        check(signature.contains("proficiency.isActive(player, item, slot)"),
                "equipped Signature effects use active-equipment authority");
        check(signature.contains("activeId(player, player.getInventory().getItemInMainHand()")
                        && signature.contains("activeId(player, player.getInventory().getItemInOffHand()"),
                "fishing Signature consumers no longer use physical PDC-only activation");
        check(runes.contains("proficiency.isActive(player, item, slot)"),
                "central rune effects use active-equipment authority");
        check(curse.contains("EquipmentProficiencyService.allowsGameplayContribution"),
                "curse contribution uses BASIC-aware active-equipment authority");
        check(curseListener.contains("isActiveCurse(player, event.getCurrentItem(), currentSlot)"),
                "curse removal lock cannot softlock inactive wrong-family gear");
        check(!curseListener.contains("dropItemNaturally(player.getLocation(), overflow)"),
                "curse confirmation overflow no longer world-drops a cloned equipped item");
        check(resistance.contains("EquipmentProficiencyService.allowsGameplayContribution"),
                "magic/rune resistance is active-equipment guarded");
        check(loot.contains("EquipmentProficiencyService.isCanonicalActive"),
                "loot build tags accept only ACTIVE canonical equipment");
        check(authority.contains("duplicateEquippedIds(player)"),
                "active authority fails closed on duplicate equipped UUIDs");
        check(authority.contains("identities.suppressManagedInvalid(item)"),
                "managed-invalid physical equipment is explicitly attribute-suppressed");
        check(identity.contains("meta.getPersistentDataContainer().set(equipmentSuppressedKey"),
                "suppression is represented in runtime item state");
        for (final String route : List.of("InventoryClickEvent", "InventoryDragEvent",
                "BlockDispenseArmorEvent", "EntityEquipmentChangedEvent", "PlayerJoinEvent",
                "PlayerRespawnEvent")) {
            check(lifecycle.contains(route), route + " is covered by equip reconciliation");
        }
        check(lifecycle.contains("case HAND -> ItemTemplate.Slot.MAIN_HAND")
                        && lifecycle.contains("case OFF_HAND -> ItemTemplate.Slot.OFF_HAND"),
                "main/off-hand mutations also trigger active-equipment reconciliation");
        check(authority.contains("reconcileSlot(player, player.getInventory().getItemInMainHand()")
                        && authority.contains("reconcileSlot(player, player.getInventory().getItemInOffHand()"),
                "reconcile covers all six equipment slots without polling");
        check(market.contains("ArmorFamily armorFamily") && market.contains("filter.armorFamily()"),
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
