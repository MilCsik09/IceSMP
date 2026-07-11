package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ritual altars (ideas.md "Rituálé-oltárok"): sneak-right-clicking a configured
 * altar block with the required sacrifices in the inventory consumes them and
 * grants an outcome. The outcome is chosen by the ritual's {@code type}:
 * <ul>
 *   <li>{@code relic} (default): summons the matching relic — the RelicManager
 *       enforces the one-of-each singleton rule.</li>
 *   <li>{@code cleanse}: removes the sinner mark and the sin counter (blocked by
 *       a sealed dark pact — that penance runs its own quest chain).</li>
 *   <li>{@code buff}: applies the configured potion effects for a duration.</li>
 *   <li>{@code heal}: fully restores health and saturation.</li>
 * </ul>
 * Non-relic rituals may set {@code cooldown-seconds} to rate-limit repeats (kept
 * in memory — a per-player convenience limiter, reset on restart). Definitions
 * live under {@code rituals.&lt;id&gt;} in config.
 */
public final class RitualManager {

    private final ConfigManager configManager;
    private final RelicManager relicManager;
    private final SinManager sinManager;
    private final MessageManager messageManager;
    // Per-player, per-ritual cooldown expiry (in-memory; only used by non-relic rituals).
    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();

    public RitualManager(final ConfigManager configManager, final RelicManager relicManager,
                         final SinManager sinManager, final MessageManager messageManager) {
        this.configManager = configManager;
        this.relicManager = relicManager;
        this.sinManager = sinManager;
        this.messageManager = messageManager;
    }

    /**
     * Attempts a ritual at an altar block of the given material.
     *
     * @param player the ritualist
     * @param altarMaterial the block they interacted with
     * @return true if a ritual was matched and handled (success or failure feedback sent)
     */
    public boolean tryRitual(final Player player, final Material altarMaterial) {
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

            final Material altar = Material.matchMaterial(ritual.getString("altar-block", ""));
            if (altar != altarMaterial) {
                continue;
            }

            performRitual(player, ritualId, ritual);
            return true;
        }

        return false;
    }

    private void performRitual(final Player player, final String ritualId, final ConfigurationSection ritual) {
        final String type = ritual.getString("type", "relic").toLowerCase(Locale.ROOT);

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

        // Resolve the outcome first — only consume the sacrifices if it actually succeeds.
        final boolean success = switch (type) {
            case "cleanse" -> tryCleanse(player);
            case "buff" -> tryBuff(player, ritual);
            case "heal" -> tryHeal(player);
            default -> tryRelic(player, ritualId, ritual);
        };
        if (!success) {
            return;
        }

        consume(player, sacrifices);
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

    /** Heal: fully restores health and saturation. */
    private boolean tryHeal(final Player player) {
        final var maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        player.setHealth(maxHealth == null ? player.getHealth() : maxHealth.getValue());
        player.setFoodLevel(20);
        player.setSaturation(20.0F);
        player.setFireTicks(0);
        player.sendMessage(messageManager.getMessage(
                "ritual-heal-success",
                "<gold>Az oltár helyreállító fénye teljesen felüdít.</gold>"
        ));
        return true;
    }

    private void playSuccessEffect(final Player player, final String type) {
        final Particle particle = switch (type) {
            case "cleanse" -> Particle.END_ROD;
            case "heal" -> Particle.HEART;
            case "buff" -> Particle.ENCHANT;
            default -> Particle.SOUL_FIRE_FLAME;
        };
        player.getWorld().spawnParticle(particle, player.getLocation().add(0.0D, 1.0D, 0.0D), 80, 0.5D, 1.0D, 0.5D, 0.05D);
        player.getWorld().spawnParticle(Particle.FLASH, player.getLocation().add(0.0D, 1.0D, 0.0D), 2);
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
        final List<PotionEffect> effects = new java.util.ArrayList<>();
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
            if (!player.getInventory().contains(entry.getKey(), entry.getValue())) {
                return false;
            }
        }
        return !sacrifices.isEmpty();
    }

    private void consume(final Player player, final Map<Material, Integer> sacrifices) {
        for (final Map.Entry<Material, Integer> entry : sacrifices.entrySet()) {
            player.getInventory().removeItem(new ItemStack(entry.getKey(), entry.getValue()));
        }
    }
}
