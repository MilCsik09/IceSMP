package hu.taliann.icesmp.quest;

import hu.taliann.icesmp.playerprofile.application.PlayerProfileAuthority;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileQuestStore;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileService;
import hu.taliann.icesmp.playerprofile.persistence.YamlPlayerProfileRepository;
import hu.taliann.icesmp.playerprofile.transaction.YamlPlayerProfileTransactionManager;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Quest Framework v2 regressziók: forrás-policy fordítás és jogosultság-mátrix (a
 * bizonyított accept/talk/journal/bind bypass-ok zárása), teljes gráf-validátor,
 * választó-token életciklus, kategória/láthatóság szótárak, marker-paletta, a
 * PlayerProfile-kiterjesztések (forrás-audit, felfedezés, követés) durable
 * viselkedése, a csomagolt quest-katalógus migrációs szerződése és a bypass-mentességet
 * őrző forrás-szerződések.
 */
public final class QuestFrameworkV2RegressionSuite {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000002101");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000002102");
    private static int assertions;

    private QuestFrameworkV2RegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        sourcePolicyParsing();
        sourceAuthorityMatrix();
        graphValidator();
        choiceTokenLifecycle();
        categoryAndVisibilityDictionaries();
        markerPalette();
        profileStoreExtensions();
        packagedQuestMigrationContract();
        bypassSourceContracts();
        System.out.println("Quest framework v2 regression suite passed. assertions=" + assertions);
    }

    // ---------- forrás-policy fordítás ----------

    private static void sourcePolicyParsing() throws Exception {
        // Legacy adapterek: giver-npc → NPC start + ugyanaz az NPC a turn-in;
        // auto-start-territory → LOCATION; egyik sem → QUEST_BOARD + AUTO.
        QuestSourcePolicy policy = QuestSourcePolicy.parse(quest("""
                giver-npc: "Mester Harcos"
                objective:
                  type: KILL_MOBS
                  count: 3
                """));
        check(policy.startType() == QuestSourcePolicy.StartType.NPC
                        && policy.startReference().equals("mester harcos")
                        && policy.turnInType() == QuestSourcePolicy.TurnInType.NPC
                        && policy.turnInReference().equals("mester harcos"),
                "legacy giver-npc maps to NPC start and same-NPC turn-in");
        check(!policy.autoTurnIn(), "NPC turn-in is not auto-complete");

        policy = QuestSourcePolicy.parse(quest("""
                auto-start-territory: "eszaki_havasok"
                objective:
                  type: VISIT_TERRITORY
                  count: 1
                """));
        check(policy.startType() == QuestSourcePolicy.StartType.LOCATION
                        && policy.startReference().equals("eszaki_havasok")
                        && policy.autoTurnIn(),
                "legacy auto-start-territory maps to LOCATION start with AUTO turn-in");

        policy = QuestSourcePolicy.parse(quest("""
                objective:
                  type: CATCH_FISH
                  count: 5
                """));
        check(policy.startType() == QuestSourcePolicy.StartType.QUEST_BOARD
                        && policy.autoTurnIn(),
                "sourceless quest defaults to QUEST_BOARD + AUTO (board contract)");

        policy = QuestSourcePolicy.parse(quest("""
                start:
                  type: CHAIN
                  auto-accept: true
                objective:
                  type: KILL_MOBS
                  count: 1
                """));
        check(policy.startType() == QuestSourcePolicy.StartType.CHAIN
                        && policy.chainAutoAccept(),
                "explicit CHAIN auto-accept parsed");

        policy = QuestSourcePolicy.parse(quest("""
                start:
                  type: NPC
                  npc: "Hírnök"
                turn-in:
                  type: LOCATION
                  territory: "menedek"
                objective:
                  type: TALK_TO_NPC
                  npc: "Hírnök"
                  count: 1
                """));
        check(policy.turnInType() == QuestSourcePolicy.TurnInType.LOCATION
                        && policy.turnInReference().equals("menedek"),
                "explicit turn-in section overrides the NPC default");

        expectPolicyReject("""
                start:
                  type: TELEPORT
                objective:
                  type: KILL_MOBS
                  count: 1
                """, "unknown start.type rejected");
        expectPolicyReject("""
                start:
                  type: NPC
                objective:
                  type: KILL_MOBS
                  count: 1
                """, "NPC start without npc field rejected");
        expectPolicyReject("""
                turn-in:
                  type: LOCATION
                objective:
                  type: KILL_MOBS
                  count: 1
                """, "LOCATION turn-in without territory rejected");
    }

    // ---------- forrás-jogosultság mátrix (a bizonyított bypass-ok zárása) ----------

    private static void sourceAuthorityMatrix() throws Exception {
        final QuestSourcePolicy npcQuest = QuestSourcePolicy.parse(quest("""
                start:
                  type: NPC
                  npc: "Mester Harcos"
                objective:
                  type: KILL_MOBS
                  count: 1
                """));
        check(npcQuest.startAuthorized(QuestSourceContext.npc("Mester Harcos")),
                "matching NPC interaction is authorized");
        check(npcQuest.startAuthorized(QuestSourceContext.npc("MESTER HARCOS")),
                "NPC matching is case-insensitive");
        check(!npcQuest.startAuthorized(QuestSourceContext.npc("Masik Npc")),
                "different NPC cannot hand out the quest (bind is not authority)");
        check(!npcQuest.startAuthorized(QuestSourceContext.board()),
                "journal/board click cannot accept an NPC-sourced quest (remote-accept bypass closed)");
        check(!npcQuest.startAuthorized(QuestSourceContext.chain("elozo")),
                "chain unlock alone cannot accept an NPC-sourced quest");
        check(!npcQuest.startAuthorized(QuestSourceContext.auto()),
                "auto context cannot accept an NPC-sourced quest");
        check(npcQuest.startAuthorized(QuestSourceContext.admin()),
                "admin context is always authorized (explicit admin authority)");
        check(!npcQuest.turnInAuthorized(QuestSourceContext.npc("Masik Npc")),
                "turn-in requires the authorized NPC");
        check(!npcQuest.turnInAuthorized(QuestSourceContext.auto()),
                "objective completion alone does not complete an NPC turn-in quest");

        final QuestSourcePolicy boardQuest = QuestSourcePolicy.parse(quest("""
                objective:
                  type: CATCH_FISH
                  count: 3
                """));
        check(boardQuest.startAuthorized(QuestSourceContext.board()),
                "board quest is acceptable from the journal board tab");
        check(!boardQuest.startAuthorized(QuestSourceContext.npc("Valaki")),
                "NPC interaction does not hand out board quests");
        check(boardQuest.turnInAuthorized(QuestSourceContext.auto()),
                "board quest auto-completes on objective completion (behaviour preserved)");

        final QuestSourcePolicy chainQuest = QuestSourcePolicy.parse(quest("""
                start:
                  type: CHAIN
                objective:
                  type: KILL_MOBS
                  count: 1
                """));
        check(chainQuest.startAuthorized(QuestSourceContext.chain("elozo_quest")),
                "chain context accepts a CHAIN-sourced quest (dialogue choice token)");
        check(!chainQuest.startAuthorized(QuestSourceContext.board()),
                "board cannot accept a CHAIN-sourced quest");
    }

    // ---------- gráf-validátor ----------

    private static void graphValidator() throws Exception {
        List<String> errors = QuestGraphValidator.validate(root("""
                quests:
                  jo_quest:
                    objective:
                      type: KILL_MOBS
                      count: 3
                """));
        check(errors.isEmpty(), "minimal valid quest passes");

        errors = QuestGraphValidator.validate(root("""
                quests:
                  ures_quest:
                    display-name: "Üres"
                """));
        check(errors.stream().anyMatch(error -> error.contains("no objectives")),
                "empty quest (no objectives) rejected");

        errors = QuestGraphValidator.validate(root("""
                quests:
                  rossz_tipus:
                    objective:
                      type: FLY_TO_MOON
                      count: 1
                """));
        check(errors.stream().anyMatch(error -> error.contains("invalid type")),
                "unknown objective type rejected");

        errors = QuestGraphValidator.validate(root("""
                quests:
                  hivatkozo:
                    next: nem_letezik
                    objective:
                      type: KILL_MOBS
                      count: 1
                """));
        check(errors.stream().anyMatch(error -> error.contains("unknown next")),
                "unknown next target rejected");

        errors = QuestGraphValidator.validate(root("""
                quests:
                  onmaga:
                    next: onmaga
                    objective:
                      type: KILL_MOBS
                      count: 1
                """));
        check(errors.stream().anyMatch(error -> error.contains("self-cycle")),
                "direct self-cycle rejected");

        errors = QuestGraphValidator.validate(root("""
                quests:
                  lanc_a:
                    next: lanc_b
                    objective:
                      type: KILL_MOBS
                      count: 1
                  lanc_b:
                    next: lanc_c
                    objective:
                      type: KILL_MOBS
                      count: 1
                  lanc_c:
                    next: lanc_a
                    objective:
                      type: KILL_MOBS
                      count: 1
                """));
        check(errors.stream().anyMatch(error -> error.contains("chain cycle detected")
                        && error.contains("lanc_a") && error.contains("->")),
                "chain cycle rejected with a diagnosable path");

        errors = QuestGraphValidator.validate(root("""
                quests:
                  fuggo:
                    requires-quest: sosem_letezett
                    objective:
                      type: KILL_MOBS
                      count: 1
                """));
        check(errors.stream().anyMatch(error -> error.contains("unknown requires-quest")),
                "unknown requires-quest rejected");

        errors = QuestGraphValidator.validate(root("""
                quests:
                  rossz_kategoria:
                    category: LEGENDARY
                    objective:
                      type: KILL_MOBS
                      count: 1
                """));
        check(errors.stream().anyMatch(error -> error.contains("invalid category")),
                "unknown category rejected");

        errors = QuestGraphValidator.validate(root("""
                quests:
                  rossz_lathatosag:
                    visibility: INVISIBLE
                    objective:
                      type: KILL_MOBS
                      count: 1
                """));
        check(errors.stream().anyMatch(error -> error.contains("invalid visibility")),
                "unknown visibility rejected");

        errors = QuestGraphValidator.validate(root("""
                quests:
                  rossz_forras:
                    start:
                      type: NPC
                    objective:
                      type: KILL_MOBS
                      count: 1
                """));
        check(errors.stream().anyMatch(error -> error.contains("start.npc")),
                "missing source field rejected at graph level");

        // A `next` lista alakban is kanonikus (elágazó branching).
        final ConfigurationSection branching = root("""
                quests:
                  elagazo:
                    next:
                      - ag_egy
                      - ag_ketto
                    objective:
                      type: KILL_MOBS
                      count: 1
                  ag_egy:
                    objective:
                      type: KILL_MOBS
                      count: 1
                  ag_ketto:
                    objective:
                      type: KILL_MOBS
                      count: 1
                """);
        check(QuestGraphValidator.validate(branching).isEmpty(),
                "branching next list validates");
        check(QuestGraphValidator.nextIds(branching.getConfigurationSection("elagazo"))
                        .equals(List.of("ag_egy", "ag_ketto")),
                "next list parsed in order");
    }

    // ---------- választó-tokenek ----------

    private static void choiceTokenLifecycle() {
        final QuestChoiceRegistry registry = new QuestChoiceRegistry();
        final QuestSourceContext source = QuestSourceContext.npc("Hírnök");
        final String token = registry.issue(PLAYER, "valasztott_quest", source, 1_000_000L);
        check(token != null && !token.isBlank(), "token issued");
        check(registry.consume(OTHER, token, 1_000_500L).isEmpty(),
                "token is owner-bound (another player cannot redeem it)");
        final var consumed = registry.consume(PLAYER, token, 1_000_500L);
        check(consumed.isPresent()
                        && consumed.orElseThrow().questId().equals("valasztott_quest")
                        && consumed.orElseThrow().source().equals(source),
                "redeeming returns the ORIGINAL interaction's source context");
        check(registry.consume(PLAYER, token, 1_000_600L).isEmpty(),
                "token is single-use");

        final String expiring = registry.issue(PLAYER, "kesobbi", source, 1_000_000L);
        check(registry.consume(PLAYER, expiring, 1_000_000L + 61_000L).isEmpty(),
                "expired token cannot be redeemed");

        final String invalidated = registry.issue(PLAYER, "kilepes", source, 2_000_000L);
        registry.invalidate(PLAYER);
        check(registry.consume(PLAYER, invalidated, 2_000_100L).isEmpty(),
                "player-state cleanup invalidates pending tokens");

        final QuestChoiceRegistry bounded = new QuestChoiceRegistry();
        for (int index = 0; index < 1024; index++) {
            bounded.issue(PLAYER, "live-" + index, source, 3_000_000L);
        }
        boolean fullRejected = false;
        try {
            bounded.issue(PLAYER, "overflow", source, 3_000_000L);
        } catch (final IllegalStateException expected) {
            fullRejected = true;
        }
        check(fullRejected, "a full live-token registry rejects overflow atomically");
        check(!bounded.issue(PLAYER, "after-expiry", source, 3_061_000L).isBlank(),
                "issue purges expired tokens before applying the capacity gate");
    }

    // ---------- szótárak és paletta ----------

    private static void categoryAndVisibilityDictionaries() {
        check(QuestCategory.fromConfig("daily", QuestCategory.SIDE) == QuestCategory.DAILY,
                "category parse is case-insensitive");
        check(QuestCategory.fromConfig(null, QuestCategory.SIDE) == QuestCategory.SIDE,
                "missing category falls back");
        check(QuestCategory.fromConfig("nonsense", QuestCategory.SIDE) == null,
                "unknown category returns null for the validator");
        check(QuestCategory.MAIN.offerPriority() < QuestCategory.SIDE.offerPriority()
                        && QuestCategory.SIDE.offerPriority() < QuestCategory.DAILY.offerPriority()
                        && QuestCategory.TUTORIAL.offerPriority() < QuestCategory.SIDE.offerPriority(),
                "MMO offer priority: main/tutorial > side > daily");

        check(QuestVisibility.fromConfig("hidden", QuestVisibility.ALWAYS) == QuestVisibility.HIDDEN,
                "visibility parse works");
        check(QuestVisibility.fromConfig("", QuestVisibility.ALWAYS) == QuestVisibility.ALWAYS,
                "missing visibility falls back to ALWAYS");
        check(QuestVisibility.fromConfig("mystery", QuestVisibility.ALWAYS) == null,
                "unknown visibility returns null for the validator");
    }

    private static void markerPalette() {
        check(QuestMarkerPalette.availableStateFor(QuestCategory.DAILY)
                        == QuestMarkerPalette.MarkerState.DAILY
                        && QuestMarkerPalette.availableStateFor(QuestCategory.WEEKLY)
                        == QuestMarkerPalette.MarkerState.DAILY,
                "daily/weekly share the rotation marker state");
        check(QuestMarkerPalette.availableStateFor(QuestCategory.SIDE)
                        == QuestMarkerPalette.MarkerState.AVAILABLE
                        && QuestMarkerPalette.availableStateFor(null)
                        == QuestMarkerPalette.MarkerState.AVAILABLE,
                "default category maps to the plain available marker");
        check(QuestMarkerPalette.availableStateFor(QuestCategory.SECRET)
                        == QuestMarkerPalette.MarkerState.SECRET,
                "secret category maps to the secret marker");
        check(!QuestMarkerPalette.color(QuestMarkerPalette.MarkerState.READY_TO_TURN_IN)
                        .equals(QuestMarkerPalette.color(QuestMarkerPalette.MarkerState.AVAILABLE)),
                "ready and available markers are visually distinct");
        check(QuestMarkerPalette.symbol(QuestMarkerPalette.MarkerState.READY_TO_TURN_IN)
                        .equals("?")
                        && QuestMarkerPalette.symbol(QuestMarkerPalette.MarkerState.AVAILABLE)
                        .equals("!"),
                "canonical !/? marker symbols");
    }

    // ---------- PlayerProfile-kiterjesztések ----------

    private static void profileStoreExtensions() throws Exception {
        final Path root = Files.createTempDirectory("quest-framework-v2-");
        final YamlPlayerProfileRepository repository = new YamlPlayerProfileRepository(root);
        final YamlPlayerProfileTransactionManager transactions =
                new YamlPlayerProfileTransactionManager(repository);
        final PlayerProfileService service = new PlayerProfileService(repository, transactions);
        final PlayerProfileAuthority authority = PlayerProfileAuthority.install(
                service, repository, transactions);
        boolean firstUninstalled = false;
        try {
            repository.loadSnapshot(PLAYER).toCompletableFuture().join();
            final PlayerProfileQuestStore store = new PlayerProfileQuestStore();

            check(store.accept(PLAYER, "forras_quest", "npc:mester harcos")
                    .toCompletableFuture().join(), "accept with source audit committed");
            check("npc:mester harcos".equals(store.startSource(PLAYER, "forras_quest")),
                    "start source audit persisted with the acceptance");

            check(store.discover(PLAYER, "titkos_quest", "chain:elozo")
                    .toCompletableFuture().join(), "first discovery committed");
            check(!store.discover(PLAYER, "titkos_quest", "npc:masik")
                    .toCompletableFuture().join(), "re-discovery is an idempotent no-op");
            check(store.isDiscovered(PLAYER, "titkos_quest"), "discovery readable");
            check(!store.isDiscovered(PLAYER, "sosem_latott"), "unknown quest not discovered");

            check(store.setTracked(PLAYER, "forras_quest").toCompletableFuture().join(),
                    "tracking committed");
            check("forras_quest".equals(store.tracked(PLAYER)), "tracked quest readable");
            check(!store.setTracked(PLAYER, "forras_quest").toCompletableFuture().join(),
                    "unchanged tracking is a no-op");
            check(store.setTracked(PLAYER, null).toCompletableFuture().join(),
                    "untrack committed");
            check(store.tracked(PLAYER) == null, "tracking cleared");

            // A kiterjesztések restartot túlélnek (durable extensions, nem runtime map).
            check(service.shutdown(Duration.ofSeconds(5)).toCompletableFuture().join().drained(),
                    "repository drained");
            authority.uninstall();
            firstUninstalled = true;
            final YamlPlayerProfileRepository reopened = new YamlPlayerProfileRepository(root);
            final YamlPlayerProfileTransactionManager transactions2 =
                    new YamlPlayerProfileTransactionManager(reopened);
            final PlayerProfileService service2 = new PlayerProfileService(reopened, transactions2);
            final PlayerProfileAuthority authority2 = PlayerProfileAuthority.install(
                    service2, reopened, transactions2);
            try {
                reopened.loadSnapshot(PLAYER).toCompletableFuture().join();
                final PlayerProfileQuestStore store2 = new PlayerProfileQuestStore();
                check(store2.isDiscovered(PLAYER, "titkos_quest"),
                        "discovery survives a restart");
                check("npc:mester harcos".equals(store2.startSource(PLAYER, "forras_quest")),
                        "source audit survives a restart");
                check(service2.shutdown(Duration.ofSeconds(5)).toCompletableFuture().join()
                        .drained(), "reopened repository drained");
            } finally {
                authority2.uninstall();
            }
        } finally {
            if (!firstUninstalled) {
                authority.uninstall();
            }
            try (var paths = Files.walk(root)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (final Exception ignored) {
                    }
                });
            }
        }
    }

    // ---------- a csomagolt quest-katalógus migrációs szerződése ----------

    private static void packagedQuestMigrationContract() throws Exception {
        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                new java.io.File("src/main/resources/config/quests.yml"));
        final ConfigurationSection quests = yaml.getConfigurationSection("quests");
        check(quests != null && quests.getKeys(false).size() == 195,
                "packaged catalog carries the 160 world quests plus 35 capstone trials");

        final List<String> errors = QuestGraphValidator.validate(quests);
        check(errors.isEmpty(), "the full packaged quest graph validates: " + errors);

        int npcStarts = 0;
        int chainStarts = 0;
        int specializationTrials = 0;
        for (final String questId : quests.getKeys(false)) {
            final ConfigurationSection quest = quests.getConfigurationSection(questId);
            final QuestSourcePolicy policy = QuestSourcePolicy.parse(quest);
            check(!(quest.contains("giver-npc") && quest.isConfigurationSection("start")),
                    "no quest carries both legacy giver-npc and a start section: " + questId);
            if (policy.startType() == QuestSourcePolicy.StartType.NPC) {
                npcStarts++;
            }
            if (policy.startType() == QuestSourcePolicy.StartType.CHAIN) {
                chainStarts++;
            }
            // Lánc-célpont csak explicit CHAIN-forrással élhet: a next mostantól feloldás,
            // az auto-accept a CHAIN kötés explicit deklarációja.
            for (final String next : QuestGraphValidator.nextIds(quest)) {
                final QuestSourcePolicy nextPolicy = QuestSourcePolicy.parse(
                        quests.getConfigurationSection(next));
                check(nextPolicy.startType() == QuestSourcePolicy.StartType.CHAIN
                                && nextPolicy.chainAutoAccept()
                                || nextPolicy.startType() == QuestSourcePolicy.StartType.NPC,
                        "next target is explicit CHAIN auto-accept (or NPC giver): " + next);
            }
            final ConfigurationSection choices = quest.getConfigurationSection("dialogue.choices");
            if (choices != null) {
                for (final String key : choices.getKeys(false)) {
                    final String target = choices.getConfigurationSection(key) == null ? ""
                            : choices.getConfigurationSection(key).getString("quest", "");
                    if (!target.isBlank()) {
                        final QuestSourcePolicy targetPolicy = QuestSourcePolicy.parse(
                                quests.getConfigurationSection(target.toLowerCase(Locale.ROOT)));
                        check(targetPolicy.startType() == QuestSourcePolicy.StartType.CHAIN,
                                "dialogue choice target is CHAIN-sourced: " + target);
                    }
                }
            }
            if (quest.getString("rotation-group", "").length() > 0) {
                check("DAILY".equals(quest.getString("category")),
                        "rotation quest is DAILY category: " + questId);
            }
            if (quest.getBoolean("riddle", false)) {
                check("SECRET".equals(quest.getString("category")),
                        "riddle quest is SECRET category: " + questId);
            }
            if ("SPECIALIZATION".equals(quest.getString("category"))) {
                specializationTrials++;
                check(quest.getInt("requires-level") >= 50,
                        "capstone trial is level 50 gated: " + questId);
                check(!quest.getString("requires-specialization", "").isBlank(),
                        "capstone trial is bound to its active specialization: " + questId);
                check("CAST_SPELLS".equals(quest.getString("objective.type"))
                                && !quest.getStringList("objective.spells").isEmpty(),
                        "capstone trial practices specialization abilities: " + questId);
            }
        }
        check(npcStarts == 23, "all 23 giver quests migrated to NPC start, found " + npcStarts);
        check(chainStarts == 24, "all 24 chain targets migrated to CHAIN start, found " + chainStarts);
        check(specializationTrials == 35,
                "all 35 capstone trials are packaged, found " + specializationTrials);
        check(QuestSourcePolicy.parse(quests.getConfigurationSection("onboarding_herald"))
                        .startType() == QuestSourcePolicy.StartType.AUTO,
                "onboarding chain opener stays AUTO-sourced");

        final String raw = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/resources/config/quests.yml"));
        check(!raw.contains("giver-npc:"),
                "legacy giver-npc keys fully migrated to start sections");
    }

    // ---------- bypass-mentességet őrző forrás-szerződések ----------

    private static void bypassSourceContracts() throws Exception {
        final String manager = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/hu/taliann/icesmp/managers/QuestManager.java"));
        check(!manager.contains("public boolean accept(final Player"),
                "no public source-less accept remains on QuestManager");
        check(manager.contains("startAuthorized(context)")
                        && manager.contains("turnInAuthorized(context)"),
                "accept and turn-in both pass the source-authority gate");
        check(manager.contains("/quest choose ") && !manager.contains("\"/quest accept \" +"),
                "clickable choices carry single-use tokens, never raw accept commands");
        check(manager.contains("reloadDefinitions") && manager.contains("keeping previous"),
                "invalid definition reload keeps the previous registry snapshot");
        check(manager.contains("handleSpellCast(final Player player, final String spellId)")
                        && manager.contains("forEachActive(player, \"CAST_SPELLS\"")
                        && manager.contains("quest-requires-specialization"),
                "capstone trial progress and specialization gate share the central QuestManager");

        final String logListener = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/hu/taliann/icesmp/listeners/QuestLogListener.java"));
        check(logListener.contains("QuestSourceContext.board()"),
                "journal accepts only through the QUEST_BOARD source context");

        final String command = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/hu/taliann/icesmp/commands/QuestCommand.java"));
        check(command.contains("private void handleAccept")
                        && command.split("private void handleAccept")[1]
                        .substring(0, 400).contains("ADMIN_PERMISSION"),
                "/quest accept is admin-gated");
        check(command.split("private void handleTalk")[1]
                        .substring(0, 400).contains("ADMIN_PERMISSION"),
                "/quest talk is admin-gated (NPC spoof closed)");
        check(command.contains("List.of(\"log\", \"list\", \"info\", \"track\", \"abandon\", \"choose\")")
                        && command.contains("if (\"choose\".equals(subcommand)) return List.of();"),
                "/quest choose is discoverable without leaking single-use tokens");

        final String bridge = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/hu/taliann/icesmp/integration/FancyNpcsQuestBridge.java"));
        check(bridge.contains("handleAuthorizedNpcInteract")
                        && !bridge.contains("acceptFromNpc")
                        && !bridge.contains("acceptBoundQuest"),
                "the NPC bridge is an adapter: all decisions flow through the central authority");
    }

    // ---------- fixtures ----------

    private static ConfigurationSection quest(final String body) throws Exception {
        final YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("quest:\n" + body.strip().indent(2));
        return yaml.getConfigurationSection("quest");
    }

    private static ConfigurationSection root(final String body) throws Exception {
        final YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(body.stripIndent());
        return yaml.getConfigurationSection("quests");
    }

    private static void expectPolicyReject(final String body, final String label) throws Exception {
        boolean rejected = false;
        try {
            QuestSourcePolicy.parse(quest(body));
        } catch (final IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected, label);
    }

    private static void check(final boolean condition, final String label) {
        assertions++;
        if (!condition) {
            throw new AssertionError("Quest framework v2 regression failed: " + label);
        }
    }
}
