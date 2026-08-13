package hydro.bolt.parser;

import hydro.bolt.Config;
import hydro.bolt.TestCompiler;
import hydro.bolt.ast.ASTNode;
import hydro.bolt.ast.Visibility;
import hydro.bolt.ast.bolt.ClassDeclaration;
import hydro.bolt.ast.decl.FunctionDeclaration;
import hydro.bolt.ast.decl.VariableDeclaration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ParserTest {

    private ClassDeclaration classNamed(String source, String name) {
        for (ASTNode node : TestCompiler.parse(source).ast()) {
            if (node instanceof ClassDeclaration cls && cls.name.equals(name)) return cls;
        }
        throw new AssertionError("no class named " + name);
    }

    private Visibility fieldVisibility(ClassDeclaration cls, String field) {
        for (ASTNode member : cls.inner) {
            if (member instanceof VariableDeclaration var && var.name.equals(field)) {
                return var.visibility;
            }
        }
        throw new AssertionError("no field named " + field);
    }

    @Test
    @DisplayName("class members default to private")
    void classMembersArePrivateByDefault() {
        ClassDeclaration cls = classNamed("class Counter { int n; }", "Counter");
        assertEquals(Visibility.PRIVATE, fieldVisibility(cls, "n"));
    }

    @Test
    @DisplayName("struct members default to public, as in C")
    void structMembersArePublicByDefault() {
        ClassDeclaration cls = classNamed("struct Point { int x; }", "Point");
        assertEquals(Visibility.PUBLIC, fieldVisibility(cls, "x"));
    }

    @Test
    void explicitModifierBeatsTheDefault() {
        ClassDeclaration cls = classNamed("class Counter { public int n; }", "Counter");
        assertEquals(Visibility.PUBLIC, fieldVisibility(cls, "n"));
    }

    @Test
    @DisplayName("float literals parse in expression position")
    void floatLiteralsParse() {
        var result = TestCompiler.parse("int main() { float x = 1.5; return 0; }");
        assertFalse(result.hasErrors(), result.rendered());
    }

    @Test
    void declarationsCarrySourceLocations() {
        var result = TestCompiler.parse("\n\nint compute() { return 1; }");
        FunctionDeclaration func = null;
        for (ASTNode node : result.ast()) {
            if (node instanceof FunctionDeclaration f) func = f;
        }
        assertNotNull(func);
        assertEquals(3, func.line, "declarations need a location for diagnostics to point at");
    }

    @Test
    void genericParametersAreRecorded() {
        ClassDeclaration cls = classNamed("class Pair<A, B> { public A first; }", "Pair");
        assertEquals(2, cls.genericParams.size());
    }

    @Test
    void interfacesAreRecorded() {
        ClassDeclaration cls = classNamed(
            "interface Shape { int area(); } class Circle implements Shape { public int area() { return 1; } }",
            "Circle");
        assertTrue(cls.interfaces.contains("Shape"));
    }

    @Test
    void forbiddenHeadersAreRejected() {
        Config config = TestCompiler.config(c -> c.put("forbidden-headers", "stdio"));
        var result = TestCompiler.parse("import stdio;", config);
        assertTrue(result.hasErrors(), "a forbidden import should be reported");
    }

    @Test
    void unexpectedTokensProduceAnError() {
        var result = TestCompiler.parse("int main() { @@@ }");
        assertTrue(result.hasErrors());
    }
}
