package hydro.bolt.ast.bolt;

import hydro.bolt.ast.*;
import java.util.List;
import java.util.ArrayList;

public class DecoratorNode extends ASTNode {
    public final String name;
    public final List<String> arguments;
    public boolean isNegated = false;

    public DecoratorNode(String name) {
        this(name, new ArrayList<>());
    }

    public DecoratorNode(String name, List<String> arguments) {
        this.name = name;
        this.arguments = arguments;
    }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
