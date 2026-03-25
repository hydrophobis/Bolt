package hydro.bolt.tokens;

import hydro.bolt.parser.ErrorReporter;
import java.util.*;

// 300 lines of pure pain and switches
public class Tokenizer {

    private final String input;
    private int pos = 0;
    private int line = 1;
    private int col = 1;
    private ErrorReporter reporter = new ErrorReporter();

    private static final Set<String> KEYWORDS = Set.of(
            "auto", "break", "case", "char", "const", "continue",
            "default", "do", "double", "else", "enum", "extern",
            "float", "for", "goto", "if", "inline", "int", "long",
            "register", "restrict", "return", "short", "signed",
            "sizeof", "static", "struct", "switch", "typedef",
            "union", "unsigned", "void", "volatile", "while",
            "class", "public", "private", "protected", "import",
            "impl", "operator", "package", "new", "delete");

    public Tokenizer(String input) {
        this.input = input;
    }

    public Tokenizer(String input, ErrorReporter reporter) {
        this.input = input;
        this.reporter = reporter;
    }

    public List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();

        while (!isAtEnd()) {
            char c = peek();

            if (Character.isWhitespace(c)) {
                advance();
                continue;
            }

            int start = pos;
            int startLine = line;
            int startCol = col;

            if (Character.isLetter(c) || c == '_') {
                String ident = readIdentifier();
                TokenType type = KEYWORDS.contains(ident)
                        ? TokenType.KEYWORD
                        : TokenType.IDENTIFIER;
                tokens.add(new Token(type, ident, start, startLine, startCol));
                continue;
            }

            if (Character.isDigit(c)) {
                tokens.add(readNumber(start, startLine, startCol));
                continue;
            }

            switch (c) {
                case '"':
                    tokens.add(readString(start, startLine, startCol));
                    break;

                case '\'':
                    tokens.add(readChar(start, startLine, startCol));
                    break;

                case '+':
                    advance();
                    if (match('+'))
                        tokens.add(new Token(TokenType.INCREMENT, "++", start, startLine, startCol));
                    else if (match('='))
                        tokens.add(new Token(TokenType.PLUS_ASSIGN, "+=", start, startLine, startCol));
                    else
                        tokens.add(new Token(TokenType.PLUS, "+", start, startLine, startCol));
                    break;

                case '-':
                    advance();
                    if (match('-'))
                        tokens.add(new Token(TokenType.DECREMENT, "--", start, startLine, startCol));
                    else if (match('>'))
                        tokens.add(new Token(TokenType.ARROW, "->", start, startLine, startCol));
                    else if (match('='))
                        tokens.add(new Token(TokenType.MINUS_ASSIGN, "-=", start, startLine, startCol));
                    else
                        tokens.add(new Token(TokenType.MINUS, "-", start, startLine, startCol));
                    break;

                case '*':
                    advance();
                    if (match('='))
                        tokens.add(new Token(TokenType.STAR_ASSIGN, "*=", start, startLine, startCol));
                    else
                        tokens.add(new Token(TokenType.STAR, "*", start, startLine, startCol));
                    break;

                case '/':
                    advance();
                    if (match('/')) {
                        skipLineComment();
                    } else if (match('*')) {
                        skipBlockComment();
                    } else if (match('=')) {
                        tokens.add(new Token(TokenType.SLASH_ASSIGN, "/=", start, startLine, startCol));
                    } else {
                        tokens.add(new Token(TokenType.SLASH, "/", start, startLine, startCol));
                    }
                    break;

                case '=':
                    advance();
                    tokens.add(new Token(match('=') ? TokenType.EQUAL : TokenType.ASSIGN,
                            input.substring(start, pos), start, startLine, startCol));
                    break;

                case '!':
                    advance();
                    tokens.add(new Token(match('=') ? TokenType.NOT_EQUAL : TokenType.LOGICAL_NOT,
                            input.substring(start, pos), start, startLine, startCol));
                    break;

                case '<':
                    advance();
                    if (match('<'))
                        tokens.add(new Token(TokenType.SHIFT_LEFT, "<<", start, startLine, startCol));
                    else if (match('='))
                        tokens.add(new Token(TokenType.LESS_EQUAL, "<=", start, startLine, startCol));
                    else
                        tokens.add(new Token(TokenType.LESS, "<", start, startLine, startCol));
                    break;

                case '>':
                    advance();
                    if (match('>'))
                        tokens.add(new Token(TokenType.SHIFT_RIGHT, ">>", start, startLine, startCol));
                    else if (match('='))
                        tokens.add(new Token(TokenType.GREATER_EQUAL, ">=", start, startLine, startCol));
                    else
                        tokens.add(new Token(TokenType.GREATER, ">", start, startLine, startCol));
                    break;

                case '&':
                    advance();
                    tokens.add(new Token(match('&') ? TokenType.LOGICAL_AND : TokenType.BIT_AND,
                            input.substring(start, pos), start, startLine, startCol));
                    break;

                case '|':
                    advance();
                    tokens.add(new Token(match('|') ? TokenType.LOGICAL_OR : TokenType.BIT_OR,
                            input.substring(start, pos), start, startLine, startCol));
                    break;

                case '^':
                    advance();
                    tokens.add(new Token(TokenType.BIT_XOR, "^", start, startLine, startCol));
                    break;

                case '~':
                    advance();
                    tokens.add(new Token(TokenType.BIT_NOT, "~", start, startLine, startCol));
                    break;

                case '(':
                    advance();
                    tokens.add(new Token(TokenType.LPAREN, "(", start, startLine, startCol));
                    break;
                case ')':
                    advance();
                    tokens.add(new Token(TokenType.RPAREN, ")", start, startLine, startCol));
                    break;
                case '{':
                    advance();
                    tokens.add(new Token(TokenType.LBRACE, "{", start, startLine, startCol));
                    break;
                case '}':
                    advance();
                    tokens.add(new Token(TokenType.RBRACE, "}", start, startLine, startCol));
                    break;
                case '[':
                    advance();
                    tokens.add(new Token(TokenType.LBRACKET, "[", start, startLine, startCol));
                    break;
                case ']':
                    advance();
                    tokens.add(new Token(TokenType.RBRACKET, "]", start, startLine, startCol));
                    break;

                case ';':
                    advance();
                    tokens.add(new Token(TokenType.SEMICOLON, ";", start, startLine, startCol));
                    break;
                case ',':
                    advance();
                    tokens.add(new Token(TokenType.COMMA, ",", start, startLine, startCol));
                    break;
                case '.':
                    advance();
                    tokens.add(new Token(TokenType.DOT, ".", start, startLine, startCol));
                    break;
                case '?':
                    advance();
                    tokens.add(new Token(TokenType.QUESTION, "?", start, startLine, startCol));
                    break;
                case ':':
                    advance();
                    tokens.add(new Token(TokenType.COLON, ":", start, startLine, startCol));
                    break;
                case '@':
                    advance();
                    tokens.add(new Token(TokenType.AT, "@", start, startLine, startCol));
                    break;

                default:
                    reporter.report(new Token(TokenType.EOF, String.valueOf(c), pos, line, col), "Unexpected character '" + c + "'");
                    advance();
                    break;
            }
        }

        tokens.add(new Token(TokenType.EOF, "0", pos, line, col));
        return tokens;
    }

    private boolean isAtEnd() {
        return pos >= input.length();
    }

    private char peek() {
        return input.charAt(pos);
    }

    private char advance() {
        char c = input.charAt(pos++);
        if (c == '\n') {
            line++;
            col = 1;
        } else {
            col++;
        }
        return c;
    }

    private boolean match(char expected) {
        if (isAtEnd())
            return false;
        if (input.charAt(pos) != expected)
            return false;
        pos++;
        return true;
    }

    private String readIdentifier() {
        int start = pos;
        while (!isAtEnd()) {
            char c = peek();
            if (!Character.isLetterOrDigit(c) && c != '_')
                break;
            advance();
        }
        return input.substring(start, pos);
    }

    private Token readNumber(int start, int startLine, int startCol) {
        boolean isFloat = false;

        while (!isAtEnd() && Character.isDigit(peek()))
            advance();

        if (!isAtEnd() && peek() == '.') {
            isFloat = true;
            advance();
            while (!isAtEnd() && Character.isDigit(peek()))
                advance();
        }

        String lexeme = input.substring(start, pos);
        return new Token(
                isFloat ? TokenType.FLOAT_LITERAL : TokenType.INTEGER_LITERAL,
                lexeme,
                start, startLine, startCol);
    }

    private Token readString(int start, int startLine, int startCol) {
        advance();

        StringBuilder sb = new StringBuilder();
        while (!isAtEnd()) {
            char c = peek();
            if (c == '"') {
                advance();
                break;
            }
            if (c == '\\') {
                advance();
                if (isAtEnd()) {
                    reporter.report(new Token(TokenType.STRING_LITERAL, sb.toString(), start, startLine, startCol), "Unterminated escape in string");
                    break;
                }
                char esc = advance();
                sb.append('\\').append(esc);
            } else {
                sb.append(advance());
            }
        }

        if (isAtEnd())
            reporter.report(new Token(TokenType.STRING_LITERAL, sb.toString(), start, startLine, startCol), "Unterminated string literal");

        return new Token(TokenType.STRING_LITERAL,
                input.substring(start, pos), start, startLine, startCol);
    }


    private Token readChar(int start, int startLine, int startCol) {
        advance();

        if (peek() == '\\')
            advance();
        advance();

        if (!match('\''))
            reporter.report(new Token(TokenType.CHAR_LITERAL, "", start, startLine, startCol), "Invalid character literal");

        return new Token(TokenType.CHAR_LITERAL,
                input.substring(start, pos), start, startLine, startCol);
    }

    private void skipLineComment() {
        while (!isAtEnd() && peek() != '\n')
            advance();
    }

    private void skipBlockComment() {
        while (!isAtEnd()) {
            if (peek() == '*' && pos + 1 < input.length()
                    && input.charAt(pos + 1) == '/') {
                pos += 2;
                return;
            }
            advance();
        }
        reporter.report(null, "Unterminated block comment");
    }
}
