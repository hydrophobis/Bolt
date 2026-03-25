package hydro.bolt.ast.decl;

import hydro.bolt.ast.ASTNode;
import hydro.bolt.ast.ASTVisitor;

/**
 * Represents a typedef declaration.
 */
public class TypedefDeclaration extends ASTNode {
    public ASTNode type;
    public String name;

    public TypedefDeclaration(ASTNode type, String name, int line, int column) {
        super(line, column);
        this.type = type;
        this.name = name;
    }

    public TypedefDeclaration(ASTNode type, String name) {
        super();
        this.type = type;
        this.name = name;
    }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitTypedefDeclaration(this);
    }
}
