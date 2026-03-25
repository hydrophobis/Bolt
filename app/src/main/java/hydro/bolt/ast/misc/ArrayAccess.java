package hydro.bolt.ast.misc;

import hydro.bolt.ast.ASTNode;
import hydro.bolt.ast.ASTVisitor;

/**
 * Represents an array access expression (e.g., arr[i]).
 */
public class ArrayAccess extends ASTNode {
    public ASTNode array;
    public ASTNode index;

    public ArrayAccess(ASTNode array, ASTNode index, int line, int column) {
        super(line, column);
        this.array = array;
        this.index = index;
    }

    public ArrayAccess(ASTNode array, ASTNode index) {
        super();
        this.array = array;
        this.index = index;
    }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitArrayAccess(this);
    }
}
