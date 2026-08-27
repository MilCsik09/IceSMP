package hu.taliann.icesmp.trash;

import hu.taliann.icesmp.security.HiddenDevAuthority;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Catalog, secrecy, identity and immutable DEV-boundary regression gates. */
public final class TrashCatalogRegressionSuite {

    private static final Path CATALOG = Path.of("src/main/resources/content/trash/catalog.yml");
    private static final Path FACTORY = Path.of(
            "src/main/java/hu/taliann/icesmp/trash/TrashItemFactory.java");
    private static final Path COMMAND = Path.of(
            "src/main/java/hu/taliann/icesmp/commands/IceSMPCommand.java");
    private static final Path CORE = Path.of("src/main/java/hu/taliann/icesmp/core/IceSMPCore.java");
    private static final Path AMBIENT = Path.of(
            "src/main/java/hu/taliann/icesmp/trash/TrashAmbientManager.java");
    private static final Path MOB = Path.of(
            "src/main/java/hu/taliann/icesmp/trash/TrashMobDropListener.java");
    private static final Path FISHING = Path.of(
            "src/main/java/hu/taliann/icesmp/trash/TrashFishingListener.java");
    private static final Path BUYER = Path.of(
            "src/main/java/hu/taliann/icesmp/managers/BuyerService.java");
    private static final Path LOOT_SERVICE = Path.of(
            "src/main/java/hu/taliann/icesmp/trash/TrashLootService.java");
    private static final Path RECYCLE_POOL = Path.of(
            "src/main/java/hu/taliann/icesmp/trash/TrashRecyclePool.java");
    private static final Path VENDOR = Path.of(
            "src/main/java/hu/taliann/icesmp/trash/TrashVendorService.java");
    private static final Path DAILY_BUDGET = Path.of(
            "src/main/java/hu/taliann/icesmp/utils/DailyBudget.java");

    private TrashCatalogRegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        validatesCanonicalCatalog();
        rejectsIdentityAssetCollisions();
        preservesMinimalPhysicalStateAndNoRollBoundary();
        preservesLootEcologyRuntimeBoundary();
        preservesHardcodedHiddenAuthority();
        System.out.println("Trash catalog regression suite passed.");
    }

    private static void validatesCanonicalCatalog() {
        final TrashCatalog.Parsed parsed = parseFresh();
        check(parsed.definitions().size() == 330, "base identity denominator drifted");
        check(parsed.lifecyclePhases().size() == 27, "lifecycle phase denominator drifted");
        check("Ócska".equals(parsed.rarityLabel()), "player rarity label drifted");
        final TrashLootTuning tuning = parsed.lootTuning();
        check(tuning.chance(TrashLootSource.FISHING) == 0.065D, "fishing chance drifted");
        check(tuning.chance(TrashLootSource.MOB) == 0.11D, "mob chance drifted");
        check(tuning.chance(TrashLootSource.AMBIENT) == 0.25D, "ambient chance drifted");
        check(tuning.categoryWeight(TrashKind.MUNDANE) == 75.0D, "mundane weight drifted");
        check(tuning.categoryWeight(TrashKind.STORY) == 23.65D, "story weight drifted");
        check(tuning.categoryWeight(TrashKind.ANOMALY) == 1.25D, "anomaly weight drifted");
        check(tuning.categoryWeight(TrashKind.TRASH_RELIC) == 0.10D, "relic weight drifted");
        check(tuning.displacedChance() == 0.08D, "displaced chance drifted");
        check(tuning.recycleSubstitutionChance() == 0.50D, "recycle substitution drifted");
        check(Math.abs(TrashSourceBias.parse("WET↑").weight(TrashLootSource.AMBIENT,
                Set.of(TrashContext.WET), false, tuning) - 1.3D) < 0.000_001D,
                "matching context multiplier drifted");
        check(Math.abs(TrashSourceBias.parse("FISH↑").weight(TrashLootSource.FISHING,
                Set.of(), false, tuning) - 2.5D) < 0.000_001D,
                "matching source multiplier drifted");
        check(TrashSourceBias.parse("FISH+WET↑").weight(TrashLootSource.FISHING,
                Set.of(TrashContext.WET), true, tuning) == 2.5D,
                "displaced selection must ignore context but keep source affinity");

        final EnumMap<TrashKind, Integer> counts = new EnumMap<>(TrashKind.class);
        final Set<String> models = new HashSet<>();
        final Set<String> textures = new HashSet<>();
        for (final TrashDefinition definition : parsed.definitions().values()) {
            counts.merge(definition.internalKind(), 1, Integer::sum);
            check(models.add(definition.itemModel()), "shared base item-model: " + definition.itemModel());
            check(textures.add(definition.texture()), "shared base texture: " + definition.texture());
            check("ocska".equals(definition.playerRarity()), "non-Ócska player rarity: " + definition.id());
            check(definition.itemModel().equals("icesmp:trash/" + definition.id()),
                    "non-canonical model: " + definition.id());
            check(definition.texture().equals("icesmp:item/trash/" + definition.id()),
                    "non-canonical texture: " + definition.id());
            check(definition.internalKind().isInert() == "NONE".equals(definition.behavior()),
                    "behavior classification drift: " + definition.id());
        }
        final Set<String> referencedPhases = new HashSet<>();
        parsed.definitions().values().stream().map(TrashDefinition::successPhase)
                .filter(phase -> !phase.isBlank()).forEach(referencedPhases::add);
        for (final TrashLifecyclePhase phase : parsed.lifecyclePhases().values()) {
            check(models.add(phase.itemModel()), "shared lifecycle item-model: " + phase.itemModel());
            check(textures.add(phase.texture()), "shared lifecycle texture: " + phase.texture());
            check("ocska".equals(phase.playerRarity()), "non-Ócska lifecycle rarity: " + phase.id());
            check(phase.itemModel().equals("icesmp:trash/" + phase.id()),
                    "non-canonical lifecycle model: " + phase.id());
            check(phase.texture().equals("icesmp:item/trash/" + phase.id()),
                    "non-canonical lifecycle texture: " + phase.id());
        }
        check(counts.equals(new EnumMap<>(Map.of(
                TrashKind.MUNDANE, 190,
                TrashKind.STORY, 75,
                TrashKind.ANOMALY, 42,
                TrashKind.TRASH_RELIC, 23))), "kind denominator drifted: " + counts);
        check(models.size() == 357 && textures.size() == 357, "asset uniqueness denominator drifted");
        check(referencedPhases.equals(parsed.lifecyclePhases().keySet()),
                "lifecycle phase references drifted");
        check("par_zokni".equals(parsed.definitions().get("bal_zokni").successPhase())
                        && "par_zokni".equals(parsed.definitions().get("jobb_zokni").successPhase()),
                "paired sock transformation drifted");
        check("osszezart_lancszemek".equals(parsed.definitions().get("bal_lancszem").successPhase())
                        && "osszezart_lancszemek".equals(
                                parsed.definitions().get("jobb_lancszem").successPhase()),
                "paired chain transformation drifted");
        check(parsed.definitions().containsKey("rozsdas_szog"), "first mundane identity missing");
        check(parsed.definitions().containsKey("portalkorom"), "last anomaly identity missing");
        check(parsed.definitions().containsKey("repedt_virrasztouveg"), "last relic identity missing");
    }

    private static void rejectsIdentityAssetCollisions() {
        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(CATALOG.toFile());
        yaml.set("items.gorbe_szog.item-model", "icesmp:trash/rozsdas_szog");
        expectRejected(yaml, "model collision/canonical-path drift must fail closed");

        final YamlConfiguration countDrift = YamlConfiguration.loadConfiguration(CATALOG.toFile());
        countDrift.set("items.repedt_virrasztouveg", null);
        expectRejected(countDrift, "329-item catalog must fail closed");

        final YamlConfiguration phaseCollision = YamlConfiguration.loadConfiguration(CATALOG.toFile());
        phaseCollision.set("lifecycle-phases.kiegett_biztositek.item-model",
                "icesmp:trash/rozsdas_szog");
        expectRejected(phaseCollision, "base/lifecycle asset collision must fail closed");
    }

    private static void preservesMinimalPhysicalStateAndNoRollBoundary() throws Exception {
        final String source = Files.readString(FACTORY);
        require(source, "new NamespacedKey(plugin, \"trash_id\")", "canonical physical identity PDC");
        require(source, "new NamespacedKey(plugin, \"trash_phase\")", "opaque lifecycle phase PDC");
        require(source, "ItemDataFactory.applyItemModel", "modern ITEM_MODEL projection");
        require(source, "ItemDataFactory.applyRarity", "presentation-only vanilla rarity projection");
        check(!source.contains("ItemRarityService"), "Trash factory must never invoke rolled gear rarity");
        check(!source.contains("trash_relic"), "physical item must not expose the special kind");
        final int metaWrite = source.indexOf("item.setItemMeta(meta)");
        final int modelWrite = source.indexOf("ItemDataFactory.applyItemModel", metaWrite);
        check(metaWrite >= 0 && modelWrite > metaWrite, "data components must be applied after ItemMeta");
    }

    private static void preservesHardcodedHiddenAuthority() throws Exception {
        final UUID expected = UUID.fromString("2d47d7b6-294e-4a14-922c-befacd66ee6d");
        check(HiddenDevAuthority.PRIMARY_DEVELOPER.equals(expected), "hardcoded DEV UUID drifted");
        check(HiddenDevAuthority.isDeveloper(expected), "hardcoded developer rejected");
        check(!HiddenDevAuthority.isDeveloper(UUID.fromString("00000000-0000-0000-0000-000000000001")),
                "unlisted UUID admitted");
        final String source = Files.readString(COMMAND);
        require(source, "HiddenDevAuthority.mayUseHiddenContent(sender)", "central hidden command gate");
        require(source, "trashDevCommand.execute", "hidden Trash dispatch");
        require(source, "trashDevCommand.suggest", "hidden Trash discovery gate");
    }

    private static void preservesLootEcologyRuntimeBoundary() throws Exception {
        final String core = Files.readString(CORE);
        require(core, "new hu.taliann.icesmp.trash.TrashFishingListener", "fishing source wiring");
        require(core, "new hu.taliann.icesmp.trash.TrashMobDropListener", "mob source wiring");
        require(core, "pluginManager.registerEvents(trashAmbientManager", "ambient source wiring");
        require(core, "trashAmbientManager.start()", "loaded ambient recovery startup");
        require(core, "pluginManager.registerEvents(trashVendorService", "vendor recovery wiring");
        require(core, "trashRecyclePool", "recycle store lifecycle wiring");

        final String ambient = Files.readString(AMBIENT);
        require(ambient, "getRegionScheduler().run", "target-region ambient spawn hop");
        require(ambient, "world.isChunkLoaded", "loaded-chunk-only ambient gate");
        require(ambient, "claimManager.getClaimAt", "claim avoidance");
        require(ambient, "ProtectionBridge.queryProtected", "WorldGuard fail-closed avoidance");
        require(ambient, "maxPerNeighborhood()", "Git-authored 3x3 density cap");
        require(ambient, "setUnlimitedLifetime(true)", "authored ambient TTL ownership");
        require(ambient, "trash_ambient_expires_at", "durable ambient expiry authority");
        require(ambient, "EntitiesLoadEvent", "chunk-load ambient recovery");
        require(ambient, "EntitiesUnloadEvent", "chunk-unload density release");
        require(ambient, "territory.type().isProtectedZone()",
                "protected-zone-only territory exclusion");
        require(ambient, "ItemMergeEvent", "ambient merge isolation");
        final int shutdown = ambient.indexOf("public void shutdown()");
        check(shutdown >= 0 && !ambient.substring(shutdown).contains("item.remove()"),
                "plugin shutdown must not erase durable ambient entities");

        final String mob = Files.readString(MOB);
        require(mob, "MobKillUtil.RewardKind.FLAVOR", "boss-safe AFK/spawner/minion mob gate");
        require(mob, "claimOnce(\"trash\")", "duplicate kill-channel claim");
        final String killGate = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/utils/MobKillUtil.java"));
        require(killGate, "kind == RewardKind.FLAVOR", "bounded flavor reward-owner policy");
        require(killGate, "RewardOwner.NONE", "synthetic flavor exclusion");
        final String fishing = Files.readString(FISHING);
        require(fishing, "PlayerFishEvent.State.CAUGHT_FISH", "successful catch gate");
        require(fishing, "GameMode.SURVIVAL", "Survival fishing gate");
        check(!fishing.contains("sendMessage") && !fishing.contains("playSound"),
                "Trash fishing must remain silent");

        final String buyer = Files.readString(BUYER);
        require(buyer, "trashVendor.tryHandle", "Felvásárló Trash route");
        final String lootService = Files.readString(LOOT_SERVICE);
        final int identityRoll = lootService.indexOf("selector.select");
        final int recycleTake = lootService.indexOf("recyclePool.take");
        check(identityRoll >= 0 && recycleTake > identityRoll,
                "recycle substitution must happen after normal category/identity selection");
        require(lootService, "selection.definition().id()", "same-identity recycle lookup");
        final String recyclePool = Files.readString(RECYCLE_POOL);
        require(recyclePool, "history.isValidTracked", "history-bearing exact recycle eligibility");
        require(recyclePool, "history.recordRecycled", "recycle history continuation");
        require(recyclePool, "registerCriticalWrite", "critical recycle write registration");
        require(recyclePool, "YamlStore.saveAtomic", "atomic recycle persistence");
        require(recyclePool, "vendor-transactions", "durable vendor transaction journal");
        require(recyclePool, "MAX_PER_IDENTITY", "bounded exact-instance recycle pool");
        require(recyclePool, "persistOrRestore", "immediate recycle mutation persistence");
        require(recyclePool, "SaleStage.POOL_COMMITTED", "atomic pool commit checkpoint");

        final String vendor = Files.readString(VENDOR);
        require(vendor, "trash_vendor_sale", "inventory-side vendor recovery marker");
        require(vendor, "tryConsumeDurablyOnOwnThread", "durable daily budget reservation");
        require(vendor, "creditOnceDurably", "idempotent durable vendor payout");
        require(vendor, "recyclePool.commitRecycle", "journaled exact recycle commit");
        check(!vendor.contains("recyclePool.offer(hand"),
                "vendor must not use the legacy non-journaled pool route");

        final String dailyBudget = Files.readString(DAILY_BUDGET);
        require(dailyBudget, "DURABLE.reserve", "PlayerProfile-backed budget authority");
        require(dailyBudget, ".toCompletableFuture().join()",
                "vendor budget commit acknowledgement");
    }

    private static TrashCatalog.Parsed parseFresh() {
        final File file = CATALOG.toFile();
        check(file.isFile(), "packaged catalog missing: " + CATALOG);
        return TrashCatalog.parse(YamlConfiguration.loadConfiguration(file));
    }

    private static void expectRejected(final YamlConfiguration yaml, final String message) {
        try {
            TrashCatalog.parse(yaml);
            throw new AssertionError(message);
        } catch (final IllegalStateException expected) {
            // Fail-closed is the contract.
        }
    }

    private static void require(final String source, final String token, final String description) {
        check(source.contains(token), "missing " + description + ": " + token);
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }
}
