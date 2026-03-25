package hydro.bolt.ast.expr;

import hydro.bolt.ast.ASTNode;
import hydro.bolt.ast.ASTVisitor;

/**
 * Represents a ternary conditional expression (e.g., condition ? trueExpr : falseExpr).
 */
public class TernaryExpression extends ASTNode {
    public ASTNode condition;
    public ASTNode trueExpression;
    public ASTNode falseExpression;

    public TernaryExpression(ASTNode condition, ASTNode trueExpression, ASTNode falseExpression, int line, int column) {
        super(line, column);
        this.condition = condition;
        this.trueExpression = trueExpression;
        this.falseExpression = falseExpression;
    }

    public TernaryExpression(ASTNode condition, ASTNode trueExpression, ASTNode falseExpression) {
        super();
        this.condition = condition;
        this.trueExpression = trueExpression;
        this.falseExpression = falseExpression;
    }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitTernaryExpression(this);
    }
}
