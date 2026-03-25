package hydro.bolt.ast.misc;

import hydro.bolt.ast.ASTVisitor;

public class CharLiteral extends Literal {
    public CharLiteral(String value, int line, int column) {
        super(value, line, column);
    }

    public CharLiteral(String value) {
        super(value);
    }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitCharLiteral(this);
    }
}
