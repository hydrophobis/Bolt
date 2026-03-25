package hydro.bolt.ast.misc;

import hydro.bolt.ast.ASTNode;

/**
 * Represents a literal value (integer, float, char, or string).
 */
public abstract class Literal extends ASTNode {
    public String value;

    public Literal(String value, int line, int column) {
        super(line, column);
        this.value = value;
    }

    public Literal(String value) {
        super();
        this.value = value;
    }
}
