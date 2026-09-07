package hu.taliann.icesmp.dev.artifact;

import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.DevItemStateData;
import hu.taliann.icesmp.managers.ProfessionRecipeCatalog;
import hu.taliann.icesmp.managers.LootTable;
import hu.taliann.icesmp.items.BlueprintItemFactory;
import hu.taliann.icesmp.items.DevItemFactory;
import hu.taliann.icesmp.items.UniqueMaterialFactory;
import hu.taliann.icesmp.listeners.ProfessionRecipeBookListener;
import hu.taliann.icesmp.utils.MessageManager;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Reward behavior uses the existing manager's identity and durable behavior-state authority. */
public final class BingulusRewardBehavior implements DevArtifactBehavior {
    private static final UUID DEFAULT_OWNER = UUID.fromString("eb80c20f-092a-4d76-bd44-d168c91ea9e2");
    private static final String BASE = "dev-items." + DevItemFactory.BINGULUS_ID;
    private static final List<String> RARITY_ORDER = List.of(
            "kozonseges", "nem_mindennapi", "ritka", "epikus", "legendas", "ereklye");
    private record WeightedValue(String value, double weight) {}
    private record RewardSelection(String rarity, String entry) {}
    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final UniqueMaterialFactory uniqueMaterials;
    private final ProfessionRecipeCatalog recipeCatalog;
    private final BlueprintItemFactory blueprintFactory;
    private final ProfessionRecipeBookListener recipeBuilder;
    private final AtomicBoolean delivering = new AtomicBoolean();
    private volatile long lastActiveNanos;
    private volatile Map<String, Object> saved = initialState();
    private boolean rewardInventoryNoticeSent;
    private boolean rewardConfigWarningSent;

    public BingulusRewardBehavior(final JavaPlugin plugin, final ConfigManager configManager,
            final MessageManager messageManager, final UniqueMaterialFactory uniqueMaterials,
            final ProfessionRecipeCatalog recipeCatalog, final BlueprintItemFactory blueprintFactory,
            final ProfessionRecipeBookListener recipeBuilder) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.uniqueMaterials = uniqueMaterials;
        this.recipeCatalog = recipeCatalog;
        this.blueprintFactory = blueprintFactory;
        this.recipeBuilder = recipeBuilder;
    }

    public static DevArtifactDefinition definition(final ConfigManager config) {
        return new DevArtifactDefinition(DevItemFactory.BINGULUS_ID,
                new ConfiguredArtifactOwner(BASE + ".owner-uuid", DEFAULT_OWNER),
                () -> new DevArtifactPresentation(
                        config.getString(BASE + ".item.material", "HEART_OF_THE_SEA"),
                        config.getString(BASE + ".item.display-name", "&d&lCsodálatos Bingulus"),
                        config.getStringList(BASE + ".item.lore"),
                        Map.of(DevArtifactPresentation.ModelState.IDLE,
                                config.getString(BASE + ".item.item-model", "icesmp:csodalatos_bingulus"))),
                () -> new DevArtifactPolicy(true,
                        config.getBoolean(BASE + ".enabled", true) && config.getBoolean(BASE + ".auto-restore", true), false, false,
                        Math.clamp(config.getLong(BASE + ".check-interval-ticks", 20), 20, 1200)));
    }

    @Override public String artifactId() { return DevItemFactory.BINGULUS_ID; }
    @Override public Map<String, Object> initialState() {
        return Map.of("progress-millis", 0L, "pity", Map.of(
                "since-rare", 0, "since-epic", 0, "since-legendary", 0));
    }
    @Override public void onIssued(final DevArtifactContext context) { onUnavailable(); }
    @Override public void onRecovered(final DevArtifactContext context) { onUnavailable(); }
    @Override public void onUnavailable() { lastActiveNanos = 0; }
    @Override public void shutdown() { onUnavailable(); }
    @Override public ArtifactInteractionResult onInteract(final DevArtifactInteraction interaction) {
        return ArtifactInteractionResult.IGNORED;
    }
    @Override public Map<String, Object> saveBehaviorState() { return saved; }
    @Override public void loadBehaviorState(final Map<String, Object> behavior) {
        decode(new DevArtifactState(DEFAULT_OWNER, DEFAULT_OWNER, true, 0, behavior));
        saved = ArtifactStateValue.freeze(behavior);
    }
    @Override public void validateState(final DevArtifactState state) { decode(state); }

    private DevItemStateData<String> decode(final DevArtifactState state) {
        final Map<String, Object> data = state.behaviorState();
        if (!Set.of("progress-millis", "pity", "pending", "delivery").containsAll(data.keySet())) {
            throw new IllegalArgumentException("Unknown Bingulus behavior field");
        }
        final Map<String, Object> pity = DevArtifactStateCodec.map(data, "pity");
        DevItemStateData.PendingReward<String> pending = null;
        if (data.containsKey("pending")) {
            final Map<String, Object> raw = DevArtifactStateCodec.map(data, "pending");
            final String item = DevArtifactStateCodec.string(raw, "item");
            if (Base64.getDecoder().decode(item).length == 0) throw new IllegalArgumentException("Empty reward item");
            pending = DevItemStateData.PendingReward.of(DevArtifactStateCodec.string(raw, "rarity"),
                    DevArtifactStateCodec.string(raw, "entry"), item, value -> value,
                    value -> !value.isBlank(), rarity -> rankOf(rarity) >= 0);
        }
        if (data.containsKey("delivery") && (!"NEEDS_REVIEW".equals(data.get("delivery")) || pending == null)) {
            throw new IllegalArgumentException("Invalid reward delivery state");
        }
        return new DevItemStateData<>(state.owner(), state.instanceId(), state.issued(),
                DevArtifactStateCodec.integer(data, "progress-millis"), pending,
                new DevItemStateData.PityCounters(Math.toIntExact(DevArtifactStateCodec.integer(pity, "since-rare")),
                        Math.toIntExact(DevArtifactStateCodec.integer(pity, "since-epic")),
                        Math.toIntExact(DevArtifactStateCodec.integer(pity, "since-legendary"))));
    }

    private Map<String, Object> encode(final DevItemStateData<String> value) {
        final Map<String, Object> data = new LinkedHashMap<>();
        data.put("progress-millis", value.progressMillis());
        data.put("pity", Map.of("since-rare", value.pity().sinceRare(), "since-epic", value.pity().sinceEpic(),
                "since-legendary", value.pity().sinceLegendary()));
        if (value.pending() != null) data.put("pending", Map.of("rarity", value.pending().rarity(),
                "entry", value.pending().entry(), "item", value.pending().itemCopy(item -> item)));
        return data;
    }

    @Override public void tick(final DevArtifactContext context, final long nowMillis) {
        if (!configManager.getBoolean(BASE + ".enabled", true)) { onUnavailable(); return; }
        final Player owner = context.player();
        if (owner == null || delivering.get() || !rewardEligible(owner)) { onUnavailable(); return; }
        final DevArtifactState before = context.state();
        if (BingulusDeliveryFence.held(before.behaviorState())) { onUnavailable(); return; }
        final long now = System.nanoTime();
        final long previous = lastActiveNanos;
        lastActiveNanos = now;
        if (previous <= 0 || now <= previous) return;
        final long interval = Math.multiplyExact(Math.max(1L,
                configManager.getLong(BASE + ".reward-interval-seconds", 600L)), 1000L);
        final DevItemStateData<String> progressed = decode(before).advanceProgress(
                TimeUnit.NANOSECONDS.toMillis(now - previous), interval, item -> item);
        if (!context.updateVolatile(before.revision(), encode(progressed))) return;
        if (progressed.progressMillis() < interval || !delivering.compareAndSet(false, true)) return;
        try {
            if (progressed.pending() != null) { deliver(context); return; }
            final RewardSelection selection = rollPendingReward(progressed.pity());
            if (selection == null) {
                if (!rewardConfigWarningSent) plugin.getLogger().warning("Csodálatos Bingulus: nincs érvényes jutalom.");
                rewardConfigWarningSent = true;
                delivering.set(false);
                return;
            }
            rewardConfigWarningSent = false;
            final ItemStack rolled = resolveReward(owner, selection.entry());
            if (rolled == null || rolled.getType().isAir() || rolled.getAmount() <= 0) {
                delivering.set(false);
                return;
            }
            final String encoded = Base64.getEncoder().encodeToString(rolled.serializeAsBytes());
            final var pending = DevItemStateData.PendingReward.of(selection.rarity(), selection.entry(), encoded,
                    value -> value, value -> !value.isBlank(), rarity -> rankOf(rarity) >= 0);
            final DevArtifactState current = context.state();
            context.commit(current.revision(), encode(progressed.withPending(pending, item -> item)))
                    .whenComplete((committed, failure) -> {
                        if (failure != null) { delivering.set(false); return; }
                        context.onOwner(player -> deliver(context), () -> delivering.set(false));
                    });
        } catch (final RuntimeException failure) { delivering.set(false); throw failure; }
    }

    private void deliver(final DevArtifactContext context) {
        final Player owner = context.player();
        if (owner == null || !rewardEligible(owner)) { delivering.set(false); return; }
        final DevArtifactState before = context.state();
        final var rewardState = decode(before);
        final var pending = rewardState.pending();
        if (pending == null || BingulusDeliveryFence.held(before.behaviorState())) { delivering.set(false); return; }
        final ItemStack item = ItemStack.deserializeBytes(Base64.getDecoder().decode(pending.itemCopy(value -> value)));
        if (item.getType().isAir() || item.getAmount() <= 0) throw new IllegalStateException("Invalid pending reward");
        if (!canFit(owner.getInventory(), item)) { notifyRewardInventoryFull(owner); delivering.set(false); return; }
        final Map<String, Object> claimed = BingulusDeliveryFence.claim(before.behaviorState());
        context.commit(before.revision(), claimed).whenComplete((claim, failure) -> {
            if (failure != null) { delivering.set(false); return; }
            context.onOwner(player -> finishDelivery(context, rewardState), () -> {
                context.manager().compensateUnstartedBehavior(context, claim, before.behaviorState());
                delivering.set(false);
            });
        });
    }

    private void finishDelivery(final DevArtifactContext context, final DevItemStateData<String> rewardState) {
        try {
            final Player owner = context.player();
            if (owner == null) { delivering.set(false); return; }
            final var pending = rewardState.pending();
            final ItemStack item = ItemStack.deserializeBytes(Base64.getDecoder().decode(pending.itemCopy(value -> value)));
            final Map<String, Object> next;
            if (!rewardEligible(owner)) {
                // No inventory mutation began: release only the delivery fence, preserving the exact
                // already admitted pending reward, accumulated progress and pity counters.
                next = encode(rewardState);
                onUnavailable();
            } else if (!canFit(owner.getInventory(), item)) {
                notifyRewardInventoryFull(owner);
                next = encode(rewardState);
            } else {
                final ItemStack[] before = owner.getInventory().getStorageContents();
                final ItemStack[] copy = Arrays.stream(before).map(stack -> stack == null ? null : stack.clone()).toArray(ItemStack[]::new);
                if (!owner.getInventory().addItem(item.clone()).isEmpty()) {
                    owner.getInventory().setStorageContents(copy);
                    next = encode(rewardState);
                } else {
                    next = encode(rewardState.completed(pityAfter(pending.rarity(), rewardState.pity()), value -> value));
                    rewardInventoryNoticeSent = false;
                    announce(owner, pending.rarity(), item);
                }
            }
            context.commit(context.state().revision(), next).whenComplete((done, failure) -> delivering.set(false));
        } catch (final RuntimeException failure) { delivering.set(false); throw failure; }
    }

    private static boolean rewardEligible(final Player owner) {
        try {
            final var context = hu.taliann.icesmp.integrity.BukkitRewardSources.entity(
                    hu.taliann.icesmp.integrity.RewardChannel.DEV_ITEM_REWARD, owner).forRecipient(owner.getUniqueId());
            return hu.taliann.icesmp.integrity.GameplayRewardGate.evaluate(context).allowed();
        } catch (final RuntimeException | LinkageError unavailable) { return false; }
    }

    private void notifyRewardInventoryFull(final Player owner) {
        if (!rewardInventoryNoticeSent) {
            rewardInventoryNoticeSent = true;
            owner.sendMessage(messageManager.get("dev-item.inventory-full",
                    "&eA Csodálatos Bingulus jutalma várakozik. Szabadíts fel egy helyet az inventorydban!"));
        }
    }
    private int incrementPity(final int value) { return value == Integer.MAX_VALUE ? value : value + 1; }

    private String forcedMinimumRarity(final DevItemStateData.PityCounters pity) {
        if (pity.sinceLegendary() >= pityThreshold("legendas", 1000)) {
            return "legendas";
        }
        if (pity.sinceEpic() >= pityThreshold("epikus", 150)) {
            return "epikus";
        }
        if (pity.sinceRare() >= pityThreshold("ritka", 30)) {
            return "ritka";
        }
        return "kozonseges";
    }

    private int pityThreshold(final String rarity, final int fallback) {
        return Math.max(1, configManager.getInt(BASE + ".pity." + rarity + ".after-rolls", fallback));
    }

    private RewardSelection rollPendingReward(final DevItemStateData.PityCounters pity) {
        final String rarity = weightedRarity(forcedMinimumRarity(pity));
        final String entry = rarity == null ? null : weightedEntry(rarity);
        return entry == null ? null : new RewardSelection(rarity, entry);
    }

    private String weightedRarity(final String minimum) {
        final ConfigurationSection section = configurationSection(BASE + ".rarity-weights");
        if (section == null) {
            return null;
        }
        final int minRank = rankOf(minimum);
        final List<WeightedValue> values = new ArrayList<>();
        for (final String key : section.getKeys(false)) {
            final int rank = rankOf(key);
            final double weight = section.getDouble(key, 0.0D);
            if (rank >= minRank && weight > 0.0D && hasValidReward(key)) {
                values.add(new WeightedValue(key.toLowerCase(Locale.ROOT), weight));
            }
        }
        return pick(values);
    }

    private boolean hasValidReward(final String rarity) {
        final ConfigurationSection section = configurationSection(BASE + ".rewards." + rarity);
        if (section == null) {
            return false;
        }
        for (final String key : section.getKeys(false)) {
            final ConfigurationSection reward = section.getConfigurationSection(key);
            if (reward == null || reward.getDouble("weight", 1.0D) <= 0.0D) {
                continue;
            }
            if (describeRewardProblem(reward.getString("entry", "")) == null) {
                return true;
            }
        }
        return false;
    }

    private String weightedEntry(final String rarity) {
        final ConfigurationSection section = configurationSection(BASE + ".rewards." + rarity);
        if (section == null) {
            return null;
        }
        final List<WeightedValue> values = new ArrayList<>();
        for (final String key : section.getKeys(false)) {
            final ConfigurationSection reward = section.getConfigurationSection(key);
            if (reward == null) {
                continue;
            }
            final String entry = reward.getString("entry", "");
            final double weight = reward.getDouble("weight", 1.0D);
            if (weight > 0.0D && describeRewardProblem(entry) == null) {
                values.add(new WeightedValue(entry, weight));
            }
        }
        return pick(values);
    }

    private String pick(final List<WeightedValue> values) {
        double total = 0.0D;
        for (final WeightedValue value : values) {
            total += value.weight();
        }
        if (total <= 0.0D) {
            return null;
        }
        double roll = ThreadLocalRandom.current().nextDouble(total);
        for (final WeightedValue value : values) {
            roll -= value.weight();
            if (roll < 0.0D) {
                return value.value();
            }
        }
        return values.get(values.size() - 1).value();
    }

    private ItemStack resolveReward(final Player owner, final String rawEntry) {
        final String entry = rawEntry.trim();
        final int separator = entry.indexOf(':');
        if (separator > 0) {
            final String type = entry.substring(0, separator).toLowerCase(Locale.ROOT);
            final String id = entry.substring(separator + 1).trim();
            if ("unique".equals(type)) {
                final String[] parts = id.split(":");
                int amount = 1;
                if (parts.length >= 2) {
                    try {
                        amount = Math.max(1, Integer.parseInt(parts[1]));
                    } catch (final NumberFormatException ignored) {
                        amount = 1;
                    }
                }
                return uniqueMaterials.create(parts[0], amount);
            }
            if ("recipe".equals(type)) {
                final ProfessionRecipeCatalog.Recipe recipe = recipeCatalog.get(id);
                return recipe == null ? null : recipeBuilder.buildResult(owner, recipe);
            }
            if ("blueprint".equals(type)) {
                return recipeCatalog.get(id) == null ? null : blueprintFactory.create(id);
            }
            if ("relic".equals(type) || "relikvia".equals(type)) {
                return null;
            }
        }
        return LootTable.parseEntry(entry);
    }

    private String describeRewardProblem(final String rawEntry) {
        if (rawEntry == null || rawEntry.isBlank()) {
            return "üres entry";
        }
        final String entry = rawEntry.trim();
        final int separator = entry.indexOf(':');
        if (separator > 0) {
            final String type = entry.substring(0, separator).toLowerCase(Locale.ROOT);
            final String rest = entry.substring(separator + 1).trim();
            if ("unique".equals(type)) {
                final String[] parts = rest.split(":");
                if (parts.length < 1 || parts[0].isBlank()) {
                    return "hiányzó unique azonosító";
                }
                if (parts.length > 2) {
                    return "a unique entry csak unique:<id>[:darab] alakú lehet";
                }
                if (!uniqueMaterials.isDefined(parts[0])) {
                    return "ismeretlen unique azonosító: " + parts[0];
                }
                if (parts.length == 2) {
                    try {
                        if (Integer.parseInt(parts[1]) < 1) {
                            return "a darabszám minimum 1";
                        }
                    } catch (final NumberFormatException exception) {
                        return "nem szám a darabszám: " + parts[1];
                    }
                }
                return null;
            }
            if ("recipe".equals(type)) {
                return rest.isBlank() || recipeCatalog.get(rest) == null
                        ? "ismeretlen recipe azonosító: " + rest : null;
            }
            if ("blueprint".equals(type)) {
                return rest.isBlank() || recipeCatalog.get(rest) == null
                        ? "ismeretlen blueprint azonosító: " + rest : null;
            }
            if ("relic".equals(type) || "relikvia".equals(type)) {
                return "relikvia nem engedélyezett DEV-item jutalomként";
            }
        }
        return LootTable.describeProblem(entry);
    }

    private boolean canFit(final PlayerInventory inventory, final ItemStack incoming) {
        int remaining = incoming.getAmount();
        final int maxStack = Math.max(1, incoming.getMaxStackSize());
        for (final ItemStack existing : inventory.getStorageContents()) {
            if (existing == null || existing.getType().isAir()) {
                remaining -= maxStack;
            } else if (existing.isSimilar(incoming)) {
                remaining -= Math.max(0, maxStack - existing.getAmount());
            }
            if (remaining <= 0) {
                return true;
            }
        }
        return false;
    }

    private DevItemStateData.PityCounters pityAfter(
            final String rarity, final DevItemStateData.PityCounters current) {
        final int rank = rankOf(rarity);
        return new DevItemStateData.PityCounters(
                rank >= rankOf("ritka") ? 0 : incrementPity(current.sinceRare()),
                rank >= rankOf("epikus") ? 0 : incrementPity(current.sinceEpic()),
                rank >= rankOf("legendas") ? 0 : incrementPity(current.sinceLegendary()));
    }

    private void announce(final Player player, final String rarity, final ItemStack reward) {
        final String rarityName = configManager.getString(
                "item-rarity.rarities." + rarity + ".name", rarity);
        final String rarityColor = configManager.getString(
                "item-rarity.rarities." + rarity + ".color", "&f");
        final String itemName = reward.hasItemMeta() && reward.getItemMeta().hasDisplayName()
                ? PlainTextComponentSerializer.plainText().serialize(reward.getItemMeta().displayName())
                : prettyMaterial(reward.getType());

        player.sendMessage(messageManager.get("dev-item.reward",
                "&d✦ A Csodálatos Bingulus jutalma: %s%s &7— &f%s &7×%s",
                rarityColor, rarityName, itemName, String.valueOf(reward.getAmount())));

        final int rank = Math.max(0, rankOf(rarity));
        final Sound sound = rank >= rankOf("legendas")
                ? Sound.ITEM_TOTEM_USE
                : rank >= rankOf("epikus") ? Sound.ENTITY_PLAYER_LEVELUP
                : Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
        player.playSound(player.getLocation(), sound, 0.9F, rank >= rankOf("ritka") ? 1.2F : 1.0F);
        if (rank >= rankOf("epikus")) {
            player.getWorld().spawnParticle(Particle.END_ROD,
                    player.getLocation().add(0.0D, 1.0D, 0.0D), 18, 0.55D, 0.8D, 0.55D, 0.02D);
        }
    }

    private int rankOf(final String rarity) {
        if (rarity == null) {
            return -1;
        }
        return RARITY_ORDER.indexOf(rarity.toLowerCase(Locale.ROOT));
    }

    private String prettyMaterial(final Material material) {
        final String[] words = material.name().toLowerCase(Locale.ROOT).split("_");
        final StringBuilder result = new StringBuilder();
        for (final String word : words) {
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    private ConfigurationSection configurationSection(final String path) {
        return configManager.getConfiguration() == null
                ? null : configManager.getConfiguration().getConfigurationSection(path);
    }

    @Override public void onConfigurationReload() {
        rewardConfigWarningSent = false;
        int warningCount = 0;

        final ConfigurationSection weights = configurationSection(BASE + ".rarity-weights");
        if (weights == null) {
            plugin.getLogger().warning("dev-items.yml: hiányzik a rarity-weights szekció.");
            warningCount++;
        } else {
            for (final String key : weights.getKeys(false)) {
                if (rankOf(key) < 0) {
                    plugin.getLogger().warning("dev-items.yml: ismeretlen jutalomritkaság: " + key);
                    warningCount++;
                    continue;
                }
                final double weight = weights.getDouble(key, 0.0D);
                if (weight < 0.0D) {
                    plugin.getLogger().warning("dev-items.yml: negatív rarity-súly: " + key + " = " + weight);
                    warningCount++;
                } else if (weight > 0.0D && !hasValidReward(key)) {
                    plugin.getLogger().warning("dev-items.yml: a(z) " + key
                            + " ritkaságnak nincs pozitív súlyú, érvényes jutalma.");
                    warningCount++;
                }
            }
        }

        for (final String rarity : RARITY_ORDER) {
            final ConfigurationSection rewards = configurationSection(BASE + ".rewards." + rarity);
            if (rewards == null) {
                continue;
            }
            for (final String rewardKey : rewards.getKeys(false)) {
                final ConfigurationSection reward = rewards.getConfigurationSection(rewardKey);
                if (reward == null) {
                    plugin.getLogger().warning("dev-items.yml: a jutalom nem szekció: "
                            + rarity + "." + rewardKey);
                    warningCount++;
                    continue;
                }
                final double weight = reward.getDouble("weight", 1.0D);
                if (weight < 0.0D) {
                    plugin.getLogger().warning("dev-items.yml: negatív jutalomsúly: "
                            + rarity + "." + rewardKey + " = " + weight);
                    warningCount++;
                }
                final String entry = reward.getString("entry", "");
                final String problem = describeRewardProblem(entry);
                if (problem != null) {
                    plugin.getLogger().warning("dev-items.yml: hibás jutalom " + rarity + "." + rewardKey
                            + " ('" + entry + "'): " + problem);
                    warningCount++;
                }
            }
        }

        if (warningCount == 0) {
            hu.taliann.icesmp.utils.StartupLog.info(plugin.getLogger(), configManager, "Csodálatos Bingulus: a rarity- és jutalomtáblák érvényesek.");
        }
    }

}
