package hydro.bolt.ast.misc;

import hydro.bolt.ast.ASTNode;
import hydro.bolt.ast.ASTVisitor;

/**
 * Represents an initializer list for array or struct initialization (e.g., {1, 2, 3}).
 */
public class InitializerList extends ASTNode {
    public java.util.List<ASTNode> elements;

    public InitializerList(java.util.List<ASTNode> elements, int line, int column) {
        super(line, column);
        this.elements = elements;
    }

    public InitializerList(java.util.List<ASTNode> elements) {
        super();
        this.elements = elements;
    }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitInitializerList(this);
    }
}
