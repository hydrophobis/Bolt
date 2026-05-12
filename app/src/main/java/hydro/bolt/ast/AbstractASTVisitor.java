package hydro.bolt.ast;

import hydro.bolt.ast.decl.*;
import hydro.bolt.ast.expr.*;
import hydro.bolt.ast.misc.*;
import hydro.bolt.ast.statement.*;
import hydro.bolt.ast.type.*;
import hydro.bolt.ast.bolt.*;

// 300 lines of absolute boilerplate
public abstract class AbstractASTVisitor<T> implements ASTVisitor<T> {
    protected T defaultValue;

    public AbstractASTVisitor(T defaultValue) {
        this.defaultValue = defaultValue;
    }

    public AbstractASTVisitor() {
        this(null);
    }

    @Override
    public T visitProgram(Program node) {
        return defaultValue;
    }

    @Override
    public T visitFunctionDeclaration(FunctionDeclaration node) {
        return defaultValue;
    }

    @Override
    public T visitVariableDeclaration(VariableDeclaration node) {
        return defaultValue;
    }

    @Override
    public T visitStructDeclaration(StructDeclaration node) {
        return defaultValue;
    }

    @Override
    public T visitUnionDeclaration(UnionDeclaration node) {
        return defaultValue;
    }

    @Override
    public T visitEnumDeclaration(EnumDeclaration node) {
        return defaultValue;
    }

    @Override
    public T visitTypedefDeclaration(TypedefDeclaration node) {
        return defaultValue;
    }

    @Override
    public T visitIdentifier(Identifier node) {
        return defaultValue;
    }

    @Override
    public T visitIntegerLiteral(IntegerLiteral node) {
        return defaultValue;
    }

    @Override
    public T visitFloatLiteral(FloatLiteral node) {
        return defaultValue;
    }

    @Override
    public T visitCharLiteral(CharLiteral node) {
        return defaultValue;
    }

    @Override
    public T visitStringLiteral(StringLiteral node) {
        return defaultValue;
    }

    @Override
    public T visitBinaryExpression(BinaryExpression node) {
        return defaultValue;
    }

    @Override
    public T visitUnaryExpression(UnaryExpression node) {
        return defaultValue;
    }

    @Override
    public T visitAssignmentExpression(AssignmentExpression node) {
        return defaultValue;
    }

    @Override
    public T visitTernaryExpression(TernaryExpression node) {
        return defaultValue;
    }

    @Override
    public T visitFunctionCall(FunctionCall node) {
        return defaultValue;
    }

    @Override
    public T visitArrayAccess(ArrayAccess node) {
        return defaultValue;
    }

    @Override
    public T visitMemberAccess(MemberAccess node) {
        return defaultValue;
    }

    @Override
    public T visitPointerMemberAccess(PointerMemberAccess node) {
        return defaultValue;
    }

    @Override
    public T visitSizeofExpression(SizeofExpression node) {
        return defaultValue;
    }

    @Override
    public T visitCastExpression(CastExpression node) {
        return defaultValue;
    }

    @Override
    public T visitInitializerList(InitializerList node) {
        return defaultValue;
    }

    @Override
    public T visitBlock(Block node) {
        return defaultValue;
    }

    @Override
    public T visitExpressionStatement(ExpressionStatement node) {
        return defaultValue;
    }

    @Override
    public T visitIfStatement(IfStatement node) {
        return defaultValue;
    }

    @Override
    public T visitWhileStatement(WhileStatement node) {
        return defaultValue;
    }

    @Override
    public T visitForStatement(ForStatement node) {
        return defaultValue;
    }

    @Override
    public T visitDoWhileStatement(DoWhileStatement node) {
        return defaultValue;
    }

    @Override
    public T visitSwitchStatement(SwitchStatement node) {
        return defaultValue;
    }

    @Override
    public T visitCaseStatement(CaseStatement node) {
        return defaultValue;
    }

    @Override
    public T visitDefaultStatement(DefaultStatement node) {
        return defaultValue;
    }

    @Override
    public T visitBreakStatement(BreakStatement node) {
        return defaultValue;
    }

    @Override
    public T visitContinueStatement(ContinueStatement node) {
        return defaultValue;
    }

    @Override
    public T visitReturnStatement(ReturnStatement node) {
        return defaultValue;
    }

    @Override
    public T visitGotoStatement(GotoStatement node) {
        return defaultValue;
    }

    @Override
    public T visitLabeledStatement(LabeledStatement node) {
        return defaultValue;
    }

    @Override
    public T visitPrimitiveType(PrimitiveType node) {
        return defaultValue;
    }

    @Override
    public T visitPointerType(PointerType node) {
        return defaultValue;
    }

    @Override
    public T visitArrayType(ArrayType node) {
        return defaultValue;
    }

    @Override
    public T visitFunctionType(FunctionType node) {
        return defaultValue;
    }

    @Override
    public T visitStructType(StructType node) {
        return defaultValue;
    }

    @Override
    public T visitUnionType(UnionType node) {
        return defaultValue;
    }

    @Override
    public T visitEnumType(EnumType node) {
        return defaultValue;
    }

    @Override
    public T visitParameter(Parameter node) {
        return defaultValue;
    }

    @Override
    public T visitEnumMember(EnumMember node) {
        return defaultValue;
    }

    @Override
    public T visitStructMember(StructMember node) {
        return defaultValue;
    }

    @Override
    public T visitClassDeclaration(hydro.bolt.ast.bolt.ClassDeclaration node) {
        return defaultValue;
    }

    @Override
    public T visitInterfaceDeclaration(hydro.bolt.ast.bolt.InterfaceDeclaration node) {
        return defaultValue;
    }

    @Override
    public T visit(ImportDeclaration node) {
        return defaultValue;
    }

    @Override
    public T visit(ImplDeclaration node) {
        return defaultValue;
    }

    @Override
    public T visit(DecoratorNode node) {
        return defaultValue;
    }

    @Override
    public T visit(PackageDeclaration node) {
        return defaultValue;
    }

    @Override
    public T visit(NewExpression node) {
        return defaultValue;
    }

    @Override
    public T visit(DeleteExpression node) {
        return defaultValue;
    }

    @Override
    public T visitVariableExpression(VariableExpression variableExpression) {
        return defaultValue;
    }

    @Override
    public T visitUnaryOverload(UnaryOverloadNode overload) {
        return defaultValue;
    }

    
    @Override
    public T visitBinaryOverload(BinaryOverloadNode overload) {
        return defaultValue;
    }

    @Override
    public T visitLambdaExpression(LambdaExpression lambda) {
        return defaultValue;
    }

    @Override
    public T visit(RawCNode node) {
        return defaultValue;
    }
}
