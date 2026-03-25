package hydro.bolt.ast.type;

import hydro.bolt.ast.ASTVisitor;

/**
 * Represents a primitive type (int, float, char, void, etc.).
 */
public class PrimitiveType extends Type {
    public String name;

    public PrimitiveType(String name, int line, int column) {
        super(line, column);
        this.name = name;
    }

    public PrimitiveType(String name) {
        super();
        this.name = name;
    }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitPrimitiveType(this);
    }
}
