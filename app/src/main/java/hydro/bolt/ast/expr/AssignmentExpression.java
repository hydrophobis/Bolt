package hydro.bolt.ast.expr;

import hydro.bolt.ast.ASTNode;
import hydro.bolt.ast.ASTVisitor;

/**
 * Represents an assignment expression (e.g., x = 5, a += 3, etc.).
 */
public class AssignmentExpression extends ASTNode {
    public ASTNode target;
    public String operator;
    public ASTNode value;

    public AssignmentExpression(ASTNode target, String operator, ASTNode value, int line, int column) {
        super(line, column);
        this.target = target;
        this.operator = operator;
        this.value = value;
    }

    public AssignmentExpression(ASTNode target, String operator, ASTNode value) {
        super();
        this.target = target;
        this.operator = operator;
        this.value = value;
    }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitAssignmentExpression(this);
    }
}
