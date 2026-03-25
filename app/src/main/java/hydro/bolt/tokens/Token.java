package hydro.bolt.tokens;

public class Token {
    public final TokenType type;
    // apparently this is a real word??
    public final String lexeme;
    public final int position;
    public final int line;
    public final int column;

    public Token(TokenType type, String lexeme, int position, int line, int column) {
        this.type = type;
        this.lexeme = lexeme;
        this.position = position;
        this.line = line;
        this.column = column;
    }

    @Override
    public String toString() {
        return type + " : " + lexeme + " at line " + line + ", col " + column;
    }
}
