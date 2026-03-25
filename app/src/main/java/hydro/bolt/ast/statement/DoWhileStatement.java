package hydro.bolt.ast.statement;

import hydro.bolt.ast.ASTNode;
import hydro.bolt.ast.ASTVisitor;

/**
 * Represents a do-while statement.
 */
public class DoWhileStatement extends ASTNode {
    public ASTNode body;
    public ASTNode condition;

    public DoWhileStatement(ASTNode body, ASTNode condition, int line, int column) {
        super(line, column);
        this.body = body;
        this.condition = condition;
    }

    public DoWhileStatement(ASTNode body, ASTNode condition) {
        super();
        this.body = body;
        this.condition = condition;
    }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitDoWhileStatement(this);
    }
}
