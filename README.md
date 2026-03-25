## Bolt
C++ abstractions without the debt. A C superset with various abstractions from C++ and Java which transpiles into C.

### Compat, building, etc
Only tested on Windows, i see no reason it wouldnt work on Linux/MacOS

I use MS Java 21.0.9 for building and running

Quick examples:<br>
Bolt code:
```cpp
import stdio;

class Foo {
    int number;

    void foo() {
        printf("Hello, World! The number is %d\n", self.number);
    }
}

int main() {
    Foo foo = new Foo();
    foo.number = 42;
    foo.foo();
}
```
Generated C (partial):
```c
void __boltN3Foo3fooEv(Foo* self);
int main();

struct Foo {
    int number;
};

#include "stdio.h"
void __boltN3Foo3fooEv(Foo* self)  {
    printf("Hello, World! The number is %d\n", self->number);
}
int main()  {
    Foo foo = (*(Foo*)malloc(sizeof(Foo)));
    foo.number = 42;
    __boltN3Foo3fooEv(&foo);
}
```
