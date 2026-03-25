package hydro.bolt.ast.statement;

import hydro.bolt.ast.ASTNode;
import hydro.bolt.ast.ASTVisitor;

/**
 * Represents a while statement.
 */
public class WhileStatement extends ASTNode {
    public ASTNode condition;
    public ASTNode body;

    public WhileStatement(ASTNode condition, ASTNode body, int line, int column) {
        super(line, column);
        this.condition = condition;
        this.body = body;
    }

    public WhileStatement(ASTNode condition, ASTNode body) {
        super();
        this.condition = condition;
        this.body = body;
    }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitWhileStatement(this);
    }
}
