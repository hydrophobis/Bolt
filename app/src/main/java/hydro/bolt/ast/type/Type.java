package hydro.bolt.ast.type;

import hydro.bolt.ast.ASTNode;

/**
 * Base class for all type nodes.
 */
public abstract class Type extends ASTNode {
    public Type(int line, int column) {
        super(line, column);
    }

    public Type() {
        super();
    }
}
