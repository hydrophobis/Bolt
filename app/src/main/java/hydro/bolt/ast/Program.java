package hydro.bolt.ast;

public class Program extends ASTNode {
    public java.util.List<ASTNode> declarations;

    public Program(java.util.List<ASTNode> declarations) {
        this.declarations = declarations;
    }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitProgram(this);
    }
}
