package hu.taliann.icesmp.dev.weaver;

import hu.taliann.icesmp.dev.artifact.*;
import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.gui.*;
import hu.taliann.icesmp.dev.weaver.subject.*;
import hu.taliann.icesmp.security.HiddenDevAuthority;
import hu.taliann.icesmp.managers.DevItemManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import java.util.*;
import java.util.function.Consumer;
import hu.taliann.icesmp.dev.weaver.execution.WeaverExecutionCoordinator;
import hu.taliann.icesmp.dev.weaver.execution.WeaverRateLimiter;

/** Physical artifact authority is always delegated to the existing DEV lifecycle manager. */
public final class WorldWeaverKernel {
    private record Access(DevArtifactContext artifact, WorldWeaverSessionManager.Session session) {}
    private record Screen(Access access, SubjectSnapshot snapshot, WeaverView view) {}
    private final DevItemManager artifacts;
    private final WorldWeaverProviderRegistry providers;
    private final WeaverTypeRegistry types;
    private final SubjectSnapshotSource snapshots;
    private final WeaverItemSlots slots;
    private final WorldWeaverGUI gui;
    private final WeaverExecutionCoordinator execution;
    private final hu.taliann.icesmp.dev.weaver.area.WeaverAreaEngine areas;
    private final hu.taliann.icesmp.dev.weaver.execution.WeaverUndoCoordinator undo;
    private final java.util.function.BooleanSupplier operational;
    private final WeaverParameterDialog input = new WeaverParameterDialog();
    private final WeaverRateLimiter rate = new WeaverRateLimiter(() -> System.nanoTime() / 1_000_000L);
    private final WorldWeaverSessionManager sessions = new WorldWeaverSessionManager(() -> System.nanoTime() / 1_000_000L);
    private volatile Access active;
    private volatile Screen screen;
    private volatile WeaverActionDraft draft;
    private volatile hu.taliann.icesmp.dev.weaver.persistence.WeaverUndoClaim undoClaim;
    private volatile boolean closed;
    public WorldWeaverKernel(final DevItemManager artifacts, final WorldWeaverProviderRegistry providers, final WeaverTypeRegistry types,
                             final SubjectSnapshotSource snapshots, final WeaverItemSlots slots, final WorldWeaverGUI gui,
                             final WeaverExecutionCoordinator execution, final hu.taliann.icesmp.dev.weaver.execution.WeaverUndoCoordinator undo,
                             final hu.taliann.icesmp.dev.weaver.area.WeaverAreaEngine areas, final java.util.function.BooleanSupplier operational) {
        this.artifacts = Objects.requireNonNull(artifacts); this.providers = Objects.requireNonNull(providers); this.types = Objects.requireNonNull(types);
        this.snapshots = Objects.requireNonNull(snapshots); this.slots = Objects.requireNonNull(slots); this.gui = Objects.requireNonNull(gui);
        this.execution = Objects.requireNonNull(execution); this.undo = Objects.requireNonNull(undo); this.operational = Objects.requireNonNull(operational);
        this.areas = Objects.requireNonNull(areas);
    }
    public ArtifactInteractionResult interact(final DevArtifactInteraction interaction) {
        final DevArtifactContext context = interaction.context();
        if (closed || !operational.getAsBoolean() || context.manager() != artifacts || !WorldWeaverArtifactBehavior.ID.equals(context.artifactId())
                || !HiddenDevAuthority.isDeveloper(context.owner()) || context.player() == null) return ArtifactInteractionResult.AUTHORITY_REJECTED;
        final Access access = new Access(context, sessions.open(context.owner(), context.instanceId(), context.session())); active = access;
        draft = null; undoClaim = null;
        try {
            if (interaction.kind() == DevArtifactInteraction.Kind.SWAP_HAND) {
                if (interaction.sneaking()) access.session().selection().previous().ifPresent(ref -> subject(access, ref));
                else threads(access);
            } else {
                final SubjectRef ref = switch (interaction.kind()) {
                    case RIGHT_CLICK_ENTITY -> Bukkit.getPlayer(interaction.entityId()) == null
                            ? new EntityRef(interaction.entityId()) : new PlayerRef(interaction.entityId());
                    case RIGHT_CLICK_BLOCK -> new BlockRef(interaction.block().worldId(), interaction.block().x(), interaction.block().y(), interaction.block().z());
                    case RIGHT_CLICK_AIR -> context.player().getInventory().getItemInOffHand().isEmpty() ? new PlayerRef(context.owner())
                            : slots.capture(context.player(), WeaverSlot.named(WeaverSlot.Kind.OFF_HAND));
                    case SWAP_HAND -> throw new IllegalStateException("Swap already handled");
                };
                if (interaction.sneaking()) quickApply(access, ref); else subject(access, ref);
            }
            return ArtifactInteractionResult.HANDLED;
        } catch (final RuntimeException failure) { feedback(access, code(failure)); return ArtifactInteractionResult.HANDLED; }
    }
    private WeaverAuthorityToken authorize(final Access access) {
        if (!valid(access) || access.artifact().player() == null) throw new WeaverDomainRejection("AUTHORITY_REJECTED");
        return new WeaverAuthorityToken(access.artifact().owner(), access.session().id(), System.nanoTime() + 30_000_000_000L,
                () -> valid(access), System::nanoTime);
    }
    private boolean valid(final Access access) {
        final Access current = active;
        return !closed && operational.getAsBoolean() && current != null && current.session() == access.session() && access.session().active()
                && access.artifact().valid() && current.artifact().equals(access.artifact());
    }
    private ProviderContext context(final Access access) {
        return new ProviderContext(authorize(access), types, Lifetime.ONE_SHOT, access.session().mode());
    }
    private void fresh(final Access access, final SubjectRef ref, final Consumer<SubjectSnapshot> consumer) {
        authorize(access); final long request = access.session().nextView();
        snapshots.capture(access.artifact().owner(), ref).whenComplete((snapshot, failure) -> access.artifact().onOwner(player -> {
            if (!valid(access) || !sessions.matches(access.artifact().owner(), access.session().id(), request)) return;
            try {
                authorize(access);
                if (failure != null) { access.session().arming().clear(); feedback(access, code(failure)); return; }
                consumer.accept(snapshot);
            } catch (final RuntimeException rejected) { access.session().arming().clear(); feedback(access, code(rejected)); }
        }, () -> unavailable(access)));
    }
    private record ActionCapture(SubjectSnapshot snapshot, Optional<hu.taliann.icesmp.dev.weaver.area.WeaverAreaCollection> collection) { }
    private void freshAction(final Access access, final WeaverActionDraft current, final Consumer<ActionCapture> consumer) {
        final WeaverAuthorityToken authority = authorize(access); final long request = access.session().nextView();
        snapshots.capture(authority.actor(), current.snapshot().ref()).thenCompose(snapshot -> {
            if (snapshot.ref() instanceof AreaRef area && (current.descriptor().areaSupport() == AreaSupport.ENTITY_FANOUT || current.descriptor().areaSupport() == AreaSupport.BLOCK_FANOUT)) {
                return areas.collect(authority, area, current.descriptor()).thenApply(collection -> new ActionCapture(collection.decorate(snapshot), Optional.of(collection)));
            }
            return java.util.concurrent.CompletableFuture.completedFuture(new ActionCapture(snapshot, Optional.empty()));
        }).whenComplete((captured, failure) -> access.artifact().onOwner(player -> {
            if (!valid(access) || !sessions.matches(access.artifact().owner(), access.session().id(), request)) return;
            try {
                authorize(access);
                if (failure != null) { access.session().arming().clear(); feedback(access, code(failure)); return; }
                consumer.accept(captured);
            } catch (final RuntimeException rejected) { access.session().arming().clear(); feedback(access, code(rejected)); }
        }, () -> unavailable(access)));
    }
    private void subject(final Access access, final SubjectRef ref) {
        fresh(access, ref, snapshot -> {
            access.session().selection().select(snapshot.ref());
            final var discovery = providers.discover(snapshot);
            final List<WeaverView.Entry> entries = new ArrayList<>();
            for (final WeaverFacetView facet : WeaverFacetView.discover(providers, discovery)) {
                entries.add(entry(facet.facet().label(), List.of(facet.facet().description()), "AMETHYST_SHARD", new WeaverNavigation.Facet(facet.facet().id())));
            }
            discovery.errors().forEach((provider, error) -> entries.add(entry(Component.text(provider), List.of(Component.text(error)), "BARRIER", new WeaverNavigation.None())));
            render(access, snapshot, WeaverViewKind.SUBJECT, Component.text("Világszövő · " + snapshot.ref().kind()), entries, 0);
        });
    }
    public void click(final UUID actor, final UUID session, final long revision, final int rawSlot) {
        final Screen current = screen;
        if (current == null || !HiddenDevAuthority.isDeveloper(actor) || !current.access().artifact().owner().equals(actor)) return;
        final Access access = current.access();
        try {
            authorize(access);
            if (!sessions.matches(actor, session, revision) || current.view().revision() != revision) {
                access.session().arming().clear(); access.session().selection().current().ifPresent(ref -> subject(access, ref)); return;
            }
            current.view().at(rawSlot).ifPresent(item -> navigate(current, item.navigation()));
        } catch (final RuntimeException failure) { access.session().arming().clear(); feedback(access, code(failure)); }
    }
    private void navigate(final Screen current, final WeaverNavigation navigation) {
        final Access access = current.access();
        switch (navigation) {
            case WeaverNavigation.None ignored -> { }
            case WeaverNavigation.Close ignored -> { final var player = access.artifact().player(); if (player != null) player.closeInventory(); }
            case WeaverNavigation.Refresh ignored -> access.session().selection().current().ifPresent(ref -> subject(access, ref));
            case WeaverNavigation.Subject selected -> subject(access, selected.ref());
            case WeaverNavigation.Facet facet -> facet(access, Objects.requireNonNull(current.snapshot()).ref(), facet.id());
            case WeaverNavigation.Threads ignored -> threads(access);
            case WeaverNavigation.Recent ignored -> recent(access);
            case WeaverNavigation.SelectThread thread -> { if (!access.session().threads().select(thread.id())) throw new WeaverDomainRejection("STALE_THREAD"); threads(access); }
            case WeaverNavigation.Page page -> render(access, current.snapshot(), current.view().kind(), current.view().title(), current.view().entries(), page.index());
            case WeaverNavigation.Diagnostics ignored -> diagnostics(access);
            case WeaverNavigation.Export export -> export(access, Objects.requireNonNull(current.snapshot()).ref(), export.id());
            case WeaverNavigation.Catalog catalog -> catalog(access, Objects.requireNonNull(current.snapshot()).ref(), catalog.id(), catalog.offset());
            case WeaverNavigation.Action action -> actionPreview(access, Objects.requireNonNull(current.snapshot()).ref(), action.id());
            case WeaverNavigation.EditParameter edit -> editParameter(access, requireDraft(edit.draftId()), edit.parameterId());
            case WeaverNavigation.AssignParameter assign -> assignParameter(access, requireDraft(assign.draftId()), assign.parameterId(), assign.value());
            case WeaverNavigation.ParameterCatalog catalog -> parameterCatalog(access, requireDraft(catalog.draftId()), catalog.parameterId(), catalog.offset());
            case WeaverNavigation.CatalogParameterValue selected -> selectCatalogParameter(access, requireDraft(selected.draftId()), selected.parameterId(), selected.stableId());
            case WeaverNavigation.Preview preview -> confirm(access, requireDraft(preview.draftId()));
            case WeaverNavigation.ConfirmFinal confirm -> confirm(access, requireDraft(confirm.draftId()), true);
            case WeaverNavigation.Execute execute -> execute(access, requireDraft(execute.draftId()));
            case WeaverNavigation.History ignored -> history(access);
            case WeaverNavigation.Receipt receipt -> receipt(access, receipt.id());
            case WeaverNavigation.Undo selected -> undoPreview(access, selected.receiptId());
            case WeaverNavigation.ImportThread imported -> importThread(access, Objects.requireNonNull(current.snapshot()).ref(), imported.importerId(), imported.threadId());
        }
    }
    private void quickApply(final Access access, final SubjectRef ref) {
        if (access.session().threads().active().isEmpty()) { threads(access); return; }
        fresh(access, ref, snapshot -> {
            access.session().selection().select(snapshot.ref());
            final var thread = access.session().threads().active().orElseThrow(() -> new WeaverDomainRejection("STALE_THREAD"));
            final ProviderContext context = context(access); final List<WeaverView.Entry> entries = new ArrayList<>();
            for (final var discovered : providers.discover(snapshot).providers().entrySet()) {
                for (final String id : discovered.getValue().imports()) {
                    final ImportDescriptor importer = providers.imports().get(id); final ActionDescriptor action = providers.actions().get(importer.actionId());
                    if (!action.integrityModes().contains(access.session().mode()) || discovered.getValue().blockedActions().containsKey(action.id())
                            || !types.compatible(thread.value(), importer.acceptedType(), importer.requiredCapabilities())) continue;
                    if (!providers.invoke(discovered.getKey(), context, provider -> provider.validateImport(context, snapshot, id, thread.value())).compatible()) continue;
                    entries.add(entry(action.label(), List.of(Component.text(importer.acceptedType().canonical())), "STRING", new WeaverNavigation.ImportThread(id, thread.id())));
                }
            }
            if (entries.isEmpty()) { feedback(access, "NO_COMPATIBLE_IMPORTER"); render(access, snapshot, WeaverViewKind.PARAMETER, Component.text("Szál alkalmazása"), entries, 0); }
            else if (entries.size() == 1) {
                final WeaverNavigation.ImportThread selected = (WeaverNavigation.ImportThread) entries.getFirst().navigation();
                importThread(access, snapshot.ref(), selected.importerId(), selected.threadId());
            } else render(access, snapshot, WeaverViewKind.PARAMETER, Component.text("Szál alkalmazása"), entries, 0);
        });
    }
    private void importThread(final Access access, final SubjectRef ref, final String id, final UUID threadId) {
        undoClaim = null;
        fresh(access, ref, snapshot -> {
            final var thread = access.session().threads().active().filter(value -> value.id().equals(threadId)).orElseThrow(() -> new WeaverDomainRejection("STALE_THREAD"));
            final String owner = providers.owner(id); final var discovery = providers.discover(snapshot).providers().get(owner);
            if (discovery == null || !discovery.imports().contains(id)) throw new WeaverDomainRejection("STALE_IMPORTER");
            final ImportDescriptor importer = providers.imports().get(id); final ActionDescriptor action = providers.actions().get(importer.actionId());
            final ProviderContext context = context(access);
            if (!types.compatible(thread.value(), importer.acceptedType(), importer.requiredCapabilities()) || discovery.blockedActions().containsKey(action.id())
                    || !providers.invoke(owner, context, provider -> provider.validateImport(context, snapshot, id, thread.value())).compatible()) throw new WeaverDomainRejection("INCOMPATIBLE_THREAD");
            draft = WeaverActionDraft.start(action, snapshot, access.session().mode()).with(importer.parameterId(), thread.value(), types);
            if (draft.validate(types).valid() && execution.available(action, draft.lifetime())) confirm(access, draft); else renderAction(access, draft);
        });
    }
    private void facet(final Access access, final SubjectRef ref, final String id) {
        fresh(access, ref, snapshot -> {
            final var view = WeaverFacetView.discover(providers, providers.discover(snapshot)).stream().filter(f -> f.facet().id().equals(id)).findFirst()
                    .orElseThrow(() -> new WeaverDomainRejection("STALE_FACET"));
            final ProviderContext context = context(access);
            final InspectionResult inspection = providers.invoke(view.providerId(), context, provider -> {
                final InspectionResult result = provider.inspect(context, snapshot, id);
                if (!result.facetId().equals(id)) throw new IllegalArgumentException("Inspection facet differs from manifest");
                result.facts().values().forEach(types::validate); return result;
            });
            final List<WeaverView.Entry> entries = facts(inspection.facts());
            inspection.notes().forEach(note -> entries.add(entry(note, List.of(), "PAPER", new WeaverNavigation.None())));
            view.actions().forEach(action -> entries.add(entry(action.label(), List.of(Component.text(action.risk().name())), "LEVER", new WeaverNavigation.Action(action.id()))));
            view.catalogs().forEach(catalog -> entries.add(entry(catalog.label(), List.of(), "BOOK", new WeaverNavigation.Catalog(catalog.id(), 0))));
            view.exports().forEach(export -> entries.add(entry(Component.text(export.id()), List.of(Component.text(export.outputType().canonical())), "STRING", new WeaverNavigation.Export(export.id()))));
            render(access, snapshot, WeaverViewKind.FACET, view.facet().label(), entries, 0);
        });
    }
    private void export(final Access access, final SubjectRef ref, final String id) {
        fresh(access, ref, snapshot -> {
            final String owner = providers.owner(id); final var discovered = providers.discover(snapshot).providers().get(owner);
            if (discovered == null || !discovered.exports().contains(id)) throw new WeaverDomainRejection("STALE_EXPORT");
            final ProviderContext context = context(access);
            final WeaverValue value = providers.invoke(owner, context, provider -> {
                final WeaverValue exported = provider.exportValue(context, snapshot, id).value().orElseThrow(() -> new WeaverDomainRejection("EXPORT_UNAVAILABLE"));
                final ExportDescriptor descriptor = providers.exports().get(id);
                if (!types.compatible(exported, descriptor.outputType(), descriptor.capabilities()) || !exported.sourceProvider().equals(owner)) throw new IllegalArgumentException("Export differs from manifest");
                return exported;
            });
            access.session().threads().add(value); threads(access);
        });
    }
    private void catalog(final Access access, final SubjectRef ref, final String id, final int offset) {
        fresh(access, ref, snapshot -> {
            final String owner = providers.owner(id); final var discovery = providers.discover(snapshot).providers().get(owner);
            if (discovery == null || !discovery.catalogs().contains(id)) throw new WeaverDomainRejection("STALE_CATALOG");
            final ProviderContext context = context(access);
            final CatalogPage page = providers.catalogPage(context, snapshot, id, new CatalogQuery("", offset, 43));
            final List<WeaverView.Entry> entries = new ArrayList<>();
            for (final CatalogEntry value : page.entries()) entries.add(entry(value.label(), List.of(Component.text(value.stableId())), "BOOK", new WeaverNavigation.None()));
            if (offset > 0) entries.add(entry(Component.text("Előző"), List.of(), "ARROW", new WeaverNavigation.Catalog(id, Math.max(0, offset - 43))));
            if (page.hasNext()) entries.add(entry(Component.text("Következő"), List.of(), "ARROW", new WeaverNavigation.Catalog(id, offset + 43)));
            render(access, snapshot, WeaverViewKind.CATALOG, providers.catalogs().get(id).label(), entries, 0);
        });
    }
    private void actionPreview(final Access access, final SubjectRef ref, final String id) {
        undoClaim = null;
        fresh(access, ref, snapshot -> {
            final ActionDescriptor descriptor = providers.actions().get(id); final String owner = providers.owner(id);
            final var discovery = providers.discover(snapshot).providers().get(owner);
            if (descriptor == null || discovery == null || !discovery.actions().contains(id)) throw new WeaverDomainRejection("STALE_ACTION");
            if (discovery.blockedActions().containsKey(id)) throw new WeaverDomainRejection("ACTION_UNAVAILABLE");
            draft = WeaverActionDraft.start(descriptor, snapshot, access.session().mode()); renderAction(access, draft);
        });
    }
    private WeaverActionDraft requireDraft(final UUID id) {
        final WeaverActionDraft current = draft;
        if (current == null || !current.id().equals(id)) throw new WeaverDomainRejection("STALE_ACTION_DRAFT");
        return current;
    }
    private void renderAction(final Access access, final WeaverActionDraft current) {
        final List<WeaverView.Entry> entries = new ArrayList<>();
        entries.add(entry(current.descriptor().label(), List.of(Component.text(current.descriptor().risk().name()),
                Component.text(current.integrityMode().name()), Component.text(current.lifetime().name())), "LEVER", new WeaverNavigation.None()));
        current.descriptor().parameters().forEach(parameter -> entries.add(entry(parameter.label(), List.of(Component.text(parameter.type().canonical()),
                Component.text(current.parameters().containsKey(parameter.id()) ? current.parameters().get(parameter.id()).payload().toString() : "Nincs érték"),
                Component.text(parameter.minimum().isPresent() ? parameter.minimum().getAsDouble() + " .. " + parameter.maximum().orElseThrow()
                        : parameter.maxTextLength().isPresent() ? "Max. " + parameter.maxTextLength().getAsInt() + " karakter" : "Típuskompatibilis érték")),
                "PAPER", undoClaim == null ? new WeaverNavigation.EditParameter(current.id(), parameter.id()) : new WeaverNavigation.None())));
        if (execution.available(current.descriptor(), current.lifetime())) entries.add(entry(Component.text("Áttekintés"), List.of(), "LIME_DYE", new WeaverNavigation.Preview(current.id())));
        else entries.add(entry(Component.text("Végrehajtás zárolva"), List.of(Component.text("DURABLE_EXECUTION_UNAVAILABLE")), "BARRIER", new WeaverNavigation.None()));
        render(access, current.snapshot(), WeaverViewKind.ACTION, current.descriptor().label(), entries, 0);
    }
    private static ActionParameter parameter(final WeaverActionDraft current, final String id) {
        return current.descriptor().parameters().stream().filter(p -> p.id().equals(id)).findFirst().orElseThrow(() -> new WeaverDomainRejection("UNKNOWN_PARAMETER"));
    }
    private void assignParameter(final Access access, final WeaverActionDraft current, final String id, final WeaverValue value) {
        if (undoClaim != null) throw new WeaverDomainRejection("UNDO_PARAMETERS_LOCKED");
        draft = current.with(id, value, types); renderAction(access, draft);
    }
    private void editParameter(final Access access, final WeaverActionDraft current, final String id) {
        final ActionParameter parameter = parameter(current, id);
        if (parameter.input() == ActionParameter.InputKind.CATALOG) { parameterCatalog(access, current, id, 0); return; }
        final List<WeaverView.Entry> entries = new ArrayList<>();
        for (final var thread : access.session().threads().snapshot()) {
            if (parameter.validate(thread.value(), types).valid()) entries.add(entry(Component.text(thread.value().sourceFacet()), List.of(Component.text(thread.value().type().canonical())),
                    "STRING", new WeaverNavigation.AssignParameter(current.id(), id, thread.value())));
        }
        if (parameter.input() == ActionParameter.InputKind.BOOLEAN) {
            for (final String value : List.of("true", "false")) entries.add(entry(Component.text(value), List.of(), "LEVER", new WeaverNavigation.AssignParameter(current.id(), id,
                    WeaverParameterInput.parse(parameter, value, types))));
        } else if (parameter.input() == ActionParameter.InputKind.SUBJECT || parameter.input() == ActionParameter.InputKind.AREA) {
            final Set<SubjectRef> subjects = new LinkedHashSet<>(access.session().selection().recent()); access.session().selection().current().ifPresent(subjects::add);
            for (final SubjectRef subject : subjects) {
                final WeaverValue value = new WeaverValue(parameter.type(), SubjectKeyCodec.payload(subject), "weaver", "weaver.input", Set.of(), System.currentTimeMillis());
                if (parameter.validate(value, types).valid()) entries.add(entry(Component.text(subject.kind().name()), List.of(), "COMPASS", new WeaverNavigation.AssignParameter(current.id(), id, value)));
            }
        } else if (parameter.input() != ActionParameter.InputKind.THREAD) {
            final var player = access.artifact().player(); if (player == null) throw new WeaverDomainRejection("AUTHORITY_REJECTED");
            player.closeInventory(); final long revision = access.session().nextView();
            input.open(access.artifact(), parameter, text -> {
                if (!valid(access) || !sessions.matches(access.artifact().owner(), access.session().id(), revision)) return;
                try { assignParameter(access, requireDraft(current.id()), id, WeaverParameterInput.parse(parameter, text, types)); }
                catch (final RuntimeException failure) { feedback(access, code(failure)); }
            });
            return;
        }
        render(access, current.snapshot(), WeaverViewKind.PARAMETER, parameter.label(), entries, 0);
    }
    private void parameterCatalog(final Access access, final WeaverActionDraft current, final String id, final int offset) {
        final ActionParameter parameter = parameter(current, id); final String catalogId = parameter.catalogId().orElseThrow();
        final ProviderContext context = context(access);
        final CatalogPage page = providers.catalogPage(context, current.snapshot(), catalogId, new CatalogQuery("", offset, 43));
        final List<WeaverView.Entry> entries = new ArrayList<>();
        for (final CatalogEntry value : page.entries()) {
            if (parameter.validate(value.value(), types).valid()) entries.add(entry(value.label(), List.of(Component.text(value.stableId())), "BOOK",
                    new WeaverNavigation.CatalogParameterValue(current.id(), id, value.stableId())));
        }
        if (offset > 0) entries.add(entry(Component.text("Előző"), List.of(), "ARROW", new WeaverNavigation.ParameterCatalog(current.id(), id, Math.max(0, offset - 43))));
        if (page.hasNext()) entries.add(entry(Component.text("Következő"), List.of(), "ARROW", new WeaverNavigation.ParameterCatalog(current.id(), id, offset + 43)));
        render(access, current.snapshot(), WeaverViewKind.PARAMETER, parameter.label(), entries, 0);
    }
    private void selectCatalogParameter(final Access access, final WeaverActionDraft current, final String id, final String stableId) {
        final String catalogId = parameter(current, id).catalogId().orElseThrow(); final ProviderContext context = context(access);
        final WeaverValue value = providers.resolveCatalog(context, current.snapshot(), catalogId, stableId);
        assignParameter(access, current, id, value);
    }
    private void confirm(final Access access, final WeaverActionDraft current) { confirm(access, current, false); }
    private void confirm(final Access access, final WeaverActionDraft current, final boolean finalStep) {
        current.validate(types).requireValid();
        freshAction(access, current, captured -> {
            final SubjectSnapshot snapshot = captured.snapshot();
            requireDraft(current.id());
            if (undoClaim != null && !undoClaim.expectedFingerprint().equals(snapshot.revisionFingerprint())) throw new WeaverDomainRejection("CONFLICT");
            if (finalStep && !current.snapshot().revisionFingerprint().equals(snapshot.revisionFingerprint())) throw new WeaverDomainRejection(undoClaim == null ? "STALE_SUBJECT" : "CONFLICT");
            final WeaverActionDraft confirmed = new WeaverActionDraft(UUID.randomUUID(), current.descriptor(), snapshot, current.lifetime(), current.integrityMode(), current.parameters());
            draft = confirmed;
            final List<WeaverView.Entry> entries = facts(snapshot.facts());
            current.parameters().forEach((id, value) -> entries.add(entry(Component.text(id), List.of(Component.text(value.payload().toString())), "PAPER", new WeaverNavigation.None())));
            final boolean strong = current.descriptor().risk().ordinal() >= RiskLevel.DESTRUCTIVE.ordinal();
            entries.add(entry(Component.text(strong && !finalStep ? "Feltételek elfogadása" : "Megerősítés"), List.of(Component.text(current.descriptor().risk().name()), Component.text(current.integrityMode().name())),
                    "LIME_DYE", strong && !finalStep ? new WeaverNavigation.ConfirmFinal(confirmed.id()) : new WeaverNavigation.Execute(confirmed.id())));
            render(access, snapshot, WeaverViewKind.CONFIRMATION, current.descriptor().label(), entries, 0);
        });
    }
    private void execute(final Access access, final WeaverActionDraft current) {
        if (!execution.available(current.descriptor(), current.lifetime())) throw new WeaverDomainRejection("DURABLE_EXECUTION_UNAVAILABLE");
        requireDraft(current.id()); draft = null;
        final var claim = Optional.ofNullable(undoClaim); undoClaim = null;
        if (!access.session().arming().consume(access.session().id(), WeaverArming.required(current.descriptor(), current.integrityMode()))) throw new WeaverDomainRejection("ARMING_REQUIRED");
        freshAction(access, current, captured -> {
            final SubjectSnapshot snapshot = captured.snapshot();
            if (!snapshot.revisionFingerprint().equals(current.snapshot().revisionFingerprint())) throw new WeaverDomainRejection(claim.isPresent() ? "CONFLICT" : "STALE_SUBJECT");
            if (captured.collection().isPresent() && captured.collection().get().targets().isEmpty()) throw new WeaverDomainRejection("AREA_EMPTY");
            if (!(captured.collection().isPresent() ? rate.tryAcquireArea(captured.collection().get().targets().size(), current.descriptor().rateCost()) : rate.tryAcquire(current.descriptor().rateCost()))) throw new WeaverDomainRejection("RATE_LIMITED");
            final long executionView = access.session().viewRevision();
            final ProviderContext context = new ProviderContext(authorize(access), types, current.lifetime(), current.integrityMode());
            final String owner = providers.owner(current.descriptor().id());
            if (!current.validate(types).valid()) throw new WeaverDomainRejection("INVALID_PARAMETERS");
            final ActionRequest request = current.request(types);
            final var discovered = providers.discover(snapshot).providers().get(owner);
            if (claim.isEmpty() && (discovered == null || !discovered.actions().contains(current.descriptor().id()))
                    || discovered != null && discovered.blockedActions().containsKey(current.descriptor().id())) {
                throw new WeaverDomainRejection("ACTION_UNAVAILABLE");
            }
            final var prepared = claim.isPresent() ? undo.prepare(context, claim.get(), snapshot, captured.collection()) : providers.invoke(owner, context, provider -> {
                final var plan = captured.collection().isPresent() ? provider.prepareArea(context, snapshot, request, captured.collection().get()) : provider.prepare(context, snapshot, request);
                if (!plan.descriptor().equals(current.descriptor())) throw new IllegalArgumentException("Prepared descriptor differs from manifest");
                return captured.collection().isPresent() ? areas.guard(captured.collection().get(), plan) : plan;
            });
            final Optional<hu.taliann.icesmp.dev.weaver.execution.PreparedEffects> effects = prepared.descriptor().requiresJournal()
                    ? Optional.of(providers.invoke(owner, context, provider -> {
                        final var planned = java.util.Objects.requireNonNull(provider.prepareEffects(context, snapshot, request, prepared));
                        if ((prepared.descriptor().integrityImpacts().contains(IntegrityImpact.TAINT_CREATED) || prepared.descriptor().integrityImpacts().contains(IntegrityImpact.EVENT_ORIGIN))
                                && planned.intent().targets().isEmpty()) throw new IllegalArgumentException("Created/event effects lack pre-mutation quarantine scope");
                        if (captured.collection().isEmpty()) return planned;
                        final Set<hu.taliann.icesmp.dev.weaver.integrity.WeaverInfluenceTarget> targets = new HashSet<>(planned.intent().targets());
                        targets.add(hu.taliann.icesmp.dev.weaver.integrity.WeaverInfluenceTarget.subject(snapshot.ref()));
                        for (final SubjectSnapshot target : captured.collection().get().targets()) if (target.ref() instanceof EntityRef || target.ref() instanceof PlayerRef) targets.add(hu.taliann.icesmp.dev.weaver.integrity.WeaverInfluenceTarget.subject(target.ref()));
                        return new hu.taliann.icesmp.dev.weaver.execution.PreparedEffects(new hu.taliann.icesmp.dev.weaver.integrity.WeaverEffectIntent(targets), planned.factory());
                    })) : Optional.empty();
            providers.observeExecution(owner, execution.execute(owner, context, snapshot, request, prepared, effects, claim, () -> {
                authorize(access);
                return new WeaverAuthorityToken(access.artifact().owner(), access.session().id(), System.nanoTime() + 30_000_000_000L,
                        () -> valid(access) && sessions.matches(access.artifact().owner(), access.session().id(), executionView), System::nanoTime);
            })).whenComplete((receipt, failure) -> access.artifact().onOwner(player -> {
                if (!valid(access)) return;
                if (failure != null) { access.session().arming().clear(); feedback(access, code(failure)); return; }
                access.session().receipt(receipt);
                if (sessions.matches(access.artifact().owner(), access.session().id(), executionView)) history(access);
            }, () -> unavailable(access)));
        });
    }
    private List<WeaverReceipt> receipts(final Access access) {
        final Map<UUID, WeaverReceipt> receipts = new HashMap<>(); access.session().receipts().forEach(receipt -> receipts.put(receipt.receiptId(), receipt));
        undo.history(authorize(access)).forEach(receipt -> receipts.put(receipt.receiptId(), receipt));
        return receipts.values().stream().sorted(Comparator.comparingLong(WeaverReceipt::createdAt).reversed().thenComparing(WeaverReceipt::receiptId)).toList();
    }
    private WeaverReceipt requireReceipt(final Access access, final UUID id) {
        return receipts(access).stream().filter(receipt -> receipt.receiptId().equals(id)).findFirst().orElseThrow(() -> new WeaverDomainRejection("RECEIPT_UNAVAILABLE"));
    }
    private void history(final Access access) {
        final WeaverAuthorityToken authority = authorize(access);
        final List<WeaverView.Entry> entries = receipts(access).stream().map(receipt -> entry(Component.text(receipt.actionId()),
                List.of(Component.text(receipt.status().name()), Component.text(undo.status(authority, receipt.operationId()).map(Enum::name).orElse("SESSION")), Component.text(receipt.operationId().toString())), "BOOK", new WeaverNavigation.Receipt(receipt.receiptId()))).toList();
        render(access, null, WeaverViewKind.HISTORY, Component.text("Műveletek"), entries, 0);
    }
    private void receipt(final Access access, final UUID id) {
        final WeaverReceipt receipt = requireReceipt(access, id); final List<WeaverView.Entry> entries = new ArrayList<>();
        entries.add(entry(Component.text(receipt.actionId()), List.of(Component.text(receipt.status().name()), Component.text(undo.status(authorize(access), receipt.operationId()).map(Enum::name).orElse("SESSION")), Component.text(receipt.risk().name()), Component.text(receipt.integrityMode().name()),
                Component.text(receipt.beforeFingerprint()), Component.text(receipt.afterFingerprint())), "BOOK", new WeaverNavigation.None()));
        entries.addAll(facts(receipt.before())); entries.addAll(facts(receipt.after()));
        if (undo.available(authorize(access), id)) entries.add(entry(Component.text("Feltételes visszavonás"), List.of(Component.text("Csak változatlan célállapot esetén.")), "RECOVERY_COMPASS", new WeaverNavigation.Undo(id)));
        render(access, null, WeaverViewKind.HISTORY, Component.text("Műveleti bizonylat"), entries, 0);
    }
    private void undoPreview(final Access access, final UUID id) {
        final WeaverReceipt receipt = requireReceipt(access, id);
        final var spec = receipt.undo().orElseThrow(() -> new WeaverDomainRejection("UNDO_UNAVAILABLE"));
        final var descriptor = providers.actions().get(spec.actionId());
        if (descriptor == null || !descriptor.integrityModes().contains(access.session().mode())) throw new WeaverDomainRejection("UNDO_ACTION_UNAVAILABLE");
        final Lifetime lifetime = descriptor.lifetimes().contains(Lifetime.ONE_SHOT) ? Lifetime.ONE_SHOT
                : descriptor.lifetimes().contains(receipt.lifetime()) ? receipt.lifetime() : descriptor.lifetimes().stream().sorted().findFirst().orElseThrow();
        final var candidate = new WeaverActionDraft(UUID.randomUUID(), descriptor, new SubjectSnapshot(WeaverUndoSubject.resolve(receipt), System.currentTimeMillis(), receipt.afterFingerprint(), Map.of()), lifetime, access.session().mode(), spec.parameters());
        freshAction(access, candidate, captured -> {
            final SubjectSnapshot snapshot = captured.snapshot(); final var target = undo.target(authorize(access), id, snapshot);
            undoClaim = target.claim(); draft = new WeaverActionDraft(UUID.randomUUID(), descriptor, snapshot, lifetime, access.session().mode(), target.receipt().undo().orElseThrow().parameters());
            draft.validate(types).requireValid(); renderAction(access, draft);
        });
    }

    private void threads(final Access access) {
        final List<WeaverView.Entry> entries = access.session().threads().snapshot().stream().map(thread -> entry(Component.text(thread.value().type().canonical()),
                List.of(Component.text(thread.value().sourceFacet())), "STRING", new WeaverNavigation.SelectThread(thread.id()))).toList();
        render(access, null, WeaverViewKind.THREAD_CASE, Component.text("Szálak"), entries, 0);
    }
    private void recent(final Access access) {
        final List<WeaverView.Entry> entries = access.session().selection().recent().stream().map(ref -> entry(Component.text(ref.kind().name()),
                List.of(), "COMPASS", new WeaverNavigation.Subject(ref))).toList();
        render(access, null, WeaverViewKind.RECENT, Component.text("Korábbi Subjectek"), entries, 0);
    }
    private void diagnostics(final Access access) {
        render(access, null, WeaverViewKind.DIAGNOSTICS, Component.text("Diagnosztika"), List.of(
                entry(Component.text("Providerek: " + providers.coverage().size()), List.of(), "BOOK", new WeaverNavigation.None()),
                entry(Component.text("Típusok: " + types.snapshot().size()), List.of(), "BOOK", new WeaverNavigation.None())), 0);
    }
    private void render(final Access access, final SubjectSnapshot snapshot, final WeaverViewKind kind, final Component title,
                        final List<WeaverView.Entry> entries, final int page) {
        authorize(access);
        final Map<Integer, WeaverView.Entry> controls = new HashMap<>();
        controls.put(45, entry(Component.text("Subject"), List.of(), "COMPASS", new WeaverNavigation.Refresh()));
        controls.put(46, entry(Component.text("Szálak"), List.of(), "STRING", new WeaverNavigation.Threads()));
        controls.put(47, entry(Component.text("Korábbi"), List.of(), "CLOCK", new WeaverNavigation.Recent()));
        controls.put(49, entry(Component.text("Diagnosztika"), List.of(), "BOOK", new WeaverNavigation.Diagnostics()));
        controls.put(48, entry(Component.text("Műveletek"), List.of(), "BOOK", new WeaverNavigation.History()));
        if (page > 0) controls.put(51, entry(Component.text("Előző"), List.of(), "ARROW", new WeaverNavigation.Page(page - 1)));
        if ((page + 1) * 45 < entries.size()) controls.put(52, entry(Component.text("Következő"), List.of(), "ARROW", new WeaverNavigation.Page(page + 1)));
        controls.put(53, entry(Component.text("Bezárás"), List.of(), "BARRIER", new WeaverNavigation.Close()));
        final WeaverView view = new WeaverView(access.session().id(), access.session().nextView(), kind, title, entries, controls, page);
        screen = new Screen(access, snapshot, view); gui.open(access.artifact(), view);
        final var model = access.session().threads().active().isPresent() ? DevArtifactPresentation.ModelState.THREAD_HELD
                : access.session().selection().current().isPresent() ? DevArtifactPresentation.ModelState.SUBJECT_LOCKED : DevArtifactPresentation.ModelState.IDLE;
        artifacts.renderState(access.artifact(), model);
    }
    private static List<WeaverView.Entry> facts(final Map<String, WeaverValue> facts) {
        final List<WeaverView.Entry> entries = new ArrayList<>();
        new TreeMap<>(facts).forEach((key, value) -> {
            final String text = value.payload().toString();
            entries.add(entry(Component.text(key), List.of(Component.text(text.substring(0, Math.min(256, text.length())))), "PAPER", new WeaverNavigation.None()));
        });
        return entries;
    }
    private static WeaverView.Entry entry(final Component label, final List<Component> lore, final String icon, final WeaverNavigation navigation) {
        return new WeaverView.Entry(label, lore, icon, navigation);
    }
    public void closeView(final UUID actor, final UUID session, final long revision) {
        if (sessions.matches(actor, session, revision)) sessions.current(actor).ifPresent(value -> { value.arming().clear(); value.nextView(); });
    }
    public void clearPlayerState(final UUID actor) {
        final Access access = active;
        if (access != null && access.artifact().owner().equals(actor)) unavailable(access);
    }
    private synchronized void unavailable(final Access access) {
        if (active == null || active.session() != access.session()) return;
        sessions.close(access.artifact().owner()); active = null; screen = null; draft = null; undoClaim = null;
    }
    public void shutdown() { closed = true; areas.close(); execution.close(); sessions.shutdown(); active = null; screen = null; draft = null; undoClaim = null; }
    private void feedback(final Access access, final String code) {
        access.artifact().onOwner(player -> player.sendActionBar(Component.text("Világszövő · " + code)), () -> unavailable(access));
    }
    private static String code(final Throwable failure) {
        Throwable root = failure;
        while (root instanceof java.util.concurrent.CompletionException && root.getCause() != null) root = root.getCause();
        return root instanceof WeaverDomainRejection rejected ? rejected.code() : root instanceof SecurityException ? "AUTHORITY_REJECTED" : "REQUEST_FAILED";
    }
}
