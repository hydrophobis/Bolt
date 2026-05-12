package hydro.bolt.ast.bolt;

import hydro.bolt.ast.ASTNode;
import hydro.bolt.ast.ASTVisitor;
import hydro.bolt.ast.RawCNode;
import hydro.bolt.ast.misc.Identifier;

public class UnaryOverloadNode extends OverloadNode {
    public String operator;
    public Identifier returnType;
    public Identifier operand;

    public UnaryOverloadNode(String operator, Identifier returnType, Identifier operand, ASTNode code) {
        super(code);
        this.operator = operator;
        this.returnType = returnType;
        this.operand = operand;
    }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitUnaryOverload(this);
    }
}
