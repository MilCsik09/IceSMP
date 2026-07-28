package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.data.Territory;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ritual altars: each altar is a multi-block shrine.
 * Sneak-right-clicking the altar's core block validates the surrounding structure
 * (if configured) and, given the required sacrifices, consumes them and grants an
 * outcome chosen by the ritual's {@code type}:
 * <ul>
 *   <li>{@code relic} (default): summons the matching relic — the RelicManager
 *       enforces the one-of-each singleton rule.</li>
 *   <li>{@code cleanse}: removes the sinner mark and the sin counter (blocked by
 *       a sealed dark pact — that penance runs its own quest chain).</li>
 *   <li>{@code buff}: applies the configured potion effects for a duration.</li>
 *   <li>{@code home}: teleports the ritualist to their faction's capital.</li>
 * </ul>
 * The {@code structure} list ("dx,dy,dz:MATERIAL" offsets relative to the core
 * block) turns an altar into a buildable shrine; non-relic rituals may set
 * {@code cooldown-seconds} to rate-limit repeats (in memory, reset on restart).
 */
public final class RitualManager implements hu.taliann.icesmp.session.PlayerStateCleanup {

    /** E25 — setter-injektált függőségek a pakt-ceremóniához. */
    private volatile hu.taliann.icesmp.managers.ResourceBonusService resourceBonusService;
    private volatile hu.taliann.icesmp.items.UniqueMaterialFactory uniqueMaterialFactory;

    public void setPaktDependencies(final hu.taliann.icesmp.managers.ResourceBonusService bonusService,
                                    final hu.taliann.icesmp.items.UniqueMaterialFactory factory) {
        this.resourceBonusService = bonusService;
        this.uniqueMaterialFactory = factory;
    }

    private final org.bukkit.plugin.java.JavaPlugin plugin;
    private final ConfigManager configManager;
    private final RelicManager relicManager;
    private final SinManager sinManager;
    private final FactionManager factionManager;
    private final TerritoryManager territoryManager;
    private final JobManager jobManager;
    private final MessageManager messageManager;
    // Per-player, per-ritual cooldown expiry (in-memory; only used by non-relic rituals).
    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();
    // Folyamatban lévő hazatérés-teleportok: az áldozat/cooldown csak a sikeres megérkezéskor
    // rögzül, addig a dupla oltár-kattintást ez a jelölő fogja meg.
    private final java.util.Set<UUID> homeInFlight = ConcurrentHashMap.newKeySet();

    public RitualManager(final org.bukkit.plugin.java.JavaPlugin plugin, final ConfigManager configManager,
                         final RelicManager relicManager,
                         final SinManager sinManager, final FactionManager factionManager,
                         final TerritoryManager territoryManager, final JobManager jobManager,
                         final MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.relicManager = relicManager;
        this.sinManager = sinManager;
        this.factionManager = factionManager;
        this.territoryManager = territoryManager;
        this.jobManager = jobManager;
        this.messageManager = messageManager;
    }

    /** Drops the player's per-ritual cooldown map on logout so the nested map cannot grow unbounded. */
    @Override
    public void clearPlayerState(final UUID playerId) {
        if (playerId != null) {
            cooldowns.remove(playerId);
        }
    }

    /**
     * Attempts a ritual at the given altar block (the core block the player clicked).
     *
     * @param player the ritualist
     * @param altar the block they interacted with
     * @return true if a ritual was matched and handled (success or failure feedback sent)
     */
    public boolean tryRitual(final Player player, final Block altar) {
        if (configManager.getConfiguration() == null) {
            return false;
        }

        final ConfigurationSection ritualsSection = configManager.getConfiguration().getConfigurationSection("rituals");
        if (ritualsSection == null) {
            return false;
        }

        for (final String ritualId : ritualsSection.getKeys(false)) {
            final ConfigurationSection ritual = ritualsSection.getConfigurationSection(ritualId);
            if (ritual == null) {
                continue;
            }

            final Material core = Material.matchMaterial(ritual.getString("altar-block", ""));
            if (core != altar.getType()) {
                continue;
            }

            performRitual(player, ritualId, ritual, altar);
            return true;
        }

        return false;
    }

    private void performRitual(final Player player, final String ritualId, final ConfigurationSection ritual,
                               final Block altar) {
        final String type = ritual.getString("type", "relic").toLowerCase(Locale.ROOT);

        // Optional gates: class-specific and faction-specific altars (config: requires-class / requires-faction).
        final String requiredClass = ritual.getString("requires-class", "");
        if (!requiredClass.isBlank()) {
            final var job = jobManager.getPrimaryJob(player);
            if (job == null || !job.getId().equalsIgnoreCase(requiredClass.trim())) {
                player.sendMessage(messageManager.getMessage(
                        "ritual-wrong-class",
                        "<red>Ez az oltár nem a te kasztodhoz szól.</red>"
                ));
                return;
            }
        }
        final String requiredFaction = ritual.getString("requires-faction", "");
        if (!requiredFaction.isBlank()) {
            final FactionType faction = factionManager.getFaction(player.getUniqueId());
            if (faction == null || !faction.name().equalsIgnoreCase(requiredFaction.trim())) {
                player.sendMessage(messageManager.getMessage(
                        "ritual-wrong-faction",
                        "<red>Ez az oltár nem a te frakciódhoz szól.</red>"
                ));
                return;
            }
        }

        // Multi-block structure: every configured offset block must match, or the shrine is incomplete.
        if (!matchesStructure(altar, ritual.getStringList("structure"))) {
            player.sendMessage(messageManager.getMessage(
                    "ritual-structure-incomplete",
                    "<red>Az oltár szerkezete hiányos — építsd meg a teljes szentélyt a mag-blokk köré.</red>"
            ));
            return;
        }

        // Cooldown (non-relic rituals; the relic singleton rule is its own gate).
        final long cooldownSeconds = Math.max(0L, ritual.getLong("cooldown-seconds", 0L));
        if (cooldownSeconds > 0L) {
            final long remaining = remainingCooldown(player, ritualId);
            if (remaining > 0L) {
                player.sendMessage(messageManager.getMessage(
                        "ritual-cooldown",
                        "<red>Az oltár még nem töltődött fel — várj még {seconds} másodpercet.</red>",
                        Map.of("seconds", String.valueOf((long) Math.ceil(remaining / 1000.0D)))
                ));
                return;
            }
        }

        final Map<Material, Integer> sacrifices = parseSacrifices(ritual.getStringList("sacrifice"));
        if (!hasAll(player, sacrifices)) {
            player.sendMessage(messageManager.getMessage(
                    "ritual-missing-sacrifice",
                    "<red>Hiányoznak az áldozati tárgyak a rituáléhoz.</red>"
            ));
            return;
        }

        // A home-ág aszinkron teleportra vár — az áldozat/cooldown ott csak a TÉNYLEGES
        // megérkezés után rögzülhet, ezért nem mehet a szinkron outcome-útra.
        if ("home".equals(type)) {
            performHomeRitual(player, ritualId, sacrifices, cooldownSeconds);
            return;
        }

        // Resolve the outcome first — only consume the sacrifices if it actually succeeds.
        final boolean success = switch (type) {
            case "cleanse" -> tryCleanse(player);
            case "buff" -> tryBuff(player, ritual);
            case "uncurse" -> tryUncurse(player);
            case "pakt" -> tryPakt(player);
            default -> tryRelic(player, ritualId, ritual);
        };
        if (!success) {
            return;
        }

        AdvancementService.award(player, "first_ritual");
        if (!consume(player, sacrifices)) {
            // A hasAll UGYANEZT a predikátumot használta ugyanezen a szálon, ezért ide nem
            // szabad eljutni: ha mégis, a rituálé áldozat nélkül futott le.
            plugin.getLogger().severe("Rituálé-áldozat nem fogyott el: " + ritualId
                    + " (" + player.getName() + ")");
        }
        if (cooldownSeconds > 0L) {
            cooldowns.computeIfAbsent(player.getUniqueId(), key -> new ConcurrentHashMap<>())
                    .put(ritualId, System.currentTimeMillis() + cooldownSeconds * 1000L);
        }
        playSuccessEffect(player, type);
    }

    /** Relic summon: honours the RelicManager singleton rule (a live-owned relic can't be re-summoned). */
    private boolean tryRelic(final Player player, final String ritualId, final ConfigurationSection ritual) {
        // The relic id defaults to the ritual id (back-compat), but may be overridden explicitly.
        final String relicId = ritual.getString("relic", ritualId);
        if (!relicManager.giveRelic(player, relicId, 1)) {
            player.sendMessage(messageManager.getMessage(
                    "ritual-relic-unavailable",
                    "<red>A relikvia jelenleg nem idézhető meg (már van élő tulajdonosa).</red>"
            ));
            return false;
        }
        player.sendMessage(messageManager.getMessage(
                "ritual-success",
                "<gold>A rituálé sikeres — a relikvia testet öltött a kezedben!</gold>"
        ));
        return true;
    }

    /** B54: setter-injected átok-szolgáltatás (a service a manager után épül a DI-sorrendben). */
    private volatile CursedGearService cursedGearService;

    public void setCursedGearService(final CursedGearService cursedGearService) {
        this.cursedGearService = cursedGearService;
    }

    /**
     * B54 — Átok-törés ({@code uncurse} rituálé-típus): a rituálézó VISELT páncéljáról és
     * a két kezében tartott tárgyról leszedi az Első Csend átkát — a levételi zár és a
     * bónusz megszűnik, a tárgy megmarad. A játékos saját régió-szálán futunk.
     */
    /**
     * E25 — Boszorkánymester pakt-ceremónia: egyszeri, kaszt-zárt, NEM halmozható
     * +20% max Lélekerő; ára egy ritka unique anyag (pakt.material, alap: az Első
     * Csend Szilánkja) a táskából. A játékos saját régió-szálán futunk.
     */
    private boolean tryPakt(final Player player) {
        final hu.taliann.icesmp.managers.ResourceBonusService bonusRef = resourceBonusService;
        final hu.taliann.icesmp.items.UniqueMaterialFactory uniqueRef = uniqueMaterialFactory;
        if (bonusRef == null || uniqueRef == null) {
            return false;
        }
        if (jobManager != null && jobManager.getPrimaryJob(player) != hu.taliann.icesmp.data.JobType.WARLOCK) {
            player.sendMessage(messageManager.getMessage("ritual-pakt-wrong-job",
                    "<gray>Az oltár hallgat — a paktumot csak Boszorkánymester kötheti meg.</gray>"));
            return false;
        }
        if (bonusRef.hasPakt(player)) {
            player.sendMessage(messageManager.getMessage("ritual-pakt-already",
                    "<gray>A lelkeden már ott a pecsét — a Kárhozat nem alkuszik kétszer.</gray>"));
            return false;
        }
        final String materialId = configManager.getString("pakt.material", "elso_csend_szilankja");
        final org.bukkit.inventory.ItemStack[] contents = player.getInventory().getContents();
        for (final org.bukkit.inventory.ItemStack stack : contents) {
            if (stack != null && materialId.equals(uniqueRef.idOf(stack))) {
                stack.setAmount(stack.getAmount() - 1);
                bonusRef.sealPakt(player);
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_WITHER_SPAWN, 0.5F, 1.6F);
                player.sendMessage(messageManager.getMessage("ritual-pakt-sealed",
                        "<dark_purple>☠ A paktum megköttetett — a Lélekerő-medred tartósan kitágult. Az ár már nem a tiéd.</dark_purple>"));
                return true;
            }
        }
        player.sendMessage(messageManager.getMessage("ritual-pakt-missing",
                "<gray>A paktumhoz a(z) {material} kell a táskádban.</gray>",
                java.util.Map.of("material", uniqueRef.displayName(materialId))));
        return false;
    }

    private boolean tryUncurse(final Player player) {
        final CursedGearService serviceRef = cursedGearService;
        if (serviceRef == null) {
            return false;
        }
        boolean broken = false;
        final org.bukkit.inventory.ItemStack[] armor = player.getInventory().getArmorContents();
        for (final org.bukkit.inventory.ItemStack piece : armor) {
            broken |= serviceRef.breakCurse(piece);
        }
        player.getInventory().setArmorContents(armor);
        broken |= serviceRef.breakCurse(player.getInventory().getItemInMainHand());
        broken |= serviceRef.breakCurse(player.getInventory().getItemInOffHand());
        if (!broken) {
            player.sendMessage(messageManager.getMessage(
                    "ritual-uncurse-nothing",
                    "<gray>Nincs rajtad átkozott tárgy — az oltárnak nincs mit megtörnie.</gray>"
            ));
            return false;
        }
        player.sendMessage(messageManager.getMessage(
                "ritual-uncurse-success",
                "<gold>Az oltár megtörte az átkot — az Első Csend elengedett. A tárgy a tiéd marad, de már nem köt.</gold>"
        ));
        return true;
    }

    /** Cleanse: wipes the sinner mark and sin counter (a sealed dark pact blocks it). */
    private boolean tryCleanse(final Player player) {
        if (sinManager.getSinCount(player) <= 0 && !sinManager.isSinner(player)) {
            player.sendMessage(messageManager.getMessage(
                    "ritual-cleanse-nothing",
                    "<gray>Nincs mit feloldoznod — a lelked már tiszta.</gray>"
            ));
            return false;
        }
        if (!sinManager.clearSinner(player)) {
            player.sendMessage(messageManager.getMessage(
                    "ritual-cleanse-blocked",
                    "<red>A sötét paktum köt — ezt az oltár nem oldhatja fel, csak a vezeklés útja.</red>"
            ));
            return false;
        }
        sinManager.resetSinCount(player);
        player.sendMessage(messageManager.getMessage(
                "ritual-cleanse-success",
                "<gold>Az oltár feloldozott — bűneid lemosattak, a fejvadászok lekerülnek a nyomodról.</gold>"
        ));
        return true;
    }

    /** Buff: applies the configured potion effects for their durations. */
    private boolean tryBuff(final Player player, final ConfigurationSection ritual) {
        final List<PotionEffect> effects = parseEffects(ritual.getStringList("effects"));
        if (effects.isEmpty()) {
            return false;
        }
        for (final PotionEffect effect : effects) {
            player.addPotionEffect(effect);
        }
        player.sendMessage(messageManager.getMessage(
                "ritual-buff-success",
                "<gold>Az oltár áldása átjár — a rituálé ereje végigfut a testeden!</gold>"
        ));
        return true;
    }

    /**
     * Home: teleports the ritualist to their faction's capital (a "hearthstone" altar).
     * A teleport aszinkron — az áldozat és a cooldown csak a SIKERES megérkezés után rögzül,
     * különben egy elbukó/cancelelt teleport ingyen vinné el a hozzávalókat és az 5 percet.
     */
    private void performHomeRitual(final Player player, final String ritualId,
                                   final Map<Material, Integer> sacrifices, final long cooldownSeconds) {
        final FactionType faction = factionManager.getFaction(player.getUniqueId());
        final Territory capital = faction == null ? null : territoryManager.getCapital(faction);
        final World world = capital == null ? null : Bukkit.getWorld(capital.world());
        if (capital == null || world == null) {
            player.sendMessage(messageManager.getMessage(
                    "ritual-home-no-capital",
                    "<red>A frakciódnak nincs fővárosa, ahová hazatérhetnél.</red>"
            ));
            return;
        }
        if (!homeInFlight.add(player.getUniqueId())) {
            return;
        }
        final float yaw = player.getLocation().getYaw();
        final float pitch = player.getLocation().getPitch();
        // The highest-block lookup reads the capital's chunk — it must run on the DESTINATION region's
        // thread (Folia), not the altar's. Hop there, resolve the safe Y, then teleportAsync.
        plugin.getServer().getRegionScheduler().run(plugin, world, capital.x() >> 4, capital.z() >> 4, task -> {
            final int y = world.getHighestBlockYAt(capital.x(), capital.z()) + 1;
            player.teleportAsync(new Location(world, capital.x() + 0.5D, y, capital.z() + 0.5D, yaw, pitch))
                    .whenComplete((success, failure) -> player.getScheduler().run(plugin, done -> {
                        homeInFlight.remove(player.getUniqueId());
                        if (failure != null || success == null || !success) {
                            player.sendMessage(messageManager.getMessage(
                                    "ritual-home-failed",
                                    "<red>Az oltár fénye kihunyt — a hazatérés nem sikerült, az áldozatod megmaradt.</red>"
                            ));
                            return;
                        }
                        AdvancementService.award(player, "first_ritual");
                        if (!consume(player, sacrifices)) {
                            plugin.getLogger().severe("Rituálé-áldozat nem fogyott el: " + ritualId
                                    + " (" + player.getName() + ")");
                        }
                        if (cooldownSeconds > 0L) {
                            cooldowns.computeIfAbsent(player.getUniqueId(), key -> new ConcurrentHashMap<>())
                                    .put(ritualId, System.currentTimeMillis() + cooldownSeconds * 1000L);
                        }
                        player.sendMessage(messageManager.getMessage(
                                "ritual-home-success",
                                "<gold>Az oltár fénye hazaröpít a fővárosodba.</gold>"
                        ));
                        playSuccessEffect(player, "home");
                    }, () -> homeInFlight.remove(player.getUniqueId())));
        });
    }

    /** Validates every "dx,dy,dz:MATERIAL" offset against the blocks around the core. */
    private boolean matchesStructure(final Block core, final List<String> structure) {
        if (structure == null || structure.isEmpty()) {
            return true;
        }
        for (final String token : structure) {
            final String[] halves = token.split(":", 2);
            if (halves.length < 2) {
                continue;
            }
            final String[] offset = halves[0].split(",");
            if (offset.length < 3) {
                continue;
            }
            final Material expected = Material.matchMaterial(halves[1].trim().toUpperCase(Locale.ROOT));
            if (expected == null) {
                continue;
            }
            try {
                final int dx = Integer.parseInt(offset[0].trim());
                final int dy = Integer.parseInt(offset[1].trim());
                final int dz = Integer.parseInt(offset[2].trim());
                if (core.getRelative(dx, dy, dz).getType() != expected) {
                    return false;
                }
            } catch (final NumberFormatException ignored) {
                // Skip malformed offsets rather than failing the whole check on an admin typo.
            }
        }
        return true;
    }

    private void playSuccessEffect(final Player player, final String type) {
        final Particle particle = switch (type) {
            case "cleanse" -> Particle.END_ROD;
            case "home" -> Particle.PORTAL;
            case "buff" -> Particle.ENCHANT;
            default -> Particle.SOUL_FIRE_FLAME;
        };
        hu.taliann.icesmp.utils.ParticleUtil.spawn(player.getWorld(), particle, player.getLocation().add(0.0D, 1.0D, 0.0D), 80, 0.5D, 1.0D, 0.5D, 0.05D);
        hu.taliann.icesmp.utils.ParticleUtil.spawn(player.getWorld(), Particle.FLASH, player.getLocation().add(0.0D, 1.0D, 0.0D), 1);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0F, 0.6F);
    }

    private long remainingCooldown(final Player player, final String ritualId) {
        final Map<String, Long> perRitual = cooldowns.get(player.getUniqueId());
        if (perRitual == null) {
            return 0L;
        }
        final Long expiry = perRitual.get(ritualId);
        return expiry == null ? 0L : Math.max(0L, expiry - System.currentTimeMillis());
    }

    private List<PotionEffect> parseEffects(final List<String> raw) {
        final List<PotionEffect> effects = new ArrayList<>();
        for (final String token : raw) {
            final String[] parts = token.split(":");
            if (parts.length < 3) {
                continue;
            }
            final PotionEffectType type = PotionEffectType.getByName(parts[0].trim().toUpperCase(Locale.ROOT));
            if (type == null) {
                continue;
            }
            try {
                final int ticks = Math.max(1, Integer.parseInt(parts[1].trim()));
                final int amplifier = Math.max(0, Integer.parseInt(parts[2].trim()));
                effects.add(new PotionEffect(type, ticks, amplifier, true, true, true));
            } catch (final NumberFormatException ignored) {
                // Skip malformed effect tokens; a warning is unnecessary for admin-authored config.
            }
        }
        return effects;
    }

    private Map<Material, Integer> parseSacrifices(final List<String> raw) {
        final Map<Material, Integer> sacrifices = new HashMap<>();
        for (final String token : raw) {
            final String[] parts = token.split(":", 2);
            final Material material = Material.matchMaterial(parts[0].trim().toUpperCase(Locale.ROOT));
            if (material == null) {
                continue;
            }

            int amount = 1;
            if (parts.length > 1) {
                try {
                    amount = Math.max(1, Integer.parseInt(parts[1].trim()));
                } catch (final NumberFormatException ignored) {
                    amount = 1;
                }
            }
            sacrifices.merge(material, amount, Integer::sum);
        }
        return sacrifices;
    }

    private boolean hasAll(final Player player, final Map<Material, Integer> sacrifices) {
        for (final Map.Entry<Material, Integer> entry : sacrifices.entrySet()) {
            // Ugyanaz a predikátum, mint a fogyasztásban: az Inventory#contains típus szerint
            // számolt, a removeItem viszont meta-egyezést kért — a nevesített/bélyegzett tárgy
            // így fedezte az áldozatot, de nem fogyott el, és a rituálé INGYEN lefutott.
            if (hu.taliann.icesmp.utils.PlainIngredients.count(
                    player, entry.getKey(), uniqueMaterialFactory) < entry.getValue()) {
                return false;
            }
        }
        // Üres sacrifice-lista = nincs áldozat-követelmény (pl. pakt_oltar — a pakt a
        // SAJÁT anyag-költségét a tryPakt-ban szedi be). A korábbi !isEmpty() az ilyen
        // rituálékat NÉMÁN letiltotta ("ritual-missing-sacrifice" hibával).
        return true;
    }

    /**
     * Az áldozatok atomikus fogyasztása.
     *
     * @return true, ha MINDEN áldozat elfogyott; false esetén a hatás már lefutott, ezért a
     *         hívónak legalább naplóznia kell — enélkül a rituálé költség nélkül ismételhető
     */
    private boolean consume(final Player player, final Map<Material, Integer> sacrifices) {
        boolean allConsumed = true;
        for (final Map.Entry<Material, Integer> entry : sacrifices.entrySet()) {
            if (!hu.taliann.icesmp.utils.PlainIngredients.consume(
                    player, entry.getKey(), entry.getValue(), uniqueMaterialFactory)) {
                allConsumed = false;
            }
        }
        return allConsumed;
    }
}
