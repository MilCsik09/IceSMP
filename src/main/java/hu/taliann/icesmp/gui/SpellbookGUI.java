package hu.taliann.icesmp.gui;

import static hu.taliann.icesmp.gui.GuiUtil.grey;

import hu.taliann.icesmp.data.JobType;
import hu.taliann.icesmp.data.SpecializationType;
import hu.taliann.icesmp.listeners.AbilityCatalystListener;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.JobManager;
import hu.taliann.icesmp.managers.ResourceManager;
import hu.taliann.icesmp.managers.SpecializationManager;
import hu.taliann.icesmp.managers.SpellFavoritesManager;
import hu.taliann.icesmp.managers.SpellMasteryManager;
import hu.taliann.icesmp.managers.SpellRegistry;
import hu.taliann.icesmp.spells.Spell;
import hu.taliann.icesmp.spells.SpellCostType;
import hu.taliann.icesmp.utils.MessageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Spellbook: a clickable, paginated overview of the player's class + specialization
 * spells. Unlocked spells are bright, selectable icons (the active one glows and is
 * marked); not-yet-unlocked spells are dimmed with their required level. Every entry
 * lists a precise description — effect, cost, damage, cooldown, mastery — so players
 * can see at a glance what each spell does and pick one without cycling.
 */
public final class SpellbookGUI {

    private static final int SIZE = 54;
    private static final int PER_PAGE = 45;
    public static final int PREV_SLOT = 45;
    public static final int FILTER_SLOT = 47;
    public static final int PAGE_INFO_SLOT = 49;
    public static final int NEXT_SLOT = 53;

    private SpellbookGUI() {
    }

    /** A single spellbook row: the spell, its unlock level, and whether it is unlocked. */
    public record Entry(Spell spell, int requiredLevel, boolean unlocked) {
    }

    public static void open(final Player viewer, final AbilityCatalystListener catalyst,
                            final JobManager jobManager, final SpecializationManager specializationManager,
                            final SpellRegistry spellRegistry, final SpellMasteryManager masteryManager,
                            final ConfigManager configManager, final MessageManager messageManager,
                            final ResourceManager resourceManager, final SpellFavoritesManager favoritesManager,
                            final int page, final boolean onlyUnlocked) {
        List<Entry> entries = collectEntries(viewer, jobManager, specializationManager, spellRegistry, configManager);
        if (onlyUnlocked) {
            entries = entries.stream().filter(Entry::unlocked).toList();
        }
        final String selectedId = catalyst.getSelectedSpellId(viewer);

        final int totalPages = Math.max(1, (int) Math.ceil(entries.size() / (double) PER_PAGE));
        final int safePage = Math.max(0, Math.min(page, totalPages - 1));

        final Component title = messageManager.getComponent("messages.spellbook-title", "&5» Varázskönyv «");
        final SpellbookHolder holder = new SpellbookHolder(viewer.getUniqueId());
        holder.setPage(safePage);
        holder.setOnlyUnlocked(onlyUnlocked);
        final Inventory inventory = Bukkit.createInventory(holder, SIZE, title);
        holder.setInventory(inventory);

        final int start = safePage * PER_PAGE;
        for (int i = 0; i < PER_PAGE && start + i < entries.size(); i++) {
            final Entry entry = entries.get(start + i);
            final boolean selected = entry.unlocked() && entry.spell().getId().equalsIgnoreCase(selectedId);
            inventory.setItem(i, icon(viewer, entry, selected, masteryManager, messageManager, resourceManager, favoritesManager));
            if (entry.unlocked()) {
                holder.mapSlot(i, entry.spell().getId());
            }
        }

        for (int slot = PER_PAGE; slot < SIZE; slot++) {
            inventory.setItem(slot, GuiUtil.filler());
        }
        if (safePage > 0) {
            inventory.setItem(PREV_SLOT, nav(Material.ARROW, "« Előző oldal"));
        }
        if (safePage < totalPages - 1) {
            inventory.setItem(NEXT_SLOT, nav(Material.ARROW, "Következő oldal »"));
        }
        inventory.setItem(FILTER_SLOT, nav(Material.HOPPER,
                "Csak feloldottak: " + (onlyUnlocked ? "BE" : "KI")));
        inventory.setItem(PAGE_INFO_SLOT, nav(Material.PAPER,
                "Oldal " + (safePage + 1) + "/" + totalPages + " — sunyíts + jobb katt a könyvhöz"));

        viewer.openInventory(inventory);
    }

    /** Builds the ordered class-then-spec spell list (each by unlock level) for the player. */
    public static List<Entry> collectEntries(final Player viewer, final JobManager jobManager,
                                              final SpecializationManager specializationManager,
                                              final SpellRegistry spellRegistry, final ConfigManager configManager) {
        final List<Entry> entries = new ArrayList<>();
        if (configManager.getConfiguration() == null) {
            return entries;
        }

        final JobType job = jobManager.getPrimaryJob(viewer);
        if (job != null) {
            addFromSection(entries, viewer, jobManager, spellRegistry,
                    configManager.getConfiguration().getConfigurationSection("classes." + job.getId() + ".spell-unlocks"));
        }

        final SpecializationType spec = specializationManager.getClassSpecialization(viewer);
        if (spec != null) {
            addFromSection(entries, viewer, jobManager, spellRegistry,
                    configManager.getConfiguration().getConfigurationSection("specializations." + spec.getId() + ".spell-unlocks"));
        }

        entries.sort((a, b) -> {
            if (a.unlocked() != b.unlocked()) {
                return a.unlocked() ? -1 : 1; // unlocked (usable) spells first
            }
            return Integer.compare(a.requiredLevel(), b.requiredLevel());
        });
        return entries;
    }

    private static void addFromSection(final List<Entry> entries, final Player viewer, final JobManager jobManager,
                                       final SpellRegistry spellRegistry, final ConfigurationSection section) {
        if (section == null) {
            return;
        }
        for (final String spellId : section.getKeys(false)) {
            final Spell spell = spellRegistry.getById(spellId);
            if (spell == null) {
                continue;
            }
            final int requiredLevel = section.getInt(spellId, Integer.MAX_VALUE);
            entries.add(new Entry(spell, requiredLevel, jobManager.hasUnlockedSpell(viewer, spellId)));
        }
    }

    private static ItemStack icon(final Player viewer, final Entry entry, final boolean selected,
                                  final SpellMasteryManager masteryManager, final MessageManager messageManager,
                                  final ResourceManager resourceManager, final SpellFavoritesManager favoritesManager) {
        final Spell spell = entry.spell();
        final boolean unlocked = entry.unlocked();
        final int rank = masteryManager.getRank(viewer, spell.getId());
        final boolean favorite = unlocked && favoritesManager != null && favoritesManager.isFavorite(viewer, spell.getId());

        final Component name = Component.text(
                (selected ? "▶ " : "") + (favorite ? "★ " : "") + spell.getName() + (rank > 0 ? " ★" + rank : ""),
                unlocked ? ((selected || favorite) ? NamedTextColor.GOLD : NamedTextColor.AQUA) : NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false);

        final List<Component> lore = new ArrayList<>();

        // Effect description: configured spells auto-generate precise numbers; others fall back to messages.
        final List<String> described = spell.describe();
        if (described.isEmpty()) {
            final String desc = messageManager.get("spell." + spell.getId() + ".desc", "");
            if (!desc.isBlank()) {
                lore.add(grey(desc));
            }
        } else {
            for (final String line : described) {
                lore.add(grey(line));
            }
        }

        lore.add(Component.empty());
        if (resourceManager != null && resourceManager.usesResource(spell)) {
            // Hybrid: this spell is paid from the class resource (Mana/Düh/Energia…), which regenerates.
            lore.add(stat("Költség", resourceManager.costOf(spell) + " " + resourceManager.resourceName(viewer)));
        } else if (spell.getCostAmount() > 0) {
            // Thematic cost kept: blood magic (HP), great rituals (XP), or heavy physical effort (hunger).
            lore.add(stat("Költség", spell.getCostAmount() + " " + resourceName(spell.getCostType())));
        }
        lore.add(stat("Cooldown", spell.getCooldown() <= 0 ? "azonnali" : spell.getCooldown() + " mp"));
        if (rank > 0) {
            lore.add(stat("Mesterség", "★" + rank));
        }

        lore.add(Component.empty());
        if (!unlocked) {
            lore.add(Component.text("🔒 Szükséges szint: " + entry.requiredLevel(), NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false));
        } else if (selected) {
            lore.add(Component.text("✔ Kiválasztva", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("Kattints a kiválasztáshoz", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        }
        if (unlocked) {
            if (favorite) {
                lore.add(Component.text("★ Kedvenc — shift-katt: eltávolítás", NamedTextColor.GOLD)
                        .decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(Component.text("Shift-katt: kedvencnek jelölés", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            }
        }

        final Material material = unlocked ? Material.ENCHANTED_BOOK : Material.BOOK;
        return GuiUtil.icon(material, name, lore, selected);
    }

    private static Component stat(final String label, final String value) {
        return Component.text(label + ": ", NamedTextColor.DARK_GRAY)
                .append(Component.text(value, NamedTextColor.WHITE))
                .decoration(TextDecoration.ITALIC, false);
    }

    private static String resourceName(final SpellCostType costType) {
        return switch (costType) {
            case HUNGER -> "éhség";
            case XP -> "XP";
            case HEALTH -> "élet";
        };
    }

    private static ItemStack nav(final Material material, final String label) {
        return GuiUtil.icon(material,
                Component.text(label, NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false),
                List.of());
    }
}
