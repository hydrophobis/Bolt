package hydro.bolt.ast.statement;

import hydro.bolt.ast.ASTNode;
import hydro.bolt.ast.ASTVisitor;

/**
 * Represents a default statement (default label:).
 */
public class DefaultStatement extends ASTNode {
    public java.util.List<ASTNode> statements;

    public DefaultStatement(java.util.List<ASTNode> statements, int line, int column) {
        super(line, column);
        this.statements = statements;
    }

    public DefaultStatement(java.util.List<ASTNode> statements) {
        super();
        this.statements = statements;
    }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitDefaultStatement(this);
    }
}
