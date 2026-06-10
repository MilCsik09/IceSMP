package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.items.RelicItemFactory;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.relics.RelicDefinition;
import hu.taliann.icesmp.relics.RelicRegistry;
import hu.taliann.icesmp.relics.RelicTrigger;
import hu.taliann.icesmp.relics.RelicTriggerConfig;
import hu.taliann.icesmp.relics.SimpleRelicDefinition;
import hu.taliann.icesmp.relics.ability.RelicAbility;
import hu.taliann.icesmp.relics.ability.RelicAbilityContext;
import hu.taliann.icesmp.relics.ability.RelicAbilityRegistry;
import hu.taliann.icesmp.utils.TextUtil;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public final class RelicManager {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final RelicRegistry registry;
    private final RelicItemFactory itemFactory;
    private final RelicCooldownService cooldownService;
    private final RelicAbilityRegistry abilityRegistry;
    private final Map<String, EnumMap<RelicTrigger, RelicTriggerConfig>> triggerConfigs = new java.util.HashMap<>();

    private boolean enabled;

    public RelicManager(final JavaPlugin plugin, final ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.registry = new RelicRegistry();
        this.itemFactory = new RelicItemFactory(plugin);
        this.cooldownService = new RelicCooldownService();
        this.abilityRegistry = new RelicAbilityRegistry();
    }

    public void load() {
        enabled = configManager.getBoolean("relics.enabled", true);
        registry.clear();
        triggerConfigs.clear();

        if (!enabled) {
            plugin.getLogger().info("Relic system is disabled in config.");
            return;
        }

        if (configManager.getConfiguration() == null) {
            plugin.getLogger().warning("Relic config is not available; using built-in relic defaults.");
        }

        registerRelic(
                "metelytepo",
                Material.GOLDEN_AXE,
                1001,
                "A Mételytépő",
                "DARK_PURPLE",
                List.of("&7A törpék rejtélyes civilizációjának...", "&7egy relikviája.")
        );

        plugin.getLogger().info("Loaded " + registry.all().size() + " hardcoded relic definition(s). Cosmetics/triggers loaded from config when available.");
    }

    private void registerRelic(
            final String id,
            final Material hardcodedMaterial,
            final int hardcodedCmd,
            final String defaultName,
            final String defaultColor,
            final List<String> defaultLore
    ) {
        final String definitionPath = "relics.definitions." + id;
        final ConfigurationSection configuration = configManager.getConfiguration();
        final ConfigurationSection relicSection = configuration == null ? null : configuration.getConfigurationSection(definitionPath);

        String displayName = defaultName;
        String displayColor = toLegacyColorCode(defaultColor, "&f");
        List<String> lore = List.copyOf(defaultLore);

        if (relicSection != null) {
            final String configuredName = relicSection.getString("display-name");
            if (configuredName != null && !configuredName.isBlank()) {
                displayName = configuredName;
            }

            final String configuredColor = relicSection.getString("display-color");
            if (configuredColor != null && !configuredColor.isBlank()) {
                displayColor = toLegacyColorCode(configuredColor, displayColor);
            }

            if (relicSection.isList("lore")) {
                final List<String> configuredLore = relicSection.getStringList("lore");
                if (!configuredLore.isEmpty()) {
                    lore = configuredLore;
                }
            }
        }

        final EnumMap<RelicTrigger, RelicTriggerConfig> relicTriggers = new EnumMap<>(RelicTrigger.class);
        final ConfigurationSection triggerSection = relicSection == null ? null : relicSection.getConfigurationSection("triggers");
        for (final RelicTrigger trigger : RelicTrigger.values()) {
            final String triggerPath = trigger.name();
            final boolean triggerEnabled = triggerSection != null && triggerSection.getBoolean(triggerPath + ".enabled", false);
            final String abilityId = triggerSection == null ? "" : triggerSection.getString(triggerPath + ".ability-id", "");
            final long cooldownSeconds = Math.max(0L, triggerSection == null ? 0L : triggerSection.getLong(triggerPath + ".cooldown-seconds", 0L));
            final String message = triggerSection == null
                    ? ""
                    : triggerSection.getString(triggerPath + ".message", "&7%relic_name% triggered (&f%trigger%&7).");
            relicTriggers.put(trigger, new RelicTriggerConfig(triggerEnabled, abilityId, cooldownSeconds, message));
        }

        registry.register(new SimpleRelicDefinition(
                id,
                displayName,
                displayColor,
                lore,
                hardcodedMaterial,
                Math.max(0, hardcodedCmd)
        ));
        triggerConfigs.put(id.toLowerCase(Locale.ROOT), relicTriggers);

        if (relicSection == null) {
            plugin.getLogger().warning("Missing config section '" + definitionPath + "'; using built-in cosmetics for relic '" + id + "'.");
        }
    }

    public void save() {
        // Runtime relic ownership/timer persistence will be handled here.
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Collection<RelicDefinition> getDefinitions() {
        return registry.all();
    }

    public RelicDefinition getDefinition(final String relicId) {
        return registry.findById(relicId);
    }

    public ItemStack createRelic(final String relicId, final UUID owner) {
        final RelicDefinition definition = registry.findById(relicId);
        if (definition == null) {
            return null;
        }

        return itemFactory.create(definition, owner);
    }

    public boolean giveRelic(final Player player, final String relicId, final int amount) {
        if (!enabled || amount <= 0) {
            return false;
        }

        final RelicDefinition definition = registry.findById(relicId);
        if (definition == null) {
            return false;
        }

        int remaining = amount;
        while (remaining > 0) {
            final ItemStack itemStack = itemFactory.create(definition, player.getUniqueId());
            itemStack.setAmount(Math.min(64, remaining));
            final Map<Integer, ItemStack> leftovers = player.getInventory().addItem(itemStack);
            if (!leftovers.isEmpty()) {
                leftovers.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
            }
            remaining -= itemStack.getAmount();
        }

        return true;
    }

    public boolean isRelicItem(final ItemStack itemStack) {
        if (!enabled || !itemFactory.isRelicItem(itemStack)) {
            return false;
        }

        final RelicDefinition definition = identify(itemStack);
        return definition != null && itemStack.getType() == definition.material();
    }

    public RelicDefinition identify(final ItemStack itemStack) {
        if (!enabled || !itemFactory.isRelicItem(itemStack)) {
            return null;
        }

        final String relicType = itemFactory.getRelicType(itemStack);
        return registry.findById(relicType);
    }

    public boolean canUse(final Player player, final ItemStack itemStack) {
        final UUID owner = itemFactory.getOwner(itemStack);
        return owner == null || owner.equals(player.getUniqueId());
    }

    public boolean handleTrigger(final Player player, final ItemStack itemStack, final RelicTrigger trigger) {
        if (!enabled || trigger == null || itemStack == null) {
            return false;
        }

        final RelicDefinition definition = identify(itemStack);
        if (definition == null) {
            return false;
        }

        if (!canUse(player, itemStack)) {
            player.sendMessage(TextUtil.color("&cEz a relic nem a tied."));
            return false;
        }

        final RelicTriggerConfig triggerConfig = getTriggerConfig(definition.id(), trigger);
        if (triggerConfig == null || !triggerConfig.enabled()) {
            return false;
        }

        final String configuredAbilityId = triggerConfig.abilityId() == null ? "" : triggerConfig.abilityId().trim();
        if (!configuredAbilityId.isEmpty()) {
            final RelicAbility ability = abilityRegistry.find(configuredAbilityId);
            if (ability == null) {
                plugin.getLogger().warning("Relic '" + definition.id() + "' references missing ability-id: " + configuredAbilityId);
                return false;
            }

            if (cooldownService.isOnCooldown(player.getUniqueId(), definition.id(), trigger)) {
                final long remainingMillis = cooldownService.getRemainingMillis(player.getUniqueId(), definition.id(), trigger);
                sendCooldownMessage(player, remainingMillis);
                return true;
            }

            final boolean success = ability.execute(new RelicAbilityContext(player, itemStack, definition, trigger, configManager));
            if (!success) {
                return true;
            }

            cooldownService.startCooldown(player.getUniqueId(), definition.id(), trigger, triggerConfig.cooldownSeconds() * 1000L);
            sendTriggerMessage(player, definition, trigger, triggerConfig.message());
            return true;
        }

        final long remainingMillis = cooldownService.getRemainingMillis(player.getUniqueId(), definition.id(), trigger);
        if (remainingMillis > 0L) {
            sendCooldownMessage(player, remainingMillis);
            return true;
        }

        cooldownService.startCooldown(player.getUniqueId(), definition.id(), trigger, triggerConfig.cooldownSeconds() * 1000L);
        sendTriggerMessage(player, definition, trigger, triggerConfig.message());
        return true;
    }

    public void refreshPlayerRelicItems(final Player player) {
        if (!enabled) {
            return;
        }

        final PlayerInventory inventory = player.getInventory();
        final ItemStack[] contents = inventory.getContents();
        boolean changed = false;

        for (int slot = 0; slot < contents.length; slot++) {
            final ItemStack itemStack = contents[slot];
            final RelicDefinition definition = identify(itemStack);
            if (definition == null) {
                continue;
            }

            itemFactory.refresh(itemStack, definition);
            contents[slot] = itemStack;
            changed = true;
        }

        if (changed) {
            inventory.setContents(contents);
        }
    }

    public String describeDefinitions() {
        if (!enabled || registry.all().isEmpty()) {
            return "none";
        }

        return registry.all().stream()
                .map(RelicDefinition::id)
                .collect(Collectors.joining(", "));
    }

    private String toLegacyColorCode(final String rawColor, final String fallback) {
        if (rawColor == null || rawColor.isBlank()) {
            return fallback;
        }

        final String trimmed = rawColor.trim();
        if (trimmed.startsWith("&") || trimmed.startsWith("§")) {
            return trimmed;
        }

        return switch (trimmed.toUpperCase(Locale.ROOT)) {
            case "BLACK" -> "&0";
            case "DARK_BLUE" -> "&1";
            case "DARK_GREEN" -> "&2";
            case "DARK_AQUA" -> "&3";
            case "DARK_RED" -> "&4";
            case "DARK_PURPLE" -> "&5";
            case "GOLD" -> "&6";
            case "GRAY" -> "&7";
            case "DARK_GRAY" -> "&8";
            case "BLUE" -> "&9";
            case "GREEN" -> "&a";
            case "AQUA" -> "&b";
            case "RED" -> "&c";
            case "LIGHT_PURPLE" -> "&d";
            case "YELLOW" -> "&e";
            case "WHITE" -> "&f";
            default -> fallback;
        };
    }

    private RelicTriggerConfig getTriggerConfig(final String relicId, final RelicTrigger trigger) {
        if (relicId == null || trigger == null) {
            return null;
        }

        final EnumMap<RelicTrigger, RelicTriggerConfig> relicConfig = triggerConfigs.get(relicId.toLowerCase(Locale.ROOT));
        if (relicConfig == null) {
            return null;
        }

        return relicConfig.get(trigger);
    }


    private void sendCooldownMessage(final Player player, final long remainingMillis) {
        final String cooldownMessage = configManager.getString(
                "relics.messages.cooldown",
                "&cA relic cooldownon van: &f%seconds%s"
        );
        player.sendMessage(TextUtil.color(cooldownMessage.replace("%seconds%", String.valueOf(Math.max(1L, (remainingMillis + 999L) / 1000L)))));
    }

    private void sendTriggerMessage(final Player player, final RelicDefinition definition, final RelicTrigger trigger, final String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return;
        }

        final String message = rawMessage
                .replace("%relic_id%", definition.id())
                .replace("%relic_name%", definition.displayName())
                .replace("%trigger%", trigger.name());
        player.sendMessage(TextUtil.color(message));
    }

    public void cleanup(final UUID playerId) {
        cooldownService.clearPlayer(playerId);
    }

    public void clearPlayerState(final UUID playerId) {
        cleanup(playerId);
    }
}


