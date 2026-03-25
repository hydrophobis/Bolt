package hydro.bolt.ast.bolt;

import hydro.bolt.ast.*;

public class PackageDeclaration extends ASTNode {
    public final String name;

    public PackageDeclaration(String name) {
        this.name = name;
    }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
