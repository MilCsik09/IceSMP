package hu.taliann.icesmp.classspec.compat;

import hu.taliann.icesmp.managers.ConfigManager;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Compatibility gate for the mandatory Profile v2 runtime and its optional external integrations.
 *
 * <p>The checked-in lock is the version/provisioning authority, but only entries explicitly marked
 * {@code required-runtime} may block IceSMP startup. Optional integrations are inspected when they
 * are installed and degrade to their existing fallback/no-op paths when absent. Dev-only and
 * validation-only entries never participate in server runtime enforcement.</p>
 */
public final class ClassSpecDependencyPreflight {

    private static final String RESOURCE = "class-spec-dependencies.lock.yml";
    private static final int LOCK_SCHEMA_VERSION = 2;

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final List<VersionRequirement> requirements;

    public ClassSpecDependencyPreflight(final JavaPlugin plugin, final ConfigManager configManager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.configManager = Objects.requireNonNull(configManager, "configManager");
        this.requirements = loadRequirements(plugin);
    }

    /** Runs the compatibility check; only required-runtime failures are startup-fatal. */
    public Report verify() {
        final boolean enforce = configManager.getBoolean("class-spec-rework.dependencies.enforce", true);
        final List<Result> results = inspect(plugin.getServer().getPluginManager(), requirements);
        final Report report = new Report(true, enforce, results);

        for (final Result result : results) {
            final VersionRequirement requirement = result.requirement();
            final String label = requirement.pluginName() + " [" + requirement.role().lockValue() + "] — ";
            if (result.ok()) {
                plugin.getLogger().info("Class/spec dependency: " + label + result.detail());
            } else if (requirement.blocksStartup()) {
                plugin.getLogger().warning("Class/spec dependency HIBA: " + label + result.detail());
            } else {
                plugin.getLogger().warning("Class/spec opcionális integráció nem kompatibilis: "
                        + label + result.detail() + "; az IceSMP ettől tovább indul.");
            }
        }

        if (enforce && !report.requiredDependenciesValid()) {
            throw new IllegalStateException("A class/spec runtime nem indulhat: hiányzó vagy nem zárolt "
                    + "required-runtime capability. Lásd: " + RESOURCE);
        }
        return report;
    }

    static List<Result> inspect(final PluginManager pluginManager,
                                final List<VersionRequirement> requirements) {
        final List<Result> results = new ArrayList<>(requirements.size());
        for (final VersionRequirement requirement : requirements) {
            if (!requirement.participatesInRuntimeCheck()) {
                results.add(new Result(requirement, true, null,
                        "nem runtime dependency; csak " + requirement.role().lockValue() + " metadata"));
                continue;
            }
            final Plugin dependency = pluginManager.getPlugin(requirement.pluginName());
            if (dependency == null) {
                results.add(new Result(requirement, requirement.acceptsMissingDependency(), null,
                        requirement.blocksStartup() ? "required-runtime plugin nincs telepítve"
                                : "opcionális integráció nincs telepítve; fallback/no-op aktív"));
                continue;
            }
            final String runtimeVersion = dependency.getPluginMeta().getVersion();
            final boolean accepted = requirement.accepts(runtimeVersion);
            results.add(new Result(requirement, accepted, runtimeVersion,
                    accepted ? "verzió " + runtimeVersion : "nem elfogadott verzió: " + runtimeVersion
                            + "; elfogadott: " + requirement.acceptedVersions()));
        }
        return List.copyOf(results);
    }

    private static List<VersionRequirement> loadRequirements(final JavaPlugin plugin) {
        try (InputStream stream = plugin.getResource(RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Hiányzó dependency lock resource: " + RESOURCE);
            }
            final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
            final int schemaVersion = yaml.getInt("schema-version", -1);
            if (schemaVersion != LOCK_SCHEMA_VERSION) {
                throw new IllegalStateException("Nem támogatott dependency lock schema-version: "
                        + schemaVersion + "; elvárt: " + LOCK_SCHEMA_VERSION);
            }
            final ConfigurationSection plugins = yaml.getConfigurationSection("plugins");
            if (plugins == null || plugins.getKeys(false).isEmpty()) {
                throw new IllegalStateException("Üres dependency lock resource: " + RESOURCE);
            }
            final List<VersionRequirement> loaded = new ArrayList<>();
            for (final String id : plugins.getKeys(false)) {
                final ConfigurationSection section = plugins.getConfigurationSection(id);
                if (section == null) {
                    continue;
                }
                final String pluginName = section.getString("server-name", id);
                final VersionRequirement.RuntimeRole role;
                try {
                    role = VersionRequirement.RuntimeRole.parse(section.getString("runtime-role"));
                } catch (final IllegalArgumentException invalidRole) {
                    throw new IllegalStateException("Érvénytelen runtime-role: plugins." + id, invalidRole);
                }
                final List<String> accepted = section.getStringList("accepted-versions");
                if (accepted.isEmpty()) {
                    throw new IllegalStateException("Nincs accepted-versions: plugins." + id);
                }
                loaded.add(new VersionRequirement(pluginName, role, accepted,
                        section.getString("verification", "unverified").toLowerCase(Locale.ROOT)));
            }
            return List.copyOf(loaded);
        } catch (final IOException failure) {
            throw new IllegalStateException("A dependency lock nem olvasható: " + RESOURCE, failure);
        }
    }

    public record Result(VersionRequirement requirement, boolean ok, String runtimeVersion, String detail) {
    }

    public record Report(boolean reworkEnabled, boolean enforcementEnabled, List<Result> results) {
        public Report {
            results = List.copyOf(results);
        }

        /** Compatibility name retained for callers; now means required-runtime only. */
        public boolean requiredDependenciesValid() {
            return results.stream().noneMatch(result -> result.requirement().blocksStartup() && !result.ok());
        }
    }
}
