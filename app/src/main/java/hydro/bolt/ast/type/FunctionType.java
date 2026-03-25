package hydro.bolt.ast.type;

import hydro.bolt.ast.ASTNode;
import hydro.bolt.ast.ASTVisitor;

/**
 * Represents a function pointer type (e.g., int (*)(int, int)).
 */
public class FunctionType extends Type {
    public ASTNode returnType;
    public java.util.List<ASTNode> parameterTypes;

    public FunctionType(ASTNode returnType, java.util.List<ASTNode> parameterTypes, int line, int column) {
        super(line, column);
        this.returnType = returnType;
        this.parameterTypes = parameterTypes;
    }

    public FunctionType(ASTNode returnType, java.util.List<ASTNode> parameterTypes) {
        super();
        this.returnType = returnType;
        this.parameterTypes = parameterTypes;
    }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitFunctionType(this);
    }
}
