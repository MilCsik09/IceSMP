package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.data.JobType;
import hu.taliann.icesmp.gui.CharacterMenuContext;
import hu.taliann.icesmp.gui.JobGUI;
import hu.taliann.icesmp.gui.JobGUIHolder;
import hu.taliann.icesmp.gui.ProfileGUI;
import hu.taliann.icesmp.gui.SkillTreeGUI;
import hu.taliann.icesmp.items.CatalystItemFactory;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.JobManager;
import hu.taliann.icesmp.managers.SpecializationManager;
import hu.taliann.icesmp.managers.SpellRegistry;
import hu.taliann.icesmp.utils.MessageManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public final class JobGUIListener implements Listener {

    private final JobManager jobManager;
    private final CatalystItemFactory catalystItemFactory;
    private final SpecializationManager specializationManager;
    private final SpellRegistry spellRegistry;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final CharacterMenuContext menuContext;

    public JobGUIListener(final JobManager jobManager, final CatalystItemFactory catalystItemFactory,
                          final SpecializationManager specializationManager, final SpellRegistry spellRegistry,
                          final ConfigManager configManager, final MessageManager messageManager,
                          final CharacterMenuContext menuContext) {
        this.jobManager = jobManager;
        this.catalystItemFactory = catalystItemFactory;
        this.specializationManager = specializationManager;
        this.spellRegistry = spellRegistry;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.menuContext = menuContext;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(final InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof JobGUIHolder holder)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!holder.getOwnerUuid().equals(player.getUniqueId())) {
            player.closeInventory();
            return;
        }

        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }

        if (event.getRawSlot() == JobGUI.getBackSlot()) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0F, 1.0F);
            ProfileGUI.open(player, menuContext);
            return;
        }

        if (event.getRawSlot() == JobGUI.getCatalystSlot()) {
            handleCatalystClaim(player);
            return;
        }

        if (event.getRawSlot() == JobGUI.getSkillTreeSlot()) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0F, 1.2F);
            SkillTreeGUI.open(player, jobManager, specializationManager, spellRegistry, configManager, messageManager);
            return;
        }

        final JobType selectedJob = JobGUI.resolveJobType(event.getRawSlot());
        if (selectedJob == null) {
            return;
        }

        if (jobManager.setPrimaryJob(player, selectedJob)) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.0F);
            player.sendMessage(messageManager.getComponent("messages.job-select-primary-success", "&aElsodleges kaszt kivalasztva:").append(Component.space()).append(selectedJob.getDisplayName()));
            JobGUI.openJobMenu(player, jobManager, catalystItemFactory, messageManager);
            return;
        }

        if (jobManager.setSecondaryJob(player, selectedJob)) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.0F);
            player.sendMessage(messageManager.getComponent("messages.job-select-secondary-success", "&aMasodlagos kaszt kivalasztva:").append(Component.space()).append(selectedJob.getDisplayName()));
            JobGUI.openJobMenu(player, jobManager, catalystItemFactory, messageManager);
            return;
        }

        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
        player.sendMessage(messageManager.getComponent("messages.job-select-failed", "&cJelenleg nem valaszthatsz uj kasztot!"));
    }

    private void handleCatalystClaim(final Player player) {
        final JobType primaryJob = jobManager.getPrimaryJob(player);
        if (primaryJob == null) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
            player.sendMessage(messageManager.getComponent("messages.job-gui-catalyst-no-class", "&cElőbb válassz elsődleges kasztot!"));
            return;
        }

        for (final ItemStack itemStack : player.getInventory().getContents()) {
            if (catalystItemFactory.isCatalyst(itemStack)) {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.2F);
                player.sendMessage(messageManager.getMessage(
                        "job-gui-catalyst-already-owned",
                        "&eMár van katalizátorod: &f{catalyst}",
                        Map.of("catalyst", catalystItemFactory.getDisplayNamePlain(primaryJob))
                ));
                return;
            }
        }

        final ItemStack catalyst = catalystItemFactory.createCatalyst(primaryJob);
        final Map<Integer, ItemStack> leftover = player.getInventory().addItem(catalyst);
        if (!leftover.isEmpty()) {
            leftover.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        }

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.4F);
        player.sendMessage(messageManager.getMessage(
                "job-gui-catalyst-claimed",
                "&aKatalizátor átvéve: &e{catalyst}",
                Map.of("catalyst", catalystItemFactory.getDisplayNamePlain(primaryJob))
        ));
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(final InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof JobGUIHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(final InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof JobGUIHolder holder) {
            holder.setInventory(null);
        }
    }
}

