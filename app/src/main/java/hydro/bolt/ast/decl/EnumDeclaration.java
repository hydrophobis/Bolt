package hydro.bolt.ast.decl;

import hydro.bolt.ast.ASTNode;
import hydro.bolt.ast.ASTVisitor;
import hydro.bolt.ast.misc.EnumMember;

/**
 * Represents an enum declaration.
 */
public class EnumDeclaration extends ASTNode {
    public String name;
    public java.util.List<EnumMember> members;

    public EnumDeclaration(String name, java.util.List<EnumMember> members, int line, int column) {
        super(line, column);
        this.name = name;
        this.members = members;
    }

    public EnumDeclaration(String name, java.util.List<EnumMember> members) {
        super();
        this.name = name;
        this.members = members;
    }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitEnumDeclaration(this);
    }
}
