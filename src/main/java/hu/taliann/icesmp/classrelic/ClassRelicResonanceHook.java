package hu.taliann.icesmp.classrelic;

import java.util.Set;

/**
 * A Spec Resonance viselkedési szerződése. A class rework gameplay-eseményei a központi
 * dispatch-en (ClassRelicService.onGameplayEvent) érkeznek, és CSAK aktív resonance-szal
 * rendelkező feloldás juthat el ide — a hooknak nem kell (és nem szabad) class/spec/
 * ownership kérdéseket újra feltennie. Az implementáció a hívó szálán fut (Folia:
 * a játékos entity-schedulerén), ezért játékos-állapotot közvetlenül érinthet.
 */
@FunctionalInterface
public interface ClassRelicResonanceHook {

    /** Inert placeholder: a routing bizonyítottan működik, gameplay-hatás nélkül. */
    ClassRelicResonanceHook INERT = (activation, event, tags) -> {
    };

    void onGameplayEvent(ClassRelicActivation activation, ClassGameplayEvent event,
                         Set<AbilityTag> tags);
}
