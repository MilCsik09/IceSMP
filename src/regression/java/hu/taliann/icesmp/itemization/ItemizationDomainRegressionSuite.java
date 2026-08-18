package hu.taliann.icesmp.itemization;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ItemizationDomainRegressionSuite {

    private static int assertions;

    private ItemizationDomainRegressionSuite() {
    }

    public static void main(final String[] args) {
        templateRejectsDeadStatsAndInvalidSockets();
        normalizedQualityProducesStableRolls();
        instanceCodecRoundTripsEveryIdentityField();
        historyRemainsBoundedAndIncrementsRevision();
        rarityVocabularyHasNoLegacyConflicts();
        abilityPowerScalingIsBounded();
        runeMutationKeepsIdentityAndAdvancesRevision();
        signatureRegistryRejectsConsumerlessEffects();
        itemSetTiersActivateCumulatively();
        buildAwareWeightsStayModestAndTradeable();
        diversityHistoryIsBoundedIdempotentAndNeverGuaranteesMythic();
        controlledRerollSupportsLockAmplifierStabilityAndIdempotence();
        ascensionPreservesIdentityProvenanceRunesAndRelativeQuality();
        relativeQualityHandlesIntegerDecimalNegativeAndClamp();
        salvageIsConservativeBoundedAndLegacySafe();
        recoveryNeverGuessesAcrossAmbiguousSnapshots();
        mutationCrashRecoverySettlesExactlyOnce();
        mutationFaultMatrixCoversRerollRuneAndAscension();
        System.out.println("Itemization domain regression suite passed. assertions=" + assertions);
    }

    private static void mutationFaultMatrixCoversRerollRuneAndAscension() {
        final List<String> before = List.of("item:revision=4", "payment:present");
        final List<String> after = List.of("item:revision=5", "payment:consumed");
        for (final ItemMutationFaultMatrix.Operation operation
                : ItemMutationFaultMatrix.Operation.values()) {
            check(ItemMutationFaultMatrix.recover(ItemMutationFaultMatrix.simulate(operation,
                            ItemMutationFaultMatrix.FailurePoint.BEFORE_PREPARE, before, after))
                            == ItemMutationRecoveryPolicy.Decision.ABORT_BEFORE,
                    operation + " before prepare is a no-op");
            check(ItemMutationFaultMatrix.recover(ItemMutationFaultMatrix.simulate(operation,
                            ItemMutationFaultMatrix.FailurePoint.AFTER_PREPARE, before, after))
                            == ItemMutationRecoveryPolicy.Decision.ABORT_BEFORE,
                    operation + " prepared exact-before aborts without payment loss");
            check(ItemMutationFaultMatrix.recover(ItemMutationFaultMatrix.simulate(operation,
                            ItemMutationFaultMatrix.FailurePoint.AFTER_INVENTORY_PUBLISH, before, after))
                            == ItemMutationRecoveryPolicy.Decision.COMMIT_AFTER,
                    operation + " exact-after commits without a free retry");
            check(ItemMutationFaultMatrix.recover(ItemMutationFaultMatrix.simulate(operation,
                            ItemMutationFaultMatrix.FailurePoint.AFTER_JOURNAL_COMMIT, before, after))
                            == ItemMutationRecoveryPolicy.Decision.COMMIT_AFTER,
                    operation + " committed state remains idempotent");
            check(ItemMutationFaultMatrix.recover(ItemMutationFaultMatrix.simulate(operation,
                            ItemMutationFaultMatrix.FailurePoint.MIXED_INVENTORY, before, after))
                            == ItemMutationRecoveryPolicy.Decision.MANUAL_REVIEW,
                    operation + " mixed state fails closed");
        }
    }

    private static void templateRejectsDeadStatsAndInvalidSockets() {
        expectFailure(() -> template(Map.of("critical_chance", 5.0D), Map.of(), 1),
                "dead stats are rejected at the template boundary");
        expectFailure(() -> template(Map.of(), Map.of(), 3),
                "templates cannot exceed two rune sockets");
        check(template(Map.of("ability_power", 7.0D),
                Map.of("max_health", new ItemTemplate.StatRange(3.0D, 9.0D)), 2)
                .fixedStats().containsKey("ability_power"), "live consumer stats are accepted");
    }

    private static void normalizedQualityProducesStableRolls() {
        final ItemTemplate.StatRange range = new ItemTemplate.StatRange(10.0D, 30.0D);
        check(range.valueAt(0.0D) == 10.0D, "zero quality maps to the authored minimum");
        check(range.valueAt(0.5D) == 20.0D, "half quality maps linearly");
        check(range.valueAt(1.0D) == 30.0D, "full quality maps to the authored maximum");
        check(range.matches(20.0D, 0.5D), "stored value and quality must reproduce the authored roll");
        check(!range.matches(21.0D, 0.5D), "tampered roll value is distinguishable from its quality");
        expectFailure(() -> range.valueAt(1.01D), "out-of-range quality is rejected");
    }

    private static void instanceCodecRoundTripsEveryIdentityField() {
        final UUID itemId = UUID.fromString("d1470920-a3c2-4662-a975-400233f08a28");
        final UUID crafterId = UUID.fromString("fc296c9c-d2d5-4b82-a74e-e809b62fb577");
        final LinkedHashMap<String, ItemInstance.Roll> rolls = new LinkedHashMap<>();
        rolls.put("attack_damage", new ItemInstance.Roll(13.25D, 0.75D));
        rolls.put("attack_speed", new ItemInstance.Roll(0.06D, 0.4D));
        final ItemInstance expected = new ItemInstance(itemId, ItemInstance.CURRENT_SCHEMA,
                "glatziendorfi_jegtoro", 3, 34, rolls, List.of("fagy_runa"),
                new ItemInstance.AscensionState("awakened", 1),
                new ItemInstance.Origin("profession", "fegyverkovacs", crafterId,
                        "Taliann", "armorer", "Glatziendorf", true, 1_725_000_000_000L),
                Set.of(ItemState.CURSED), 7L,
                List.of(new ItemHistoryEvent(ItemHistoryEvent.Type.CRAFTED,
                        1_725_000_000_000L, "fegyverkovacs")),
                new ItemInstance.MutationState(4, 3, List.of(UUID.fromString(
                        "00000000-0000-0000-0000-000000000099"))));
        final ItemInstance decoded = ItemInstanceCodec.decode(ItemInstanceCodec.encode(expected));
        check(decoded.equals(expected), "codec preserves the complete canonical instance");
        final ItemInstance schemaOne = new ItemInstance(UUID.randomUUID(), 1,
                "legacy_readable", 1, 1, Map.of(), List.of(), ItemInstance.AscensionState.base(),
                new ItemInstance.Origin("combat:legacy", "regression", null, "", 2L),
                Set.of(), 0L, List.of());
        check(ItemInstanceCodec.decode(ItemInstanceCodec.encode(schemaOne)).equals(schemaOne),
                "schema-one canonical payloads remain readable during the schema-two rollout");
        expectFailure(() -> ItemInstanceCodec.decode("not-valid-base64%%%"),
                "malformed payloads fail closed");
        expectFailure(() -> new ItemInstance(itemId, ItemInstance.CURRENT_SCHEMA,
                        "glatziendorfi_jegtoro", 3, 34, rolls,
                        List.of("fagy_runa", "fagy_runa"),
                        ItemInstance.AscensionState.base(), expected.origin(), Set.of(), 0L, List.of()),
                "duplicate rune state fails closed");
    }

    private static void historyRemainsBoundedAndIncrementsRevision() {
        ItemInstance instance = new ItemInstance(UUID.randomUUID(), ItemInstance.CURRENT_SCHEMA,
                "history_test", 1, 1, Map.of(), List.of(), ItemInstance.AscensionState.base(),
                new ItemInstance.Origin("admin", "regression", null, "", 1L), Set.of(), 0L, List.of());
        for (int index = 0; index < ItemInstance.MAX_HISTORY + 5; index++) {
            instance = instance.appendHistory(new ItemHistoryEvent(ItemHistoryEvent.Type.REROLLED,
                    10L + index, "step_" + index));
        }
        check(instance.history().size() == ItemInstance.MAX_HISTORY,
                "history evicts oldest entries beyond its hard bound");
        check(instance.mutationRevision() == ItemInstance.MAX_HISTORY + 5L,
                "every mutation advances the durable revision");
        check(instance.history().get(0).detail().equals("step_5"),
                "bounded history retains the newest evidence");
    }

    private static void rarityVocabularyHasNoLegacyConflicts() {
        final ArrayList<String> ids = new ArrayList<>();
        for (final ItemRarity rarity : ItemRarity.values()) ids.add(rarity.id());
        check(!ids.contains("ocska"), "Ócska is an item state, not a rarity");
        check(!ids.contains("ereklye"), "Ereklye is an item family, not a rarity");
        check(ItemRarity.parse("mitikus") == ItemRarity.MYTHIC,
                "canonical Hungarian rarity ids parse deterministically");
    }

    private static void abilityPowerScalingIsBounded() {
        check(close(ItemStatScaling.abilityPowerMultiplier(12.0D, 1.0D, 50.0D), 1.12D),
                "ability power points scale cast magnitude deterministically");
        check(close(ItemStatScaling.abilityPowerMultiplier(80.0D, 1.0D, 50.0D), 1.5D),
                "ability power respects its configured bonus cap");
        check(close(ItemStatScaling.abilityPowerMultiplier(-4.0D, 1.0D, 50.0D), 1.0D),
                "negative ability power cannot invert or weaken a cast");
    }

    private static void runeMutationKeepsIdentityAndAdvancesRevision() {
        final UUID id = UUID.randomUUID();
        final ItemInstance before = new ItemInstance(id, ItemInstance.CURRENT_SCHEMA,
                "rune_test", 1, 1, Map.of(), List.of(), ItemInstance.AscensionState.base(),
                new ItemInstance.Origin("craft", "regression", null, "", 1L),
                Set.of(), 4L, List.of());
        final ItemInstance after = before.addRune("runa_fagy", 2L);
        check(after.itemId().equals(id), "rune mutation preserves the item UUID");
        check(after.mutationRevision() == 5L, "rune mutation advances exactly one revision");
        check(after.runes().equals(List.of("runa_fagy")), "rune mutation stores canonical socket state");
        check(after.history().get(0).type() == ItemHistoryEvent.Type.RUNE_CHANGED,
                "rune mutation records bounded lifecycle history");
    }

    private static void signatureRegistryRejectsConsumerlessEffects() {
        final SignatureEffectRegistry.Definition effect =
                SignatureEffectRegistry.require("glatziendorfi_jegtoro");
        check(!effect.consumer().isBlank(), "registered signature effects name their gameplay consumer");
        check(effect.triggers().contains(SignatureEffectRegistry.Trigger.ON_HIT),
                "signature lifecycle declares its activation trigger");
        expectFailure(() -> SignatureEffectRegistry.require("nincs_ilyen_effect"),
                "unknown signature effects fail template validation");
    }

    private static void itemSetTiersActivateCumulatively() {
        final ItemSetDefinition set = new ItemSetDefinition("regression_set", "Regression",
                Map.of(2, Map.of("ability_power", 2.0D),
                        4, Map.of("ability_power", 3.0D)));
        check(set.activeStats(1).isEmpty(), "an incomplete set grants no bonus");
        check(set.activeStats(2).equals(Map.of("ability_power", 2.0D)),
                "the first set tier activates at its authored piece count");
        check(set.activeStats(4).equals(Map.of("ability_power", 5.0D)),
                "higher set tiers retain earlier bonuses");
    }

    private static void buildAwareWeightsStayModestAndTradeable() {
        final ItemTemplate relevant = lootTemplate("relevant_sword", ItemRarity.RARE,
                ItemTemplate.Family.WEAPON, ItemTemplate.Slot.MAIN_HAND,
                Set.of("warrior"), Set.of("berserker"),
                Map.of("attack_damage", 2.0D));
        final ItemTemplate tradeableAlternative = lootTemplate("tradeable_armor", ItemRarity.EPIC,
                ItemTemplate.Family.ARMOR, ItemTemplate.Slot.CHEST,
                Set.of("wizard"), Set.of("elementalist"),
                Map.of("max_health", 4.0D));
        final BuildAwareLootService selector = new BuildAwareLootService();
        final BuildAwareLootService.Context context = new BuildAwareLootService.Context(
                20, "warrior", "berserker", Set.of("stat:attack_damage"),
                ItemTemplate.Slot.MAIN_HAND, Set.of("combat:wilderness"));
        final var relevantWeight = selector.weight(relevant, context,
                LootDiversityState.empty(), BuildAwareLootService.Tuning.defaults());
        final var alternativeWeight = selector.weight(tradeableAlternative, context,
                LootDiversityState.empty(), BuildAwareLootService.Tuning.defaults());
        check(relevantWeight.personalizationMultiplier() > alternativeWeight.personalizationMultiplier(),
                "matching level, class, spec, build, slot and source receive only a modest preference");
        check(relevantWeight.personalizationMultiplier() <= 1.5D,
                "build-aware personalization cannot exceed the authored hard cap");
        check(alternativeWeight.weight() > 0.0D,
                "another class's tradeable item always remains in the weighted pool");
    }

    private static void diversityHistoryIsBoundedIdempotentAndNeverGuaranteesMythic() {
        final ItemTemplate repeated = lootTemplate("repeated_weapon", ItemRarity.LEGENDARY,
                ItemTemplate.Family.WEAPON, ItemTemplate.Slot.MAIN_HAND,
                Set.of(), Set.of(), Map.of("attack_damage", 1.0D));
        final ItemTemplate unseenMythic = lootTemplate("unseen_mythic", ItemRarity.MYTHIC,
                ItemTemplate.Family.ARMOR, ItemTemplate.Slot.HEAD,
                Set.of(), Set.of(), Map.of("armor", 1.0D));
        LootDiversityState state = LootDiversityState.empty();
        final UUID firstId = UUID.fromString("00000000-0000-0000-0000-000000000301");
        state = state.record(LootDiversityState.Drop.of(firstId, repeated));
        state = state.record(LootDiversityState.Drop.of(firstId, repeated));
        check(state.recentDrops().size() == 1, "the same item receipt cannot skew pity twice");
        for (int index = 0; index < LootDiversityState.MAX_DROPS + 8; index++) {
            state = state.record(LootDiversityState.Drop.of(new UUID(0L, 1_000L + index), repeated));
        }
        check(state.recentDrops().size() == LootDiversityState.MAX_DROPS,
                "loot diversity history evicts old evidence at its durable bound");
        final BuildAwareLootService selector = new BuildAwareLootService();
        final BuildAwareLootService.Context context = new BuildAwareLootService.Context(
                20, "", "", Set.of(), ItemTemplate.Slot.NONE, Set.of("combat:wilderness"));
        final var repeatedWeight = selector.weight(repeated, context, state,
                BuildAwareLootService.Tuning.defaults());
        final var mythicWeight = selector.weight(unseenMythic, context, state,
                LootDiversityState.empty(), BuildAwareLootService.Tuning.defaults());
        check(repeatedWeight.diversityMultiplier() < mythicWeight.diversityMultiplier(),
                "recent repetition is softened while unseen categories receive a mild boost");
        check(repeatedWeight.weight() > 0.0D && mythicWeight.weight() > 0.0D,
                "soft pity never excludes a candidate or guarantees the mythic rarity");
        check(selector.select(List.of(repeated, unseenMythic), context, state,
                BuildAwareLootService.Tuning.defaults(), 0.0D).orElseThrow()
                .template().equals(repeated),
                "even after a full repeated history a non-mythic result remains selectable");
    }

    private static void controlledRerollSupportsLockAmplifierStabilityAndIdempotence() {
        final ItemTemplate template = mutationTemplate();
        final ItemInstance before = mutationInstance(template, 0.90D, 0.50D);
        final ItemMutationService service = new ItemMutationService();
        check(template.matchesAscensionState("base", 0)
                        && template.matchesAscensionState("awakened", 1)
                        && !template.matchesAscensionState("awakened", 0),
                "ascension stage id and index must agree with the authored path");

        final UUID fullId = UUID.fromString("00000000-0000-0000-0000-000000000401");
        final double[] fullRolls = {0.10D, 0.20D};
        final int[] cursor = {0};
        final var full = service.reroll(template, before,
                new ItemMutationService.RerollRequest(fullId, "", 0.0D, false, 10L),
                () -> fullRolls[cursor[0]++]);
        check(full.applied(), "full reroll builds an immutable candidate");
        check(full.candidate().itemId().equals(before.itemId()), "full reroll preserves identity");
        check(!close(full.candidate().rolls().get("attack_damage").quality(),
                        before.rolls().get("attack_damage").quality())
                        && !close(full.candidate().rolls().get("attack_speed").quality(),
                        before.rolls().get("attack_speed").quality())
                        && full.candidate().rolls().values().stream()
                        .map(ItemInstance.Roll::quality).collect(java.util.stream.Collectors.toSet())
                        .equals(Set.of(0.10D, 0.20D)),
                "full reroll changes every authored roll independent of map iteration order");
        check(full.candidate().mutation().rerollCount() == 1
                        && full.candidate().mutation().rerollCostStep() == 1,
                "ordinary reroll advances both durable counters");

        final UUID lockId = UUID.fromString("00000000-0000-0000-0000-000000000402");
        final var locked = service.reroll(template, before,
                new ItemMutationService.RerollRequest(lockId, "attack_damage", 0.65D, true, 11L),
                () -> 0.0D);
        check(locked.candidate().rolls().get("attack_damage")
                        .equals(before.rolls().get("attack_damage")),
                "Stat Lock preserves the selected roll exactly");
        check(close(locked.candidate().rolls().get("attack_speed").quality(), 0.65D),
                "Quality Amplifier raises the next reroll floor");
        check(locked.candidate().mutation().rerollCount() == 1
                        && locked.candidate().mutation().rerollCostStep() == 0,
                "Stability Seal advances history without increasing the next cost step");
        check(service.reroll(template, locked.candidate(),
                new ItemMutationService.RerollRequest(lockId, "attack_damage", 0.65D, true, 12L),
                () -> 1.0D).status() == ItemMutationService.Status.ALREADY_APPLIED,
                "duplicate reroll receipt is idempotent");
        check(service.reroll(template, before,
                new ItemMutationService.RerollRequest(UUID.randomUUID(), "armor", 0.0D, false, 13L),
                () -> 0.5D).status() == ItemMutationService.Status.INVALID_LOCK,
                "fixed or undeclared stats cannot be locked");
        check(ItemMutationService.boundedGeometricCost(2L, 2.0D, 4, 20L) == 20L,
                "reroll cost progression saturates at the configured bound");
        check(ItemMutationService.boundedGeometricCost(Long.MAX_VALUE / 2, 4.0D, 99,
                Long.MAX_VALUE) == Long.MAX_VALUE, "reroll cost overflow saturates safely");
    }

    private static void ascensionPreservesIdentityProvenanceRunesAndRelativeQuality() {
        final ItemTemplate template = mutationTemplate();
        final ItemInstance before = mutationInstance(template, 0.90D, 0.50D).addRune("runa_fagy", 5L);
        final UUID operation = UUID.fromString("00000000-0000-0000-0000-000000000410");
        final ItemMutationService service = new ItemMutationService();
        final var result = service.ascend(template, before,
                new ItemMutationService.AscensionRequest(operation, 20L));
        final ItemInstance after = result.candidate();
        check(result.applied() && after.itemId().equals(before.itemId()),
                "ascension is deterministic and keeps the exact UUID");
        check(after.origin().equals(before.origin()) && after.runes().equals(before.runes()),
                "ascension preserves provenance and rune state");
        check(after.ascension().equals(new ItemInstance.AscensionState("awakened", 1))
                        && after.itemLevel() == 40,
                "ascension publishes the authored stage and item level");
        check(close(after.rolls().get("attack_damage").value(), 38.0D)
                        && close(after.rolls().get("attack_damage").quality(), 0.90D),
                "19/10..20 quality maps to 38/20..40 without rerolling");
        check(close(after.rolls().get("attack_speed").value(), -0.5D),
                "negative stat ranges preserve the same normalized quality");
        check(template.runeSocketCountAt(after.ascension().stageId()) == 2
                        && template.signatureTierAt(after.ascension().stageId()) == 2,
                "authored ascension can add a socket and upgrade the signature tier");
        check(service.ascend(template, after,
                new ItemMutationService.AscensionRequest(operation, 21L)).status()
                        == ItemMutationService.Status.ALREADY_APPLIED,
                "duplicate ascension request cannot charge or upgrade twice");
    }

    private static void relativeQualityHandlesIntegerDecimalNegativeAndClamp() {
        check(ItemMutationService.preserveRelativeQuality(
                new ItemTemplate.StatRange(10.0D, 20.0D), 19.0D,
                new ItemTemplate.StatRange(20.0D, 40.0D), true) == 38.0D,
                "integer ascension preserves ninety-percent quality with deterministic rounding");
        check(close(ItemMutationService.preserveRelativeQuality(
                new ItemTemplate.StatRange(0.1D, 0.3D), 0.25D,
                new ItemTemplate.StatRange(0.2D, 0.6D), false), 0.5D),
                "decimal ranges preserve relative quality");
        check(close(ItemMutationService.preserveRelativeQuality(
                new ItemTemplate.StatRange(-20.0D, -10.0D), -11.0D,
                new ItemTemplate.StatRange(-40.0D, -20.0D), false), -22.0D),
                "negative scales preserve relative quality");
        check(ItemMutationService.preserveRelativeQuality(
                new ItemTemplate.StatRange(10.0D, 20.0D), 99.0D,
                new ItemTemplate.StatRange(20.0D, 40.0D), false) == 40.0D,
                "out-of-range legacy input clamps at the new authored maximum");
    }

    private static void salvageIsConservativeBoundedAndLegacySafe() {
        final ItemTemplate template = mutationTemplate();
        final ItemInstance item = mutationInstance(template, 0.5D, 0.5D);
        final ItemSalvageService service = new ItemSalvageService();
        final ItemSalvageService.Tuning tuning = new ItemSalvageService.Tuning(1, 1, 1, 1, 8);
        final var preview = service.preview(template, item, tuning, 64, false);
        check(preview.allowed() && preview.estimatedOutputValue() <= preview.estimatedInputValue(),
                "salvage output is lossy against conservative craft input value");
        check(preview.outputs().getOrDefault("signature_dust", 0) == 1,
                "signature salvage supports the reroll/rune ecosystem with a bounded output");
        final ItemInstance legacy = new ItemInstance(item.itemId(), item.schemaVersion(),
                item.templateId(), item.templateVersion(), item.itemLevel(), item.rolls(), item.runes(),
                item.ascension(), item.origin(), Set.of(ItemState.LEGACY), item.mutationRevision(),
                item.history(), item.mutation());
        check(service.preview(template, legacy, tuning, 64, false).status()
                        == ItemSalvageService.Status.LEGACY_DISABLED,
                "unknown-origin legacy gear is never salvaged automatically");
        expectStateFailure(() -> service.preview(template, item,
                        new ItemSalvageService.Tuning(50, 50, 50, 50, 500), 1, false),
                "misconfigured craft-to-salvage profit fails closed");
    }

    private static void recoveryNeverGuessesAcrossAmbiguousSnapshots() {
        final List<String> before = List.of("item-before", "material-before");
        final List<String> after = List.of("item-after", "material-after");
        check(ItemMutationRecoveryPolicy.decide(before, before, after)
                        == ItemMutationRecoveryPolicy.Decision.ABORT_BEFORE,
                "crash before inventory publication aborts without payment loss");
        check(ItemMutationRecoveryPolicy.decide(after, before, after)
                        == ItemMutationRecoveryPolicy.Decision.COMMIT_AFTER,
                "crash after inventory publication commits without duplicate payment");
        check(ItemMutationRecoveryPolicy.decide(List.of("partial", "state"), before, after)
                        == ItemMutationRecoveryPolicy.Decision.MANUAL_REVIEW,
                "partial or externally changed inventory fails closed instead of guessing");
        check(ItemMutationRecoveryPolicy.decide(before, before, before)
                        == ItemMutationRecoveryPolicy.Decision.ABORT_BEFORE,
                "an exact before==after witness is a benign no-op and closes safely");
        check(ItemMutationRecoveryPolicy.decide(List.of("missing", "witness"), before, before)
                        == ItemMutationRecoveryPolicy.Decision.MANUAL_REVIEW,
                "a missing no-op witness still fails closed instead of guessing");
    }

    private static void mutationCrashRecoverySettlesExactlyOnce() {
        final List<String> before = List.of("same-item@revision-4", "three-reroll-dust");
        final List<String> after = List.of("same-item@revision-5", "one-reroll-dust");

        final RecoveryHarness killedBeforePublish = new RecoveryHarness(before, after);
        check(killedBeforePublish.restart(before)
                        == ItemMutationRecoveryPolicy.Decision.ABORT_BEFORE,
                "prepared journal plus exact-before restart aborts");
        check(killedBeforePublish.closed && killedBeforePublish.commits == 0,
                "exact-before closes the witness without a free mutation");
        killedBeforePublish.restart(before);
        check(killedBeforePublish.settlements == 1,
                "exact-before retry cannot settle the journal twice");

        final RecoveryHarness killedAfterPublish = new RecoveryHarness(before, after);
        check(killedAfterPublish.restart(after)
                        == ItemMutationRecoveryPolicy.Decision.COMMIT_AFTER,
                "published inventory plus open journal commits after restart");
        check(killedAfterPublish.closed && killedAfterPublish.commits == 1,
                "exact-after accounts one payment and one upgrade");
        killedAfterPublish.restart(after);
        check(killedAfterPublish.commits == 1 && killedAfterPublish.settlements == 1,
                "duplicate recovery cannot duplicate payment or upgrade");

        final RecoveryHarness mixed = new RecoveryHarness(before, after);
        check(mixed.restart(List.of("same-item@revision-5", "three-reroll-dust"))
                        == ItemMutationRecoveryPolicy.Decision.MANUAL_REVIEW,
                "mixed item/payment state is quarantined");
        check(!mixed.closed && mixed.commits == 0 && mixed.settlements == 0,
                "mixed state stays pending and grants nothing");
    }

    private static final class RecoveryHarness {
        private final List<String> before;
        private final List<String> after;
        private boolean closed;
        private int commits;
        private int settlements;

        private RecoveryHarness(final List<String> before, final List<String> after) {
            this.before = before;
            this.after = after;
        }

        private ItemMutationRecoveryPolicy.Decision restart(final List<String> current) {
            if (closed) return ItemMutationRecoveryPolicy.Decision.COMMIT_AFTER;
            final ItemMutationRecoveryPolicy.Decision decision =
                    ItemMutationRecoveryPolicy.decide(current, before, after);
            if (decision != ItemMutationRecoveryPolicy.Decision.MANUAL_REVIEW) {
                closed = true;
                settlements++;
                if (decision == ItemMutationRecoveryPolicy.Decision.COMMIT_AFTER) commits++;
            }
            return decision;
        }
    }

    private static ItemTemplate mutationTemplate() {
        final Map<String, ItemTemplate.StatRange> baseRolls = Map.of(
                "attack_damage", new ItemTemplate.StatRange(10.0D, 20.0D),
                "attack_speed", new ItemTemplate.StatRange(-0.4D, -0.1D));
        final ItemTemplate.AscensionStage awakened = new ItemTemplate.AscensionStage(
                "awakened", 40, 30, Map.of("ability_power", 3.0D), Map.of(
                "attack_damage", new ItemTemplate.StatRange(20.0D, 40.0D),
                "attack_speed", new ItemTemplate.StatRange(-0.8D, -0.2D)),
                2, 2, "", "", List.of("Awakened"));
        return new ItemTemplate("mutation_template", ItemTemplate.CURRENT_SCHEMA, 1,
                "Mutation", List.of(), ItemRarity.LEGENDARY, 30, ItemTemplate.Family.WEAPON,
                ItemTemplate.Slot.MAIN_HAND, "NETHERITE_AXE", "", "", 20,
                Set.of(), Set.of(), Set.of(), 0.0D, 0.0D,
                Map.of("ability_power", 2.0D), baseRolls, 1,
                "glatziendorfi_jegtoro", "", ItemTemplate.BindPolicy.NONE,
                ItemTemplate.TradePolicy.TRADEABLE, ItemTemplate.SalvagePolicy.SIGNATURE_MATERIALS,
                Set.of("profession:armorer"), Set.of(), Set.of("mining:test"),
                Set.of("armorer:test"), Map.of(), List.of("awakened"),
                Map.of("awakened", awakened));
    }

    private static ItemInstance mutationInstance(final ItemTemplate template,
                                                 final double damageQuality,
                                                 final double speedQuality) {
        final LinkedHashMap<String, ItemInstance.Roll> rolls = new LinkedHashMap<>();
        rolls.put("attack_damage", new ItemInstance.Roll(
                template.rolledStats().get("attack_damage").valueAt(damageQuality), damageQuality));
        rolls.put("attack_speed", new ItemInstance.Roll(
                template.rolledStats().get("attack_speed").valueAt(speedQuality), speedQuality));
        return new ItemInstance(UUID.fromString("00000000-0000-0000-0000-000000000400"),
                ItemInstance.CURRENT_SCHEMA, template.templateId(), template.templateVersion(),
                template.itemLevel(), rolls, List.of(), ItemInstance.AscensionState.base(),
                new ItemInstance.Origin("profession:craft", "armorer", UUID.fromString(
                        "00000000-0000-0000-0000-000000000499"), "Crafter", "armorer",
                        "Glatziendorf", true, 1L), Set.of(), 0L, List.of(),
                ItemInstance.MutationState.fresh());
    }

    private static boolean close(final double left, final double right) {
        return Math.abs(left - right) < 0.000_000_1D;
    }

    private static ItemTemplate template(final Map<String, Double> fixed,
                                         final Map<String, ItemTemplate.StatRange> rolled,
                                         final int sockets) {
        return new ItemTemplate("regression_template", ItemTemplate.CURRENT_SCHEMA, 1,
                "Regression", List.of(), ItemRarity.RARE, 20, ItemTemplate.Family.WEAPON,
                ItemTemplate.Slot.MAIN_HAND, "IRON_SWORD", "", "", 0,
                Set.of(), Set.of(), Set.of(), 0.0D, 0.0D, fixed, rolled, sockets,
                "", "", ItemTemplate.BindPolicy.NONE, ItemTemplate.TradePolicy.TRADEABLE,
                ItemTemplate.SalvagePolicy.MATERIALS, Set.of("regression"), Set.of(),
                Set.of("mining:test_ore"), Set.of("smithing:test"), Map.of(), List.of());
    }

    private static ItemTemplate lootTemplate(final String id, final ItemRarity rarity,
                                             final ItemTemplate.Family family,
                                             final ItemTemplate.Slot slot,
                                             final Set<String> classes,
                                             final Set<String> specializations,
                                             final Map<String, Double> fixed) {
        return new ItemTemplate(id, ItemTemplate.CURRENT_SCHEMA, 1,
                id, List.of(), rarity, 20, family, slot, "IRON_SWORD", "", "", 0,
                classes, specializations, Set.of(), 0.0D, 0.0D, fixed, Map.of(), 0,
                "", "", ItemTemplate.BindPolicy.NONE, ItemTemplate.TradePolicy.TRADEABLE,
                ItemTemplate.SalvagePolicy.MATERIALS, Set.of("combat:wilderness"), Set.of(),
                Set.of("hunting:test"), Set.of("salvage:test"), Map.of(), List.of());
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

    private static void expectStateFailure(final Runnable action, final String message) {
        assertions++;
        try {
            action.run();
        } catch (final IllegalStateException expected) {
            return;
        }
        throw new AssertionError(message);
    }

    private static void check(final boolean condition, final String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
