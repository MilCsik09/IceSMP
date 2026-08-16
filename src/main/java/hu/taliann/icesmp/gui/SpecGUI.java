package hu.taliann.icesmp.gui;

import hu.taliann.icesmp.classspec.application.ClassProgressView;
import hu.taliann.icesmp.classspec.application.ClassMechanicView;
import hu.taliann.icesmp.classspec.application.DoctrinePresentation;
import hu.taliann.icesmp.classspec.domain.CapstoneStatus;
import hu.taliann.icesmp.classspec.domain.LoadoutSlot;
import hu.taliann.icesmp.classspec.domain.LoadoutStatus;
import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.data.JobType;
import hu.taliann.icesmp.data.ProfessionSpecializationType;
import hu.taliann.icesmp.data.ProfessionType;
import hu.taliann.icesmp.data.SpecializationType;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.prologue.PrologueContentPolicy;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import static hu.taliann.icesmp.gui.GuiUtil.accent;
import static hu.taliann.icesmp.gui.GuiUtil.grey;
import static hu.taliann.icesmp.gui.GuiUtil.label;

/** First-party class progression surface backed only by the Profile v2 projection. */
public final class SpecGUI {
    private static final int SIZE = 54;
    private static final int[] CLASS_SPEC_SLOTS = {19, 21, 23, 25};
    private static final int[] PROF_SPEC_SLOTS = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
    private static final int[][] DOCTRINE_SLOTS = {{37, 38}, {40, 41}, {43, 44}};
    private static final int[] DOCTRINE_LEVELS = {30, 40, 50};

    private SpecGUI() { }

    public static void open(final Player viewer, final CharacterMenuContext ctx) {
        openClassProgress(viewer, ctx);
    }

    public static void openClassProgress(final Player viewer, final CharacterMenuContext ctx) {
        if (viewer == null || ctx == null) return;
        final SpecHolder holder = new SpecHolder(viewer.getUniqueId(), SpecHolder.Mode.CLASS_PROGRESS);
        final Inventory inventory = createInventory(holder, viewer, ctx, "spec-gui-title",
                "<dark_aqua>» Kasztműhely «</dark_aqua>");
        fillThemed(inventory, faction(viewer, ctx));
        final ClassProgressView view = ctx.specializationManager().classProgressView(viewer);

        inventory.setItem(4, classHeader(viewer, ctx, view));
        inventory.setItem(10, loadoutItem(viewer, ctx, view, LoadoutSlot.FIRST));
        inventory.setItem(16, loadoutItem(viewer, ctx, view, LoadoutSlot.SECOND));
        bindLoadoutSwitch(holder, viewer, ctx, view, LoadoutSlot.FIRST, 10);
        bindLoadoutSwitch(holder, viewer, ctx, view, LoadoutSlot.SECOND, 16);
        inventory.setItem(13, masteryItem(view, activeLoadout(view)));

        final List<SpecializationType> options = classSpecOptions(viewer, ctx);
        for (int index = 0; index < options.size() && index < CLASS_SPEC_SLOTS.length; index++) {
            final SpecializationType specialization = options.get(index);
            final int slot = CLASS_SPEC_SLOTS[index];
            final Optional<String> blockReason = classSpecsSeasonLocked()
                    ? Optional.of("A Prologue alatt a specializációk még lezártak.")
                    : ctx.specializationManager().classSelectionBlockReason(viewer, specialization);
            inventory.setItem(slot, classSpecItem(view, specialization, blockReason));
            if (blockReason.isEmpty()) holder.bind(slot, new SpecHolder.Action(
                    SpecHolder.ActionType.SELECT_CLASS_SPEC, specialization.getId(), 0));
        }

        inventory.setItem(31, capstoneItem(ctx, view, activeLoadout(view)));
        holder.bind(31, SpecHolder.Action.of(SpecHolder.ActionType.OPEN_CAPSTONE_DETAIL));
        inventory.setItem(29, mechanicItem(viewer, ctx, activeLoadout(view)));
        final ClassMechanicView mechanic = activeLoadout(view).specializationId()
                .flatMap(ClassMechanicView::forSpecialization).orElse(null);
        if (mechanic != null && ("paladin".equals(mechanic.classId())
                || "priest".equals(mechanic.classId()))) {
            holder.bind(29, SpecHolder.Action.of(SpecHolder.ActionType.OPEN_MECHANIC_SETUP));
        }
        if (mechanic != null && mechanic.companionSpecialization()) {
            inventory.setItem(33, GuiUtil.icon(Material.BONE,
                    text("Társműhely", NamedTextColor.DARK_GREEN), List.of(
                            grey("Az aktív út tartós társlistája és fejlődése."),
                            Component.empty(), click("Megnyitás"))));
            holder.bind(33, SpecHolder.Action.of(SpecHolder.ActionType.OPEN_COMPANION));
        }
        renderDoctrines(inventory, holder, ctx, view, activeLoadout(view));
        inventory.setItem(35, GuiUtil.icon(Material.LECTERN,
                text("Doctrine-kódex", NamedTextColor.AQUA), List.of(
                        grey("Mindhárom doctrine-szint részletes hatása és állapota."),
                        Component.empty(), click("Megnyitás"))));
        holder.bind(35, SpecHolder.Action.of(SpecHolder.ActionType.OPEN_DOCTRINE_DETAIL));
        inventory.setItem(45, GuiUtil.icon(Material.GRINDSTONE,
                text("Aktív út visszaváltása", NamedTextColor.GOLD), List.of(
                        grey("Csak az aktív összeállítást törli."),
                        grey("Ár: " + ctx.currencyManager().formatBalance(
                                ctx.specializationManager().getRespecCost()) + " frakcióvaluta."),
                        Component.empty(), click("Megerősítés megnyitása"))));
        holder.bind(45, SpecHolder.Action.of(SpecHolder.ActionType.REQUEST_CLASS_RESPEC));
        inventory.setItem(47, GuiUtil.icon(Material.AMETHYST_SHARD,
                text("Lélekkapocs és Class Relic", NamedTextColor.LIGHT_PURPLE), List.of(
                        grey("Személyes spellbook, rezonancia és Awakening-állapot."),
                        Component.empty(), click("Megnyitás"))));
        holder.bind(47, SpecHolder.Action.of(SpecHolder.ActionType.OPEN_ARTIFACT_DETAIL));
        inventory.setItem(49, backItem());
        holder.bind(49, SpecHolder.Action.of(SpecHolder.ActionType.BACK));
        inventory.setItem(53, GuiUtil.icon(Material.SMITHING_TABLE,
                text("Szakma-specializációk", NamedTextColor.AQUA),
                List.of(grey("A szakmai utak külön felülete."), Component.empty(), click("Megnyitás"))));
        holder.bind(53, SpecHolder.Action.of(SpecHolder.ActionType.OPEN_PROFESSION));
        viewer.openInventory(inventory);
        GuiUtil.sound(viewer, GuiUtil.GuiSound.OPEN);
    }

    public static void openProfession(final Player viewer, final CharacterMenuContext ctx) {
        if (viewer == null || ctx == null) return;
        final SpecHolder holder = new SpecHolder(viewer.getUniqueId(), SpecHolder.Mode.PROFESSION);
        final Inventory inventory = createInventory(holder, viewer, ctx, "profession-spec-gui-title",
                "<dark_aqua>» Szakmai utak «</dark_aqua>");
        fillThemed(inventory, faction(viewer, ctx));
        final ProfessionSpecializationType current = ctx.specializationManager()
                .getProfessionSpecialization(viewer);
        inventory.setItem(4, GuiUtil.icon(Material.SMITHING_TABLE, accent("Szakmai specializáció"),
                List.of(label("Aktív út", current == null ? text("nincs", NamedTextColor.GRAY)
                                : current.getDisplayName()),
                        grey("A szakmai út nem foglal class-loadout helyet."))));
        final List<ProfessionSpecializationType> options = professionSpecOptions(viewer, ctx);
        for (int index = 0; index < options.size() && index < PROF_SPEC_SLOTS.length; index++) {
            final ProfessionSpecializationType specialization = options.get(index);
            final int slot = PROF_SPEC_SLOTS[index];
            final boolean selected = current == specialization;
            final boolean available = current == null && ctx.specializationManager()
                    .canSelectProfessionSpecialization(viewer, specialization);
            final List<Component> lore = new ArrayList<>();
            lore.add(label("Szakma", specialization.getParentProfession().getDisplayName()));
            if (selected) lore.add(ok("Aktív szakmai út"));
            else if (available) lore.add(click("Kiválasztás"));
            else lore.add(error(current == null ? "A szint- vagy szakmafeltétel hiányzik."
                    : "Előbb váltsd vissza a jelenlegi szakmai utat."));
            inventory.setItem(slot, GuiUtil.icon(selected ? Material.ENCHANTED_BOOK : Material.BOOK,
                    specialization.getDisplayName().decoration(TextDecoration.ITALIC, false), lore, selected));
            if (available) holder.bind(slot, new SpecHolder.Action(
                    SpecHolder.ActionType.SELECT_PROFESSION_SPEC, specialization.name(), 0));
        }
        inventory.setItem(45, GuiUtil.icon(Material.GRINDSTONE,
                text("Szakmai út visszaváltása", NamedTextColor.GOLD),
                List.of(grey("Ár: " + ctx.currencyManager().formatBalance(
                        ctx.specializationManager().getRespecCost()) + " frakcióvaluta."),
                        Component.empty(), click("Visszaváltás"))));
        holder.bind(45, SpecHolder.Action.of(SpecHolder.ActionType.RESPEC_PROFESSION));
        inventory.setItem(49, backItem());
        holder.bind(49, SpecHolder.Action.of(SpecHolder.ActionType.OPEN_CLASS_PROGRESS));
        viewer.openInventory(inventory);
        GuiUtil.sound(viewer, GuiUtil.GuiSound.PAGE);
    }

    public static void openRespecConfirmation(final Player viewer, final CharacterMenuContext ctx) {
        if (viewer == null || ctx == null) return;
        final SpecHolder holder = new SpecHolder(viewer.getUniqueId(), SpecHolder.Mode.CONFIRM_CLASS_RESPEC);
        final Inventory inventory = createInventory(holder, viewer, ctx, "spec-respec-confirm-title",
                "<dark_red>» Visszaváltás megerősítése «</dark_red>");
        fillThemed(inventory, faction(viewer, ctx));
        final ClassProgressView view = ctx.specializationManager().classProgressView(viewer);
        final ClassProgressView.LoadoutView active = activeLoadout(view);
        inventory.setItem(13, GuiUtil.icon(Material.WRITABLE_BOOK,
                text("Megtartott állapot", NamedTextColor.GREEN), List.of(
                        grey("A másik összeállítás megmarad."), grey("A kasztod és kasztszinted megmarad."),
                        grey("A szakmáid változatlanok maradnak."))));
        inventory.setItem(22, GuiUtil.icon(Material.GRINDSTONE,
                text("Végleg visszaváltod ezt az utat?", NamedTextColor.RED), List.of(
                        label("Specializáció", specName(active)),
                        label("Mastery", text(active.masteryRank() + "/10", NamedTextColor.WHITE)),
                        grey("A doctrine-, mastery- és spec-állapot törlődik."),
                        grey("Ár: " + ctx.currencyManager().formatBalance(
                                ctx.specializationManager().getRespecCost()) + " frakcióvaluta."))));
        inventory.setItem(30, GuiUtil.icon(Material.LIME_CONCRETE,
                text("Igen, visszaváltom", NamedTextColor.GREEN), List.of(click("Megerősítés"))));
        holder.bind(30, SpecHolder.Action.of(SpecHolder.ActionType.CONFIRM_CLASS_RESPEC));
        inventory.setItem(32, GuiUtil.icon(Material.RED_CONCRETE,
                text("Mégsem", NamedTextColor.RED), List.of(grey("Vissza a kasztműhelybe."))));
        holder.bind(32, SpecHolder.Action.of(SpecHolder.ActionType.CANCEL_CLASS_RESPEC));
        viewer.openInventory(inventory);
        GuiUtil.sound(viewer, GuiUtil.GuiSound.PAGE);
    }

    public static void openMechanicSetup(final Player viewer, final CharacterMenuContext ctx) {
        if (viewer == null || ctx == null) return;
        final ClassProgressView view = ctx.specializationManager().classProgressView(viewer);
        final ClassMechanicView mechanic = activeLoadout(view).specializationId()
                .flatMap(ClassMechanicView::forSpecialization).orElse(null);
        if (mechanic == null || (!"paladin".equals(mechanic.classId())
                && !"priest".equals(mechanic.classId()))) {
            openClassProgress(viewer, ctx);
            return;
        }
        final SpecHolder holder = new SpecHolder(viewer.getUniqueId(), SpecHolder.Mode.MECHANIC_SETUP);
        final Inventory inventory = createInventory(holder, viewer, ctx, "spec-mechanic-setup-title",
                "<gold>» Harci irány beállítása «</gold>");
        fillThemed(inventory, faction(viewer, ctx));
        inventory.setItem(13, mechanicItem(viewer, ctx, activeLoadout(view)));
        final var live = ctx.resourceManager() == null ? null : ctx.resourceManager().classHudState(viewer);
        final String currentChoice = live == null || live.metric(0) == null
                ? "" : live.metric(0).state();
        if ("paladin".equals(mechanic.classId())) {
            bindMechanicChoice(inventory, holder, 29, Material.GHAST_TEAR, "Irgalom",
                    "A gyógyító tettek építik a Meggyőződést.",
                    SpecHolder.ActionType.CHOOSE_PALADIN_OATH, "irgalom", "irgalom".equals(currentChoice));
            bindMechanicChoice(inventory, holder, 31, Material.GOLDEN_SWORD, "Ítélet",
                    "A sebző tettek építik a Meggyőződést.",
                    SpecHolder.ActionType.CHOOSE_PALADIN_OATH, "itelet", "itelet".equals(currentChoice));
            bindMechanicChoice(inventory, holder, 33, Material.SHIELD, "Oltalmazás",
                    "A védelmi tettek építik a Meggyőződést.",
                    SpecHolder.ActionType.CHOOSE_PALADIN_OATH, "oltalmazas", "oltalmazas".equals(currentChoice));
        } else {
            bindMechanicChoice(inventory, holder, 29, Material.GHAST_TEAR, "Vigasz",
                    "Gyógyító tettek gyűjtik a litánia verseit.",
                    SpecHolder.ActionType.CHOOSE_PRIEST_LITANY, "vigasz", "vigasz".equals(currentChoice));
            bindMechanicChoice(inventory, holder, 31, Material.IRON_SWORD, "Ostor",
                    "Sebző tettek gyűjtik a litánia verseit.",
                    SpecHolder.ActionType.CHOOSE_PRIEST_LITANY, "ostor", "ostor".equals(currentChoice));
            bindMechanicChoice(inventory, holder, 33, Material.ECHO_SHARD, "Csend",
                    "Árnyékos tettek gyűjtik a litánia verseit.",
                    SpecHolder.ActionType.CHOOSE_PRIEST_LITANY, "csend", "csend".equals(currentChoice));
        }
        inventory.setItem(49, backItem());
        holder.bind(49, SpecHolder.Action.of(SpecHolder.ActionType.OPEN_CLASS_PROGRESS));
        viewer.openInventory(inventory);
        GuiUtil.sound(viewer, GuiUtil.GuiSound.PAGE);
    }

    public static void openDoctrineDetail(final Player viewer, final CharacterMenuContext ctx) {
        if (viewer == null || ctx == null) return;
        final ClassProgressView view = ctx.specializationManager().classProgressView(viewer);
        final ClassProgressView.LoadoutView loadout = activeLoadout(view);
        final SpecializationType specialization = loadout.specializationId()
                .map(SpecializationType::fromId).orElse(null);
        if (specialization == null) {
            openClassProgress(viewer, ctx);
            return;
        }
        final SpecHolder holder = new SpecHolder(viewer.getUniqueId(), SpecHolder.Mode.DOCTRINE_DETAIL);
        final Inventory inventory = createInventory(holder, viewer, ctx, "spec-doctrine-detail-title",
                "<aqua>» Doctrine-kódex «</aqua>");
        fillThemed(inventory, faction(viewer, ctx));
        inventory.setItem(4, GuiUtil.icon(Material.ENCHANTED_BOOK,
                ClassUiAssets.badgeName(specialization), List.of(
                        grey("A doctrine loadoutonként tartós és a kiválasztás után végleges."),
                        grey("A százalékos értékeket az élő class-balansz alkalmazza."))));
        final int[][] slots = {{19, 21}, {28, 30}, {37, 39}};
        for (int tier = 0; tier < DOCTRINE_LEVELS.length; tier++) {
            final int level = DOCTRINE_LEVELS[tier];
            int index = 0;
            for (final String option : ctx.specializationManager().doctrineChoices(specialization, level)
                    .stream().sorted().toList()) {
                if (index >= 2) break;
                final int slot = slots[tier][index++];
                final String committed = loadout.doctrineChoices().get("level_" + level);
                final boolean selected = option.equals(committed);
                final boolean available = view.classLevel() >= level && committed == null
                        && loadout.status() == LoadoutStatus.ACTIVE;
                final DoctrinePresentation presentation = DoctrinePresentation.of(
                        specialization, level, option);
                final List<Component> lore = new ArrayList<>();
                lore.add(label("Doctrine-szint", text(Integer.toString(level), NamedTextColor.WHITE)));
                lore.add(grey(presentation.tierRole()));
                lore.add(Component.empty());
                lore.add(grey(presentation.effect()));
                lore.add(Component.empty());
                if (selected) lore.add(ok("Ezen a loadouton kiválasztva"));
                else if (available) lore.add(click("Végleges kiválasztás"));
                else if (committed != null) lore.add(error("Ezen a szinten már döntöttél."));
                else if (view.classLevel() < level) lore.add(error(level + ". kasztszinten nyílik."));
                else lore.add(error("Csak az aktív loadout módosítható."));
                inventory.setItem(slot, GuiUtil.icon(selected ? Material.ENCHANTED_BOOK : Material.WRITABLE_BOOK,
                        text(presentation.title(), selected ? NamedTextColor.GREEN : NamedTextColor.AQUA),
                        lore, selected));
                if (available) holder.bind(slot, new SpecHolder.Action(
                        SpecHolder.ActionType.CHOOSE_DOCTRINE, option, level));
            }
        }
        inventory.setItem(49, backItem());
        holder.bind(49, SpecHolder.Action.of(SpecHolder.ActionType.OPEN_CLASS_PROGRESS));
        viewer.openInventory(inventory);
        GuiUtil.sound(viewer, GuiUtil.GuiSound.PAGE);
    }

    public static void openCapstoneDetail(final Player viewer, final CharacterMenuContext ctx) {
        if (viewer == null || ctx == null) return;
        final ClassProgressView view = ctx.specializationManager().classProgressView(viewer);
        final ClassProgressView.LoadoutView loadout = activeLoadout(view);
        final SpecializationType specialization = loadout.specializationId()
                .map(SpecializationType::fromId).orElse(null);
        if (specialization == null) {
            openClassProgress(viewer, ctx);
            return;
        }
        final SpecHolder holder = new SpecHolder(viewer.getUniqueId(), SpecHolder.Mode.CAPSTONE_DETAIL);
        final Inventory inventory = createInventory(holder, viewer, ctx, "spec-capstone-detail-title",
                "<gold>» Végső próba «</gold>");
        fillThemed(inventory, faction(viewer, ctx));
        inventory.setItem(4, GuiUtil.icon(Material.DRAGON_EGG,
                ClassUiAssets.badgeName(specialization), List.of(
                        grey("Az 50. szint elérhetővé teszi a próbát;"),
                        grey("a capstone spell csak a próba teljesítése után oldódik."))));
        inventory.setItem(13, capstoneItem(ctx, view, loadout));
        final String spellId = ctx.configManager().getString(
                "specializations." + specialization.getId() + ".capstone-spell", "");
        final var spell = ctx.spellRegistry().getById(spellId);
        final List<Component> spellLore = new ArrayList<>();
        spellLore.add(label("Azonosító", text(spellId.isBlank() ? "nincs" : spellId, NamedTextColor.GRAY)));
        if (spell != null) {
            final List<String> description = spell.describe();
            if (description.isEmpty()) spellLore.add(grey("A részletes hatást a Varázskönyv mutatja."));
            else description.forEach(line -> spellLore.add(grey(line)));
            spellLore.add(label("Cooldown", text(spell.getCooldown() + " mp", NamedTextColor.WHITE)));
        }
        inventory.setItem(22, GuiUtil.icon(loadout.capstoneStatus() == CapstoneStatus.COMPLETED
                        ? Material.ENCHANTED_BOOK : Material.BOOK,
                text(spell == null ? displayId(spellId) : spell.getName(), NamedTextColor.LIGHT_PURPLE),
                spellLore, loadout.capstoneStatus() == CapstoneStatus.COMPLETED));
        final String trialId = ctx.specializationManager().capstoneTrialId(specialization).orElse("nincs");
        inventory.setItem(31, GuiUtil.icon(Material.FILLED_MAP,
                text("Próba útvonala", NamedTextColor.YELLOW), List.of(
                        label("Küldetés", text(displayId(trialId), NamedTextColor.WHITE)),
                        grey("Az elérhető végső próbát a Küldetéstáblán találod."))));
        inventory.setItem(40, masteryItem(view, loadout));
        inventory.setItem(49, backItem());
        holder.bind(49, SpecHolder.Action.of(SpecHolder.ActionType.OPEN_CLASS_PROGRESS));
        viewer.openInventory(inventory);
        GuiUtil.sound(viewer, GuiUtil.GuiSound.PAGE);
    }

    public static void openArtifactDetail(final Player viewer, final CharacterMenuContext ctx) {
        if (viewer == null || ctx == null) return;
        final JobType job = ctx.jobManager().getPrimaryJob(viewer);
        if (job == null) {
            openClassProgress(viewer, ctx);
            return;
        }
        final SpecHolder holder = new SpecHolder(viewer.getUniqueId(), SpecHolder.Mode.ARTIFACT_DETAIL);
        final Inventory inventory = createInventory(holder, viewer, ctx, "spec-artifact-detail-title",
                "<light_purple>» Lélekkapocs és Class Relic «</light_purple>");
        fillThemed(inventory, faction(viewer, ctx));
        inventory.setItem(13, ctx.catalystItemFactory().createCatalyst(job));
        inventory.setItem(22, GuiUtil.icon(Material.KNOWLEDGE_BOOK,
                text("Lélekkapocs", NamedTextColor.AQUA), List.of(
                        grey("A személyes spellbookod és kézi class-interakcióid eszköze."),
                        grey("Jobb katt: aktív spell; lopakodás + jobb katt: célkijelölés vagy class-akció."),
                        grey("Elvesztéskor a Kasztok menüből kérhető új példány."))));
        final var activation = ctx.classRelicService() == null ? null
                : ctx.classRelicService().resolve(viewer.getUniqueId());
        final List<Component> relicLore = new ArrayList<>();
        if (activation == null || activation.relicId().isBlank()) {
            relicLore.add(error("Ehhez a kaszthoz nincs betöltött relic-kötés."));
        } else {
            relicLore.add(label("Relikvia", text(displayId(activation.relicId()), NamedTextColor.WHITE)));
            relicLore.add(label("Alaperő", activation.basePowerActive()
                    ? ok("aktív") : error("nyugvó")));
            relicLore.add(label("Rezonancia", activation.resolvedResonanceId()
                    .map(id -> text(displayId(id), activation.resonanceActive()
                            ? NamedTextColor.GREEN : NamedTextColor.YELLOW))
                    .orElseGet(() -> text("nincs", NamedTextColor.GRAY))));
            relicLore.add(label("Awakening", text(activation.awakeningConfigured()
                    ? "konfigurálva" : "nincs", NamedTextColor.LIGHT_PURPLE)));
            if (!activation.basePowerActive()) relicLore.add(error(
                    dormantReason(activation.dormantReason().name())));
        }
        inventory.setItem(31, GuiUtil.icon(Material.NETHER_STAR,
                text("Class Relic", NamedTextColor.LIGHT_PURPLE), relicLore,
                activation != null && activation.basePowerActive()));
        inventory.setItem(40, GuiUtil.icon(Material.AMETHYST_CLUSTER,
                text("Rezonancia szabály", NamedTextColor.GOLD), List.of(
                        grey("A relikvia csak a hozzá kötött kaszttal aktiválódik."),
                        grey("A specializáció rezonanciája az aktív loadoutot követi."),
                        grey("A birtoklás és a fizikai példány ellenőrzése fail-closed."))));
        inventory.setItem(49, backItem());
        holder.bind(49, SpecHolder.Action.of(SpecHolder.ActionType.OPEN_CLASS_PROGRESS));
        viewer.openInventory(inventory);
        GuiUtil.sound(viewer, GuiUtil.GuiSound.PAGE);
    }

    private static void bindMechanicChoice(final Inventory inventory, final SpecHolder holder,
                                           final int slot, final Material material,
                                           final String title, final String description,
                                           final SpecHolder.ActionType actionType,
                                           final String value, final boolean selected) {
        inventory.setItem(slot, GuiUtil.icon(material, text(title, NamedTextColor.GOLD),
                List.of(grey(description), grey("A választás erre a munkamenetre szól."),
                        Component.empty(), selected ? ok("Jelenlegi irány") : click("Kiválasztás")), selected));
        holder.bind(slot, new SpecHolder.Action(actionType, value, 0));
    }

    private static void renderDoctrines(final Inventory inventory, final SpecHolder holder,
                                        final CharacterMenuContext ctx, final ClassProgressView view,
                                        final ClassProgressView.LoadoutView loadout) {
        final SpecializationType specialization = loadout.specializationId()
                .map(SpecializationType::fromId).orElse(null);
        for (int tier = 0; tier < DOCTRINE_LEVELS.length; tier++) {
            final int level = DOCTRINE_LEVELS[tier];
            final Set<String> options = ctx.specializationManager().doctrineChoices(specialization, level);
            int optionIndex = 0;
            for (final String option : options.stream().sorted().toList()) {
                if (optionIndex >= 2) break;
                final int slot = DOCTRINE_SLOTS[tier][optionIndex++];
                final String committed = loadout.doctrineChoices().get("level_" + level);
                final boolean selected = option.equals(committed);
                final boolean available = view.classLevel() >= level && committed == null
                        && loadout.status() == LoadoutStatus.ACTIVE;
                final DoctrinePresentation presentation = DoctrinePresentation.of(
                        specialization, level, option);
                final List<Component> lore = new ArrayList<>();
                lore.add(label("Doctrine szint", text(Integer.toString(level), NamedTextColor.WHITE)));
                lore.add(grey(presentation.effect()));
                lore.add(Component.empty());
                if (selected) lore.add(ok("Kiválasztva — végleges ezen az összeállításon"));
                else if (available) lore.add(click("Doctrine kiválasztása"));
                else if (committed != null) lore.add(error("Ezen a szinten már választottál."));
                else if (view.classLevel() < level) lore.add(error("A(z) " + level + ". kasztszinten nyílik."));
                else lore.add(error("Csak az aktív összeállítás doctrine-ja választható."));
                inventory.setItem(slot, GuiUtil.icon(selected ? Material.ENCHANTED_BOOK : Material.PAPER,
                        text(presentation.title(), selected ? NamedTextColor.GREEN : NamedTextColor.AQUA), lore, selected));
                if (available) holder.bind(slot, new SpecHolder.Action(
                        SpecHolder.ActionType.CHOOSE_DOCTRINE, option, level));
            }
        }
    }

    private static ItemStack classHeader(final Player player, final CharacterMenuContext ctx,
                                         final ClassProgressView view) {
        final JobType job = view.primaryClassId().map(JobType::fromId).orElse(null);
        final FactionType faction = faction(player, ctx);
        final List<Component> lore = new ArrayList<>();
        lore.add(label("Kaszt", job == null ? text("nincs", NamedTextColor.GRAY) : job.getDisplayName()));
        lore.add(label("Kasztszint", text(view.classLevel() + "/50", NamedTextColor.WHITE)));
        lore.add(label("Frakciótéma", text(faction.getDisplayName(), themeColor(faction))));
        lore.add(label("Második összeállítás", view.secondSlotUnlocked()
                ? text("feloldva", NamedTextColor.GREEN)
                : text(view.secondSlotUnlockLevel() + ". szinten nyílik", NamedTextColor.RED)));
        if (!view.gameplayUsable()) {
            lore.add(Component.empty());
            lore.add(error(view.unavailableReason().orElse("A Profile v2 állapot nem használható.")));
        }
        return GuiUtil.icon(Material.NETHER_STAR, accent("Kasztműhely"), lore, view.gameplayUsable());
    }

    private static ItemStack loadoutItem(final Player player, final CharacterMenuContext ctx,
                                         final ClassProgressView view, final LoadoutSlot slot) {
        final ClassProgressView.LoadoutView loadout = view.loadout(slot);
        final List<Component> lore = new ArrayList<>();
        lore.add(label("Specializáció", specName(loadout)));
        lore.add(label("Állapot", loadoutStatus(loadout.status())));
        lore.add(label("Mastery", text(loadout.masteryRank() + "/10", NamedTextColor.WHITE)));
        if (slot == LoadoutSlot.SECOND && !view.secondSlotUnlocked()) {
            lore.add(error("A(z) " + view.secondSlotUnlockLevel() + ". kasztszinten nyílik."));
        } else if (loadout.status() == LoadoutStatus.INACTIVE) {
            final Optional<String> reason = ctx.specializationManager().classSwitchBlockReason(player, slot);
            lore.add(reason.map(SpecGUI::error).orElseGet(() -> click("Aktiválás")));
        } else if (loadout.status() == LoadoutStatus.SEALED) {
            lore.add(error("Lepecsételve: a DARK kapufeltételek hiányoznak."));
            loadout.sealReason().ifPresent(reason -> lore.add(grey(displayId(reason.cause().name()))));
        } else if (loadout.status() == LoadoutStatus.EMPTY && (slot == LoadoutSlot.FIRST
                || view.secondSlotUnlocked())) {
            lore.add(grey("Válassz lent egy specializációt."));
        } else if (loadout.status() == LoadoutStatus.ACTIVE) {
            lore.add(ok("Jelenleg aktív összeállítás"));
        }
        final Material material = switch (loadout.status()) {
            case ACTIVE -> Material.ENDER_EYE;
            case INACTIVE -> Material.ENDER_PEARL;
            case SEALED -> Material.CRYING_OBSIDIAN;
            case EMPTY -> Material.GRAY_DYE;
        };
        return GuiUtil.icon(material, text(slot == LoadoutSlot.FIRST ? "I. összeállítás" : "II. összeállítás",
                loadout.status() == LoadoutStatus.ACTIVE ? NamedTextColor.GREEN : NamedTextColor.WHITE), lore,
                loadout.status() == LoadoutStatus.ACTIVE);
    }

    private static void bindLoadoutSwitch(final SpecHolder holder, final Player player,
                                          final CharacterMenuContext ctx, final ClassProgressView view,
                                          final LoadoutSlot slot, final int inventorySlot) {
        if (view.loadout(slot).status() == LoadoutStatus.INACTIVE
                && ctx.specializationManager().classSwitchBlockReason(player, slot).isEmpty()) {
            holder.bind(inventorySlot, new SpecHolder.Action(
                    SpecHolder.ActionType.SWITCH_LOADOUT, slot.name(), 0));
        }
    }

    private static ItemStack classSpecItem(final ClassProgressView view,
                                           final SpecializationType specialization,
                                           final Optional<String> blockReason) {
        final boolean learned = view.loadouts().values().stream().anyMatch(loadout ->
                loadout.specializationId().filter(specialization.getId()::equalsIgnoreCase).isPresent());
        final List<Component> lore = new ArrayList<>();
        lore.add(label("Kaszt", specialization.getParentJob().getDisplayName()));
        if (specialization.getRequiredFaction() != null) {
            lore.add(label("Frakciókapu", text(specialization.getRequiredFaction().getDisplayName(),
                    themeColor(specialization.getRequiredFaction()))));
        }
        if (specialization.requiresSinner()) lore.add(label("Bűnös kapu", text("szükséges", NamedTextColor.RED)));
        if (learned) lore.add(ok("Már megtanult út"));
        else blockReason.ifPresentOrElse(reason -> lore.add(error(reason)), () -> lore.add(click("Megtanulás")));
        return GuiUtil.icon(learned ? Material.ENCHANTED_BOOK
                        : blockReason.isEmpty() ? Material.WRITABLE_BOOK : Material.BOOK,
                ClassUiAssets.badgeName(specialization).decoration(TextDecoration.ITALIC, false), lore, learned);
    }

    private static ItemStack masteryItem(final ClassProgressView view,
                                         final ClassProgressView.LoadoutView loadout) {
        final List<Component> lore = new ArrayList<>();
        lore.add(label("Specializáció", specName(loadout)));
        if (view.classLevel() < 50) lore.add(error("A mastery az 50. kasztszinten nyílik."));
        else if (loadout.specializationId().isEmpty()) lore.add(error("Nincs aktív specializáció."));
        else if (loadout.masteryRank() >= 10) lore.add(ok("Elérted a maximális mastery rangot."));
        else {
            lore.add(label("Rang", text(loadout.masteryRank() + "/10", NamedTextColor.WHITE)));
            lore.add(label("Haladás", text(loadout.experienceIntoRank() + "/"
                    + loadout.masteryExperiencePerRank() + " XP", NamedTextColor.AQUA)));
            lore.add(grey("Csak az aktív úttal, valódi harcban fejlődik."));
        }
        return GuiUtil.icon(Material.EXPERIENCE_BOTTLE,
                text("Specializáció-mastery", NamedTextColor.LIGHT_PURPLE), lore,
                loadout.masteryRank() >= 5);
    }

    private static ItemStack mechanicItem(final Player viewer, final CharacterMenuContext ctx,
                                          final ClassProgressView.LoadoutView loadout) {
        final ClassMechanicView mechanic = loadout.specializationId()
                .flatMap(ClassMechanicView::forSpecialization).orElse(null);
        if (mechanic == null) {
            return GuiUtil.icon(Material.COMPASS, text("Harci mechanika", NamedTextColor.GRAY),
                    List.of(grey("Válassz aktív specializációt a leírás megnyitásához.")));
        }
        final List<Component> lore = new ArrayList<>();
        lore.add(label("Kasztmag", text(mechanic.classMechanic(), NamedTextColor.GOLD)));
        lore.add(grey(mechanic.classCycle()));
        lore.add(Component.empty());
        lore.add(label("Spec-mechanika", text(mechanic.specializationMechanic(), NamedTextColor.AQUA)));
        lore.add(grey(mechanic.specializationCycle()));
        final var live = ctx.resourceManager() == null ? null : ctx.resourceManager().classHudState(viewer);
        if (live != null && !live.classId().isBlank()) {
            lore.add(Component.empty());
            lore.add(text("Élő állapot", NamedTextColor.GREEN));
            if (!live.state().isBlank()) lore.add(label("Állapot", text(live.state(), NamedTextColor.WHITE)));
            if (!live.proc().isBlank()) lore.add(label("Aktív jel", text(live.proc(), NamedTextColor.GOLD)));
            for (final var metric : live.metrics().stream().limit(3).toList()) {
                final String value = !metric.text().isBlank() ? metric.text()
                        : metric.maximum() > 0.0D ? Math.round(metric.value()) + "/" + Math.round(metric.maximum())
                        : metric.state();
                lore.add(label(metric.label().isBlank() ? displayId(metric.id()) : metric.label(),
                        text(value.isBlank() ? "—" : value, NamedTextColor.AQUA)));
            }
            live.mechanics().stream().filter(value -> !value.isBlank()).limit(3)
                    .forEach(value -> lore.add(grey("• " + value)));
        }
        if (!mechanic.interactionHint().isBlank()) {
            lore.add(Component.empty());
            lore.add(text("Irányítás", NamedTextColor.YELLOW));
            lore.add(grey(mechanic.interactionHint()));
        }
        if ("paladin".equals(mechanic.classId()) || "priest".equals(mechanic.classId())) {
            lore.add(Component.empty());
            lore.add(click("Eskü/Litánia beállítása"));
        }
        return GuiUtil.icon(Material.RECOVERY_COMPASS,
                text("Hogyan működik az utad?", NamedTextColor.LIGHT_PURPLE), lore);
    }

    private static ItemStack capstoneItem(final CharacterMenuContext ctx,
                                          final ClassProgressView view,
                                          final ClassProgressView.LoadoutView loadout) {
        final SpecializationType specialization = loadout.specializationId()
                .map(SpecializationType::fromId).orElse(null);
        final List<Component> lore = new ArrayList<>();
        lore.add(label("Specializáció", specName(loadout)));
        final CapstoneStatus status = loadout.capstoneStatus();
        switch (status) {
            case LOCKED -> lore.add(error(view.classLevel() < 50
                    ? "Az 50. kasztszinten nyílik meg a végső próba."
                    : "A végső próba még nem aktiválódott."));
            case AVAILABLE -> {
                lore.add(click("A végső próba elérhető"));
                ctx.specializationManager().capstoneTrialId(specialization)
                        .ifPresent(ignored -> lore.add(grey("Keresd a Küldetéstáblán a spec végső próbáját.")));
                lore.add(grey("A level 50-es capstone spell a próba után oldódik fel."));
            }
            case IN_PROGRESS -> lore.add(text("◈ A végső próba folyamatban van", NamedTextColor.YELLOW));
            case COMPLETED -> lore.add(ok("Végső próba teljesítve — capstone feloldva"));
        }
        final Material material = status == CapstoneStatus.COMPLETED ? Material.DRAGON_EGG
                : status == CapstoneStatus.LOCKED ? Material.LODESTONE : Material.END_CRYSTAL;
        return GuiUtil.icon(material, text("Végső próba", NamedTextColor.GOLD), lore,
                status == CapstoneStatus.COMPLETED);
    }

    public static List<SpecializationType> classSpecOptions(final Player player,
                                                            final CharacterMenuContext ctx) {
        final List<SpecializationType> options = new ArrayList<>();
        final JobType primary = ctx.jobManager().getPrimaryJob(player);
        if (primary == null) return options;
        for (final SpecializationType specialization : SpecializationType.values()) {
            if (specialization.getParentJob() == primary) options.add(specialization);
        }
        return options;
    }

    public static List<ProfessionSpecializationType> professionSpecOptions(
            final Player player, final CharacterMenuContext ctx) {
        final List<ProfessionType> active = ctx.professionManager().getActiveProfessions(player);
        final List<ProfessionSpecializationType> options = new ArrayList<>();
        for (final ProfessionSpecializationType specialization : ProfessionSpecializationType.values()) {
            if (active.contains(specialization.getParentProfession())) options.add(specialization);
        }
        return options;
    }

    private static Inventory createInventory(final SpecHolder holder, final Player viewer,
                                             final CharacterMenuContext ctx,
                                             final String messageKey, final String fallback) {
        final ClassUiAssets.Surface surface = switch (holder.getMode()) {
            case CLASS_PROGRESS, PROFESSION -> ClassUiAssets.Surface.WORKSHOP;
            case MECHANIC_SETUP, DOCTRINE_DETAIL, CAPSTONE_DETAIL, ARTIFACT_DETAIL,
                    CONFIRM_CLASS_RESPEC -> ClassUiAssets.Surface.DETAIL;
        };
        final Component background = ClassUiAssets.title(surface, faction(viewer, ctx),
                ctx.messageManager().getMessage(messageKey, fallback));
        final Inventory inventory = Bukkit.createInventory(holder, SIZE, background);
        holder.setInventory(inventory);
        return inventory;
    }

    private static void fillThemed(final Inventory inventory, final FactionType faction) {
        ClassUiAssets.fill(inventory, faction);
    }

    private static FactionType faction(final Player player, final CharacterMenuContext ctx) {
        return ctx.factionManager().getChosenFaction(player.getUniqueId()).orElse(FactionType.NEUTRAL);
    }

    private static NamedTextColor themeColor(final FactionType faction) {
        return switch (faction) {
            case RED -> NamedTextColor.RED;
            case BLUE -> NamedTextColor.AQUA;
            case DARK -> NamedTextColor.DARK_PURPLE;
            case NEUTRAL -> NamedTextColor.GRAY;
        };
    }

    private static ClassProgressView.LoadoutView activeLoadout(final ClassProgressView view) {
        return view.activeSlot().map(view::loadout).orElseGet(ClassProgressView.LoadoutView::empty);
    }

    private static Component specName(final ClassProgressView.LoadoutView loadout) {
        final SpecializationType specialization = loadout.specializationId()
                .map(SpecializationType::fromId).orElse(null);
        return specialization == null ? text("nincs", NamedTextColor.GRAY)
                : ClassUiAssets.badgeName(specialization).decoration(TextDecoration.ITALIC, false);
    }

    private static Component loadoutStatus(final LoadoutStatus status) {
        return switch (status) {
            case ACTIVE -> text("aktív", NamedTextColor.GREEN);
            case INACTIVE -> text("tartalék", NamedTextColor.YELLOW);
            case SEALED -> text("lepecsételve", NamedTextColor.DARK_PURPLE);
            case EMPTY -> text("üres", NamedTextColor.GRAY);
        };
    }

    private static boolean classSpecsSeasonLocked() {
        final ConfigManager config = ConfigManager.current();
        return config != null && !PrologueContentPolicy.specializationAvailable(config);
    }

    private static String displayId(final String id) {
        if (id == null || id.isBlank()) return "Nincs";
        final String[] words = id.toLowerCase(Locale.ROOT).replace('-', '_').split("_");
        final StringBuilder result = new StringBuilder();
        for (final String word : words) {
            if (word.isBlank()) continue;
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    private static String dormantReason(final String reason) {
        return switch (reason == null ? "" : reason) {
            case "FRAMEWORK_DISABLED" -> "A Class Relic rendszer jelenleg ki van kapcsolva.";
            case "NO_BINDING" -> "Ehhez a kaszthoz nincs relic-kötés.";
            case "PROFILE_NOT_USABLE" -> "A Profile v2 állapot még nem használható.";
            case "WRONG_CLASS" -> "A relikvia másik kaszthoz tartozik.";
            case "NOT_OWNER" -> "Nem te vagy a világ-egyedi relikvia tulajdonosa.";
            case "RELIC_LOST" -> "A relikvia elveszett állapotban van.";
            case "NO_PHYSICAL_POSSESSION" -> "A fizikai relikvia nincs nálad.";
            default -> "A relikvia jelenleg nyugvó állapotban van.";
        };
    }

    private static ItemStack backItem() {
        return GuiUtil.icon(Material.ARROW, text("Vissza", NamedTextColor.RED), List.of());
    }

    private static Component text(final String value, final NamedTextColor color) {
        return Component.text(value, color).decoration(TextDecoration.ITALIC, false);
    }

    private static Component click(final String value) { return text("» " + value, NamedTextColor.YELLOW); }
    private static Component ok(final String value) { return text("✔ " + value, NamedTextColor.GREEN); }
    private static Component error(final String value) { return text("✖ " + value, NamedTextColor.RED); }
}
