package hydro.bolt.ast;

import hydro.bolt.ast.bolt.DecoratorNode;
import java.util.ArrayList;
import java.util.List;

public abstract class ASTNode {
    public int line;
    public int column;
    public List<DecoratorNode> decorators = new ArrayList<>();

    public ASTNode(int line, int column) {
        this.line = line;
        this.column = column;
    }

    public ASTNode() {
        this(0, 0);
    }

    public String toString() {
        return this.getClass().getSimpleName();
    }

    public abstract <T> T accept(ASTVisitor<T> visitor);
}
