#include <string.h>
#include <stdlib.h>
#include <stdarg.h>
#include <stdio.h>


static char* __bolt_string_copy(const char* s) {
    if (!s) return NULL;
    size_t len = strlen(s);
    char* res = (char*)malloc(len + 1);
    if (res) { memcpy(res, s, len); res[len] = '\0'; }
    return res;
}

static void __bolt_string_assign(char** dest, const char* src) {
    if (*dest == src) return;
    if (*dest) free(*dest);
    *dest = src ? __bolt_string_copy(src) : NULL;
}

static char* __bolt_string_concat(const char* a, const char* b) {
    if (!a) a = ""; if (!b) b = "";
    size_t len_a = strlen(a);
    size_t len_b = strlen(b);
    char* res = (char*)malloc(len_a + len_b + 1);
    if (res) { memcpy(res, a, len_a); memcpy(res + len_a, b, len_b); res[len_a + len_b] = '\0'; }
    return res;
}

static char* __bolt_concat_int_str(int i, const char* s) {
    if (!s) s = "";
    char buf[65536];
    int len = snprintf(buf, sizeof(buf), "%d", i);
    size_t len_s = strlen(s);
    char* res = (char*)malloc(len + len_s + 1);
    if (res) { memcpy(res, buf, len); memcpy(res + len, s, len_s); res[len + len_s] = '\0'; }
    return res;
}

static char* __bolt_concat_str_int(const char* s, int i) {
    if (!s) s = "";
    char buf[65536];
    int len = snprintf(buf, sizeof(buf), "%d", i);
    size_t len_s = strlen(s);
    char* res = (char*)malloc(len_s + len + 1);
    if (res) { memcpy(res, s, len_s); memcpy(res + len_s, buf, len); res[len_s + len] = '\0'; }
    return res;
}

static char* __bolt_string_concat_n(int n, ...) {
    va_list args;
    va_start(args, n);
    size_t total_len = 0;
    const char** strs = (const char**)malloc(n * sizeof(char*));
    if (!strs) { va_end(args); return NULL; }
    for (int i = 0; i < n; i++) {
        strs[i] = va_arg(args, const char*);
        if (strs[i]) total_len += strlen(strs[i]);
    }
    va_end(args);
    char* res = (char*)malloc(total_len + 1);
    if (res) {
        char* p = res;
        for (int i = 0; i < n; i++) {
            if (strs[i]) {
                size_t len = strlen(strs[i]);
                memcpy(p, strs[i], len);
                p += len;
            }
        }
        *p = '\0';
    }
    free(strs);
    return res;
}

int main();


#include "imports/helper.h"
#include <stdio.h>
int main() /* line 6 */  {
    /* line 7 */     printf("%d\n", add(19, 23));
    /* line 8 */     return 0;
}
