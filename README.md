## Bolt
C++ abstractions without the debt. A C superset with various abstractions from C++ and Java which transpiles into C.

### Compat, building, etc
Only tested on Windows, i see no reason it wouldnt work on Linux/MacOS

I use MS Java 21.0.9 for building and running

---
### Good first issues
- generated `string` code is very slow and memory inefficient
- i believe there's some leftover debug prints
- LSP: member completion, currently suggests form all functions instead of from that type
- add support for hex/octal/binary in the tokenizer (0x etc)
- bitwise + modulo assignment operators are missing
- document some missing bolt.cfg settings (Just in `Config.java` i think)
- LSP: cross file goto definition
- more std library support on includes

### Planned features
<small>feel free to make or work on any of these still</small>
- header file generation 
- public and private methods
- interfaces
- lambdas
- a bolt std library

### Features

#### Classes

Classes group fields and methods together. Methods receive an implicit `self` pointer. Use `new` to heap-allocate and `delete` to free.

```cpp
import stdio;

class Vec2 {
    int x;
    int y;

    void print() {
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

#### init / dinit (constructors & destructors)

Define `init` and `dinit` methods on a class for automatic construction and destruction. Stack allocated instances have `dinit` called automatically at end of scope.

```cpp
import stdio;
import stdlib;

class Buffer {
    int* data;

    Buffer init(int size) {
        self.data = (int*)malloc(size * sizeof(int));
    }

    void dinit() {
        free(self.data);
    }
}

int main() {
    Buffer b = Buffer(64);
    // dinit called automatically when b goes out of scope
}
```

---

#### impl blocks

Add methods to a type outside its original declaration - useful for separating interface from implementation, or extending existing types.

```cpp
import stdio;

class Point {
    int x;
    int y;
}

impl Point {
    void print() {
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

#### Generics

Classes and functions can be parameterized with type arguments. Bolt treats these the same as C++ templates.

```cpp
import stdio;

class Pair<A, B> {
    A first;
    B second;
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
void swap<T>(T* a, T* b) {
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

#### Operator overloading

Define custom behavior for operators on your types. Unary form: `operator ReturnType Op Arg`. Binary form: `operator ReturnType Left Op Right`.

```cpp
import stdio;

class Vec2 {
    int x;
    int y;
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

#### string type

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

#### Packages & imports

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

#### Decorators

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

#### bolt.cfg

Project-level configuration file. Place `bolt.cfg` in your working directory.

```ini
# Name mangling (default: true)
mangle=true

# Disallow heap allocation - new/delete/string become errors (default: false)
no-heap=false

# Emit source traceability comments in generated C (default: false)
traceability=true

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
```
