#include <string.h>
#include <stdlib.h>
#include <stdarg.h>
#include <stdio.h>

typedef struct Foo Foo;

static char* __bolt_string_copy(const char* s) {
    if (!s) return NULL;
    char* res = (char*)malloc(strlen(s) + 1);
    if (res) strcpy(res, s); return res;
}

static void __bolt_string_assign(char** dest, const char* src) {
    if (*dest == src) return;
    if (*dest) free(*dest);
    *dest = src ? __bolt_string_copy(src) : NULL;
}

static char* __bolt_string_concat(const char* a, const char* b) {
    if (!a) a = ""; if (!b) b = "";
    int len = strlen(a) + strlen(b);
    char* res = (char*)malloc(len + 1);
    if (res) { strcpy(res, a); strcat(res, b); }
    return res;
}

static char* __bolt_concat_int_str(int i, const char* s) {
    if (!s) s = "";
    char buf[65536];
    sprintf(buf, "%d", i);
    int len = strlen(buf) + strlen(s);
    char* res = (char*)malloc(len + 1);
    if (res) { strcpy(res, buf); strcat(res, s); }
    return res;
}

static char* __bolt_concat_str_int(const char* s, int i) {
    if (!s) s = "";
    char buf[65536];
    sprintf(buf, "%d", i);
    int len = strlen(s) + strlen(buf);
    char* res = (char*)malloc(len + 1);
    if (res) { strcpy(res, s); strcat(res, buf); }
    return res;
}

static char* __bolt_string_concat_n(int n, ...) {
    va_list args;
    va_start(args, n);
    int total_len = 0;
    const char** strs = (const char**)malloc(n * sizeof(char*));
    for (int i = 0; i < n; i++) {
        strs[i] = va_arg(args, const char*);
        if (strs[i]) total_len += strlen(strs[i]);
    }
    va_end(args);
    char* res = (char*)malloc(total_len + 1);
    if (res) {
        res[0] = '\0';
        for (int i = 0; i < n; i++) {
            if (strs[i]) strcat(res, strs[i]);
        }
    }
    free(strs);
    return res;
}

int main();

struct Foo {

    /* private */ int secret;

};


int main()  {
    Foo f = ((Foo*)calloc(1, sizeof(Foo)));
    f.secret = 10;
    {
        int _ret = 0;
                return _ret;
    }
}
