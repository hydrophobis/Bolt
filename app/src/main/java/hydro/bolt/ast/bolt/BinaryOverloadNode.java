package hydro.bolt.ast.bolt;

import hydro.bolt.ast.ASTNode;
import hydro.bolt.ast.ASTVisitor;
import hydro.bolt.ast.RawCNode;
import hydro.bolt.ast.misc.Identifier;

public class BinaryOverloadNode extends OverloadNode {
    public Identifier returnType;
    public Identifier operand1, operand2;
    public String operator;

    public BinaryOverloadNode(Identifier returnType, Identifier operand1, Identifier operand2, String operator, ASTNode code) {
        super(code);
        this.returnType = returnType;
        this.operand1 = operand1;
        this.operand2 = operand2;
        this.operator = operator;
    }
    
    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitBinaryOverload(this);
    }
}
