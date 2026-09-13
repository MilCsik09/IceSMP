package hu.taliann.icesmp.items;

import hu.taliann.icesmp.managers.ConfigManager;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;
import java.util.Objects;

/** Read-only rarity projection that never rolls affixes, adjectives or negative stats. */
public final class RarityPresentationService {

    public record Presentation(String id, String label, String legacyColor,
                               org.bukkit.inventory.ItemRarity vanillaRarity) {
    }

    private final ConfigManager configManager;

    public RarityPresentationService(final ConfigManager configManager) {
        this.configManager = Objects.requireNonNull(configManager, "configManager");
    }

    public Presentation require(final String rawId) {
        final String id = rawId == null ? "" : rawId.trim().toLowerCase(Locale.ROOT);
        final ConfigurationSection root = configManager.getConfiguration() == null ? null
                : configManager.getConfiguration().getConfigurationSection("item-rarity.rarities." + id);
        if (root == null) throw new IllegalArgumentException("ismeretlen rarity presentation: " + rawId);
        final String label = root.getString("name", "").trim();
        final String color = root.getString("color", "").trim();
        if (label.isBlank() || !color.matches("&[0-9a-fA-F]")) {
            throw new IllegalStateException("hibás rarity presentation: " + id);
        }
        return new Presentation(id, label, color, ItemDataFactory.vanillaRarityOf(id));
    }
}
