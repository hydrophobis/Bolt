package hydro.bolt.ast.decl;

import hydro.bolt.ast.ASTNode;
import hydro.bolt.ast.ASTVisitor;
import hydro.bolt.ast.misc.StructMember;

/**
 * Represents a struct declaration.
 */
public class StructDeclaration extends ASTNode {
    public String name;
    public java.util.List<StructMember> members;

    public StructDeclaration(String name, java.util.List<StructMember> members, int line, int column) {
        super(line, column);
        this.name = name;
        this.members = members;
    }

    public StructDeclaration(String name, java.util.List<StructMember> members) {
        super();
        this.name = name;
        this.members = members;
    }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitStructDeclaration(this);
    }
}
