package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Ritual altars (ideas.md "Rituálé-oltárok"): relics are not crafted but
 * summoned at a configured altar block by sacrificing items. Right-clicking the
 * matching altar block with the required sacrifices in the inventory consumes
 * them and grants the relic (the RelicManager enforces the one-of-each
 * singleton rule, so a relic already owned by an active player cannot be made).
 * Definitions live under 'rituals.&lt;relicId&gt;' in config.yml.
 */
public final class RitualManager {

    private final ConfigManager configManager;
    private final RelicManager relicManager;
    private final MessageManager messageManager;

    public RitualManager(final ConfigManager configManager, final RelicManager relicManager,
                         final MessageManager messageManager) {
        this.configManager = configManager;
        this.relicManager = relicManager;
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

        for (final String relicId : ritualsSection.getKeys(false)) {
            final ConfigurationSection ritual = ritualsSection.getConfigurationSection(relicId);
            if (ritual == null) {
                continue;
            }

            final Material altar = Material.matchMaterial(ritual.getString("altar-block", ""));
            if (altar != altarMaterial) {
                continue;
            }

            performRitual(player, relicId, ritual);
            return true;
        }

        return false;
    }

    private void performRitual(final Player player, final String relicId, final ConfigurationSection ritual) {
        final Map<Material, Integer> sacrifices = parseSacrifices(ritual.getStringList("sacrifice"));
        if (!hasAll(player, sacrifices)) {
            player.sendMessage(messageManager.getMessage(
                    "ritual-missing-sacrifice",
                    "<red>Hiányoznak az áldozati tárgyak a rituáléhoz.</red>"
            ));
            return;
        }

        // The RelicManager singleton rule rejects the give if the relic already has an active owner.
        if (!relicManager.giveRelic(player, relicId, 1)) {
            player.sendMessage(messageManager.getMessage(
                    "ritual-relic-unavailable",
                    "<red>A relikvia jelenleg nem idézhető meg (már van élő tulajdonosa).</red>"
            ));
            return;
        }

        consume(player, sacrifices);
        player.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, player.getLocation().add(0.0D, 1.0D, 0.0D), 80, 0.5D, 1.0D, 0.5D, 0.05D);
        player.getWorld().spawnParticle(Particle.FLASH, player.getLocation().add(0.0D, 1.0D, 0.0D), 2);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0F, 0.6F);
        player.sendMessage(messageManager.getMessage(
                "ritual-success",
                "<gold>A rituálé sikeres — a relikvia testet öltött a kezedben!</gold>"
        ));
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
