package hydro.bolt.ast.expr;

import hydro.bolt.ast.*;

public class DeleteExpression extends ASTNode {
    public final ASTNode target;

    public DeleteExpression(ASTNode target) {
        this.target = target;
    }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
