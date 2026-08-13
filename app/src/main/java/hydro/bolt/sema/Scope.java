package hydro.bolt.sema;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Scope {
    public static class Entry {
        public final String name;
        public final String type;
        public final int line;
        public final int column;
        public boolean used;
        public final boolean reportUnused;
        Entry(String name, String type, int line, int column, boolean reportUnused) {
            this.name = name;
            this.type = type;
            this.line = line;
            this.column = column;
            this.reportUnused = reportUnused;
        }
    }

    private final Map<String, Entry> entries = new HashMap<>();
    private final Scope parent;

    public Scope() {
        this(null);
    }

    public Scope(Scope parent) {
        this.parent = parent;
    }

    public Scope parent() {
        return parent;
    }

    public Entry declare(String name, String type, int line, int column, boolean reportUnused) {
        Entry entry = new Entry(name, type, line, column, reportUnused);
        entries.put(name, entry);
        return entry;
    }

    public Entry lookupLocal(String name) {
        return entries.get(name);
    }

    public Entry lookup(String name) {
        Entry e = entries.get(name);
        if (e != null) return e;
        return parent != null ? parent.lookup(name) : null;
    }

    public Entry lookupEnclosing(String name) {
        return parent != null ? parent.lookup(name) : null;
    }

    public void markUsed(String name) {
        Entry e = lookup(name);
        if (e != null) e.used = true;
    }

    public List<Entry> entries() {
        return new ArrayList<>(entries.values());
    }
}
