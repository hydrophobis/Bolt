## <img src="bolt-vscode\icon.png" style="width: 15px"> <span style="color:#913311;">Bolt</span> <img src="bolt-vscode\icon.png" style="width: 15px">
C++ abstractions without the debt. A C superset with various abstractions from C++ and Java which transpiles into C.

Note: Bolt is very much in beta, expect bugs

### <span style="color:#913311;">Compat, building, etc
Only tested on Windows, i see no reason it wouldnt work on Linux/MacOS

I use MS Java 21.0.9 for building and running

```sh
./gradlew build          # compile and run the unit tests
./run_tests.sh           # transpile, compile, and run the .bolt test programs
```

The end-to-end suite needs `clang` on PATH. CI runs both on every push.

---
### <span style="color:#913311;">Planned features
- a bolt std library wrapping the C standard library

### <span style="color:#913311;">Features

#### <span style="color:#913311;">Classes

Classes group fields and methods together. Methods receive an implicit `self` pointer. Use `new` to construct and `delete` to destroy; whether that involves the heap depends on whether you declare the variable as a value or a pointer (see below).

Access modifiers:
- `public` - accessible from anywhere
- `private` - accessible only within the class

Members with no modifier default to `private` in a `class` and `public` in a
`struct`, matching C++. Access is enforced by the compiler, so reaching for a
private field from outside its class is an error (`E2007`).

```cpp
import stdio;

class Vec2 {
    public int x;
    public int y;

    public void print() {
        printf("(%d, %d)\n", self.x, self.y);
    }
}

int main() {
    Vec2 v = new Vec2();
    v.x = 3;
    v.y = 4;
    v.print();
    delete v;
}
```

Generated C (partial):
```c
struct Vec2 { int x; int y; };

void __boltN4Vec25printEv(Vec2* self) {
    printf("(%d, %d)\n", self->x, self->y);
}
int main() {
    Vec2 v = (Vec2){0};
    v.x = 3;
    v.y = 4;
    __boltN4Vec25printEv(&v);
    (void)0;
}
```

Declaring the variable as a value gives you an object, constructed in place -
nothing is allocated, so nothing can leak, and `delete` on it just runs `dinit`
if the class has one. Declare it as a pointer to get a real heap allocation that
`delete` frees:

```cpp
Vec2* v = new Vec2();
v.x = 3;
v.print();
delete v;          // calls dinit if present, then free()
```

```c
Vec2* v = ((Vec2*)calloc(1, sizeof(Vec2)));
v->x = 3;
__boltN4Vec25printEv(v);
free(v);
```

---

#### <span style="color:#913311;">init / dinit (constructors & destructors)

Define `init` and `dinit` methods on a class for automatic construction and destruction. Stack allocated instances have `dinit` called automatically at end of scope.

```cpp
import stdio;
import stdlib;

class Buffer {
    public int* data;

    public Buffer init(int size) {
        self.data = (int*)malloc(size * sizeof(int));
    }

    public void dinit() {
        free(self.data);
    }
}

int main() {
    Buffer b = Buffer(64);
    // dinit called automatically when b goes out of scope
}
```

---

#### <span style="color:#913311;">impl blocks

Add methods to a type outside its original declaration - useful for separating interface from implementation, or extending existing types.

```cpp
import stdio;

class Point {
    public int x;
    public int y;
}

impl Point {
    public void print() {
        printf("Point(%d, %d)\n", self.x, self.y);
    }
}

int main() {
    Point p = new Point();
    p.x = 1;
    p.y = 2;
    p.print();
    delete p;
}
```

---

#### <span style="color:#913311;">Interfaces

Define interfaces with method signatures that classes must implement. Supports dynamic dispatch through interface types.

```cpp
import stdio;

interface Shape {
    void draw();
    int getArea();
}

class Circle implements Shape {
    int radius;

    public void init(int r) {
        self.radius = r;
    }

    public void draw() {
        printf("Drawing Circle with radius: %d\n", self.radius);
    }

    public int getArea() {
        return 3 * self.radius * self.radius;
    }
}

void render(Shape s) {
    s.draw();
    printf("Area: %d\n", s.getArea());
}

int main() {
    Circle c;
    c.init(5);
    render(c);
}
```

---

#### <span style="color:#913311;">Generics

Classes and functions can be parameterized with type arguments. Bolt treats these the same as C++ templates.

```cpp
import stdio;

class Pair<A, B> {
    public A first;
    public B second;
}

int main() {
    Pair<int, int> p = new Pair<int, int>();
    p.first = 10;
    p.second = 20;
    printf("%d %d\n", p.first, p.second);
    delete p;
}
```

Generic functions work the same way:

```cpp
public void swap<T>(T* a, T* b) {
    T tmp = *a;
    *a = *b;
    *b = tmp;
}

int main() {
    int x = 1, y = 2;
    swap<int>(&x, &y);
}
```

---

#### <span style="color:#913311;">Operator overloading

Define custom behavior for operators on your types. Unary form: `operator ReturnType Op Arg`. Binary form: `operator ReturnType Left Op Right`.

**Operator overloading is off by default.** Set `operator-overloading=true` in
`bolt.cfg` to enable it.

```cpp
import stdio;

class Vec2 {
    public int x;
    public int y;
}

operator Vec2 Vec2 + Vec2 {
    Vec2 result = new Vec2();
    result.x = a.x + b.x;
    result.y = a.y + b.y;
    return result;
}

operator void ! Vec2 {
    printf("Vec2(%d, %d)\n", a.x, a.y);
}

int main() {
    Vec2 u = new Vec2();
    u.x = 1; u.y = 2;
    Vec2 v = new Vec2();
    v.x = 3; v.y = 4;
    Vec2 w = u + v;
    !w;
}
```

Generated C (partial):
```c
Vec2 __bolt_operator_plus_Vec2_Vec2(Vec2 a, Vec2 b) { ... }
void __bolt_operator_lnot_Vec2(Vec2 a) { ... }
```

---

#### <span style="color:#913311;">Lambdas

Anonymous functions that can capture variables from their enclosing scope.

**Lambdas are off by default.** Set `lambdas=true` in `bolt.cfg` (or pass
`--lambdas`) to enable them, otherwise using one is a compile error:

```ini
lambdas=true
```

The return type is inferred from the body.

```cpp
import stdio;

int main() {
    printf("5 + 3 = %d\n", fn(int a, int b) { return a + b; }(5, 3));

    fn(int v) { printf("value: %d\n", v); }(42);
}
```

---

#### <span style="color:#913311;">string type

`string` is a managed `char*` with built-in concatenation via `+`, `+=`, and value-equality via `==` / `!=`.

```cpp
import stdio;

int main() {
    string first = "Hello";
    string last = "World";
    string msg = first + ", " + last + "!";
    printf("%s\n", msg);

    if (msg == "Hello, World!") {
        printf("match\n");
    }
}
```

String + int concatenation is also supported:

```cpp
int count = 42;
string result = "Count: " + count;
```

---

#### <span style="color:#913311;">Number literals

Support for hexadecimal, octal, and binary number literals.

```cpp
int hex = 0xFF;        // 255
int octal = 0755;      // 493
int binary = 0b1010;   // 10
```

---

#### <span style="color:#913311;">Assignment operators

All compound assignment operators are supported, including bitwise and modulo.

```cpp
int a = 10;
int b = 3;

a += 5;    // addition
a -= 2;    // subtraction
a *= 3;    // multiplication
a /= 4;    // division
a %= b;    // modulo

a &= 7;    // bitwise AND
a |= 3;    // bitwise OR
a ^= 5;    // bitwise XOR
a <<= 2;   // left shift
a >>= 1;   // right shift
```

---

#### <span style="color:#913311;">Packages & imports

`package` declares the current module's namespace. `import` maps to a C `#include`. Dot-separated paths map to directory separators.

```cpp
package math.utils;

import stdio;
import std.stdlib;
import mylib.helpers;  // -> #include "mylib/helpers.h"
```

Standard library shortcuts:

| Bolt import   | C include        |
|---------------|------------------|
| `std.io`      | `<stdio.h>`      |
| `std.stdlib`  | `<stdlib.h>`     |
| `std.math`    | `<math.h>`       |
| `std.string`  | `<string.h>`     |
| `std.time`    | `<time.h>`       |

---

#### <span style="color:#913311;">Decorators

Decorators annotate declarations with metadata. Built-in decorators:
- `@mangle` - (inconsistent currrently) mangles the function name
- `@inline` - works the same as the C inline keyword
- `@manual` - opt a variable out of automatic `dinit` cleanup
- `@bind(target)` - forward a method call to another function

```cpp
@manual
Buffer b = Buffer(64);  // dinit will NOT be called automatically

@bind(puts)
void log(string msg);   // log(msg) -> puts(msg)
```

---

#### <span style="color:#913311;">bolt.cfg

Project-level configuration file. Place `bolt.cfg` in your working directory.
Any setting can also be overridden per-invocation: `--key=value`, or `--key` on
its own to set a boolean to true.

Unknown keys and out-of-range values are reported with a
suggestion when a key looks like a typo:

```
bolt.cfg:2:1: warning[W3001]: unknown setting 'strict_typing'; did you mean 'strict-typing'?
  |
2 | strict_typing=true
  | ^^^^^^^^^^^^^
```

##### Language features

| Setting | Default | Effect |
|---|---|---|
| `strict-typing` | `true` | Type-rule violations are errors. Set `false` to demote them to warnings while migrating existing code. |
| `lambdas` | `false` | Allow lambda expressions. |
| `operator-overloading` | `false` | Allow `operator` declarations. |
| `allow-recursion` | `true` | Allow a function to call itself. |
| `no-heap` | `false` | Forbid `new`, `delete`, and managed `string`. |
| `forbidden-headers` | *(empty)* | Comma-separated import paths to reject. |

##### Output

| Setting | Default | Effect |
|---|---|---|
| `c-standard` | `c99` | Target C standard: `c89`, `c90`, `c99`, `c11`, `c17`, `c23`. Targeting C89 disables `@inline`, since `inline` is a C99 addition. |
| `mangle` | `true` | Mangle generated names. |
| `mangle-prefix` | `__bolt` | Prefix for mangled names. |
| `traceability` | `false` | Emit `/* line N */` comments mapping generated C back to Bolt source. |
| `no-std-includes` | `false` | Skip the automatic `<stdio.h>`/`<stdlib.h>`/`<string.h>`/`<stdarg.h>` includes. |
| `no-string-helpers` | `false` | Skip the generated `string` runtime helpers. |

##### Formatting

| Setting | Default | Effect |
|---|---|---|
| `indent-size` | `4` | Columns per indent level. |
| `indent-style` | `space` | `space` or `tab`. |
| `brace-style` | `k&r` | `k&r` or `allman`. |
| `line-width` | `80` | Wrap generated lines longer than this at argument commas. `0` disables wrapping. |

##### Memory

| Setting | Default | Effect |
|---|---|---|
| `string-buffer-size` | `256` | Stack buffer size used when formatting numbers into strings. |
| `static-string-pool` | `false` | Deduplicate identical string literals into shared `static const` storage. Copy-on-assign semantics are unchanged. |

##### Diagnostics

| Setting | Default | Effect |
|---|---|---|
| `max-errors` | `10` | Stop after this many errors. `0` means unlimited. Warnings are never capped. |
| `verbose` | `false` | Print the resolved configuration, and stack traces for internal compiler errors. |

---

#### <span style="color:#913311;">Diagnostics

Bolt checks your program before generating C, so mistakes are reported against
your source rather than surfacing later as errors in generated C.

```
tests/shapes.bolt:14:7: error[E2007]: 'radius' is private to 'Circle'
   |
14 |     c.radius = 5;
   |       ^^^^^^
1 error generated.
```

Every diagnostic carries a stable code you can search for. The number tells you
which phase produced it:

| Range | Phase |
|---|---|
| `E0xxx` | Tokenizer |
| `E1xxx` | Parser |
| `E2xxx` / `W2xxx` | Semantic analysis |
| `E3xxx` / `W3xxx` | Configuration |
| `E9xxx` | Internal compiler error, please report these |

Some of what the analyzer catches:

- unknown types, undefined names, and unknown or private class members
- wrong argument counts, and argument, assignment, and return type mismatches
- classes that do not implement an interface they declare
- wrong number of generic type arguments
- value-returning functions that never return a value
- duplicate declarations, unreachable code, shadowed and unused variables
- `new`, `delete`, or `string` used under `no-heap`; recursion under
  `allow-recursion=false`; lambdas or operator overloads while disabled

The analyzer is deliberately conservative. Bolt compiles one file at a time on
top of C, so when it cannot see enough it stays quiet.
A checker you have to argue with is a checker you turn off. (As they say)
