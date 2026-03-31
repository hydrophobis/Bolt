package hydro.bolt.ast.bolt;

import hydro.bolt.ast.*;

public class ImportDeclaration extends ASTNode {
    public final String path;
    public boolean isCHeader = false;
    public String cHeaderName = null;

    public ImportDeclaration(String path) {
        this.path = path;
    }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
