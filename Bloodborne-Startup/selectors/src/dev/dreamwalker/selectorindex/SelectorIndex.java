package dev.dreamwalker.selectorindex;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.class_2248;
import net.minecraft.class_2680;
import net.minecraft.class_2689;
import net.minecraft.class_2769;
import net.minecraft.class_2960;
import net.minecraft.class_7923;
import net.minecraft.class_812;
import net.minecraft.class_815;
import net.minecraft.class_818;
import net.minecraft.class_819;

/** Temporary index of conjunctions of single-value selectors during model loading. */
public final class SelectorIndex {
    private static final LongAdder GRAPHS = new LongAdder();
    private static final LongAdder LOOKUPS = new LongAdder();
    private static final LongAdder SKIPPED_CHECKS = new LongAdder();
    private static final int MAX_SLOTS = 16384;
    private static final int MAX_REFERENCES = 1000000;
    private static final Field COMPONENT_SELECTOR = field(class_819.class, "field_4335");
    private static final Field AND_CHILDREN = field(class_812.class, "field_4324");
    private static final Field SIMPLE_NAME = field(class_818.class, "field_4333");
    private static final Field SIMPLE_VALUE = field(class_818.class, "field_4332");

    private final int originalSize;
    private final List<class_2769<?>> properties;
    private final List<Map<Comparable<?>, Integer>> ordinals;
    private final int[] strides;
    private final List<class_819>[] buckets;

    private SelectorIndex(int originalSize, List<class_2769<?>> properties, List<Map<Comparable<?>, Integer>> ordinals,
                          int[] strides, List<class_819>[] buckets) {
        this.originalSize = originalSize;
        this.properties = properties;
        this.ordinals = ordinals;
        this.strides = strides;
        this.buckets = buckets;
    }

    private static Field field(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    public static SelectorIndex create(class_2689<class_2248, class_2680> manager, List<class_819> components) {
        if (COMPONENT_SELECTOR == null || AND_CHILDREN == null || SIMPLE_NAME == null || SIMPLE_VALUE == null || components.size() < 32) return null;
        class_2960 id = class_7923.field_41175.method_10221(manager.method_11660());
        if (id == null || !id.method_12836().equals("bloodborne_dw") || !id.method_12832().startsWith("source_") || !(manager.method_11660() instanceof dev.dreamwalker.bloodbornedw.composite.ImportedCompactBlock)) return null;
        try {
            List<Map<class_2769<?>, Comparable<?>>> conditions = new ArrayList<>(components.size());
            Map<class_2769<?>, Boolean> usedProperties = new LinkedHashMap<>();
            for (class_819 component : components) {
                Map<class_2769<?>, Comparable<?>> values = new LinkedHashMap<>();
                if (!readSelector((class_815) COMPONENT_SELECTOR.get(component), manager, values, 0) || values.isEmpty()) return null;
                for (class_2769<?> property : values.keySet()) usedProperties.put(property, Boolean.TRUE);
                conditions.add(values);
            }
            if (usedProperties.size() > 6) return null;
            List<class_2769<?>> properties = new ArrayList<>(usedProperties.keySet());
            List<Map<Comparable<?>, Integer>> ordinals = new ArrayList<>();
            int[] strides = new int[properties.size()];
            int size = 1;
            for (int i = 0; i < properties.size(); i++) {
                strides[i] = size;
                Map<Comparable<?>, Integer> values = new HashMap<>();
                for (Comparable<?> value : properties.get(i).method_11898()) values.put(value, values.size());
                if (values.isEmpty() || size > MAX_SLOTS / values.size()) return null;
                size *= values.size();
                ordinals.add(values);
            }
            @SuppressWarnings("unchecked") List<class_819>[] buckets = (List<class_819>[]) new List<?>[size];
            int references = 0;
            for (int n = 0; n < components.size(); n++) {
                Map<class_2769<?>, Comparable<?>> condition = conditions.get(n);
                int[] required = new int[properties.size()];
                int count = 1;
                for (int i = 0; i < properties.size(); i++) {
                    Comparable<?> value = condition.get(properties.get(i));
                    if (value == null) {
                        required[i] = -1;
                        count *= ordinals.get(i).size();
                    } else {
                        Integer ordinal = ordinals.get(i).get(value);
                        if (ordinal == null) return null;
                        required[i] = ordinal;
                    }
                }
                references += count;
                if (references > MAX_REFERENCES) return null;
                add(buckets, components.get(n), required, ordinals, strides, 0, 0);
            }
            GRAPHS.increment();
            return new SelectorIndex(components.size(), properties, ordinals, strides, buckets);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static boolean readSelector(class_815 selector, class_2689<class_2248, class_2680> manager,
                                        Map<class_2769<?>, Comparable<?>> values, int depth) throws ReflectiveOperationException {
        if (selector == null || depth > 8) return false;
        if (selector.getClass() == class_812.class) {
            for (Object child : (Iterable<?>) AND_CHILDREN.get(selector)) {
                if (!(child instanceof class_815) || !readSelector((class_815) child, manager, values, depth + 1)) return false;
            }
            return true;
        }
        if (selector.getClass() != class_818.class) return false;
        String name = (String) SIMPLE_NAME.get(selector);
        String text = (String) SIMPLE_VALUE.get(selector);
        if (text.isEmpty() || text.indexOf('|') >= 0 || text.charAt(0) == '!') return false;
        class_2769<?> property = manager.method_11663(name);
        if (property == null) return false;
        Comparable<?> value = (Comparable<?>) property.method_11900(text).orElse(null);
        if (value == null) return false;
        Comparable<?> previous = values.putIfAbsent(property, value);
        return previous == null || previous.equals(value);
    }

    private static void add(List<class_819>[] buckets, class_819 component, int[] required,
                            List<Map<Comparable<?>, Integer>> ordinals, int[] strides, int axis, int slot) {
        if (axis == required.length) {
            if (buckets[slot] == null) buckets[slot] = new ArrayList<>();
            buckets[slot].add(component);
            return;
        }
        if (required[axis] >= 0) {
            add(buckets, component, required, ordinals, strides, axis + 1, slot + required[axis] * strides[axis]);
        } else {
            for (int value = 0; value < ordinals.get(axis).size(); value++) {
                add(buckets, component, required, ordinals, strides, axis + 1, slot + value * strides[axis]);
            }
        }
    }

    public static Map<String, Long> metrics() {
        return Map.of("graphsIndexed", GRAPHS.sum(), "stateLookups", LOOKUPS.sum(), "selectorChecksAvoided", SKIPPED_CHECKS.sum());
    }

    public List<class_819> select(class_2680 state) {
        int slot = 0;
        for (int i = 0; i < properties.size(); i++) {
            Integer ordinal = ordinals.get(i).get(state.method_11654(properties.get(i)));
            if (ordinal == null) return null;
            slot += ordinal * strides[i];
        }
        List<class_819> selected = buckets[slot] == null ? Collections.emptyList() : buckets[slot];
        LOOKUPS.increment();
        SKIPPED_CHECKS.add(originalSize - selected.size());
        return selected;
    }
}


