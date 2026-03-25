package hydro.bolt.ast.misc;

import hydro.bolt.ast.ASTNode;
import hydro.bolt.ast.ASTVisitor;

/**
 * Represents a struct or union member.
 */
public class StructMember extends ASTNode {
    public ASTNode type;
    public String name;

    public StructMember(ASTNode type, String name, int line, int column) {
        super(line, column);
        this.type = type;
        this.name = name;
    }

    public StructMember(ASTNode type, String name) {
        super();
        this.type = type;
        this.name = name;
    }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitStructMember(this);
    }
}
