## <img src="bolt-vscode\icon.png" style="width: 15px"> <span style="color:#913311;">Bolt</span> <img src="bolt-vscode\icon.png" style="width: 15px">
C++ abstractions without the debt. A C superset with various abstractions from C++ and Java which transpiles into C.

Note: Bolt is very much in beta, expect bugs

### <span style="color:#913311;">Compat, building, etc
Only tested on Windows, i see no reason it wouldnt work on Linux/MacOS

I use MS Java 21.0.9 for building and running

---
### <span style="color:#913311;">Planned features
- a better bolt std library

### <span style="color:#913311;">Features

#### <span style="color:#913311;">Classes

Classes group fields and methods together. Methods receive an implicit `self` pointer. Use `new` to heap-allocate and `delete` to free. Class methods are private by default, and fields are private by default.

Access modifiers:
- `public` - accessible from anywhere
- `private` - accessible only within the class

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
    Vec2 v = (*(Vec2*)malloc(sizeof(Vec2)));
    v.x = 3; v.y = 4;
    __boltN4Vec25printEv(&v);
    free(v);
}
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

```cpp
import stdio;

int main() {
    int x = 10;

    // Simple lambda
    printf("5 + 3 = %d\n", fn(int a, int b) { printf("Add lambda: %d\n", a + b); }(5, 3));
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

```ini
# Name mangling (default: true)
mangle=true

# Disallow heap allocation - new/delete/string become errors (default: false)
no-heap=false

# Emit source traceability comments in generated C (default: false)
traceability=true

# Enable operator overloading (default: false)
operator-overloading=false

# Target C standard (default: c99)
c-standard=c99

# Allow recursive functions (default: true)
allow-recursion=true

# Comma-separated list of forbidden import paths
forbidden-headers=

# Enforce strict type checking (default: false)
strict-typing=false

# Prefix used for mangled names (default: __bolt)
mangle-prefix=__bolt

# Code style & formatting
indent-size=4
indent-style=space
brace-style=k&r
line-width=80

# Memory & resource management
string-buffer-size=256
static-string-pool=false

# Transpiler behavior & dx
max-errors=10
no-std-includes=false
no-string-helpers=false
verbose=false
```
