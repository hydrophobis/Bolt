package hydro.bolt.ast.expr;

import hydro.bolt.ast.ASTNode;
import hydro.bolt.ast.ASTVisitor;

/**
 * Represents a sizeof expression (e.g., sizeof(int), sizeof(x)).
 */
public class SizeofExpression extends ASTNode {
    public ASTNode operand;
    public boolean isType;

    public SizeofExpression(ASTNode operand, boolean isType, int line, int column) {
        super(line, column);
        this.operand = operand;
        this.isType = isType;
    }

    public SizeofExpression(ASTNode operand, boolean isType) {
        super();
        this.operand = operand;
        this.isType = isType;
    }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitSizeofExpression(this);
    }
}
