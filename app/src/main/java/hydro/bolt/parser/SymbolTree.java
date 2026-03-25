package hydro.bolt.parser;

import java.util.*;

public class SymbolTree {
    private final Map<String, Symbol> symbols = new HashMap<>();
    private final SymbolTree parent;

    public SymbolTree() {
        this.parent = null;
    }

    public SymbolTree(SymbolTree parent) {
        this.parent = parent;
    }

    public void put(String name, Symbol symbol) {
        symbols.put(name, symbol);
    }

    public Symbol get(String name) {
        Symbol s = symbols.get(name);
        if (s == null && parent != null) {
            return parent.get(name);
        }
        return s;
    }

    public boolean contains(String name) {
        return symbols.containsKey(name) || (parent != null && parent.contains(name));
    }

    public void put(String name, String type) {
        symbols.put(name, new Symbol(name, Symbol.Kind.VARIABLE, type));
    }

    public Set<String> keySet() {
        return symbols.keySet();
    }
}
