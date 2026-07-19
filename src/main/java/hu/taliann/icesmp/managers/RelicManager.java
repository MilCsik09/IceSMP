package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.storage.PersistentStore;

import hu.taliann.icesmp.session.PlayerStateCleanup;

import hu.taliann.icesmp.storage.YamlStore;

import hu.taliann.icesmp.items.RelicItemFactory;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.relics.RelicDefinition;
import hu.taliann.icesmp.relics.RelicOwnership;
import hu.taliann.icesmp.relics.RelicRegistry;
import hu.taliann.icesmp.relics.RelicTrigger;
import hu.taliann.icesmp.relics.RelicTriggerConfig;
import hu.taliann.icesmp.relics.SimpleRelicDefinition;
import hu.taliann.icesmp.relics.ability.RelicAbility;
import hu.taliann.icesmp.relics.ability.RelicAbilityContext;
import hu.taliann.icesmp.relics.ability.RelicAbilityRegistry;
import hu.taliann.icesmp.utils.TextUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class RelicManager implements PlayerStateCleanup, PersistentStore {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final RelicRegistry registry;
    private final RelicItemFactory itemFactory;
    private final RelicCooldownService cooldownService;
    private final RelicAbilityRegistry abilityRegistry;
    private final Map<String, EnumMap<RelicTrigger, RelicTriggerConfig>> triggerConfigs = new java.util.HashMap<>();
    private final Map<String, RelicOwnership> ownerships = new ConcurrentHashMap<>();
    private final File ownershipFile;

    private boolean enabled;
    private boolean inactivityEnabled;
    private long inactivityExpiryMillis;

    public RelicManager(final JavaPlugin plugin, final ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.registry = new RelicRegistry();
        this.itemFactory = new RelicItemFactory(plugin);
        this.cooldownService = new RelicCooldownService();
        this.abilityRegistry = new RelicAbilityRegistry();
        this.ownershipFile = new File(plugin.getDataFolder(), "relics.yml");
        plugin.getDataFolder().mkdirs();
    }

    public void load() {
        enabled = configManager.getBoolean("relics.enabled", true);
        inactivityEnabled = configManager.getBoolean("relics.inactivity.enabled", true);
        final long expiryDays = Math.max(0L, configManager.getLong("relics.inactivity.expiry-days", 14L));
        inactivityExpiryMillis = expiryDays * 24L * 60L * 60L * 1000L;
        registry.clear();
        triggerConfigs.clear();
        // Ownerships are loaded even when the system is disabled so a later save() cannot wipe them.
        loadOwnerships();

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
                // 4101 — a relics.yml-ben dokumentált érték; az 1001 a Piros Tokennel osztozott,
                // és a resource pack kedvéért minden CMD egyedi (lásd docs/RESOURCE_PACK_CMD.md).
                4101,
                "A Mételytépő",
                "DARK_PURPLE",
                List.of("&7A törpék rejtélyes civilizációjának...", "&7egy relikviája.")
        );

        // A 4 frakció-elytra relikvia (ideas.md: "4 frakció – 4 elytra relikvia").
        registerRelic(
                "phoenix_wing",
                Material.ELYTRA,
                4201,
                "Főnix-szárny",
                "RED",
                List.of("&7A Piros királyság lángoló ereklyéje.", "&7Viselőjét nem égeti tűz, és zuhanása", "&7lángviharban végződik.")
        );
        registerRelic(
                "frost_wing",
                Material.ELYTRA,
                4202,
                "Zúzmara-szárny",
                "AQUA",
                List.of("&7A Kék királyság jeges ereklyéje.", "&7Szárnyra kapva megfagyasztja", "&7a körülötte lévőket.")
        );
        registerRelic(
                "wander_wind",
                Material.ELYTRA,
                4203,
                "Vándorszél",
                "WHITE",
                List.of("&7A Semlegesek szabad szele.", "&7Gyorsabb sikló, és a föld", "&7sosem üti meg viselőjét.")
        );
        registerRelic(
                "bone_wing",
                Material.ELYTRA,
                4204,
                "Csontszárny",
                "DARK_GRAY",
                List.of("&7Az összeomlott királyság csontból", "&7szőtt szárnya. Éjjel a viselője", "&7maga is árnyékká válik.")
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
        try {
            final YamlConfiguration yaml = new YamlConfiguration();

            for (final Map.Entry<String, RelicOwnership> entry : ownerships.entrySet()) {
                final String basePath = "ownerships." + entry.getKey();
                yaml.set(basePath + ".owner", entry.getValue().owner().toString());
                yaml.set(basePath + ".last-seen", entry.getValue().lastSeenMillis());
            }

            YamlStore.saveAtomic(ownershipFile, yaml);
        } catch (final IOException exception) {
            plugin.getLogger().severe("Failed to save relic ownerships: " + exception.getMessage());
        }
    }

    private void loadOwnerships() {
        ownerships.clear();

        if (!ownershipFile.exists()) {
            return;
        }

        try {
            final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(ownershipFile);
            final ConfigurationSection ownershipSection = yaml.getConfigurationSection("ownerships");
            if (ownershipSection == null) {
                return;
            }

            for (final String relicId : ownershipSection.getKeys(false)) {
                final String rawOwner = ownershipSection.getString(relicId + ".owner");
                final long lastSeenMillis = ownershipSection.getLong(relicId + ".last-seen", 0L);
                if (rawOwner == null || rawOwner.isBlank()) {
                    continue;
                }

                try {
                    final UUID owner = UUID.fromString(rawOwner);
                    ownerships.put(relicId.toLowerCase(Locale.ROOT), new RelicOwnership(owner, lastSeenMillis));
                } catch (final IllegalArgumentException exception) {
                    plugin.getLogger().warning("Invalid owner UUID in relics.yml for relic '" + relicId + "': " + rawOwner);
                }
            }

            plugin.getLogger().info("Loaded " + ownerships.size() + " relic ownership record(s).");
        } catch (final Exception exception) {
            plugin.getLogger().severe("Failed to load relic ownerships: " + exception.getMessage());
        }
    }

    /** @return the relic's persistent ownership record, or null if the relic is unclaimed */
    public RelicOwnership getOwnership(final String relicId) {
        if (relicId == null || relicId.isBlank()) {
            return null;
        }

        return ownerships.get(relicId.toLowerCase(Locale.ROOT));
    }

    /**
     * Records a player as the current owner of a relic and refreshes the last-seen timestamp.
     *
     * @param relicId the relic identifier
     * @param owner the owning player's UUID
     */
    public void recordOwnership(final String relicId, final UUID owner) {
        if (relicId == null || relicId.isBlank() || owner == null) {
            return;
        }

        ownerships.put(relicId.toLowerCase(Locale.ROOT), new RelicOwnership(owner, System.currentTimeMillis()));
        save();
    }

    /**
     * Releases the ownership of a relic so it can be claimed again.
     *
     * @param relicId the relic identifier
     */
    public void releaseOwnership(final String relicId) {
        if (relicId == null || relicId.isBlank()) {
            return;
        }

        if (ownerships.remove(relicId.toLowerCase(Locale.ROOT)) != null) {
            save();
        }
    }

    private boolean isExpired(final RelicOwnership ownership) {
        return inactivityEnabled
                && inactivityExpiryMillis > 0L
                && ownership != null
                && (System.currentTimeMillis() - ownership.lastSeenMillis()) > inactivityExpiryMillis;
    }

    /**
     * Handles the join-time relic inactivity sweep:
     * expired relics are removed from the joining player's inventory with a smoke effect,
     * while active relics owned by the player get a refreshed last-seen timestamp.
     *
     * @param player the joining player
     */
    public void handlePlayerJoin(final Player player) {
        if (!enabled) {
            return;
        }

        final UUID playerId = player.getUniqueId();
        final long now = System.currentTimeMillis();
        final PlayerInventory inventory = player.getInventory();
        final ItemStack[] contents = inventory.getContents();
        boolean inventoryChanged = false;
        boolean ownershipChanged = false;
        final java.util.Set<String> seenRelics = new java.util.HashSet<>();

        for (int slot = 0; slot < contents.length; slot++) {
            final ItemStack itemStack = contents[slot];
            final RelicDefinition definition = identify(itemStack);
            if (definition == null) {
                continue;
            }

            final String relicId = definition.id().toLowerCase(Locale.ROOT);
            final RelicOwnership ownership = ownerships.get(relicId);

            if (isExpired(ownership)) {
                contents[slot] = null;
                inventoryChanged = true;
                ownerships.remove(relicId);
                ownershipChanged = true;
                playExpiryEffect(player);
                sendExpiryMessage(player, definition);
                continue;
            }

            // Singleton: a player may hold at most one copy of a relic. The first copy is clamped
            // to a single item; any further copies (duped/stacked) are removed.
            if (seenRelics.add(relicId)) {
                if (itemStack.getAmount() > 1) {
                    final ItemStack single = itemStack.clone();
                    single.setAmount(1);
                    contents[slot] = single;
                    inventoryChanged = true;
                }
            } else {
                contents[slot] = null;
                inventoryChanged = true;
                continue;
            }

            final UUID itemOwner = itemFactory.getOwner(itemStack);
            if (itemOwner == null || itemOwner.equals(playerId)) {
                ownerships.put(relicId, new RelicOwnership(playerId, now));
                ownershipChanged = true;
            }
        }

        if (inventoryChanged) {
            inventory.setContents(contents);
        }

        if (ownershipChanged) {
            save();
        }
    }

    private void playExpiryEffect(final Player player) {
        final Location effectLocation = player.getLocation().add(0.0D, 1.0D, 0.0D);
        player.getWorld().spawnParticle(Particle.LARGE_SMOKE, effectLocation, 24, 0.3D, 0.5D, 0.3D, 0.02D);
        player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXTINGUISH_FIRE, 1.0F, 0.8F);
    }

    private void sendExpiryMessage(final Player player, final RelicDefinition definition) {
        final String expiredMessage = configManager.getString(
                "relics.messages.expired",
                "&5A(z) &f%relic_name% &5relikvia a hosszú tétlenség miatt elenyészett, és újra megszerezhetővé vált."
        );
        player.sendMessage(TextUtil.color(expiredMessage.replace("%relic_name%", definition.displayName())));
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

    // synchronized: the singleton-ownership check and recordOwnership must be atomic, or two
    // concurrent grants (two altars / altar + admin give) could both pass the check and
    // duplicate a supposedly unique relic.
    public synchronized boolean giveRelic(final Player player, final String relicId, final int amount) {
        if (!enabled || amount <= 0) {
            return false;
        }

        final RelicDefinition definition = registry.findById(relicId);
        if (definition == null) {
            return false;
        }

        final String normalizedId = definition.id().toLowerCase(Locale.ROOT);
        final RelicOwnership currentOwnership = ownerships.get(normalizedId);
        if (currentOwnership != null && !currentOwnership.owner().equals(player.getUniqueId()) && !isExpired(currentOwnership)) {
            // Singleton rule: only one instance of each relic may exist while its owner is active.
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

        recordOwnership(normalizedId, player.getUniqueId());
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
        if (owner != null && !owner.equals(player.getUniqueId())) {
            return false;
        }

        // Singleton enforcement: the CENTRAL ownership record is authoritative. A stale copy
        // (transferred/expired-then-reclaimed by someone else) keeps its item PDC owner but is no
        // longer the active owner — so it must not work, preventing two usable copies of one relic.
        final RelicDefinition definition = identify(itemStack);
        if (definition != null) {
            final RelicOwnership ownership = ownerships.get(definition.id().toLowerCase(Locale.ROOT));
            if (ownership != null && !ownership.owner().equals(player.getUniqueId()) && !isExpired(ownership)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks whether a relic is a weapon relic — those may change hands in PvP
     * (todo.md rule), while passive relics stay protected.
     *
     * @param relicId the relic identifier
     * @return true if the relic is configured as a weapon relic
     */
    public boolean isWeaponRelic(final String relicId) {
        if (relicId == null || relicId.isBlank()) {
            return false;
        }

        final List<String> configured = configManager.getStringList("relics.weapon-relics");
        final List<String> effective = configured.isEmpty() ? List.of("metelytepo") : configured;
        return effective.stream().anyMatch(id -> id.equalsIgnoreCase(relicId));
    }

    /**
     * Transfers a relic's ownership to a new owner: rewrites the item PDC and
     * the persistent ownership record (used by the PvP weapon-relic transfer).
     *
     * @param relicId the relic identifier
     * @param itemStack the relic item to rewrite
     * @param newOwner the new owner
     */
    public void transferOwnership(final String relicId, final ItemStack itemStack, final Player newOwner) {
        if (relicId == null || newOwner == null) {
            return;
        }

        itemFactory.setOwner(itemStack, newOwner.getUniqueId());
        recordOwnership(relicId.toLowerCase(Locale.ROOT), newOwner.getUniqueId());
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
        markOwnerSeen(playerId);
    }

    /**
     * Refreshes the last-seen timestamp on every relic owned by the given player.
     * Called on quit/kick so inactivity is measured from the player's last session.
     *
     * @param playerId the player UUID
     */
    public void markOwnerSeen(final UUID playerId) {
        if (playerId == null) {
            return;
        }

        final long now = System.currentTimeMillis();
        boolean changed = false;

        for (final Map.Entry<String, RelicOwnership> entry : ownerships.entrySet()) {
            if (playerId.equals(entry.getValue().owner())) {
                entry.setValue(new RelicOwnership(playerId, now));
                changed = true;
            }
        }

        if (changed) {
            save();
        }
    }
}


