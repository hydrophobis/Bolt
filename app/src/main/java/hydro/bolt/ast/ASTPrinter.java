package hydro.bolt.ast;

import hydro.bolt.ast.bolt.*;
import hydro.bolt.ast.decl.*;
import hydro.bolt.ast.expr.*;
import hydro.bolt.ast.misc.*;
import hydro.bolt.ast.type.*;
import hydro.bolt.ast.statement.*;

// DO NOT USE, VERY BAD NUH UH
public class ASTPrinter extends AbstractASTVisitor<String> {
    private int indentLevel = 0;
    private static final String INDENT_STRING = "  ";

    public String print(ASTNode node) {
        indentLevel = 0;
        return printWithDecorators(node);
    }

    private String printWithDecorators(ASTNode node) {
        StringBuilder sb = new StringBuilder();
        if (node.decorators != null && !node.decorators.isEmpty()) {
            for (DecoratorNode dec : node.decorators) {
                sb.append(indent()).append("@").append(dec.isNegated ? "!" : "").append(dec.name);
                if (dec.arguments != null && !dec.arguments.isEmpty()) {
                    sb.append("(").append(String.join(", ", dec.arguments)).append(")");
                }
                sb.append("\n");
            }
        }
        sb.append(node.accept(this));
        return sb.toString();
    }

    private String indent() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < indentLevel; i++) {
            sb.append(INDENT_STRING);
        }
        return sb.toString();
    }

    @Override
    public String visitUnaryOverload(UnaryOverloadNode overload) {
        return indent() + "UnaryOverload: " + overload.operator + " for " + overload.operand.name;
    }

    @Override
    public String visitProgram(Program node) {
        StringBuilder sb = new StringBuilder();
        sb.append(indent()).append("Program\n");
        if (node.declarations != null) {
            indentLevel++;
            for (ASTNode decl : node.declarations) {
                sb.append(printWithDecorators(decl));
            }
            indentLevel--;
        }
        return sb.toString();
    }

    @Override
    public String visitFunctionDeclaration(FunctionDeclaration node) {
        StringBuilder sb = new StringBuilder();
        sb.append(indent()).append("FunctionDeclaration: ").append(node.name).append("\n");
        indentLevel++;
        sb.append(indent()).append("ReturnType:\n");
        indentLevel++;
        sb.append(node.returnType.accept(this));
        indentLevel--;
        if (node.parameters != null && !node.parameters.isEmpty()) {
            sb.append(indent()).append("Parameters:\n");
            indentLevel++;
            for (Parameter param : node.parameters) {
                sb.append(printWithDecorators(param));
            }
            indentLevel--;
        }
        if (node.body != null) {
            sb.append(indent()).append("Body:\n");
            indentLevel++;
            sb.append(node.body.accept(this));
            indentLevel--;
        }
        indentLevel--;
        return sb.toString();
    }

    @Override
    public String visitVariableDeclaration(VariableDeclaration node) {
        StringBuilder sb = new StringBuilder();
        sb.append(indent()).append("VariableDeclaration: ").append(node.name).append("\n");
        indentLevel++;
        sb.append(indent()).append("Type:\n");
        indentLevel++;
        sb.append(node.type.accept(this));
        indentLevel--;
        if (node.initializer != null) {
            sb.append(indent()).append("Initializer:\n");
            indentLevel++;
            sb.append(node.initializer.accept(this));
            indentLevel--;
        }
        indentLevel--;
        return sb.toString();
    }

    @Override
    public String visitIdentifier(Identifier node) {
        return indent() + "Identifier: " + node.name + "\n";
    }

    @Override
    public String visitIntegerLiteral(IntegerLiteral node) {
        return indent() + "IntegerLiteral: " + node.value + "\n";
    }

    @Override
    public String visitFloatLiteral(FloatLiteral node) {
        return indent() + "FloatLiteral: " + node.value + "\n";
    }

    @Override
    public String visitStringLiteral(StringLiteral node) {
        return indent() + "StringLiteral: \"" + node.value + "\"\n";
    }

    @Override
    public String visitBinaryExpression(BinaryExpression node) {
        StringBuilder sb = new StringBuilder();
        sb.append(indent()).append("BinaryExpression: ").append(node.operator).append("\n");
        indentLevel++;
        sb.append(indent()).append("Left:\n");
        indentLevel++;
        sb.append(node.left.accept(this));
        indentLevel--;
        sb.append(indent()).append("Right:\n");
        indentLevel++;
        sb.append(node.right.accept(this));
        indentLevel--;
        indentLevel--;
        return sb.toString();
    }

    @Override
    public String visitPrimitiveType(PrimitiveType node) {
        return indent() + "PrimitiveType: " + node.name + "\n";
    }

    @Override
    public String visitPointerType(PointerType node) {
        StringBuilder sb = new StringBuilder();
        sb.append(indent()).append("PointerType:\n");
        indentLevel++;
        sb.append(node.baseType.accept(this));
        indentLevel--;
        return sb.toString();
    }

    @Override
    public String visitBlock(Block node) {
        StringBuilder sb = new StringBuilder();
        sb.append(indent()).append("Block\n");
        if (node.statements != null) {
            indentLevel++;
            for (ASTNode stmt : node.statements) {
                sb.append(printWithDecorators(stmt));
            }
            indentLevel--;
        }
        return sb.toString();
    }

    @Override
    public String visitClassDeclaration(ClassDeclaration node) {
        StringBuilder sb = new StringBuilder();
        sb.append(indent()).append("ClassDeclaration: ").append(node.name).append("\n");

        if (node.inner != null) {
            indentLevel++;
            for (ASTNode member : node.inner) {
                sb.append(printWithDecorators(member));
            }
            indentLevel--;
        }

        return sb.toString();
    }


    @Override
    public String visit(ImportDeclaration node) {
        return indent() + "ImportDeclaration: " + node.path + "\n";
    }

    @Override
    public String visit(ImplDeclaration node) {
        StringBuilder sb = new StringBuilder();
        sb.append(indent()).append("ImplDeclaration: ").append(node.targetType).append("\n");
        if (node.members != null) {
            indentLevel++;
            for (ASTNode member : node.members) {
                sb.append(printWithDecorators(member));
            }
            indentLevel--;
        }
        return sb.toString();
    }

    @Override
    public String visit(DecoratorNode node) {
        return indent() + "DecoratorNode: " + node.name + "\n";
    }

    @Override
    public String visit(RawCNode node) {
        return indent() + "RawCNode: " + node.content.trim() + "\n";
    }

    @Override
    public String visitStructDeclaration(StructDeclaration node) {
        return indent() + "StructDeclaration: " + node.name + "\n";
    }

    @Override
    public String visitUnionDeclaration(UnionDeclaration node) {
        return indent() + "UnionDeclaration: " + node.name + "\n";
    }

    @Override
    public String visitEnumDeclaration(EnumDeclaration node) {
        return indent() + "EnumDeclaration: " + node.name + "\n";
    }

    @Override
    public String visitTypedefDeclaration(TypedefDeclaration node) {
        return indent() + "TypedefDeclaration\n";
    }

    @Override
    public String visitCharLiteral(CharLiteral node) {
        return indent() + "CharLiteral: '" + node.value + "'\n";
    }

    @Override
    public String visitUnaryExpression(UnaryExpression node) {
        StringBuilder sb = new StringBuilder();
        sb.append(indent()).append("UnaryExpression: ").append(node.operator).append(" (prefix=").append(node.operand).append(")\n");
        indentLevel++;
        sb.append(node.operand.accept(this));
        indentLevel--;
        return sb.toString();
    }

    @Override
    public String visitAssignmentExpression(AssignmentExpression node) {
        return indent() + "AssignmentExpression\n";
    }

    @Override
    public String visitTernaryExpression(TernaryExpression node) {
        return indent() + "TernaryExpression\n";
    }

    @Override
    public String visitFunctionCall(FunctionCall node) {
        StringBuilder sb = new StringBuilder();
        sb.append(indent()).append("FunctionCall\n");
        indentLevel++;
        sb.append(indent()).append("Function:\n");
        indentLevel++;
        sb.append(node.function.accept(this));
        indentLevel--;
        if (node.arguments != null && !node.arguments.isEmpty()) {
            sb.append(indent()).append("Arguments:\n");
            indentLevel++;
            for (ASTNode arg : node.arguments) {
                sb.append(arg.accept(this));
            }
            indentLevel--;
        }
        indentLevel--;
        return sb.toString();
    }

    @Override
    public String visitArrayAccess(ArrayAccess node) {
        StringBuilder sb = new StringBuilder();
        sb.append(indent()).append("ArrayAccess\n");
        indentLevel++;
        sb.append(indent()).append("Array:\n");
        indentLevel++;
        sb.append(node.array.accept(this));
        indentLevel--;
        sb.append(indent()).append("Index:\n");
        indentLevel++;
        sb.append(node.index.accept(this));
        indentLevel--;
        indentLevel--;
        return sb.toString();
    }

    @Override
    public String visitMemberAccess(MemberAccess node) {
        StringBuilder sb = new StringBuilder();
        sb.append(indent()).append("MemberAccess: .").append(node.member).append("\n");
        indentLevel++;
        sb.append(node.object.accept(this));
        indentLevel--;
        return sb.toString();
    }

    @Override
    public String visitPointerMemberAccess(PointerMemberAccess node) {
        return indent() + "PointerMemberAccess: ->" + node.member + "\n";
    }

    @Override
    public String visitSizeofExpression(SizeofExpression node) {
        return indent() + "SizeofExpression\n";
    }

    @Override
    public String visitCastExpression(CastExpression node) {
        return indent() + "CastExpression\n";
    }

    @Override
    public String visitInitializerList(InitializerList node) {
        return indent() + "InitializerList\n";
    }

    @Override
    public String visitExpressionStatement(ExpressionStatement node) {
        StringBuilder sb = new StringBuilder();
        sb.append(indent()).append("ExpressionStatement\n");
        if (node.expression != null) {
            indentLevel++;
            sb.append(node.expression.accept(this));
            indentLevel--;
        }
        return sb.toString();
    }

    @Override
    public String visitIfStatement(IfStatement node) {
        StringBuilder sb = new StringBuilder();
        sb.append(indent()).append("IfStatement\n");
        indentLevel++;
        sb.append(indent()).append("Condition:\n");
        indentLevel++;
        sb.append(node.condition.accept(this));
        indentLevel--;
        sb.append(indent()).append("Then:\n");
        indentLevel++;
        sb.append(node.thenBranch.accept(this));
        indentLevel--;
        if (node.elseBranch != null) {
            sb.append(indent()).append("Else:\n");
            indentLevel++;
            sb.append(node.elseBranch.accept(this));
            indentLevel--;
        }
        indentLevel--;
        return sb.toString();
    }

    @Override
    public String visitWhileStatement(WhileStatement node) {
        StringBuilder sb = new StringBuilder();
        sb.append(indent()).append("WhileStatement\n");
        indentLevel++;
        sb.append(indent()).append("Condition:\n");
        indentLevel++;
        sb.append(node.condition.accept(this));
        indentLevel--;
        sb.append(indent()).append("Body:\n");
        indentLevel++;
        sb.append(node.body.accept(this));
        indentLevel--;
        indentLevel--;
        return sb.toString();
    }

    @Override
    public String visitForStatement(ForStatement node) {
        return indent() + "ForStatement\n";
    }

    @Override
    public String visitDoWhileStatement(DoWhileStatement node) {
        return indent() + "DoWhileStatement\n";
    }

    @Override
    public String visitSwitchStatement(SwitchStatement node) {
        StringBuilder sb = new StringBuilder();
        sb.append(indent()).append("SwitchStatement\n");
        indentLevel++;
        sb.append(indent()).append("Expression:\n");
        indentLevel++;
        sb.append(node.expression.accept(this));
        indentLevel--;
        indentLevel--;
        return sb.toString();
    }

    @Override
    public String visitCaseStatement(CaseStatement node) {
        return indent() + "CaseStatement\n";
    }

    @Override
    public String visitDefaultStatement(DefaultStatement node) {
        return indent() + "DefaultStatement\n";
    }

    @Override
    public String visitBreakStatement(BreakStatement node) {
        return indent() + "BreakStatement\n";
    }

    @Override
    public String visitContinueStatement(ContinueStatement node) {
        return indent() + "ContinueStatement\n";
    }

    @Override
    public String visitReturnStatement(ReturnStatement node) {
        StringBuilder sb = new StringBuilder();
        sb.append(indent()).append("ReturnStatement\n");
        if (node.value != null) {
            indentLevel++;
            sb.append(node.value.accept(this));
            indentLevel--;
        }
        return sb.toString();
    }

    @Override
    public String visitGotoStatement(GotoStatement node) {
        return indent() + "GotoStatement\n";
    }

    @Override
    public String visitLabeledStatement(LabeledStatement node) {
        return indent() + "LabeledStatement\n";
    }

    @Override
    public String visitArrayType(ArrayType node) {
        return indent() + "ArrayType\n";
    }

    @Override
    public String visitFunctionType(FunctionType node) {
        return indent() + "FunctionType\n";
    }

    @Override
    public String visitStructType(StructType node) {
        return indent() + "StructType\n";
    }

    @Override
    public String visitUnionType(UnionType node) {
        return indent() + "UnionType\n";
    }

    @Override
    public String visitEnumType(EnumType node) {
        return indent() + "EnumType\n";
    }

    @Override
    public String visitParameter(Parameter node) {
        return indent() + "Parameter: " + node.name + " : " + node.type + "\n";
    }

    @Override
    public String visitEnumMember(EnumMember node) {
        return indent() + "EnumMember\n";
    }

    @Override
    public String visitStructMember(StructMember node) {
        return indent() + "StructMember\n";
    }

    @Override
    public String visit(PackageDeclaration node) {
        return indent() + "PackageDeclaration: " + node.name + "\n";
    }

    @Override
    public String visit(NewExpression node) {
        StringBuilder sb = new StringBuilder();
        sb.append(indent()).append("NewExpression: ").append(node.typeName).append("\n");
        if (node.arguments != null && !node.arguments.isEmpty()) {
            indentLevel++;
            sb.append(indent()).append("Arguments:\n");
            indentLevel++;
            for (ASTNode arg : node.arguments) {
                sb.append(arg.accept(this));
            }
            indentLevel--;
            indentLevel--;
        }
        return sb.toString();
    }

    @Override
    public String visit(DeleteExpression node) {
        StringBuilder sb = new StringBuilder();
        sb.append(indent()).append("DeleteExpression\n");
        indentLevel++;
        sb.append(node.target.accept(this));
        indentLevel--;
        return sb.toString();
    }

    @Override
    public String visitVariableExpression(VariableExpression node) {
        return indent() + "VariableExpression: " + node.name + "\n";
    }

    @Override
    public String visitBinaryOverload(BinaryOverloadNode node) {
        StringBuilder sb = new StringBuilder();
        sb.append(indent()).append("BinaryOverloadNode: ").append(node.operator).append("\n");
        indentLevel++;
        sb.append(indent()).append("Operand1: ").append(node.operand1.name).append("\n");
        sb.append(indent()).append("Operand2: ").append(node.operand2.name).append("\n");
        sb.append(indent()).append("Code:\n");
        indentLevel++;
        sb.append(node.code.accept(this));
        indentLevel--;
        indentLevel--;
        return sb.toString();
    }

    @Override
    public String visitLambdaExpression(LambdaExpression node) {
        StringBuilder sb = new StringBuilder();
        sb.append(indent()).append("LambdaExpression\n");
        indentLevel++;
        sb.append(indent()).append("Parameters:\n");
        indentLevel++;
        for (Parameter param : node.parameters) {
            sb.append(printWithDecorators(param));
        }
        indentLevel--;
        sb.append(indent()).append("Body:\n");
        indentLevel++;
        sb.append(node.body.accept(this));
        indentLevel--;
        indentLevel--;
        return sb.toString();
    }
}
