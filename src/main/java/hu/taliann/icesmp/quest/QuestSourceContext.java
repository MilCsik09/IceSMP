package hu.taliann.icesmp.quest;

import java.util.Locale;
import java.util.Objects;

/**
 * Egy TÉNYLEGESEN megtörtént forrás-esemény bizonyítéka (adapter-oldalról): "a játékos
 * X NPC-vel interaktált" / "belépett T territóriumba" / stb. Azt, hogy ez a forrás
 * jogosult-e az adott quest kiadására/leadására, KIZÁRÓLAG a {@link QuestSourcePolicy}
 * dönti el — az adapterek (FancyNpcs-híd, territórium-listener, parancsok) csak
 * kontextust szállítanak, authorityt soha.
 */
public record QuestSourceContext(Type type, String reference) {

    public enum Type {
        NPC,
        LOCATION,
        ITEM,
        EVENT,
        CHAIN,
        QUEST_BOARD,
        AUTO,
        ADMIN
    }

    public QuestSourceContext {
        Objects.requireNonNull(type, "type");
        reference = reference == null ? "" : reference.trim().toLowerCase(Locale.ROOT);
    }

    public static QuestSourceContext npc(final String npcName) {
        return new QuestSourceContext(Type.NPC, npcName);
    }

    public static QuestSourceContext territory(final String territoryId) {
        return new QuestSourceContext(Type.LOCATION, territoryId);
    }

    public static QuestSourceContext item(final String itemId) {
        return new QuestSourceContext(Type.ITEM, itemId);
    }

    public static QuestSourceContext event(final String eventId) {
        return new QuestSourceContext(Type.EVENT, eventId);
    }

    public static QuestSourceContext chain(final String previousQuestId) {
        return new QuestSourceContext(Type.CHAIN, previousQuestId);
    }

    public static QuestSourceContext board() {
        return new QuestSourceContext(Type.QUEST_BOARD, "");
    }

    public static QuestSourceContext auto() {
        return new QuestSourceContext(Type.AUTO, "");
    }

    public static QuestSourceContext admin() {
        return new QuestSourceContext(Type.ADMIN, "");
    }
}
