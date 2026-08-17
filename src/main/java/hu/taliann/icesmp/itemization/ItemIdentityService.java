package hu.taliann.icesmp.itemization;

import hu.taliann.icesmp.items.ItemDataFactory;
import hu.taliann.icesmp.items.WearablePresentation;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;

public final class ItemIdentityService {

    public enum Status {
        VALID,
        NOT_MANAGED,
        MALFORMED,
        INTEGRITY_MISMATCH,
        TEMPLATE_MISSING,
        TEMPLATE_VERSION_STALE,
        TEMPLATE_MISMATCH
    }

    public enum LegacyKind {
        NONE,
        RANDOM_AFFIX_GEAR,
        SINGLE_RUNE_GEAR,
        RELIC,
        UNMAPPED_CUSTOM_GEAR
    }

    public enum RuneMutationStatus {
        APPLIED,
        NOT_MANAGED,
        INVALID_IDENTITY,
        NO_SOCKET,
        SOCKETS_FULL,
        DUPLICATE_RUNE
    }

    public record Inspection(Status status, ItemInstance instance, ItemTemplate template, String diagnostic) {
        public boolean readable() {
            return status == Status.VALID || status == Status.TEMPLATE_VERSION_STALE;
        }
    }

    public record DuplicateReport(Set<UUID> exactDuplicates, Set<UUID> staleDuplicates) {
        public DuplicateReport {
            exactDuplicates = Set.copyOf(exactDuplicates);
            staleDuplicates = Set.copyOf(staleDuplicates);
        }

        public boolean clean() {
            return exactDuplicates.isEmpty() && staleDuplicates.isEmpty();
        }
    }

    public record RuneMutation(RuneMutationStatus status, ItemInstance instance, ItemTemplate template) {
        public boolean applied() {
            return status == RuneMutationStatus.APPLIED;
        }
    }

    private record EquippedAbilityCandidate(UUID itemId, ItemInstance instance, ItemTemplate template) { }

    private final ItemTemplateRegistry templates;
    private final NamespacedKey schemaKey;
    private final NamespacedKey uuidKey;
    private final NamespacedKey templateKey;
    private final NamespacedKey templateVersionKey;
    private final NamespacedKey payloadKey;
    private final NamespacedKey integrityKey;
    private final NamespacedKey abilityPowerKey;
    private final NamespacedKey signatureProjectionKey;
    private final NamespacedKey signatureTierProjectionKey;
    private final NamespacedKey legacyRarityKey;
    private final NamespacedKey legacyRuneKey;
    private final NamespacedKey relicKey;

    public ItemIdentityService(final JavaPlugin plugin, final ItemTemplateRegistry templates) {
        this.templates = java.util.Objects.requireNonNull(templates, "templates");
        this.schemaKey = new NamespacedKey(plugin, "item_schema");
        this.uuidKey = new NamespacedKey(plugin, "item_uuid");
        this.templateKey = new NamespacedKey(plugin, "item_template");
        this.templateVersionKey = new NamespacedKey(plugin, "item_template_version");
        this.payloadKey = new NamespacedKey(plugin, "item_instance");
        this.integrityKey = new NamespacedKey(plugin, "item_integrity");
        this.abilityPowerKey = new NamespacedKey(plugin, "ability_power");
        this.signatureProjectionKey = new NamespacedKey(plugin, "signature_item");
        this.signatureTierProjectionKey = new NamespacedKey(plugin, "signature_tier");
        this.legacyRarityKey = new NamespacedKey(plugin, "masterwork_quality");
        this.legacyRuneKey = new NamespacedKey(plugin, "rune_effect");
        this.relicKey = new NamespacedKey(plugin, "relic_id");
    }

    public ItemStack create(final String templateId, final String sourceTag, final String sourceId,
                            final UUID crafterId) {
        return create(templateId, sourceTag, sourceId, crafterId, "");
    }

    public ItemTemplate template(final String templateId) {
        return templates.require(templateId);
    }

    public ItemStack create(final String templateId, final String sourceTag, final String sourceId,
                            final UUID crafterId, final String creationLocation) {
        final ItemTemplate template = templates.require(templateId);
        final long now = System.currentTimeMillis();
        final ItemInstance instance = rollInstance(template, UUID.randomUUID(), sourceTag, sourceId,
                crafterId, creationLocation, now, () -> ThreadLocalRandom.current().nextDouble());
        return render(template, instance);
    }

    public ItemInstance rollInstance(final ItemTemplate template, final UUID itemId,
                                     final String sourceTag, final String sourceId,
                                     final UUID crafterId, final long createdAt,
                                     final DoubleSupplier qualitySource) {
        return rollInstance(template, itemId, sourceTag, sourceId, crafterId, "", createdAt, qualitySource);
    }

    public ItemInstance rollInstance(final ItemTemplate template, final UUID itemId,
                                     final String sourceTag, final String sourceId,
                                     final UUID crafterId, final String creationLocation,
                                     final long createdAt, final DoubleSupplier qualitySource) {
        final LinkedHashMap<String, ItemInstance.Roll> rolls = new LinkedHashMap<>();
        template.rolledStats().forEach((statId, range) -> {
            final double quality = clampQuality(qualitySource.getAsDouble());
            rolls.put(statId, new ItemInstance.Roll(range.valueAt(quality), quality));
        });
        final ItemHistoryEvent.Type eventType = initialEvent(sourceTag);
        return new ItemInstance(itemId, ItemInstance.CURRENT_SCHEMA, template.templateId(),
                template.templateVersion(), template.itemLevel(), rolls, List.of(),
                ItemInstance.AscensionState.base(),
                new ItemInstance.Origin(sourceTag, sourceId, crafterId, creationLocation, createdAt),
                Set.of(), 0L, List.of(new ItemHistoryEvent(eventType, createdAt, sourceId)));
    }

    public ItemInstance rollCraftedInstance(final ItemTemplate template, final UUID itemId,
                                            final UUID crafterId, final String crafterName,
                                            final String professionId, final String creationLocation,
                                            final boolean masterwork, final long createdAt,
                                            final double minimumQuality,
                                            final DoubleSupplier qualitySource) {
        if (!Double.isFinite(minimumQuality) || minimumQuality < 0.0D || minimumQuality > 1.0D) {
            throw new IllegalArgumentException("invalid crafted minimum quality");
        }
        final LinkedHashMap<String, ItemInstance.Roll> rolls = new LinkedHashMap<>();
        template.rolledStats().forEach((statId, range) -> {
            final double raw = clampQuality(qualitySource.getAsDouble());
            final double quality = minimumQuality + ((1.0D - minimumQuality) * raw);
            rolls.put(statId, new ItemInstance.Roll(range.valueAt(quality), quality));
        });
        return new ItemInstance(itemId, ItemInstance.CURRENT_SCHEMA, template.templateId(),
                template.templateVersion(), template.itemLevel(), rolls, List.of(),
                ItemInstance.AscensionState.base(),
                new ItemInstance.Origin("profession:craft", professionId, crafterId, crafterName,
                        professionId, creationLocation, masterwork, createdAt),
                Set.of(), 0L, List.of(new ItemHistoryEvent(ItemHistoryEvent.Type.CRAFTED,
                        createdAt, professionId)));
    }

    public ItemStack render(final ItemTemplate template, final ItemInstance instance) {
        requireMatching(template, instance);
        final Material material = Material.matchMaterial(template.material());
        if (material == null || material.isAir()) {
            throw new IllegalStateException("template material is unavailable: " + template.material());
        }
        final ItemStack item = new ItemStack(material);
        final ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(template.displayName(), color(template.rarity()))
                .decoration(TextDecoration.ITALIC, false));
        final ArrayList<Component> lore = new ArrayList<>();
        lore.add(Component.text(template.rarity().displayName() + " • Tárgyszint " + instance.itemLevel(),
                color(template.rarity())).decoration(TextDecoration.ITALIC, false));
        final String stageId = instance.ascension().stageId();
        if (template.levelRequirementAt(stageId) > 0) {
            lore.add(Component.text("Követelmény: " + template.levelRequirementAt(stageId) + ". szint",
                    NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }
        template.fixedStatsAt(stageId).forEach((id, value) -> lore.add(statLine(id, value, null)));
        instance.rolls().forEach((id, roll) -> lore.add(statLine(id, roll.value(), roll.quality())));
        if (!template.signatureEffectId().isBlank()) {
            final SignatureEffectRegistry.Definition effect =
                    SignatureEffectRegistry.require(template.signatureEffectId());
            lore.add(Component.text("✦ " + effect.displayName(),
                    NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text(effect.tooltip(), NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            final int signatureTier = template.signatureTierAt(stageId);
            if (signatureTier > 1) {
                lore.add(Component.text("Signature fokozat: " + signatureTier,
                        NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            }
        }
        if (template.runeSocketCountAt(stageId) > 0) {
            final int runeCapacity = template.runeSocketCountAt(stageId);
            lore.add(Component.text("◆ Rúnahely: " + instance.runes().size() + "/" + runeCapacity,
                    NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
            for (int socket = 0; socket < runeCapacity; socket++) {
                final String rune = socket < instance.runes().size()
                        ? displayRune(instance.runes().get(socket)) : "üres";
                lore.add(Component.text((socket < instance.runes().size() ? "  ◆ " : "  ◇ ")
                                + (socket + 1) + ". foglalat: " + rune,
                        socket < instance.runes().size() ? NamedTextColor.AQUA : NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            }
        }
        if (!template.setId().isBlank()) {
            final ItemSetDefinition set = templates.requireSet(template.setId());
            lore.add(Component.text("Szett: " + set.displayName(),
                            NamedTextColor.DARK_GREEN)
                    .decoration(TextDecoration.ITALIC, false));
            set.tierStats().forEach((pieces, stats) -> stats.forEach((id, value) ->
                    lore.add(Component.text("  " + pieces + " db: ", NamedTextColor.DARK_GRAY)
                            .decoration(TextDecoration.ITALIC, false)
                            .append(statLine(id, value, null)))));
        }
        if (!template.ascensionPath().isEmpty()) {
            lore.add(Component.text("Felemelkedés: " + instance.ascension().stageId(), NamedTextColor.LIGHT_PURPLE)
                    .decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.text("Forrás: " + instance.origin().sourceId(), NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, true));
        if (!instance.origin().creationLocation().isBlank()) {
            lore.add(Component.text("Készült: " + instance.origin().creationLocation(), NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, true));
        }
        if (instance.origin().crafterId() != null) {
            final String crafter = instance.origin().crafterNameSnapshot().isBlank()
                    ? instance.origin().crafterId().toString() : instance.origin().crafterNameSnapshot();
            lore.add(Component.text("Készítette: " + crafter
                    + (instance.origin().masterwork() ? " • Mestermű" : ""), NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, true));
        }
        for (final String line : template.loreAt(stageId)) {
            lore.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, true));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        applyStats(item, template, instance);
        writeIdentity(item, template, instance);
        refreshPresentation(item, template, instance);
        return item;
    }

    public void refreshPresentation(final ItemStack item, final ItemTemplate template) {
        refreshPresentation(item, template, null);
    }

    public void refreshPresentation(final ItemStack item, final ItemTemplate template,
                                    final ItemInstance instance) {
        final String stageId = instance == null ? "base" : instance.ascension().stageId();
        WearablePresentation.applyWearablePresentation(item,
                template.itemModelAt(stageId), template.equipmentAssetAt(stageId));
        ItemDataFactory.hideAttributeTooltip(item);
        ItemDataFactory.applyRarity(item, ItemDataFactory.vanillaRarityOf(template.rarity().id()));
    }

    public Inspection inspect(final ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return new Inspection(Status.NOT_MANAGED, null, null, "nincs item identity");
        }
        final PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        final boolean any = pdc.has(schemaKey) || pdc.has(uuidKey) || pdc.has(templateKey)
                || pdc.has(templateVersionKey) || pdc.has(payloadKey) || pdc.has(integrityKey);
        if (!any) return new Inspection(Status.NOT_MANAGED, null, null, "nincs item identity");
        final Integer schema = pdc.get(schemaKey, PersistentDataType.INTEGER);
        final String uuid = pdc.get(uuidKey, PersistentDataType.STRING);
        final String templateId = pdc.get(templateKey, PersistentDataType.STRING);
        final Integer templateVersion = pdc.get(templateVersionKey, PersistentDataType.INTEGER);
        final String payload = pdc.get(payloadKey, PersistentDataType.STRING);
        final String integrity = pdc.get(integrityKey, PersistentDataType.STRING);
        if (schema == null || uuid == null || templateId == null || templateVersion == null
                || payload == null || integrity == null) {
            return new Inspection(Status.MALFORMED, null, null, "hiányos item identity PDC");
        }
        if (!MessageDigest.isEqual(integrity.getBytes(StandardCharsets.US_ASCII),
                digest(payload).getBytes(StandardCharsets.US_ASCII))) {
            return new Inspection(Status.INTEGRITY_MISMATCH, null, null, "item payload checksum eltérés");
        }
        final ItemInstance instance;
        try {
            instance = ItemInstanceCodec.decode(payload);
        } catch (final RuntimeException malformed) {
            return new Inspection(Status.MALFORMED, null, null, malformed.getMessage());
        }
        if (schema != instance.schemaVersion() || !uuid.equals(instance.itemId().toString())
                || !templateId.equals(instance.templateId()) || templateVersion != instance.templateVersion()) {
            return new Inspection(Status.INTEGRITY_MISMATCH, instance, null,
                    "item identity index nem egyezik a payloaddal");
        }
        final Optional<ItemTemplate> resolved = templates.find(instance.templateId());
        if (resolved.isEmpty()) {
            return new Inspection(Status.TEMPLATE_MISSING, instance, null, "hiányzó item template");
        }
        final ItemTemplate template = resolved.orElseThrow();
        if (item.getAmount() != 1 || !item.getType().name().equals(template.material())) {
            return new Inspection(Status.TEMPLATE_MISMATCH, instance, template,
                    "material vagy stackméret eltér a templatetől");
        }
        if (instance.templateVersion() > template.templateVersion()) {
            return new Inspection(Status.TEMPLATE_MISMATCH, instance, template,
                    "az item újabb, mint a szerver template-verziója");
        }
        if (instance.templateVersion() < template.templateVersion()) {
            return new Inspection(Status.TEMPLATE_VERSION_STALE, instance, template,
                    "migrálandó template-verzió");
        }
        final String signatureProjection = pdc.get(signatureProjectionKey, PersistentDataType.STRING);
        final Integer signatureTierProjection = pdc.get(signatureTierProjectionKey, PersistentDataType.INTEGER);
        if ((!template.signatureEffectId().isBlank()
                && !template.signatureEffectId().equals(signatureProjection))
                || (template.signatureEffectId().isBlank() && signatureProjection != null)) {
            return new Inspection(Status.TEMPLATE_MISMATCH, instance, template,
                    "signature effect projekció eltér a templatetől");
        }
        if (instance.schemaVersion() >= 2 && !java.util.Objects.equals(signatureTierProjection,
                template.signatureTierAt(instance.ascension().stageId()))) {
            return new Inspection(Status.TEMPLATE_MISMATCH, instance, template,
                    "signature tier projekció eltér a templatetől");
        }
        try {
            requireMatching(template, instance);
        } catch (final IllegalArgumentException mismatch) {
            return new Inspection(Status.TEMPLATE_MISMATCH, instance, template, mismatch.getMessage());
        }
        return new Inspection(Status.VALID, instance, template, "érvényes item identity");
    }

    public Optional<ItemInstance> instanceOf(final ItemStack item) {
        final Inspection inspection = inspect(item);
        return inspection.readable() ? Optional.of(inspection.instance()) : Optional.empty();
    }

    private static String displayRune(final String runeId) {
        final String normalized = ItemStatCatalog.normalizeId(runeId);
        final String withoutPrefix = normalized.startsWith("runa_")
                ? normalized.substring("runa_".length()) : normalized;
        if (withoutPrefix.isBlank()) return normalized;
        final String display = withoutPrefix.replace('_', ' ');
        return Character.toUpperCase(display.charAt(0)) + display.substring(1);
    }

    public List<String> runesOf(final ItemStack item) {
        final Inspection inspection = inspect(item);
        if (inspection.readable()) return inspection.instance().runes();
        if (inspection.status() != Status.NOT_MANAGED || item == null || !item.hasItemMeta()) {
            return List.of();
        }
        final String legacy = item.getItemMeta().getPersistentDataContainer()
                .get(legacyRuneKey, PersistentDataType.STRING);
        return legacy == null || legacy.isBlank() ? List.of() : List.of(legacy);
    }

    public boolean hasRune(final ItemStack item, final String runeId) {
        if (runeId == null || runeId.isBlank()) return false;
        final String normalized = ItemStatCatalog.normalizeId(runeId);
        return runesOf(item).contains(normalized);
    }

    public RuneMutation applyRune(final ItemStack item, final String runeId, final long occurredAt) {
        final Inspection inspection = inspect(item);
        if (inspection.status() == Status.NOT_MANAGED) {
            return new RuneMutation(RuneMutationStatus.NOT_MANAGED, null, null);
        }
        if (inspection.status() != Status.VALID) {
            return new RuneMutation(RuneMutationStatus.INVALID_IDENTITY,
                    inspection.instance(), inspection.template());
        }
        final ItemInstance current = inspection.instance();
        final ItemTemplate template = inspection.template();
        final int socketCount = template.runeSocketCountAt(current.ascension().stageId());
        if (socketCount == 0) {
            return new RuneMutation(RuneMutationStatus.NO_SOCKET, current, template);
        }
        if (current.runes().size() >= socketCount) {
            return new RuneMutation(RuneMutationStatus.SOCKETS_FULL, current, template);
        }
        final String normalized = ItemStatCatalog.normalizeId(runeId);
        if (current.runes().contains(normalized)) {
            return new RuneMutation(RuneMutationStatus.DUPLICATE_RUNE, current, template);
        }
        final ItemInstance updated = current.addRune(normalized, occurredAt);
        requireMatching(template, updated);
        writeIdentity(item, template, updated);
        final ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(legacyRuneKey, PersistentDataType.STRING,
                updated.runes().get(0));
        item.setItemMeta(meta);
        return new RuneMutation(RuneMutationStatus.APPLIED, updated, template);
    }

    public Set<UUID> duplicateIds(final Collection<ItemStack> items) {
        final DuplicateReport report = inspectDuplicates(items);
        final LinkedHashSet<UUID> duplicates = new LinkedHashSet<>(report.exactDuplicates());
        duplicates.addAll(report.staleDuplicates());
        return Set.copyOf(duplicates);
    }

    public DuplicateReport inspectDuplicates(final Collection<ItemStack> items) {
        final Map<UUID, Long> revisions = new LinkedHashMap<>();
        final LinkedHashSet<UUID> exact = new LinkedHashSet<>();
        final LinkedHashSet<UUID> stale = new LinkedHashSet<>();
        if (items == null) return new DuplicateReport(Set.of(), Set.of());
        for (final ItemStack item : items) {
            instanceOf(item).ifPresent(instance -> {
                final Long previous = revisions.putIfAbsent(instance.itemId(), instance.mutationRevision());
                if (previous == null) return;
                if (previous == instance.mutationRevision()) {
                    if (!stale.contains(instance.itemId())) exact.add(instance.itemId());
                } else {
                    stale.add(instance.itemId());
                    exact.remove(instance.itemId());
                }
            });
        }
        return new DuplicateReport(exact, stale);
    }

    public LegacyKind classifyLegacy(final ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()
                || inspect(item).status() != Status.NOT_MANAGED) return LegacyKind.NONE;
        final PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        if (pdc.has(relicKey, PersistentDataType.STRING)) return LegacyKind.RELIC;
        if (pdc.has(legacyRarityKey, PersistentDataType.STRING)) return LegacyKind.RANDOM_AFFIX_GEAR;
        if (pdc.has(legacyRuneKey, PersistentDataType.STRING)) return LegacyKind.SINGLE_RUNE_GEAR;
        if (item.getMaxStackSize() == 1 && (item.getItemMeta().hasDisplayName()
                || item.getItemMeta().hasLore())) return LegacyKind.UNMAPPED_CUSTOM_GEAR;
        return LegacyKind.NONE;
    }

    public double abilityPowerOf(final ItemStack item) {
        final Inspection inspection = inspect(item);
        if (inspection.status() != Status.VALID) return 0.0D;
        double value = inspection.template().fixedStatsAt(inspection.instance().ascension().stageId())
                .getOrDefault("ability_power", 0.0D);
        final ItemInstance.Roll roll = inspection.instance().rolls().get("ability_power");
        if (roll != null) value += roll.value();
        return value;
    }

    /**
     * Canonical equipped ability power is slot-aware and duplicate-safe. A copied UUID, stale
     * revision, invalid checksum or wrong-slot item contributes neither direct power nor a set piece.
     */
    public double equippedAbilityPower(final Player player) {
        if (player == null) return 0.0D;
        final ArrayList<EquippedAbilityCandidate> candidates = new ArrayList<>(6);
        addAbilityCandidate(candidates, player.getInventory().getItemInMainHand(), ItemTemplate.Slot.MAIN_HAND);
        addAbilityCandidate(candidates, player.getInventory().getItemInOffHand(), ItemTemplate.Slot.OFF_HAND);
        addAbilityCandidate(candidates, player.getInventory().getHelmet(), ItemTemplate.Slot.HEAD);
        addAbilityCandidate(candidates, player.getInventory().getChestplate(), ItemTemplate.Slot.CHEST);
        addAbilityCandidate(candidates, player.getInventory().getLeggings(), ItemTemplate.Slot.LEGS);
        addAbilityCandidate(candidates, player.getInventory().getBoots(), ItemTemplate.Slot.FEET);
        final LinkedHashMap<UUID, Integer> counts = new LinkedHashMap<>();
        for (final EquippedAbilityCandidate candidate : candidates) {
            counts.merge(candidate.itemId(), 1, Integer::sum);
        }
        double total = 0.0D;
        final LinkedHashMap<String, Integer> setPieces = new LinkedHashMap<>();
        for (final EquippedAbilityCandidate candidate : candidates) {
            if (counts.getOrDefault(candidate.itemId(), 0) != 1) continue;
            final ItemTemplate template = candidate.template();
            final ItemInstance instance = candidate.instance();
            total += template.fixedStatsAt(instance.ascension().stageId())
                    .getOrDefault("ability_power", 0.0D);
            final ItemInstance.Roll roll = instance.rolls().get("ability_power");
            if (roll != null) total += roll.value();
            if (!template.setId().isBlank()) setPieces.merge(template.setId(), 1, Integer::sum);
        }
        for (final Map.Entry<String, Integer> entry : setPieces.entrySet()) {
            total += templates.requireSet(entry.getKey()).activeStats(entry.getValue())
                    .getOrDefault("ability_power", 0.0D);
        }
        return total;
    }

    private void addAbilityCandidate(final List<EquippedAbilityCandidate> result, final ItemStack item,
                                     final ItemTemplate.Slot equippedSlot) {
        final Inspection inspection = inspect(item);
        if (inspection.status() != Status.VALID) return;
        final ItemTemplate.Slot authored = inspection.template().slot();
        final boolean fits = authored == equippedSlot || (equippedSlot == ItemTemplate.Slot.MAIN_HAND
                && authored == ItemTemplate.Slot.TWO_HAND);
        if (!fits) return;
        result.add(new EquippedAbilityCandidate(inspection.instance().itemId(),
                inspection.instance(), inspection.template()));
    }

    private void applyStats(final ItemStack item, final ItemTemplate template, final ItemInstance instance) {
        final LinkedHashMap<String, Double> totals = new LinkedHashMap<>(
                template.fixedStatsAt(instance.ascension().stageId()));
        instance.rolls().forEach((id, roll) -> totals.merge(id, roll.value(), Double::sum));
        if (template.baseDamage() > 0.0D) totals.merge("attack_damage", template.baseDamage(), Double::sum);
        if (template.baseArmor() > 0.0D) totals.merge("armor", template.baseArmor(), Double::sum);
        final ArrayList<String> attributes = new ArrayList<>();
        double abilityPower = 0.0D;
        for (final Map.Entry<String, Double> entry : totals.entrySet()) {
            if ("ability_power".equals(entry.getKey())) abilityPower += entry.getValue();
            else attributes.add(entry.getKey() + ':' + entry.getValue());
        }
        ItemDataFactory.applyAttributeModifiers(item, attributes, false);
        if (abilityPower != 0.0D) {
            final ItemMeta meta = item.getItemMeta();
            meta.getPersistentDataContainer().set(abilityPowerKey, PersistentDataType.DOUBLE, abilityPower);
            item.setItemMeta(meta);
        }
    }

    private void writeIdentity(final ItemStack item, final ItemTemplate template,
                               final ItemInstance instance) {
        final String payload = ItemInstanceCodec.encode(instance);
        final ItemMeta meta = item.getItemMeta();
        final PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(schemaKey, PersistentDataType.INTEGER, instance.schemaVersion());
        pdc.set(uuidKey, PersistentDataType.STRING, instance.itemId().toString());
        pdc.set(templateKey, PersistentDataType.STRING, instance.templateId());
        pdc.set(templateVersionKey, PersistentDataType.INTEGER, instance.templateVersion());
        pdc.set(payloadKey, PersistentDataType.STRING, payload);
        pdc.set(integrityKey, PersistentDataType.STRING, digest(payload));
        if (template.signatureEffectId().isBlank()) {
            pdc.remove(signatureProjectionKey);
        } else {
            pdc.set(signatureProjectionKey, PersistentDataType.STRING, template.signatureEffectId());
        }
        pdc.set(signatureTierProjectionKey, PersistentDataType.INTEGER,
                template.signatureTierAt(instance.ascension().stageId()));
        item.setItemMeta(meta);
    }

    private static void requireMatching(final ItemTemplate template, final ItemInstance instance) {
        if (!template.templateId().equals(instance.templateId())
                || template.templateVersion() != instance.templateVersion()) {
            throw new IllegalArgumentException("item instance does not match template");
        }
        if (!template.matchesAscensionState(instance.ascension().stageId(),
                instance.ascension().stageIndex())) {
            throw new IllegalArgumentException("item ascension state does not match template path");
        }
        final Map<String, ItemTemplate.StatRange> authoredRanges =
                template.rolledStatsAt(instance.ascension().stageId());
        for (final String stat : instance.rolls().keySet()) {
            if (!authoredRanges.containsKey(stat)) {
                throw new IllegalArgumentException("item roll is not declared by template: " + stat);
            }
            final ItemInstance.Roll roll = instance.rolls().get(stat);
            if (!authoredRanges.get(stat).matches(roll.value(), roll.quality())) {
                throw new IllegalArgumentException("item roll does not match authored range: " + stat);
            }
        }
        if (!instance.rolls().keySet().equals(authoredRanges.keySet())) {
            throw new IllegalArgumentException("item is missing an authored roll");
        }
        if (instance.runes().size() > template.runeSocketCountAt(instance.ascension().stageId())) {
            throw new IllegalArgumentException("item contains more runes than its template permits");
        }
    }

    private static Component statLine(final String statId, final double value, final Double quality) {
        final String formatted = Math.abs(value - Math.rint(value)) < 0.000_001D
                ? Long.toString(Math.round(value)) : String.format(Locale.ROOT, "%.2f", value);
        final String qualityText = quality == null ? "" : " [" + Math.round(quality * 100.0D) + "%]";
        return Component.text((value >= 0.0D ? "+" : "") + formatted + " "
                        + ItemStatCatalog.require(statId).displayName() + qualityText,
                value >= 0.0D ? NamedTextColor.GRAY : NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false);
    }

    private static ItemHistoryEvent.Type initialEvent(final String rawSource) {
        final String source = rawSource == null ? "" : rawSource.toLowerCase(Locale.ROOT);
        if (source.contains("craft") || source.contains("profession")) return ItemHistoryEvent.Type.CRAFTED;
        if (source.contains("boss")) return ItemHistoryEvent.Type.BOSS_DROP;
        if (source.contains("combat")) return ItemHistoryEvent.Type.FOUND;
        if (source.contains("quest")) return ItemHistoryEvent.Type.QUEST_REWARD;
        if (source.contains("found") || source.contains("discover") || source.contains("explor")) {
            return ItemHistoryEvent.Type.FOUND;
        }
        return ItemHistoryEvent.Type.CREATED;
    }

    private static double clampQuality(final double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("roll quality source returned non-finite value");
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    private static NamedTextColor color(final ItemRarity rarity) {
        return switch (rarity) {
            case COMMON -> NamedTextColor.WHITE;
            case UNCOMMON -> NamedTextColor.GREEN;
            case RARE -> NamedTextColor.BLUE;
            case EPIC -> NamedTextColor.DARK_PURPLE;
            case LEGENDARY -> NamedTextColor.GOLD;
            case MYTHIC -> NamedTextColor.RED;
        };
    }

    private static String digest(final String payload) {
        try {
            final byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8));
            final StringBuilder result = new StringBuilder(bytes.length * 2);
            for (final byte value : bytes) result.append(String.format(Locale.ROOT, "%02x", value));
            return result.toString();
        } catch (final NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
