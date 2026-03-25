package hydro.bolt.ast.expr;

import hydro.bolt.ast.ASTNode;
import hydro.bolt.ast.ASTVisitor;

/**
 * Represents a unary expression (e.g., -x, !a, ++i, etc.).
 */
public class UnaryExpression extends ASTNode {
    public String operator;
    public ASTNode operand;
    public boolean isPrefix;

    public UnaryExpression(String operator, ASTNode operand, boolean isPrefix, int line, int column) {
        super(line, column);
        this.operator = operator;
        this.operand = operand;
        this.isPrefix = isPrefix;
    }

    public UnaryExpression(String operator, ASTNode operand, boolean isPrefix) {
        super();
        this.operator = operator;
        this.operand = operand;
        this.isPrefix = isPrefix;
    }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitUnaryExpression(this);
    }
}
