package hydro.bolt.ast.statement;

import hydro.bolt.ast.ASTNode;
import hydro.bolt.ast.ASTVisitor;

/**
 * Represents a return statement.
 */
public class ReturnStatement extends ASTNode {
    public ASTNode value;

    public ReturnStatement(ASTNode value, int line, int column) {
        super(line, column);
        this.value = value;
    }

    public ReturnStatement(ASTNode value) {
        super();
        this.value = value;
    }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitReturnStatement(this);
    }
}
