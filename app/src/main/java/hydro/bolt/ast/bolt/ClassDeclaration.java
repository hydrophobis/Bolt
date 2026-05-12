package hydro.bolt.ast.bolt;

import hydro.bolt.ast.*;

public class ClassDeclaration extends ASTNode{
    public String name;
    public ASTTree inner;
    public java.util.List<String> genericParams = new java.util.ArrayList<>();
    public java.util.List<String> interfaces = new java.util.ArrayList<>();

    public ClassDeclaration(String name, ASTTree inner) {
        super();
        this.name = name;
        this.inner = inner;
    }

    public ClassDeclaration(String name, int line, int column) {
        super(line, column);
        this.name = name;
    }

    public ClassDeclaration(String name) {
        super();
        this.name = name;
    }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitClassDeclaration(this);
    }
}
