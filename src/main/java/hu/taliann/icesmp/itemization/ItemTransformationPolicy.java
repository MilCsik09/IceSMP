package hu.taliann.icesmp.itemization;

import hu.taliann.icesmp.managers.ConfigManager;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Canonical authority for every vanilla item transformation decision. */
public final class ItemTransformationPolicy {

    public enum Domain {
        VANILLA_SURVIVAL,
        BASIC_SURVIVAL_GEAR,
        CANONICAL_MMO_GEAR,
        LEGACY
    }

    public enum Transformation {
        VANILLA_CRAFT_INPUT,
        VANILLA_RECIPE_OUTPUT,
        FURNACE_OR_CAMPFIRE,
        VANILLA_CONSUMER_SINK,
        STONECUTTER,
        ANVIL_RENAME,
        ANVIL_MATERIAL_REPAIR,
        ANVIL_ITEM_REPAIR,
        ANVIL_ENCHANT_MERGE,
        ENCHANTING_TABLE,
        SMITHING_UPGRADE,
        ARMOR_TRIM,
        GRINDSTONE,
        VILLAGER_TRADE,
        DURABILITY_DAMAGE,
        ITEM_BREAK,
        CONTAINER_MOVE,
        DROP_OR_DEATH,
        MARKET_TRANSFER,
        CANONICAL_SALVAGE,
        CANONICAL_PROFESSION_MUTATION,
        CANONICAL_AUTHORED_PRODUCER
    }

    public enum Action {
        ALLOW,
        DENY,
        CANONICAL_MUTATION_REQUIRED
    }

    public enum DurabilityMode {
        VANILLA
    }

    public record Classification(Domain domain, ItemIdentityService.Status identityStatus,
                                 ItemIdentityService.LegacyKind legacyKind, String diagnostic) {
        public boolean canonicalIdentityValid() {
            return identityStatus == ItemIdentityService.Status.VALID;
        }

        public boolean transformationProtected() {
            return domain == Domain.CANONICAL_MMO_GEAR || domain == Domain.LEGACY;
        }
    }

    public record Decision(Action action, String reasonKey, String diagnostic) {
        public boolean directlyAllowed() {
            return action == Action.ALLOW;
        }
    }

    public record Rules(long generation, boolean basicSurvivalGear,
                        boolean canonicalRename, boolean canonicalArmorTrim,
                        boolean canonicalVanillaRepair, boolean canonicalSmithingUpgrade,
                        boolean canonicalGrindstone, boolean canonicalEnchanting,
                        Set<String> allowedCanonicalEnchantments,
                        DurabilityMode durabilityMode, long feedbackCooldownMillis,
                        boolean valid, List<String> errors) {
        public Rules {
            allowedCanonicalEnchantments = Set.copyOf(allowedCanonicalEnchantments);
            errors = List.copyOf(errors);
        }

        public static Rules safeDefaults() {
            return new Rules(0L, true, false, false, false, false, false, false,
                    Set.of(), DurabilityMode.VANILLA, 2_000L, true, List.of());
        }
    }

    private static final String ROOT = "itemization.vanilla-boundary.";
    private static final Set<String> BASIC_EXACT = Set.of(
            "BOW", "CROSSBOW", "SHIELD", "TRIDENT", "MACE");

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final ItemIdentityService identity;
    private volatile Rules cached = Rules.safeDefaults();

    public ItemTransformationPolicy(final JavaPlugin plugin, final ConfigManager config,
                                    final ItemIdentityService identity) {
        this.plugin = java.util.Objects.requireNonNull(plugin, "plugin");
        this.config = java.util.Objects.requireNonNull(config, "config");
        this.identity = java.util.Objects.requireNonNull(identity, "identity");
        identity.setCanonicalStateValidator(this::illegalCanonicalState);
        rules();
    }

    public Classification classify(final ItemStack item) {
        final ItemIdentityService.Inspection inspected = identity.inspectIdentity(item);
        if (inspected.status() != ItemIdentityService.Status.NOT_MANAGED) {
            return new Classification(Domain.CANONICAL_MMO_GEAR, inspected.status(),
                    ItemIdentityService.LegacyKind.NONE, inspected.diagnostic());
        }
        final ItemIdentityService.LegacyKind legacy = identity.classifyLegacy(item);
        // Display name/lore alone is cosmetic vanilla state. It is not a durable IceSMP identity
        // footprint, so a legitimately renamed/lored sword or armor piece remains BASIC.
        if (legacy != ItemIdentityService.LegacyKind.NONE
                && legacy != ItemIdentityService.LegacyKind.UNMAPPED_CUSTOM_GEAR) {
            return new Classification(Domain.LEGACY, inspected.status(), legacy,
                    "legacy:" + legacy.name().toLowerCase(Locale.ROOT));
        }
        final Material material = item == null ? Material.AIR : item.getType();
        final Domain domain = rules().basicSurvivalGear() && isBasicSurvivalGear(material)
                ? Domain.BASIC_SURVIVAL_GEAR : Domain.VANILLA_SURVIVAL;
        return new Classification(domain, inspected.status(), legacy, "vanilla:" + material.name());
    }

    public Decision decide(final Classification item, final Transformation transformation) {
        return decide(item.domain(), item.canonicalIdentityValid(), transformation, rules());
    }

    public static Decision decide(final Domain domain, final boolean canonicalIdentityValid,
                                  final Transformation transformation, final Rules rules) {
        java.util.Objects.requireNonNull(domain, "domain");
        java.util.Objects.requireNonNull(transformation, "transformation");
        java.util.Objects.requireNonNull(rules, "rules");
        if (domain == Domain.VANILLA_SURVIVAL || domain == Domain.BASIC_SURVIVAL_GEAR) {
            if (transformation == Transformation.CANONICAL_SALVAGE
                    || transformation == Transformation.CANONICAL_PROFESSION_MUTATION) {
                return deny("itemization-vanilla-boundary-salvage",
                        "Vanilla/basic tárgy nem konvertálható canonical gazdasági értékké.");
            }
            return allow("A vanilla survival tárgy az eredeti Minecraft-szabályt követi.");
        }
        if (domain == Domain.LEGACY) {
            return switch (transformation) {
                case CONTAINER_MOVE, DROP_OR_DEATH ->
                        allow("A legacy tárgy mozgatható, de nem alakítható át.");
                default -> deny("itemization-vanilla-boundary-legacy",
                        "A legacy metadata vanilla transzformációval elveszne.");
            };
        }
        if (!rules.valid()) {
            return deny("itemization-vanilla-boundary-config-invalid",
                    "A vanilla boundary konfigurációja hibás; canonical tárgynál fail-closed.");
        }
        if (!canonicalIdentityValid) {
            return switch (transformation) {
                case CONTAINER_MOVE, DROP_OR_DEATH ->
                        allow("A hibás tárgy elkülönítéshez mozgatható, de nem használható.");
                default -> deny("itemization-vanilla-boundary-invalid",
                        "A canonical identity nem VALID.");
            };
        }
        return switch (transformation) {
            case DURABILITY_DAMAGE, ITEM_BREAK, CONTAINER_MOVE, DROP_OR_DEATH, MARKET_TRANSFER ->
                    allow("A művelet nem írja át az ItemInstance authorityt.");
            case CANONICAL_AUTHORED_PRODUCER ->
                    allow("Explicit IceSMP producer hozza létre a canonical példányt.");
            case CANONICAL_SALVAGE, CANONICAL_PROFESSION_MUTATION ->
                    mutation("A meglévő ItemMutationCoordinator/WAL kötelező.");
            case ANVIL_RENAME -> rules.canonicalRename()
                    ? mutation("A rename csak journalolt cosmetic mutationként támogatható.")
                    : deny("itemization-vanilla-boundary-rename", "Canonical rename tiltott.");
            case ARMOR_TRIM -> rules.canonicalArmorTrim()
                    ? mutation("A trim csak journalolt cosmetic mutationként támogatható.")
                    : deny("itemization-vanilla-boundary-trim", "Canonical armor trim tiltott.");
            case ANVIL_MATERIAL_REPAIR, ANVIL_ITEM_REPAIR -> rules.canonicalVanillaRepair()
                    ? mutation("A canonical repair durable mutationt igényel.")
                    : deny("itemization-vanilla-boundary-repair", "Canonical vanilla repair tiltott.");
            case SMITHING_UPGRADE -> rules.canonicalSmithingUpgrade()
                    ? mutation("A canonical smithing upgrade durable mutationt igényel.")
                    : deny("itemization-vanilla-boundary-smithing", "Canonical smithing upgrade tiltott.");
            case GRINDSTONE -> rules.canonicalGrindstone()
                    ? mutation("A canonical grindstone művelet durable mutationt igényel.")
                    : deny("itemization-vanilla-boundary-grindstone", "Canonical grindstone tiltott.");
            case ENCHANTING_TABLE, ANVIL_ENCHANT_MERGE -> rules.canonicalEnchanting()
                    ? mutation("A canonical enchant durable mutationt igényel.")
                    : deny("itemization-vanilla-boundary-enchant", "Canonical vanilla enchant tiltott.");
            case VANILLA_CRAFT_INPUT, VANILLA_RECIPE_OUTPUT, FURNACE_OR_CAMPFIRE,
                    VANILLA_CONSUMER_SINK, STONECUTTER, VILLAGER_TRADE ->
                    deny("itemization-vanilla-boundary-blocked",
                    "A vanilla út nem canonical producer és lemoshatná az identityt.");
        };
    }

    public Rules rules() {
        final ConfigManager.ConfigSnapshot source = config.snapshot();
        final Rules current = cached;
        if (current.generation() == source.generation()) return current;
        synchronized (this) {
            if (cached.generation() == source.generation()) return cached;
            final Rules replacement = parse(source);
            cached = replacement;
            if (!replacement.valid()) {
                plugin.getLogger().severe("Invalid vanilla crafting boundary config; canonical transformations fail closed: "
                        + String.join("; ", replacement.errors()));
            }
            return replacement;
        }
    }

    /** Empty means legal. Non-empty diagnostics turn ItemIdentityService.inspect into POLICY_VIOLATION. */
    public String illegalCanonicalState(final ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return "";
        final Rules rules = rules();
        if (!rules.valid()) return "hibás vanilla boundary config";
        final LinkedHashSet<String> present = new LinkedHashSet<>();
        item.getEnchantments().keySet().forEach(enchantment -> present.add(key(enchantment)));
        final ItemMeta meta = item.getItemMeta();
        if (meta instanceof EnchantmentStorageMeta storage) {
            storage.getStoredEnchants().keySet().forEach(enchantment -> present.add(key(enchantment)));
        }
        present.removeAll(rules.allowedCanonicalEnchantments());
        return present.isEmpty() ? "" : "tiltott canonical enchant: " + String.join(",", present);
    }

    public static boolean isBasicSurvivalGear(final Material material) {
        if (material == null || material == Material.AIR
                || material == Material.CAVE_AIR || material == Material.VOID_AIR) return false;
        final String name = material.name();
        if (BASIC_EXACT.contains(name) || name.endsWith("_SWORD")) return true;
        if (name.equals("TURTLE_HELMET") || name.equals("ELYTRA")) return true;
        return name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS");
    }

    private Rules parse(final ConfigManager.ConfigSnapshot source) {
        final ArrayList<String> errors = new ArrayList<>();
        final boolean basic = bool(source, ROOT + "basic-survival-gear", true, errors);
        final boolean rename = bool(source, ROOT + "canonical.rename", false, errors);
        final boolean trim = bool(source, ROOT + "canonical.armor-trim", false, errors);
        final boolean repair = bool(source, ROOT + "canonical.vanilla-repair", false, errors);
        final boolean smithing = bool(source, ROOT + "canonical.smithing-upgrade", false, errors);
        final boolean grindstone = bool(source, ROOT + "canonical.grindstone", false, errors);
        final boolean enchanting = bool(source, ROOT + "canonical.enchanting", false, errors);
        final long cooldownSeconds = integer(source, ROOT + "feedback-cooldown-seconds", 2L,
                0L, 30L, errors);
        final String rawDurability = string(source, ROOT + "canonical.durability", "vanilla", errors);
        final DurabilityMode durability;
        try {
            durability = DurabilityMode.valueOf(rawDurability.trim().toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException invalid) {
            errors.add(ROOT + "canonical.durability ismeretlen: " + rawDurability);
            return new Rules(source.generation(), basic, rename, trim, repair, smithing,
                    grindstone, enchanting, Set.of(), DurabilityMode.VANILLA,
                    cooldownSeconds * 1_000L, false, errors);
        }
        final LinkedHashSet<String> allowedEnchantments = new LinkedHashSet<>();
        final Object rawAllow = value(source, ROOT + "canonical.allowed-enchantments");
        if (rawAllow != null && !(rawAllow instanceof List<?>)) {
            errors.add(ROOT + "canonical.allowed-enchantments csak lista lehet");
        } else if (rawAllow instanceof List<?> list) {
            for (final Object raw : list) {
                final String enchantment = raw == null ? "" : raw.toString().trim().toLowerCase(Locale.ROOT);
                final NamespacedKey key = NamespacedKey.fromString(enchantment);
                if (key == null || resolveEnchantment(key) == null) {
                    errors.add("ismeretlen canonical enchant: " + enchantment);
                } else {
                    allowedEnchantments.add(key.toString());
                }
            }
        }
        if (enchanting && allowedEnchantments.isEmpty()) {
            errors.add(ROOT + "canonical.enchanting=true mellett az allowed-enchantments nem lehet üres");
        }
        return new Rules(source.generation(), basic, rename, trim, repair, smithing,
                grindstone, enchanting, allowedEnchantments, durability,
                cooldownSeconds * 1_000L, errors.isEmpty(), errors);
    }

    private static Object value(final ConfigManager.ConfigSnapshot snapshot, final String path) {
        return snapshot.configuration() == null ? null : snapshot.configuration().get(path);
    }

    private static boolean bool(final ConfigManager.ConfigSnapshot snapshot, final String path,
                                final boolean fallback, final List<String> errors) {
        final Object raw = value(snapshot, path);
        if (raw == null) return fallback;
        if (raw instanceof Boolean value) return value;
        errors.add(path + " csak true/false lehet");
        return fallback;
    }

    private static long integer(final ConfigManager.ConfigSnapshot snapshot, final String path,
                                final long fallback, final long minimum, final long maximum,
                                final List<String> errors) {
        final Object raw = value(snapshot, path);
        if (raw == null) return fallback;
        if (!(raw instanceof Number number)) {
            errors.add(path + " csak egész szám lehet");
            return fallback;
        }
        final long value = number.longValue();
        if (value < minimum || value > maximum || number.doubleValue() != value) {
            errors.add(path + " tartománya: " + minimum + ".." + maximum);
            return fallback;
        }
        return value;
    }

    private static String string(final ConfigManager.ConfigSnapshot snapshot, final String path,
                                 final String fallback, final List<String> errors) {
        final Object raw = value(snapshot, path);
        if (raw == null) return fallback;
        if (raw instanceof String value && !value.isBlank()) return value;
        errors.add(path + " nem üres szöveg kell legyen");
        return fallback;
    }

    private static Enchantment resolveEnchantment(final NamespacedKey key) {
        try {
            return io.papermc.paper.registry.RegistryAccess.registryAccess()
                    .getRegistry(io.papermc.paper.registry.RegistryKey.ENCHANTMENT).get(key);
        } catch (final RuntimeException unavailable) {
            return null;
        }
    }

    private static String key(final Enchantment enchantment) {
        return enchantment.getKey().toString().toLowerCase(Locale.ROOT);
    }

    private static Decision allow(final String diagnostic) {
        return new Decision(Action.ALLOW, "", diagnostic);
    }

    private static Decision deny(final String reasonKey, final String diagnostic) {
        return new Decision(Action.DENY, reasonKey, diagnostic);
    }

    private static Decision mutation(final String diagnostic) {
        return new Decision(Action.CANONICAL_MUTATION_REQUIRED,
                "itemization-vanilla-boundary-canonical-mutation", diagnostic);
    }
}
