package hydro.bolt.ast.misc;

import hydro.bolt.ast.ASTVisitor;

public class FloatLiteral extends Literal {
    public FloatLiteral(String value, int line, int column) {
        super(value, line, column);
    }

    public FloatLiteral(String value) {
        super(value);
    }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitFloatLiteral(this);
    }
}
