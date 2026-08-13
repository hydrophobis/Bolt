package hydro.bolt;

import hydro.bolt.parser.ErrorCode;
import hydro.bolt.parser.ErrorReporter;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

public class Config {
    private enum Kind { BOOL, INT, ENUM, STRING }

    private record Spec(Kind kind, String defaultValue, Set<String> allowed) {
        static Spec bool(String def) {
            return new Spec(Kind.BOOL, def, Set.of("true", "false"));
        }

        static Spec integer(String def) {
            return new Spec(Kind.INT, def, null);
        }

        static Spec choice(String def, String... options) {
            return new Spec(Kind.ENUM, def, Set.of(options));
        }

        static Spec string(String def) {
            return new Spec(Kind.STRING, def, null);
        }
    }

    private static final Map<String, Spec> SPECS = Map.ofEntries(
        Map.entry("mangle", Spec.bool("true")),
        Map.entry("no-heap", Spec.bool("false")),
        Map.entry("traceability", Spec.bool("false")),
        Map.entry("indent-size", Spec.integer("4")),
        Map.entry("indent-style", Spec.choice("space", "space", "tab")),
        Map.entry("brace-style", Spec.choice("k&r", "k&r", "allman")),
        Map.entry("line-width", Spec.integer("80")),
        Map.entry("string-buffer-size", Spec.integer("256")),
        Map.entry("static-string-pool", Spec.bool("false")),
        Map.entry("c-standard", Spec.choice("c99", "c89", "c90", "c99", "c11", "c17", "c23")),
        Map.entry("allow-recursion", Spec.bool("true")),
        Map.entry("forbidden-headers", Spec.string("")),
        Map.entry("strict-typing", Spec.bool("true")),
        Map.entry("operator-overloading", Spec.bool("false")),
        Map.entry("lambdas", Spec.bool("false")),
        Map.entry("max-errors", Spec.integer("10")),
        Map.entry("mangle-prefix", Spec.string("__bolt")),
        Map.entry("no-std-includes", Spec.bool("false")),
        Map.entry("no-string-helpers", Spec.bool("false")),
        Map.entry("verbose", Spec.bool("false"))
    );

    public static final String CONFIG_FILE = "bolt.cfg";

    private final Map<String, String> settings = new HashMap<>();

    private final ErrorReporter diagnostics = new ErrorReporter();
    public Config() {
        this(new File(CONFIG_FILE));
    }

    public Config(File configFile) {
        for (Map.Entry<String, Spec> e : SPECS.entrySet()) {
            settings.put(e.getKey(), e.getValue().defaultValue());
        }
        load(configFile);
    }

    public static Set<String> knownKeys() {
        return Collections.unmodifiableSet(SPECS.keySet());
    }

    public ErrorReporter diagnostics() {
        return diagnostics;
    }

    private void load(File configFile) {
        if (configFile == null || !configFile.exists()) {
            return;
        }
        String source;
        try {
            source = Files.readString(configFile.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            diagnostics.warn(ErrorCode.MALFORMED_CONFIG_LINE,
                "could not read " + configFile.getName() + ": " + e.getMessage());
            return;
        }
        diagnostics.setSource(configFile.getPath(), source);
        String[] lines = source.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String raw = lines[i];
            int lineNo = i + 1;
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int eq = line.indexOf('=');
            if (eq < 0) {
                diagnostics.warn(ErrorCode.MALFORMED_CONFIG_LINE, lineNo,
                    indexOfTrimmed(raw) + 1, line.length(),
                    "expected 'key=value', ignoring this line");
                continue;
            }
            String key = line.substring(0, eq).trim();
            String value = line.substring(eq + 1).trim();
            int keyColumn = raw.indexOf(key) + 1;
            if (key.isEmpty()) {
                diagnostics.warn(ErrorCode.MALFORMED_CONFIG_LINE, lineNo,
                    indexOfTrimmed(raw) + 1, line.length(), "missing setting name, ignoring this line");
                continue;
            }
            if (!SPECS.containsKey(key)) {
                diagnostics.warn(ErrorCode.UNKNOWN_CONFIG_KEY, lineNo, keyColumn, key.length(),
                    "unknown setting '" + key + "'" + suggestionFor(key));
                continue;
            }
            int valueColumn = raw.indexOf(value, eq) + 1;
            if (validate(key, value, lineNo, Math.max(1, valueColumn))) {
                settings.put(key, value);
            }
        }
    }

    private static int indexOfTrimmed(String raw) {
        int i = 0;
        while (i < raw.length() && Character.isWhitespace(raw.charAt(i))) i++;
        return i;
    }

    private boolean validate(String key, String value, int line, int column) {
        Spec spec = SPECS.get(key);
        switch (spec.kind()) {
            case BOOL -> {
                if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
                    diagnostics.error(ErrorCode.INVALID_CONFIG_VALUE, line, column, value.length(),
                        "'" + key + "' expects true or false, got '" + value + "'");
                    return false;
                }
            }
            case INT -> {
                try {
                    int n = Integer.parseInt(value);
                    if (n < 0) {
                        diagnostics.error(ErrorCode.INVALID_CONFIG_VALUE, line, column, value.length(),
                            "'" + key + "' expects a non-negative integer, got '" + value + "'");
                        return false;
                    }
                } catch (NumberFormatException e) {
                    diagnostics.error(ErrorCode.INVALID_CONFIG_VALUE, line, column, value.length(),
                        "'" + key + "' expects an integer, got '" + value + "'");
                    return false;
                }
            }
            case ENUM -> {
                if (!spec.allowed().contains(value.toLowerCase())) {
                    ErrorCode code = key.equals("c-standard")
                        ? ErrorCode.UNSUPPORTED_C_STANDARD
                        : ErrorCode.INVALID_CONFIG_VALUE;
                    List<String> sorted = new ArrayList<>(spec.allowed());
                    Collections.sort(sorted);
                    diagnostics.error(code, line, column, value.length(),
                        "'" + key + "' expects one of " + String.join(", ", sorted)
                            + ", got '" + value + "'");
                    return false;
                }
            }
            case STRING -> {
            }
        }
        return true;
    }

    private String suggestionFor(String key) {
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (String candidate : SPECS.keySet()) {
            int d = editDistance(key.toLowerCase(), candidate);
            if (d < bestDistance) {
                bestDistance = d;
                best = candidate;
            }
        }
        if (best != null && bestDistance <= Math.max(2, best.length() / 3)) {
            return "; did you mean '" + best + "'?";
        }
        return "";
    }

    private static int editDistance(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] swap = prev;
            prev = curr;
            curr = swap;
        }
        return prev[b.length()];
    }

    public void put(String key, String value) {
        if (!SPECS.containsKey(key)) {
            diagnostics.warn(ErrorCode.UNKNOWN_CONFIG_KEY,
                "unknown setting '" + key + "'" + suggestionFor(key));
            return;
        }
        if (validate(key, value, 0, 0)) {
            settings.put(key, value);
        }
    }

    public void putRaw(String key, String value) {
        settings.put(key, value);
    }

    public boolean getBoolean(String key) {
        return Boolean.parseBoolean(settings.get(key));
    }

    public String get(String key) {
        return settings.get(key);
    }

    public int getInt(String key) {
        String value = settings.get(key);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException | NullPointerException e) {
            Spec spec = SPECS.get(key);
            return spec != null ? Integer.parseInt(spec.defaultValue()) : 0;
        }
    }
}
