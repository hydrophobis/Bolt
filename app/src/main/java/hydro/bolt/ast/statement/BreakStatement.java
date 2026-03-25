package hydro.bolt.ast.statement;

import hydro.bolt.ast.ASTNode;
import hydro.bolt.ast.ASTVisitor;

/**
 * Represents a break statement.
 */
public class BreakStatement extends ASTNode {
    public BreakStatement(int line, int column) {
        super(line, column);
    }

    public BreakStatement() {
        super();
    }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitBreakStatement(this);
    }
}
