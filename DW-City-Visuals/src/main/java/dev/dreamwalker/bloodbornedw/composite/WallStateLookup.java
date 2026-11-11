package dev.dreamwalker.bloodbornedw.composite;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.state.property.Property;

/** Bounded startup-only mixed-radix index over a complete native state domain. */
public final class WallStateLookup extends AbstractMap<Map<Property<?>, Comparable<?>>, Object> {
    private static Object previousOwner;
    private static Map<?, ?> previousSource;
    private static WallStateLookup previousIndex;
    private static int uses;
    private final Map<Map<Property<?>, Comparable<?>>, Object> source;
    private final List<Property<?>> properties;
    private final List<Map<Comparable<?>, Integer>> ordinals;
    private final Object[] states;

    private WallStateLookup(Map<Map<Property<?>, Comparable<?>>, Object> source) {
        this.source = source;
        if (source.isEmpty()) throw new IllegalArgumentException("empty state domain");
        properties = new ArrayList<>(source.keySet().iterator().next().keySet());
        ordinals = new ArrayList<>(properties.size());
        int size = 1;
        for (Property<?> property : properties) {
            Map<Comparable<?>, Integer> values = new HashMap<>();
            for (Comparable<?> value : property.getValues()) values.put(value, values.size());
            if (values.isEmpty() || size > 100000 / values.size()) throw new IllegalArgumentException("unsupported state domain");
            size *= values.size();
            ordinals.add(values);
        }
        if (size != source.size()) throw new IllegalArgumentException("non-cartesian state domain");
        states = new Object[size];
        for (var entry : source.entrySet()) {
            int index = index(entry.getKey());
            if (index < 0 || states[index] != null || entry.getValue() == null) throw new IllegalArgumentException("invalid state domain");
            states[index] = entry.getValue();
        }
    }

    private int index(Object key) {
        if (!(key instanceof Map<?, ?> map) || map.size() != properties.size()) return -1;
        int value = 0;
        for (int i = 0; i < properties.size(); i++) {
            Integer digit = ordinals.get(i).get(map.get(properties.get(i)));
            if (digit == null) return -1;
            value = value * ordinals.get(i).size() + digit;
        }
        return value;
    }
    @Override public Object get(Object key) {
        int index = index(key);
        return index < 0 ? source.get(key) : states[index];
    }
    @Override public Set<Entry<Map<Property<?>, Comparable<?>>, Object>> entrySet() { return source.entrySet(); }
    @Override public int size() { return source.size(); }

    @SuppressWarnings("unchecked")
    public static synchronized Map<Map<Property<?>, Comparable<?>>, Object> prepare(Object owner, Map<?, ?> raw) {
        if (!(owner instanceof ImportedCompactBlock) || raw.size() < 20000 || raw.size() > 100000)
            return (Map<Map<Property<?>, Comparable<?>>, Object>) raw;
        if (previousOwner != owner || previousSource != raw) {
            previousOwner = null; previousSource = null; previousIndex = null; uses = 0;
            try {
                var source = (Map<Map<Property<?>, Comparable<?>>, Object>) raw;
                previousIndex = new WallStateLookup(source);
                previousOwner = owner; previousSource = raw;
            } catch (RuntimeException unsupported) {
                previousOwner = owner; previousSource = raw; previousIndex = null;
            }
        }
        WallStateLookup index = previousIndex;
        if (++uses >= raw.size()) { previousOwner = null; previousSource = null; previousIndex = null; uses = 0; }
        return index == null ? (Map<Map<Property<?>, Comparable<?>>, Object>) raw : index;
    }
}
