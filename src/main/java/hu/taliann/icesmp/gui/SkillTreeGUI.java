package hu.taliann.icesmp.gui;

import hu.taliann.icesmp.data.JobType;
import hu.taliann.icesmp.data.SpecializationType;
import hu.taliann.icesmp.classspec.application.ClassProgressView;
import hu.taliann.icesmp.classspec.domain.CapstoneStatus;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.FactionManager;
import hu.taliann.icesmp.managers.JobManager;
import hu.taliann.icesmp.managers.SpecializationManager;
import hu.taliann.icesmp.managers.SpellRegistry;
import hu.taliann.icesmp.spells.Spell;
import hu.taliann.icesmp.utils.MessageManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Skill tree view: a read-only board showing the
 * player's primary class spell unlocks and the chosen specialization's spells,
 * ordered by unlock level — unlocked spells glow, locked ones show their
 * required level.
 */
@SuppressWarnings("deprecation")
public final class SkillTreeGUI {

    private static final int SIZE = 54;
    private static final int BACK_SLOT = 49;
    private static final int MAX_ENTRIES = 45;

    private SkillTreeGUI() {
    }

    public static int getBackSlot() {
        return BACK_SLOT;
    }

    public static void open(final Player viewer, final JobManager jobManager,
                            final SpecializationManager specializationManager, final SpellRegistry spellRegistry,
                            final ConfigManager configManager, final FactionManager factionManager,
                            final MessageManager messageManager) {
        final hu.taliann.icesmp.data.FactionType faction = ClassUiAssets.faction(viewer, factionManager);
        final Component title = ClassUiAssets.title(ClassUiAssets.Surface.SKILL_TREE, faction,
                messageManager.getComponent("messages.skilltree-title", "&5» Képesség-fa «"));
        final SkillTreeHolder holder = new SkillTreeHolder(viewer.getUniqueId());
        final Inventory inventory = Bukkit.createInventory(holder, SIZE, title);
        holder.setInventory(inventory);
        ClassUiAssets.fill(inventory, faction);

        final List<ItemStack> entries = buildEntries(viewer, jobManager, specializationManager, spellRegistry,
                configManager, messageManager);
        for (int slot = 0; slot < Math.min(entries.size(), MAX_ENTRIES); slot++) {
            inventory.setItem(slot, entries.get(slot));
        }

        inventory.setItem(BACK_SLOT, createBackButton(messageManager));
        viewer.openInventory(inventory);
    }

    private static List<ItemStack> buildEntries(final Player viewer, final JobManager jobManager,
                                                final SpecializationManager specializationManager,
                                                final SpellRegistry spellRegistry, final ConfigManager configManager,
                                                final MessageManager messageManager) {
        final List<ItemStack> entries = new ArrayList<>();
        final JobType primaryJob = jobManager.getPrimaryJob(viewer);
        if (primaryJob == null) {
            final ItemStack noClass = new ItemStack(Material.BARRIER);
            final ItemMeta meta = noClass.getItemMeta();
            meta.displayName(messageManager.getComponent("messages.skilltree-no-class", "&cElőbb válassz kasztot!"));
            noClass.setItemMeta(meta);
            entries.add(noClass);
            return entries;
        }

        appendUnlockSection(entries, viewer, "classes." + primaryJob.getId() + ".spell-unlocks",
                false, "", CapstoneStatus.LOCKED, jobManager, spellRegistry, configManager, messageManager);

        final SpecializationType spec = specializationManager.getClassSpecialization(viewer);
        if (spec != null) {
            final ClassProgressView view = specializationManager.classProgressView(viewer);
            final CapstoneStatus capstoneStatus = view.activeSlot().map(view::loadout)
                    .map(ClassProgressView.LoadoutView::capstoneStatus).orElse(CapstoneStatus.LOCKED);
            appendUnlockSection(entries, viewer, "specializations." + spec.getId() + ".spell-unlocks",
                    true, configManager.getString(
                            "specializations." + spec.getId() + ".capstone-spell", ""),
                    capstoneStatus, jobManager, spellRegistry, configManager, messageManager);
        }

        return entries;
    }

    private static void appendUnlockSection(final List<ItemStack> entries, final Player viewer, final String path,
                                            final boolean specSection, final String capstoneSpell,
                                            final CapstoneStatus capstoneStatus, final JobManager jobManager,
                                            final SpellRegistry spellRegistry, final ConfigManager configManager,
                                            final MessageManager messageManager) {
        if (configManager.getConfiguration() == null) {
            return;
        }

        final ConfigurationSection section = configManager.getConfiguration().getConfigurationSection(path);
        if (section == null) {
            return;
        }

        final List<Map.Entry<String, Integer>> sorted = section.getKeys(false).stream()
                .map(spellId -> Map.entry(spellId, section.getInt(spellId, Integer.MAX_VALUE)))
                .sorted(Comparator.comparingInt(Map.Entry::getValue))
                .toList();

        final int playerLevel = jobManager.getPrimaryLevel(viewer);
        for (final Map.Entry<String, Integer> entry : sorted) {
            entries.add(createSpellNode(viewer, entry.getKey(), entry.getValue(), playerLevel, specSection,
                    capstoneSpell, capstoneStatus, jobManager, spellRegistry, messageManager));
        }
    }

    private static ItemStack createSpellNode(final Player viewer, final String spellId, final int requiredLevel,
                                             final int playerLevel, final boolean specSection,
                                             final String capstoneSpell, final CapstoneStatus capstoneStatus,
                                             final JobManager jobManager, final SpellRegistry spellRegistry,
                                             final MessageManager messageManager) {
        final boolean unlocked = jobManager.hasUnlockedSpell(viewer, spellId);
        final Spell spell = spellRegistry.getById(spellId);
        final String spellName = spell == null ? spellId : spell.getName();

        final Component name = messageManager.getComponent(
                unlocked ? "messages.skilltree-node-unlocked" : "messages.skilltree-node-locked",
                unlocked ? "&a%s" : "&7%s",
                spellName
        );

        final List<Component> lore = new ArrayList<>();
        final boolean trialLocked = !unlocked && spellId.equalsIgnoreCase(capstoneSpell)
                && capstoneStatus != CapstoneStatus.COMPLETED;
        lore.add(trialLocked
                ? messageManager.getComponent("messages.skilltree-node-requirement-capstone",
                        "&7Feloldási feltétel: &fSpecializációs végső próba")
                : messageManager.getComponent("messages.skilltree-node-level",
                        "&7Szükséges szint: &f%s", requiredLevel));
        if (spell != null) {
            for (final String line : spell.describe().stream().limit(3).toList()) {
                lore.add(Component.text(line, net.kyori.adventure.text.format.NamedTextColor.GRAY));
            }
        }
        if (specSection) {
            lore.add(messageManager.getComponent("messages.skilltree-node-spec", "&5Specializációs képesség"));
        }
        if (unlocked) {
            lore.add(messageManager.getComponent("messages.skilltree-node-state-unlocked", "&a✔ Feloldva"));
        } else if (trialLocked) {
            lore.add(messageManager.getComponent("messages.skilltree-node-state-capstone",
                    "&c✖ A specializáció végső próbája szükséges"));
            lore.add(messageManager.getComponent("messages.skilltree-node-capstone-help",
                    "&7A próbát az 50. szinten, a Küldetéstáblán találod."));
        } else if (playerLevel < requiredLevel) {
            lore.add(messageManager.getComponent("messages.skilltree-node-state-locked", "&c✖ Zárolva"));
        } else {
            lore.add(messageManager.getComponent("messages.skilltree-node-state-sync",
                    "&e◌ A Profile v2 feloldás frissítése folyamatban van"));
        }

        // Unlocked nodes glow; the flag set matches GuiUtil.icon exactly, so it is a render-equivalent build.
        return GuiUtil.icon(unlocked ? Material.ENCHANTED_BOOK : Material.BOOK, name, lore, unlocked);
    }

    private static ItemStack createBackButton(final MessageManager messageManager) {
        final ItemStack itemStack = new ItemStack(Material.ARROW);
        final ItemMeta meta = itemStack.getItemMeta();
        meta.displayName(messageManager.getComponent("messages.skilltree-back", "&cVissza a kasztokhoz"));
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ATTRIBUTES);
        itemStack.setItemMeta(meta);
        return itemStack;
    }
}
