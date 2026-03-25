package hydro.bolt.ast.statement;

import hydro.bolt.ast.ASTNode;
import hydro.bolt.ast.ASTVisitor;

/**
 * Represents an expression statement (expression followed by semicolon).
 */
public class ExpressionStatement extends ASTNode {
    public ASTNode expression;

    public ExpressionStatement(ASTNode expression, int line, int column) {
        super(line, column);
        this.expression = expression;
    }

    public ExpressionStatement(ASTNode expression) {
        super();
        this.expression = expression;
    }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitExpressionStatement(this);
    }
}
