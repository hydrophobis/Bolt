package hydro.bolt.ast;

import hydro.bolt.ast.decl.*;
import hydro.bolt.ast.expr.*;
import hydro.bolt.ast.misc.*;
import hydro.bolt.ast.statement.*;
import hydro.bolt.ast.type.*;
import hydro.bolt.ast.bolt.*;

// some nodes have their own visit methods, some dont and should just be overloaded of visit
public interface ASTVisitor<T> {
    T visitClassDeclaration(ClassDeclaration node);
    T visitInterfaceDeclaration(InterfaceDeclaration node);

    T visit(ImportDeclaration node);

    T visit(ImplDeclaration node);

    T visit(DecoratorNode node);

    T visitProgram(Program node);

    T visitFunctionDeclaration(FunctionDeclaration node);

    T visitVariableDeclaration(VariableDeclaration node);

    T visitStructDeclaration(StructDeclaration node);

    T visitUnionDeclaration(UnionDeclaration node);

    T visitEnumDeclaration(EnumDeclaration node);

    T visitTypedefDeclaration(TypedefDeclaration node);

    T visitIdentifier(Identifier node);

    T visitIntegerLiteral(IntegerLiteral node);

    T visitFloatLiteral(FloatLiteral node);

    T visitCharLiteral(CharLiteral node);

    T visitStringLiteral(StringLiteral node);

    T visitBinaryExpression(BinaryExpression node);

    T visitUnaryExpression(UnaryExpression node);

    T visitAssignmentExpression(AssignmentExpression node);

    T visitTernaryExpression(TernaryExpression node);

    T visitFunctionCall(FunctionCall node);

    T visitArrayAccess(ArrayAccess node);

    T visitMemberAccess(MemberAccess node);

    T visitPointerMemberAccess(PointerMemberAccess node);

    T visitSizeofExpression(SizeofExpression node);

    T visitCastExpression(CastExpression node);

    T visitInitializerList(InitializerList node);

    T visitBlock(Block node);

    T visitExpressionStatement(ExpressionStatement node);

    T visitIfStatement(IfStatement node);

    T visitWhileStatement(WhileStatement node);

    T visitForStatement(ForStatement node);

    T visitDoWhileStatement(DoWhileStatement node);

    T visitSwitchStatement(SwitchStatement node);

    T visitCaseStatement(CaseStatement node);

    T visitDefaultStatement(DefaultStatement node);

    T visitBreakStatement(BreakStatement node);

    T visitContinueStatement(ContinueStatement node);

    T visitReturnStatement(ReturnStatement node);

    T visitGotoStatement(GotoStatement node);

    T visitLabeledStatement(LabeledStatement node);

    T visitPrimitiveType(PrimitiveType node);

    T visitPointerType(PointerType node);

    T visitArrayType(ArrayType node);

    T visitFunctionType(FunctionType node);

    T visitStructType(StructType node);

    T visitUnionType(UnionType node);

    T visitEnumType(EnumType node);

    T visitParameter(Parameter node);

    T visitEnumMember(EnumMember node);

    T visitStructMember(StructMember node);

    T visit(PackageDeclaration node);

    T visit(NewExpression node);
    T visit(DeleteExpression node);

    T visitVariableExpression(VariableExpression variableExpression);

    T visit(RawCNode node);

    T visitUnaryOverload(UnaryOverloadNode overload);
    T visitBinaryOverload(BinaryOverloadNode overload);

    T visitLambdaExpression(LambdaExpression lambda);
}
