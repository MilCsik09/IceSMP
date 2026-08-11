package hu.taliann.icesmp.playerprofile.domain;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;

/** Immutable extension-map copier for extensible PlayerProfile section records. */
public final class PlayerProfileSectionExtensions {

    private PlayerProfileSectionExtensions() {
    }

    public static <T extends ProfileSectionData> T mutate(final T current,
                                                          final UnaryOperator<Map<String, Object>> mutation) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(mutation, "mutation");
        final LinkedHashMap<String, Object> mutable = new LinkedHashMap<>(current.extensions());
        final Map<String, Object> next = Objects.requireNonNull(mutation.apply(mutable), "extension mutation");
        return copyWithExtensions(current, next);
    }

    public static <T extends ProfileSectionData> T put(final T current,
                                                       final String key,
                                                       final Object value) {
        final String normalized = ImmutableValues.id(key, "extension key");
        return mutate(current, extensions -> {
            final LinkedHashMap<String, Object> next = new LinkedHashMap<>(extensions);
            if (value == null) {
                next.remove(normalized);
            } else {
                next.put(normalized, value);
            }
            return next;
        });
    }

    @SuppressWarnings("unchecked")
    public static <T extends ProfileSectionData> T copyWithExtensions(final T current,
                                                                      final Map<String, Object> extensions) {
        Objects.requireNonNull(current, "current");
        final Map<String, Object> requested = Map.copyOf(new LinkedHashMap<>(
                Objects.requireNonNull(extensions, "extensions")));
        final Class<?> type = current.getClass();
        if (!type.isRecord()) {
            if (requested.equals(current.extensions())) {
                return current;
            }
            throw new IllegalArgumentException(
                    "PlayerProfile section does not expose extension storage: " + type.getName());
        }
        final RecordComponent[] components = type.getRecordComponents();
        final Class<?>[] parameterTypes = new Class<?>[components.length];
        final Object[] arguments = new Object[components.length];
        boolean extensionComponentFound = false;
        try {
            for (int index = 0; index < components.length; index++) {
                final RecordComponent component = components[index];
                parameterTypes[index] = component.getType();
                if ("extensions".equals(component.getName())) {
                    arguments[index] = requested;
                    extensionComponentFound = true;
                } else {
                    arguments[index] = component.getAccessor().invoke(current);
                }
            }
            if (!extensionComponentFound) {
                if (requested.equals(current.extensions())) {
                    return current;
                }
                throw new IllegalArgumentException(
                        "PlayerProfile section has no extensions component: " + type.getName());
            }
            final Constructor<?> constructor = type.getDeclaredConstructor(parameterTypes);
            return (T) constructor.newInstance(arguments);
        } catch (final ReflectiveOperationException failure) {
            final Throwable cause = failure instanceof InvocationTargetException invocation
                    && invocation.getCause() != null ? invocation.getCause() : failure;
            throw new IllegalStateException(
                    "Unable to copy PlayerProfile section extensions: " + type.getName(), cause);
        }
    }
}
