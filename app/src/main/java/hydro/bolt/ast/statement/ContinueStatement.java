package hydro.bolt.ast.statement;

import hydro.bolt.ast.ASTNode;
import hydro.bolt.ast.ASTVisitor;

/**
 * Represents a continue statement.
 */
public class ContinueStatement extends ASTNode {
    public ContinueStatement(int line, int column) {
        super(line, column);
    }

    public ContinueStatement() {
        super();
    }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitContinueStatement(this);
    }
}
