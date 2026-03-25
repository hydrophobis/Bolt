package hydro.bolt.ast.expr;

import hydro.bolt.ast.ASTNode;

public class VariableExpression extends ASTNode {
    public String name;

    public VariableExpression(String name, int line, int column) {
        super(line, column);
        this.name = name;
    }

    public VariableExpression(String name) {
        super();
        this.name = name;
    }

    @Override
    public <T> T accept(hydro.bolt.ast.ASTVisitor<T> visitor) {
        return visitor.visitVariableExpression(this);
    }
}
