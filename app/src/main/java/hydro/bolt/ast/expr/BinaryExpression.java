package hydro.bolt.ast.expr;

import hydro.bolt.ast.ASTNode;
import hydro.bolt.ast.ASTVisitor;

/**
 * Represents a binary expression (e.g., a + b, x * y, etc.).
 */
public class BinaryExpression extends ASTNode {
    public ASTNode left;
    public String operator;
    public ASTNode right;

    public BinaryExpression(ASTNode left, String operator, ASTNode right, int line, int column) {
        super(line, column);
        this.left = left;
        this.operator = operator;
        this.right = right;
    }

    public BinaryExpression(ASTNode left, String operator, ASTNode right) {
        super();
        this.left = left;
        this.operator = operator;
        this.right = right;
    }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitBinaryExpression(this);
    }
}
