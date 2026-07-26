package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.data.ProfessionSpecializationType;
import hu.taliann.icesmp.data.ProfessionType;
import hu.taliann.icesmp.data.SpecializationType;
import hu.taliann.icesmp.gui.CharacterMenuContext;
import hu.taliann.icesmp.gui.JobGUI;
import hu.taliann.icesmp.gui.ProfessionGUI;
import hu.taliann.icesmp.gui.ProfessionHolder;
import hu.taliann.icesmp.gui.ProfileGUI;
import hu.taliann.icesmp.gui.ProfileHolder;
import hu.taliann.icesmp.gui.SkillTreeGUI;
import hu.taliann.icesmp.gui.SpecGUI;
import hu.taliann.icesmp.gui.SpecHolder;
import hu.taliann.icesmp.gui.TalentGUI;
import hu.taliann.icesmp.gui.TalentHolder;
import net.kyori.adventure.text.Component;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

import java.util.Map;

/**
 * Single listener for the character menu hub and its sub-menus (profile, spec,
 * profession, talent). Routes clicks to the matching menu actions and re-renders
 * after a change. Job and skill-tree menus keep their own listeners.
 */
public final class CharacterGUIListener implements Listener {

    private final CharacterMenuContext ctx;

    public CharacterGUIListener(final CharacterMenuContext ctx) {
        this.ctx = ctx;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(final InventoryClickEvent event) {
        final InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof ProfileHolder || holder instanceof SpecHolder
                || holder instanceof ProfessionHolder || holder instanceof TalentHolder)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        final int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) {
            return;
        }

        if (holder instanceof ProfileHolder profileHolder) {
            if (profileHolder.getOwnerUuid().equals(player.getUniqueId())) {
                handleProfile(player, slot);
            }
        } else if (holder instanceof SpecHolder specHolder) {
            if (specHolder.getOwnerUuid().equals(player.getUniqueId())) {
                handleSpec(player, slot);
            }
        } else if (holder instanceof ProfessionHolder professionHolder) {
            if (professionHolder.getOwnerUuid().equals(player.getUniqueId())) {
                handleProfession(player, slot);
            }
        } else if (holder instanceof TalentHolder talentHolder) {
            if (talentHolder.getOwnerUuid().equals(player.getUniqueId())) {
                handleTalent(player, slot);
            }
        }
    }

    private void handleProfile(final Player player, final int slot) {
        switch (slot) {
            case ProfileGUI.JOB_SLOT -> {
                click(player);
                JobGUI.openJobMenu(player, ctx.jobManager(), ctx.catalystItemFactory(), ctx.messageManager());
            }
            case ProfileGUI.SPEC_SLOT -> {
                click(player);
                SpecGUI.open(player, ctx);
            }
            case ProfileGUI.PROFESSION_SLOT -> {
                click(player);
                ProfessionGUI.open(player, ctx);
            }
            case ProfileGUI.TALENT_SLOT -> {
                click(player);
                TalentGUI.open(player, ctx);
            }
            case ProfileGUI.SKILLTREE_SLOT -> {
                click(player);
                SkillTreeGUI.open(player, ctx.jobManager(), ctx.specializationManager(),
                        ctx.spellRegistry(), ctx.configManager(), ctx.messageManager());
            }
            case ProfileGUI.MENU_SLOT -> {
                click(player);
                player.performCommand("menu");
            }
            case ProfileGUI.CLOSE_SLOT -> player.closeInventory();
            default -> {
                // Head and economy panes are display-only.
            }
        }
    }

    private void handleSpec(final Player player, final int slot) {
        if (slot == SpecGUI.getBackSlot()) {
            click(player);
            ProfileGUI.open(player, ctx);
            return;
        }
        if (slot == SpecGUI.getRespecClassSlot()) {
            respec(player, true);
            return;
        }
        if (slot == SpecGUI.getRespecProfSlot()) {
            respec(player, false);
            return;
        }

        final SpecializationType classSpec = SpecGUI.resolveClassSpec(player, ctx, slot);
        if (classSpec != null) {
            if (ctx.specializationManager().selectClassSpecialization(player, classSpec)) {
                success(player, ctx.messageManager().getMessage("spec-choose-success", "&aSpecializáció kiválasztva:")
                        .append(Component.space()).append(classSpec.getDisplayName()));
            } else {
                fail(player, ctx.messageManager().getComponent("spec-choose-failed",
                        "&cNem választhatod ezt a specializációt (kaszt, szint, frakció vagy bűnös feltétel hiányzik)."));
            }
            SpecGUI.open(player, ctx);
            return;
        }

        final ProfessionSpecializationType profSpec = SpecGUI.resolveProfSpec(player, ctx, slot);
        if (profSpec != null) {
            if (ctx.specializationManager().selectProfessionSpecialization(player, profSpec)) {
                success(player, ctx.messageManager().getMessage("spec-choose-success", "&aSpecializáció kiválasztva:")
                        .append(Component.space()).append(profSpec.getDisplayName()));
            } else {
                fail(player, ctx.messageManager().getComponent("spec-choose-failed-profession",
                        "&cNem választhatod ezt a specializációt (szakma vagy szint feltétel hiányzik)."));
            }
            SpecGUI.open(player, ctx);
        }
    }

    private void handleProfession(final Player player, final int slot) {
        if (slot == ProfessionGUI.getBackSlot()) {
            click(player);
            ProfileGUI.open(player, ctx);
            return;
        }

        final ProfessionType profession = ProfessionGUI.resolvePrimary(slot);
        if (profession == null) {
            return;
        }

        if (ctx.professionManager().selectProfession(player, profession)) {
            success(player, ctx.messageManager().getMessage("profession-gui-learned", "&aSzakma megtanulva:")
                    .append(Component.space()).append(profession.getDisplayName()));
        } else {
            fail(player, ctx.messageManager().getComponent("profession-gui-cannot",
                    "&cEzt a szakmát most nem tanulhatod — ebben a kategóriában már betöltötted a helyed."));
        }
        ProfessionGUI.open(player, ctx);
    }

    private void handleTalent(final Player player, final int slot) {
        if (slot == TalentGUI.getBackSlot()) {
            click(player);
            ProfileGUI.open(player, ctx);
            return;
        }

        final TalentGUI.Node node = TalentGUI.resolveTalent(player, ctx, slot);
        if (node == null) {
            return;
        }

        if (ctx.talentManager().spendPoint(player, node.classPool(), node.id())) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.4F);
            player.sendMessage(ctx.messageManager().getMessage(
                    "talent-spend-success",
                    "&aTalent fejlesztve: &e{talent} &7(rang: &f{rank}&7) | Maradék pont: &f{points}",
                    Map.of(
                            "talent", talentDisplayName(ctx, node),
                            "rank", String.valueOf(ctx.talentManager().getRank(player, node.classPool(), node.id())),
                            "points", String.valueOf(ctx.talentManager().getAvailablePoints(player, node.classPool()))
                    )));
        } else {
            fail(player, ctx.messageManager().getComponent("talent-spend-failed",
                    "&cNem sikerült a pont elköltése (zárolt talent, nincs pont, vagy max rang)."));
        }
        TalentGUI.open(player, ctx);
    }

    /** Respecs the class or profession specialization for the faction-currency cost. */
    private void respec(final Player player, final boolean classPool) {
        final hu.taliann.icesmp.managers.RespecService.Outcome outcome =
                ctx.respecService().respec(player, classPool);
        switch (outcome.status()) {
            case NOTHING_TO_RESPEC -> {
                fail(player, ctx.messageManager().getComponent("spec-respec-nothing",
                        "&cNincs mit visszaváltani: nincs ilyen specializációd."));
                return;
            }
            case INSUFFICIENT_FUNDS -> {
                fail(player, ctx.messageManager().getComponent(
                        "spec-respec-insufficient",
                        "&cA visszaváltás ára &f%s %s&c, de csak &f%s&c van a bankodban.",
                        ctx.currencyManager().formatBalance(outcome.cost()),
                        outcome.currency().getDisplayName(),
                        ctx.currencyManager().formatBalance(
                                ctx.currencyManager().getBalance(player, outcome.currency()))));
                return;
            }
            case OK -> {
                player.playSound(player.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 1.0F, 1.0F);
                player.sendMessage(ctx.messageManager().getComponent(
                        "spec-respec-success",
                        "&aSpecializáció visszaváltva &7(ár: &f%s %s&7, visszakapott talentpont: &f%s&7)&a.",
                        ctx.currencyManager().formatBalance(outcome.cost()),
                        outcome.currency().getDisplayName(),
                        outcome.refundedTalentPoints()));
                SpecGUI.open(player, ctx);
            }
        }
    }

    private void click(final Player player) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0F, 1.0F);
    }

    private void success(final Player player, final Component message) {
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.0F);
        player.sendMessage(message);
    }

    private void fail(final Player player, final Component message) {
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
        player.sendMessage(message);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(final InventoryDragEvent event) {
        final InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof ProfileHolder || holder instanceof SpecHolder
                || holder instanceof ProfessionHolder || holder instanceof TalentHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(final InventoryCloseEvent event) {
        final InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof ProfileHolder profileHolder) {
            profileHolder.setInventory(null);
        } else if (holder instanceof SpecHolder specHolder) {
            specHolder.setInventory(null);
        } else if (holder instanceof ProfessionHolder professionHolder) {
            professionHolder.setInventory(null);
        } else if (holder instanceof TalentHolder talentHolder) {
            talentHolder.setInventory(null);
        }
    }

    /** A talent MAGYAR neve a definícióból — a node.id() belső azonosító, nem játékos-szöveg. */
    private static String talentDisplayName(final hu.taliann.icesmp.gui.CharacterMenuContext ctx,
                                            final hu.taliann.icesmp.gui.TalentGUI.Node node) {
        final org.bukkit.configuration.ConfigurationSection definitions =
                ctx.talentManager().getDefinitions(node.classPool());
        final org.bukkit.configuration.ConfigurationSection talent =
                definitions == null ? null : definitions.getConfigurationSection(node.id());
        return talent == null ? node.id() : talent.getString("display-name", node.id());
    }
}
