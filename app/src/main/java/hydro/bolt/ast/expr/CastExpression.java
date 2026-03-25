package hydro.bolt.ast.expr;

import hydro.bolt.ast.ASTNode;
import hydro.bolt.ast.ASTVisitor;

/**
 * Represents a cast expression (e.g., (int)x, (float*)ptr).
 */
public class CastExpression extends ASTNode {
    public ASTNode type;
    public ASTNode expression;

    public CastExpression(ASTNode type, ASTNode expression, int line, int column) {
        super(line, column);
        this.type = type;
        this.expression = expression;
    }

    public CastExpression(ASTNode type, ASTNode expression) {
        super();
        this.type = type;
        this.expression = expression;
    }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitCastExpression(this);
    }
}
