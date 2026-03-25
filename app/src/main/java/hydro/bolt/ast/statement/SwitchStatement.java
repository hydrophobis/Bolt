package hydro.bolt.ast.statement;

import hydro.bolt.ast.ASTNode;
import hydro.bolt.ast.ASTVisitor;

/**
 * Represents a switch statement.
 */
public class SwitchStatement extends ASTNode {
    public ASTNode expression;
    public ASTNode body;

    public SwitchStatement(ASTNode expression, ASTNode body, int line, int column) {
        super(line, column);
        this.expression = expression;
        this.body = body;
    }

    public SwitchStatement(ASTNode expression, ASTNode body) {
        super();
        this.expression = expression;
        this.body = body;
    }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitSwitchStatement(this);
    }
}
