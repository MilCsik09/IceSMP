package hu.taliann.icesmp.managers;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scoreboard.Team;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Focused regressions for the native YAML sidebar and Paper 1.21.11 team-colour sync. */
public final class HudRegressionSuite {

    private HudRegressionSuite() {
    }

    public static void main(final String[] args) {
        packagedLayoutLeavesRoomForLogo();
        customLayoutKeepsDynamicTokens();
        oversizedLayoutEvictsLowPrioritySections();
        malformedLayoutFallsBackSafely();
        uncolouredTeamNeverReadsThrowingGetter();
        unchangedTeamColourIsNotRewritten();
        changedTeamColourIsRewritten();
        System.out.println("HUD regression suite passed.");
    }

    private static void packagedLayoutLeavesRoomForLogo() {
        final YamlConfiguration general = YamlConfiguration.loadConfiguration(
                Path.of("src/main/resources/config/general.yml").toFile());
        check(!general.getString("hud.sidebar.title", "").isBlank(),
                "packaged HUD title must not be blank");

        final List<HudSidebarLayout.Entry> layout = HudSidebarLayout.parse(
                general.getMapList("hud.sidebar.layout"));
        check(!layout.isEmpty(), "packaged HUD layout must not be empty");
        check(layout.get(0).type() == HudSidebarLayout.Type.SPACER,
                "the tall scoreboard logo needs a real spacer as the first rendered row");
        check(layout.size() > 1 && layout.get(1).type() == HudSidebarLayout.Type.SEPARATOR,
                "the upper separator must render after the logo spacer");
        check(layout.stream().anyMatch(entry -> entry.type() == HudSidebarLayout.Type.RESOURCE),
                "packaged layout lost the dynamic class-resource row");
        check(layout.stream().anyMatch(entry -> entry.type() == HudSidebarLayout.Type.PARTY),
                "packaged layout lost the expanding party block");
    }

    private static void customLayoutKeepsDynamicTokens() {
        final List<HudSidebarLayout.Entry> layout = HudSidebarLayout.parse(List.of(
                Map.of("type", "text", "section", "valuta",
                        "text", "&6{balance} &8• {faction} &7Lv.{class_level}"),
                Map.of("type", "separator"),
                Map.of("type", "text", "text", "{resource_name}: {resource_value}/{resource_max}")
        ));
        check(layout.size() == 3, "custom YAML layout order changed during parsing");

        final String first = HudSidebarLayout.render(layout.get(0).text(), Map.of(
                "balance", "12", "faction", "Piros", "class_level", "4"));
        final String second = HudSidebarLayout.render(layout.get(0).text(), Map.of(
                "balance", "99", "faction", "Kék", "class_level", "8"));
        check("&612 &8• Piros &7Lv.4".equals(first),
                "configured row text or token order was not preserved");
        check("&699 &8• Kék &7Lv.8".equals(second),
                "the parsed layout froze values instead of rendering live tokens");
    }

    private static void oversizedLayoutEvictsLowPrioritySections() {
        final List<HudSidebarLayout.Row<String>> rows = new ArrayList<>();
        rows.add(new HudSidebarLayout.Row<>("", "spacer"));
        rows.add(new HudSidebarLayout.Row<>("", "top"));
        rows.add(new HudSidebarLayout.Row<>(HudManager.SECTION_EVENT, "event"));
        rows.add(new HudSidebarLayout.Row<>(HudManager.SECTION_CURRENCY, "currency"));
        rows.add(new HudSidebarLayout.Row<>(HudManager.SECTION_FACTION, "faction"));
        rows.add(new HudSidebarLayout.Row<>(HudManager.SECTION_CLASS, "class"));
        rows.add(new HudSidebarLayout.Row<>(HudManager.SECTION_RESOURCE, "resource"));
        for (int i = 0; i < 8; i++) {
            rows.add(new HudSidebarLayout.Row<>(HudManager.SECTION_PARTY, "party-" + i));
        }
        rows.add(new HudSidebarLayout.Row<>("", "bottom"));

        final List<HudSidebarLayout.Row<String>> fitted = HudSidebarLayout.fit(rows, 15, List.of(
                HudManager.SECTION_EVENT,
                HudManager.SECTION_CURRENCY,
                HudManager.SECTION_FACTION,
                HudManager.SECTION_CLASS,
                HudManager.SECTION_PARTY,
                HudManager.SECTION_RESOURCE));
        check(fitted.size() == 15, "sidebar row budget must be exactly capped at 15");
        check(fitted.stream().noneMatch(row -> "event".equals(row.value())),
                "lowest-priority event row should be evicted first");
        check(fitted.stream().anyMatch(row -> "resource".equals(row.value())),
                "combat-critical resource row was evicted before lower-priority content");
        check(fitted.stream().anyMatch(row -> row.value().startsWith("party-")),
                "party rows were evicted before lower-priority content");
        check(fitted.stream().anyMatch(row -> "spacer".equals(row.value()))
                        && fitted.stream().anyMatch(row -> "bottom".equals(row.value())),
                "structural rows must survive section-priority eviction");
    }

    private static void malformedLayoutFallsBackSafely() {
        final List<HudSidebarLayout.Entry> parsed = HudSidebarLayout.parse(
                List.of("not-a-map", 42, Boolean.TRUE));
        check(parsed.equals(HudSidebarLayout.defaults()),
                "a wholly malformed layout must fall back instead of crashing the HUD tick");
    }

    private static void uncolouredTeamNeverReadsThrowingGetter() {
        final AtomicReference<NamedTextColor> colour = new AtomicReference<>();
        final AtomicInteger setters = new AtomicInteger();
        final Team team = teamProxy(colour, setters, true);

        TablistManager.syncTeamColor(team, NamedTextColor.RED);

        check(NamedTextColor.RED.equals(colour.get()),
                "newly created team did not receive its first colour");
        check(setters.get() == 1, "newly created team colour must be written exactly once");
    }

    private static void unchangedTeamColourIsNotRewritten() {
        final AtomicReference<NamedTextColor> colour = new AtomicReference<>(NamedTextColor.BLUE);
        final AtomicInteger setters = new AtomicInteger();
        final Team team = teamProxy(colour, setters, false);

        TablistManager.syncTeamColor(team, NamedTextColor.BLUE);

        check(setters.get() == 0, "unchanged team colour should preserve diff-only updates");
    }

    private static void changedTeamColourIsRewritten() {
        final AtomicReference<NamedTextColor> colour = new AtomicReference<>(NamedTextColor.BLUE);
        final AtomicInteger setters = new AtomicInteger();
        final Team team = teamProxy(colour, setters, false);

        TablistManager.syncTeamColor(team, NamedTextColor.RED);

        check(NamedTextColor.RED.equals(colour.get()) && setters.get() == 1,
                "changed relation/faction colour must be written exactly once");
    }

    private static Team teamProxy(final AtomicReference<NamedTextColor> colour,
                                  final AtomicInteger setters,
                                  final boolean getterMustNotRunWhileUncoloured) {
        return (Team) Proxy.newProxyInstance(
                Team.class.getClassLoader(),
                new Class<?>[]{Team.class},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "toString" -> "HudRegressionTeam";
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == args[0];
                            default -> null;
                        };
                    }
                    if ("hasColor".equals(method.getName())) {
                        return colour.get() != null;
                    }
                    if ("color".equals(method.getName()) && method.getParameterCount() == 0) {
                        if (getterMustNotRunWhileUncoloured && colour.get() == null) {
                            throw new AssertionError("Team.color() getter was called before a colour existed");
                        }
                        return colour.get();
                    }
                    if ("color".equals(method.getName()) && method.getParameterCount() == 1) {
                        setters.incrementAndGet();
                        colour.set((NamedTextColor) args[0]);
                        return null;
                    }
                    return primitiveDefault(method.getReturnType());
                });
    }

    private static Object primitiveDefault(final Class<?> type) {
        if (!type.isPrimitive() || type == void.class) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0.0F;
        }
        return 0.0D;
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
