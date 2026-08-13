package hydro.bolt.ast;

import hydro.bolt.ast.misc.*;
import hydro.bolt.parser.ErrorReporter;
import hydro.bolt.tokens.Token;
import hydro.bolt.tokens.TokenType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import hydro.bolt.ast.bolt.*;
import hydro.bolt.ast.decl.*;
import hydro.bolt.ast.type.*;
import hydro.bolt.ast.expr.*;
import hydro.bolt.ast.statement.*;
import hydro.bolt.Config;
import hydro.bolt.parser.Symbol;
import hydro.bolt.parser.SymbolTree;
import hydro.bolt.template.Template;

// almost 1800 lines of sadness and barely functional code generation
// Almost no comments either because Im lazy so good luck me
public class Codegen extends AbstractASTVisitor<String> {
    public StringBuilder builder = new StringBuilder();
    public ASTTree tree;
    public SymbolTree symbols;
    public SymbolTree currentScope;
    public ErrorReporter reporter;
    public List<String> allActiveInstances = new ArrayList<>();
    public List<String> imports = new ArrayList<>();
    public String currentPackage = null;
    public Config config;
    private List<String> currentGenericParams = new ArrayList<>();
    private Map<String, ClassDeclaration> genericClasses = new HashMap<>();
    private Map<String, FunctionDeclaration> genericFunctions = new HashMap<>();
    private Map<String, String> genericMapping = new HashMap<>();
    private Set<String> generatedInstantiations = new HashSet<>();
    private StringBuilder genericBuilder = new StringBuilder();
    private List<String[]> tempStringDecls = new ArrayList<>();
    private int tempCounter = 0;
    private String currentOperatorName = null;
    private int indentLevel = 0;
    private String currentFunctionName = null;
    private String currentFunctionReturnType = null;
    private int recursionDepth = 0;
    private List<LambdaExpression> lambdaFunctions = new ArrayList<>();
    private Map<String, String> lambdaSignatures = new HashMap<>();
    private String cachedIndent = "";
    private final Map<String, String> stringPool = new LinkedHashMap<>();
    private boolean generatingHeader = false;

    public String generate() {
        currentScope = symbols;
        builder.setLength(0);
        lambdaFunctions.clear();
        lambdaSignatures.clear();

        // First pass: collect lambda functions
        for (ASTNode node : tree) {
            collectLambdas(node);
        }

        for (ASTNode node : tree) {
            if (node instanceof ClassDeclaration cls) {
                genericClasses.put(cls.name, cls);
            } else if (node instanceof FunctionDeclaration func && !func.genericParams.isEmpty()) {
                genericFunctions.put(func.name, func);
            }
        }

        for (ASTNode node : tree) {
            if (node instanceof PackageDeclaration pkg) {
                currentPackage = pkg.name;
                break;
            }
        }

        if (!config.getBoolean("no-std-includes")) {
            builder.append("#include <string.h>\n");
            builder.append("#include <stdlib.h>\n");
            builder.append("#include <stdarg.h>\n");
            builder.append("#include <stdio.h>\n\n");
        }

        for (String name : symbols.keySet()) {
            Symbol sym = symbols.get(name);
            if (sym.kind == Symbol.Kind.CLASS || sym.kind == Symbol.Kind.STRUCT) {
                String cName = toCName(name);
                builder.append("typedef struct ").append(cName).append(" ").append(cName).append(";\n");
            }
        }
        builder.append("\n");

        if (!config.getBoolean("no-string-helpers")) {
            builder.append("static char* __bolt_string_copy(const char* s) {\n");
            builder.append("    if (!s) return NULL;\n");
            builder.append("    size_t len = strlen(s);\n");
            builder.append("    char* res = (char*)malloc(len + 1);\n");
            builder.append("    if (res) { memcpy(res, s, len); res[len] = '\\0'; }\n");
            builder.append("    return res;\n");
            builder.append("}\n\n");

            builder.append("static void __bolt_string_assign(char** dest, const char* src) {\n");
            builder.append("    if (*dest == src) return;\n");
            builder.append("    if (*dest) free(*dest);\n");
            builder.append("    *dest = src ? __bolt_string_copy(src) : NULL;\n");
            builder.append("}\n\n");

            builder.append("static char* __bolt_string_concat(const char* a, const char* b) {\n");
            builder.append("    if (!a) a = \"\"; if (!b) b = \"\";\n");
            builder.append("    size_t len_a = strlen(a);\n");
            builder.append("    size_t len_b = strlen(b);\n");
            builder.append("    char* res = (char*)malloc(len_a + len_b + 1);\n");
            builder.append("    if (res) { memcpy(res, a, len_a); memcpy(res + len_a, b, len_b); res[len_a + len_b] = '\\0'; }\n");
            builder.append("    return res;\n");
            builder.append("}\n\n");

            builder.append("static char* __bolt_concat_int_str(int i, const char* s) {\n");
            builder.append("    if (!s) s = \"\";\n");
            builder.append("    char buf[").append(config.get("string-buffer-size")).append("];\n");
            builder.append("    int len = snprintf(buf, sizeof(buf), \"%d\", i);\n");
            builder.append("    size_t len_s = strlen(s);\n");
            builder.append("    char* res = (char*)malloc(len + len_s + 1);\n");
            builder.append("    if (res) { memcpy(res, buf, len); memcpy(res + len, s, len_s); res[len + len_s] = '\\0'; }\n");
            builder.append("    return res;\n");
            builder.append("}\n\n");

            builder.append("static char* __bolt_concat_str_int(const char* s, int i) {\n");
            builder.append("    if (!s) s = \"\";\n");
            builder.append("    char buf[").append(config.get("string-buffer-size")).append("];\n");
            builder.append("    int len = snprintf(buf, sizeof(buf), \"%d\", i);\n");
            builder.append("    size_t len_s = strlen(s);\n");
            builder.append("    char* res = (char*)malloc(len_s + len + 1);\n");
            builder.append("    if (res) { memcpy(res, s, len_s); memcpy(res + len_s, buf, len); res[len_s + len] = '\\0'; }\n");
            builder.append("    return res;\n");
            builder.append("}\n\n");

            builder.append("static char* __bolt_string_concat_n(int n, ...) {\n");
            builder.append("    va_list args;\n");
            builder.append("    va_start(args, n);\n");
            builder.append("    size_t total_len = 0;\n");
            builder.append("    const char** strs = (const char**)malloc(n * sizeof(char*));\n");
            builder.append("    if (!strs) { va_end(args); return NULL; }\n");
            builder.append("    for (int i = 0; i < n; i++) {\n");
            builder.append("        strs[i] = va_arg(args, const char*);\n");
            builder.append("        if (strs[i]) total_len += strlen(strs[i]);\n");
            builder.append("    }\n");
            builder.append("    va_end(args);\n");
            builder.append("    char* res = (char*)malloc(total_len + 1);\n");
            builder.append("    if (res) {\n");
            builder.append("        char* p = res;\n");
            builder.append("        for (int i = 0; i < n; i++) {\n");
            builder.append("            if (strs[i]) {\n");
            builder.append("                size_t len = strlen(strs[i]);\n");
            builder.append("                memcpy(p, strs[i], len);\n");
            builder.append("                p += len;\n");
            builder.append("            }\n");
            builder.append("        }\n");
            builder.append("        *p = '\\0';\n");
            builder.append("    }\n");
            builder.append("    free(strs);\n");
            builder.append("    return res;\n");
            builder.append("}\n\n");
        }

        for (ASTNode node : tree) {
            if (isTypeDeclaration(node)) {
                node.accept(this);
            }
        }

        generateForwardDeclarations();

        for (ASTNode node : tree) {
            if (node instanceof InterfaceDeclaration inter) {
                generateInterfaceDefinition(inter);
            }
            if (node instanceof ClassDeclaration cls) {
                generateStructDefinition(cls);
            }
        }
        builder.append("\n");

        generateClassVTables();

        // Emit lambda forward declarations
        for (LambdaExpression lambda : lambdaFunctions) {
            String lambdaName = "__bolt_lambda_" + lambdaFunctions.indexOf(lambda);
            String sig = lambdaSignatures.get(lambdaName);
            if (sig != null) {
                builder.append(sig).append(";\n");
            }
        }

        for (ASTNode node : tree) {
            if (isTypeDeclaration(node)) continue;
            node.accept(this);
        }

        // Emit lambda functions
        for (LambdaExpression lambda : lambdaFunctions) {
            String lambdaName = "__bolt_lambda_" + lambdaFunctions.indexOf(lambda);
            String sig = lambdaSignatures.get(lambdaName);
            if (sig != null) {
                builder.append(sig).append(" {\n");
                indent();

                // Emit body
                if (lambda.body instanceof Block block) {
                    for (ASTNode stmt : block.statements) {
                        emitIndent();
                        stmt.accept(this);
                        if (!(stmt instanceof Block) && !(stmt instanceof IfStatement) &&
                            !(stmt instanceof WhileStatement) && !(stmt instanceof ForStatement) &&
                            !(stmt instanceof SwitchStatement)) {
                            builder.append(";\n");
                        }
                    }
                } else {
                    emitIndent();
                    lambda.body.accept(this);
                    builder.append(";\n");
                }

                outdent();
                builder.append("}\n\n");
            }
        }

        StringBuilder finalBuilder = new StringBuilder();
        if (!config.getBoolean("no-std-includes")) {
            int idx = builder.indexOf("#include <string.h>");
            if (idx != -1) {
                finalBuilder.append(builder, 0, idx);
                finalBuilder.append(genericBuilder);
                finalBuilder.append(builder, idx, builder.length());
            } else {
                finalBuilder.append(genericBuilder);
                finalBuilder.append(builder);
            }
        } else {
            finalBuilder.append(genericBuilder);
            finalBuilder.append(builder);
        }

        String output = finalBuilder.toString();
        output = insertStringPool(output);
        return hydro.bolt.format.LineWrapper.wrap(
            output, Integer.parseInt(config.get("line-width")), oneIndent());
    }

    private String oneIndent() {
        int size = Integer.parseInt(config.get("indent-size"));
        return config.get("indent-style").equals("tab") ? "\t" : " ".repeat(size);
    }

    private String insertStringPool(String output) {
        if (stringPool.isEmpty()) return output;

        StringBuilder pool = new StringBuilder();
        pool.append("/* pooled string literals */\n");
        for (Map.Entry<String, String> entry : stringPool.entrySet()) {
            pool.append("static const char ").append(entry.getValue())
                .append("[] = ").append(entry.getKey()).append(";\n");
        }
        pool.append("\n");

        return pool + output;
    }

    public String generateHeader() {
        generatingHeader = true;
        currentScope = symbols;
        builder.setLength(0);
        genericBuilder.setLength(0);
        generatedInstantiations.clear();

        for (ASTNode node : tree) {
            if (node instanceof PackageDeclaration pkg) {
                currentPackage = pkg.name;
                break;
            }
        }

        String guard = "BOLT_HEADER_" + (currentPackage != null ? currentPackage.replace('.', '_').toUpperCase() : "ROOT") + "_H";
        builder.append("#ifndef ").append(guard).append("\n");
        builder.append("#define ").append(guard).append("\n\n");

        for (ASTNode node : tree) {
            if (node instanceof ImportDeclaration imp) {
                // Angle-bracket C header: import <stdio.h>
                if (imp.isCHeader) {
                    builder.append("#include <").append(imp.cHeaderName).append(">\n");
                    continue;
                }
                String path = imp.path;

                if (path.equals("cstd.io")) {
                    builder.append("#include <stdio.h>\n");
                } else if (path.equals("cstd.stdlib")) {
                    builder.append("#include <stdlib.h>\n");
                } else if (path.equals("cstd.math")) {
                    builder.append("#include <math.h>\n");
                } else if (path.equals("cstd.string")) {
                    builder.append("#include <string.h>\n");
                } else if (path.equals("cstd.time")) {
                    builder.append("#include <time.h>\n");
                } else {
                    builder.append("#include \"").append(path.replace(".", "/")).append(".h\"\n");
                }
            }
        }

        if (!tree.isEmpty()) {
            builder.append("\n");
        }

        for (String name : symbols.keySet()) {
            Symbol sym = symbols.get(name);
            if (sym.kind == Symbol.Kind.CLASS || sym.kind == Symbol.Kind.STRUCT) {
                String cName = toCName(name);
                builder.append("typedef struct ").append(cName).append(" ").append(cName).append(";\n");
            }
        }

        builder.append("\n");

        for (ASTNode node : tree) {
            if (node instanceof ClassDeclaration cls) {
                generateStructDefinition(cls);
            }
        }

        builder.append("\n");

        generateForwardDeclarations();

        builder.append("\n#endif // ").append(guard).append("\n");
        return builder.toString();
    }

    private void generateForwardDeclarations() {
        for (ASTNode node : tree) {
            if (node instanceof FunctionDeclaration func && func.genericParams.isEmpty()) {
                if (func.visibility == Visibility.PRIVATE) continue;
                generateSignature(func, null, false);
                builder.append(";\n");
            } else if (node instanceof ClassDeclaration cls && cls.genericParams.isEmpty()) {
                String fullClassName = resolveTypeName(cls.name);
                if (cls.inner != null) {
                    for (ASTNode member : cls.inner) {
                        if (member instanceof FunctionDeclaration func) {
                            // Skip private methods
                            if (func.visibility == Visibility.PRIVATE) continue;
                            generateSignature(func, fullClassName, true);
                            builder.append(";\n");
                        }
                    }
                }
            } else if (node instanceof ImplDeclaration impl) {
                String fullTypeName = resolveTypeName(impl.targetType);
                Symbol targetSym = symbols.get(fullTypeName);
                boolean isClass = targetSym != null && targetSym.kind == Symbol.Kind.CLASS;
                for (ASTNode member : impl.members) {
                    if (member instanceof FunctionDeclaration func) {
                        generateSignature(func, fullTypeName, isClass);
                        builder.append(";\n");
                    }
                }
            } else if (node instanceof BinaryOverloadNode over) {
                over.returnType.accept(this);
                builder.append(" __bolt_operator_").append(operatorToName(over.operator)).append("_")
                       .append(toCName(over.operand1.name)).append("_").append(toCName(over.operand2.name))
                       .append("(").append(toCName(over.operand1.name)).append(" a, ")
                       .append(toCName(over.operand2.name)).append(" b);\n");
            } else if (node instanceof UnaryOverloadNode over) {
                over.returnType.accept(this);
                builder.append(" __bolt_operator_").append(operatorToName(over.operator)).append("_")
                       .append(toCName(over.operand.name))
                       .append("(").append(toCName(over.operand.name)).append(" a);\n");
            } else if (node instanceof InterfaceDeclaration inter) {
                String cName = toCName(inter.name);
                builder.append("typedef struct ").append(cName).append(" ").append(cName).append(";\n");
                builder.append("typedef struct ").append(cName).append("_VTable ").append(cName).append("_VTable;\n");
            }
        }
        builder.append("\n");
    }

    private void generateSignature(FunctionDeclaration func, String className, boolean isClass) {
        generateSignature(func, className, isClass, null);
    }

    private void generateSignature(FunctionDeclaration func, String className, boolean isClass, String customName) {
        if (hasDecorator(func, "inline")) {
            if (supportsInline()) {
                builder.append("static inline ");
            } else {
                reporter.warn(hydro.bolt.parser.ErrorCode.UNSUPPORTED_C_STANDARD,
                    func.line, func.column, func.name.length(),
                    "@inline ignored: 'inline' requires C99 or later, but c-standard is "
                        + config.get("c-standard"));
                builder.append("static ");
            }
        }

        boolean isLifecycle = className != null && !className.isEmpty() && (func.name.equals("init") || func.name.equals("dinit"));
        if (isLifecycle) {
            builder.append(toCName(className)).append("*");
        } else {
            func.returnType.accept(this);
        }
        builder.append(" ");

        String funcName = (customName != null) ? customName : func.name;
        List<String> paramTypes = new ArrayList<>();
        if (func.parameters != null) {
            for (Parameter p : func.parameters) {
                String typeStr = p.type.toString();
                if (genericMapping.containsKey(typeStr)) {
                    typeStr = genericMapping.get(typeStr);
                }
                paramTypes.add(typeStr);
            }
        }

        if (className != null && !className.isEmpty()) {
            Symbol classSym = symbols.get(className);
            String mangledName = mangleIdentifier(funcName, paramTypes, className);
            
            if (shouldMangle(func) || (classSym != null && classSym.mangle)) {
                builder.append(mangledName);
            } else {
                builder.append(toCName(className)).append("_").append(funcName);
            }
            
            builder.append("(").append(toCName(className));
            if (isClass) builder.append("*");
            builder.append(" self");
            if (func.parameters != null && !func.parameters.isEmpty()) {
                builder.append(", ");
            }
        } else {
            if (customName != null) {
                builder.append(toCName(customName));
            } else if (shouldMangle(func)) {
                builder.append(mangleIdentifier(funcName, paramTypes, null));
            } else {
                builder.append(mangleGlobalName(funcName));
            }
            builder.append("(");
        }

        if (func.parameters != null) {
            for (int i = 0; i < func.parameters.size(); i++) {
                emitParameter(func.parameters.get(i));
                if (i < func.parameters.size() - 1) {
                    builder.append(", ");
                }
            }
        }
        builder.append(")");
    }

    private void emitParameter(Parameter param) {
        if (param.type instanceof FunctionType fnType) {
            fnType.returnType.accept(this);
            builder.append(" (*").append(param.name).append(")(");
            if (fnType.parameterTypes == null || fnType.parameterTypes.isEmpty()) {
                builder.append("void");
            } else {
                for (int i = 0; i < fnType.parameterTypes.size(); i++) {
                    fnType.parameterTypes.get(i).accept(this);
                    if (i < fnType.parameterTypes.size() - 1) builder.append(", ");
                }
            }
            builder.append(")");
            return;
        }

        param.type.accept(this);
        builder.append(" ").append(param.name);
        emitArrayBrackets(param.type);
    }

    @Override
    public String visitFunctionType(FunctionType node) {
        node.returnType.accept(this);
        builder.append(" (*)(");
        if (node.parameterTypes != null) {
            for (int i = 0; i < node.parameterTypes.size(); i++) {
                node.parameterTypes.get(i).accept(this);
                if (i < node.parameterTypes.size() - 1) builder.append(", ");
            }
        }
        builder.append(")");
        return null;
    }

    private static final Map<String, String> OPERATOR_NAMES = Map.ofEntries(
        Map.entry("+", "plus"),
        Map.entry("-", "minus"),
        Map.entry("*", "mul"),
        Map.entry("/", "div"),
        Map.entry("%", "mod"),
        Map.entry("==", "eq"),
        Map.entry("!=", "neq"),
        Map.entry("<", "lt"),
        Map.entry("<=", "lte"),
        Map.entry(">", "gt"),
        Map.entry(">=", "gte"),
        Map.entry("&&", "land"),
        Map.entry("||", "lor"),
        Map.entry("!", "lnot"),
        Map.entry("&", "band"),
        Map.entry("|", "bor"),
        Map.entry("^", "bxor"),
        Map.entry("~", "bnot"),
        Map.entry("<<", "shl"),
        Map.entry(">>", "shr"),
        Map.entry("[", "lbracket"),
        Map.entry("]", "rbracket"),
        Map.entry("(", "lparen"),
        Map.entry(")", "rparen")
    );

    private String operatorToName(String op) {
        String result = OPERATOR_NAMES.get(op);
        if (result != null) return result;

        StringBuilder sb = new StringBuilder();
        for (char c : op.toCharArray()) {
            if (Character.isLetterOrDigit(c)) sb.append(c);
            else sb.append("_").append((int)c);
        }
        return sb.toString();
    }

    private String typeName(ASTNode node) {
        if (node instanceof Identifier id) return id.name;
        if (node instanceof PrimitiveType pt) return pt.name;
        return node.toString();
    }

    private String resolveTypeName(String name) {
        if (genericMapping.containsKey(name)) {
            return genericMapping.get(name);
        }
        if (currentGenericParams.contains(name)) {
            return name;
        }
        if (currentPackage != null) {
            String namespaced = currentPackage + "." + name;
            if (symbols.contains(namespaced)) return namespaced;
        }
        for (String imp : imports) {
            if (imp.endsWith("." + name)) return imp;
        }
        return name;
    }

    private String stripGeneric(String type) {
        if (type == null) return null;
        int angle = type.indexOf('<');
        if (angle != -1) return type.substring(0, angle);
        return type;
    }

    private String stripPointer(String type) {
        if (type == null) return null;
        String t = stripGeneric(type);
        if (t.endsWith("*")) {
            return t.substring(0, t.length() - 1).trim();
        }
        return t;
    }

    private String resolveType(ASTNode node) {
        String t = resolveTypeInternal(node);
        if (genericMapping.containsKey(t)) return genericMapping.get(t);
        return t;
    }

    private String resolveTypeInternal(ASTNode node) {
    if (node instanceof Identifier id) {
        Symbol sym = currentScope.get(id.name);
        if (sym != null) {
            String type = sym.typeName;
            if (sym.isPointer && !type.endsWith("*")) type += "*";
            return type;
        }
        if (!id.genericArguments.isEmpty()) {
            return getMangledGenericName(id);
        }
        String full = resolveTypeName(id.name);
        Symbol globalSym = symbols.get(full);
        if (globalSym != null) return globalSym.typeName;
        if (id.name.equals("self")) return resolveTypeName("self") + "*";
        return "unknown";
    }
    if (node instanceof BinaryExpression bin) {
       String t1 = resolveType(bin.left);
       String t2 = resolveType(bin.right);
       if ("string".equals(t1) || "string".equals(t2)) {
            return "string";
       }
       
       String opName = "__bolt_operator_" + operatorToName(bin.operator) + "_" + toCName(t1) + "_" + toCName(t2);
       if (symbols.contains(opName)) return symbols.get(opName).typeName;

       switch (bin.operator) {
            case "==": case "!=": case "<": case "<=": case ">": case ">=":
            case "&&": case "||": return "int";
        }
       
       return t1; // default to left op type for arithmetic/bitwise ops
    }
    if (node instanceof VariableExpression ve) {
        Symbol sym = currentScope.get(ve.name);
        return (sym != null) ? sym.typeName : "unknown";
    }
    if (node instanceof FunctionCall call) {
        if (call.function instanceof Identifier id) {
            String full = resolveTypeName(id.name);
            Symbol sym = symbols.get(full);
            if (sym != null) return sym.typeName;
        } else if (call.function instanceof MemberAccess ma) {
            String objType = stripPointer(resolveType(ma.object));
            Symbol classSym = symbols.get(resolveTypeName(objType));
            if (classSym != null && classSym.members.containsKey(ma.member)) {
                return classSym.members.get(ma.member).typeName;
            }
        }
        return "unknown";
    }
    if (node instanceof MemberAccess ma) {
        String objType = stripPointer(resolveType(ma.object));
        Symbol classSym = symbols.get(resolveTypeName(objType));
        if (classSym != null && classSym.members.containsKey(ma.member)) {
            return classSym.members.get(ma.member).typeName;
        }
        return "unknown";
    }
    if (node instanceof NewExpression ne) {
        return resolveTypeName(ne.typeName) + "*";
    }
    if (node instanceof RawCNode raw) {
        if (raw.content.matches("\\d+")) return "int";
        if (raw.content.startsWith("\"")) return "string";
    }
    if (node instanceof UnaryExpression ue) {
        return resolveType(ue.operand);
    }
    if (node instanceof StringLiteral) return "string";
    if (node instanceof IntegerLiteral) return "int";
    if (node instanceof FloatLiteral) return "float";
    if (node instanceof CharLiteral) return "char";
    
    return "unknown";
}

    private String mangleGlobalName(String name) {
        if (name.equals("main")) return "main";
        if (currentPackage == null) return toCName(name);
        if (name.contains(".")) return toCName(name); // Already namespaced
        return toCName(currentPackage) + "_" + toCName(name);
    }

    private String toCName(String name) {
        if (name == null) return "unknown";
        return name.replace(".", "_");
    }

    private boolean shouldMangle(ASTNode node) {
        boolean mangleDefault = config.getBoolean("mangle");
        for (DecoratorNode dec : node.decorators) {
            if (dec.name.equals("mangle")) {
                return !dec.isNegated;
            }
        }
        return mangleDefault;
    }


    private void emitTrace(ASTNode node) {
        if (config.getBoolean("traceability") && node.line > 0) {
            emitIndent();
            builder.append("/* line ").append(node.line).append(" */ ");
        }
    }

    private void emitIndent() {
        builder.append(cachedIndent);
    }

    private void indent() {
        indentLevel++;
        updateCachedIndent();
    }

    private void outdent() {
        indentLevel--;
        if (indentLevel < 0) indentLevel = 0;
        updateCachedIndent();
    }

    private void updateCachedIndent() {
        int size = Integer.parseInt(config.get("indent-size"));
        String style = config.get("indent-style");
        String indentChar = style.equals("tab") ? "\t" : " ";
        cachedIndent = indentChar.repeat(indentLevel * size);
    }

    private boolean supportsInline() {
        String std = config.get("c-standard");
        return !("c89".equalsIgnoreCase(std) || "c90".equalsIgnoreCase(std));
    }

    private boolean isTypeDeclaration(ASTNode node) {
        return node instanceof EnumDeclaration || node instanceof UnionDeclaration
            || node instanceof StructDeclaration || node instanceof TypedefDeclaration;
    }

    private boolean isClassType(String type) {
        if (type == null) return false;
        Symbol sym = symbols.get(resolveTypeName(stripGeneric(type)));
        if (sym == null) sym = symbols.get(stripGeneric(type));
        return sym != null && sym.kind == Symbol.Kind.CLASS;
    }

    private void emitAsValue(ASTNode expr) {
        String type = resolveType(expr);
        if (type != null && type.endsWith("*") && isClassType(stripPointer(type))) {
            builder.append("(*");
            expr.accept(this);
            builder.append(")");
        } else {
            expr.accept(this);
        }
    }

    private boolean isStringType(String type) {
        return "string".equals(type);
    }

    private String getBraceStart() {
        return config.get("brace-style").equalsIgnoreCase("allman") ? "\n" + cachedIndent + "{" : " {";
    }

    private String mangleIdentifier(String name, List<String> paramTypes, String className) {
        if (!config.getBoolean("mangle")) return toCName(name);
        if (name.equals("main")) return "main";
        
        StringBuilder sb = new StringBuilder();
        sb.append(config.get("mangle-prefix"));
        
        if (className != null && !className.isEmpty()) {
            sb.append("N");
            String cClassName = toCName(className);
            sb.append(cClassName.length()).append(cClassName);
        }
        
        String cName = toCName(name);
        sb.append(cName.length()).append(cName);
        
        if (className != null && !className.isEmpty()) {
            sb.append("E");
        }
        
        if (paramTypes != null && !paramTypes.isEmpty()) {
            for (String type : paramTypes) {
                sb.append(getMangledType(type));
            }
        } else {
            sb.append("v"); // void or no params
        }
        
        return sb.toString();
    }

    private static final Map<String, String> MANGLED_TYPE_NAMES = Map.ofEntries(
        Map.entry("int", "i"),
        Map.entry("float", "f"),
        Map.entry("char", "c"),
        Map.entry("string", "s"),
        Map.entry("bool", "b"),
        Map.entry("void", "v")
    );

    private String poolStringLiteral(String content) {
        if (generatingHeader) return null;
        if (!config.getBoolean("static-string-pool")) return null;
        if (content == null) return null;

        String text = content.trim();
        if (text.length() < 2 || !text.startsWith("\"") || !text.endsWith("\"")) return null;
        for (int i = 1; i < text.length() - 1; i++) {
            if (text.charAt(i) == '\\') {
                i++;
            } else if (text.charAt(i) == '"') {
                return null;
            }
        }

        return stringPool.computeIfAbsent(text,
            k -> config.get("mangle-prefix") + "_str_" + stringPool.size());
    }

    private String getMangledType(String type) {
        if (type == null) return "v";
        type = stripPointer(type);
        String result = MANGLED_TYPE_NAMES.get(type);
        if (result != null) return result;
        return type.length() + toCName(type);
    }

    @Override
    public String visitTernaryExpression(TernaryExpression node) {
        builder.append("(");
        node.condition.accept(this);
        builder.append(" ? ");
        node.trueExpression.accept(this);
        builder.append(" : ");
        node.falseExpression.accept(this);
        builder.append(")");
        return null;
    }

    @Override
    public String visitPointerMemberAccess(PointerMemberAccess node) {
        node.pointer.accept(this);
        builder.append("->").append(node.member);
        return null;
    }

    @Override
    public String visitEnumDeclaration(EnumDeclaration node) {
        builder.append("typedef enum ").append(toCName(node.name)).append(getBraceStart()).append("\n");
        indent();
        if (node.members != null) {
            for (int i = 0; i < node.members.size(); i++) {
                EnumMember member = node.members.get(i);
                emitIndent();
                builder.append(member.name);
                if (member.value != null) {
                    builder.append(" = ");
                    member.value.accept(this);
                }
                if (i < node.members.size() - 1) builder.append(",");
                builder.append("\n");
            }
        }
        outdent();
        builder.append("} ").append(toCName(node.name)).append(";\n\n");
        return null;
    }

    @Override
    public String visitUnionDeclaration(UnionDeclaration node) {
        builder.append("typedef union ").append(toCName(node.name)).append(getBraceStart()).append("\n");
        indent();
        emitAggregateMembers(node.members);
        outdent();
        builder.append("} ").append(toCName(node.name)).append(";\n\n");
        return null;
    }

    @Override
    public String visitStructDeclaration(StructDeclaration node) {
        builder.append("typedef struct ").append(toCName(node.name)).append(getBraceStart()).append("\n");
        indent();
        emitAggregateMembers(node.members);
        outdent();
        builder.append("} ").append(toCName(node.name)).append(";\n\n");
        return null;
    }

    private void emitAggregateMembers(List<StructMember> members) {
        if (members == null) return;
        for (StructMember member : members) {
            emitIndent();
            member.type.accept(this);
            builder.append(" ").append(member.name);
            emitArrayBrackets(member.type);
            builder.append(";\n");
        }
    }

    @Override
    public String visitTypedefDeclaration(TypedefDeclaration node) {
        builder.append("typedef ");
        node.type.accept(this);
        builder.append(" ").append(toCName(node.name));
        emitArrayBrackets(node.type);
        builder.append(";\n\n");
        return null;
    }

    @Override
    public String visitEnumMember(EnumMember node) {
        builder.append(node.name);
        return null;
    }

    @Override
    public String visitIntegerLiteral(IntegerLiteral node) {
        builder.append(node.value);
        return "int";
    }

    @Override
    public String visitFloatLiteral(FloatLiteral node) {
        builder.append(node.value);
        return "float";
    }

    @Override
    public String visitCharLiteral(CharLiteral node) {
        builder.append(node.value);
        return "char";
    }

    @Override
    public String visitStringLiteral(StringLiteral node) {
        String pooled = poolStringLiteral(node.value);
        builder.append(pooled != null ? pooled : node.value);
        return "string";
    }

    public String visitRawCNode(RawCNode node) {
        String pooled = poolStringLiteral(node.content);
        builder.append(pooled != null ? pooled : node.content);
        // if content looks like struct definition but missing semicolon, add it.
        // also handle "struct Point { ... }" where the closing brace might be followed by spaces
        if (node.content.trim().startsWith("struct") && node.content.trim().endsWith("}")) {
             builder.append(";");
        }

        // only append newline if its not a short literal like string
        if (node.content.length() > 0 && !Character.isDigit(node.content.charAt(0)) && !node.content.startsWith("\"")) {
            builder.append("\n");
        }
        return null;
    }

    // legacy support if visit(RawCNode) was used
    public String visit(RawCNode node) {
        return visitRawCNode(node);
    }

    @Override
    public String visitClassDeclaration(ClassDeclaration node) {
        if (!node.genericParams.isEmpty()) return null;
        return generateClassMethods(node, node.name);
    }

    private void generateClassVTables() {
        for (ASTNode node : tree) {
            if (node instanceof ClassDeclaration cls && cls.genericParams.isEmpty()) {
                for (String inter : cls.interfaces) {
                    generateClassVTableInstance(cls, inter);
                }
            }
        }
    }

    @Override
    public String visitInterfaceDeclaration(InterfaceDeclaration node) {
        return null;
    }

    private void generateClassVTableInstance(ClassDeclaration node, String interfaceName) {
        String fullClassName = resolveTypeName(node.name);
        String fullInterfaceName = resolveTypeName(interfaceName);
        Symbol interfaceSym = symbols.get(fullInterfaceName);
        if (interfaceSym == null) return;

        String vtableName = "__bolt_vtable_" + toCName(fullClassName) + "_" + toCName(fullInterfaceName);
        builder.append("static ").append(toCName(fullInterfaceName)).append("_VTable ").append(vtableName).append(" = {\n");
        indent();
        for (String methodName : interfaceSym.members.keySet()) {
            Symbol methodSym = interfaceSym.members.get(methodName);
            String mangledName = methodSym.mangle ? mangleIdentifier(methodName, methodSym.parameterTypes, fullClassName) : toCName(fullClassName) + "_" + methodName;
            emitIndent();
            builder.append(".").append(methodName).append(" = (void*)").append(mangledName).append(",\n");
        }
        outdent();
        builder.append("};\n\n");
    }

    private String generateClassMethods(ClassDeclaration node, String className) {
        currentGenericParams = node.genericParams;
        String fullClassName = resolveTypeName(className);

        List<FunctionDeclaration> functions = new ArrayList<>();
        if (node.inner != null) {
            for (ASTNode member : node.inner) {
                if (member instanceof FunctionDeclaration func) {
                    functions.add(func);
                }
            }
        }

        for (FunctionDeclaration func : functions) {
            List<String> oldGenericParams = currentGenericParams;
            currentGenericParams = new ArrayList<>(node.genericParams);
            currentGenericParams.addAll(func.genericParams);
            generateSignature(func, className, true);
            builder.append(" ");
            generateFunctionBody(func, fullClassName, true);
            currentGenericParams = oldGenericParams;
        }

        currentGenericParams = new ArrayList<>();
        return null;
    }

    private void generateStructDefinition(ClassDeclaration node) {
        if (!node.genericParams.isEmpty()) return;
        generateStructDefinition(node, node.name, mangleGlobalName(node.name));
    }

    private void generateStructDefinition(ClassDeclaration node, String className, String mangledName) {
        List<String> oldGenericParams = currentGenericParams;
        currentGenericParams = node.genericParams;
        String classTemplate = """
            struct {{name}} {
            {{repeat:fields}}
                {{type}} {{name}}{{brackets}};
            {{endrepeat}}
            };
            """;

        Template t = new Template(classTemplate);
        List<Map<String, Object>> fields = new ArrayList<>();

        if (node.inner != null) {
            for (ASTNode member : node.inner) {
                if (member instanceof VariableDeclaration field) {
                    StringBuilder oldBuilder = builder;
                    builder = new StringBuilder();
                    if (field.visibility == Visibility.PRIVATE) {
                        builder.append("/* private */ ");
                    }
                    field.type.accept(this);
                    String typeStr = builder.toString();

                    builder = new StringBuilder();
                    emitArrayBrackets(field.type);
                    String bracketsStr = builder.toString();

                    builder = oldBuilder;

                    fields.add(Map.of(
                        "type", typeStr,
                        "name", field.name,
                        "brackets", bracketsStr
                    ));
                }
            }
        }

        Map<String, Object> data = Map.of(
            "name", mangledName,
            "fields", fields
        );

        builder.append(t.apply(data)).append("\n");
        currentGenericParams = oldGenericParams;
    }

    private void generateInterfaceDefinition(InterfaceDeclaration node) {
        String cName = toCName(node.name);
        builder.append("struct ").append(cName).append(" {\n");
        builder.append("    void* obj;\n");
        builder.append("    void* vtable;\n");
        builder.append("};\n\n");

        builder.append("struct ").append(cName).append("_VTable {\n");
        indent();
        for (ASTNode member : node.inner) {
            if (member instanceof FunctionDeclaration func) {
                emitIndent();
                func.returnType.accept(this);
                builder.append(" (*").append(func.name).append(")(void*");
                if (func.parameters != null && !func.parameters.isEmpty()) {
                    builder.append(", ");
                    for (int i = 0; i < func.parameters.size(); i++) {
                        func.parameters.get(i).type.accept(this);
                        if (i < func.parameters.size() - 1) builder.append(", ");
                    }
                }
                builder.append(");\n");
            }
        }
        outdent();
        builder.append("};\n\n");
    }

    @Override
    public String visitSwitchStatement(SwitchStatement node) {
        emitTrace(node);
        emitIndent();
        builder.append("switch (");
        node.expression.accept(this);
        builder.append(")").append(getBraceStart()).append("\n");
        indent();
        if (node.body instanceof Block block && block.statements != null) {
            for (ASTNode section : block.statements) {
                section.accept(this);
            }
        } else if (node.body != null) {
            node.body.accept(this);
        }
        outdent();
        emitIndent();
        builder.append("}\n");
        return null;
    }

    @Override
    public String visitCaseStatement(CaseStatement node) {
        emitIndent();
        builder.append("case ");
        node.value.accept(this);
        builder.append(":\n");
        indent();
        emitStatements(node.statements);
        outdent();
        return null;
    }

    @Override
    public String visitDefaultStatement(DefaultStatement node) {
        emitIndent();
        builder.append("default:\n");
        indent();
        emitStatements(node.statements);
        outdent();
        return null;
    }

    private void emitStatements(List<ASTNode> statements) {
        if (statements == null) return;
        for (ASTNode stmt : statements) {
            emitStatement(stmt);
        }
    }

    private void emitStatement(ASTNode stmt) {
        if (stmt == null) return;
        if (isSelfDelimiting(stmt)) {
            stmt.accept(this);
            return;
        }
        emitIndent();
        stmt.accept(this);
        builder.append(";\n");
    }

    private boolean isSelfDelimiting(ASTNode stmt) {
        return stmt instanceof Block || stmt instanceof IfStatement
            || stmt instanceof WhileStatement || stmt instanceof ForStatement
            || stmt instanceof DoWhileStatement || stmt instanceof SwitchStatement
            || stmt instanceof CaseStatement || stmt instanceof DefaultStatement
            || stmt instanceof BreakStatement || stmt instanceof ContinueStatement
            || stmt instanceof GotoStatement || stmt instanceof LabeledStatement
            || stmt instanceof ReturnStatement || stmt instanceof VariableDeclaration
            || stmt instanceof RawCNode;
    }

    @Override
    public String visitForStatement(ForStatement node) {
        emitTrace(node);
        SymbolTree oldScope = currentScope;
        currentScope = new SymbolTree(oldScope);

        StringBuilder header = new StringBuilder();
        StringBuilder oldBuilder = builder;

        builder = header;
        header.append("for (");
        emitForInitializer(node.initializer);
        header.append("; ");
        if (node.condition != null) node.condition.accept(this);
        header.append("; ");
        emitForUpdate(node.update);
        header.append(")");
        builder = oldBuilder;

        emitIndent();
        builder.append(header).append(getBraceStart()).append("\n");
        indent();
        if (node.body instanceof Block block) {
            emitStatements(block.statements);
        } else {
            emitStatement(node.body);
        }
        outdent();
        emitIndent();
        builder.append("}\n");

        currentScope = oldScope;
        return null;
    }

    private void emitForInitializer(ASTNode initializer) {
        if (initializer == null) return;

        if (initializer instanceof Block block && block.statements != null) {
            for (int i = 0; i < block.statements.size(); i++) {
                ASTNode part = block.statements.get(i);
                if (i > 0) builder.append(", ");
                if (part instanceof VariableDeclaration vd) {
                    if (i == 0) {
                        emitForDeclaration(vd);
                    } else {
                        emitForDeclarator(vd);
                    }
                } else {
                    part.accept(this);
                }
            }
            return;
        }

        if (initializer instanceof VariableDeclaration vd) {
            emitForDeclaration(vd);
        } else if (initializer instanceof ExpressionStatement es) {
            es.expression.accept(this);
        } else {
            initializer.accept(this);
        }
    }

    private void emitForUpdate(ASTNode update) {
        if (update == null) return;

        if (update instanceof Block block && block.statements != null) {
            for (int i = 0; i < block.statements.size(); i++) {
                if (i > 0) builder.append(", ");
                block.statements.get(i).accept(this);
            }
            return;
        }
        update.accept(this);
    }

    private void emitForDeclarator(VariableDeclaration vd) {
        Symbol sym = new Symbol(vd.name, Symbol.Kind.VARIABLE, getBaseTypeName(vd.type));
        sym.isPointer = vd.type instanceof PointerType;
        currentScope.put(vd.name, sym);

        builder.append(vd.name);
        if (vd.initializer != null) {
            builder.append(" = ");
            vd.initializer.accept(this);
        }
    }

    private void emitForDeclaration(VariableDeclaration vd) {
        String baseType = getBaseTypeName(vd.type);
        Symbol sym = new Symbol(vd.name, Symbol.Kind.VARIABLE, baseType);
        sym.isPointer = vd.type instanceof PointerType;
        currentScope.put(vd.name, sym);

        vd.type.accept(this);
        builder.append(" ").append(vd.name);
        if (vd.initializer != null) {
            builder.append(" = ");
            vd.initializer.accept(this);
        }
    }

    @Override
    public String visitDoWhileStatement(DoWhileStatement node) {
        emitTrace(node);
        emitIndent();
        builder.append("do").append(getBraceStart()).append("\n");
        indent();
        if (node.body instanceof Block block) {
            emitStatements(block.statements);
        } else {
            emitStatement(node.body);
        }
        outdent();
        emitIndent();
        builder.append("} while (");
        node.condition.accept(this);
        builder.append(");\n");
        return null;
    }

    @Override
    public String visitBreakStatement(BreakStatement node) {
        emitIndent();
        builder.append("break;\n");
        return null;
    }

    @Override
    public String visitContinueStatement(ContinueStatement node) {
        emitIndent();
        builder.append("continue;\n");
        return null;
    }

    @Override
    public String visitGotoStatement(GotoStatement node) {
        emitIndent();
        builder.append("goto ").append(node.label).append(";\n");
        return null;
    }

    @Override
    public String visitLabeledStatement(LabeledStatement node) {
        builder.append(node.label).append(":\n");
        if (node.statement != null) {
            emitStatement(node.statement);
        } else {
            emitIndent();
            builder.append(";\n");
        }
        return null;
    }

    @Override
    public String visitBlock(Block node) {
        return visitBlock(node, null, null);
    }

    public String visitBlock(Block node, String prelude, String postlude) {
        emitTrace(node);
        SymbolTree oldScope = currentScope;
        currentScope = new SymbolTree(oldScope);
        int previousSize = allActiveInstances.size();
        boolean blockHasReturned = false;

        builder.append(getBraceStart()).append("\n");
        indent();
        if (prelude != null) {
            emitIndent();
            builder.append(prelude);
        }

        if (node.statements != null) {
            for (ASTNode stmt : node.statements) {
                stmt.accept(this);
                if (stmt instanceof ReturnStatement) {
                    blockHasReturned = true;
                    break;
                }
            }
        }

        if (!blockHasReturned) {
            if (postlude != null) {
                emitIndent();
                builder.append(postlude);
            }
            for (int i = allActiveInstances.size() - 1; i >= previousSize; i--) {
                emitIndent();
                callDinit(allActiveInstances.get(i));
            }
        }

        outdent();
        emitIndent();
        builder.append("}\n");
        currentScope = oldScope;
        while (allActiveInstances.size() > previousSize) {
            allActiveInstances.remove(allActiveInstances.size() - 1);
        }
        return null;
    }

    @Override
public String visitExpressionStatement(ExpressionStatement node) {
    if (node.expression != null) {
        emitTrace(node);
        List<String[]> prevDecls = tempStringDecls;
        tempStringDecls = new ArrayList<>();
        // capture expr into a side buffer so we can prepend decls
        StringBuilder exprBuf = new StringBuilder();
        StringBuilder oldBuilder = builder;
        builder = exprBuf;
        node.expression.accept(this);
        builder = oldBuilder;
        for (String[] decl : tempStringDecls) {
            emitIndent();
            builder.append("char* ").append(decl[0]).append(" = ").append(decl[1]).append(";\n");
        }
        emitIndent();
        builder.append(exprBuf).append(";\n");
        for (String[] decl : tempStringDecls) {
            emitIndent();
            builder.append("free(").append(decl[0]).append(");\n");
        }
        tempStringDecls = prevDecls;
    }
    return null;
}

    @Override
    public String visitIfStatement(IfStatement node) {
        emitTrace(node);
        List<String[]> prevDecls = tempStringDecls;
        tempStringDecls = new ArrayList<>();
        StringBuilder condBuf = new StringBuilder();
        StringBuilder oldBuilder = builder;
        builder = condBuf;
        node.condition.accept(this);
        builder = oldBuilder;
        
        List<String[]> condDecls = tempStringDecls;
        tempStringDecls = prevDecls;
        
        if (!condDecls.isEmpty()) {
            emitIndent();
            builder.append("{\n");
            indent();
            for (String[] decl : condDecls) {
                emitIndent();
                builder.append("char* ").append(decl[0]).append(" = ").append(decl[1]).append(";\n");
            }
        }
        
        emitIndent();
        builder.append("if (").append(condBuf).append(") ");
        if (node.thenBranch instanceof Block) {
            node.thenBranch.accept(this);
        } else {
            builder.append("{\n");
            node.thenBranch.accept(this);
            builder.append("\n}");
        }
        
        if (node.elseBranch != null) {
            builder.append(" else ");
            if (node.elseBranch instanceof Block || node.elseBranch instanceof IfStatement) {
                node.elseBranch.accept(this);
            } else {
                builder.append("{\n");
                node.elseBranch.accept(this);
                builder.append("\n}");
            }
        }
        
        if (!condDecls.isEmpty()) {
            for (String[] decl : condDecls) {
                emitIndent();
                builder.append("free(").append(decl[0]).append(");\n");
            }
            outdent();
            emitIndent();
            builder.append("}\n");
        } else {
            builder.append("\n");
        }
        
        return null;
    }

    @Override
    public String visitWhileStatement(WhileStatement node) {
        emitTrace(node);
        List<String[]> prevDecls = tempStringDecls;
        
        builder.append("while (1) {\n");
        
        tempStringDecls = new ArrayList<>();
        StringBuilder condBuf = new StringBuilder();
        StringBuilder oldBuilder = builder;
        builder = condBuf;
        node.condition.accept(this);
        builder = oldBuilder;
        
        List<String[]> condDecls = tempStringDecls;
        for (String[] decl : condDecls) {
            emitIndent();
            builder.append("char* ").append(decl[0]).append(" = ").append(decl[1]).append(";\n");
        }
        
        emitIndent();
        builder.append("if (!(").append(condBuf).append(")) {\n");
        indent();
        for (String[] decl : condDecls) {
            emitIndent();
            builder.append("free(").append(decl[0]).append(");\n");
        }
        emitIndent();
        builder.append("break;\n");
        outdent();
        emitIndent();
        builder.append("}\n");
        
        tempStringDecls = prevDecls;
        node.body.accept(this);
        
        for (String[] decl : condDecls) {
            emitIndent();
            builder.append("free(").append(decl[0]).append(");\n");
        }
        outdent();
        emitIndent();
        builder.append("}\n");
        return null;
    }

    private boolean hasDecorator(ASTNode node, String name) {
        if (node.decorators == null) return false;
        for (DecoratorNode dec : node.decorators) {
            if (dec.name.equals(name)) return !dec.isNegated;
        }
        return false;
    }

    private void callDinit(String instanceName) {
        Symbol sym = currentScope.get(instanceName);
        if (sym != null) {
            if (sym.isManual) return;

            if (isStringType(sym.typeName)) {
                builder.append("if (").append(instanceName).append(") free(").append(instanceName).append(");\n");
                return;
            }

            Symbol classSym = symbols.get(sym.typeName);
            if (classSym != null && classSym.members.containsKey("dinit")) {
                String typeName = sym.typeName;
                boolean mangle = config.getBoolean("mangle");
                String methodName = mangle ? mangleIdentifier("dinit", new ArrayList<>(), typeName) : toCName(typeName) + "_dinit";
                builder.append(methodName).append("(&").append(instanceName).append(");\n");
            }
        }
    }

    @Override
    public String visit(ImplDeclaration node) {
        String fullTypeName = resolveTypeName(node.targetType);
        Symbol targetSym = symbols.get(fullTypeName);
        boolean isClass = targetSym != null && (targetSym.kind == Symbol.Kind.CLASS || targetSym.kind == Symbol.Kind.STRUCT);

        for (ASTNode member : node.members) {
            if (member instanceof FunctionDeclaration func) {
                // add symbol for the function to the class members for mangling purposes
                if (targetSym != null) {
                    Symbol funcSymbol = new Symbol(func.name, Symbol.Kind.FUNCTION, func.returnType.toString());
                    funcSymbol.mangle = true; // mark for mangling
                    targetSym.members.put(func.name, funcSymbol);
                }
                generateSignature(func, node.targetType, isClass);
                builder.append(" ");
                generateFunctionBody(func, fullTypeName, isClass);
            } else {
                member.accept(this);
            }
        }
        return null;
    }

    @Override
    public String visit(DecoratorNode node) {
        builder.append("@").append(node.name).append("\n");
        return null;
    }

    @Override
    public String visit(PackageDeclaration node) {
        currentPackage = node.name;
        return null;
    }

    @Override
    public String visit(NewExpression node) {
        String fullTypeName = resolveTypeName(node.typeName);
        String cTypeName = mangleGenericTypeName(fullTypeName);

        Symbol classSym = symbols.get(fullTypeName);
        if (classSym == null) classSym = symbols.get(resolveTypeName(stripGeneric(node.typeName)));
        if (classSym != null && classSym.members.containsKey("init")) {
            List<String> argTypes = new ArrayList<>();
            if (node.arguments != null) {
                for (ASTNode arg : node.arguments) argTypes.add(resolveType(arg));
            }
            String methodName = mangleIdentifier("init", argTypes, fullTypeName);
            builder.append(methodName).append("((").append(cTypeName).append("*)calloc(1, sizeof(").append(cTypeName).append("))");
            if (node.arguments != null && !node.arguments.isEmpty()) {
                builder.append(", ");
                for (int i = 0; i < node.arguments.size(); i++) {
                    node.arguments.get(i).accept(this);
                    if (i < node.arguments.size() - 1) builder.append(", ");
                }
            }
            builder.append(")");
        } else {
            builder.append("((").append(cTypeName).append("*)calloc(1, sizeof(").append(cTypeName).append(")))");
        }
        return null;
    }

    @Override
    public String visit(DeleteExpression node) {
        String type = "unknown";
        boolean isPointer = false;
        if (node.target instanceof Identifier id) {
            Symbol sym = currentScope.get(id.name);
            if (sym != null) {
                type = sym.typeName;
                isPointer = sym.isPointer;
            }
        } else if (node.target instanceof MemberAccess ma) {
            type = resolveType(ma);
        } else {
            type = resolveType(node.target);
        }
        isPointer = isPointer || (type != null && type.endsWith("*"));

        String baseType = stripPointer(type);
        Symbol classSym = symbols.get(baseType);
        boolean hasDinit = classSym != null && classSym.members.containsKey("dinit");

        if (!isPointer) {
            if (hasDinit) {
                builder.append(dinitName(baseType)).append("(&");
                node.target.accept(this);
                builder.append(")");
                markDeleted(node.target);
            } else {
                builder.append("(void)0");
            }
            return null;
        }

        if (hasDinit) {
            String methodName = dinitName(baseType);
            builder.append("free(").append(methodName).append("((").append(toCName(baseType)).append("*)");
            node.target.accept(this);
            builder.append("))");
        } else {
            builder.append("free(");
            node.target.accept(this);
            builder.append(")");
        }

        return null;
    }

    private void emitInPlaceConstruction(NewExpression node) {
        String fullTypeName = resolveTypeName(node.typeName);
        String cTypeName = mangleGenericTypeName(fullTypeName);

        Symbol classSym = symbols.get(fullTypeName);
        if (classSym == null) classSym = symbols.get(resolveTypeName(stripGeneric(node.typeName)));

        if (classSym == null || !classSym.members.containsKey("init")) {
            builder.append("(").append(cTypeName).append("){0}");
            return;
        }

        List<String> argTypes = new ArrayList<>();
        if (node.arguments != null) {
            for (ASTNode arg : node.arguments) argTypes.add(resolveType(arg));
        }

        builder.append("*").append(mangleIdentifier("init", argTypes, fullTypeName))
               .append("(&(").append(cTypeName).append("){0}");
        if (node.arguments != null) {
            for (ASTNode arg : node.arguments) {
                builder.append(", ");
                arg.accept(this);
            }
        }
        builder.append(")");
    }

    private String dinitName(String typeName) {
        return config.getBoolean("mangle")
            ? mangleIdentifier("dinit", new ArrayList<>(), typeName)
            : toCName(typeName) + "_dinit";
    }

    private void markDeleted(ASTNode target) {
        if (target instanceof Identifier id) {
            allActiveInstances.remove(id.name);
        }
    }

    private static final Map<String, String> STD_LIBS = Map.ofEntries(
        Map.entry("std.io", "stdio.h"),
        Map.entry("io", "stdio.h"),
        Map.entry("stdio", "stdio.h"),
        Map.entry("std.stdlib", "stdlib.h"),
        Map.entry("stdlib", "stdlib.h"),
        Map.entry("std.math", "math.h"),
        Map.entry("math", "math.h"),
        Map.entry("std.string", "string.h"),
        Map.entry("string", "string.h"),
        Map.entry("std.time", "time.h"),
        Map.entry("time", "time.h"),
        Map.entry("std.stdarg", "stdarg.h"),
        Map.entry("stdarg", "stdarg.h"),
        Map.entry("std.stdbool", "stdbool.h"),
        Map.entry("stdbool", "stdbool.h"),
        Map.entry("std.stdint", "stdint.h"),
        Map.entry("stdint", "stdint.h"),
        Map.entry("std.ctype", "ctype.h"),
        Map.entry("ctype", "ctype.h"),
        Map.entry("std.assert", "assert.h"),
        Map.entry("assert", "assert.h"),
        Map.entry("std.stddef", "stddef.h"),
        Map.entry("stddef", "stddef.h")
    );

    @Override
    public String visit(ImportDeclaration node) {
        if (node.isCHeader) {
            builder.append("#include <").append(node.cHeaderName).append(">\n");
            return null;
        }
        String path = node.path;

        String header = STD_LIBS.get(path);
        if (header != null) {
            builder.append("#include <").append(header).append(">\n");
        } else {
            builder.append("#include \"").append(path.replace(".", "/")).append(".h\"\n");
        }
        return null;
    }

    @Override
    public String visitUnaryOverload(UnaryOverloadNode node) {
        node.returnType.accept(this);
        String opName = "__bolt_operator_" + operatorToName(node.operator) + "_" + toCName(node.operand.name);
        builder.append(" ").append(opName)
               .append("(").append(toCName(node.operand.name)).append(" a) ");

        Symbol symA = new Symbol("a", Symbol.Kind.VARIABLE, node.operand.name);
        currentScope.put("a", symA);
        
        String prevOp = currentOperatorName;
        String prevReturn = currentFunctionReturnType;
        currentOperatorName = opName;
        currentFunctionReturnType = node.returnType.name;
        node.code.accept(this);
        currentFunctionReturnType = prevReturn;
        currentOperatorName = prevOp;
        
        builder.append("\n");
        return null;
    }

    @Override
    public String visitBinaryOverload(BinaryOverloadNode node) {
        node.returnType.accept(this);
        String opName = "__bolt_operator_" + operatorToName(node.operator) + "_" + toCName(node.operand1.name) + "_" + toCName(node.operand2.name);
        builder.append(" ").append(opName)
               .append("(").append(toCName(node.operand1.name)).append(" a, ")
               .append(toCName(node.operand2.name)).append(" b) ");

        Symbol symA = new Symbol("a", Symbol.Kind.VARIABLE, node.operand1.name);
        Symbol symB = new Symbol("b", Symbol.Kind.VARIABLE, node.operand2.name);
        currentScope.put("a", symA);
        currentScope.put("b", symB);
        
        String prevOp = currentOperatorName;
        String prevReturn = currentFunctionReturnType;
        currentOperatorName = opName;
        currentFunctionReturnType = node.returnType.name;
        node.code.accept(this);
        currentFunctionReturnType = prevReturn;
        currentOperatorName = prevOp;

        builder.append("\n");
        return null;
    }

    private void collectStringAddends(ASTNode node, List<ASTNode> addends) {
        if (node instanceof BinaryExpression bin && bin.operator.equals("+") && isStringType(resolveType(bin.left)) && isStringType(resolveType(bin.right))) {
            collectStringAddends(bin.left, addends);
            collectStringAddends(bin.right, addends);
        } else {
            addends.add(node);
        }
    }

    @Override
    public String visitBinaryExpression(BinaryExpression node) {
        String t1 = resolveType(node.left);
        String t2 = resolveType(node.right);

        // handle assignments for strings
        if (node.operator.equals("=") && "string".equals(t1)) {
            builder.append("__bolt_string_assign(&");
            node.left.accept(this);
            builder.append(", ");
            node.right.accept(this);
            builder.append(")");
            return null;
        }

        // handle assignments for interfaces
        if (node.operator.equals("=")) {
            String leftBase = stripPointer(t1);
            Symbol leftSym = symbols.get(leftBase);
            if (leftSym != null && leftSym.kind == Symbol.Kind.INTERFACE) {
                String rightBase = stripPointer(t2);
                Symbol rightSym = symbols.get(rightBase);
                if (rightSym != null && rightSym.kind == Symbol.Kind.CLASS) {
                    node.left.accept(this);
                    builder.append(" = (").append(toCName(leftBase)).append("){ .obj = ");
                    if (!t2.endsWith("*")) builder.append("&");
                    node.right.accept(this);
                    builder.append(", .vtable = &__bolt_vtable_").append(toCName(rightBase)).append("_").append(toCName(leftBase)).append(" }");
                    return null;
                }
            }
        }

        if (node.operator.equals("+=") && "string".equals(t1)) {
            builder.append("__bolt_string_assign(&");
            node.left.accept(this);
            builder.append(", __bolt_string_concat(");
            node.left.accept(this);
            builder.append(", ");
            node.right.accept(this);
            builder.append("))");
            return null;
        }

        // handle string concat
        if ("string".equals(t1) || "string".equals(t2)) {
            if (node.operator.equals("+")) {
                String tmpVar = "__bolt_tmp_" + (tempCounter++);
                
                StringBuilder oldBuilder = builder;
                StringBuilder expr1Buf = new StringBuilder();
                StringBuilder expr2Buf = new StringBuilder();

                builder = expr1Buf;
                node.left.accept(this);
                String expr1 = expr1Buf.toString();

                builder = expr2Buf;
                node.right.accept(this);
                String expr2 = expr2Buf.toString();
                builder = oldBuilder;

                String call;
                if ("string".equals(t1) && "string".equals(t2)) {
                    List<ASTNode> addends = new ArrayList<>();
                    collectStringAddends(node, addends);
                    if (addends.size() > 2) {
                        StringBuilder multiConcatBuf = new StringBuilder();
                        multiConcatBuf.append("__bolt_string_concat_n(").append(addends.size()).append(", ");
                        for (int i = 0; i < addends.size(); i++) {
                            StringBuilder addendBuf = new StringBuilder();
                            builder = addendBuf;
                            addends.get(i).accept(this);
                            multiConcatBuf.append(addendBuf.toString());
                            builder = oldBuilder;
                            if (i < addends.size() - 1) multiConcatBuf.append(", ");
                        }
                        multiConcatBuf.append(")");
                        call = multiConcatBuf.toString();
                    } else {
                        call = "__bolt_string_concat(" + expr1 + ", " + expr2 + ")";
                    }
                } else if ("string".equals(t1) && "int".equals(t2)) {
                    call = "__bolt_concat_str_int(" + expr1 + ", " + expr2 + ")";
                } else if ("int".equals(t1) && "string".equals(t2)) {
                    call = "__bolt_concat_int_str(" + expr1 + ", " + expr2 + ")";
                } else {
                    // generic fallback for string concat
                    call = "__bolt_string_concat(" + expr1 + ", " + expr2 + ")";
                }
                
                tempStringDecls.add(new String[]{tmpVar, call});
                builder.append(tmpVar);
                return null;
            }
            
            if ("string".equals(t1) && "string".equals(t2)) {
                if (node.operator.equals("==")) {
                    builder.append("(strcmp(");
                    node.left.accept(this);
                    builder.append(", ");
                    node.right.accept(this);
                    builder.append(") == 0)");
                    return null;
                }
                if (node.operator.equals("!=")) {
                    builder.append("(strcmp(");
                    node.left.accept(this);
                    builder.append(", ");
                    node.right.accept(this);
                    builder.append(") != 0)");
                    return null;
                }
            }
        }

        // handle operator overloads
        String opName = "__bolt_operator_" + operatorToName(node.operator) + "_"
            + toCName(stripPointer(t1)) + "_" + toCName(stripPointer(t2));
        if (symbols.contains(opName) && !opName.equals(currentOperatorName)) {
            builder.append(opName).append("(");
            emitAsValue(node.left);
            builder.append(", ");
            emitAsValue(node.right);
            builder.append(")");
        } else {
            // standard C operator fallback
            emitOperand(node.left);
            builder.append(" ").append(node.operator).append(" ");
            emitOperand(node.right);
        }
        return null;
    }

    private void emitOperand(ASTNode operand) {
        if (operand instanceof BinaryExpression) {
            builder.append("(");
            operand.accept(this);
            builder.append(")");
        } else {
            operand.accept(this);
        }
    }

    @Override
    public String visitMemberAccess(MemberAccess node) {
        String type = resolveType(node.object);
        node.object.accept(this);
        String op = (type != null && type.endsWith("*")) ? "->" : ".";
        builder.append(op).append(node.member);
        return null;
    }

    @Override
    public String visitArrayAccess(ArrayAccess node) {
        node.array.accept(this);
        builder.append("[");
        node.index.accept(this);
        builder.append("]");
        return null;
    }


    @Override
    public String visitFunctionCall(FunctionCall node) {
        if (node.function instanceof Identifier id && !id.genericArguments.isEmpty()) {
             String mangledName = getMangledGenericName(id);
             if (!generatedInstantiations.contains(mangledName)) {
                 instantiateGenericFunction(id, mangledName);
             }
             builder.append(toCName(mangledName)).append("(");
             if (node.arguments != null) {
                for (int i = 0; i < node.arguments.size(); i++) {
                    node.arguments.get(i).accept(this);
                    if (i < node.arguments.size() - 1) {
                        builder.append(", ");
                    }
                }
            }
            builder.append(")");
            return null;
        }

        List<String> argTypes = new ArrayList<>();
        if (node.arguments != null) {
            for (ASTNode arg : node.arguments) {
                argTypes.add(resolveType(arg));
            }
        }

        if (node.function instanceof MemberAccess ma) {
            String type = resolveType(ma.object);
            String typeName = stripPointer(type);

            // for generics, we might need to find the base type to check if it should mangle
            String baseTypeName = typeName;
            int underscoreIndex = typeName.indexOf('_');
            if (underscoreIndex != -1) { // simple mangled generic name
                baseTypeName = typeName.substring(0, underscoreIndex);
            }

            Symbol classSym = symbols.get(typeName);
            if (classSym == null) classSym = symbols.get(baseTypeName);

            if (classSym != null && classSym.kind == Symbol.Kind.INTERFACE) {
                // Interface call: ((Interface_VTable*)ma.vtable)->member(ma.obj, args)
                builder.append("((").append(toCName(typeName)).append("_VTable*)");
                ma.object.accept(this);
                builder.append(".vtable)->").append(ma.member).append("(");
                ma.object.accept(this);
                builder.append(".obj");
                if (node.arguments != null && !node.arguments.isEmpty()) {
                    builder.append(", ");
                    for (int i = 0; i < node.arguments.size(); i++) {
                        node.arguments.get(i).accept(this);
                        if (i < node.arguments.size() - 1) builder.append(", ");
                    }
                }
                builder.append(")");
                return null;
            }

            boolean mangle = true;
            if (classSym != null && classSym.members.containsKey(ma.member)) {
                mangle = classSym.members.get(ma.member).mangle;
            }

            if (mangle) {
                builder.append(mangleIdentifier(ma.member, argTypes, typeName));
            } else {
                builder.append(toCName(typeName)).append("_").append(ma.member);
            }

            builder.append("(");

            Symbol objTypeSym = symbols.get(typeName);
            if (objTypeSym != null && (objTypeSym.kind == Symbol.Kind.CLASS || objTypeSym.kind == Symbol.Kind.STRUCT)) {
                if (type != null && !type.endsWith("*")) {
                    builder.append("&");
                }
            }
            ma.object.accept(this);
            
            if (node.arguments != null && !node.arguments.isEmpty()) {
                builder.append(", ");
                for (int i = 0; i < node.arguments.size(); i++) {
                    node.arguments.get(i).accept(this);
                    if (i < node.arguments.size() - 1) {
                        builder.append(", ");
                    }
                }
            }
            builder.append(")");
        } else if (node.function instanceof Identifier id) {
            String full = resolveTypeName(id.name);
            Symbol sym = symbols.get(full);
            boolean mangle = sym != null && sym.mangle;

            // fall back to scanning the tree for a matching FunctionDeclaration and apply the same shouldMangle() logic
            // that generateForwardDeclarations() uses, so the call name matches the declaration.
            if (sym == null) {
                for (ASTNode treeNode : tree) {
                    if (treeNode instanceof FunctionDeclaration fd && fd.name.equals(id.name)) {
                        mangle = shouldMangle(fd);
                        break;
                    }
                }
            }
            
            if (sym != null && sym.kind == Symbol.Kind.CLASS) {
                // Constructor call: Point(10, 20)
                String mangledInit = mangleIdentifier("init", argTypes, full);
                builder.append("*").append(mangledInit).append("(&(").append(toCName(full)).append("){0}");
                if (node.arguments != null && !node.arguments.isEmpty()) {
                    builder.append(", ");
                    for (int i = 0; i < node.arguments.size(); i++) {
                        node.arguments.get(i).accept(this);
                        if (i < node.arguments.size() - 1) builder.append(", ");
                    }
                }
                builder.append(")");
                return null; // complete call already built, do not fall through to arg emission block below
            } else if (mangle) {
                builder.append(mangleIdentifier(id.name, declaredParamTypes(id.name, argTypes), null));
            } else {
                boolean hasNonStdImport = imports.stream().anyMatch(imp -> !imp.startsWith("std."));
                if (sym == null && hasNonStdImport) {
                    builder.append(id.name);
                } else {
                    builder.append(mangleGlobalName(id.name));
                }
            }

            builder.append("(");
            if (node.arguments != null) {
                List<String> declared = declaredParamTypes(id.name, argTypes);
                for (int i = 0; i < node.arguments.size(); i++) {
                    emitArgument(node.arguments.get(i),
                        i < declared.size() ? declared.get(i) : null);
                    if (i < node.arguments.size() - 1) {
                        builder.append(", ");
                    }
                }
            }
            builder.append(")");
        } else if (node.function instanceof LambdaExpression) {
            // Lambda function call: emit the lambda name and arguments
            node.function.accept(this);
            builder.append("(");
            if (node.arguments != null) {
                for (int i = 0; i < node.arguments.size(); i++) {
                    node.arguments.get(i).accept(this);
                    if (i < node.arguments.size() - 1) {
                        builder.append(", ");
                    }
                }
            }
            builder.append(")");
        } else {
            node.function.accept(this);
            builder.append("(");
            if (node.arguments != null) {
                for (int i = 0; i < node.arguments.size(); i++) {
                    node.arguments.get(i).accept(this);
                    if (i < node.arguments.size() - 1) {
                        builder.append(", ");
                    }
                }
            }
            builder.append(")");
        }
        return null;
    }

    @Override
    public String visitUnaryExpression(UnaryExpression node) {
        String type = resolveType(node.operand);
        String opName = "__bolt_operator_" + operatorToName(node.operator) + "_"
            + toCName(stripPointer(type));

        if (symbols.contains(opName) && !opName.equals(currentOperatorName)) {
            builder.append(opName).append("(");
            emitAsValue(node.operand);
            builder.append(")");
        } else {
            builder.append(node.operator);
            emitOperand(node.operand);
        }
        return null;
    }

    private ASTNode findReturnedValue(ASTNode node) {
        if (node == null) return null;
        if (node instanceof ReturnStatement ret) return ret.value;
        if (node instanceof Block block && block.statements != null) {
            for (ASTNode stmt : block.statements) {
                ASTNode found = findReturnedValue(stmt);
                if (found != null) return found;
            }
        }
        if (node instanceof IfStatement is) {
            ASTNode found = findReturnedValue(is.thenBranch);
            return found != null ? found : findReturnedValue(is.elseBranch);
        }
        if (node instanceof WhileStatement ws) return findReturnedValue(ws.body);
        return null;
    }

    private String resolveInLambdaScope(LambdaExpression lambda, ASTNode expr) {
        SymbolTree oldScope = currentScope;
        currentScope = new SymbolTree(oldScope);
        for (Parameter p : lambda.parameters) {
            currentScope.put(p.name, new Symbol(p.name, Symbol.Kind.VARIABLE, getBaseTypeName(p.type)));
        }
        try {
            return resolveType(expr);
        } finally {
            currentScope = oldScope;
        }
    }

    private String toCType(String type, String fallback) {
        if (type == null || type.equals("unknown")) return fallback;
        if (isStringType(type)) return "char*";
        return toCName(type);
    }

    @Override
    public String visitLambdaExpression(LambdaExpression node) {
        // Check if this lambda was already collected
        int lambdaIndex = lambdaFunctions.indexOf(node);
        if (lambdaIndex == -1) {
            // Not collected yet, add it now
            lambdaIndex = lambdaFunctions.size();
            lambdaFunctions.add(node);

            // Build the function signature
            StringBuilder sig = new StringBuilder();
            sig.append("/* lambda */ ");

            // Determine return type from body
            String returnType = "void";
            if (node.body instanceof Block block) {
                ASTNode returned = findReturnedValue(block);
                if (returned != null) {
                    returnType = toCType(resolveInLambdaScope(node, returned), "void");
                }
            } else {
                returnType = toCType(resolveInLambdaScope(node, node.body), "__auto_type");
            }

            sig.append(returnType).append(" ").append("__bolt_lambda_" + lambdaIndex).append("(");

            // Add parameters
            for (int i = 0; i < node.parameters.size(); i++) {
                Parameter p = node.parameters.get(i);
                String paramType = toCName(p.type.toString());
                if (paramType.equals("unknown")) paramType = "__auto_type";
                sig.append(paramType).append(" ").append(p.name);
                if (i < node.parameters.size() - 1) {
                    sig.append(", ");
                }
            }

            sig.append(")");

            // Store the signature for later emission
            lambdaSignatures.put("__bolt_lambda_" + lambdaIndex, sig.toString());
        }

        // Emit the function call (the lambda itself)
        builder.append("__bolt_lambda_" + lambdaIndex);

        return null;
    }



    @Override
    public String visitReturnStatement(ReturnStatement node) {
        emitTrace(node);
        
        List<String[]> prevDecls = tempStringDecls;
        tempStringDecls = new ArrayList<>();
        StringBuilder exprBuf = new StringBuilder();
        
        String exprCode = "";
        if (node.value != null) {
            StringBuilder oldBuilder = builder;
            builder = exprBuf;
            if (isClassType(currentFunctionReturnType)) {
                emitAsValue(node.value);
            } else {
                node.value.accept(this);
            }
            builder = oldBuilder;
            exprCode = exprBuf.toString();
        }

        if (node.value == null) {
            for (int i = allActiveInstances.size() - 1; i >= 0; i--) {
                emitIndent();
                callDinit(allActiveInstances.get(i));
            }
            emitIndent();
            builder.append("return;\n");
        } else {
            // if we have temps or active instances, we need a block to handle cleanup
            if (!tempStringDecls.isEmpty() || !allActiveInstances.isEmpty()) {
                emitIndent();
                builder.append("{\n");
                indent();
                
                // emit temp string declarations
                for (String[] decl : tempStringDecls) {
                    emitIndent();
                    builder.append("char* ").append(decl[0]).append(" = ").append(decl[1]).append(";\n");
                }

                String type = resolveType(node.value);
                boolean isString = "string".equals(type);
                String cType = isString ? "char*" : (type.equals("unknown") ? "__auto_type" : toCName(type));

                emitIndent();
                builder.append(cType).append(" _ret = ");
                
                // determine if we need to copy the string result.
                // we dont copy if the result is one of our temporary strings (its already heap allocated and we own it)
                boolean isTemp = false;
                for (String[] decl : tempStringDecls) {
                    if (decl[0].equals(exprCode)) isTemp = true;
                }

                if (isString && !isTemp) builder.append("__bolt_string_copy(");
                builder.append(exprCode);
                if (isString && !isTemp) builder.append(")");
                builder.append(";\n");

                // free all temps EXCEPT the one we are returning
                for (String[] decl : tempStringDecls) {
                    if (!decl[0].equals(exprCode)) {
                        emitIndent();
                        builder.append("free(").append(decl[0]).append(");\n");
                    }
                }

                // dinit active stack instances
                for (int i = allActiveInstances.size() - 1; i >= 0; i--) {
                    emitIndent();
                    callDinit(allActiveInstances.get(i));
                }

                emitIndent();
                builder.append("return _ret;\n");
                outdent();
                emitIndent();
                builder.append("}\n");
            } else {
                emitIndent();
                builder.append("return ").append(exprCode).append(";\n");
            }
        }
        
        tempStringDecls = prevDecls;
        return null;
    }

    private String getBaseTypeName(ASTNode type) {
        if (type instanceof Identifier id) {
            if (!id.genericArguments.isEmpty()) {
                return getMangledGenericName(id);
            }
            return resolveTypeName(id.name);
        }
        if (type instanceof PointerType pt) return getBaseTypeName(pt.baseType);
        if (type instanceof ArrayType at) return getBaseTypeName(at.elementType);
        if (type instanceof PrimitiveType pt) return pt.name;
        return type.toString();
    }

    @Override
    public String visitVariableDeclaration(VariableDeclaration node) {
        emitTrace(node);
        String baseType = getBaseTypeName(node.type);

        Symbol sym = new Symbol(node.name, Symbol.Kind.VARIABLE, baseType);
        sym.isManual = hasDecorator(node, "manual");
        sym.isPointer = (node.type instanceof PointerType);
        currentScope.put(node.name, sym);

        StringBuilder typeAndName = new StringBuilder();
        StringBuilder oldBuilder = builder;
        builder = typeAndName;
        node.type.accept(this);
        builder.append(" ").append(node.name);
        emitArrayBrackets(node.type);
        String declPrefix = typeAndName.toString();
        builder = oldBuilder;

        StringBuilder initBuilder = new StringBuilder();
        builder = initBuilder;
        if (node.initializer != null) {
            if (isStringType(baseType) && !(node.initializer instanceof FunctionCall)) {
                builder.append("__bolt_string_copy(");
                node.initializer.accept(this);
                builder.append(")");
            } else if (node.initializer instanceof NewExpression ne
                    && !(node.type instanceof PointerType) && isClassType(baseType)) {
                emitInPlaceConstruction(ne);
            } else {
                node.initializer.accept(this);
            }
        } else if (isStringType(baseType)) {
            builder.append("NULL");
        }
        String initCode = initBuilder.toString();
        builder = oldBuilder;

        for (String[] decl : tempStringDecls) {
            emitIndent();
            builder.append("char* ").append(decl[0]).append(" = ").append(decl[1]).append(";\n");
        }
        tempStringDecls.clear();

        emitIndent();
        builder.append(declPrefix);
        if (!initCode.isEmpty()) {
            Symbol targetSym = symbols.get(baseType);
            if (targetSym != null && targetSym.kind == Symbol.Kind.INTERFACE) {
                String valType = resolveType(node.initializer);
                String valBase = stripPointer(valType);
                Symbol valSym = symbols.get(valBase);
                if (valSym != null && valSym.kind == Symbol.Kind.CLASS) {
                    // Implicit cast to interface: (Interface){ .obj = &val, .vtable = &__bolt_vtable_Val_Interface }
                    builder.append(" = (").append(toCName(baseType)).append("){ .obj = ");
                    if (!valType.endsWith("*")) builder.append("&");
                    builder.append(initCode).append(", .vtable = &__bolt_vtable_").append(toCName(valBase)).append("_").append(toCName(baseType)).append(" }");
                } else {
                    builder.append(" = ").append(initCode);
                }
            } else {
                builder.append(" = ").append(initCode);
            }
        }
        builder.append(";\n");

        Symbol classSym = symbols.get(baseType);
        if (isStringType(baseType)) {
            allActiveInstances.add(node.name);
        } else if (classSym != null && classSym.kind == Symbol.Kind.CLASS
                && !(node.type instanceof PointerType)) {
            allActiveInstances.add(node.name);
            if (node.initializer == null && classSym.members.containsKey("init")) {
                Symbol initSym = classSym.members.get("init");
                // Only call no-arg init if the init method has no parameters
                if (initSym != null && initSym.parameterTypes.isEmpty()) {
                    boolean mangle = config.getBoolean("mangle");
                    String method = mangle ? mangleIdentifier("init", new ArrayList<>(), baseType) : toCName(baseType) + "_init";
                    emitIndent();
                    builder.append(method).append("(&").append(node.name).append(");\n");
                }
            }
        }

        return null;
    }

    @Override
    public String visitPrimitiveType(PrimitiveType node) {
        if (isStringType(node.name)) {
            builder.append("char*");
            return "char*";
        }
        String resolved = resolveTypeName(node.name);
        builder.append(toCName(resolved));
        return resolved;
    }

    @Override
    public String visitPointerType(PointerType node) {
        node.baseType.accept(this);
        builder.append("*");
        return null;
    }

    @Override
    public String visitIdentifier(Identifier node) {
        if (genericMapping.containsKey(node.name)) {
            String realType = genericMapping.get(node.name);
            if (realType.equals("string")) {
                builder.append("char*");
            } else {
                builder.append(toCName(realType));
            }
            return realType;
        }
        if (currentGenericParams.contains(node.name)) {
            builder.append(node.name);
            return node.name;
        }
        if (isStringType(node.name)) {
            builder.append("char*");
            return "char*";
        }

        if (!node.genericArguments.isEmpty()) {
            String mangledName = getMangledGenericName(node);
            if (!generatedInstantiations.contains(mangledName)) {
                instantiateGeneric(node, mangledName);
            }
            builder.append(toCName(mangledName));
            return mangledName;
        }

        String asFunction = functionReferenceName(node.name);
        if (asFunction != null) {
            builder.append(asFunction);
            return node.name;
        }

        builder.append(node.name);
        return node.name;
    }

    private String functionReferenceName(String name) {
        if (currentScope != null) {
            Symbol existing = currentScope.get(name);
            if (existing != null && existing.kind == Symbol.Kind.VARIABLE) return null;
        }

        for (ASTNode treeNode : tree) {
            if (!(treeNode instanceof FunctionDeclaration fd) || !fd.name.equals(name)) continue;
            if (!fd.genericParams.isEmpty()) return null;

            List<String> paramTypes = new ArrayList<>();
            if (fd.parameters != null) {
                for (Parameter p : fd.parameters) {
                    paramTypes.add(getBaseTypeName(p.type));
                }
            }
            return shouldMangle(fd)
                ? mangleIdentifier(name, paramTypes, null)
                : mangleGlobalName(name);
        }
        return null;
    }

    private void emitArgument(ASTNode arg, String declaredType) {
        Symbol declaredSym = declaredType != null ? symbols.get(resolveTypeName(declaredType)) : null;
        if (declaredSym == null || declaredSym.kind != Symbol.Kind.INTERFACE) {
            arg.accept(this);
            return;
        }

        String argType = resolveType(arg);
        String argBase = stripPointer(argType);
        Symbol argSym = symbols.get(resolveTypeName(argBase));
        if (argSym == null || argSym.kind != Symbol.Kind.CLASS) {
            arg.accept(this);
            return;
        }

        builder.append("(").append(toCName(declaredType)).append("){ .obj = ");
        if (argType == null || !argType.endsWith("*")) builder.append("&");
        arg.accept(this);
        builder.append(", .vtable = &__bolt_vtable_").append(toCName(argBase))
               .append("_").append(toCName(declaredType)).append(" }");
    }

    private List<String> declaredParamTypes(String name, List<String> argTypes) {
        FunctionDeclaration arityMatch = null;
        for (ASTNode treeNode : tree) {
            if (!(treeNode instanceof FunctionDeclaration fd) || !fd.name.equals(name)) continue;
            int count = fd.parameters == null ? 0 : fd.parameters.size();
            if (count != argTypes.size()) continue;

            List<String> declared = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                declared.add(getBaseTypeName(fd.parameters.get(i).type));
            }
            if (declared.equals(argTypes)) return declared;
            if (arityMatch == null) arityMatch = fd;
        }

        if (arityMatch != null) {
            List<String> declared = new ArrayList<>();
            for (Parameter p : arityMatch.parameters) {
                declared.add(getBaseTypeName(p.type));
            }
            return declared;
        }
        return argTypes;
    }

    private String mangleGenericTypeName(String type) {
        if (type == null) return "unknown";
        int angle = type.indexOf('<');
        int close = type.lastIndexOf('>');
        if (angle == -1 || close < angle) return toCName(type);

        StringBuilder sb = new StringBuilder(toCName(type.substring(0, angle).trim()));
        for (String arg : type.substring(angle + 1, close).split(",")) {
            sb.append("_").append(toCName(arg.trim()));
        }
        return sb.toString();
    }

    private String getMangledGenericName(Identifier node) {
        StringBuilder sb = new StringBuilder(node.name);
        for (ASTNode arg : node.genericArguments) {
            sb.append("_").append(toCName(arg.toString()));
        }
        return sb.toString();
    }

    private void instantiateGeneric(Identifier node, String mangledName) {
        if (genericClasses.containsKey(node.name)) {
            generatedInstantiations.add(mangledName);
            ClassDeclaration template = genericClasses.get(node.name);

            Symbol instantiatedSym = new Symbol(mangledName, Symbol.Kind.CLASS, mangledName);
            instantiatedSym.mangle = true;
            symbols.put(mangledName, instantiatedSym);

            Map<String, String> oldMapping = genericMapping;
            genericMapping = new HashMap<>(oldMapping);
            for (int i = 0; i < template.genericParams.size(); i++) {
                genericMapping.put(template.genericParams.get(i), node.genericArguments.get(i).toString());
            }

            StringBuilder oldBuilder = builder;
            builder = genericBuilder;

            builder.append("typedef struct ").append(toCName(mangledName)).append(" ").append(toCName(mangledName)).append(";\n");
            generateStructDefinition(template, mangledName, toCName(mangledName));
            generateClassMethods(template, mangledName);

            builder = oldBuilder;
            genericMapping = oldMapping;
        }
    }

    private void instantiateGenericFunction(Identifier node, String mangledName) {
        if (genericFunctions.containsKey(node.name)) {
            generatedInstantiations.add(mangledName);
            FunctionDeclaration template = genericFunctions.get(node.name);

            Map<String, String> oldMapping = genericMapping;
            genericMapping = new HashMap<>(oldMapping);
            for (int i = 0; i < template.genericParams.size(); i++) {
                genericMapping.put(template.genericParams.get(i), node.genericArguments.get(i).toString());
            }

            StringBuilder oldBuilder = builder;
            builder = genericBuilder;

            generateSignature(template, null, false, mangledName);
            builder.append(" ");
            generateFunctionBody(template, null, false);

            builder = oldBuilder;
            genericMapping = oldMapping;
        }
    }

    private void generateFunctionBody(FunctionDeclaration node, String className, boolean isClass) {
        String oldFunc = currentFunctionName;
        String oldReturnType = currentFunctionReturnType;
        currentFunctionName = node.name;
        currentFunctionReturnType = node.returnType != null ? getBaseTypeName(node.returnType) : null;
        if (node.returnType instanceof PointerType) currentFunctionReturnType = null;

        String bindTarget = null;
        for (DecoratorNode dec : node.decorators) {
            if (dec.name.equals("bind")) {
                bindTarget = dec.arguments.get(0);
                break;
            }
        }

        if (bindTarget != null) {
            builder.append(getBraceStart()).append("\n");
            indent();
            emitIndent();
            if (!typeName(node.returnType).equals("void")) builder.append("return ");
            builder.append(bindTarget).append("(");
            if (node.parameters != null) {
                for (int i = 0; i < node.parameters.size(); i++) {
                    builder.append(node.parameters.get(i).name);
                    if (i < node.parameters.size() - 1) builder.append(", ");
                }
            }
            builder.append(");\n");
            outdent();
            emitIndent();
            builder.append("}\n");
            currentFunctionName = oldFunc;
            currentFunctionReturnType = oldReturnType;
            return;
        }

        List<String> oldGenericParams = currentGenericParams;
        if (className != null) {
            ClassDeclaration cls = genericClasses.get(stripGeneric(className));
            if (cls != null) {
                currentGenericParams = new ArrayList<>(cls.genericParams);
            }
        }
        currentGenericParams.addAll(node.genericParams);

        if (node.body != null) {
            if (node.body instanceof DecoratorNode dec && dec.name.equals("bind")) {
                builder.append(getBraceStart()).append("\n");
                indent();
                emitIndent();
                if (!typeName(node.returnType).equals("void")) builder.append("return ");
                builder.append(dec.arguments.get(0)).append("(");
                if (node.parameters != null) {
                    for (int i = 0; i < node.parameters.size(); i++) {
                        builder.append(node.parameters.get(i).name);
                        if (i < node.parameters.size() - 1) builder.append(", ");
                    }
                }
                builder.append(");\n");
                outdent();
                emitIndent();
                builder.append("}\n");
            } else {
                SymbolTree oldScope = currentScope;
                currentScope = new SymbolTree(oldScope);

                if (className != null) {
                    Symbol selfSym = new Symbol("self", Symbol.Kind.VARIABLE, className);
                    selfSym.isPointer = isClass;
                    currentScope.put("self", selfSym);
                }

                if (node.parameters != null) {
                    for (Parameter p : node.parameters) {
                        Symbol pSym = new Symbol(p.name, Symbol.Kind.VARIABLE, p.type.toString());
                        pSym.isPointer = (p.type instanceof PointerType);
                        currentScope.put(p.name, pSym);
                    }
                }

                String prelude = null;
                String postlude = null;
                if (className != null && (node.name.equals("init") || node.name.equals("dinit"))) {
                    prelude = "if (!self) return NULL;\n";
                    postlude = "return self;\n";
                }

                if (node.body instanceof Block b) {
                    visitBlock(b, prelude, postlude);
                } else {
                    builder.append(getBraceStart()).append("\n");
                    indent();
                    node.body.accept(this);
                    outdent();
                    emitIndent();
                    builder.append("}\n");
                }
                currentScope = oldScope;
            }
        } else {
            builder.append(";\n");
        }
        currentGenericParams = oldGenericParams;
        currentFunctionName = oldFunc;
        currentFunctionReturnType = oldReturnType;
    }

    @Override
    public String visitArrayType(ArrayType node) {
        node.elementType.accept(this);
        return null;
    }

    private void emitArrayBrackets(ASTNode type) {
        if (type instanceof ArrayType at) {
            builder.append("[");
            if (at.size != null) at.size.accept(this);
            builder.append("]");
            emitArrayBrackets(at.elementType);
        }
    }

    @Override
    public String visitFunctionDeclaration(FunctionDeclaration node) {
        if (!node.genericParams.isEmpty()) return null;
        generateSignature(node, null, false);
        builder.append(" ");
        generateFunctionBody(node, null, false);
        return null;
    }

    private void collectLambdas(ASTNode node) {
        if (node instanceof LambdaExpression lambda) {
            int lambdaIndex = lambdaFunctions.size();
            String lambdaName = "__bolt_lambda_" + lambdaIndex;
            lambdaFunctions.add(lambda);

            // Build the function signature
            StringBuilder sig = new StringBuilder();
            sig.append("/* lambda */ ");

            // Determine return type from body
            String returnType = "void";
            if (lambda.body instanceof Block block) {
                ASTNode returned = findReturnedValue(block);
                if (returned != null) {
                    returnType = toCType(resolveInLambdaScope(lambda, returned), "void");
                }
            } else {
                returnType = toCType(resolveInLambdaScope(lambda, lambda.body), "__auto_type");
            }

            sig.append(returnType).append(" ").append(lambdaName).append("(");

            // Add parameters
            for (int i = 0; i < lambda.parameters.size(); i++) {
                Parameter p = lambda.parameters.get(i);
                String paramType = toCName(p.type.toString());
                if (paramType.equals("unknown")) paramType = "__auto_type";
                sig.append(paramType).append(" ").append(p.name);
                if (i < lambda.parameters.size() - 1) {
                    sig.append(", ");
                }
            }

            sig.append(")");

            // Store the signature for later emission
            lambdaSignatures.put(lambdaName, sig.toString());
        } else if (node instanceof Block block) {
            for (ASTNode stmt : block.statements) {
                collectLambdas(stmt);
            }
        } else if (node instanceof FunctionDeclaration func) {
            if (func.body != null) {
                collectLambdas(func.body);
            }
        } else if (node instanceof IfStatement ifStmt) {
            collectLambdas(ifStmt.condition);
            collectLambdas(ifStmt.thenBranch);
            if (ifStmt.elseBranch != null) {
                collectLambdas(ifStmt.elseBranch);
            }
        } else if (node instanceof WhileStatement whileStmt) {
            collectLambdas(whileStmt.condition);
            collectLambdas(whileStmt.body);
        } else if (node instanceof DoWhileStatement doWhileStmt) {
            collectLambdas(doWhileStmt.condition);
            collectLambdas(doWhileStmt.body);
        } else if (node instanceof ForStatement forStmt) {
            if (forStmt.initializer != null) collectLambdas(forStmt.initializer);
            if (forStmt.condition != null) collectLambdas(forStmt.condition);
            if (forStmt.update != null) collectLambdas(forStmt.update);
            collectLambdas(forStmt.body);
        } else if (node instanceof SwitchStatement switchStmt) {
            collectLambdas(switchStmt.expression);
            collectLambdas(switchStmt.body);
        } else if (node instanceof CaseStatement caseStmt) {
            if (caseStmt.value != null) collectLambdas(caseStmt.value);
            for (ASTNode stmt : caseStmt.statements) {
                collectLambdas(stmt);
            }
        } else if (node instanceof DefaultStatement defaultStmt) {
            for (ASTNode stmt : defaultStmt.statements) {
                collectLambdas(stmt);
            }
        } else if (node instanceof ReturnStatement returnStmt) {
            if (returnStmt.value != null) collectLambdas(returnStmt.value);
        } else if (node instanceof ExpressionStatement exprStmt) {
            collectLambdas(exprStmt.expression);
        } else if (node instanceof VariableDeclaration varDecl) {
            if (varDecl.initializer != null) collectLambdas(varDecl.initializer);
        } else if (node instanceof AssignmentExpression assignExpr) {
            collectLambdas(assignExpr.target);
            collectLambdas(assignExpr.value);
        } else if (node instanceof BinaryExpression binExpr) {
            collectLambdas(binExpr.left);
            collectLambdas(binExpr.right);
        } else if (node instanceof UnaryExpression unaryExpr) {
            collectLambdas(unaryExpr.operand);
        } else if (node instanceof FunctionCall funcCall) {
            collectLambdas(funcCall.function);
            if (funcCall.arguments != null) {
                for (ASTNode arg : funcCall.arguments) {
                    collectLambdas(arg);
                }
            }
        } else if (node instanceof TernaryExpression ternaryExpr) {
            collectLambdas(ternaryExpr.condition);
            collectLambdas(ternaryExpr.trueExpression);
            collectLambdas(ternaryExpr.falseExpression);
        }
    }

    public Codegen() {
        this.tree = new ASTTree();
        this.symbols = new SymbolTree();
        this.config = new Config();
    }

    public Codegen(ASTTree tree) {
        this.tree = tree;
        this.symbols = new SymbolTree();
        this.config = new Config();
    }

    public Codegen(ASTTree tree, Config config) {
        this.tree = tree;
        this.symbols = new SymbolTree();
        this.config = config;
    }
}