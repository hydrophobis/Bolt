package hydro.bolt.ast.misc;

import hydro.bolt.ast.ASTVisitor;

public class StringLiteral extends Literal {
    public StringLiteral(String value, int line, int column) {
        super(value, line, column);
    }

    public StringLiteral(String value) {
        super(value);
    }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitStringLiteral(this);
    }
}
