package hydro.bolt.parser;

import java.util.*;

import hydro.bolt.tokens.*;
import hydro.bolt.ast.*;
import hydro.bolt.ast.type.*;
import hydro.bolt.ast.decl.*;
import hydro.bolt.ast.expr.*;
import hydro.bolt.ast.misc.ArrayAccess;
import hydro.bolt.ast.misc.Block;
import hydro.bolt.ast.misc.FunctionCall;
import hydro.bolt.ast.misc.Identifier;
import hydro.bolt.ast.misc.MemberAccess;
import hydro.bolt.ast.misc.Parameter;
import hydro.bolt.ast.statement.*;
import hydro.bolt.ast.bolt.*;

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
                if (node != null) {
                    ast.add(node);
                }
            } catch (ParseError e) {
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
        if (matchKeyword("operator")) return parseOperatorOverload();
        if (matchKeyword("package")) return parsePackage();
        if (matchKeyword("import")) return parseImport();
        if (matchKeyword("class")) return parseClass();
        if (matchKeyword("struct")) return parseClass();
        if (matchKeyword("impl")) return parseImpl();

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

        return parseStandardC();
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

        RawCNode body = new RawCNode("");
        while (!check(TokenType.RBRACE) && !isAtEnd()) {
            body = new RawCNode(body.content + consume().lexeme + " ");
        }

        consume(TokenType.RBRACE);
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
                ASTNode paramType = parseType();
                Token paramName = consume(TokenType.IDENTIFIER);
                parameters.add(new Parameter(paramType, paramName.lexeme));
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
        return func;
    }


    private ASTNode parseExpression() {
        return parseBinary(0, null);
    }

    private ASTNode parseExpression(TokenType stopAt) {
        return parseBinary(0, stopAt);
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
        switch (type) {
            case PLUS: case MINUS: case STAR: case SLASH: case PERCENT:
            case EQUAL: case NOT_EQUAL: case LESS: case LESS_EQUAL:
            case GREATER: case GREATER_EQUAL:
            case LOGICAL_AND: case LOGICAL_OR:
            case BIT_AND: case BIT_OR: case BIT_XOR:
            case SHIFT_LEFT: case SHIFT_RIGHT:
            case ASSIGN:
            case PLUS_ASSIGN:
            case MINUS_ASSIGN:
            case STAR_ASSIGN:
            case SLASH_ASSIGN:
            case RBRACKET:
                return true;
            default:
                return false;
        }
    }

    private int getPrecedence(TokenType type) {
        switch (type) {
            case ASSIGN:
            case PLUS_ASSIGN:
            case MINUS_ASSIGN:
            case STAR_ASSIGN:
            case SLASH_ASSIGN:
                return 5;
            case LOGICAL_OR: return 10;
            case LOGICAL_AND: return 20;
            case EQUAL: case NOT_EQUAL: return 30;
            case LESS: case LESS_EQUAL: case GREATER: case GREATER_EQUAL: return 40;
            case SHIFT_LEFT: case SHIFT_RIGHT: return 45;
            case BIT_AND: return 27;
            case BIT_XOR: return 26;
            case BIT_OR: return 25;
            case PLUS: case MINUS: return 50;
            case STAR: case SLASH: case PERCENT: return 60;
            case RBRACKET: return 70;
            default: return 0;
        }
    }

    private ASTNode parseUnary() {
        if (check(TokenType.MINUS) || check(TokenType.LOGICAL_NOT) || 
            check(TokenType.BIT_NOT) || check(TokenType.INCREMENT) || 
            check(TokenType.DECREMENT) || check(TokenType.PLUS)) {
            Token op = consume();
            ASTNode operand = parseUnary();
            return new UnaryExpression(op.lexeme, operand, true);
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
            Identifier id = new Identifier(t.lexeme);
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
                    RawCNode boolNode = new RawCNode(t.lexeme);
                    boolNode.line = t.line; boolNode.column = t.column;
                    return boolNode;
                }
                if (t.lexeme.equals("switch")) {
                    return parseSwitchStatement();
                }
                consume();
                return new Identifier(t.lexeme);

            case INTEGER_LITERAL:
                consume();
                RawCNode intNode = new RawCNode(t.lexeme);
                intNode.line = t.line; intNode.column = t.column;
                return intNode;
                
            case STRING_LITERAL:
                consume();
                RawCNode strNode = new RawCNode(t.lexeme);
                strNode.line = t.line; strNode.column = t.column;
                return strNode;

            case LPAREN: {
                consume(TokenType.LPAREN);
                // Check for C cast: (type)expr
                if (isTypeStart(peek()) && peekNext() != null && peekNext().type == TokenType.RPAREN) {
                    ASTNode typeNode = parseType();
                    consume(TokenType.RPAREN);
                    ASTNode target = parseExpression();
                    return new UnaryExpression("( " + typeNode.toString() + " )", target, true);
                }
                ASTNode expr = parseExpression();
                consume(TokenType.RPAREN);
                return expr;
            }

            case CHAR_LITERAL:
                consume();
                RawCNode charNode = new RawCNode(t.lexeme);
                charNode.line = t.line; charNode.column = t.column;
                return charNode;

            default:
                throw error(t, "Unexpected token in expression");
        }
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

        if (checkKeyword("for") || checkKeyword("switch")) {
            return parseComplexStatement();
        }

        if (check(TokenType.LBRACE)) {
            Token t = consume(TokenType.LBRACE);
            Block b = new Block(parseBlockStatements());
            b.line = t.line; b.column = t.column;
            return b;
        }

        // is this a variable decl or expression?
        if (isTypeStart(peek()) || check(TokenType.LPAREN)) {
            int saved = position;
            if (peek().type == TokenType.KEYWORD && (peek().lexeme.equals("return") || peek().lexeme.equals("if") ||
                peek().lexeme.equals("while") || peek().lexeme.equals("new") ||
                peek().lexeme.equals("delete") || peek().lexeme.equals("package") ||
                peek().lexeme.equals("import") || peek().lexeme.equals("class") ||
                peek().lexeme.equals("impl") || peek().lexeme.equals("operator"))) {
            } else if (!looksLikeArrayElementAccess()) {
                ASTNode typeNode = parseType();

                if (check(TokenType.IDENTIFIER)) {
                    Token afterName = peekNext();

                    if (afterName != null &&
                        (afterName.type == TokenType.SEMICOLON || afterName.type == TokenType.ASSIGN)) {
                        Token nameToken = consume(TokenType.IDENTIFIER);
                        ASTNode initializer = null;
                        if (match(TokenType.ASSIGN)) {
                            initializer = parseExpression();
                        }
                        consume(TokenType.SEMICOLON);

                        VariableDeclaration vd = new VariableDeclaration(
                            typeNode,
                            nameToken.lexeme,
                            initializer,
                            false,
                            false
                        );
                        vd.line = nameToken.line;
                        vd.column = nameToken.column;
                        return vd;
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

        return parseStandardC();
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
        switch (op) {
            case "+": return "plus";
            case "-": return "minus";
            case "*": return "mul";
            case "/": return "div";
            case "%": return "mod";
            case "==": return "eq";
            case "!=": return "neq";
            case "<": return "lt";
            case "<=": return "lte";
            case ">": return "gt";
            case ">=": return "gte";
            case "&&": return "land";
            case "||": return "lor";
            case "!": return "lnot";
            case "&": return "band";
            case "|": return "bor";
            case "^": return "bxor";
            case "~": return "bnot";
            case "<<": return "shl";
            case ">>": return "shr";
            case "[": return "lbracket";
            case "]": return "rbracket";
            case "(": return "lparen";
            case ")": return "rparen";
            default:
                StringBuilder sb = new StringBuilder();
                for (char c : op.toCharArray()) {
                    if (Character.isLetterOrDigit(c)) sb.append(c);
                    else sb.append("_").append((int)c);
                }
                return sb.toString();
        }
    }

    private String blockToRawC(Block block) {
        StringBuilder sb = new StringBuilder();
        if (block.statements != null) {
            for (ASTNode stmt : block.statements) {
                sb.append(nodeToString(stmt)).append(" ");
            }
        }
        return sb.toString().trim();
    }
    
    // basic node to string helper
    private String nodeToString(ASTNode node) {
        if (node instanceof RawCNode raw) {
            return raw.content;
        } else if (node instanceof ReturnStatement ret) {
            return "return " + (ret.value != null ? nodeToString(ret.value) : "") + ";";
        } else if (node instanceof Identifier id) {
            return id.name;
        } else if (node instanceof UnaryExpression unary) {
            if (unary.isPrefix) {
                return unary.operator + nodeToString(unary.operand);
            } else {
                return nodeToString(unary.operand) + unary.operator;
            }
        } else if (node instanceof ExpressionStatement exprStmt) {
            return nodeToString(exprStmt.expression) + ";";
        }
        return node.toString();
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

        consume(TokenType.LBRACE);
        ASTTree members = new ASTTree();
        while (!check(TokenType.RBRACE) && !isAtEnd()) {
            ASTNode node = parseDeclaration();
            if (node != null) {
                members.add(node);
            }
        }
        consume(TokenType.RBRACE);

        ClassDeclaration classNode = new ClassDeclaration(name.lexeme, members);
        classNode.genericParams = genericParams;
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
                classSym.members.put(func.name, funcSymbol);
            }
        }
        symbols.put(fullClassName, classSym);
        
        return classNode;
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

    private ASTNode parseStandardC() {
        StringBuilder raw = new StringBuilder();
        int braceCount = 0;

        // handle for and switch statements specially, they have braced bodies
        if (checkKeyword("for") || checkKeyword("switch")) {
            return parseComplexStatement();
        }

        while (!isAtEnd()) {
            Token t = peek();
            if (t == null) break;

            if (t.type == TokenType.LBRACE) braceCount++;
            if (t.type == TokenType.RBRACE) {
                if (braceCount == 0) {
                    // this belongs to the caller
                    break;
                }
                braceCount--;
            }

            if (braceCount == 0 && t.type == TokenType.SEMICOLON) {
                raw.append(consume().lexeme);
                break;
            }

            raw.append(t.lexeme).append(" ");
            consume();
        }

        String content = raw.toString().trim();
        if (content.isEmpty()) return null;
        return new RawCNode(content);
    }

    private ASTNode parseComplexStatement() {
        StringBuilder raw = new StringBuilder();
        int braceDepth = 0;

        while (!isAtEnd()) {
            Token t = peek();
            if (t == null) break;

            // append token to raw output
            raw.append(t.lexeme).append(" ");

            if (t.type == TokenType.LBRACE) {
                braceDepth++;
            } else if (t.type == TokenType.RBRACE) {
                braceDepth--;
                // after closing brace of the body, we're done
                if (braceDepth == 0) {
                    consume();
                    break;
                }
            }

            consume();
        }

        String content = raw.toString().trim();
        if (content.isEmpty()) return null;
        return new RawCNode(content);
    }




    private String captureBracedContent() {
        int braceLevel = 1;
        StringBuilder content = new StringBuilder();

        while (braceLevel > 0 && !isAtEnd()) {
            Token t = consume();
            if (t.type == TokenType.LBRACE)
                braceLevel++;
            if (t.type == TokenType.RBRACE)
                braceLevel--;

            if (braceLevel > 0) {
                // SHITTY CODE: needs original source spacing
                content.append(t.lexeme).append(" ");
            }
        }

        return content.toString();
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

    private void consumeKeyword(String lexeme) {
        if (checkKeyword(lexeme))
            consume();
        else
            throw error(peek(), "Expected keyword " + lexeme);
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
