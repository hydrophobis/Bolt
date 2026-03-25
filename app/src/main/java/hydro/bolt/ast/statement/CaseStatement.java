package hydro.bolt.ast.statement;

import hydro.bolt.ast.ASTNode;
import hydro.bolt.ast.ASTVisitor;

/**
 * Represents a case statement (case label:).
 */
public class CaseStatement extends ASTNode {
    public ASTNode value;
    public java.util.List<ASTNode> statements;

    public CaseStatement(ASTNode value, java.util.List<ASTNode> statements, int line, int column) {
        super(line, column);
        this.value = value;
        this.statements = statements;
    }

    public CaseStatement(ASTNode value, java.util.List<ASTNode> statements) {
        super();
        this.value = value;
        this.statements = statements;
    }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitCaseStatement(this);
    }
}
