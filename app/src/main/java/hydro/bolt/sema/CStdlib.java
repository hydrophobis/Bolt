package hydro.bolt.sema;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class CStdlib {
    private CStdlib() {
    }

    private static final Map<String, Set<String>> BY_HEADER = Map.ofEntries(
        Map.entry("stdio", Set.of(
            "printf", "fprintf", "sprintf", "snprintf", "vprintf", "vfprintf", "vsnprintf",
            "scanf", "fscanf", "sscanf",
            "fopen", "freopen", "fclose", "fflush", "fread", "fwrite",
            "fseek", "ftell", "rewind", "feof", "ferror", "clearerr",
            "fgetc", "fgets", "fputc", "fputs", "getc", "getchar", "putc", "putchar",
            "puts", "ungetc", "perror", "remove", "rename", "tmpfile", "setvbuf",
            "stdin", "stdout", "stderr", "FILE", "EOF", "NULL", "SEEK_SET", "SEEK_CUR", "SEEK_END"
        )),
        Map.entry("stdlib", Set.of(
            "malloc", "calloc", "realloc", "free",
            "abort", "exit", "atexit", "system", "getenv",
            "atoi", "atol", "atoll", "atof", "strtol", "strtoul", "strtod", "strtof",
            "rand", "srand", "qsort", "bsearch", "abs", "labs", "llabs", "div", "ldiv",
            "NULL", "EXIT_SUCCESS", "EXIT_FAILURE", "RAND_MAX", "size_t"
        )),
        Map.entry("string", Set.of(
            "strlen", "strcpy", "strncpy", "strcat", "strncat",
            "strcmp", "strncmp", "strcoll", "strchr", "strrchr", "strstr",
            "strspn", "strcspn", "strpbrk", "strtok", "strdup", "strerror",
            "memcpy", "memmove", "memset", "memcmp", "memchr",
            "NULL", "size_t"
        )),
        Map.entry("math", Set.of(
            "sin", "cos", "tan", "asin", "acos", "atan", "atan2",
            "sinh", "cosh", "tanh", "exp", "log", "log10", "log2",
            "pow", "sqrt", "cbrt", "hypot", "ceil", "floor", "round", "trunc",
            "fabs", "fmod", "fmin", "fmax", "modf", "frexp", "ldexp",
            "isnan", "isinf", "isfinite", "M_PI", "M_E", "NAN", "INFINITY", "HUGE_VAL"
        )),
        Map.entry("time", Set.of(
            "time", "clock", "difftime", "mktime", "localtime", "gmtime",
            "asctime", "ctime", "strftime", "nanosleep",
            "time_t", "clock_t", "CLOCKS_PER_SEC", "NULL"
        )),
        Map.entry("ctype", Set.of(
            "isalpha", "isdigit", "isalnum", "isspace", "isupper", "islower",
            "ispunct", "isprint", "isgraph", "iscntrl", "isxdigit",
            "toupper", "tolower"
        )),
        Map.entry("assert", Set.of("assert")),
        Map.entry("errno", Set.of("errno", "EDOM", "ERANGE", "EINVAL", "ENOMEM")),
        Map.entry("stdbool", Set.of("bool", "true", "false")),
        Map.entry("stdint", Set.of(
            "int8_t", "int16_t", "int32_t", "int64_t",
            "uint8_t", "uint16_t", "uint32_t", "uint64_t",
            "intptr_t", "uintptr_t",
            "INT8_MAX", "INT16_MAX", "INT32_MAX", "INT64_MAX",
            "UINT8_MAX", "UINT16_MAX", "UINT32_MAX", "UINT64_MAX"
        )),
        Map.entry("stddef", Set.of("size_t", "ptrdiff_t", "NULL", "offsetof")),
        Map.entry("stdarg", Set.of("va_list", "va_start", "va_arg", "va_end", "va_copy")),
        Map.entry("limits", Set.of(
            "CHAR_BIT", "CHAR_MAX", "CHAR_MIN", "SHRT_MAX", "SHRT_MIN",
            "INT_MAX", "INT_MIN", "LONG_MAX", "LONG_MIN", "UINT_MAX", "ULONG_MAX"
        )),
        Map.entry("float", Set.of("FLT_MAX", "FLT_MIN", "FLT_EPSILON", "DBL_MAX", "DBL_MIN", "DBL_EPSILON"))
    );
    public static final Set<String> ALWAYS_AVAILABLE = Set.of(
        "NULL", "size_t", "true", "false", "bool",
        "malloc", "calloc", "realloc", "free",
        "memcpy", "memmove", "memset", "strlen"
    );

    public static boolean isKnownHeader(String importPath) {
        return headerKey(importPath) != null;
    }

    public static Set<String> symbolsFor(String importPath) {
        String key = headerKey(importPath);
        return key != null ? BY_HEADER.get(key) : Set.of();
    }

    private static String headerKey(String importPath) {
        if (importPath == null) return null;
        String path = importPath.trim();
        if (path.startsWith("std.")) {
            String rest = path.substring(4);
            if (rest.equals("io")) rest = "stdio";
            if (BY_HEADER.containsKey(rest)) return rest;
            return null;
        }
        if (path.endsWith(".h")) {
            path = path.substring(0, path.length() - 2);
        }
        return BY_HEADER.containsKey(path) ? path : null;
    }

    public static Set<String> symbolsForAll(Iterable<String> importPaths) {
        Set<String> out = new HashSet<>(ALWAYS_AVAILABLE);
        for (String path : importPaths) {
            out.addAll(symbolsFor(path));
        }
        return out;
    }
}
