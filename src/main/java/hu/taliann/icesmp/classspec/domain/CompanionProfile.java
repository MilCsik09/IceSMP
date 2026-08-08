package hu.taliann.icesmp.classspec.domain;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Durable logical companion data; a live entity UUID is intentionally absent. */
public record CompanionProfile(UUID companionId, String namespace, String typeId, String name,
                               int level, long experience, String traitId, String stance,
                               List<String> equipmentIds, long resummonAtEpochMillis,
                               Map<String, String> persistentState) {
    public static final int MAX_LEVEL = 10_000;
    public static final int MAX_EQUIPMENT = 32;
    public static final int MAX_STATE_ENTRIES = 128;
    public CompanionProfile {
        Objects.requireNonNull(companionId, "companionId");
        namespace = required(namespace, "namespace");
        typeId = required(typeId, "typeId");
        name = clean(name, 128); traitId = clean(traitId, 128); stance = clean(stance, 64);
        if (level < 1 || level > MAX_LEVEL || experience < 0L || resummonAtEpochMillis < 0L)
            throw new IllegalArgumentException("Companion level/experience/timestamps are outside bounds");
        equipmentIds = List.copyOf(Objects.requireNonNull(equipmentIds, "equipmentIds"));
        if (equipmentIds.size() > MAX_EQUIPMENT)
            throw new IllegalArgumentException("Companion equipment exceeds " + MAX_EQUIPMENT + " entries");
        if (equipmentIds.stream().anyMatch(value -> value == null || value.isBlank()))
            throw new IllegalArgumentException("Companion equipment ids must be non-blank");
        persistentState = collisionSafeMap(persistentState);
    }
    public CompanionProfile withName(final String value) { return copy(value, level, experience, stance, equipmentIds, resummonAtEpochMillis, persistentState); }
    public CompanionProfile withStance(final String value) { return copy(name, level, experience, value, equipmentIds, resummonAtEpochMillis, persistentState); }
    public CompanionProfile withProgress(final int lvl, final long xp) { return copy(name, lvl, xp, stance, equipmentIds, resummonAtEpochMillis, persistentState); }
    public CompanionProfile withEquipment(final List<String> value) { return copy(name, level, experience, stance, value, resummonAtEpochMillis, persistentState); }
    public CompanionProfile withPersistentState(final Map<String,String> value) { return copy(name, level, experience, stance, equipmentIds, resummonAtEpochMillis, value); }
    public CompanionProfile withResummonAt(final long value) { return copy(name, level, experience, stance, equipmentIds, value, persistentState); }
    private CompanionProfile copy(String n,int l,long xp,String s,List<String> eq,long resummonAt,Map<String,String> state) {
        return new CompanionProfile(companionId, namespace, typeId, n, l, xp, traitId, s, eq, resummonAt, state);
    }
    private static String required(final String value, final String field) {
        final String cleaned=clean(value,128); if(cleaned.isEmpty()) throw new IllegalArgumentException(field+" must be non-blank"); return cleaned;
    }
    private static String clean(final String value, final int maximum) {
        final String cleaned=value==null?"":value.trim(); if(cleaned.length()>maximum) throw new IllegalArgumentException("Companion text exceeds "+maximum+" characters"); return cleaned;
    }
    private static Map<String,String> collisionSafeMap(final Map<String,String> source) {
        Objects.requireNonNull(source,"persistentState"); if(source.size()>MAX_STATE_ENTRIES) throw new IllegalArgumentException("Companion state exceeds "+MAX_STATE_ENTRIES+" entries");
        final Map<String,String> result=new LinkedHashMap<>(); final Set<String> normalized=new LinkedHashSet<>();
        for(final Map.Entry<String,String> entry:source.entrySet()) {
            if(entry.getKey()==null||entry.getKey().isBlank()||entry.getValue()==null) throw new IllegalArgumentException("Companion state keys and values must be present");
            final String key=ClassSpecCatalog.normalize(entry.getKey()); if(!normalized.add(key)) throw new IllegalArgumentException("Normalized companion state key collision: "+entry.getKey());
            result.put(key,clean(entry.getValue(),4096));
        }
        return Map.copyOf(result);
    }
}
