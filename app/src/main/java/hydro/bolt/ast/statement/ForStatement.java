package hydro.bolt.ast.statement;

import hydro.bolt.ast.ASTNode;
import hydro.bolt.ast.ASTVisitor;

/**
 * Represents a for statement.
 */
public class ForStatement extends ASTNode {
    public ASTNode initializer;
    public ASTNode condition;
    public ASTNode update;
    public ASTNode body;

    public ForStatement(ASTNode initializer, ASTNode condition, ASTNode update, ASTNode body, int line, int column) {
        super(line, column);
        this.initializer = initializer;
        this.condition = condition;
        this.update = update;
        this.body = body;
    }

    public ForStatement(ASTNode initializer, ASTNode condition, ASTNode update, ASTNode body) {
        super();
        this.initializer = initializer;
        this.condition = condition;
        this.update = update;
        this.body = body;
    }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitForStatement(this);
    }
}
