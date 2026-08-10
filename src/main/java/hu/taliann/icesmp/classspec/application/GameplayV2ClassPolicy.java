package hu.taliann.icesmp.classspec.application;
import java.util.Locale;import java.util.Set;import java.util.TreeSet;
/** Explicit allowlist of classes whose gameplay-v2 vertical slice is complete. */
public final class GameplayV2ClassPolicy {private static final Set<String> ENABLED=Set.of("warrior","evoker","archer","shaman","monk");private GameplayV2ClassPolicy(){}public static boolean isEnabled(String id){return id!=null&&ENABLED.contains(id.toLowerCase(Locale.ROOT));}public static String enabledList(){return String.join(", ",new TreeSet<>(ENABLED));}}
