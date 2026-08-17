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
import hu.taliann.icesmp.pve.EquippedCombatPowerService;
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
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

public final class JobGUIListener implements Listener {

    private record PendingClassPick(JobType job, long at) { }

    private final java.util.concurrent.ConcurrentHashMap<java.util.UUID, PendingClassPick> classConfirmPending =
            new java.util.concurrent.ConcurrentHashMap<>();

    private final JobManager jobManager;
    private final JavaPlugin plugin;
    private final CatalystItemFactory catalystItemFactory;
    private final SpecializationManager specializationManager;
    private final SpellRegistry spellRegistry;
    private final ConfigManager configManager;
    private volatile hu.taliann.icesmp.managers.FactionManager factionManager;
    private final MessageManager messageManager;
    private final CharacterMenuContext menuContext;

    public JobGUIListener(final JavaPlugin plugin, final JobManager jobManager,
                          final CatalystItemFactory catalystItemFactory,
                          final SpecializationManager specializationManager,
                          final SpellRegistry spellRegistry,
                          final ConfigManager configManager,
                          final MessageManager messageManager,
                          final CharacterMenuContext menuContext) {
        this.plugin = plugin;
        this.jobManager = jobManager;
        this.catalystItemFactory = catalystItemFactory;
        this.specializationManager = specializationManager;
        this.spellRegistry = spellRegistry;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.menuContext = menuContext;
    }

    public void setFactionManager(final hu.taliann.icesmp.managers.FactionManager factionManager) {
        this.factionManager = factionManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(final InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof JobGUIHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!holder.getOwnerUuid().equals(player.getUniqueId())) {
            player.closeInventory();
            return;
        }
        if (event.getRawSlot() < 0
                || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;

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
            SkillTreeGUI.open(player, jobManager, specializationManager, spellRegistry,
                    configManager, messageManager);
            return;
        }

        final JobType selectedJob = JobGUI.resolveJobType(event.getRawSlot());
        if (selectedJob == null) return;
        if (selectedJob == JobType.DEATH_KNIGHT
                && configManager.getBoolean("classes.death-knight.dark-only", false)
                && factionManager != null
                && !factionManager.isMember(player.getUniqueId(),
                hu.taliann.icesmp.data.FactionType.DARK)) {
            player.sendMessage(messageManager.getMessage("job-dk-dark-only",
                    "<dark_red>A halál lovagja nem tartozhat az élők királyságaihoz — ezt az utat csak a Kitaszítottak járhatják.</dark_red>"));
            return;
        }

        final long confirmWindowMillis = Math.max(0L,
                configManager.getLong("classes.select-confirm-seconds", 30L)) * 1000L;
        if (confirmWindowMillis > 0L && !jobManager.hasPrimaryJob(player)) {
            final long now = System.currentTimeMillis();
            classConfirmPending.values().removeIf(
                    pending -> now - pending.at() > confirmWindowMillis);
            final PendingClassPick pending = classConfirmPending.get(player.getUniqueId());
            if (pending == null || pending.job() != selectedJob) {
                classConfirmPending.put(player.getUniqueId(),
                        new PendingClassPick(selectedJob, now));
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0F, 0.8F);
                player.sendMessage(messageManager.getMessage("job-select-confirm",
                        "<gold>⚠ A kaszt-választás VÉGLEGES (csak admin fordíthatja vissza). "
                                + "Ha biztos vagy benne, kattints újra a(z) {job} ikonjára {seconds} másodpercen belül.</gold>",
                        Map.of("job", net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                                        .plainText().serialize(selectedJob.getDisplayName()),
                                "seconds", String.valueOf(confirmWindowMillis / 1000L))));
                return;
            }
            classConfirmPending.remove(player.getUniqueId());
        }

        player.closeInventory();
        jobManager.setPrimaryJobV2(player, selectedJob)
                .whenComplete((selected, failure) -> player.getScheduler().run(plugin, task -> {
                    if (failure == null && Boolean.TRUE.equals(selected)) {
                        hu.taliann.icesmp.itemization.EquipmentProficiencyService
                                .reconcileAfterClassChange(player);
                        EquippedCombatPowerService.refreshAfterMutation(player);
                        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP,
                                1.0F, 1.0F);
                        player.sendMessage(messageManager.getComponent(
                                        "messages.job-select-primary-success",
                                        "&aElsődleges kaszt kiválasztva:")
                                .append(Component.space()).append(selectedJob.getDisplayName()));
                        JobGUI.openJobMenu(player, jobManager, catalystItemFactory, messageManager);
                    } else {
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO,
                                1.0F, 1.0F);
                        player.sendMessage(messageManager.getComponent(
                                "messages.job-select-failed",
                                "&cA Profile v2 mentése meghiúsult; a kaszt nem aktiválódott."));
                    }
                }, null));
    }

    private void handleCatalystClaim(final Player player) {
        final JobType primaryJob = jobManager.getPrimaryJob(player);
        if (primaryJob == null) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
            player.sendMessage(messageManager.getComponent(
                    "messages.job-gui-catalyst-no-class",
                    "&cElőbb válassz elsődleges kasztot!"));
            return;
        }
        for (final ItemStack itemStack : player.getInventory().getContents()) {
            if (catalystItemFactory.isPersonalCopyFor(
                    itemStack, player.getUniqueId(), primaryJob)) {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.2F);
                player.sendMessage(messageManager.getMessage(
                        "job-gui-catalyst-already-owned",
                        "&eMár van személyes Lélekkapcsod: &f{catalyst}",
                        Map.of("catalyst",
                                catalystItemFactory.getDisplayNamePlain(primaryJob))));
                return;
            }
        }
        if (player.getInventory().firstEmpty() < 0) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 0.8F);
            player.sendMessage(messageManager.get("soulbond.inventory-full",
                    "&cA Lélekkapocs visszaállításához szabadíts fel egy inventory helyet."));
            return;
        }
        player.getInventory().addItem(
                catalystItemFactory.createCatalyst(primaryJob, player.getUniqueId()));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.4F);
        player.sendMessage(messageManager.getMessage(
                "job-gui-catalyst-claimed",
                "&aLélekkapocs helyreállítva: &e{catalyst}",
                Map.of("catalyst", catalystItemFactory.getDisplayNamePlain(primaryJob))));
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
