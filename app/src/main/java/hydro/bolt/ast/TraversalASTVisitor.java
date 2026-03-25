package hydro.bolt.ast;

import hydro.bolt.ast.decl.*;
import hydro.bolt.ast.expr.*;
import hydro.bolt.ast.misc.*;
import hydro.bolt.ast.statement.*;
import hydro.bolt.ast.type.*;
import hydro.bolt.ast.bolt.*;

/**
 * A traversing visitor that recursively visits all child nodes in the AST.
 * Useful as a base class for visitors that need to process entire subtrees.
 * Subclasses should override specific visit methods and call super.visitXxx()
 * to continue traversal to child nodes.
 */
public abstract class TraversalASTVisitor extends AbstractASTVisitor<Void> {

    @Override
    public Void visitProgram(Program node) {
        if (node.declarations != null) {
            for (ASTNode decl : node.declarations) {
                decl.accept(this);
            }
        }
        return null;
    }

    @Override
    public Void visitFunctionDeclaration(FunctionDeclaration node) {
        if (node.returnType != null) {
            node.returnType.accept(this);
        }
        if (node.parameters != null) {
            for (Parameter param : node.parameters) {
                param.accept(this);
            }
        }
        if (node.body != null) {
            node.body.accept(this);
        }
        return null;
    }

    @Override
    public Void visitVariableDeclaration(VariableDeclaration node) {
        if (node.type != null) {
            node.type.accept(this);
        }
        if (node.initializer != null) {
            node.initializer.accept(this);
        }
        return null;
    }

    @Override
    public Void visitStructDeclaration(StructDeclaration node) {
        if (node.members != null) {
            for (StructMember member : node.members) {
                member.accept(this);
            }
        }
        return null;
    }

    @Override
    public Void visitUnionDeclaration(UnionDeclaration node) {
        if (node.members != null) {
            for (StructMember member : node.members) {
                member.accept(this);
            }
        }
        return null;
    }

    @Override
    public Void visitEnumDeclaration(EnumDeclaration node) {
        if (node.members != null) {
            for (EnumMember member : node.members) {
                member.accept(this);
            }
        }
        return null;
    }

    @Override
    public Void visitBinaryExpression(BinaryExpression node) {
        node.left.accept(this);
        node.right.accept(this);
        return null;
    }

    @Override
    public Void visitUnaryExpression(UnaryExpression node) {
        node.operand.accept(this);
        return null;
    }

    @Override
    public Void visitAssignmentExpression(AssignmentExpression node) {
        node.target.accept(this);
        node.value.accept(this);
        return null;
    }

    @Override
    public Void visitTernaryExpression(TernaryExpression node) {
        node.condition.accept(this);
        node.trueExpression.accept(this);
        node.falseExpression.accept(this);
        return null;
    }

    @Override
    public Void visitFunctionCall(FunctionCall node) {
        node.function.accept(this);
        if (node.arguments != null) {
            for (ASTNode arg : node.arguments) {
                arg.accept(this);
            }
        }
        return null;
    }

    @Override
    public Void visitArrayAccess(ArrayAccess node) {
        node.array.accept(this);
        node.index.accept(this);
        return null;
    }

    @Override
    public Void visitMemberAccess(MemberAccess node) {
        node.object.accept(this);
        return null;
    }

    @Override
    public Void visitPointerMemberAccess(PointerMemberAccess node) {
        node.pointer.accept(this);
        return null;
    }

    @Override
    public Void visitSizeofExpression(SizeofExpression node) {
        if (node.operand != null) {
            node.operand.accept(this);
        }
        return null;
    }

    @Override
    public Void visitCastExpression(CastExpression node) {
        node.type.accept(this);
        node.expression.accept(this);
        return null;
    }

    @Override
    public Void visitInitializerList(InitializerList node) {
        if (node.elements != null) {
            for (ASTNode element : node.elements) {
                element.accept(this);
            }
        }
        return null;
    }

    @Override
    public Void visitBlock(Block node) {
        if (node.statements != null) {
            for (ASTNode stmt : node.statements) {
                stmt.accept(this);
            }
        }
        return null;
    }

    @Override
    public Void visitExpressionStatement(ExpressionStatement node) {
        node.expression.accept(this);
        return null;
    }

    @Override
    public Void visitIfStatement(IfStatement node) {
        node.condition.accept(this);
        node.thenBranch.accept(this);
        if (node.elseBranch != null) {
            node.elseBranch.accept(this);
        }
        return null;
    }

    @Override
    public Void visitWhileStatement(WhileStatement node) {
        node.condition.accept(this);
        node.body.accept(this);
        return null;
    }

    @Override
    public Void visitForStatement(ForStatement node) {
        if (node.initializer != null) {
            node.initializer.accept(this);
        }
        if (node.condition != null) {
            node.condition.accept(this);
        }
        if (node.update != null) {
            node.update.accept(this);
        }
        node.body.accept(this);
        return null;
    }

    @Override
    public Void visitDoWhileStatement(DoWhileStatement node) {
        node.body.accept(this);
        node.condition.accept(this);
        return null;
    }

    @Override
    public Void visitSwitchStatement(SwitchStatement node) {
        node.expression.accept(this);
        node.body.accept(this);
        return null;
    }

    @Override
    public Void visitCaseStatement(CaseStatement node) {
        node.value.accept(this);
        if (node.statements != null) {
            for (ASTNode stmt : node.statements) {
                stmt.accept(this);
            }
        }
        return null;
    }

    @Override
    public Void visitDefaultStatement(DefaultStatement node) {
        if (node.statements != null) {
            for (ASTNode stmt : node.statements) {
                stmt.accept(this);
            }
        }
        return null;
    }

    @Override
    public Void visitReturnStatement(ReturnStatement node) {
        if (node.value != null) {
            node.value.accept(this);
        }
        return null;
    }

    @Override
    public Void visitPointerType(PointerType node) {
        node.baseType.accept(this);
        return null;
    }

    @Override
    public Void visitArrayType(ArrayType node) {
        node.elementType.accept(this);
        if (node.size != null) {
            node.size.accept(this);
        }
        return null;
    }

    @Override
    public Void visitFunctionType(FunctionType node) {
        node.returnType.accept(this);
        if (node.parameterTypes != null) {
            for (ASTNode paramType : node.parameterTypes) {
                paramType.accept(this);
            }
        }
        return null;
    }

    @Override
    public Void visitParameter(Parameter node) {
        node.type.accept(this);
        return null;
    }

    @Override
    public Void visitStructMember(StructMember node) {
        node.type.accept(this);
        return null;
    }

    @Override
    public Void visitEnumMember(EnumMember node) {
        if (node.value != null) {
            node.value.accept(this);
        }
        return null;
    }

    @Override
    public Void visitTypedefDeclaration(TypedefDeclaration node) {
        node.type.accept(this);
        return null;
    }

    @Override
    public Void visitClassDeclaration(ClassDeclaration node) {
        return null;
    }

    @Override
    public Void visit(ImportDeclaration node) {
        return null;
    }

    @Override
    public Void visit(ImplDeclaration node) {
        if (node.members != null) {
            for (ASTNode member : node.members) {
                member.accept(this);
            }
        }
        return null;
    }

    @Override
    public Void visit(DecoratorNode node) {
        return null;
    }

    @Override
    public Void visitVariableExpression(VariableExpression variableExpression) {
        return null;
    }

    @Override
    public Void visit(RawCNode node) {
        return null;
    }
}
