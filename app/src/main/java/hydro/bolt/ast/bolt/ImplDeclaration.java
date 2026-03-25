package hydro.bolt.ast.bolt;

import hydro.bolt.ast.*;
import java.util.List;

public class ImplDeclaration extends ASTNode {
    public final String targetType;
    public final List<ASTNode> members;

    public ImplDeclaration(String targetType, List<ASTNode> members) {
        this.targetType = targetType;
        this.members = members;
    }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
