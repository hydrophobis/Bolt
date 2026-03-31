package hydro.bolt.ast.decl;

import hydro.bolt.ast.ASTNode;
import hydro.bolt.ast.ASTVisitor;
import hydro.bolt.ast.Visibility;

public class VariableDeclaration extends ASTNode {
    public ASTNode type;
    public String name;
    public ASTNode initializer;
    public boolean isStatic;
    public boolean isExtern;
    public Visibility visibility = Visibility.PUBLIC;

    public VariableDeclaration(ASTNode type, String name, ASTNode initializer, boolean isStatic, boolean isExtern, int line, int column) {
        super(line, column);
        this.type = type;
        this.name = name;
        this.initializer = initializer;
        this.isStatic = isStatic;
        this.isExtern = isExtern;
    }

    public VariableDeclaration(ASTNode type, String name, ASTNode initializer, boolean isStatic, boolean isExtern) {
        super();
        this.type = type;
        this.name = name;
        this.initializer = initializer;
        this.isStatic = isStatic;
        this.isExtern = isExtern;
    }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitVariableDeclaration(this);
    }
}
