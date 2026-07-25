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
    // Reload (clear+rebuild) közben régió-szálak olvassák — concurrent szerkezet kell.
    private final Map<String, EnumMap<RelicTrigger, RelicTriggerConfig>> triggerConfigs = new ConcurrentHashMap<>();
    private final Map<String, RelicOwnership> ownerships = new ConcurrentHashMap<>();
    /**
     * "Elveszett" passzív relikviák (halálkor a tárgy megsemmisül, de a tulajdon marad):
     * relicId → az elvesztés időpontja. Amíg él, CSAK a tulajdonos idézheti újra az
     * oltárnál; a rövidített lost-expiry után a relikvia mindenki számára felszabadul.
     */
    private final Map<String, Long> lostSince = new ConcurrentHashMap<>();
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
                "A Mételytépő",
                "DARK_PURPLE",
                List.of("&7A törpék rejtélyes civilizációjának...", "&7egy relikviája.")
        );

        // A 4 frakció-elytra relikvia.
        registerRelic(
                "phoenix_wing",
                Material.ELYTRA,
                "Főnix-szárny",
                "RED",
                List.of("&7Perinfernicitas lángoló ereklyéje,", "&7Soleil főnixeinek tollából szőve.", "&7Viselőjét nem égeti tűz, és zuhanása", "&7lángviharban végződik.")
        );
        registerRelic(
                "frost_wing",
                Material.ELYTRA,
                "Zúzmara-szárny",
                "AQUA",
                List.of("&7Cryghaliris jeges ereklyéje, Kallan", "&7jégsárkányainak leheletével átitatva.", "&7Szárnyra kapva megfagyasztja", "&7a körülötte lévőket.")
        );
        registerRelic(
                "wander_wind",
                Material.ELYTRA,
                "Vándorszél",
                "WHITE",
                List.of("&7Ryanora & Caldestera szabad szele,", "&7Arkynn békés örökségének fuvallata.", "&7Gyorsabb sikló, és a föld", "&7sosem üti meg viselőjét.")
        );
        // Eleftheria Könnye — misztikus, egy-példányos gyűjtő-relikvia (nincs aktív képessége;
        // a Néma Királynő-lore hordozója, rituálé/admin úton szerezhető).
        registerRelic(
                "eleftheria_konnye",
                Material.HEART_OF_THE_SEA,
                "Eleftheria Könnye",
                "DARK_PURPLE",
                List.of("&7Megkövült, éjfekete csepp; a Néma Királynő", "&7első suttogása hozta létre, magába zárva", "&7a Fa kínjait és a magányt.")
        );
        registerRelic(
                "bone_wing",
                Material.ELYTRA,
                "Csontszárny",
                "DARK_GRAY",
                List.of("&7A Káoszkor csontból szőtt szárnya, a", "&7Néma Királynő élőhalottainak maradványa.", "&7Éjjel a viselője maga is", "&7árnyékká válik.")
        );

        // Kaszt-tematikus relikvia: a hatását a ResourceBonusService kapuzza Sárkányidézőre.
        registerRelic(
                "sarkany_tojas",
                Material.DRAGON_EGG,
                "Sárkánytojás-töredék",
                "LIGHT_PURPLE",
                List.of("&7Egy sosem kelt sárkány álma, kőbe zárva.", "&7A Sárkányidéző kezében az Eszencia", "&7medre kitágul — másnak csak hideg kő.")
        );
        plugin.getLogger().info("Loaded " + registry.all().size() + " hardcoded relic definition(s). Cosmetics/triggers loaded from config when available.");
    }

    private void registerRelic(
            final String id,
            final Material hardcodedMaterial,
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
                hardcodedMaterial
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
                final Long lost = lostSince.get(entry.getKey());
                if (lost != null) {
                    yaml.set(basePath + ".lost-since", lost);
                }
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
                    final long lost = ownershipSection.getLong(relicId + ".lost-since", 0L);
                    if (lost > 0L) {
                        lostSince.put(relicId.toLowerCase(Locale.ROOT), lost);
                    }
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

        // Az elveszett-jelölés a tulajdonnal együtt jár — felszabadításkor az is törlendő,
        // különben a következő tulajdonos öröklött "lost" állapotot kapna.
        final boolean removedOwnership = ownerships.remove(relicId.toLowerCase(Locale.ROOT)) != null;
        final boolean removedLost = lostSince.remove(relicId.toLowerCase(Locale.ROOT)) != null;
        if (removedOwnership || removedLost) {
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
     * Id-tudatos lejárat: a normál inaktivitás MELLETT az "elveszett" (halálban
     * megsemmisült) relikvia RÖVIDÍTETT lejáratát is nézi — ha a tulajdonos
     * lost-expiry-days alatt nem idézi újra, a relikvia mindenkinek felszabadul.
     */
    private boolean isExpiredFor(final String relicId, final RelicOwnership ownership) {
        if (isExpired(ownership)) {
            return true;
        }
        final Long lost = relicId == null ? null : lostSince.get(relicId.toLowerCase(Locale.ROOT));
        if (lost == null) {
            return false;
        }
        final long lostExpiryMillis = Math.max(0L,
                configManager.getLong("relics.inactivity.lost-expiry-days", 3L)) * 24L * 60L * 60L * 1000L;
        return lostExpiryMillis > 0L && (System.currentTimeMillis() - lost) > lostExpiryMillis;
    }

    /** A relikvia "elveszett" állapotba kerül (halál reclaim-módban): a tárgy megsemmisült,
     * a tulajdon marad — csak a tulaj idézheti újra, a rövidített lejáratig. */
    public void markLost(final String relicId) {
        if (relicId != null) {
            lostSince.put(relicId.toLowerCase(Locale.ROOT), System.currentTimeMillis());
            save();
        }
    }

    /** Az elveszett-jelölés törlése (sikeres újraidézés / új tulajdonos). */
    private void clearLost(final String relicId) {
        if (relicId != null && lostSince.remove(relicId.toLowerCase(Locale.ROOT)) != null) {
            save();
        }
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

            if (isExpiredFor(relicId, ownership)) {
                contents[slot] = null;
                inventoryChanged = true;
                ownerships.remove(relicId);
                lostSince.remove(relicId);
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
            // A központi tulajdonjogot csak akkor frissítjük, ha az üres, lejárt,
            // vagy MÁR EZÉ a játékosé — egy másik, aktív tulajdonos jogát egy régi (halott)
            // példány belépése nem írhatja felül (ownership-eltérítés).
            if ((itemOwner == null || itemOwner.equals(playerId))
                    && (ownership == null || ownership.owner().equals(playerId) || isExpiredFor(relicId, ownership))) {
                ownerships.put(relicId, new RelicOwnership(playerId, now));
                ownershipChanged = true;
            }
        }

        // A VISELT páncél (a 6 relikviából 4 elytra!) és az offhand is része a
        // sweepnek — korábban a getContents() csak a 36 fő slotot látta, így a viselt szárny
        // sosem évült el, nem frissült és a dedup sem érte el.
        final ItemStack[] armor = inventory.getArmorContents();
        boolean armorChanged = false;
        for (int slot = 0; slot < armor.length; slot++) {
            final ItemStack itemStack = armor[slot];
            final RelicDefinition definition = identify(itemStack);
            if (definition == null) {
                continue;
            }
            final String relicId = definition.id().toLowerCase(Locale.ROOT);
            final RelicOwnership ownership = ownerships.get(relicId);
            if (isExpiredFor(relicId, ownership)) {
                armor[slot] = null;
                armorChanged = true;
                ownerships.remove(relicId);
                lostSince.remove(relicId);
                ownershipChanged = true;
                playExpiryEffect(player);
                sendExpiryMessage(player, definition);
                continue;
            }
            if (!seenRelics.add(relicId)) {
                armor[slot] = null;
                armorChanged = true;
                continue;
            }
            final UUID itemOwner = itemFactory.getOwner(itemStack);
            if ((itemOwner == null || itemOwner.equals(playerId))
                    && (ownership == null || ownership.owner().equals(playerId) || isExpiredFor(relicId, ownership))) {
                ownerships.put(relicId, new RelicOwnership(playerId, now));
                ownershipChanged = true;
            }
        }
        if (armorChanged) {
            inventory.setArmorContents(armor);
        }
        final ItemStack offhand = inventory.getItemInOffHand();
        final RelicDefinition offhandDefinition = identify(offhand);
        if (offhandDefinition != null) {
            final String relicId = offhandDefinition.id().toLowerCase(Locale.ROOT);
            final RelicOwnership ownership = ownerships.get(relicId);
            if (isExpiredFor(relicId, ownership)) {
                inventory.setItemInOffHand(null);
                ownerships.remove(relicId);
                lostSince.remove(relicId);
                ownershipChanged = true;
                playExpiryEffect(player);
                sendExpiryMessage(player, offhandDefinition);
            } else if (!seenRelics.add(relicId)) {
                inventory.setItemInOffHand(null);
            } else {
                final UUID itemOwner = itemFactory.getOwner(offhand);
                if ((itemOwner == null || itemOwner.equals(playerId))
                        && (ownership == null || ownership.owner().equals(playerId) || isExpiredFor(relicId, ownership))) {
                    ownerships.put(relicId, new RelicOwnership(playerId, now));
                    ownershipChanged = true;
                }
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

    public synchronized boolean giveRelic(final Player player, final String relicId, final int amount) {
        return giveRelic(player, relicId, amount, false);
    }

    // synchronized: the singleton-ownership check and recordOwnership must be atomic, or two
    // concurrent grants (two altars / altar + admin give) could both pass the check and
    // duplicate a supposedly unique relic.
    public synchronized boolean giveRelic(final Player player, final String relicId, final int amount, final boolean force) {
        if (!enabled || amount <= 0) {
            return false;
        }

        final RelicDefinition definition = registry.findById(relicId);
        if (definition == null) {
            return false;
        }

        final String normalizedId = definition.id().toLowerCase(Locale.ROOT);
        final RelicOwnership currentOwnership = ownerships.get(normalizedId);
        // Reclaim-út: a halálban ELVESZETT relikviát a tulajdonosa a lost-expiry lejártáig
        // újraidézheti (más nem); minden más aktív-tulajdonos eset tiltott (self-dup védelem).
        final boolean ownerReclaim = currentOwnership != null
                && currentOwnership.owner().equals(player.getUniqueId())
                && lostSince.containsKey(normalizedId);
        if (!force && !ownerReclaim && currentOwnership != null && !isExpiredFor(normalizedId, currentOwnership)) {
            // Singleton rule: while ANY active owner exists — the requester included — no new
            // copy may be minted. A self-re-summon would otherwise duplicate a usable relic
            // (the old check only blocked FOREIGN owners). A destroyed/lost item is
            // recovered via the reclaim ritual, the lost/inactivity expiry or an admin force-give.
            return false;
        }
        clearLost(normalizedId);

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
        AdvancementService.award(player, "first_relic");
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
            final String relicId = definition.id().toLowerCase(Locale.ROOT);
            final RelicOwnership ownership = ownerships.get(relicId);
            if (ownership != null && !ownership.owner().equals(player.getUniqueId()) && !isExpiredFor(relicId, ownership)) {
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

        // Az explicit ÜRES lista tiszteletben tartva (= egyik relikvia sem cserél gazdát PvP-ben);
        // a default csak akkor él, ha a kulcs egyáltalán nincs kiírva a configban.
        final List<String> configured = configManager.getStringList("relics.weapon-relics");
        final boolean keySet = configManager.getConfiguration() != null
                && configManager.getConfiguration().isSet("relics.weapon-relics");
        final List<String> effective = configured.isEmpty() && !keySet ? List.of("metelytepo") : configured;
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

        // A viselt páncél és az offhand relikviái is frissülnek (a szárnyak
        // jellemzően a mellvért-slotban élnek — korábban sosem kaptak kozmetikai frissítést).
        final ItemStack[] armor = inventory.getArmorContents();
        boolean armorChanged = false;
        for (final ItemStack itemStack : armor) {
            final RelicDefinition definition = identify(itemStack);
            if (definition != null) {
                itemFactory.refresh(itemStack, definition);
                armorChanged = true;
            }
        }
        if (armorChanged) {
            inventory.setArmorContents(armor);
        }
        final ItemStack offhand = inventory.getItemInOffHand();
        final RelicDefinition offhandDefinition = identify(offhand);
        if (offhandDefinition != null) {
            itemFactory.refresh(offhand, offhandDefinition);
            inventory.setItemInOffHand(offhand);
        }
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
        // SZÁNDÉKOSAN nem törli a képesség-cooldownokat: a ki-be lépés különben
        // ingyen nullázná az 5-30 perces relikvia-cooldownt (relog-exploit). A map
        // amúgy is apró (relikvia-tulajdonosok száma korlátos), nem szivárgás-veszély.
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


