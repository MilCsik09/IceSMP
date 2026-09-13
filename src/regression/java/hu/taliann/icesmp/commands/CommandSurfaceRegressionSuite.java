package hu.taliann.icesmp.commands;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Source contract for permission-safe dispatch, help and Paper trailing-space completion. */
public final class CommandSurfaceRegressionSuite {

    private static final Path COMMANDS = Path.of("src/main/java/hu/taliann/icesmp/commands");

    private CommandSurfaceRegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        verifiesIceSmpDomainsAreIndependentlyPermissioned();
        verifiesRouterDiscoveryMatchesExecution();
        verifiesAdminSubcommandsDeclarePermissions();
        System.out.println("Command surface regression suite passed.");
    }

    private static void verifiesIceSmpDomainsAreIndependentlyPermissioned() throws Exception {
        final String source = read("IceSMPCommand.java");
        require(source, "if (!require(sender, PERMISSION)) return;", "reload execution permission");
        require(source, "if (!require(sender, CONFIG_PERMISSION)) return;", "config execution permission");
        require(source, "if (!require(sender, INSPECT_PERMISSION)) return;", "inspect execution permission");
        require(source, "if (!require(sender, CLIENT_PERMISSION)) return;", "client execution permission");
        require(source, "rootSuggestions(sender, \"\")", "empty-root filtered completion");
        require(source, "args.length == 1 && !List.of(\"reload\", \"config\", \"inspect\", \"client\", \"dev\").contains(root)",
                "Paper exact-root completion handoff");
        require(source, "final String prefix = args.length == 1 ? \"\" : args[1]", "trailing-space empty prefix");
        require(source, "configManager.operatorEditablePaths()", "set suggestions bounded to operator schema");
        require(source, "configManager.snapshot().overridePaths()", "unset suggestions bounded to stored overrides");
        require(source, "sender.hasPermission(PERMISSION)", "reload help/discovery permission");
        require(source, "sender.hasPermission(CONFIG_PERMISSION)", "config help/discovery permission");
        require(source, "sender.hasPermission(INSPECT_PERMISSION)", "inspect help/discovery permission");
        require(source, "sender.hasPermission(CLIENT_PERMISSION)", "client help/discovery permission");

        final int execute = source.indexOf("public void execute");
        final int firstDomain = source.indexOf("if (\"reload\".equalsIgnoreCase", execute);
        check(execute >= 0 && firstDomain > execute, "IceSMP execute layout changed");
        final String preDispatch = source.substring(execute, firstDomain);
        check(!preDispatch.contains("hasPermission(PERMISSION)"),
                "global reload permission must not hide other /icesmp domains");
    }

    private static void verifiesRouterDiscoveryMatchesExecution() throws Exception {
        final String dispatch = read("AbstractDispatchCommand.java");
        require(dispatch, "if (!subcommand.isVisibleTo(sender))", "router execution gate");
        require(dispatch, ".filter(subcommand -> subcommand.isVisibleTo(sender))", "router root completion filter");
        require(dispatch, ".filter(name -> subcommands.get(name).isVisibleTo(sender))", "router prefix filter");
        require(dispatch, "if (!subcommand.isVisibleTo(sender)) continue;", "router help filter");
        require(dispatch, "args.length == 1 && subcommand == null", "router trailing-space handoff");
        require(dispatch, "Arrays.copyOfRange(args, 1, args.length)", "router subcommand arguments");

        final String contract = read("Subcommand.java");
        require(contract, "default String permission()", "subcommand permission contract");
        require(contract, "sender.hasPermission(permission())", "subcommand visibility permission");
    }

    private static void verifiesAdminSubcommandsDeclarePermissions() throws Exception {
        final List<String> adminHandlers = List.of(
                "currency/CurrencySetSubcommand.java",
                "faction/FactionSetSubcommand.java",
                "job/JobAddXpSubcommand.java",
                "job/JobAdminSubcommand.java",
                "job/JobGiveCatalystSubcommand.java",
                "job/JobListSpellsSubcommand.java",
                "job/JobSetXpSubcommand.java",
                "job/JobStatusSubcommand.java",
                "job/JobUnlockSpellSubcommand.java");
        for (final String handler : adminHandlers) {
            final String source = read(handler);
            require(source, "public String permission()", handler + " permission override");
            require(source, "return PERMISSION;", handler + " canonical permission");
        }
    }

    private static String read(final String relative) throws Exception {
        return Files.readString(COMMANDS.resolve(relative));
    }

    private static void require(final String source, final String token, final String description) {
        check(source.contains(token), "missing " + description + ": " + token);
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }
}
