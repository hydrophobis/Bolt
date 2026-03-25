package hydro.bolt.ast.misc;

import hydro.bolt.ast.ASTNode;
import hydro.bolt.ast.ASTVisitor;

/**
 * Represents a pointer member access (e.g., ptr->member).
 */
public class PointerMemberAccess extends ASTNode {
    public ASTNode pointer;
    public String member;

    public PointerMemberAccess(ASTNode pointer, String member, int line, int column) {
        super(line, column);
        this.pointer = pointer;
        this.member = member;
    }

    public PointerMemberAccess(ASTNode pointer, String member) {
        super();
        this.pointer = pointer;
        this.member = member;
    }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitPointerMemberAccess(this);
    }
}
