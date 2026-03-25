package hydro.bolt.ast.expr;

import hydro.bolt.ast.*;

public class NewExpression extends ASTNode {
    public final String typeName;
    public final java.util.List<ASTNode> arguments;

    public NewExpression(String typeName, java.util.List<ASTNode> arguments) {
        this.typeName = typeName;
        this.arguments = arguments;
    }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
