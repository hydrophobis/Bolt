package hydro.bolt.ast.type;

import hydro.bolt.ast.ASTNode;
import hydro.bolt.ast.ASTVisitor;

/**
 * Represents an array type (e.g., int[], int[10], etc.).
 */
public class ArrayType extends Type {
    public ASTNode elementType;
    public ASTNode size;

    public ArrayType(ASTNode elementType, ASTNode size, int line, int column) {
        super(line, column);
        this.elementType = elementType;
        this.size = size;
    }

    public ArrayType(ASTNode elementType, ASTNode size) {
        super();
        this.elementType = elementType;
        this.size = size;
    }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitArrayType(this);
    }
}
