package hydro.bolt.sema;

import java.util.Map;
import java.util.Set;

public final class Types {
    private Types() {
    }

    public static final String UNKNOWN = null;
    private static final Set<String> PRIMITIVES = Set.of(
        "void", "bool", "char", "short", "int", "long", "float", "double",
        "signed", "unsigned", "size_t", "ssize_t", "ptrdiff_t",
        "int8_t", "int16_t", "int32_t", "int64_t",
        "uint8_t", "uint16_t", "uint32_t", "uint64_t",
        "string"
    );
    private static final Set<String> INTEGERS = Set.of(
        "bool", "char", "short", "int", "long", "signed", "unsigned",
        "size_t", "ssize_t", "ptrdiff_t",
        "int8_t", "int16_t", "int32_t", "int64_t",
        "uint8_t", "uint16_t", "uint32_t", "uint64_t"
    );
    private static final Set<String> FLOATS = Set.of("float", "double");
    private static final Map<String, Integer> RANK = Map.ofEntries(
        Map.entry("bool", 1),
        Map.entry("char", 2),
        Map.entry("int8_t", 2),
        Map.entry("uint8_t", 2),
        Map.entry("short", 3),
        Map.entry("int16_t", 3),
        Map.entry("uint16_t", 3),
        Map.entry("int", 4),
        Map.entry("int32_t", 4),
        Map.entry("uint32_t", 4),
        Map.entry("signed", 4),
        Map.entry("unsigned", 4),
        Map.entry("long", 5),
        Map.entry("int64_t", 5),
        Map.entry("uint64_t", 5),
        Map.entry("size_t", 5),
        Map.entry("float", 6),
        Map.entry("double", 7)
    );
    public static boolean isUnknown(String type) {
        return type == null || type.isEmpty() || type.equals("unknown");
    }

    public static boolean isPointer(String type) {
        return type != null && type.trim().endsWith("*");
    }

    public static String deref(String type) {
        if (!isPointer(type)) return type;
        String t = type.trim();
        return t.substring(0, t.length() - 1).trim();
    }

    public static String pointerTo(String type) {
        return type == null ? null : type.trim() + "*";
    }

    public static String baseName(String type) {
        if (type == null) return null;
        String t = type.trim();
        while (t.endsWith("*")) {
            t = t.substring(0, t.length() - 1).trim();
        }
        int angle = t.indexOf('<');
        if (angle != -1) t = t.substring(0, angle).trim();
        return t;
    }

    private static final Set<String> SPECIFIER_WORDS = Set.of(
        "unsigned", "signed", "long", "short", "int", "char",
        "float", "double", "void", "const", "volatile", "register"
    );

    public static boolean isSpecifierCombination(String type) {
        if (type == null) return false;
        String base = baseName(type);
        if (base == null || base.isEmpty()) return false;
        String[] words = base.trim().split("\\s+");
        if (words.length < 2) return false;
        for (String word : words) {
            if (!SPECIFIER_WORDS.contains(word)) return false;
        }
        return true;
    }

    public static boolean isBuiltin(String type) {
        if (type == null) return false;
        return PRIMITIVES.contains(baseName(type)) || isSpecifierCombination(type);
    }

    public static boolean isPrimitive(String type) {
        if (type == null || isPointer(type)) return false;
        return PRIMITIVES.contains(baseName(type)) || isSpecifierCombination(type);
    }

    public static boolean isString(String type) {
        return "string".equals(type == null ? null : type.trim());
    }

    public static boolean isVoid(String type) {
        return "void".equals(type == null ? null : type.trim());
    }

    public static boolean isInteger(String type) {
        if (type == null || isPointer(type)) return false;
        String t = type.trim();
        if (INTEGERS.contains(t)) return true;
        return isSpecifierCombination(t) && !t.contains("float") && !t.contains("double")
            && !t.contains("void");
    }

    public static boolean isFloating(String type) {
        if (type == null || isPointer(type)) return false;
        String t = type.trim();
        if (FLOATS.contains(t)) return true;
        return isSpecifierCombination(t) && (t.contains("float") || t.contains("double"));
    }

    public static boolean isNumeric(String type) {
        return isInteger(type) || isFloating(type);
    }

    public static boolean isCharBuffer(String type) {
        return isString(type) || "char*".equals(type == null ? null : type.replace(" ", ""));
    }

    public static boolean assignable(String from, String to) {
        if (isUnknown(from) || isUnknown(to)) return true;
        String f = from.trim();
        String t = to.trim();
        if (f.equals(t)) return true;
        if (isCharBuffer(f) && isCharBuffer(t)) return true;
        if (isNumeric(f) && isNumeric(t)) return true;
        if (isPointer(t) && isInteger(f)) return true;
        if (isPointer(f) && isInteger(t)) return true;
        if (isPointer(f) && isPointer(t)) {
            String fb = baseName(f);
            String tb = baseName(t);
            if (fb.equals("void") || tb.equals("void")) return true;
            if (fb.equals("char") || tb.equals("char")) return true;
            return fb.equals(tb);
        }

        if (isPointer(f) != isPointer(t)) return false;

        return baseName(f).equals(baseName(t));
    }

    public static boolean narrows(String from, String to) {
        if (!isNumeric(from) || !isNumeric(to)) return false;
        Integer f = RANK.get(from.trim());
        Integer t = RANK.get(to.trim());
        if (f == null || t == null) return false;
        if (isFloating(from) && isInteger(to)) return true;
        return t < f;
    }

    public static String arithmeticResult(String left, String right) {
        if (isUnknown(left)) return right;
        if (isUnknown(right)) return left;
        if (isPointer(left)) return left;
        if (isPointer(right)) return right;
        if (!isNumeric(left) || !isNumeric(right)) return left;
        Integer l = RANK.get(left.trim());
        Integer r = RANK.get(right.trim());
        if (l == null) return right;
        if (r == null) return left;
        return l >= r ? left : right;
    }

    public static boolean isScalar(String type) {
        return isUnknown(type) || isNumeric(type) || isPointer(type) || isString(type);
    }
}
