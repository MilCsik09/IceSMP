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
 * Fail-fast compatibility gate for the opt-in class/spec rework.
 *
 * <p>The legacy runtime remains available while {@code class-spec-rework.enabled} is false. Once the
 * rework is enabled, every required engine in the checked-in lock manifest must be present at an
 * explicitly accepted version. This prevents a partially-rendered profile from becoming authoritative.</p>
 */
public final class ClassSpecDependencyPreflight {

    private static final String RESOURCE = "class-spec-dependencies.lock.yml";

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final List<VersionRequirement> requirements;

    public ClassSpecDependencyPreflight(final JavaPlugin plugin, final ConfigManager configManager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.configManager = Objects.requireNonNull(configManager, "configManager");
        this.requirements = loadRequirements(plugin);
    }

    /** Runs the gate and throws before gameplay startup when strict rework dependencies are invalid. */
    public Report verify() {
        final boolean enabled = configManager.getBoolean("class-spec-rework.enabled", false);
        final boolean enforce = configManager.getBoolean("class-spec-rework.dependencies.enforce", true);
        if (!enabled) {
            plugin.getLogger().info("A class/spec rework ki van kapcsolva; a verziózárt dependency "
                    + "preflight nem futott le.");
            return new Report(false, enforce, List.of());
        }
        final List<Result> results = inspect(plugin.getServer().getPluginManager(), requirements);
        final Report report = new Report(enabled, enforce, results);

        for (final Result result : results) {
            final String prefix = result.ok() ? "Class/spec dependency OK: " : "Class/spec dependency HIBA: ";
            final String message = prefix + result.requirement().pluginName() + " — " + result.detail();
            if (result.ok()) {
                plugin.getLogger().info(message);
            } else if (result.requirement().required()) {
                plugin.getLogger().warning(message);
            } else {
                plugin.getLogger().info(message);
            }
        }

        if (enabled && enforce && !report.requiredDependenciesValid()) {
            throw new IllegalStateException("A class/spec rework nem indulhat: hiányzó vagy nem zárolt "
                    + "kötelező pluginverzió. Lásd: " + RESOURCE);
        }
        return report;
    }

    static List<Result> inspect(final PluginManager pluginManager,
                                final List<VersionRequirement> requirements) {
        final List<Result> results = new ArrayList<>(requirements.size());
        for (final VersionRequirement requirement : requirements) {
            final Plugin dependency = pluginManager.getPlugin(requirement.pluginName());
            if (dependency == null) {
                results.add(new Result(requirement, requirement.acceptsMissingDependency(), null,
                        requirement.required() ? "nincs telepítve" : "opcionális és nincs telepítve"));
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
                final boolean required = section.getBoolean("required", false);
                final List<String> accepted = section.getStringList("accepted-versions");
                if (accepted.isEmpty()) {
                    throw new IllegalStateException("Nincs accepted-versions: plugins." + id);
                }
                loaded.add(new VersionRequirement(pluginName, required, accepted,
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

        public boolean requiredDependenciesValid() {
            return results.stream().noneMatch(result -> result.requirement().required() && !result.ok());
        }
    }
}
