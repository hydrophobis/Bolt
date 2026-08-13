package hydro.bolt.parser;

import java.util.*;

import hydro.bolt.tokens.*;
import hydro.bolt.ast.*;
import hydro.bolt.ast.type.*;
import hydro.bolt.ast.decl.*;
import hydro.bolt.ast.expr.*;
import hydro.bolt.ast.misc.ArrayAccess;
import hydro.bolt.ast.misc.EnumMember;
import hydro.bolt.ast.misc.StructMember;
import hydro.bolt.ast.misc.CharLiteral;
import hydro.bolt.ast.misc.FloatLiteral;
import hydro.bolt.ast.misc.IntegerLiteral;
import hydro.bolt.ast.misc.StringLiteral;
import hydro.bolt.ast.misc.Block;
import hydro.bolt.ast.misc.FunctionCall;
import hydro.bolt.ast.misc.Identifier;
import hydro.bolt.ast.misc.MemberAccess;
import hydro.bolt.ast.misc.PointerMemberAccess;
import hydro.bolt.ast.misc.Parameter;
import hydro.bolt.ast.statement.*;
import hydro.bolt.ast.bolt.*;
import hydro.bolt.ast.Visibility;

// warcrime of a parser
public class Parser {
    List<Token> tokens;
    int position = 0;
    ASTTree ast = new ASTTree();
    public SymbolTree symbols = new SymbolTree();
    public String currentPackage = null;
    public List<String> imports = new ArrayList<>();
    public hydro.bolt.Config config = new hydro.bolt.Config();
    public ErrorReporter reporter = new ErrorReporter();

    private static final Set<TokenType> BINARY_OPERATORS = Set.of(
        TokenType.PLUS, TokenType.MINUS, TokenType.STAR, TokenType.SLASH, TokenType.PERCENT,
        TokenType.EQUAL, TokenType.NOT_EQUAL, TokenType.LESS, TokenType.LESS_EQUAL,
        TokenType.GREATER, TokenType.GREATER_EQUAL,
        TokenType.LOGICAL_AND, TokenType.LOGICAL_OR,
        TokenType.BIT_AND, TokenType.BIT_OR, TokenType.BIT_XOR,
        TokenType.SHIFT_LEFT, TokenType.SHIFT_RIGHT,
        TokenType.ASSIGN,
        TokenType.PLUS_ASSIGN,
        TokenType.MINUS_ASSIGN,
        TokenType.STAR_ASSIGN,
        TokenType.SLASH_ASSIGN,
        TokenType.PERCENT_ASSIGN,
        TokenType.BIT_AND_ASSIGN,
        TokenType.BIT_OR_ASSIGN,
        TokenType.BIT_XOR_ASSIGN,
        TokenType.SHIFT_LEFT_ASSIGN,
        TokenType.SHIFT_RIGHT_ASSIGN,
        TokenType.RBRACKET
    );

    private static final Map<TokenType, Integer> OPERATOR_PRECEDENCE = Map.ofEntries(
        Map.entry(TokenType.ASSIGN, 5),
        Map.entry(TokenType.PLUS_ASSIGN, 5),
        Map.entry(TokenType.MINUS_ASSIGN, 5),
        Map.entry(TokenType.STAR_ASSIGN, 5),
        Map.entry(TokenType.SLASH_ASSIGN, 5),
        Map.entry(TokenType.PERCENT_ASSIGN, 5),
        Map.entry(TokenType.BIT_AND_ASSIGN, 5),
        Map.entry(TokenType.BIT_OR_ASSIGN, 5),
        Map.entry(TokenType.BIT_XOR_ASSIGN, 5),
        Map.entry(TokenType.SHIFT_LEFT_ASSIGN, 5),
        Map.entry(TokenType.SHIFT_RIGHT_ASSIGN, 5),
        Map.entry(TokenType.LOGICAL_OR, 10),
        Map.entry(TokenType.LOGICAL_AND, 20),
        Map.entry(TokenType.EQUAL, 30),
        Map.entry(TokenType.NOT_EQUAL, 30),
        Map.entry(TokenType.LESS, 40),
        Map.entry(TokenType.LESS_EQUAL, 40),
        Map.entry(TokenType.GREATER, 40),
        Map.entry(TokenType.GREATER_EQUAL, 40),
        Map.entry(TokenType.SHIFT_LEFT, 45),
        Map.entry(TokenType.SHIFT_RIGHT, 45),
        Map.entry(TokenType.BIT_AND, 27),
        Map.entry(TokenType.BIT_XOR, 26),
        Map.entry(TokenType.BIT_OR, 25),
        Map.entry(TokenType.PLUS, 50),
        Map.entry(TokenType.MINUS, 50),
        Map.entry(TokenType.STAR, 60),
        Map.entry(TokenType.SLASH, 60),
        Map.entry(TokenType.PERCENT, 60),
        Map.entry(TokenType.RBRACKET, 70)
    );

    private static final Map<String, String> OPERATOR_NAMES = Map.ofEntries(
        Map.entry("+", "plus"),
        Map.entry("-", "minus"),
        Map.entry("*", "mul"),
        Map.entry("/", "div"),
        Map.entry("%", "mod"),
        Map.entry("==", "eq"),
        Map.entry("!=", "neq"),
        Map.entry("<", "lt"),
        Map.entry("<=", "lte"),
        Map.entry(">", "gt"),
        Map.entry(">=", "gte"),
        Map.entry("&&", "land"),
        Map.entry("||", "lor"),
        Map.entry("!", "lnot"),
        Map.entry("&", "band"),
        Map.entry("|", "bor"),
        Map.entry("^", "bxor"),
        Map.entry("~", "bnot"),
        Map.entry("<<", "shl"),
        Map.entry(">>", "shr"),
        Map.entry("[", "lbracket"),
        Map.entry("]", "rbracket"),
        Map.entry("(", "lparen"),
        Map.entry(")", "rparen")
    );

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    public Parser(List<Token> tokens, hydro.bolt.Config config) {
        this.tokens = tokens;
        this.config = config;
    }

    public ASTTree parse() {
        while (!isAtEnd()) {
            try {
                ASTNode node = parseDeclaration();
                while (!pendingNodes.isEmpty()) {
                    ast.add(pendingNodes.remove(0));
                }
                if (node != null) {
                    ast.add(node);
                }
            } catch (ParseError e) {
                pendingNodes.clear();
                synchronize();
            }
        }
        return ast;
    }

    private static class ParseError extends RuntimeException {}

    private List<DecoratorNode> parseDecorators() {
        List<DecoratorNode> decorators = new ArrayList<>();
        while (check(TokenType.AT) || (check(TokenType.LOGICAL_NOT) && peekNext() != null && peekNext().type == TokenType.AT)) {
            boolean negated = match(TokenType.LOGICAL_NOT);
            DecoratorNode dec = parseDecorator();
            dec.isNegated = negated;
            decorators.add(dec);
        }
        return decorators;
    }

    private ASTNode parseDeclaration() {
        List<DecoratorNode> decorators = parseDecorators();
        ASTNode node = parseTopLevel();
        if (node != null) {
            node.decorators.addAll(decorators);
        }
        return node;
    }

    private boolean shouldMangle(ASTNode node) {
        boolean mangleDefault = config.getBoolean("mangle");
        for (DecoratorNode dec : node.decorators) {
            if (dec.name.equals("mangle")) {
                return !dec.isNegated;
            }
        }
        return mangleDefault;
    }

    private boolean isTypeStart(Token t) {
        if (t == null) return false;
        return t.type == TokenType.KEYWORD || t.type == TokenType.IDENTIFIER || t.type == TokenType.LBRACKET;
    }

    private ASTNode parseTopLevel() {
        if (match(TokenType.SEMICOLON)) return null;
        if (matchKeyword("operator")) return parseOperatorOverload();
        if (matchKeyword("package")) return parsePackage();
        if (matchKeyword("import")) return parseImport();
        if (matchKeyword("class")) return parseClass(Visibility.PRIVATE);
        if (matchKeyword("struct")) return parseClass(Visibility.PUBLIC);
        if (matchKeyword("interface")) return parseInterface();
        if (matchKeyword("impl")) return parseImpl();
        if (matchKeyword("enum")) return parseEnum();
        if (matchKeyword("union")) return parseUnion();
        if (matchKeyword("typedef")) return parseTypedef();

        // Visibility block: public { ... } / private { ... }
        if (checkKeyword("public") || checkKeyword("private")) {
            Token visToken = peek();
            Visibility vis = visToken.lexeme.equals("public") ? Visibility.PUBLIC : Visibility.PRIVATE;
            if (peekNext() != null && peekNext().type == TokenType.LBRACE) {
                consume();
                consume(TokenType.LBRACE);
                List<ASTNode> visNodes = new ArrayList<>();
                while (!check(TokenType.RBRACE) && !isAtEnd()) {
                    ASTNode decl = parseDeclaration();
                    if (decl != null) {
                        setVisibility(decl, vis);
                        visNodes.add(decl);
                    }
                }
                consume(TokenType.RBRACE);
                pendingNodes.addAll(visNodes);
                return null;
            } else {
                consume(); // consume public/private
                List<DecoratorNode> decorators = parseDecorators();
                ASTNode node = parseTopLevelWithoutVisibility();
                if (node != null) {
                    node.decorators.addAll(decorators);
                    setVisibility(node, vis);
                }
                return node;
            }
        }

        return parseTopLevelWithoutVisibility();
    }

    private List<ASTNode> pendingNodes = new ArrayList<>();
    private List<ASTNode> pendingStatements = new ArrayList<>();

    /** Apply visibility to a node if it supports it. */
    private void setVisibility(ASTNode node, Visibility vis) {
        if (node instanceof FunctionDeclaration func) {
            func.visibility = vis;
        } else if (node instanceof VariableDeclaration var) {
            var.visibility = vis;
        }
    }

    private ASTNode parseTopLevelWithoutVisibility() {
        if (isTypeStart(peek()) || check(TokenType.LPAREN)) {
            int saved = position;
            
            // Check for explicit-return-type function: Type Name (...)
            boolean looksLikeExplicitFunction = false;
            try {
                ASTNode typeNode = parseType();
                if (check(TokenType.IDENTIFIER)) {
                    Token nameToken = consume(TokenType.IDENTIFIER);
                    if (check(TokenType.LPAREN) || check(TokenType.LESS)) {
                        FunctionDeclaration func = parseFunctionDeclaration(typeNode, nameToken);
                        Symbol sym = new Symbol(func.name, Symbol.Kind.FUNCTION, func.returnType.toString());
                        sym.mangle = true;
                        symbols.put(func.name, sym);
                        return func;
                    }
                }
            } catch (Exception e) {}
            position = saved;

            // Check for implicit return type function: Name (...)
            if (check(TokenType.IDENTIFIER)) {
                Token nameToken = peek();
                if (peekNext() != null && peekNext().type == TokenType.LPAREN) {
                    consume(TokenType.IDENTIFIER);
                    FunctionDeclaration func = parseFunctionDeclaration(new PrimitiveType("void"), nameToken);
                    Symbol sym = new Symbol(func.name, Symbol.Kind.FUNCTION, "void");
                    sym.mangle = true;
                    symbols.put(func.name, sym);
                    return func;
                }
            }
            position = saved;

            // Check for standard declaration: Type Name; or Type Name = ...
            try {
                ASTNode typeNode = parseType();
                if (check(TokenType.IDENTIFIER)) {
                    if (checkNext(TokenType.SEMICOLON) || checkNext(TokenType.ASSIGN) || checkNext(TokenType.LBRACKET)) {
                        position = saved;
                        return parseStatement();
                    }
                }
            } catch (Exception e) {}
            position = saved;
        }

        // if its a decorator, don't let greedy parseStandardC swallow it, loop will handle it
        if (check(TokenType.AT)) return null;

        throw error(peek(), "Unexpected token at start of statement");
    }
    
    private boolean checkNext(TokenType type) {
        Token n = peekNext();
        return n != null && n.type == type;
    }

    private SwitchStatement parseSwitchStatement() {
        Token switchToken = consume(TokenType.KEYWORD); // switch
        consume(TokenType.LPAREN);
        ASTNode condition = parseExpression();
        consume(TokenType.RPAREN);
        consume(TokenType.LBRACE);

        List<ASTNode> cases = new ArrayList<>();

        while (!check(TokenType.RBRACE) && !isAtEnd()) {
            if (check(TokenType.KEYWORD) && peek().lexeme.equals("case")) {
                Token caseToken = consume(TokenType.KEYWORD);
                ASTNode caseValue = parseExpression();
                consume(TokenType.COLON);

                List<ASTNode> caseBody = new ArrayList<>();
                while (!check(TokenType.KEYWORD) && !check(TokenType.RBRACE) && !isAtEnd()) {
                    if (check(TokenType.KEYWORD) && peek().lexeme.equals("case")) break;
                    if (check(TokenType.KEYWORD) && peek().lexeme.equals("default")) break;
                    ASTNode stmt = parseStatement();
                    if (stmt != null) {
                        caseBody.add(stmt);
                    }
                }

                CaseStatement caseStmt = new CaseStatement(caseValue, caseBody);
                caseStmt.line = caseToken.line;
                caseStmt.column = caseToken.column;
                cases.add(caseStmt);
            } else if (check(TokenType.KEYWORD) && peek().lexeme.equals("default")) {
                Token defaultToken = consume(TokenType.KEYWORD);
                consume(TokenType.COLON);

                List<ASTNode> defaultBody = new ArrayList<>();
                while (!check(TokenType.KEYWORD) && !check(TokenType.RBRACE) && !isAtEnd()) {
                    if (check(TokenType.KEYWORD) && peek().lexeme.equals("case")) break;
                    if (check(TokenType.KEYWORD) && peek().lexeme.equals("default")) break;
                    ASTNode stmt = parseStatement();
                    if (stmt != null) {
                        defaultBody.add(stmt);
                    }
                }

                DefaultStatement defaultStmt = new DefaultStatement(defaultBody);
                defaultStmt.line = defaultToken.line;
                defaultStmt.column = defaultToken.column;
                cases.add(defaultStmt);
            } else {
                // Skip unknown tokens
                consume();
            }
        }

        consume(TokenType.RBRACE);

        Block body = new Block(cases);
        body.line = switchToken.line;
        body.column = switchToken.column;

        return new SwitchStatement(condition, body);
    }

    private FunctionDeclaration parseFunctionDeclaration(ASTNode type, Token name) {
        List<String> genericParams = new ArrayList<>();
        if (check(TokenType.LESS)) {
            consume(TokenType.LESS);
            do {
                genericParams.add(consume(TokenType.IDENTIFIER).lexeme);
            } while (match(TokenType.COMMA));
            consume(TokenType.GREATER);
        }

        consume(TokenType.LPAREN);
        List<Parameter> parameters = new ArrayList<>();
        if (!check(TokenType.RPAREN)) {
            do {
                parameters.add(parseParameter());
            } while (match(TokenType.COMMA));
        }
        consume(TokenType.RPAREN);
        
        ASTNode body = null;
        if (check(TokenType.LBRACE)) {
            body = parseStatement();
        } else if (match(TokenType.ASSIGN)) {
             body = parseExpression();
             consume(TokenType.SEMICOLON);
        } else {
             consume(TokenType.SEMICOLON);
        }

        FunctionDeclaration func = new FunctionDeclaration(type, name.lexeme, parameters, body, false);
        func.genericParams = genericParams;
        func.line = name.line;
        func.column = name.column;
        return func;
    }


    private ASTNode parseExpression() {
        return parseTernary(null);
    }

    private ASTNode parseExpression(TokenType stopAt) {
        return parseTernary(stopAt);
    }

    private ASTNode parseTernary(TokenType stopAt) {
        ASTNode condition = parseBinary(0, stopAt);
        if (!check(TokenType.QUESTION)) {
            return condition;
        }

        Token question = consume(TokenType.QUESTION);
        ASTNode whenTrue = parseTernary(TokenType.COLON);
        consume(TokenType.COLON);
        ASTNode whenFalse = parseTernary(stopAt);

        TernaryExpression ternary = new TernaryExpression(condition, whenTrue, whenFalse);
        ternary.line = question.line;
        ternary.column = question.column;
        return ternary;
    }

    private ASTNode parseBinary(int precedence, TokenType stopAt) {
        ASTNode left = parseUnary();

        while (true) {
            Token op = peek();
            if (op == null || !isBinaryOp(op.type)) break;
            if (stopAt != null && op.type == stopAt) break;

            int opPrecedence = getPrecedence(op.type);
            if (opPrecedence < precedence) break;

            consume();
            ASTNode right = parseBinary(opPrecedence + 1, stopAt);
            BinaryExpression bin = new BinaryExpression(left, op.lexeme, right);
            bin.line = op.line;
            bin.column = op.column;
            left = bin;
        }

        return left;
    }

    private boolean isBinaryOp(TokenType type) {
        return BINARY_OPERATORS.contains(type);
    }

    private int getPrecedence(TokenType type) {
        Integer result = OPERATOR_PRECEDENCE.get(type);
        return result != null ? result : 0;
    }

    private ASTNode parseUnary() {
        if (check(TokenType.MINUS) || check(TokenType.LOGICAL_NOT) ||
            check(TokenType.BIT_NOT) || check(TokenType.INCREMENT) ||
            check(TokenType.DECREMENT) || check(TokenType.PLUS) ||
            check(TokenType.STAR) || check(TokenType.BIT_AND)) {
            Token op = consume();
            ASTNode operand = parseUnary();
            UnaryExpression ue = new UnaryExpression(op.lexeme, operand, true);
            ue.line = op.line;
            ue.column = op.column;
            return ue;
        }

        return parsePostfix();
    }

    private ASTNode parsePostfix() {
        ASTNode expr = parsePrimary();

        while (true) {
            if (match(TokenType.DOT)) {
                Token member = consume(TokenType.IDENTIFIER);
                MemberAccess ma = new MemberAccess(expr, member.lexeme);
                ma.line = member.line; ma.column = member.column;
                expr = ma;
            } else if (match(TokenType.ARROW)) {
                Token member = consume(TokenType.IDENTIFIER);
                PointerMemberAccess pma = new PointerMemberAccess(expr, member.lexeme);
                pma.line = member.line; pma.column = member.column;
                expr = pma;
            } else if (match(TokenType.LPAREN)) {
                Token lp = tokens.get(position - 1);
                List<ASTNode> args = new ArrayList<>();
                
                if (!check(TokenType.RPAREN)) {
                    do {
                        args.add(parseExpression());
                    } while (match(TokenType.COMMA));
                }
                
                consume(TokenType.RPAREN);
                FunctionCall fc = new FunctionCall(expr, args);
                fc.line = lp.line; fc.column = lp.column;
                expr = fc;
            } else if (match(TokenType.LBRACKET)) {
                ASTNode index = parseExpression(TokenType.RBRACKET);
                consume(TokenType.RBRACKET);
                expr = new ArrayAccess(expr, index);
            } else if (check(TokenType.INCREMENT) || check(TokenType.DECREMENT)) {
                Token op = consume();
                expr = new UnaryExpression(op.lexeme, expr, false);
            } else {
                break;
            }
        }

        return expr;
    }

    private Identifier parseIdentifier() {
        Token t = consume(TokenType.IDENTIFIER);
        Identifier id = new Identifier(t.lexeme, t.line, t.column);
        if (check(TokenType.LESS) && isTypeStart(peekNext()) && looksLikeGenericArgs()) {
            consume(TokenType.LESS);
            do {
                id.genericArguments.add(parseType());
            } while (match(TokenType.COMMA));
            consume(TokenType.GREATER);
        }
        return id;
    }

    private static final Set<String> TYPE_SPECIFIERS = Set.of(
        "unsigned", "signed", "long", "short", "int", "char",
        "float", "double", "void", "const", "volatile", "register"
    );

    private static final Set<String> TAGGED_TYPES = Set.of("struct", "union", "enum");

    private String readTypeSpecifiers(Token first) {
        StringBuilder name = new StringBuilder(first.lexeme);

        if (TAGGED_TYPES.contains(first.lexeme) && check(TokenType.IDENTIFIER)) {
            name.append(" ").append(consume().lexeme);
            return name.toString();
        }

        if (!TYPE_SPECIFIERS.contains(first.lexeme)) {
            return name.toString();
        }

        while (check(TokenType.KEYWORD) && TYPE_SPECIFIERS.contains(peek().lexeme)) {
            name.append(" ").append(consume().lexeme);
        }
        return name.toString();
    }

    private Parameter parseParameter() {
        ASTNode paramType = parseType();

        if (check(TokenType.LPAREN) && peekNext() != null && peekNext().type == TokenType.STAR) {
            consume(TokenType.LPAREN);
            consume(TokenType.STAR);
            Token fnName = consume(TokenType.IDENTIFIER);
            consume(TokenType.RPAREN);

            consume(TokenType.LPAREN);
            List<ASTNode> argTypes = new ArrayList<>();
            if (!check(TokenType.RPAREN)) {
                do {
                    argTypes.add(parseType());
                    if (check(TokenType.IDENTIFIER)) consume();
                } while (match(TokenType.COMMA));
            }
            consume(TokenType.RPAREN);

            FunctionType fnType = new FunctionType(paramType, argTypes, fnName.line, fnName.column);
            return new Parameter(fnType, fnName.lexeme, fnName.line, fnName.column);
        }

        Token paramName = consume(TokenType.IDENTIFIER);
        paramType = applyArraySuffix(paramType);
        return new Parameter(paramType, paramName.lexeme, paramName.line, paramName.column);
    }

    private ASTNode parseType() {
        ASTNode type;
        if (match(TokenType.LBRACKET)) {
            ASTNode elementType = parseType();
            consume(TokenType.SEMICOLON);
            ASTNode size = parseExpression(TokenType.RBRACKET);
            consume(TokenType.RBRACKET);
            type = new ArrayType(elementType, size);
        } else {
            Token t = consume();
            Identifier id = new Identifier(readTypeSpecifiers(t));
            id.line = t.line;
            id.column = t.column;
            if (check(TokenType.LESS) && isTypeStart(peekNext()) && looksLikeGenericArgs()) {
                consume(TokenType.LESS);
                do {
                    id.genericArguments.add(parseType());
                } while (match(TokenType.COMMA));
                consume(TokenType.GREATER);
            }
            type = id;
        }

        while (match(TokenType.STAR)) {
            type = new PointerType(type);
        }
        return type;
    }

    // Scans ahead from the current < to see if there's a matching > before
    // any operator that would indicate this is a comparison, not a generic list
    private boolean looksLikeGenericArgs() {
        int depth = 0;
        for (int i = position; i < tokens.size(); i++) {
            TokenType tt = tokens.get(i).type;
            if (tt == TokenType.LESS) depth++;
            else if (tt == TokenType.GREATER) {
                depth--;
                if (depth == 0) return true;
            } else if (tt == TokenType.SEMICOLON || tt == TokenType.LBRACE ||
                       tt == TokenType.RBRACE || tt == TokenType.EOF ||
                       tt == TokenType.LOGICAL_AND || tt == TokenType.LOGICAL_OR ||
                       tt == TokenType.EQUAL || tt == TokenType.NOT_EQUAL ||
                       tt == TokenType.LESS_EQUAL || tt == TokenType.GREATER_EQUAL ||
                       tt == TokenType.PLUS || tt == TokenType.MINUS ||
                       tt == TokenType.SLASH || tt == TokenType.PERCENT ||
                       tt == TokenType.ASSIGN) {
                return false;
            }
        }
        return false;
    }

    private boolean looksLikeCast() {
        if (!isTypeStart(peek())) return false;

        int i = position;
        if (tokens.get(i).type == TokenType.LBRACKET) return false;
        i++;

        while (i < tokens.size() && tokens.get(i).type == TokenType.STAR) {
            i++;
        }
        if (i >= tokens.size() || tokens.get(i).type != TokenType.RPAREN) return false;

        if (i == position + 1 && tokens.get(position).type == TokenType.IDENTIFIER) {
            String name = tokens.get(position).lexeme;
            if (!symbols.contains(name) && !symbols.contains(resolveName(name))) return false;
        }

        int after = i + 1;
        if (after >= tokens.size()) return false;
        TokenType next = tokens.get(after).type;
        return next == TokenType.IDENTIFIER || next == TokenType.KEYWORD
            || next == TokenType.INTEGER_LITERAL || next == TokenType.FLOAT_LITERAL
            || next == TokenType.CHAR_LITERAL || next == TokenType.STRING_LITERAL
            || next == TokenType.LPAREN || next == TokenType.STAR
            || next == TokenType.BIT_AND || next == TokenType.MINUS;
    }

    private String resolveName(String name) {
        return currentPackage != null ? currentPackage + "." + name : name;
    }

    private ASTNode parsePrimary() {
        Token t = peek();

        switch (t.type) {
            case IDENTIFIER:
                return parseIdentifier();

            case KEYWORD:
                if (t.lexeme.equals("new")) {
                    Token newToken = consume();
                    ASTNode typeNode = parseType();
                    List<ASTNode> args = new ArrayList<>();
                    if (match(TokenType.LPAREN)) {
                        if (!check(TokenType.RPAREN)) {
                            do {
                                args.add(parseExpression());
                            } while (match(TokenType.COMMA));
                        }
                        consume(TokenType.RPAREN);
                    }
                    NewExpression ne = new NewExpression(typeNode.toString(), args);
                    ne.line = newToken.line;
                    ne.column = newToken.column;
                    return ne;
                }
                if (t.lexeme.equals("delete")) {
                    consume();
                    return new DeleteExpression(parseExpression());
                }
                if (t.lexeme.equals("true") || t.lexeme.equals("false")) {
                    consume();
                    return new IntegerLiteral(t.lexeme.equals("true") ? "1" : "0", t.line, t.column);
                }
                if (t.lexeme.equals("switch")) {
                    return parseSwitchStatement();
                }
                if (t.lexeme.equals("fn")) {
                    return parseLambda();
                }
                consume();
                Identifier keywordId = new Identifier(t.lexeme);
                keywordId.line = t.line; keywordId.column = t.column;
                return keywordId;

            case INTEGER_LITERAL:
                consume();
                return new IntegerLiteral(t.lexeme, t.line, t.column);

            case FLOAT_LITERAL:
                consume();
                return new FloatLiteral(t.lexeme, t.line, t.column);

            case STRING_LITERAL:
                consume();
                return new StringLiteral(t.lexeme, t.line, t.column);

            case LPAREN: {
                consume(TokenType.LPAREN);
                // Check for C cast: (type)expr
                if (looksLikeCast()) {
                    ASTNode typeNode = parseType();
                    consume(TokenType.RPAREN);
                    ASTNode target = parseUnary();
                    return new UnaryExpression("( " + typeNode.toString() + " )", target, true);
                }
                ASTNode expr = parseExpression();
                consume(TokenType.RPAREN);
                return expr;
            }

            case CHAR_LITERAL:
                consume();
                return new CharLiteral(t.lexeme, t.line, t.column);

            default:
                throw error(t, "Unexpected token in expression");
        }
    }

    private ASTNode parseLambda() {
        Token fnToken = consume(TokenType.KEYWORD); // consume 'fn'
        List<Parameter> parameters = new ArrayList<>();

        // Parse parameters
        consume(TokenType.LPAREN);
        if (!check(TokenType.RPAREN)) {
            do {
                ASTNode paramType = parseType();
                Token paramName = consume(TokenType.IDENTIFIER);
                Parameter param = new Parameter(paramType, paramName.lexeme);
                param.line = paramName.line;
                param.column = paramName.column;
                parameters.add(param);
            } while (match(TokenType.COMMA));
        }
        consume(TokenType.RPAREN);

        // Parse body - either a block or a single expression
        ASTNode body;
        if (check(TokenType.LBRACE)) {
            Token t = consume(TokenType.LBRACE);
            body = new Block(parseBlockStatements());
            body.line = t.line;
            body.column = t.column;
        } else {
            body = parseExpression();
        }

        LambdaExpression lambda = new LambdaExpression(parameters, body);
        lambda.line = fnToken.line;
        lambda.column = fnToken.column;
        return lambda;
    }

    private ASTNode parseStatement() {
        if (checkKeyword("return")) {
            Token t = consume();
            ASTNode expr = null;
            if (!check(TokenType.SEMICOLON)) {
                expr = parseExpression();
            }
            consume(TokenType.SEMICOLON);
            ReturnStatement rs = new ReturnStatement(expr);
            rs.line = t.line; rs.column = t.column;
            return rs;
        }

        if (checkKeyword("if")) {
            Token t = consume();
            consume(TokenType.LPAREN);
            ASTNode condition = parseExpression();
            consume(TokenType.RPAREN);
            ASTNode thenBranch = parseStatement();
            ASTNode elseBranch = null;
            if (matchKeyword("else")) {
                elseBranch = parseStatement();
            }
            IfStatement is = new IfStatement(condition, thenBranch, elseBranch);
            is.line = t.line; is.column = t.column;
            return is;
        }

        if (checkKeyword("while")) {
            Token t = consume();
            consume(TokenType.LPAREN);
            ASTNode condition = parseExpression();
            consume(TokenType.RPAREN);
            ASTNode body = parseStatement();
            WhileStatement ws = new WhileStatement(condition, body);
            ws.line = t.line; ws.column = t.column;
            return ws;
        }

        if (checkKeyword("for")) {
            return parseFor();
        }

        if (checkKeyword("switch")) {
            return parseSwitch();
        }

        if (checkKeyword("do")) {
            Token t = consume();
            ASTNode body = parseStatement();
            if (!matchKeyword("while")) {
                throw error(peek(), "Expected 'while' after 'do' body");
            }
            consume(TokenType.LPAREN);
            ASTNode condition = parseExpression();
            consume(TokenType.RPAREN);
            consume(TokenType.SEMICOLON);
            DoWhileStatement dw = new DoWhileStatement(body, condition);
            dw.line = t.line; dw.column = t.column;
            return dw;
        }

        if (checkKeyword("break")) {
            Token t = consume();
            consume(TokenType.SEMICOLON);
            return new BreakStatement(t.line, t.column);
        }

        if (checkKeyword("continue")) {
            Token t = consume();
            consume(TokenType.SEMICOLON);
            return new ContinueStatement(t.line, t.column);
        }

        if (checkKeyword("goto")) {
            Token t = consume();
            Token label = consume(TokenType.IDENTIFIER);
            consume(TokenType.SEMICOLON);
            return new GotoStatement(label.lexeme, t.line, t.column);
        }

        if (check(TokenType.IDENTIFIER) && peekNext() != null
                && peekNext().type == TokenType.COLON) {
            Token label = consume(TokenType.IDENTIFIER);
            consume(TokenType.COLON);
            ASTNode labelled = check(TokenType.RBRACE) ? null : parseStatement();
            return new LabeledStatement(label.lexeme, labelled, label.line, label.column);
        }

        if (check(TokenType.SEMICOLON)) {
            consume(TokenType.SEMICOLON);
            return null;
        }

        if (check(TokenType.LBRACE)) {
            Token t = consume(TokenType.LBRACE);
            Block b = new Block(parseBlockStatements());
            b.line = t.line; b.column = t.column;
            return b;
        }

        // is this a variable decl or expression?
        if (isTypeStart(peek()) || check(TokenType.LPAREN) || startsPrefixExpression()) {
            int saved = position;
            if (peek().type == TokenType.KEYWORD && (peek().lexeme.equals("return") || peek().lexeme.equals("if") ||
                peek().lexeme.equals("while") || peek().lexeme.equals("new") ||
                peek().lexeme.equals("delete") || peek().lexeme.equals("package") ||
                peek().lexeme.equals("import") || peek().lexeme.equals("class") ||
                peek().lexeme.equals("impl") || peek().lexeme.equals("operator"))) {
            } else if (!looksLikeArrayElementAccess() && !startsPrefixExpression()) {
                ASTNode typeNode = parseType();

                if (check(TokenType.IDENTIFIER)) {
                    Token afterName = peekNext();

                    if (afterName != null &&
                        (afterName.type == TokenType.SEMICOLON || afterName.type == TokenType.ASSIGN
                         || afterName.type == TokenType.COMMA || afterName.type == TokenType.LBRACKET)) {
                        VariableDeclaration first = null;
                        do {
                            Token nameToken = consume(TokenType.IDENTIFIER);
                            ASTNode declType = applyArraySuffix(typeNode);
                            ASTNode initializer = null;
                            if (match(TokenType.ASSIGN)) {
                                initializer = parseExpression();
                            }
                            VariableDeclaration vd = new VariableDeclaration(
                                declType,
                                nameToken.lexeme,
                                initializer,
                                false,
                                false
                            );
                            vd.line = nameToken.line;
                            vd.column = nameToken.column;
                            if (first == null) {
                                first = vd;
                            } else {
                                pendingStatements.add(vd);
                            }
                        } while (match(TokenType.COMMA) && check(TokenType.IDENTIFIER));
                        consume(TokenType.SEMICOLON);
                        return first;
                    }
                }
            }

            // backtrack and try expression
            position = saved;

            try {
                ASTNode expr = parseExpression();
                if (check(TokenType.SEMICOLON)) {
                    consume(TokenType.SEMICOLON);
                    ExpressionStatement es = new ExpressionStatement(expr);
                    es.line = expr.line; es.column = expr.column;
                    return es;
                }
                // if it looks like an expression but has no semicolon, it might be standard C or something else.
                // reset position to allow other parsers to try (and honestly probably fail)
                position = saved;
            } catch (ParseError e) {
                position = saved;
            }
        }

        throw error(peek(), "Unexpected token at start of statement");
    }

    private List<ASTNode> parseBlockStatements() {
        List<ASTNode> stmts = new ArrayList<>();

        while (!check(TokenType.RBRACE) && !isAtEnd()) {
            List<DecoratorNode> decorators = parseDecorators();
            ASTNode stmt = parseStatement();
            if (stmt != null) {
                stmt.decorators.addAll(decorators);
                stmts.add(stmt);
            }
            while (!pendingStatements.isEmpty()) {
                stmts.add(pendingStatements.remove(0));
            }
        }

        consume(TokenType.RBRACE);
        return stmts;
    }

    private boolean looksLikeExpression() {
        if (isAtEnd()) return false;

        Token t = peek();
        switch (t.type) {
            case IDENTIFIER:
            case INTEGER_LITERAL:
            case STRING_LITERAL:
            case LPAREN:
                return true;
            default:
                return false;
        }
    }

    private boolean startsPrefixExpression() {
        return check(TokenType.LOGICAL_NOT) || check(TokenType.BIT_NOT)
            || check(TokenType.STAR) || check(TokenType.BIT_AND)
            || check(TokenType.MINUS) || check(TokenType.INCREMENT)
            || check(TokenType.DECREMENT);
    }

    private boolean looksLikeArrayElementAccess() {
        if (!check(TokenType.IDENTIFIER)) return false;
        Token next = peekNext();
        return next != null && next.type == TokenType.LBRACKET;
    }

    // operator <op-or-ident> <op-or-ident>
    // operator <type> <op> <ident> (unary) OR operator <type> <ident> <op> <ident> (binary)
    private OverloadNode parseOperatorOverload() {
        List<Token> operands = new ArrayList<>();
        while (!check(TokenType.LBRACE)) {
            operands.add(consume());
        }
        
        consume(TokenType.LBRACE);
        List<ASTNode> bodyStatements = parseBlockStatements();
        Block block = new Block(bodyStatements);

        switch (operands.size()) {
            case 3: {
                // unary op with return type: Ret Op Arg
                String retType = operands.get(0).lexeme;
                String op = operands.get(1).lexeme;
                String arg = operands.get(2).lexeme;

                // validate order: Type Op Ident
                if (operands.get(1).type == TokenType.IDENTIFIER) {
                     throw error(operands.get(1), "Expected operator, got identifier");
                }
                
                Symbol overloadSym = new Symbol("__bolt_operator_" + getSafeOperatorName(op) + "_" + arg, Symbol.Kind.FUNCTION, retType);
                symbols.put(overloadSym.name, overloadSym);

                return new UnaryOverloadNode(
                    op,
                    new Identifier(retType),
                    new Identifier(arg), 
                    block
                );
            }
            
            case 4: {
                // binary op with return type: Ret Left Op Right
                String retType = operands.get(0).lexeme;
                String t1 = operands.get(1).lexeme;
                String op = operands.get(2).lexeme;
                String t2 = operands.get(3).lexeme;

                Symbol overloadSym = new Symbol("__bolt_operator_" + getSafeOperatorName(op) + "_" + t1 + "_" + t2, Symbol.Kind.FUNCTION, retType);
                symbols.put(overloadSym.name, overloadSym);

                return new BinaryOverloadNode(
                    new Identifier(retType),
                    new Identifier(t1),
                    new Identifier(t2),
                    op,
                    block
                );
            }
        
            default: {
                throw error(operands.isEmpty() ? null : operands.get(0), "Bad operator overload. Use 'operator ReturnType Op Arg' (unary) or 'operator ReturnType Left Op Right' (binary). Got " + operands.size() + " tokens.");
            }
        }
    }
    
    // keep this until blocks are fully supported
    private String getSafeOperatorName(String op) {
        String result = OPERATOR_NAMES.get(op);
        if (result != null) return result;

        StringBuilder sb = new StringBuilder();
        for (char c : op.toCharArray()) {
            if (Character.isLetterOrDigit(c)) sb.append(c);
            else sb.append("_").append((int)c);
        }
        return sb.toString();
    }

    private DecoratorNode parseDecorator() {
        consume(TokenType.AT);
        Token name;
        if (check(TokenType.KEYWORD)) name = consume();
        else name = consume(TokenType.IDENTIFIER);

        List<String> args = new ArrayList<>();
        if (match(TokenType.LPAREN)) {
            if (!check(TokenType.RPAREN)) {
                do {
                    args.add(consume().lexeme);
                } while (match(TokenType.COMMA));
            }
            consume(TokenType.RPAREN);
        }
        return new DecoratorNode(name.lexeme, args);
    }

    private PackageDeclaration parsePackage() {
        StringBuilder path = new StringBuilder();
        path.append(consume(TokenType.IDENTIFIER).lexeme);
        while (check(TokenType.DOT)) {
            consume(TokenType.DOT);
            path.append(".").append(consume(TokenType.IDENTIFIER).lexeme);
        }
        if (check(TokenType.SEMICOLON))
            consume(TokenType.SEMICOLON);
        currentPackage = path.toString();
        return new PackageDeclaration(currentPackage);
    }

    private ImportDeclaration parseImport() {
        if (check(TokenType.LESS)) {
            consume(TokenType.LESS);
            StringBuilder header = new StringBuilder();
            while (!check(TokenType.GREATER) && !isAtEnd()) {
                header.append(consume().lexeme);
            }
            consume(TokenType.GREATER);
            if (check(TokenType.SEMICOLON)) consume(TokenType.SEMICOLON);
            ImportDeclaration imp = new ImportDeclaration("");
            imp.isCHeader = true;
            imp.cHeaderName = header.toString();
            return imp;
        }

        StringBuilder path = new StringBuilder();
        Token first = consume(TokenType.IDENTIFIER);
        path.append(first.lexeme);
        while (check(TokenType.DOT)) {
            consume(TokenType.DOT);
            path.append(".").append(consume(TokenType.IDENTIFIER).lexeme);
        }
        if (check(TokenType.SEMICOLON))
            consume(TokenType.SEMICOLON);
        String importPath = path.toString();
        
        String forbidden = config.get("forbidden-headers");
        if (!forbidden.isEmpty()) {
            String[] forbiddenList = forbidden.split(",");
            for (String f : forbiddenList) {
                if (importPath.equals(f.trim())) {
                    reporter.report(first, "Import of forbidden header '" + importPath + "' is disallowed by configuration");
                }
            }
        }

        imports.add(importPath);
        return new ImportDeclaration(importPath);
    }

    private ClassDeclaration parseClass() {
        return parseClass(Visibility.PRIVATE);
    }

    private ClassDeclaration parseClass(Visibility defaultVisibility) {
        // keyword already consumed by matchKeyword in parseTopLevel
        Token name = consume(TokenType.IDENTIFIER);
        
        List<String> genericParams = new ArrayList<>();
        if (check(TokenType.LESS)) {
            consume(TokenType.LESS);
            do {
                genericParams.add(consume(TokenType.IDENTIFIER).lexeme);
            } while (match(TokenType.COMMA));
            consume(TokenType.GREATER);
        }

        List<String> interfaces = new ArrayList<>();
        if (matchKeyword("implements")) {
            do {
                interfaces.add(consume(TokenType.IDENTIFIER).lexeme);
            } while (match(TokenType.COMMA));
        }

        consume(TokenType.LBRACE);
        ASTTree members = new ASTTree();
        while (!check(TokenType.RBRACE) && !isAtEnd()) {
            if (checkKeyword("public") || checkKeyword("private")) {
                Token visToken = peek();
                Visibility vis = visToken.lexeme.equals("public") ? Visibility.PUBLIC : Visibility.PRIVATE;
                if (peekNext() != null && peekNext().type == TokenType.LBRACE) {
                    // block form: public { ... } / private { ... }
                    consume();
                    consume(TokenType.LBRACE);
                    while (!check(TokenType.RBRACE) && !isAtEnd()) {
                        ASTNode decl = parseDeclaration();
                        if (decl != null) {
                            setVisibility(decl, vis);
                            members.add(decl);
                        }
                    }
                    consume(TokenType.RBRACE);
                    continue;
                } else {
                    // prefix form: public void foo() ...
                    consume();
                    List<DecoratorNode> decorators = parseDecorators();
                    ASTNode decl = parseDeclaration();
                    if (decl != null) {
                        decl.decorators.addAll(0, decorators);
                        setVisibility(decl, vis);
                        members.add(decl);
                    }
                    continue;
                }
            }
            ASTNode node = parseDeclaration();
            if (node != null) {
                setVisibility(node, defaultVisibility);
                members.add(node);
            }
        }
        consume(TokenType.RBRACE);

        ClassDeclaration classNode = new ClassDeclaration(name.lexeme, members);
        classNode.line = name.line;
        classNode.column = name.column;
        classNode.genericParams = genericParams;
        classNode.interfaces = interfaces;
        String fullClassName = (currentPackage != null) ? currentPackage + "." + name.lexeme : name.lexeme;
        
        Symbol classSym = new Symbol(name.lexeme, Symbol.Kind.CLASS, name.lexeme);
        classSym.members.clear(); 
        for (ASTNode memberNode : members) {
            if (memberNode instanceof VariableDeclaration var) {
                Symbol memberSymbol = new Symbol(var.name, Symbol.Kind.VARIABLE, var.type.toString());
                classSym.members.put(var.name, memberSymbol);
            } else if (memberNode instanceof FunctionDeclaration func) {
                Symbol funcSymbol = new Symbol(func.name, Symbol.Kind.FUNCTION, func.returnType.toString());
                funcSymbol.mangle = true;
                if (func.parameters != null) {
                    for (Parameter p : func.parameters) {
                        funcSymbol.parameterTypes.add(p.type.toString());
                    }
                }
                classSym.members.put(func.name, funcSymbol);
            }
        }
        symbols.put(fullClassName, classSym);
        
        return classNode;
    }

    private InterfaceDeclaration parseInterface() {
        Token name = consume(TokenType.IDENTIFIER);
        consume(TokenType.LBRACE);
        ASTTree members = new ASTTree();
        while (!check(TokenType.RBRACE) && !isAtEnd()) {
            ASTNode node = parseDeclaration();
            if (node != null) {
                if (!(node instanceof FunctionDeclaration)) {
                    reporter.report(new Token(TokenType.IDENTIFIER, name.lexeme, node.line, node.column, 0), "Interfaces can only contain function declarations");
                }
                members.add(node);
            }
        }
        consume(TokenType.RBRACE);

        InterfaceDeclaration interfaceNode = new InterfaceDeclaration(name.lexeme, members);
        interfaceNode.line = name.line;
        interfaceNode.column = name.column;
        String fullInterfaceName = (currentPackage != null) ? currentPackage + "." + name.lexeme : name.lexeme;
        
        Symbol interfaceSym = new Symbol(name.lexeme, Symbol.Kind.INTERFACE, name.lexeme);
        for (ASTNode memberNode : members) {
            if (memberNode instanceof FunctionDeclaration func) {
                Symbol funcSymbol = new Symbol(func.name, Symbol.Kind.FUNCTION, func.returnType.toString());
                funcSymbol.mangle = true;
                if (func.parameters != null) {
                    for (Parameter p : func.parameters) {
                        funcSymbol.parameterTypes.add(p.type.toString());
                    }
                }
                interfaceSym.members.put(func.name, funcSymbol);
            }
        }
        symbols.put(fullInterfaceName, interfaceSym);
        
        return interfaceNode;
    }


    private ImplDeclaration parseImpl() {
        Token targetType;
        if (check(TokenType.KEYWORD)) {
            targetType = consume();
        } else {
            targetType = consume(TokenType.IDENTIFIER);
        }
        consume(TokenType.LBRACE);

        List<ASTNode> members = new ArrayList<>();
        while (!check(TokenType.RBRACE) && !isAtEnd()) {
            ASTNode node = parseDeclaration();
            if (node != null) {
                members.add(node);
            }
        }
        consume(TokenType.RBRACE);

        return new ImplDeclaration(targetType.lexeme, members);
    }

    private EnumDeclaration parseEnum() {
        Token name = consume(TokenType.IDENTIFIER);
        consume(TokenType.LBRACE);
        List<EnumMember> members = new ArrayList<>();
        while (!check(TokenType.RBRACE) && !isAtEnd()) {
            Token member = consume(TokenType.IDENTIFIER);
            ASTNode value = null;
            if (match(TokenType.ASSIGN)) {
                value = parseExpression();
            }
            members.add(new EnumMember(member.lexeme, value, member.line, member.column));
            if (!match(TokenType.COMMA)) break;
        }
        consume(TokenType.RBRACE);
        match(TokenType.SEMICOLON);
        return new EnumDeclaration(name.lexeme, members, name.line, name.column);
    }

    private UnionDeclaration parseUnion() {
        Token name = consume(TokenType.IDENTIFIER);
        consume(TokenType.LBRACE);
        List<StructMember> members = new ArrayList<>();
        while (!check(TokenType.RBRACE) && !isAtEnd()) {
            ASTNode memberType = parseType();
            Token memberName = consume(TokenType.IDENTIFIER);
            memberType = applyArraySuffix(memberType);
            consume(TokenType.SEMICOLON);
            members.add(new StructMember(memberType, memberName.lexeme, memberName.line, memberName.column));
        }
        consume(TokenType.RBRACE);
        match(TokenType.SEMICOLON);
        return new UnionDeclaration(name.lexeme, members, name.line, name.column);
    }

    private TypedefDeclaration parseTypedef() {
        ASTNode aliased = parseType();
        Token name = consume(TokenType.IDENTIFIER);
        aliased = applyArraySuffix(aliased);
        consume(TokenType.SEMICOLON);
        return new TypedefDeclaration(aliased, name.lexeme, name.line, name.column);
    }

    private ASTNode applyArraySuffix(ASTNode type) {
        while (check(TokenType.LBRACKET)) {
            consume(TokenType.LBRACKET);
            ASTNode size = check(TokenType.RBRACKET) ? null : parseExpression(TokenType.RBRACKET);
            consume(TokenType.RBRACKET);
            type = new ArrayType(type, size);
        }
        return type;
    }

    private ASTNode parseFor() {
        Token t = consume();
        consume(TokenType.LPAREN);

        ASTNode initializer = null;
        if (!check(TokenType.SEMICOLON)) {
            initializer = parseForInitializer();
        } else {
            consume(TokenType.SEMICOLON);
        }

        ASTNode condition = null;
        if (!check(TokenType.SEMICOLON)) {
            condition = parseExpression();
        }
        consume(TokenType.SEMICOLON);

        ASTNode update = null;
        if (!check(TokenType.RPAREN)) {
            List<ASTNode> updates = new ArrayList<>();
            do {
                updates.add(parseExpression());
            } while (match(TokenType.COMMA));
            update = updates.size() == 1 ? updates.get(0) : new Block(updates);
        }
        consume(TokenType.RPAREN);

        ASTNode body = parseStatement();
        ForStatement fs = new ForStatement(initializer, condition, update, body);
        fs.line = t.line; fs.column = t.column;
        return fs;
    }

    private ASTNode parseForInitializer() {
        int saved = position;
        if (isTypeStart(peek()) && !looksLikeArrayElementAccess()) {
            try {
                ASTNode typeNode = parseType();
                if (check(TokenType.IDENTIFIER)) {
                    List<ASTNode> declarators = new ArrayList<>();
                    do {
                        Token name = consume(TokenType.IDENTIFIER);
                        ASTNode declType = applyArraySuffix(typeNode);
                        ASTNode init = null;
                        if (match(TokenType.ASSIGN)) {
                            init = parseExpression();
                        }
                        VariableDeclaration vd =
                            new VariableDeclaration(declType, name.lexeme, init, false, false);
                        vd.line = name.line; vd.column = name.column;
                        declarators.add(vd);
                    } while (match(TokenType.COMMA) && check(TokenType.IDENTIFIER));

                    if (check(TokenType.SEMICOLON)) {
                        consume(TokenType.SEMICOLON);
                        return declarators.size() == 1
                            ? declarators.get(0)
                            : new Block(declarators);
                    }
                }
            } catch (ParseError e) {
            }
            position = saved;
        }

        ASTNode expr = parseExpression();
        consume(TokenType.SEMICOLON);
        ExpressionStatement es = new ExpressionStatement(expr);
        es.line = expr.line; es.column = expr.column;
        return es;
    }

    private ASTNode parseSwitch() {
        Token t = consume();
        consume(TokenType.LPAREN);
        ASTNode subject = parseExpression();
        consume(TokenType.RPAREN);
        consume(TokenType.LBRACE);

        List<ASTNode> sections = new ArrayList<>();
        while (!check(TokenType.RBRACE) && !isAtEnd()) {
            if (matchKeyword("case")) {
                Token caseToken = tokens.get(position - 1);
                ASTNode value = parseExpression();
                consume(TokenType.COLON);
                CaseStatement cs = new CaseStatement(value, parseSwitchSection());
                cs.line = caseToken.line; cs.column = caseToken.column;
                sections.add(cs);
            } else if (matchKeyword("default")) {
                Token defaultToken = tokens.get(position - 1);
                consume(TokenType.COLON);
                DefaultStatement ds = new DefaultStatement(parseSwitchSection());
                ds.line = defaultToken.line; ds.column = defaultToken.column;
                sections.add(ds);
            } else {
                ASTNode stray = parseStatement();
                if (stray != null) sections.add(stray);
            }
        }
        consume(TokenType.RBRACE);

        Block body = new Block(sections);
        body.line = t.line; body.column = t.column;
        SwitchStatement ss = new SwitchStatement(subject, body);
        ss.line = t.line; ss.column = t.column;
        return ss;
    }

    private List<ASTNode> parseSwitchSection() {
        List<ASTNode> statements = new ArrayList<>();
        while (!check(TokenType.RBRACE) && !isAtEnd()
                && !checkKeyword("case") && !checkKeyword("default")) {
            ASTNode stmt = parseStatement();
            if (stmt != null) statements.add(stmt);
            while (!pendingStatements.isEmpty()) {
                statements.add(pendingStatements.remove(0));
            }
        }
        return statements;
    }

    private boolean check(TokenType type) {
        if (isAtEnd())
            return false;
        return peek().type == type;
    }

    private boolean checkKeyword(String lexeme) {
        if (isAtEnd()) return false;
        Token t = peek();
        return (t.type == TokenType.KEYWORD || t.type == TokenType.IDENTIFIER) && t.lexeme.equals(lexeme);
    }

    private Token peek() {
        if (position >= tokens.size())
            return null;
        return tokens.get(position);
    }

    private Token peekNext() {
        return peekNext(1);
    }

    private Token peekNext(int p) {
        if (position + p >= tokens.size())
            return null;
        return tokens.get(position + p);
    }

    private Token consume() {
        return tokens.get(position++);
    }

    private Token consume(TokenType type) {
        if (check(type))
            return consume();
        throw error(peek(), "Expected " + type + " but got " + (peek() != null ? peek().type : "EOF"));
    }

    private ParseError error(Token token, String message) {
        reporter.report(token, message);
        return new ParseError();
    }

    private void synchronize() {
        consume();

        while (!isAtEnd()) {
            if (peek().type == TokenType.SEMICOLON) {
                consume();
                return;
            }

            switch (peek().lexeme) {
                case "class":
                case "void":
                case "int":
                case "char":
                case "float":
                case "double":
                case "string":
                case "if":
                case "while":
                case "return":
                case "package":
                case "import":
                case "impl":
                case "operator":
                    return;
            }

            consume();
        }
    }

    private boolean matchKeyword(String lexeme) {
        if (checkKeyword(lexeme)) {
            consume();
            return true;
        }
        return false;
    }

    private boolean match(TokenType type) {
        if (check(type)) {
            consume();
            return true;
        }
        return false;
    }

    private void skipUntil(TokenType type) {
        while (!check(type) && !isAtEnd()) consume();
    }

    private String captureUntil(TokenType type) {
        StringBuilder sb = new StringBuilder();
        while (!check(type) && !isAtEnd()) {
            sb.append(consume().lexeme).append(" ");
        }
        return sb.toString().trim();
    }


    private boolean isAtEnd() {
        return position >= tokens.size() || peek().type == TokenType.EOF;
    }
}
