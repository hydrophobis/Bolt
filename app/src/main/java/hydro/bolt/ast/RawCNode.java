package hydro.bolt.ast;

public class RawCNode extends ASTNode {
    public final String content;

    public RawCNode(String content) {
        this.content = content;
    }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    public String toString() {
        return "RawCNode: " + content;
    }
}
