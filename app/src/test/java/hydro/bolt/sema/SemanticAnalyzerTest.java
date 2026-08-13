package hydro.bolt.sema;

import hydro.bolt.Config;
import hydro.bolt.TestCompiler;
import hydro.bolt.parser.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SemanticAnalyzerTest {
    @Nested
    @DisplayName("rejects broken programs")
    class Rejects {

        @Test
        void undefinedFunction() {
            var result = TestCompiler.analyze("int main() { nope(1); return 0; }");
            assertTrue(result.has(ErrorCode.UNDEFINED_SYMBOL), result.rendered());
        }
        @Test
        void undefinedVariable() {
            var result = TestCompiler.analyze("int main() { return missing; }");
            assertTrue(result.has(ErrorCode.UNDEFINED_SYMBOL), result.rendered());
        }
        @Test
        void unknownType() {
            var result = TestCompiler.analyze("int main() { Widget w; return 0; }");
            assertTrue(result.has(ErrorCode.UNKNOWN_TYPE), result.rendered());
        }
        @Test
        void assigningStringToInt() {
            var result = TestCompiler.analyze("""
                int main() {
                    int x;
                    x = "text";
                    return 0;
                }
                """);
            assertTrue(result.has(ErrorCode.TYPE_MISMATCH), result.rendered());
        }
        @Test
        void tooFewArguments() {
            var result = TestCompiler.analyze("""
                int add(int a, int b) { return a + b; }
                int main() { return add(1); }
                """);
            var diagnostic = result.first(ErrorCode.ARGUMENT_COUNT_MISMATCH);
            assertTrue(diagnostic.message.contains("takes 2 arguments but 1 was given"),
                diagnostic.message);
        }
        @Test
        void tooManyArguments() {
            var result = TestCompiler.analyze("""
                int add(int a, int b) { return a + b; }
                int main() { return add(1, 2, 3); }
                """);
            assertTrue(result.has(ErrorCode.ARGUMENT_COUNT_MISMATCH), result.rendered());
        }
        @Test
        void unknownMember() {
            var result = TestCompiler.analyze("""
                class Vec { public int x; }
                int main() {
                    Vec v = new Vec();
                    v.y = 1;
                    return 0;
                }
                """);
            assertTrue(result.has(ErrorCode.UNKNOWN_MEMBER), result.rendered());
        }
        @Test
        void privateMemberFromOutside() {
            var result = TestCompiler.analyze("""
                class Counter { private int hidden; }
                int main() {
                    Counter c = new Counter();
                    c.hidden = 1;
                    return 0;
                }
                """);
            assertTrue(result.has(ErrorCode.PRIVATE_MEMBER), result.rendered());
        }
        @Test
        void missingInterfaceMethod() {
            var result = TestCompiler.analyze("""
                interface Shape {
                    int area();
                    void draw();
                }
                class Circle implements Shape {
                    public int area() { return 1; }
                }
                """);
            var diagnostic = result.first(ErrorCode.INTERFACE_NOT_IMPLEMENTED);
            assertTrue(diagnostic.message.contains("draw"), diagnostic.message);
        }
        @Test
        void genericArityMismatch() {
            var result = TestCompiler.analyze("""
                class Pair<A, B> { public A first; }
                int main() {
                    Pair<int> p;
                    return 0;
                }
                """);
            assertTrue(result.has(ErrorCode.GENERIC_ARITY_MISMATCH), result.rendered());
        }
        @Test
        void functionNeverReturnsAValue() {
            var result = TestCompiler.analyze("int compute(int n) { int x = n; }");
            assertTrue(result.has(ErrorCode.MISSING_RETURN), result.rendered());
        }
        @Test
        void duplicateLocalDeclaration() {
            var result = TestCompiler.analyze("""
                int main() {
                    int x = 1;
                    int x = 2;
                    return x;
                }
                """);
            assertTrue(result.has(ErrorCode.DUPLICATE_DECLARATION), result.rendered());
        }
        @Test
        void continueOutsideLoop() {
            var result = TestCompiler.analyze("int main() { continue; return 0; }");
            assertTrue(result.has(ErrorCode.CONTINUE_OUTSIDE_LOOP), result.rendered());
        }
        @Test
        void breakOutsideLoop() {
            var result = TestCompiler.analyze("int main() { break; return 0; }");
            assertTrue(result.has(ErrorCode.BREAK_OUTSIDE_LOOP), result.rendered());
        }
        @Test
        void unreachableCodeAfterReturn() {
            var result = TestCompiler.analyze("""
                int main() {
                    return 1;
                    int dead = 2;
                }
                """);
            assertTrue(result.has(ErrorCode.UNREACHABLE_CODE), result.rendered());
        }
    }

    @Nested
    @DisplayName("config-gated rules")
    class Gated {

        @Test
        void newIsRejectedInNoHeapMode() {
            Config config = TestCompiler.config(c -> c.put("no-heap", "true"));
            var result = TestCompiler.analyze("""
                class Box { public int v; }
                int main() { Box b = new Box(); return 0; }
                """, config);
            assertTrue(result.has(ErrorCode.NO_HEAP_VIOLATION), result.rendered());
        }
        @Test
        void stringIsRejectedInNoHeapMode() {
            Config config = TestCompiler.config(c -> c.put("no-heap", "true"));
            var result = TestCompiler.analyze("int main() { string s = \"x\"; return 0; }", config);
            assertTrue(result.has(ErrorCode.NO_HEAP_VIOLATION), result.rendered());
        }
        @Test
        void heapIsFineByDefault() {
            var result = TestCompiler.analyze("int main() { string s = \"x\"; return 0; }");
            assertFalse(result.has(ErrorCode.NO_HEAP_VIOLATION), result.rendered());
        }
        @Test
        void recursionIsRejectedWhenDisabled() {
            Config config = TestCompiler.config(c -> c.put("allow-recursion", "false"));
            var result = TestCompiler.analyze(
                "int fact(int n) { return fact(n); }", config);
            assertTrue(result.has(ErrorCode.RECURSION_FORBIDDEN), result.rendered());
        }
        @Test
        void recursionIsAllowedByDefault() {
            var result = TestCompiler.analyze("int fact(int n) { return fact(n); }");
            assertFalse(result.has(ErrorCode.RECURSION_FORBIDDEN), result.rendered());
        }
        @Test
        void lambdasAreRejectedByDefault() {
            var result = TestCompiler.analyze("int main() { fn() { }(); return 0; }");
            assertTrue(result.has(ErrorCode.LAMBDAS_FORBIDDEN), result.rendered());
        }
        @Test
        void lambdasAreAllowedWhenEnabled() {
            Config config = TestCompiler.config(c -> c.put("lambdas", "true"));
            var result = TestCompiler.analyze("int main() { fn() { }(); return 0; }", config);
            assertFalse(result.has(ErrorCode.LAMBDAS_FORBIDDEN), result.rendered());
        }
        @Test
        void operatorOverloadingIsRejectedByDefault() {
            var result = TestCompiler.analyze("""
                class Vec { public int x; }
                operator Vec Vec + Vec { return a; }
                """);
            assertTrue(result.has(ErrorCode.OPERATOR_OVERLOADING_FORBIDDEN), result.rendered());
        }
        @Test
        void operatorOverloadingIsAllowedWhenEnabled() {
            Config config = TestCompiler.config(c -> c.put("operator-overloading", "true"));
            var result = TestCompiler.analyze("""
                class Vec { public int x; }
                operator Vec Vec + Vec { return a; }
                """, config);
            assertFalse(result.has(ErrorCode.OPERATOR_OVERLOADING_FORBIDDEN), result.rendered());
        }
        @Test
        void strictTypingDowngradesToWarningsWhenOff() {
            Config config = TestCompiler.config(c -> c.put("strict-typing", "false"));
            var result = TestCompiler.analyze("""
                int main() {
                    int x;
                    x = "text";
                    return 0;
                }
                """, config);
            assertTrue(result.has(ErrorCode.TYPE_MISMATCH), result.rendered());
            assertFalse(result.hasErrors(), "type rules should be advisory when strict-typing is off");
        }
    }

    @Nested
    @DisplayName("stays quiet on valid programs")
    class Accepts {

        @Test
        void helloWorld() {
            var result = TestCompiler.analyze("""
                import std.io;
                int main() {
                    printf("Hello, Bolt!\\n");
                    return 0;
                }
                """);
            assertFalse(result.hasErrors(), result.rendered());
        }
        @Test
        void printfWithoutAnImportStillResolves() {
            var result = TestCompiler.analyze("int main() { printf(\"hi\"); return 0; }");
            assertFalse(result.hasErrors(), result.rendered());
        }
        @Test
        void newAssignedToValueTyped() {
            var result = TestCompiler.analyze("""
                class Vec { public int x; }
                int main() {
                    Vec v = new Vec();
                    v.x = 3;
                    return v.x;
                }
                """);
            assertFalse(result.hasErrors(), result.rendered());
        }
        @Test
        void classSatisfyingItsInterface() {
            var result = TestCompiler.analyze("""
                interface Shape { int area(); }
                class Circle implements Shape {
                    private int r;
                    public int area() { return self.r * self.r; }
                }
                """);
            assertFalse(result.hasErrors(), result.rendered());
        }
        @Test
        void classIsAssignableToItsInterface() {
            var result = TestCompiler.analyze("""
                interface Shape { int area(); }
                class Circle implements Shape {
                    public int area() { return 1; }
                }
                int main() {
                    Circle c;
                    Shape s;
                    s = c;
                    return 0;
                }
                """);
            assertFalse(result.hasErrors(), result.rendered());
        }
        @Test
        void privateMemberFromInsideTheClass() {
            var result = TestCompiler.analyze("""
                class Counter {
                    private int n;
                    public int get() { return self.n; }
                }
                """);
            assertFalse(result.hasErrors(), result.rendered());
        }
        @Test
        void structFieldsArePublicByDefault() {
            var result = TestCompiler.analyze("""
                struct Point { int x; int y; }
                int main() {
                    Point p;
                    p.x = 1;
                    return p.x;
                }
                """);
            assertFalse(result.hasErrors(), result.rendered());
        }
        @Test
        void loopCounterInRawCIsNotFlaggedUnused() {

            var result = TestCompiler.analyze("""
                int total(int n) {
                    int sum = 0;
                    int i;
                    for (i = 0; i < n; i = i + 1) { sum = sum + i; }
                    return sum;
                }
                """);
            assertFalse(result.has(ErrorCode.UNUSED_VARIABLE), result.rendered());
        }
        @Test
        void stringConcatenationWithAnInt() {
            var result = TestCompiler.analyze("""
                int main() {
                    int count = 42;
                    string s = "Count: " + count;
                    return 0;
                }
                """);
            assertFalse(result.hasErrors(), result.rendered());
        }
        @Test
        void genericsWithMatchingArity() {
            var result = TestCompiler.analyze("""
                class Pair<A, B> {
                    public A first;
                    public B second;
                }
                int main() {
                    Pair<int, int> p = new Pair<int, int>();
                    return 0;
                }
                """);
            assertFalse(result.hasErrors(), result.rendered());
        }
        @Test
        void implBlockMethodsCountAsMembers() {
            var result = TestCompiler.analyze("""
                class Point { public int x; }
                impl Point {
                    public int get() { return self.x; }
                }
                int main() {
                    Point p = new Point();
                    return p.get();
                }
                """);
            assertFalse(result.hasErrors(), result.rendered());
        }
        @Test
        void unparsedImportSuppressesUndefinedNames() {

            var result = TestCompiler.analyze("""
                import mylib.helpers;
                int main() { return helperFunction(1); }
                """);
            assertFalse(result.hasErrors(), result.rendered());
        }
        @Test
        void mainMayFallOffTheEnd() {
            var result = TestCompiler.analyze("int main() { }");
            assertFalse(result.has(ErrorCode.MISSING_RETURN), result.rendered());
        }
        @Test
        @DisplayName("break inside a loop is not an undefined name")
        void breakInsideLoopIsFine() {

            var result = TestCompiler.analyze("""
                int f(int n) {
                    while (n > 0) {
                        if (n == 5) { break; }
                        n = n - 1;
                    }
                    return n;
                }
                """);
            assertFalse(result.hasErrors(), result.rendered());
        }
    }

    @Nested
    @DisplayName("literal typing")
    class Literals {

        @Test
        void recognisesLiteralForms() {
            assertEquals("int", SemanticAnalyzer.literalType("42"));
            assertEquals("int", SemanticAnalyzer.literalType("0xFF"));
            assertEquals("int", SemanticAnalyzer.literalType("0b1010"));
            assertEquals("long", SemanticAnalyzer.literalType("42L"));
            assertEquals("float", SemanticAnalyzer.literalType("1.5"));
            assertEquals("string", SemanticAnalyzer.literalType("\"hello\""));
            assertEquals("char", SemanticAnalyzer.literalType("'a'"));
            assertEquals("int", SemanticAnalyzer.literalType("true"));
        }
        @Test
        void leavesNonLiteralsUnknown() {
            assertNull(SemanticAnalyzer.literalType("for ( i = 0 ; i < n ; i ++ )"));
            assertNull(SemanticAnalyzer.literalType("someIdentifier"));
        }
    }

    @Test
    @DisplayName("identifier extraction ignores string contents")
    void extractIdentifiersSkipsLiterals() {
        var found = new java.util.HashSet<String>();
        SemanticAnalyzer.extractIdentifiers("printf ( \"total notAName\" , total )", found);
        assertTrue(found.contains("printf"));
        assertTrue(found.contains("total"));
        assertFalse(found.contains("notAName"), "names inside string literals are not references");
    }
}
