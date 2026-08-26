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

    private TrashCatalogRegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        validatesCanonicalCatalog();
        rejectsIdentityAssetCollisions();
        preservesMinimalPhysicalStateAndNoRollBoundary();
        preservesHardcodedHiddenAuthority();
        System.out.println("Trash catalog regression suite passed.");
    }

    private static void validatesCanonicalCatalog() {
        final TrashCatalog.Parsed parsed = parseFresh();
        check(parsed.definitions().size() == 330, "base identity denominator drifted");
        check("Ócska".equals(parsed.rarityLabel()), "player rarity label drifted");

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
        check(counts.equals(new EnumMap<>(Map.of(
                TrashKind.MUNDANE, 190,
                TrashKind.STORY, 75,
                TrashKind.ANOMALY, 42,
                TrashKind.TRASH_RELIC, 23))), "kind denominator drifted: " + counts);
        check(models.size() == 330 && textures.size() == 330, "asset uniqueness denominator drifted");
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
