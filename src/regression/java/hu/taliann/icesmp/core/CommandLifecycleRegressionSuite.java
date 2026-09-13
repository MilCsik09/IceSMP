package hu.taliann.icesmp.core;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;

import java.lang.reflect.Proxy;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.tools.ToolProvider;

public final class CommandLifecycleRegressionSuite {
    private static int assertions;

    public static void main(final String[] args) throws Exception {
        startupCloseAndPermission();
        enteredCallbackDrainsAndExceptionsRelease();
        closedJarNeverReachesLazyCommandBody();
        actualRegistrationUsesTheGuard();
        System.out.println("Command lifecycle passed. assertions=" + assertions);
    }

    private static void startupCloseAndPermission() {
        final AtomicBoolean enabled = new AtomicBoolean(true);
        final AtomicInteger calls = new AtomicInteger();
        final AtomicInteger permissions = new AtomicInteger();
        final CommandLifecycle lifecycle = new CommandLifecycle(enabled::get);
        final BasicCommand guarded = lifecycle.wrap(new BasicCommand() {
            public void execute(final CommandSourceStack source, final String[] args) { calls.incrementAndGet(); }
            public Collection<String> suggest(final CommandSourceStack source, final String[] args) {
                calls.incrementAndGet(); return List.of("live");
            }
            public boolean canUse(final CommandSender sender) { calls.incrementAndGet(); return true; }
            public String permission() { permissions.incrementAndGet(); return "test.lifecycle"; }
        });
        final List<String> messages = new ArrayList<>();
        final CommandSourceStack source = source(messages);
        guarded.execute(source, new String[0]);
        check(!guarded.canUse(source.getSender()) && guarded.suggest(source, new String[0]).isEmpty(), "startup admitted command");
        check(calls.get() == 0 && messages.size() == 1, "startup touched retired authority or gave no feedback");
        lifecycle.open();
        guarded.execute(source, new String[0]);
        check(guarded.canUse(source.getSender()), "live permission semantics lost");
        check(guarded.suggest(source, new String[0]).equals(List.of("live")), "live suggestions lost");
        check(calls.get() == 3, "live callbacks not forwarded exactly once");
        enabled.set(false);
        guarded.execute(source, new String[0]);
        check(!guarded.canUse(source.getSender()) && guarded.suggest(source, new String[0]).isEmpty(), "disabled plugin admitted command");
        check(calls.get() == 3, "isEnabled refusal still reached delegate");
        check(lifecycle.close().isDone(), "empty close did not drain");
        enabled.set(true);
        guarded.execute(source, new String[0]);
        check(!guarded.canUse(source.getSender()) && guarded.suggest(source, new String[0]).isEmpty(), "old instance reopened");
        check("test.lifecycle".equals(guarded.permission()) && permissions.get() == 1, "closed node calls delegate permission");
        expect(IllegalStateException.class, lifecycle::open);
        expect(IllegalStateException.class, () -> lifecycle.wrap((s, a) -> {}));
        final CommandLifecycle failedStartup = new CommandLifecycle(() -> true);
        failedStartup.close();
        expect(IllegalStateException.class, failedStartup::open);
    }

    private static void enteredCallbackDrainsAndExceptionsRelease() throws Exception {
        final CommandLifecycle lifecycle = new CommandLifecycle(() -> true);
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final BasicCommand guarded = lifecycle.wrap(new BasicCommand() {
            public void execute(final CommandSourceStack source, final String[] args) { throw new AssertionError("unexpected execution"); }
            public Collection<String> suggest(final CommandSourceStack source, final String[] args) {
                entered.countDown();
                try { check(release.await(5, TimeUnit.SECONDS), "test callback not released"); }
                catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new AssertionError(failure); }
                throw new IllegalArgumentException("original body failure");
            }
        });
        lifecycle.open();
        try (var executor = Executors.newSingleThreadExecutor()) {
            final var running = executor.submit(() -> expect(IllegalArgumentException.class,
                    () -> guarded.suggest(source(new ArrayList<>()), new String[0])));
            check(entered.await(5, TimeUnit.SECONDS), "real delegate never entered");
            final var drained = lifecycle.close();
            check(!drained.isDone(), "close claimed an entered callback was drained");
            drained.cancel(false);
            check(!lifecycle.close().isDone(), "caller cancellation corrupted the native drain future");
            check(guarded.suggest(null, new String[0]).isEmpty(), "closed suggestion reached delegate");
            check(!guarded.canUse(null), "closed permission reached delegate");
            release.countDown();
            running.get(5, TimeUnit.SECONDS);
            lifecycle.close().get(5, TimeUnit.SECONDS);
            check(true, "exception did not release admission");
        } finally { release.countDown(); }
    }

    private static void closedJarNeverReachesLazyCommandBody() throws Exception {
        final Path root = Files.createTempDirectory("icesmp-command-loader-");
        try {
            final Path source = root.resolve("ClosedJarCommand.java");
            Files.writeString(source, """
                    import io.papermc.paper.command.brigadier.*;
                    import java.util.*;
                    public final class ClosedJarCommand implements BasicCommand {
                        public void execute(CommandSourceStack source, String[] args) { LateBody.values(); }
                        public Collection<String> suggest(CommandSourceStack source, String[] args) { return LateBody.values(); }
                        public String permission() { return "loader.test"; }
                    }
                    final class LateBody { static Collection<String> values() { return List.of("late"); } }
                    """);
            check(ToolProvider.getSystemJavaCompiler().run(null, null, null,
                    "-proc:none", "-classpath", System.getProperty("java.class.path"),
                    "-d", root.toString(), source.toString()) == 0, "real test JAR compilation failed");
            final Path jar = root.resolve("command.jar");
            try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
                for (final String type : List.of("ClosedJarCommand", "LateBody")) {
                    output.putNextEntry(new JarEntry(type + ".class"));
                    output.write(Files.readAllBytes(root.resolve(type + ".class")));
                    output.closeEntry();
                }
            }
            final CommandLifecycle lifecycle = new CommandLifecycle(() -> true);
            final BasicCommand delegate;
            final BasicCommand guarded;
            try (URLClassLoader loader = new URLClassLoader(new java.net.URL[] {jar.toUri().toURL()},
                    CommandLifecycleRegressionSuite.class.getClassLoader())) {
                delegate = (BasicCommand) loader.loadClass("ClosedJarCommand").getConstructor().newInstance();
                guarded = lifecycle.wrap(delegate);
                lifecycle.open();
                lifecycle.close().get(5, TimeUnit.SECONDS);
            }
            check(guarded.suggest(null, new String[0]).isEmpty(), "closed JAR suggestion attempted lazy class load");
            check(!guarded.canUse(null) && "loader.test".equals(guarded.permission()), "closed JAR permission touched body");
            final List<String> messages = new ArrayList<>();
            guarded.execute(source(messages), new String[0]);
            check(messages.size() == 1, "closed JAR execute did not refuse cleanly");
            expect(NoClassDefFoundError.class, () -> delegate.suggest(null, new String[0]));
        } finally {
            try (var paths = Files.walk(root)) {
                for (final Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }

    private static void actualRegistrationUsesTheGuard() throws Exception {
        final String entry = Files.readString(Path.of("src/main/java/hu/taliann/icesmp/IceSMP.java"));
        check(entry.contains("super.registerCommand(label, description, aliases, commands.wrap(command))"), "actual registrations bypass lifecycle");
        check(entry.indexOf("core.enable();") < entry.indexOf("commands.open();"), "commands open before full core enable");
        final String disable = entry.substring(entry.indexOf("public void onDisable()"));
        check(disable.indexOf("commands.close();") < disable.indexOf("core.disable();"), "authority teardown precedes command closure");
    }

    private static CommandSourceStack source(final List<String> messages) {
        final CommandSender sender = (CommandSender) Proxy.newProxyInstance(CommandSender.class.getClassLoader(),
                new Class<?>[] {CommandSender.class}, (proxy, method, args) -> {
                    if (method.getName().equals("sendMessage")) { messages.add(String.valueOf(args[0])); return null; }
                    if (method.getReturnType() == boolean.class) return false;
                    return null;
                });
        return (CommandSourceStack) Proxy.newProxyInstance(CommandSourceStack.class.getClassLoader(),
                new Class<?>[] {CommandSourceStack.class}, (proxy, method, args) ->
                        method.getName().equals("getSender") ? sender : null);
    }

    private static void expect(final Class<? extends Throwable> type, final Runnable body) {
        try { body.run(); } catch (Throwable failure) {
            check(type.isInstance(failure), "wrong failure: " + failure); return;
        }
        throw new AssertionError("expected " + type.getSimpleName());
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
        assertions++;
    }
}
