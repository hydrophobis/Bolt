package hydro.bolt.ast.decl;

import hydro.bolt.ast.ASTNode;
import hydro.bolt.ast.ASTVisitor;
import hydro.bolt.ast.misc.StructMember;

/**
 * Represents a union declaration.
 */
public class UnionDeclaration extends ASTNode {
    public String name;
    public java.util.List<StructMember> members;

    public UnionDeclaration(String name, java.util.List<StructMember> members, int line, int column) {
        super(line, column);
        this.name = name;
        this.members = members;
    }

    public UnionDeclaration(String name, java.util.List<StructMember> members) {
        super();
        this.name = name;
        this.members = members;
    }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitUnionDeclaration(this);
    }
}
