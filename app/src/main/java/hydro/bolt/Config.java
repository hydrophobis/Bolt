package hydro.bolt;

import java.io.*;
import java.util.*;

public class Config {
    private final Map<String, String> settings = new HashMap<>();

    public Config() {
        // defaults
        settings.put("mangle", "true");
        settings.put("no-heap", "false");
        settings.put("traceability", "false");

        // code style & formatting
        settings.put("indent-size", "4");
        settings.put("indent-style", "space");
        settings.put("brace-style", "k&r");
        settings.put("line-width", "80");

        // memory & resource management
        settings.put("string-buffer-size", "65536");
        settings.put("static-string-pool", "false");

        // safety & compliance
        settings.put("c-standard", "c99");
        settings.put("allow-recursion", "true");
        settings.put("forbidden-headers", "");
        settings.put("strict-typing", "false");

        // transpiler behavior & dx
        settings.put("max-errors", "10");
        settings.put("mangle-prefix", "__bolt");
        settings.put("no-std-includes", "false");
        settings.put("no-string-helpers", "false");
        settings.put("verbose", "false");

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
