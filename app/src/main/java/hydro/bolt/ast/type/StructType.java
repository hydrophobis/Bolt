package hydro.bolt.ast.type;

import hydro.bolt.ast.ASTVisitor;

/**
 * Represents a struct type reference (e.g., struct Point).
 */
public class StructType extends Type {
    public String name;

    public StructType(String name, int line, int column) {
        super(line, column);
        this.name = name;
    }

    public StructType(String name) {
        super();
        this.name = name;
    }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitStructType(this);
    }
}
