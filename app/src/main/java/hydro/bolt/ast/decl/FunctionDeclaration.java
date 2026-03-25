package hydro.bolt.ast.decl;

import hydro.bolt.ast.ASTNode;
import hydro.bolt.ast.ASTVisitor;
import hydro.bolt.ast.misc.Parameter;

/**
 * Represents a function declaration.
 */
public class FunctionDeclaration extends ASTNode {
    public ASTNode returnType;
    public String name;
    public java.util.List<Parameter> parameters;
    public ASTNode body;
    public boolean isExtern;
    public java.util.List<String> genericParams = new java.util.ArrayList<>();

    public FunctionDeclaration(ASTNode returnType, String name, java.util.List<Parameter> parameters, ASTNode body, boolean isExtern, int line, int column) {
        super(line, column);
        this.returnType = returnType;
        this.name = name;
        this.parameters = parameters;
        this.body = body;
        this.isExtern = isExtern;
    }

    public FunctionDeclaration(ASTNode returnType, String name, java.util.List<Parameter> parameters, ASTNode body, boolean isExtern) {
        super();
        this.returnType = returnType;
        this.name = name;
        this.parameters = parameters;
        this.body = body;
        this.isExtern = isExtern;
    }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitFunctionDeclaration(this);
    }
}
