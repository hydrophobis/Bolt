package hydro.bolt.ast.statement;

import hydro.bolt.ast.ASTNode;
import hydro.bolt.ast.ASTVisitor;

/**
 * Represents a labeled statement (label:).
 */
public class LabeledStatement extends ASTNode {
    public String label;
    public ASTNode statement;

    public LabeledStatement(String label, ASTNode statement, int line, int column) {
        super(line, column);
        this.label = label;
        this.statement = statement;
    }

    public LabeledStatement(String label, ASTNode statement) {
        super();
        this.label = label;
        this.statement = statement;
    }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitLabeledStatement(this);
    }
}
