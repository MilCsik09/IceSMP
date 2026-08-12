package hu.taliann.icesmp.playerprofile.application;

import hu.taliann.icesmp.playerprofile.domain.ProfileSectionId;
import hu.taliann.icesmp.playerprofile.domain.section.QuestSection;
import hu.taliann.icesmp.playerprofile.persistence.YamlPlayerProfileRepository;
import hu.taliann.icesmp.playerprofile.transaction.YamlPlayerProfileTransactionManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

/** Main quest lifecycle, reward receipt and restart regressions. */
public final class PlayerProfileQuestStoreRegressionSuite {
    private static int assertions;

    private PlayerProfileQuestStoreRegressionSuite() { }

    public static void main(final String[] args) throws Exception {
        final Path root = Files.createTempDirectory("player-profile-quest-store-");
        final YamlPlayerProfileRepository repository = new YamlPlayerProfileRepository(root);
        final YamlPlayerProfileTransactionManager transactions =
                new YamlPlayerProfileTransactionManager(repository);
        final PlayerProfileService service = new PlayerProfileService(repository, transactions);
        final PlayerProfileAuthority authority = PlayerProfileAuthority.install(
                service, repository, transactions);
        try {
            final UUID player = UUID.fromString("00000000-0000-0000-0000-000000001092");
            repository.loadSnapshot(player).toCompletableFuture().join();
            final PlayerProfileQuestStore store = new PlayerProfileQuestStore();

            check(store.accept(player, "story_one").toCompletableFuture().join(),
                    "first acceptance committed");
            check(!store.accept(player, "story_one").toCompletableFuture().join(),
                    "duplicate acceptance rejected");
            check(store.active(player).contains("story_one"), "active list updated");

            check(store.incrementProgress(player, "story_one", 0, 2, 5)
                    .toCompletableFuture().join() == 2, "progress incremented");
            check(store.incrementProgress(player, "story_one", 0, 10, 5)
                    .toCompletableFuture().join() == 5, "progress capped");
            check(store.setProgress(player, "story_one", 1, 3)
                    .toCompletableFuture().join() == 3, "second objective stored");
            check(store.progress(player, "story_one", 0) == 5,
                    "first objective readable");
            check(store.progress(player, "story_one", 1) == 3,
                    "second objective readable");

            final long completedAt = 1_700_000_000_000L;
            final long season = 123_456L;
            final var receipt = store.complete(player, "story_one", completedAt, season)
                    .toCompletableFuture().join();
            check(receipt.committed(), "completion committed");
            check(!receipt.receiptId().isBlank(), "claimable receipt created");
            check(!store.active(player).contains("story_one"), "active state removed");
            check(store.completed(player).contains("story_one"), "completed state added");
            check(store.lastCompletedAt(player, "story_one") == completedAt,
                    "completion cooldown persisted");
            check(store.completedSeason(player, "story_one") == season,
                    "completion season persisted");
            check(store.pendingRewards(player).contains(receipt.receiptId()),
                    "reward remains claimable");

            final long revision = repository.cached(player).orElseThrow().quests().revision();
            final var replay = store.complete(player, "story_one", completedAt + 1, season)
                    .toCompletableFuture().join();
            check(!replay.committed(), "completion replay rejected");
            check(repository.cached(player).orElseThrow().quests().revision() == revision,
                    "completion replay no-op");

            check(store.settleReward(player, receipt.receiptId())
                    .toCompletableFuture().join(), "reward settled");
            check(!store.settleReward(player, receipt.receiptId())
                    .toCompletableFuture().join(), "reward settlement replay rejected");
            check(store.pendingRewards(player).isEmpty(), "claimable receipt removed");

            check(store.accept(player, "repeatable").toCompletableFuture().join(),
                    "repeatable accepted");
            final var pending = store.complete(player, "repeatable", completedAt + 10, season)
                    .toCompletableFuture().join();
            check(pending.committed(), "pending reward completion committed");
            repository.invalidate(player);
            repository.loadSnapshot(player).toCompletableFuture().join();
            check(store.pendingRewards(player).contains(pending.receiptId()),
                    "pending receipt restart durable");
            check("repeatable".equals(store.questFromReceipt(pending.receiptId())),
                    "receipt resolves quest id");
            check(store.completed(player).contains("story_one")
                    && store.completed(player).contains("repeatable"),
                    "completed quests restart durable");

            final Set<String> physical = Set.of("item:0:0", "currency:red:0");
            check(store.prepareRewardComponents(player, pending.receiptId(), physical)
                            .toCompletableFuture().join(),
                    "physical reward components prepared durably");
            check(!store.prepareRewardComponents(player, pending.receiptId(), physical)
                            .toCompletableFuture().join(),
                    "physical reward prepare replay is idempotent");
            Map<String, PlayerProfileQuestStore.RewardComponentState> componentStates =
                    store.rewardComponentStates(player, pending.receiptId());
            check(componentStates.size() == 2
                            && componentStates.values().stream().allMatch(state ->
                            state == PlayerProfileQuestStore.RewardComponentState.PREPARED),
                    "prepared component ledger readable");

            repository.invalidate(player);
            repository.loadSnapshot(player).toCompletableFuture().join();
            componentStates = store.rewardComponentStates(player, pending.receiptId());
            check(componentStates.size() == 2,
                    "prepared component ledger survives restart");
            expectStage(IllegalStateException.class,
                    store.settleReward(player, pending.receiptId()),
                    "parent settlement fails closed while physical components are PREPARED");
            check(store.pendingRewards(player).contains(pending.receiptId()),
                    "premature settlement leaves parent receipt claimable");
            check(store.rewardComponentStates(player, pending.receiptId()).values().stream()
                            .allMatch(state -> state
                                    == PlayerProfileQuestStore.RewardComponentState.PREPARED),
                    "premature settlement preserves prepared component evidence");
            check(QuestRewardDeliveryProtocol.decide(
                            PlayerProfileQuestStore.RewardComponentState.PREPARED, false)
                            == QuestRewardDeliveryProtocol.Decision.DELIVER,
                    "prepared component without witness must deliver");
            check(QuestRewardDeliveryProtocol.decide(
                            PlayerProfileQuestStore.RewardComponentState.PREPARED, true)
                            == QuestRewardDeliveryProtocol.Decision.ACKNOWLEDGE_WITNESS,
                    "crash-after-delivery witness is acknowledged instead of duplicated");

            check(store.markRewardComponentsDelivered(player, pending.receiptId(),
                            Set.of("item:0:0")).toCompletableFuture().join(),
                    "first physical component marked delivered");
            check(store.rewardComponentStates(player, pending.receiptId())
                            .get("item:0:0")
                            == PlayerProfileQuestStore.RewardComponentState.DELIVERED,
                    "delivered state stored");
            expectStage(IllegalStateException.class,
                    store.settleReward(player, pending.receiptId()),
                    "parent settlement still rejects a mixed DELIVERED/PREPARED ledger");
            check(store.markRewardComponentsDelivered(player, pending.receiptId(), physical)
                            .toCompletableFuture().join(),
                    "remaining physical component marked delivered");
            check(!store.markRewardComponentsDelivered(player, pending.receiptId(), physical)
                            .toCompletableFuture().join(),
                    "delivered replay is idempotent");
            check(QuestRewardDeliveryProtocol.decide(
                            PlayerProfileQuestStore.RewardComponentState.DELIVERED, false)
                            == QuestRewardDeliveryProtocol.Decision.SKIP_DELIVERED,
                    "durably delivered component never remints without a witness");

            repository.invalidate(player);
            repository.loadSnapshot(player).toCompletableFuture().join();
            check(store.rewardComponentStates(player, pending.receiptId()).values().stream()
                            .allMatch(state -> state
                                    == PlayerProfileQuestStore.RewardComponentState.DELIVERED),
                    "delivered ledger survives restart before parent receipt settlement");
            check(store.settleReward(player, pending.receiptId()).toCompletableFuture().join(),
                    "parent reward settles after physical components are durable");
            check(store.rewardComponentStates(player, pending.receiptId()).isEmpty(),
                    "component ledger cleaned atomically with parent settlement");
            check(!store.pendingRewards(player).contains(pending.receiptId()),
                    "settled physical reward no longer pending");

            final String questManager = Files.readString(Path.of(
                    "src/main/java/hu/taliann/icesmp/managers/QuestManager.java"));
            final int applyStart = questManager.indexOf(
                    "private CompletionStage<Void> applyRewards");
            final int applyEnd = questManager.indexOf(
                    "private void unlockNextQuests", applyStart);
            check(applyStart >= 0 && applyEnd > applyStart,
                    "QuestManager reward method source contract located");
            final String applyBlock = questManager.substring(applyStart, applyEnd);
            check(applyBlock.contains("QuestPhysicalRewardDeliveryService"),
                    "QuestManager routes physical rewards through durable delivery service");
            check(!applyBlock.contains("payOutTokens")
                            && !applyBlock.contains("dropItemNaturally")
                            && !applyBlock.contains("new org.bukkit.inventory.ItemStack"),
                    "QuestManager no longer performs direct physical reward side effects");

            final String deliverySource = Files.readString(Path.of(
                    "src/main/java/hu/taliann/icesmp/managers/QuestPhysicalRewardDeliveryService.java"));
            check(deliverySource.contains("quest_reward_receipt")
                            && deliverySource.contains("quest_reward_component"),
                    "physical reward items carry durable recovery witness ids");
            check(deliverySource.contains("inventory.getContents()")
                            && !deliverySource.contains("dropItemNaturally"),
                    "recovery witnesses cover all inventory slots and never use lossy world overflow");
            check(deliverySource.contains("CompletableFuture.failedFuture(failure)"),
                    "synchronous physical reward plan failures surface as failed stages");

            final String listenerSource = Files.readString(Path.of(
                    "src/main/java/hu/taliann/icesmp/listeners/QuestProgressListener.java"));
            check(listenerSource.contains("onPendingRewardClick")
                            && listenerSource.contains("onPendingRewardDrag")
                            && listenerSource.contains("onPendingRewardDrop")
                            && listenerSource.contains("onPendingRewardHandSwap")
                            && listenerSource.contains("onPendingRewardInteract")
                            && listenerSource.contains("onPendingRewardConsume")
                            && listenerSource.contains("onPendingRewardPlace")
                            && listenerSource.contains("onPendingRewardDeath"),
                    "pending physical witnesses are immutable until durable ACK");
            check(listenerSource.contains("event.getItemsToKeep().add(drop)")
                            && listenerSource.contains("iterator.remove()"),
                    "death witness is removed from drops before being retained");

            check(store.accept(player, "abandon_me").toCompletableFuture().join(),
                    "abandon quest accepted");
            store.setProgress(player, "abandon_me", 0, 7).toCompletableFuture().join();
            check(store.abandon(player, "abandon_me").toCompletableFuture().join(),
                    "abandon committed");
            check(!store.abandon(player, "abandon_me").toCompletableFuture().join(),
                    "abandon replay rejected");
            check(!store.active(player).contains("abandon_me"),
                    "abandoned progress removed");

            final UUID cliff = UUID.fromString("00000000-0000-0000-0000-000000001098");
            repository.loadSnapshot(cliff).toCompletableFuture().join();
            for (int index = 0; index < 160; index++) {
                final String quest = "cliff_" + index;
                check(store.discover(cliff, quest, "npc:test")
                                .toCompletableFuture().join(),
                        "quest cliff discovery " + index);
                check(store.accept(cliff, quest, "npc:test")
                                .toCompletableFuture().join(),
                        "quest cliff acceptance " + index);
            }
            final QuestSection compact = repository.cached(cliff).orElseThrow().quests().value();
            check(compact.extensions().size() == 1
                            && compact.extensions().containsKey("quest-metadata"),
                    "160 quests use one structured extension key");
            check(compact.active().size() == 160,
                    "quest cliff remains below the active quest capacity");
            repository.invalidate(cliff);
            repository.loadSnapshot(cliff).toCompletableFuture().join();
            check(store.active(cliff).size() == 160 && store.isDiscovered(cliff, "cliff_159"),
                    "compact quest metadata survives restart beyond the former 512-key cliff");
            final var cliffCompletion = store.complete(cliff, "cliff_0",
                    1_800_000_000_000L, 1L).toCompletableFuture().join();
            check(cliffCompletion.committed() && store.startSource(cliff, "cliff_0") == null,
                    "completion removes active-lifetime quest metadata");
            check(store.isDiscovered(cliff, "cliff_0"),
                    "completion retains durable quest discovery");

            final UUID legacy = UUID.fromString("00000000-0000-0000-0000-000000001097");
            repository.loadSnapshot(legacy).toCompletableFuture().join();
            service.mutateSectionConditional(legacy, ProfileSectionId.QUESTS,
                            QuestSection.class, current -> {
                                final LinkedHashMap<String, Map<String, Long>> active =
                                        new LinkedHashMap<>(current.active());
                                active.put("legacy_one", Map.of());
                                final LinkedHashMap<String, Object> extensions =
                                        new LinkedHashMap<>(current.extensions());
                                extensions.put("source.legacy_one", "npc:legacy");
                                extensions.put("accepted-at.legacy_one", 1_700_000_000_000L);
                                extensions.put("discovered.legacy_one", "npc:legacy");
                                return PlayerProfileService.ConditionalMutation.changed(
                                        new QuestSection(active, current.completed(),
                                                current.rewardReceipts(), current.cooldowns(),
                                                current.communityContributions(),
                                                current.claimableRewards(), extensions), true);
                            }).toCompletableFuture().join();
            check("npc:legacy".equals(store.startSource(legacy, "legacy_one"))
                            && store.isDiscovered(legacy, "legacy_one"),
                    "legacy quest metadata remains readable");
            store.setProgress(legacy, "legacy_one", 0, 1).toCompletableFuture().join();
            final Map<String, Object> migrated = repository.cached(legacy).orElseThrow()
                    .quests().value().extensions();
            check(migrated.containsKey("quest-metadata")
                            && !migrated.containsKey("source.legacy_one")
                            && !migrated.containsKey("accepted-at.legacy_one")
                            && !migrated.containsKey("discovered.legacy_one"),
                    "legacy quest keys migrate on the next write");

            check(service.shutdown(Duration.ofSeconds(5)).toCompletableFuture().join().drained(),
                    "repository drained");
        } finally {
            authority.uninstall();
            try (var paths = Files.walk(root)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); }
                    catch (final Exception ignored) { }
                });
            }
        }
        System.out.println("PlayerProfile quest store regression suite passed. assertions="
                + assertions);
    }

    private static void expectStage(final Class<? extends Throwable> expected,
                                    final CompletionStage<?> stage,
                                    final String message) {
        assertions++;
        try {
            stage.toCompletableFuture().join();
            throw new AssertionError(message + ": expected " + expected.getSimpleName());
        } catch (final CompletionException failure) {
            final Throwable cause = failure.getCause();
            if (!expected.isInstance(cause)) {
                throw new AssertionError(message + ": expected " + expected.getSimpleName()
                        + " but got " + cause, cause);
            }
        }
    }

    private static void check(final boolean condition, final String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
