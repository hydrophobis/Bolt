package hydro.bolt.ast.type;

import hydro.bolt.ast.ASTVisitor;

/**
 * Represents an enum type reference (e.g., enum Color).
 */
public class EnumType extends Type {
    public String name;

    public EnumType(String name, int line, int column) {
        super(line, column);
        this.name = name;
    }

    public EnumType(String name) {
        super();
        this.name = name;
    }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitEnumType(this);
    }
}
