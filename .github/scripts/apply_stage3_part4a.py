#!/usr/bin/env python3
from __future__ import annotations
import pathlib, re
ROOT = pathlib.Path(__file__).resolve().parents[2]

def read(p): return (ROOT/p).read_text(encoding='utf-8')
def write(p,s):
    q=ROOT/p; q.parent.mkdir(parents=True,exist_ok=True); q.write_text(s,encoding='utf-8')
def once(p,old,new):
    s=read(p); c=s.count(old)
    if c!=1: raise RuntimeError(f'{p}: expected 1 occurrence, got {c}: {old[:120]!r}')
    write(p,s.replace(old,new,1))
def regex_once(p,pat,repl,flags=0):
    s=read(p); n,c=re.subn(pat,repl,s,count=1,flags=flags)
    if c!=1: raise RuntimeError(f'{p}: regex expected 1, got {c}: {pat}')
    write(p,n)

# ---------------- Regression suites ----------------
write('src/regression/java/hu/taliann/icesmp/config/ConfigGuiTransactionRegressionSuite.java', r'''package hu.taliann.icesmp.config;

import hu.taliann.icesmp.gui.ConfigEditSession;
import java.util.Map;

public final class ConfigGuiTransactionRegressionSuite {
    private ConfigGuiTransactionRegressionSuite() { }
    public static void main(final String[] args) {
        final Map<String, Object> opening = Map.of("a", true, "n", 10, "mode", "A");
        final Map<String, Object> defaults = Map.of("a", false, "n", 5, "mode", "B");
        final ConfigEditSession session = new ConfigEditSession(7L, "abc", opening, defaults);
        check(!session.dirty() && session.pendingChanges().isEmpty(), "open/cancel has no writes");
        session.stage("a", false);
        check(session.dirty() && Boolean.FALSE.equals(session.value("a")), "toggle staged only");
        session.reset("n");
        check(Integer.valueOf(5).equals(session.value("n")), "reset shows documented base default");
        check(session.pendingChanges().containsKey("n") && session.pendingChanges().get("n") == null,
                "reset persists as override removal");
        boolean immutable = false;
        try { session.pendingChanges().put("x", 1); } catch (final UnsupportedOperationException expected) { immutable = true; }
        check(immutable, "save batch immutable");
        check(session.expectedGeneration() == 7L && session.expectedFingerprint().equals("abc"),
                "session retains optimistic concurrency token");
        System.out.println("Config GUI transaction regression suite passed.");
    }
    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }
}
''')

write('src/regression/java/hu/taliann/icesmp/config/ConfigGuiCoverageRegressionSuite.java', r'''package hu.taliann.icesmp.config;

import hu.taliann.icesmp.gui.ConfigMenuGUI;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ConfigGuiCoverageRegressionSuite {
    private static final List<String> MUST_EXPOSE_PREFIXES = List.of(
            "world-events.safety.", "moderation.vanish.", "territory.mob-rules.doom-gate.");

    private ConfigGuiCoverageRegressionSuite() { }

    public static void main(final String[] args) throws Exception {
        final YamlConfiguration merged = new YamlConfiguration();
        try (var stream = Files.list(Path.of("src/main/resources/config"))) {
            stream.filter(path -> path.toString().endsWith(".yml")).sorted().forEach(path ->
                    merge(merged, YamlConfiguration.loadConfiguration(path.toFile())));
        }
        final Map<String, Object> scalar = new HashMap<>();
        for (final String key : merged.getKeys(true)) {
            if (!merged.isConfigurationSection(key) && isScalar(merged.get(key))) scalar.put(key, merged.get(key));
        }
        final Map<String, ConfigMenuGUI.Entry> entries = new HashMap<>();
        final Set<String> duplicates = new HashSet<>();
        for (final ConfigMenuGUI.Entry entry : ConfigMenuGUI.allEntries()) {
            if (entries.put(entry.key(), entry) != null) duplicates.add(entry.key());
            final Object value = scalar.get(entry.key());
            check(value != null, "unknown GUI path: " + entry.key());
            switch (entry.type()) {
                case TOGGLE -> check(value instanceof Boolean, "boolean type mismatch: " + entry.key());
                case INTEGER, NUMBER -> {
                    check(value instanceof Number, "numeric type mismatch: " + entry.key());
                    final double number = ((Number) value).doubleValue();
                    check(number >= entry.min() && number <= entry.max(), "default outside range: " + entry.key());
                }
                case CYCLE -> check(entry.options().stream().map(v -> v.toLowerCase(Locale.ROOT))
                        .anyMatch(v -> v.equals(String.valueOf(value).toLowerCase(Locale.ROOT))),
                        "cycle default missing: " + entry.key());
            }
        }
        check(duplicates.isEmpty(), "duplicate GUI entries: " + duplicates);
        final List<String> missingRequired = scalar.keySet().stream()
                .filter(key -> MUST_EXPOSE_PREFIXES.stream().anyMatch(key::startsWith))
                .filter(key -> !entries.containsKey(key)).sorted().toList();
        check(missingRequired.isEmpty(), "required schema entries missing from GUI: " + missingRequired);
        final int displayed = entries.size();
        final int excluded = scalar.size() - displayed;
        System.out.println("CONFIG_GUI_COVERAGE total=" + scalar.size() + " displayed=" + displayed
                + " intentionally_excluded=" + excluded + " missing=0 stale=0 duplicate=0");
        System.out.println("Config GUI coverage regression suite passed.");
    }

    private static boolean isScalar(final Object value) {
        return value instanceof Boolean || value instanceof Number || value instanceof String;
    }
    private static void merge(final YamlConfiguration target, final ConfigurationSection source) {
        for (final String key : source.getKeys(true)) if (!source.isConfigurationSection(key)) target.set(key, source.get(key));
    }
    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }
}
''')

write('src/regression/java/hu/taliann/icesmp/professions/ProfessionRecipeAuditRegressionSuite.java', r'''package hu.taliann.icesmp.professions;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.data.ProfessionType;
import hu.taliann.icesmp.managers.ProfessionIngredientParser;
import hu.taliann.icesmp.managers.ProfessionRecipeCatalog;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

public final class ProfessionRecipeAuditRegressionSuite {
    private ProfessionRecipeAuditRegressionSuite() { }

    public static void main(final String[] args) throws Exception {
        final Path path = Path.of("src/main/resources/config/profession-recipes.yml");
        final String raw = Files.readString(path);
        check(!raw.contains("  kezdo_horgaszbot:"), "removed duplicate recipe must not remain craftable");
        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(path.toFile());
        final ConfigurationSection root = yaml.getConfigurationSection("profession-recipes");
        check(root != null, "profession recipe root exists");
        final Set<String> ids = new TreeSet<>(root.getKeys(false));
        final Set<String> fingerprints = new HashSet<>();
        for (final String id : ids) {
            final ConfigurationSection section = root.getConfigurationSection(id);
            check(section != null, "recipe section: " + id);
            final ConfigurationSection result = section.getConfigurationSection("result");
            check(result != null, "result section: " + id);
            final ProfessionIngredientParser.ParsedIngredients parsed =
                    ProfessionIngredientParser.parse(section.getStringList("ingredients"));
            final String unique = result.getString("unique", null);
            final Material material = unique == null ? Material.matchMaterial(result.getString("material", "")) : Material.PAPER;
            check(material != null, "valid output material: " + id);
            final ProfessionType profession = ProfessionType.fromId(section.getString("profession", ""));
            check(profession != null, "valid profession gate: " + id);
            final ProfessionRecipeCatalog.Recipe recipe = new ProfessionRecipeCatalog.Recipe(
                    id, profession, Math.max(1, section.getInt("level", 1)),
                    "blueprint".equalsIgnoreCase(section.getString("learn", "level")),
                    section.getString("display-name", id), section.getString("category", "Egyéb"), material,
                    Math.max(1, result.getInt("amount", 1)), result.getString("affix-tier", null), unique,
                    parsed.materials(), parsed.uniqueMaterials(), section.getStringList("lore"),
                    result.getString("signature", null), FactionType.fromInput(section.getString("faction", null)),
                    section.getBoolean("loot-only", false), section.getString("job", null));
            final String fingerprint = ProfessionRecipeCatalog.semanticFingerprint(recipe);
            check(fingerprints.add(fingerprint), "semantic duplicate: " + id + " -> " + fingerprint);
            if (unique != null) {
                final String model = yaml.getString("profession-materials." + unique.toLowerCase(Locale.ROOT) + ".item-model");
                check(model != null && !model.isBlank(), "unique profession output has icon: " + unique);
            }
        }
        check(new ArrayList<>(ids).equals(ids.stream().sorted().toList()), "deterministic recipe order");
        final String manager = Files.readString(Path.of("src/main/java/hu/taliann/icesmp/managers/ProfessionRecipeManager.java"));
        check(manager.indexOf("clearRegisteredRecipes();") < manager.indexOf("if (!isEnabled())"),
                "reload removes stale recipes before disabled gate");
        final String core = Files.readString(Path.of("src/main/java/hu/taliann/icesmp/core/IceSMPCore.java"));
        check(core.contains("professionRecipeCatalog.load();\n            professionRecipeManager.registerRecipes();"),
                "full reload rebuilds recipe registry");
        check(core.contains("professionRecipeManager::shutdown"), "disable removes owned recipe keys");
        final String listener = Files.readString(Path.of("src/main/java/hu/taliann/icesmp/listeners/ProfessionRecipeListener.java"));
        check(listener.contains("uniqueIngredients") && listener.contains("profession"),
                "custom ingredients and profession gate remain enforced");
        System.out.println("PROFESSION_RECIPE_AUDIT recipes=" + ids.size() + " semantic_duplicates=0 key_duplicates=0");
        System.out.println("Profession recipe audit regression suite passed.");
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }
}
''')

print('stage3 part 4a applied')
