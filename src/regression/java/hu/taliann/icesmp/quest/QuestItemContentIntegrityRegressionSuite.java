package hu.taliann.icesmp.quest;

import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.data.FactionType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Behavioral and exhaustive source regressions for quest/item integrity finding closure. */
public final class QuestItemContentIntegrityRegressionSuite {

    private static final Path QUESTS = Path.of(
            "src/main/resources/content/progression/quests.yml");
    private static final Path ENEMIES = Path.of(
            "src/main/resources/content/pve/enemies.yml");
    private static final Path MATERIALS = Path.of(
            "src/main/resources/content/professions/materials.yml");
    private static int assertions;

    private QuestItemContentIntegrityRegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        questCurrencyResolutionIsGuestSafeAndFactionExact();
        fullQuestCatalogHasRewardPreviewParity();
        specializationCapstonesAreDistinctAndCompatible();
        professionGatesAreTypedAndReachable();
        authoredDailyIsTheOnlyLiveAuthority();
        worldBossesCarryTypedSpecificRewards();
        playerFacingItemTruthContracts();
        moneyPouchRemainsOpaqueUntilOpening();
        System.out.println("Quest/item content-integrity regression suite passed. assertions="
                + assertions);
    }

    /** Real calls through the one resolver used by both preview and physical payout. */
    private static void questCurrencyResolutionIsGuestSafeAndFactionExact() {
        check(QuestCurrencyResolver.resolve("OWN", Optional.of(FactionType.RED))
                        == CurrencyType.RED,
                "RED OWN reward resolves to Parázsló Parals");
        check(QuestCurrencyResolver.resolve("OWN", Optional.of(FactionType.BLUE))
                        == CurrencyType.BLUE,
                "BLUE OWN reward resolves to Hópihér-veret");
        check(QuestCurrencyResolver.resolve("OWN", Optional.of(FactionType.DARK))
                        == CurrencyType.DARK,
                "DARK OWN reward resolves to Csontveret");
        check(QuestCurrencyResolver.resolve("OWN", Optional.of(FactionType.NEUTRAL))
                        == CurrencyType.NEUTRAL,
                "NEUTRAL OWN reward resolves to Creutzér");
        check(QuestCurrencyResolver.resolve("OWN", Optional.empty()) == CurrencyType.NEUTRAL,
                "factionless Menedék guest OWN reward deterministically resolves to Creutzér");
        check(QuestCurrencyResolver.resolve("BLUE", Optional.empty()) == CurrencyType.BLUE,
                "an explicit reward currency does not change for a guest");
        check("Creutzér".equals(CurrencyType.NEUTRAL.getDisplayName()),
                "the canonical neutral currency is Creutzér");
    }

    private static void fullQuestCatalogHasRewardPreviewParity() throws Exception {
        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(QUESTS.toFile());
        final ConfigurationSection quests = required(yaml, "quests");
        check(quests.getKeys(false).size() == 195, "the exhaustive quest denominator remains 195");
        int ownWithoutFaction = 0;
        int withRewards = 0;
        final Set<String> supportedRewardKeys = Set.of(
                "currency", "class-xp", "items", "crate-key", "unlock-spell", "cleanse-sins");
        for (final String id : quests.getKeys(false)) {
            final ConfigurationSection quest = required(quests, id);
            final ConfigurationSection rewards = quest.getConfigurationSection("rewards");
            if (rewards == null || rewards.getKeys(false).isEmpty()) continue;
            withRewards++;
            check(supportedRewardKeys.containsAll(rewards.getKeys(false)),
                    id + " uses only payout/preview-supported reward categories");
            if ("OWN".equalsIgnoreCase(rewards.getString("currency.type", ""))
                    && quest.getString("requires-faction", "").isBlank()) {
                ownWithoutFaction++;
            }
        }
        check(withRewards == 160,
                "all 160 reward-bearing quests are covered while the 35 rewardless capstones omit the category");
        check(ownWithoutFaction == 96,
                "the exact reviewed set of 96 guest-reachable OWN rewards is covered");

        final String manager = source("src/main/java/hu/taliann/icesmp/managers/QuestManager.java");
        final String delivery = source("src/main/java/hu/taliann/icesmp/managers/QuestPhysicalRewardDeliveryService.java");
        final String gui = source("src/main/java/hu/taliann/icesmp/gui/QuestLogGUI.java");
        check(manager.contains("QuestCurrencyResolver.resolve(")
                        && delivery.contains("QuestCurrencyResolver.resolve("),
                "preview and payout call the same canonical currency resolver");
        check(gui.contains("questManager.describeRewards(viewer, questId)"),
                "every quest card asks the runtime manager for its reward preview");
        check(gui.contains("if (!rewards.isEmpty())"),
                "the reward category is omitted rather than rendered empty");
    }

    private static void specializationCapstonesAreDistinctAndCompatible() {
        final ConfigurationSection quests = required(
                YamlConfiguration.loadConfiguration(QUESTS.toFile()), "quests");
        int capstones = 0;
        for (final String id : quests.getKeys(false)) {
            final ConfigurationSection quest = required(quests, id);
            if (!"SPECIALIZATION".equalsIgnoreCase(quest.getString("category", ""))) continue;
            capstones++;
            final ConfigurationSection objectives = required(quest, "objectives");
            check(objectives.getKeys(false).size() >= 2,
                    id + " has at least two objective dimensions");
            check("CAST_SPELLS".equalsIgnoreCase(
                            required(objectives, "1").getString("type", "")),
                    id + " preserves its old objective.0 spell-progress slot as objective 1");
            check(objectives.getKeys(false).stream().map(key -> required(objectives, key))
                            .anyMatch(objective -> !"CAST_SPELLS".equalsIgnoreCase(
                                    objective.getString("type", ""))),
                    id + " is no longer a generic cast-only capstone");
        }
        check(capstones == 35, "all 35 specialization capstones were exhaustively checked");
    }

    private static void professionGatesAreTypedAndReachable() {
        final ConfigurationSection quests = required(
                YamlConfiguration.loadConfiguration(QUESTS.toFile()), "quests");
        final Map<String, String> expected = Map.ofEntries(
                Map.entry("miner_ore_haul", "miner"),
                Map.entry("smith_smelt_iron", "armorer"),
                Map.entry("farmer_harvest", "cook"),
                Map.entry("kovacs_acel_rendeles", "armorer"),
                Map.entry("red_heti_kohok", "armorer"),
                Map.entry("kovacs_fegyvermustra", "armorer"),
                Map.entry("parazs_gyujtes", "alchemist"),
                Map.entry("uti_kenyer", "cook"),
                Map.entry("hamu_zuzmara_2", "armorer"));
        expected.forEach((id, profession) -> {
            final ConfigurationSection quest = required(quests, id);
            check(profession.equals(quest.getString("requires-profession")),
                    id + " is gated by its intended canonical profession");
            check(quest.getInt("requires-profession-level", 0) > 0,
                    id + " has an explicit positive profession-level gate");
        });
        final String manager = uncheckedSource(
                "src/main/java/hu/taliann/icesmp/managers/QuestManager.java");
        check(manager.contains("professions.hasProfession(player, type)")
                        && manager.contains("professions.getLevel(player, type)"),
                "acceptance checks actual profession membership and level");
        check(manager.contains("private boolean isStillEligible")
                        && manager.contains("requiredProfession"),
                "profession switching freezes incompatible active progress");
    }

    private static void authoredDailyIsTheOnlyLiveAuthority() throws Exception {
        final ConfigurationSection quests = required(
                YamlConfiguration.loadConfiguration(QUESTS.toFile()), "quests");
        final long authored = quests.getKeys(false).stream()
                .map(id -> required(quests, id))
                .filter(quest -> "DAILY".equalsIgnoreCase(quest.getString("category", "")))
                .count();
        check(authored == 17L, "all 17 authored daily quests remain canonical content");
        final String dailyManager = source(
                "src/main/java/hu/taliann/icesmp/managers/DailyQuestManager.java");
        final String command = source("src/main/java/hu/taliann/icesmp/commands/DailyCommand.java");
        check(!dailyManager.contains("advanceDaily(") && !dailyManager.contains("advanceWeekly(")
                        && !dailyManager.contains("payOutTokens("),
                "the retired procedural daily compatibility view cannot progress or pay rewards");
        check(command.contains("QuestLogGUI.open") && command.contains("QuestLogHolder.Tab.BOARD"),
                "/daily routes to the authored quest journal");
        check(!Files.exists(Path.of(
                        "src/main/java/hu/taliann/icesmp/listeners/DailyQuestListener.java")),
                "no procedural event listener remains registered or present");
    }

    private static void worldBossesCarryTypedSpecificRewards() {
        final ConfigurationSection bosses = required(
                YamlConfiguration.loadConfiguration(ENEMIES.toFile()), "mob-templates");
        final ConfigurationSection materials = required(
                YamlConfiguration.loadConfiguration(MATERIALS.toFile()), "profession-materials");
        final List<String> ids = List.of(
                "ring_warden", "magma_behemoth", "frost_king", "bone_king", "deep_horror",
                "venom_broodmother", "storm_herald", "plague_titan", "golem_sentinel",
                "piglin_warlord");
        final Set<String> rewards = new HashSet<>();
        for (final String id : ids) {
            final ConfigurationSection boss = required(bosses, id);
            check("WORLD_BOSS".equals(boss.getString("rank")), id + " remains a world boss");
            final String reward = boss.getString("boss-specific-reward", "");
            check(!reward.isBlank() && materials.isConfigurationSection(reward),
                    id + " references an existing canonical material reward");
            check(rewards.add(reward), id + " has a distinct reward identity");
        }
        check(rewards.size() == 10, "no world boss lacks a specific reward identity");
        final String manager = uncheckedSource(
                "src/main/java/hu/taliann/icesmp/managers/WorldBossManager.java");
        check(manager.contains("resolveBossSpecificReward(archetypeName)")
                        && manager.contains("delivery.reserveEligibility")
                        && manager.contains("delivery.activate"),
                "the typed reward uses the durable personal reserve/activate delivery protocol");
    }

    private static void playerFacingItemTruthContracts() throws Exception {
        final String loot = source("src/main/resources/content/pve/loot.yml");
        final String equipment = source("src/main/resources/content/equipment/equipment.yml");
        final String relics = source("src/main/resources/content/equipment/relics.yml");
        final String recipes = source("src/main/resources/content/professions/recipes.yml");
        final String professionProjection = source(
                "src/main/java/hu/taliann/icesmp/listeners/ProfessionRecipeBookListener.java");
        check(!loot.contains("A Néma Királynő Suttogása")
                        && loot.contains("A Néma Udvar Suttogása"),
                "global undead loot no longer claims a personal named-boss provenance");
        check(!loot.contains("A sötét mágia védelmezi viselőjét")
                        && !loot.contains("Nem ég el. Nem törik el."),
                "corrupted named loot contains no unsupported concrete mechanics");
        check(equipment.contains("display-name: Napfogyatkozás Íja")
                        && !equipment.contains("display-name: Napfogyatkozás Fókusza"),
                "Napfogyatkozás clearly presents its unchanged bow identity");
        check(relics.contains("Használat") && relics.contains("Shift + bal katt")
                        && relics.contains("Shift + jobb katt") && relics.contains("PvP-ben elesel"),
                "Mételytépő exposes its decision-critical controls and transfer consequence");
        check(!recipes.contains(" craft\n") && !recipes.contains("Craftoláshoz"),
                "profession recipe and blueprint register contains no reviewed Hunglish leakage");
        check(professionProjection.indexOf("meta.displayName(LEGACY.deserialize(recipe.displayName())")
                        < professionProjection.indexOf("if (recipe.lore() != null"),
                "authored crafted display name projects independently of optional lore");
    }

    private static void moneyPouchRemainsOpaqueUntilOpening() throws Exception {
        final String factory = source(
                "src/main/java/hu/taliann/icesmp/items/MoneyPouchItemFactory.java");
        final String listener = source(
                "src/main/java/hu/taliann/icesmp/listeners/MoneyPouchListener.java");
        check(factory.contains("pdc.set(valueKey") && factory.contains("pdc.set(currencyKey"),
                "currency and amount are rolled and stored when the pouch is created");
        final int loreStart = factory.indexOf("meta.lore(List.of(");
        final int loreEnd = factory.indexOf("));", loreStart);
        final String lore = factory.substring(loreStart, loreEnd);
        check(!lore.contains("currency.getDisplayName") && !lore.contains("rounded")
                        && !lore.contains("value"),
                "unopened pouch lore leaks no currency, amount or range");
        check(listener.contains("pouchFactory.getValue(hand)")
                        && listener.contains("pouchFactory.getCurrency(hand)")
                        && listener.contains("createCurrencyItem(currency, batch)"),
                "opening reveals stored values and pays physical currency tokens");
    }

    private static ConfigurationSection required(final ConfigurationSection parent,
                                                  final String path) {
        final ConfigurationSection section = parent.getConfigurationSection(path);
        if (section == null) throw new AssertionError("missing configuration section: " + path);
        return section;
    }

    private static String source(final String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    private static String uncheckedSource(final String path) {
        try {
            return source(path);
        } catch (final Exception failure) {
            throw new AssertionError("cannot read source: " + path, failure);
        }
    }

    private static void check(final boolean condition, final String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
