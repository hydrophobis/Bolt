package hydro.bolt.ast.statement;

import hydro.bolt.ast.ASTNode;
import hydro.bolt.ast.ASTVisitor;

/**
 * Represents an if statement (if, else if, else).
 */
public class IfStatement extends ASTNode {
    public ASTNode condition;
    public ASTNode thenBranch;
    public ASTNode elseBranch;

    public IfStatement(ASTNode condition, ASTNode thenBranch, ASTNode elseBranch, int line, int column) {
        super(line, column);
        this.condition = condition;
        this.thenBranch = thenBranch;
        this.elseBranch = elseBranch;
    }

    public IfStatement(ASTNode condition, ASTNode thenBranch, ASTNode elseBranch) {
        super();
        this.condition = condition;
        this.thenBranch = thenBranch;
        this.elseBranch = elseBranch;
    }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitIfStatement(this);
    }
}
