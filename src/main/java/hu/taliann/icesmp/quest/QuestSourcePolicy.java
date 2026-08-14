package hu.taliann.icesmp.quest;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;
import java.util.Objects;

/**
 * Egy quest start- és turn-in-forrás policyje — a felvétel/leadás EGYETLEN
 * authority-döntési pontja. Két külön kérdés marad külön: az eligibilitás
 * (szint/kaszt/frakció/előfeltétel — QuestManager.getAcceptBlocker) és a
 * forrás-jogosultság (ez az osztály): "jogosult vagyok rá" ≠ "innen most
 * ténylegesen felvehetem".
 */
public record QuestSourcePolicy(
        StartType startType,
        String startReference,
        boolean chainAutoAccept,
        TurnInType turnInType,
        String turnInReference) {

    public enum StartType { NPC, LOCATION, ITEM, EVENT, CHAIN, QUEST_BOARD, AUTO, ADMIN }

    public enum TurnInType { NPC, LOCATION, AUTO, EVENT, ADMIN }

    public QuestSourcePolicy {
        Objects.requireNonNull(startType, "startType");
        Objects.requireNonNull(turnInType, "turnInType");
        startReference = normalize(startReference);
        turnInReference = normalize(turnInReference);
    }

    /**
     * A kanonikus schema (`start:`/`turn-in:` szekciók) fordítója. A legacy `giver-npc`
     * mező rövid, dokumentált kompatibilitási adapterként NPC start+turn-in-re képződik le;
     * start-szekció nélküli (admin-készítette) quest alapból QUEST_BOARD megbízás. A
     * turn-in defaultja NPC-startnál ugyanaz az NPC (MMO-alak), minden másnál AUTO.
     */
    public static QuestSourcePolicy parse(final ConfigurationSection quest) {
        Objects.requireNonNull(quest, "quest");
        final ConfigurationSection start = quest.getConfigurationSection("start");
        StartType startType;
        String startReference = "";
        boolean chainAutoAccept = false;
        if (start != null) {
            startType = parseStart(start.getString("type", ""));
            if (startType == null) {
                throw new IllegalArgumentException("invalid start.type: "
                        + start.getString("type", ""));
            }
            startReference = switch (startType) {
                case NPC -> required(start.getString("npc"), "start.npc");
                case LOCATION -> required(start.getString("territory"), "start.territory");
                case ITEM -> required(start.getString("item-id"), "start.item-id");
                case EVENT -> required(start.getString("event-id"), "start.event-id");
                case CHAIN, QUEST_BOARD, AUTO, ADMIN -> "";
            };
            chainAutoAccept = startType == StartType.CHAIN && start.getBoolean("auto-accept", false);
        } else {
            final String legacyGiver = quest.getString("giver-npc", "");
            final String legacyTerritory = quest.getString("auto-start-territory", "");
            if (!legacyGiver.isBlank()) {
                startType = StartType.NPC;
                startReference = legacyGiver;
            } else if (!legacyTerritory.isBlank()) {
                startType = StartType.LOCATION;
                startReference = legacyTerritory;
            } else {
                startType = StartType.QUEST_BOARD;
            }
        }

        final ConfigurationSection turnIn = quest.getConfigurationSection("turn-in");
        TurnInType turnInType;
        String turnInReference = "";
        if (turnIn != null) {
            turnInType = parseTurnIn(turnIn.getString("type", ""));
            if (turnInType == null) {
                throw new IllegalArgumentException("invalid turn-in.type: "
                        + turnIn.getString("type", ""));
            }
            turnInReference = switch (turnInType) {
                case NPC -> required(turnIn.getString("npc"), "turn-in.npc");
                case LOCATION -> required(turnIn.getString("territory"), "turn-in.territory");
                case EVENT -> required(turnIn.getString("event-id"), "turn-in.event-id");
                case AUTO, ADMIN -> "";
            };
        } else if (startType == StartType.NPC) {
            turnInType = TurnInType.NPC;
            turnInReference = startReference;
        } else {
            turnInType = TurnInType.AUTO;
        }
        return new QuestSourcePolicy(startType, startReference, chainAutoAccept,
                turnInType, turnInReference);
    }

    /** Forrás-jogosultság a felvételhez; ADMIN kontextus mindig jogosult (admin-parancs). */
    public boolean startAuthorized(final QuestSourceContext context) {
        if (context == null) {
            return false;
        }
        if (context.type() == QuestSourceContext.Type.ADMIN) {
            return true;
        }
        return switch (startType) {
            case NPC -> context.type() == QuestSourceContext.Type.NPC
                    && startReference.equals(context.reference());
            case LOCATION -> context.type() == QuestSourceContext.Type.LOCATION
                    && startReference.equals(context.reference());
            case ITEM -> context.type() == QuestSourceContext.Type.ITEM
                    && startReference.equals(context.reference());
            case EVENT -> context.type() == QuestSourceContext.Type.EVENT
                    && startReference.equals(context.reference());
            case CHAIN -> context.type() == QuestSourceContext.Type.CHAIN;
            case QUEST_BOARD -> context.type() == QuestSourceContext.Type.QUEST_BOARD;
            case AUTO -> context.type() == QuestSourceContext.Type.AUTO;
            case ADMIN -> false;
        };
    }

    /** Forrás-jogosultság a leadáshoz; AUTO turn-in a feladat-teljesítés pillanatában jogosult. */
    public boolean turnInAuthorized(final QuestSourceContext context) {
        if (context == null) {
            return false;
        }
        if (context.type() == QuestSourceContext.Type.ADMIN) {
            return true;
        }
        return switch (turnInType) {
            case NPC -> context.type() == QuestSourceContext.Type.NPC
                    && turnInReference.equals(context.reference());
            case LOCATION -> context.type() == QuestSourceContext.Type.LOCATION
                    && turnInReference.equals(context.reference());
            case EVENT -> context.type() == QuestSourceContext.Type.EVENT
                    && turnInReference.equals(context.reference());
            case AUTO -> context.type() == QuestSourceContext.Type.AUTO;
            case ADMIN -> false;
        };
    }

    public boolean autoTurnIn() {
        return turnInType == TurnInType.AUTO;
    }

    private static StartType parseStart(final String raw) {
        try {
            return StartType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException unknown) {
            return null;
        }
    }

    private static TurnInType parseTurnIn(final String raw) {
        try {
            return TurnInType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException unknown) {
            return null;
        }
    }

    private static String required(final String value, final String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("missing required source field: " + field);
        }
        return value;
    }

    private static String normalize(final String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
