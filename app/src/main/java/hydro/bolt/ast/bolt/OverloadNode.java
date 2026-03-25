package hydro.bolt.ast.bolt;

import javax.management.RuntimeErrorException;

import hydro.bolt.ast.ASTNode;
import hydro.bolt.ast.ASTVisitor;
import hydro.bolt.ast.RawCNode;

public class OverloadNode extends ASTNode {
    public ASTNode code;

    public OverloadNode(ASTNode code) {
        this.code = code;
    }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        throw new UnsupportedOperationException("Base OverloadNode cannot be visited directly");
    }
    
}
