package hydro.bolt.ast.misc;

import hydro.bolt.ast.ASTNode;
import hydro.bolt.ast.ASTVisitor;

/**
 * Represents an identifier (variable name, function name, etc.).
 */
public class Identifier extends ASTNode {
    public String name;
    public java.util.List<ASTNode> genericArguments = new java.util.ArrayList<>();

    public Identifier(String name, int line, int column) {
        super(line, column);
        this.name = name;
    }

    public Identifier(String name) {
        super();
        this.name = name;
    }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitIdentifier(this);
    }

    @Override
    public String toString() {
        if (genericArguments.isEmpty()) return name;
        StringBuilder sb = new StringBuilder(name).append("<");
        for (int i = 0; i < genericArguments.size(); i++) {
            sb.append(genericArguments.get(i).toString());
            if (i < genericArguments.size() - 1) sb.append(", ");
        }
        sb.append(">");
        return sb.toString();
    }
}
