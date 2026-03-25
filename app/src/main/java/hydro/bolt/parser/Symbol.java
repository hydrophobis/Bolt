package hydro.bolt.parser;

import java.util.*;
import hydro.bolt.ast.ASTNode;
import hydro.bolt.ast.type.Type;

public class Symbol {
    public enum Kind {
        VARIABLE, FUNCTION, CLASS, STRUCT
    }

    public final String name;
    public final Kind kind;
    public final String typeName;
    public final List<String> parameterTypes;
    public final Map<String, Symbol> members;
    public boolean mangle = false;
    public boolean isManual = false;
    public boolean isPointer = false;

    public Symbol(String name, Kind kind, String typeName) {
        this.name = name;
        this.kind = kind;
        this.typeName = typeName;
        this.parameterTypes = new ArrayList<>();
        this.members = new HashMap<>();
    }
}
