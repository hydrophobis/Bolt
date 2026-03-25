package hydro.bolt.ast.type;

import hydro.bolt.ast.ASTNode;
import hydro.bolt.ast.ASTVisitor;

/**
 * Represents a pointer type (e.g., int*, char*, etc.).
 */
public class PointerType extends Type {
    public ASTNode baseType;

    public PointerType(ASTNode baseType, int line, int column) {
        super(line, column);
        this.baseType = baseType;
    }

    public PointerType(ASTNode baseType) {
        super();
        this.baseType = baseType;
    }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitPointerType(this);
    }

    @Override
    public String toString() {
        return baseType.toString() + "*";
    }
}
