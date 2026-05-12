package hydro.bolt.ast.bolt;

import hydro.bolt.ast.*;

public class InterfaceDeclaration extends ASTNode {
    public String name;
    public ASTTree inner;

    public InterfaceDeclaration(String name, ASTTree inner) {
        super();
        this.name = name;
        this.inner = inner;
    }

    public InterfaceDeclaration(String name, int line, int column) {
        super(line, column);
        this.name = name;
    }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitInterfaceDeclaration(this);
    }
}
