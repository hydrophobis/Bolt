package hydro.bolt.ast.type;

import hydro.bolt.ast.ASTVisitor;

/**
 * Represents a union type reference (e.g., union Data).
 */
public class UnionType extends Type {
    public String name;

    public UnionType(String name, int line, int column) {
        super(line, column);
        this.name = name;
    }

    public UnionType(String name) {
        super();
        this.name = name;
    }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitUnionType(this);
    }
}
