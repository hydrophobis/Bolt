package hydro.bolt.ast.misc;

import hydro.bolt.ast.ASTNode;
import hydro.bolt.ast.ASTVisitor;

/**
 * Represents a block statement { ... }.
 */
public class Block extends ASTNode {
    public java.util.List<ASTNode> statements;

    public Block(java.util.List<ASTNode> statements, int line, int column) {
        super(line, column);
        this.statements = statements;
    }

    public Block(java.util.List<ASTNode> statements) {
        super();
        this.statements = statements;
    }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitBlock(this);
    }
}
