package hydro.bolt.ast.misc;

import hydro.bolt.ast.ASTNode;
import hydro.bolt.ast.ASTVisitor;

/**
 * Represents a struct member access (e.g., obj.member).
 */
public class MemberAccess extends ASTNode {
    public ASTNode object;
    public String member;

    public MemberAccess(ASTNode object, String member, int line, int column) {
        super(line, column);
        this.object = object;
        this.member = member;
    }

    public MemberAccess(ASTNode object, String member) {
        super();
        this.object = object;
        this.member = member;
    }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitMemberAccess(this);
    }
}
