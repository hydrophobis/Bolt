package hydro.bolt.format;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LineWrapperTest {

    @Test
    void shortLinesAreUntouched() {
        String source = "int main() { return 0; }\n";
        assertEquals(source, LineWrapper.wrap(source, 80, "    "));
    }

    @Test
    void aWidthOfZeroDisablesWrapping() {
        String source = "f(" + "a, ".repeat(60) + "b);\n";
        assertEquals(source, LineWrapper.wrap(source, 0, "    "));
    }

    @Test
    @DisplayName("long argument lists break after a comma")
    void wrapsAtArgumentCommas() {
        String source = "    printf(\"%d %d %d\", first_value, second_value, third_value);";
        String wrapped = LineWrapper.wrap(source, 40, "    ");

        assertTrue(wrapped.contains("\n"), "expected a break: " + wrapped);
        for (String line : wrapped.split("\n")) {
            assertTrue(line.stripTrailing().endsWith(",")
                    || line.contains(")")
                    || line.isBlank(),
                "break landed somewhere odd: " + line);
        }
    }

    @Test
    @DisplayName("preprocessor lines are never broken")
    void leavesPreprocessorLinesAlone() {
        String source = "#define LONG_MACRO(a, b, c, d, e, f, g) do_something(a, b, c, d, e, f, g)";
        assertEquals(source, LineWrapper.wrap(source, 20, "    "));
    }

    @Test
    @DisplayName("a comma inside a string literal is not a break point")
    void doesNotBreakInsideStringLiterals() {
        String source = "    puts(\"a very long message, with a comma inside of it indeed\");";
        String wrapped = LineWrapper.wrap(source, 30, "    ");
        assertTrue(wrapped.contains("\"a very long message, with a comma inside of it indeed\""),
            "the literal was split: " + wrapped);
    }

    @Test
    void continuationLinesAreIndented() {
        String source = "    call(first_argument_value, second_argument_value);";
        String wrapped = LineWrapper.wrap(source, 30, "    ");
        String[] lines = wrapped.split("\n");
        assertTrue(lines.length > 1);
        assertTrue(lines[1].startsWith("        "), "continuation not indented: " + lines[1]);
    }

    @Test
    void lineCountIsPreservedForUnwrappableInput() {
        String source = "aaaa\nbbbb\ncccc";
        assertEquals(source, LineWrapper.wrap(source, 80, "    "));
    }
}
