package hydro.bolt.sema;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TypesTest {

    @Test
    void recognisesPointerShape() {
        assertTrue(Types.isPointer("Vec2*"));
        assertFalse(Types.isPointer("Vec2"));
        assertEquals("Vec2", Types.deref("Vec2*"));
        assertEquals("Vec2*", Types.pointerTo("Vec2"));
    }

    @Test
    void baseNameStripsPointersAndGenerics() {
        assertEquals("Pair", Types.baseName("Pair<int, int>*"));
        assertEquals("int", Types.baseName("int"));
    }

    @Test
    @DisplayName("unknown types are compatible with everything")
    void unknownIsAlwaysCompatible() {
        assertTrue(Types.assignable(null, "int"));
        assertTrue(Types.assignable("int", null));
    }

    @Test
    void numericConversionsAreAllowed() {
        assertTrue(Types.assignable("int", "float"));
        assertTrue(Types.assignable("float", "int"));
        assertTrue(Types.assignable("char", "int"));
    }

    @Test
    void stringAndCharPointerInterchange() {
        assertTrue(Types.assignable("string", "char*"));
        assertTrue(Types.assignable("char*", "string"));
    }

    @Test
    void unrelatedShapesAreRejected() {
        assertFalse(Types.assignable("string", "int"));
        assertFalse(Types.assignable("Vec2", "int"));
        assertFalse(Types.assignable("Vec2*", "Point*"));
    }

    @Test
    @DisplayName("void* is compatible with any pointer, as in C")
    void voidPointerIsUniversal() {
        assertTrue(Types.assignable("void*", "Vec2*"));
        assertTrue(Types.assignable("Vec2*", "void*"));
    }

    @Test
    void narrowingIsDetected() {
        assertTrue(Types.narrows("long", "int"));
        assertTrue(Types.narrows("float", "int"));
        assertTrue(Types.narrows("int", "char"));

        assertFalse(Types.narrows("int", "long"));
        assertFalse(Types.narrows("int", "int"));
        assertFalse(Types.narrows("Vec2", "Vec2"));
    }

    @Test
    void arithmeticPicksTheWiderType() {
        assertEquals("float", Types.arithmeticResult("int", "float"));
        assertEquals("long", Types.arithmeticResult("long", "int"));
        assertEquals("int", Types.arithmeticResult("int", "char"));
    }

    @Test
    void scalarsCanBeTestedForTruth() {
        assertTrue(Types.isScalar("int"));
        assertTrue(Types.isScalar("Vec2*"));
        assertTrue(Types.isScalar(null));
        assertFalse(Types.isScalar("Vec2"));
    }
}
