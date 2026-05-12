package hydro.bolt;

import java.io.*;
import java.util.*;

public class Config {
    private final Map<String, String> settings = new HashMap<>();

    private static final Map<String, String> DEFAULT_SETTINGS = Map.ofEntries(
        Map.entry("mangle", "true"),
        Map.entry("no-heap", "false"),
        Map.entry("traceability", "false"),
        Map.entry("indent-size", "4"),
        Map.entry("indent-style", "space"),
        Map.entry("brace-style", "k&r"),
        Map.entry("line-width", "80"),
        Map.entry("string-buffer-size", "256"),
        Map.entry("static-string-pool", "false"),
        Map.entry("c-standard", "c99"),
        Map.entry("allow-recursion", "true"),
        Map.entry("forbidden-headers", ""),
        Map.entry("strict-typing", "true"),
        Map.entry("lambdas", "false"),
        Map.entry("max-errors", "10"),
        Map.entry("mangle-prefix", "__bolt"),
        Map.entry("no-std-includes", "false"),
        Map.entry("no-string-helpers", "false"),
        Map.entry("verbose", "false")
    );

    public Config() {
        settings.putAll(DEFAULT_SETTINGS);
        load();
    }

    private void load() {
        File configFile = new File("bolt.cfg");
        if (!configFile.exists()) {
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split("=", 2);
                if (parts.length == 2) {
                    settings.put(parts[0].trim(), parts[1].trim());
                }
            }
        } catch (IOException e) {
            System.err.println("Warning: Could not read bolt.cfg: " + e.getMessage());
        }
    }

    public void put(String key, String value) {
        settings.put(key, value);
    }

    public boolean getBoolean(String key) {
        return Boolean.parseBoolean(settings.get(key));
    }

    public String get(String key) {
        return settings.get(key);
    }
}
