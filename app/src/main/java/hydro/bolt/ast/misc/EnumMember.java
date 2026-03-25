package hydro.bolt.ast.misc;

import hydro.bolt.ast.ASTNode;
import hydro.bolt.ast.ASTVisitor;

/**
 * Represents an enum member.
 */
public class EnumMember extends ASTNode {
    public String name;
    public ASTNode value;

    public EnumMember(String name, ASTNode value, int line, int column) {
        super(line, column);
        this.name = name;
        this.value = value;
    }

    public EnumMember(String name, ASTNode value) {
        super();
        this.name = name;
        this.value = value;
    }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitEnumMember(this);
    }
}
