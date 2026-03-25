package hydro.bolt.ast.misc;

import hydro.bolt.ast.ASTNode;
import hydro.bolt.ast.ASTVisitor;

/**
 * Represents a function parameter.
 */
public class Parameter extends ASTNode {
    public ASTNode type;
    public String name;

    public Parameter(ASTNode type, String name, int line, int column) {
        super(line, column);
        this.type = type;
        this.name = name;
    }

    public Parameter(ASTNode type, String name) {
        super();
        this.type = type;
        this.name = name;
    }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitParameter(this);
    }
}
