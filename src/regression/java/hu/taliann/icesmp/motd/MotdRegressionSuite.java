package hu.taliann.icesmp.motd;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

public final class MotdRegressionSuite {

    private MotdRegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        timeRotationUsesFloorMod();
        randomRotationIsStableWithinBucket();
        randomRotationCoversPoolAndUsesSeed();
        eventPriorityIsStrict();
        seasonThresholdIsMillisecondPrecise();
        invalidInputsFailClosed();
        bundledIconsAreValid();
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

    private static void expectFailure(final Runnable runnable) {
        try {
            runnable.run();
            throw new AssertionError("expected IllegalArgumentException");
        } catch (final IllegalArgumentException expected) {
            // expected
        }
    }

    private static void require(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
