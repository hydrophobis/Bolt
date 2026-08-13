package hydro.bolt.tokens;

import hydro.bolt.TestCompiler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TokenizerTest {

    private Token firstOfType(String source, TokenType type) {
        for (Token t : TestCompiler.tokenize(source)) {
            if (t.type == type) return t;
        }
        throw new AssertionError("no " + type + " token in: " + source);
    }

    @Test
    @DisplayName("hex literals pass through unchanged")
    void hexLiteral() {
        assertEquals("0xFF", firstOfType("int a = 0xFF;", TokenType.INTEGER_LITERAL).lexeme);
    }

    @Test
    @DisplayName("0o octal is rewritten to C's leading-zero form")
    void octalLiteralIsNormalised() {
        assertEquals("077", firstOfType("int a = 0o77;", TokenType.INTEGER_LITERAL).lexeme);
        assertEquals("010", firstOfType("int a = 0o10;", TokenType.INTEGER_LITERAL).lexeme);
    }

    @Test
    @DisplayName("0b binary is rewritten to decimal")
    void binaryLiteralIsNormalised() {
        assertEquals("10", firstOfType("int a = 0b1010;", TokenType.INTEGER_LITERAL).lexeme);
        assertEquals("15", firstOfType("int a = 0b1111;", TokenType.INTEGER_LITERAL).lexeme);
    }

    @Test
    void floatLiteralIsItsOwnTokenType() {
        assertEquals("1.5", firstOfType("float a = 1.5;", TokenType.FLOAT_LITERAL).lexeme);
    }

    @Test
    void tracksLineAndColumn() {
        List<Token> tokens = TestCompiler.tokenize("int a;\nint b;");
        Token second = tokens.stream()
            .filter(t -> t.type == TokenType.IDENTIFIER && t.lexeme.equals("b"))
            .findFirst()
            .orElseThrow();
        assertEquals(2, second.line);
        assertEquals(5, second.column);
    }

    @Test
    void endsWithEof() {
        List<Token> tokens = TestCompiler.tokenize("int a;");
        assertEquals(TokenType.EOF, tokens.get(tokens.size() - 1).type);
    }
}
