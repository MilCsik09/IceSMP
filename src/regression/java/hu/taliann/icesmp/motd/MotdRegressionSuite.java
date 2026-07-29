package hu.taliann.icesmp.motd;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class MotdRegressionSuite {

    private MotdRegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        timeRotationUsesFloorMod();
        randomRotationIsStableWithinBucket();
        randomRotationCoversPoolAndUsesSeed();
        eventPriorityIsStrict();
        seasonThresholdIsMillisecondPrecise();
        seasonThresholdDoesNotOverflow();
        wholeNumbersPreserveSignedLongPrecision();
        booleansAreTypeStrict();
        placeholdersAreStrict();
        generationLifecycleRejectsStaleCallbacks();
        generationAdvanceCannotOvertakePublication();
        invalidInputsFailClosed();
        bundledIconsAreValid();
        iconDirectoryIsNoFollowAndBounded();
        System.out.println("MOTD regression suite passed.");
    }

    private static void timeRotationUsesFloorMod() {
        require(MotdSelector.selectIndex(MotdSelector.Mode.TIME, 4, 0L, 10_000L, 0L) == 0,
                "time bucket zero");
        require(MotdSelector.selectIndex(MotdSelector.Mode.TIME, 4, 39_999L, 10_000L, 0L) == 3,
                "time bucket three");
        require(MotdSelector.selectIndex(MotdSelector.Mode.TIME, 4, -1L, 10_000L, 0L) == 3,
                "negative epoch must floor-mod, not produce a negative index");
    }

    private static void randomRotationIsStableWithinBucket() {
        final int first = MotdSelector.selectIndex(MotdSelector.Mode.RANDOM, 7, 12_000L, 10_000L, 1234L);
        final int second = MotdSelector.selectIndex(MotdSelector.Mode.RANDOM, 7, 19_999L, 10_000L, 1234L);
        require(first == second, "random selection must be stable during one configured window");
    }

    private static void randomRotationCoversPoolAndUsesSeed() {
        final Set<Integer> selected = new HashSet<>();
        for (int bucket = 0; bucket < 256; bucket++) {
            selected.add(MotdSelector.selectIndex(MotdSelector.Mode.RANDOM, 5,
                    bucket * 10_000L, 10_000L, 77L));
        }
        require(selected.size() == 5, "random selector should reach every configured variant");
        final int seedA = MotdSelector.selectIndex(MotdSelector.Mode.RANDOM, 97, 50_000L, 10_000L, 1L);
        final int seedB = MotdSelector.selectIndex(MotdSelector.Mode.RANDOM, 97, 50_000L, 10_000L, 2L);
        require(seedA != seedB, "changing the configured random seed must affect selection");
    }

    private static void eventPriorityIsStrict() {
        require(MotdSelector.selectEvent(true, true, 10_000L, 0L, 20_000L)
                        == MotdSelector.ActiveEvent.BLOOD_MOON,
                "blood moon must outrank every other event");
        require(MotdSelector.selectEvent(false, true, 10_000L, 0L, 20_000L)
                        == MotdSelector.ActiveEvent.WORLD_BOSS,
                "world boss must outrank season end");
        require(MotdSelector.selectEvent(false, false, 10_000L, 0L, 20_000L)
                        == MotdSelector.ActiveEvent.SEASON_END,
                "season end should apply inside the configured window");
    }

    private static void seasonThresholdIsMillisecondPrecise() {
        require(MotdSelector.selectEvent(false, false, 86_400_001L, 0L, 86_400_000L)
                        == MotdSelector.ActiveEvent.NONE,
                "one millisecond outside the season window must not be rounded into it");
        require(MotdSelector.selectEvent(false, false, 86_400_000L, 0L, 86_400_000L)
                        == MotdSelector.ActiveEvent.SEASON_END,
                "the exact threshold must be inclusive");
        require(MotdSelector.selectEvent(false, false, -1L, 0L, 86_400_000L)
                        == MotdSelector.ActiveEvent.NONE,
                "an already ended season must not remain active");
    }

    private static void seasonThresholdDoesNotOverflow() {
        require(MotdSelector.selectEvent(false, false, Long.MIN_VALUE, Long.MAX_VALUE, 10L)
                        == MotdSelector.ActiveEvent.NONE,
                "subtraction overflow must not resurrect an ancient season end");
        require(MotdSelector.selectEvent(false, false, Long.MAX_VALUE, Long.MAX_VALUE - 5L, 10L)
                        == MotdSelector.ActiveEvent.SEASON_END,
                "the upper long boundary must remain inside a saturated threshold");
        require(MotdSelector.selectEvent(false, false, Long.MAX_VALUE, Long.MAX_VALUE - 5L, 4L)
                        == MotdSelector.ActiveEvent.NONE,
                "the upper long boundary must still honor an exact short threshold");
        require(MotdSelector.selectEvent(false, false, -1L, Long.MIN_VALUE, Long.MAX_VALUE)
                        == MotdSelector.ActiveEvent.SEASON_END,
                "a full-range threshold must work without addition overflow");
    }

    private static void wholeNumbersPreserveSignedLongPrecision() {
        final String path = "motd.random-seed";
        final long aboveDoublePrecision = 9_007_199_254_740_993L;
        require(MotdSelector.parseWholeNumber(aboveDoublePrecision, 0L,
                Long.MIN_VALUE, Long.MAX_VALUE, path) == aboveDoublePrecision,
                "an exact Long seed above 2^53 must not be rounded through double");
        require(MotdSelector.parseWholeNumber(Long.MIN_VALUE, 0L,
                Long.MIN_VALUE, Long.MAX_VALUE, path) == Long.MIN_VALUE, "Long.MIN_VALUE object");
        require(MotdSelector.parseWholeNumber(Long.MAX_VALUE, 0L,
                Long.MIN_VALUE, Long.MAX_VALUE, path) == Long.MAX_VALUE, "Long.MAX_VALUE object");
        require(MotdSelector.parseWholeNumber("9223372036854775807", 0L,
                Long.MIN_VALUE, Long.MAX_VALUE, path) == Long.MAX_VALUE, "valid positive string");
        require(MotdSelector.parseWholeNumber("-9223372036854775808", 0L,
                Long.MIN_VALUE, Long.MAX_VALUE, path) == Long.MIN_VALUE, "valid negative string");
        require(MotdSelector.parseWholeNumber(null, 17L, -100L, 100L, path) == 17L,
                "missing key must use the documented default");

        expectFailure(() -> MotdSelector.parseWholeNumber(new BigInteger("9223372036854775808"), 0L,
                Long.MIN_VALUE, Long.MAX_VALUE, path));
        expectFailure(() -> MotdSelector.parseWholeNumber(new BigInteger("-9223372036854775809"), 0L,
                Long.MIN_VALUE, Long.MAX_VALUE, path));
        expectFailure(() -> MotdSelector.parseWholeNumber("9223372036854775808", 0L,
                Long.MIN_VALUE, Long.MAX_VALUE, path));
        expectFailure(() -> MotdSelector.parseWholeNumber("-9223372036854775809", 0L,
                Long.MIN_VALUE, Long.MAX_VALUE, path));
        expectFailure(() -> MotdSelector.parseWholeNumber(new BigDecimal("1.5"), 0L,
                Long.MIN_VALUE, Long.MAX_VALUE, path));
        expectFailure(() -> MotdSelector.parseWholeNumber(1.0D, 0L,
                Long.MIN_VALUE, Long.MAX_VALUE, path));
        expectFailure(() -> MotdSelector.parseWholeNumber(Double.NaN, 0L,
                Long.MIN_VALUE, Long.MAX_VALUE, path));
        expectFailure(() -> MotdSelector.parseWholeNumber(Double.POSITIVE_INFINITY, 0L,
                Long.MIN_VALUE, Long.MAX_VALUE, path));
        expectFailure(() -> MotdSelector.parseWholeNumber("not-a-number", 0L,
                Long.MIN_VALUE, Long.MAX_VALUE, path));
        expectFailure(() -> MotdSelector.parseWholeNumber(Boolean.TRUE, 0L,
                Long.MIN_VALUE, Long.MAX_VALUE, path));
        expectFailure(() -> MotdSelector.parseWholeNumber(List.of(1), 0L,
                Long.MIN_VALUE, Long.MAX_VALUE, path));
    }

    private static void booleansAreTypeStrict() {
        require(MotdSelector.parseBoolean(null, true, "motd.enabled"), "missing boolean uses default");
        require(MotdSelector.parseBoolean(Boolean.TRUE, false, "motd.enabled"), "true boolean");
        require(!MotdSelector.parseBoolean(Boolean.FALSE, true, "motd.enabled"), "false boolean");
        for (final Object invalid : List.of("true", 1, 0L, List.of(true), new Object())) {
            expectFailure(() -> MotdSelector.parseBoolean(invalid, false, "motd.enabled"));
        }
    }

    private static void placeholdersAreStrict() {
        MotdSelector.validatePlaceholders("<gray>{online}/{max}</gray>", "motd.variants.test.line1");
        MotdSelector.validatePlaceholders("{online} + {online} / {max}", "motd.variants.test.line1");
        MotdSelector.validatePlaceholders("<gradient:#fff:#000><b>Ice SMP</b></gradient>",
                "motd.variants.test.line1");
        MotdSelector.validatePlaceholders("<gold>{online}</gold> <gray>/ {max}</gray>",
                "motd.variants.test.line1");
        expectFailure(() -> MotdSelector.validatePlaceholders("{players}", "motd.variants.test.line1"));
        expectFailure(() -> MotdSelector.validatePlaceholders("{onlin}", "motd.variants.test.line1"));
        expectFailure(() -> MotdSelector.validatePlaceholders("{Online}", "motd.variants.test.line1"));
        expectFailure(() -> MotdSelector.validatePlaceholders("{online", "motd.variants.test.line1"));
        expectFailure(() -> MotdSelector.validatePlaceholders("online}", "motd.variants.test.line1"));
    }

    private static void generationLifecycleRejectsStaleCallbacks() {
        final MotdGenerationGate gate = new MotdGenerationGate();
        final AtomicInteger publications = new AtomicInteger();
        final AtomicInteger rejections = new AtomicInteger();

        final long first = gate.nextGeneration();
        final MotdGenerationGate.Attempt stale = gate.newAttempt(first);
        final long second = gate.nextGeneration();
        require(stale.runCurrent(publications::incrementAndGet), "the scheduled callback may win once");
        require(publications.get() == 0, "a late callback from the previous reload must not publish");
        require(!stale.rejectCurrent(rejections::incrementAndGet), "task/rejection gate must have one winner");

        final MotdGenerationGate.Attempt rejected = gate.newAttempt(second);
        require(rejected.rejectCurrent(rejections::incrementAndGet), "current scheduler rejection must run");
        require(rejections.get() == 1, "scheduler rejection must run exactly once");
        require(!rejected.runCurrent(publications::incrementAndGet), "late task after rejection must not run");

        final MotdGenerationGate.Attempt accepted = gate.newAttempt(second);
        require(accepted.runCurrent(publications::incrementAndGet), "current task must run");
        require(publications.get() == 1, "current task publishes exactly once");
        require(!accepted.rejectCurrent(rejections::incrementAndGet), "fallback after task must not run");

        gate.invalidate();
        require(!gate.publishIfCurrent(second, publications::incrementAndGet),
                "disable invalidation must reject an already decoded result");
        require(publications.get() == 1, "disable must not permit stale cache publication");
    }

    private static void generationAdvanceCannotOvertakePublication() throws Exception {
        final MotdGenerationGate gate = new MotdGenerationGate();
        final long current = gate.nextGeneration();
        final CountDownLatch publisherEntered = new CountDownLatch(1);
        final CountDownLatch releasePublisher = new CountDownLatch(1);
        final AtomicBoolean generationAdvanced = new AtomicBoolean();

        final Thread publisher = Thread.ofPlatform().start(() -> gate.publishIfCurrent(current, () -> {
            publisherEntered.countDown();
            await(releasePublisher);
        }));
        require(publisherEntered.await(5L, TimeUnit.SECONDS), "publisher did not enter generation fence");
        final Thread reloader = Thread.ofPlatform().start(() -> {
            gate.nextGeneration();
            generationAdvanced.set(true);
        });
        Thread.sleep(50L);
        require(!generationAdvanced.get(), "reload generation must not overtake an in-progress publication");
        releasePublisher.countDown();
        publisher.join(5_000L);
        reloader.join(5_000L);
        require(!publisher.isAlive() && !reloader.isAlive(), "generation fence threads must terminate");
        require(generationAdvanced.get(), "reload generation must advance after publication leaves the fence");
    }

    private static void await(final CountDownLatch latch) {
        try {
            if (!latch.await(5L, TimeUnit.SECONDS)) {
                throw new AssertionError("latch timeout");
            }
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted", exception);
        }
    }

    private static void invalidInputsFailClosed() {
        expectFailure(() -> MotdSelector.Mode.parse("shuffle"));
        expectFailure(() -> MotdSelector.selectIndex(MotdSelector.Mode.TIME, 0, 0L, 1L, 0L));
        expectFailure(() -> MotdSelector.selectIndex(MotdSelector.Mode.TIME, 1, 0L, 0L, 0L));
    }

    private static void bundledIconsAreValid() throws Exception {
        for (final String name : new String[]{"frost", "war", "book", "whisper", "blood_moon", "world_boss", "season_end"}) {
            try (InputStream input = MotdRegressionSuite.class.getClassLoader().getResourceAsStream("icons/" + name + ".png")) {
                require(input != null, "missing bundled icon: " + name);
                final BufferedImage image = ImageIO.read(input);
                require(image != null && image.getWidth() == 64 && image.getHeight() == 64,
                        "bundled icon must decode as exactly 64x64: " + name);
            }
        }
    }

    private static void iconDirectoryIsNoFollowAndBounded() throws Exception {
        final Path data = Files.createTempDirectory("icesmp-motd-data-");
        final Path icons = Files.createDirectory(data.resolve("icons"));
        copyBundledIcon(icons.resolve("valid.png"));
        require(MotdIconValidator.scanPngDirectory(data, Path.of("icons"), 64, 1_048_576L)
                        .icons().size() == 1,
                "a regular valid icon must load through the secure directory handle");
        copyBundledIcon(icons.resolve("valid-second.png"));
        final MotdIconValidator.ScanResult limited = MotdIconValidator.scanPngDirectory(
                data, Path.of("icons"), 1, 1_048_576L);
        require(limited.discoveredPngFiles() == 2 && limited.icons().size() == 1,
                "the configured icon count limit must be deterministic and reported");
        require(!limited.warnings().isEmpty(), "truncating the icon directory must emit a warning");

        final Path wrongSize = icons.resolve("wrong-size.png");
        ImageIO.write(new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB), "png", wrongSize.toFile());
        Files.write(icons.resolve("corrupt.png"), new byte[]{1, 2, 3, 4, 5});
        copyBundledIcon(icons.resolve("too-large.png"));
        final MotdIconValidator.ScanResult invalidScan = MotdIconValidator.scanPngDirectory(
                data, Path.of("icons"), 64, Files.size(icons.resolve("valid.png")) - 1L);
        require(invalidScan.icons().isEmpty(), "wrong-size, corrupt and oversized files must fail closed");
        require(invalidScan.warnings().size() >= 4, "every invalid PNG must be reported without aborting the scan");

        final Path linkedFile = icons.resolve("linked.png");
        final Path outside = Files.createTempDirectory("icesmp-motd-outside-");
        copyBundledIcon(outside.resolve("outside.png"));
        try {
            Files.createSymbolicLink(linkedFile, outside.resolve("outside.png"));
            final MotdIconValidator.ScanResult linkScan = MotdIconValidator.scanPngDirectory(
                    data, Path.of("icons"), 64, 1_048_576L);
            require(linkScan.icons().stream().noneMatch(icon -> icon.fileName().equals("linked.png")),
                    "a symlinked icon file must not be followed");

            final Path nested = icons.resolve("nested");
            Files.createSymbolicLink(nested, outside);
            expectIOException(() -> MotdIconValidator.readValidatedPng(data,
                    Path.of("icons", "nested", "outside.png"), 1_048_576L));
            Files.deleteIfExists(nested);

            final Path rootLinkData = Files.createTempDirectory("icesmp-motd-root-link-");
            Files.createSymbolicLink(rootLinkData.resolve("icons"), outside);
            expectIOException(() -> MotdIconValidator.scanPngDirectory(
                    rootLinkData, Path.of("icons"), 64, 1_048_576L));
            Files.deleteIfExists(rootLinkData.resolve("icons"));
            Files.deleteIfExists(rootLinkData);
        } catch (final UnsupportedOperationException | SecurityException exception) {
            // Linux CI executes the symlink assertions; unsupported local filesystems still run all other checks.
        }

        expectIOException(() -> MotdIconValidator.readValidatedPng(data,
                Path.of("..", outside.getFileName().toString(), "outside.png"), 1_048_576L));

        deleteTree(data);
        deleteTree(outside);
    }

    private static void copyBundledIcon(final Path destination) throws IOException {
        try (InputStream input = MotdRegressionSuite.class.getClassLoader().getResourceAsStream("icons/frost.png")) {
            require(input != null, "missing bundled frost icon");
            Files.copy(input, destination);
        }
    }

    private static void deleteTree(final Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            final List<Path> sorted = paths.sorted(java.util.Comparator.reverseOrder()).toList();
            for (final Path path : sorted) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static void expectFailure(final Runnable runnable) {
        try {
            runnable.run();
            throw new AssertionError("expected IllegalArgumentException");
        } catch (final IllegalArgumentException expected) {
            // expected
        }
    }

    private static void expectIOException(final CheckedRunnable runnable) throws Exception {
        try {
            runnable.run();
            throw new AssertionError("expected IOException");
        } catch (final IOException expected) {
            // expected
        }
    }

    private static void require(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    @FunctionalInterface
    private interface CheckedRunnable {
        void run() throws Exception;
    }
}
