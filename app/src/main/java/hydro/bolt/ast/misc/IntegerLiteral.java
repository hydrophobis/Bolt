package hydro.bolt.ast.misc;

import hydro.bolt.ast.ASTVisitor;

public class IntegerLiteral extends Literal {
    public IntegerLiteral(String value, int line, int column) {
        super(value, line, column);
    }

    public IntegerLiteral(String value) {
        super(value);
    }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitIntegerLiteral(this);
    }
}
