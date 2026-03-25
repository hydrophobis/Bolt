package hydro.bolt.ast.statement;

import hydro.bolt.ast.ASTNode;
import hydro.bolt.ast.ASTVisitor;

/**
 * Represents a goto statement.
 */
public class GotoStatement extends ASTNode {
    public String label;

    public GotoStatement(String label, int line, int column) {
        super(line, column);
        this.label = label;
    }

    public GotoStatement(String label) {
        super();
        this.label = label;
    }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitGotoStatement(this);
    }
}
