package hydro.bolt.ast.expr;

import hydro.bolt.ast.ASTNode;
import hydro.bolt.ast.ASTVisitor;
import hydro.bolt.ast.misc.Parameter;
import java.util.ArrayList;
import java.util.List;

// Syntax: |param1, param2| { body } or |param1, param2| expression
public class LambdaExpression extends ASTNode {
    public List<Parameter> parameters;
    public ASTNode body; // Can be a Block or a single expression
    public List<String> captures; // List of captured variable names

    public LambdaExpression(List<Parameter> parameters, ASTNode body, int line, int column) {
        super(line, column);
        this.parameters = parameters;
        this.body = body;
        this.captures = new ArrayList<>();
    }

    public LambdaExpression(List<Parameter> parameters, ASTNode body) {
        this(parameters, body, 0, 0);
    }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitLambdaExpression(this);
    }
}
