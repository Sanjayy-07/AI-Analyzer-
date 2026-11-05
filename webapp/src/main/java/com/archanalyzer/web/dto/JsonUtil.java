package com.archanalyzer.web.dto;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.*;

/**
 * Minimal, dependency-free JSON serialization for the raw Servlet REST
 * endpoints (PART 29). Deliberately avoids requiring Jackson/Gson on the
 * classpath for these specific endpoints so they have no extra runtime
 * dependency beyond the JDK.
 *
 * Phase S.3.1 Hardened (NOVELTY-2 REST SERIALIZATION REMEDIATION):
 * - Serializes structured DTOs, domain models, and encapsulated JavaBeans
 *   with private fields and getters into structured JSON objects.
 * - Inspects entire class hierarchy up to Object for com.archanalyzer types.
 * - Protects against circular reference graphs via ThreadLocal cycle detection.
 * - Supports objects, maps, collections, arrays, primitives, enums, records, and null.
 */
public final class JsonUtil {

    private static final ThreadLocal<Set<Object>> VISITED = ThreadLocal.withInitial(
            () -> Collections.newSetFromMap(new IdentityHashMap<>())
    );

    private JsonUtil() {}

    public static String toJson(Object value) {
        StringBuilder sb = new StringBuilder();
        try {
            write(value, sb);
        } finally {
            VISITED.get().clear();
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void write(Object value, StringBuilder sb) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String s) {
            sb.append('"').append(escape(s)).append('"');
        } else if (value instanceof Number || value instanceof Boolean) {
            sb.append(value);
        } else if (value instanceof Character c) {
            sb.append('"').append(escape(String.valueOf(c))).append('"');
        } else if (value.getClass().isEnum()) {
            sb.append('"').append(escape(((Enum<?>) value).name())).append('"');
        } else if (value instanceof Map<?, ?> map) {
            Set<Object> set = VISITED.get();
            if (!set.add(value)) {
                sb.append("null");
                return;
            }
            try {
                sb.append('{');
                boolean first = true;
                for (var entry : map.entrySet()) {
                    if (!first) sb.append(',');
                    first = false;
                    sb.append('"').append(escape(String.valueOf(entry.getKey()))).append("\":");
                    write(entry.getValue(), sb);
                }
                sb.append('}');
            } finally {
                set.remove(value);
            }
        } else if (value instanceof Collection<?> col) {
            Set<Object> set = VISITED.get();
            if (!set.add(value)) {
                sb.append("null");
                return;
            }
            try {
                sb.append('[');
                boolean first = true;
                for (Object item : col) {
                    if (!first) sb.append(',');
                    first = false;
                    write(item, sb);
                }
                sb.append(']');
            } finally {
                set.remove(value);
            }
        } else if (value.getClass().isArray()) {
            sb.append('[');
            int len = java.lang.reflect.Array.getLength(value);
            for (int i = 0; i < len; i++) {
                if (i > 0) sb.append(',');
                write(java.lang.reflect.Array.get(value, i), sb);
            }
            sb.append(']');
        } else if (isPojo(value)) {
            Set<Object> set = VISITED.get();
            if (!set.add(value)) {
                sb.append("null");
                return;
            }
            try {
                serializePojo(value, sb);
            } finally {
                set.remove(value);
            }
        } else {
            sb.append('"').append(escape(value.toString())).append('"');
        }
    }

    private static boolean isPojo(Object value) {
        if (value == null) return false;
        Class<?> cls = value.getClass();
        if (cls.isPrimitive() || cls.isEnum() || Number.class.isAssignableFrom(cls)
                || Boolean.class.isAssignableFrom(cls) || CharSequence.class.isAssignableFrom(cls)) {
            return false;
        }
        String pkg = cls.getPackageName();
        return pkg.startsWith("com.archanalyzer");
    }

    private static void serializePojo(Object value, StringBuilder sb) {
        sb.append('{');
        boolean first = true;
        Class<?> cls = value.getClass();

        if (cls.isRecord()) {
            for (RecordComponent rc : cls.getRecordComponents()) {
                if (!first) sb.append(',');
                first = false;
                sb.append('"').append(escape(rc.getName())).append("\":");
                try {
                    write(rc.getAccessor().invoke(value), sb);
                } catch (Exception e) {
                    sb.append("null");
                }
            }
        } else {
            List<Field> fields = collectHierarchyFields(cls);
            for (Field field : fields) {
                if (!first) sb.append(',');
                first = false;
                sb.append('"').append(escape(field.getName())).append("\":");
                try {
                    field.setAccessible(true);
                    write(field.get(value), sb);
                } catch (Exception e) {
                    sb.append("null");
                }
            }
        }
        sb.append('}');
    }

    private static List<Field> collectHierarchyFields(Class<?> startClass) {
        List<Field> fields = new ArrayList<>();
        Set<String> seenNames = new HashSet<>();
        Class<?> current = startClass;
        while (current != null && current != Object.class && current.getPackageName().startsWith("com.archanalyzer")) {
            for (Field f : current.getDeclaredFields()) {
                int mod = f.getModifiers();
                if (Modifier.isStatic(mod) || Modifier.isTransient(mod) || f.isSynthetic()) {
                    continue;
                }
                if (f.getName().startsWith("this$")) {
                    continue;
                }
                if (seenNames.add(f.getName())) {
                    fields.add(f);
                }
            }
            current = current.getSuperclass();
        }
        return fields;
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
