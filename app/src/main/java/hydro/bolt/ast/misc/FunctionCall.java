package hydro.bolt.ast.misc;

import hydro.bolt.ast.ASTNode;
import hydro.bolt.ast.ASTVisitor;

/**
 * Represents a function call (e.g., foo(a, b, c)).
 */
public class FunctionCall extends ASTNode {
    public ASTNode function;
    public java.util.List<ASTNode> arguments;

    public FunctionCall(ASTNode function, java.util.List<ASTNode> arguments, int line, int column) {
        super(line, column);
        this.function = function;
        this.arguments = arguments;
    }

    public FunctionCall(ASTNode function, java.util.List<ASTNode> arguments) {
        super();
        this.function = function;
        this.arguments = arguments;
    }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitFunctionCall(this);
    }
}
