package hu.taliann.icesmp.classspec.persistence;

import hu.taliann.icesmp.classspec.domain.CapstoneStatus;
import hu.taliann.icesmp.classspec.domain.ClassLoadout;
import hu.taliann.icesmp.classspec.domain.ClassProfile;
import hu.taliann.icesmp.classspec.domain.ClassSpecCatalog;
import hu.taliann.icesmp.classspec.domain.CompanionProfile;
import hu.taliann.icesmp.classspec.domain.LoadoutSlot;
import hu.taliann.icesmp.classspec.domain.LoadoutStatus;
import hu.taliann.icesmp.classspec.domain.MasteryProgress;
import hu.taliann.icesmp.classspec.domain.MigrationState;
import hu.taliann.icesmp.classspec.domain.ProfileDiagnostics;
import hu.taliann.icesmp.classspec.domain.ProfileStatus;
import hu.taliann.icesmp.classspec.domain.SealCause;
import hu.taliann.icesmp.classspec.domain.SealReason;
import hu.taliann.icesmp.classspec.domain.SoulbondState;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.CRC32;

/** Deterministic, bounded and checksummed ICS2 Profile v2 codec. */
public final class ClassProfileCodec {

    public static final byte[] MAGIC = {'I', 'C', 'S', '2'};
    public static final int CODEC_VERSION = 1;
    private static final int HEADER_BYTES = MAGIC.length + Integer.BYTES + Integer.BYTES;
    private static final int CHECKSUM_BYTES = Integer.BYTES;

    private final Limits limits;

    public ClassProfileCodec() {
        this(Limits.defaults());
    }

    public ClassProfileCodec(final Limits limits) {
        this.limits = limits;
    }

    public byte[] encode(final ClassProfile profile) {
        if (profile == null) {
            throw new IllegalArgumentException("profile must not be null");
        }
        final Writer payload = new Writer(limits.maxPayloadBytes(), limits);
        writeProfile(payload, profile);
        final byte[] payloadBytes = payload.toByteArray();

        final Writer envelope = new Writer(HEADER_BYTES + payloadBytes.length + CHECKSUM_BYTES, limits);
        envelope.writeRaw(MAGIC);
        envelope.writeInt(CODEC_VERSION);
        envelope.writeInt(payloadBytes.length);
        envelope.writeRaw(payloadBytes);
        final byte[] checksummed = envelope.toByteArray();
        final CRC32 crc = new CRC32();
        crc.update(checksummed);
        envelope.writeInt((int) crc.getValue());
        return envelope.toByteArray();
    }

    public ClassProfile decode(final byte[] encoded) throws DecodeException {
        if (encoded == null) {
            throw new DecodeException("Profile payload is null");
        }
        if (encoded.length < HEADER_BYTES + CHECKSUM_BYTES) {
            throw new DecodeException("Truncated ICS2 envelope");
        }
        final Reader envelope = new Reader(encoded, limits);
        final byte[] magic = envelope.readRaw(MAGIC.length);
        if (!Arrays.equals(MAGIC, magic)) {
            throw new DecodeException("Invalid ICS2 magic");
        }
        final int codecVersion = envelope.readInt();
        if (codecVersion != CODEC_VERSION) {
            throw new DecodeException("Unsupported profile codec version: " + codecVersion);
        }
        final int payloadLength = envelope.readLength(limits.maxPayloadBytes(), "payload");
        final long expectedLength = (long) HEADER_BYTES + payloadLength + CHECKSUM_BYTES;
        if (expectedLength != encoded.length) {
            throw new DecodeException(expectedLength < encoded.length
                    ? "Trailing data after ICS2 envelope" : "Truncated ICS2 payload");
        }
        final byte[] payload = envelope.readRaw(payloadLength);
        final long storedChecksum = Integer.toUnsignedLong(envelope.readInt());
        final CRC32 crc = new CRC32();
        crc.update(encoded, 0, HEADER_BYTES + payloadLength);
        if (storedChecksum != crc.getValue()) {
            throw new DecodeException("ICS2 checksum mismatch");
        }
        envelope.requireExhausted("envelope");

        final Reader reader = new Reader(payload, limits);
        final ClassProfile profile;
        try {
            profile = readProfile(reader);
        } catch (final DecodeException failure) {
            throw failure;
        } catch (final RuntimeException invalidDomain) {
            throw new DecodeException("Decoded profile violates domain invariants", invalidDomain);
        }
        reader.requireExhausted("profile payload");
        return profile;
    }

    private void writeProfile(final Writer writer, final ClassProfile profile) {
        writer.writeInt(profile.schemaVersion());
        writer.writeLong(profile.revision());
        writer.writeEnum(profile.status());
        writer.writeString(profile.primaryClassId());
        writer.writeInt(profile.classLevel());
        writer.writeNullableEnum(profile.activeSlot());
        writer.writeBoolean(profile.secondSpecUnlocked());
        writer.writeCount(profile.loadouts().size(), "loadouts");
        for (final ClassLoadout loadout : profile.loadouts()) {
            writeLoadout(writer, loadout);
        }
        writer.writeString(profile.migrationState().lastSuccessfulMigration());
        writer.writeStringList(profile.migrationState().reviewReasons());
        writer.writeStringMap(profile.migrationState().preservedLegacy());
        writer.writeString(profile.diagnostics().quarantineReason());
        writer.writeString(profile.diagnostics().sessionBlockReason());
    }

    private ClassProfile readProfile(final Reader reader) throws DecodeException {
        final int schemaVersion = reader.readInt();
        final long revision = reader.readLong();
        final ProfileStatus status = reader.readEnum(ProfileStatus.class);
        final String classId = reader.readString();
        final int classLevel = reader.readInt();
        final LoadoutSlot activeSlot = reader.readNullableEnum(LoadoutSlot.class);
        final boolean secondUnlocked = reader.readBoolean();
        final int loadoutCount = reader.readCount("loadouts");
        if (loadoutCount != 2) {
            throw new DecodeException("Profile v2 must contain exactly two loadouts");
        }
        final List<ClassLoadout> loadouts = new ArrayList<>(loadoutCount);
        for (int index = 0; index < loadoutCount; index++) {
            loadouts.add(readLoadout(reader));
        }
        final MigrationState migration = new MigrationState(reader.readString(),
                reader.readStringList(), reader.readStringMap());
        final ProfileDiagnostics diagnostics = new ProfileDiagnostics(
                reader.readString(), reader.readString());
        return ClassProfile.builder()
                .schemaVersion(schemaVersion)
                .revision(revision)
                .status(status)
                .primaryClassId(classId)
                .classLevel(classLevel)
                .activeSlot(activeSlot)
                .secondSpecUnlocked(secondUnlocked)
                .loadout(LoadoutSlot.FIRST, loadouts.get(0))
                .loadout(LoadoutSlot.SECOND, loadouts.get(1))
                .migrationState(migration)
                .diagnostics(diagnostics)
                .build();
    }

    private void writeLoadout(final Writer writer, final ClassLoadout loadout) {
        writer.writeString(loadout.specializationId());
        writer.writeEnum(loadout.status());
        writer.writeBoolean(loadout.sealReason() != null);
        if (loadout.sealReason() != null) {
            writer.writeEnum(loadout.sealReason().cause());
            writer.writeString(loadout.sealReason().gateId());
            writer.writeString(loadout.sealReason().detail());
        }
        writer.writeStringMap(loadout.doctrineChoices());
        writer.writeInt(loadout.mastery().rank());
        writer.writeLong(loadout.mastery().experience());
        writer.writeBoolean(loadout.soulbond() != null);
        if (loadout.soulbond() != null) {
            writer.writeUuid(loadout.soulbond().signatureId());
            writer.writeInt(loadout.soulbond().evolution());
            writer.writeStringList(loadout.soulbond().modules());
            writer.writeLong(loadout.soulbond().revision());
            writer.writeString(loadout.soulbond().recoveryNote());
        }
        writer.writeStringSet(loadout.favoriteSpells());
        writer.writeString(loadout.selectedSpell());
        writer.writeEnum(loadout.capstoneStatus());
        writer.writeCount(loadout.companionRoster().size(), "companion roster");
        loadout.companionRoster().entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
                .forEach(entry -> writeCompanion(writer, entry.getValue()));
        writer.writeStringMap(loadout.mechanicState());
        writer.writeString(loadout.migrationNote());
    }

    private ClassLoadout readLoadout(final Reader reader) throws DecodeException {
        final String specId = reader.readString();
        final LoadoutStatus status = reader.readEnum(LoadoutStatus.class);
        final SealReason sealReason;
        if (reader.readBoolean()) {
            sealReason = new SealReason(reader.readEnum(SealCause.class),
                    reader.readString(), reader.readString());
        } else {
            sealReason = null;
        }
        final Map<String, String> doctrines = reader.readStringMap();
        final MasteryProgress mastery = new MasteryProgress(reader.readInt(), reader.readLong());
        final SoulbondState soulbond;
        if (reader.readBoolean()) {
            soulbond = new SoulbondState(reader.readUuid(), reader.readInt(), reader.readStringList(),
                    reader.readLong(), reader.readString());
        } else {
            soulbond = null;
        }
        final Set<String> favorites = reader.readStringSet();
        final String selected = reader.readString();
        final CapstoneStatus capstone = reader.readEnum(CapstoneStatus.class);
        final int rosterSize = reader.readCount("companion roster");
        final Map<UUID, CompanionProfile> roster = new LinkedHashMap<>();
        for (int index = 0; index < rosterSize; index++) {
            final CompanionProfile companion = readCompanion(reader);
            if (roster.putIfAbsent(companion.companionId(), companion) != null) {
                throw new DecodeException("Repeated companion id: " + companion.companionId());
            }
        }
        return new ClassLoadout(specId, status, sealReason, doctrines, mastery, soulbond, favorites,
                selected, capstone, roster, reader.readStringMap(), reader.readString());
    }

    private void writeCompanion(final Writer writer, final CompanionProfile companion) {
        writer.writeUuid(companion.companionId());
        writer.writeString(companion.namespace());
        writer.writeString(companion.typeId());
        writer.writeString(companion.name());
        writer.writeInt(companion.level());
        writer.writeLong(companion.experience());
        writer.writeString(companion.traitId());
        writer.writeString(companion.stance());
        writer.writeStringList(companion.equipmentIds());
        writer.writeLong(companion.resummonAtEpochMillis());
        writer.writeStringMap(companion.persistentState());
    }

    private CompanionProfile readCompanion(final Reader reader) throws DecodeException {
        return new CompanionProfile(reader.readUuid(), reader.readString(), reader.readString(),
                reader.readString(), reader.readInt(), reader.readLong(), reader.readString(),
                reader.readString(), reader.readStringList(), reader.readLong(), reader.readStringMap());
    }

    /** Hard limits are part of the decode contract and are injectable for focused regressions. */
    public record Limits(int maxPayloadBytes, int maxStringBytes, int maxCollectionEntries) {
        public Limits {
            if (maxPayloadBytes < 64 || maxStringBytes < 1 || maxCollectionEntries < 1
                    || maxStringBytes > maxPayloadBytes) {
                throw new IllegalArgumentException("Invalid Profile v2 codec limits");
            }
        }

        public static Limits defaults() {
            return new Limits(1_048_576, 4_096, 1_024);
        }
    }

    public static final class DecodeException extends Exception {
        private static final long serialVersionUID = 1L;

        public DecodeException(final String message) {
            super(message);
        }

        public DecodeException(final String message, final Throwable cause) {
            super(message, cause);
        }
    }

    private static final class Writer {
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private final int maximumBytes;
        private final Limits limits;

        private Writer(final int maximumBytes, final Limits limits) {
            this.maximumBytes = maximumBytes;
            this.limits = limits;
        }

        private void writeRaw(final byte[] bytes) {
            ensure(bytes.length);
            output.writeBytes(bytes);
        }

        private void writeBoolean(final boolean value) {
            ensure(1);
            output.write(value ? 1 : 0);
        }

        private void writeInt(final int value) {
            ensure(Integer.BYTES);
            output.write((value >>> 24) & 0xff);
            output.write((value >>> 16) & 0xff);
            output.write((value >>> 8) & 0xff);
            output.write(value & 0xff);
        }

        private void writeLong(final long value) {
            ensure(Long.BYTES);
            for (int shift = 56; shift >= 0; shift -= 8) {
                output.write((int) (value >>> shift) & 0xff);
            }
        }

        private void writeUuid(final UUID value) {
            writeLong(value.getMostSignificantBits());
            writeLong(value.getLeastSignificantBits());
        }

        private void writeString(final String value) {
            if (value == null) {
                throw new IllegalArgumentException("Codec strings must not be null");
            }
            final byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            if (bytes.length > limits.maxStringBytes()) {
                throw new IllegalArgumentException("Profile string exceeds codec limit");
            }
            writeInt(bytes.length);
            writeRaw(bytes);
        }

        private void writeEnum(final Enum<?> value) {
            writeString(value.name());
        }

        private void writeNullableEnum(final Enum<?> value) {
            writeBoolean(value != null);
            if (value != null) {
                writeEnum(value);
            }
        }

        private void writeCount(final int count, final String label) {
            if (count < 0 || count > limits.maxCollectionEntries()) {
                throw new IllegalArgumentException(label + " exceeds codec entry limit");
            }
            writeInt(count);
        }

        private void writeStringList(final List<String> values) {
            writeCount(values.size(), "list");
            values.forEach(this::writeString);
        }

        private void writeStringSet(final Set<String> values) {
            writeCount(values.size(), "set");
            values.stream().sorted().forEach(this::writeString);
        }

        private void writeStringMap(final Map<String, String> values) {
            writeCount(values.size(), "map");
            values.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                writeString(entry.getKey());
                writeString(entry.getValue());
            });
        }

        private byte[] toByteArray() {
            return output.toByteArray();
        }

        private void ensure(final int additional) {
            if (additional < 0 || (long) output.size() + additional > maximumBytes) {
                throw new IllegalArgumentException("Profile payload exceeds codec size limit");
            }
        }
    }

    private static final class Reader {
        private final byte[] input;
        private final Limits limits;
        private int position;

        private Reader(final byte[] input, final Limits limits) {
            this.input = input;
            this.limits = limits;
        }

        private byte[] readRaw(final int length) throws DecodeException {
            require(length);
            final byte[] result = Arrays.copyOfRange(input, position, position + length);
            position += length;
            return result;
        }

        private boolean readBoolean() throws DecodeException {
            require(1);
            final int value = input[position++] & 0xff;
            if (value != 0 && value != 1) {
                throw new DecodeException("Invalid boolean value: " + value);
            }
            return value == 1;
        }

        private int readInt() throws DecodeException {
            require(Integer.BYTES);
            final int value = ((input[position] & 0xff) << 24)
                    | ((input[position + 1] & 0xff) << 16)
                    | ((input[position + 2] & 0xff) << 8)
                    | (input[position + 3] & 0xff);
            position += Integer.BYTES;
            return value;
        }

        private long readLong() throws DecodeException {
            require(Long.BYTES);
            long value = 0L;
            for (int index = 0; index < Long.BYTES; index++) {
                value = (value << 8) | (input[position++] & 0xffL);
            }
            return value;
        }

        private UUID readUuid() throws DecodeException {
            return new UUID(readLong(), readLong());
        }

        private int readLength(final int maximum, final String label) throws DecodeException {
            final int length = readInt();
            if (length < 0 || length > maximum) {
                throw new DecodeException("Invalid " + label + " length: " + length);
            }
            return length;
        }

        private int readCount(final String label) throws DecodeException {
            return readLength(limits.maxCollectionEntries(), label);
        }

        private String readString() throws DecodeException {
            final int length = readLength(limits.maxStringBytes(), "string");
            require(length);
            try {
                final String value = StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(input, position, length)).toString();
                position += length;
                return value;
            } catch (final CharacterCodingException malformed) {
                throw new DecodeException("Invalid UTF-8 in profile payload", malformed);
            }
        }

        private <E extends Enum<E>> E readEnum(final Class<E> type) throws DecodeException {
            final String raw = readString();
            try {
                return Enum.valueOf(type, raw);
            } catch (final IllegalArgumentException unknown) {
                throw new DecodeException("Unknown " + type.getSimpleName() + ": " + raw, unknown);
            }
        }

        private <E extends Enum<E>> E readNullableEnum(final Class<E> type) throws DecodeException {
            return readBoolean() ? readEnum(type) : null;
        }

        private List<String> readStringList() throws DecodeException {
            final int count = readCount("list");
            final List<String> result = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                result.add(readString());
            }
            return List.copyOf(result);
        }

        private Set<String> readStringSet() throws DecodeException {
            final int count = readCount("set");
            final Set<String> result = new LinkedHashSet<>();
            final Set<String> normalized = new LinkedHashSet<>();
            for (int index = 0; index < count; index++) {
                final String value = readString();
                if (!result.add(value) || !normalized.add(ClassSpecCatalog.normalize(value))) {
                    throw new DecodeException("Repeated or normalized-colliding set value: " + value);
                }
            }
            return Set.copyOf(result);
        }

        private Map<String, String> readStringMap() throws DecodeException {
            final int count = readCount("map");
            final Map<String, String> result = new LinkedHashMap<>();
            final Set<String> normalized = new LinkedHashSet<>();
            for (int index = 0; index < count; index++) {
                final String key = readString();
                final String value = readString();
                if (result.putIfAbsent(key, value) != null
                        || !normalized.add(ClassSpecCatalog.normalize(key))) {
                    throw new DecodeException("Repeated or normalized-colliding map key: " + key);
                }
            }
            return Map.copyOf(result);
        }

        private void require(final int length) throws DecodeException {
            if (length < 0 || (long) position + length > input.length) {
                throw new DecodeException("Truncated profile payload");
            }
        }

        private void requireExhausted(final String label) throws DecodeException {
            if (position != input.length) {
                throw new DecodeException("Trailing data in " + label);
            }
        }
    }
}
