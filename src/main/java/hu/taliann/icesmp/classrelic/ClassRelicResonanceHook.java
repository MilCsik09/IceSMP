package hu.taliann.icesmp.classrelic;

/**
 * A Spec Resonance viselkedési szerződése. A class rework gameplay-jelzései a központi
 * dispatch-en (ClassRelicService.onGameplaySignal) érkeznek, és CSAK aktív resonance-szal
 * rendelkező feloldás juthat el ide — a hooknak nem kell (és nem szabad) class/spec/
 * ownership kérdéseket újra feltennie. A kontextus a régió-helyes {@code actor} Player
 * referenciát is hordozza: a hook az actor saját szálán fut (a dispatch kényszeríti ki),
 * ezért az actor állapotát közvetlenül érintheti; idegen célt csak scheduler-hoppal.
 */
@FunctionalInterface
public interface ClassRelicResonanceHook {

    /** Inert placeholder: a routing bizonyítottan működik, gameplay-hatás nélkül. */
    ClassRelicResonanceHook INERT = context -> {
    };

    void onSignal(ClassRelicResonanceContext context);
}
