package hydro.bolt.ast;

import hydro.bolt.ast.misc.*;
import hydro.bolt.parser.ErrorReporter;
import hydro.bolt.tokens.Token;
import hydro.bolt.tokens.TokenType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import hydro.bolt.ast.bolt.*;
import hydro.bolt.ast.decl.*;
import hydro.bolt.ast.type.*;
import hydro.bolt.ast.expr.*;
import hydro.bolt.ast.statement.*;
import hydro.bolt.Config;
import hydro.bolt.parser.Parser;
import hydro.bolt.parser.Symbol;
import hydro.bolt.parser.SymbolTree;
import hydro.bolt.template.Template;
import hydro.bolt.tokens.Tokenizer;

// almost 1800 lines of sadness and barely functional code generation
// Almost no comments either because Im lazy so good luck me
// held together by the will of Terry Davis
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
    private int recursionDepth = 0;

    public String generate() {
        currentScope = symbols;
        builder.setLength(0);

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
            builder.append("    char* res = (char*)malloc(strlen(s) + 1);\n");
            builder.append("    if (res) strcpy(res, s); return res;\n");
            builder.append("}\n\n");

            builder.append("static void __bolt_string_assign(char** dest, const char* src) {\n");
            builder.append("    if (*dest == src) return;\n");
            builder.append("    if (*dest) free(*dest);\n");
            builder.append("    *dest = src ? __bolt_string_copy(src) : NULL;\n");
            builder.append("}\n\n");

            builder.append("static char* __bolt_string_concat(const char* a, const char* b) {\n");
            builder.append("    if (!a) a = \"\"; if (!b) b = \"\";\n");
            builder.append("    int len = strlen(a) + strlen(b);\n");
            builder.append("    char* res = (char*)malloc(len + 1);\n");
            builder.append("    if (res) { strcpy(res, a); strcat(res, b); }\n");
            builder.append("    return res;\n");
            builder.append("}\n\n");

            builder.append("static char* __bolt_concat_int_str(int i, const char* s) {\n");
            builder.append("    if (!s) s = \"\";\n");
            builder.append("    char buf[").append(config.get("string-buffer-size")).append("];\n");
            builder.append("    sprintf(buf, \"%d\", i);\n");
            builder.append("    int len = strlen(buf) + strlen(s);\n");
            builder.append("    char* res = (char*)malloc(len + 1);\n");
            builder.append("    if (res) { strcpy(res, buf); strcat(res, s); }\n");
            builder.append("    return res;\n");
            builder.append("}\n\n");

            builder.append("static char* __bolt_concat_str_int(const char* s, int i) {\n");
            builder.append("    if (!s) s = \"\";\n");
            builder.append("    char buf[").append(config.get("string-buffer-size")).append("];\n");
            builder.append("    sprintf(buf, \"%d\", i);\n");
            builder.append("    int len = strlen(s) + strlen(buf);\n");
            builder.append("    char* res = (char*)malloc(len + 1);\n");
            builder.append("    if (res) { strcpy(res, s); strcat(res, buf); }\n");
            builder.append("    return res;\n");
            builder.append("}\n\n");

            builder.append("static char* __bolt_string_concat_n(int n, ...) {\n");
            builder.append("    va_list args;\n");
            builder.append("    va_start(args, n);\n");
            builder.append("    int total_len = 0;\n");
            builder.append("    const char** strs = (const char**)malloc(n * sizeof(char*));\n");
            builder.append("    for (int i = 0; i < n; i++) {\n");
            builder.append("        strs[i] = va_arg(args, const char*);\n");
            builder.append("        if (strs[i]) total_len += strlen(strs[i]);\n");
            builder.append("    }\n");
            builder.append("    va_end(args);\n");
            builder.append("    char* res = (char*)malloc(total_len + 1);\n");
            builder.append("    if (res) {\n");
            builder.append("        res[0] = '\\0';\n");
            builder.append("        for (int i = 0; i < n; i++) {\n");
            builder.append("            if (strs[i]) strcat(res, strs[i]);\n");
            builder.append("        }\n");
            builder.append("    }\n");
            builder.append("    free(strs);\n");
            builder.append("    return res;\n");
            builder.append("}\n\n");
        }

        generateForwardDeclarations();

        for (ASTNode node : tree) {
            if (node instanceof ClassDeclaration cls) {
                generateStructDefinition(cls);
            }
        }
        builder.append("\n");

        for (ASTNode node : tree) {            
            node.accept(this);
        }

        StringBuilder finalBuilder = new StringBuilder();
        if (!config.getBoolean("no-std-includes")) {
            int idx = builder.indexOf("#include <string.h>");
            if (idx != -1) {
                finalBuilder.append(builder.substring(0, idx));
                finalBuilder.append(genericBuilder);
                finalBuilder.append(builder.substring(idx));
            } else {
                finalBuilder.append(genericBuilder);
                finalBuilder.append(builder);
            }
        } else {
            finalBuilder.append(genericBuilder);
            finalBuilder.append(builder);
        }

        return finalBuilder.toString();
    }

    public String generateHeader() {
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
                String path = imp.path;
                if (path.equals("std.io")) {
                    builder.append("#include <stdio.h>\n");
                } else if (path.equals("std.stdlib")) {
                    builder.append("#include <stdlib.h>\n");
                } else if (path.equals("std.math")) {
                    builder.append("#include <math.h>\n");
                } else if (path.equals("std.string")) {
                    builder.append("#include <string.h>\n");
                } else if (path.equals("std.time")) {
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
                generateSignature(func, null, false);
                builder.append(";\n");
            } else if (node instanceof ClassDeclaration cls && cls.genericParams.isEmpty()) {
                String fullClassName = resolveTypeName(cls.name);
                if (cls.inner != null) {
                    for (ASTNode member : cls.inner) {
                        if (member instanceof FunctionDeclaration func) {
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
            }
        }
        builder.append("\n");
    }

    private void generateSignature(FunctionDeclaration func, String className, boolean isClass) {
        generateSignature(func, className, isClass, null);
    }

    private void generateSignature(FunctionDeclaration func, String className, boolean isClass, String customName) {
        if (hasDecorator(func, "inline")) {
            builder.append("static inline ");
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
                Parameter param = func.parameters.get(i);
                param.type.accept(this);
                builder.append(" ").append(param.name);
                if (i < func.parameters.size() - 1) {
                    builder.append(", ");
                }
            }
        }
        builder.append(")");
    }

    private String operatorToName(String op) {
        switch (op) {
            case "+": return "plus";
            case "-": return "minus";
            case "*": return "mul";
            case "/": return "div";
            case "%": return "mod";
            case "==": return "eq";
            case "!=": return "neq";
            case "<": return "lt";
            case "<=": return "lte";
            case ">": return "gt";
            case ">=": return "gte";
            case "&&": return "land";
            case "||": return "lor";
            case "!": return "lnot";
            case "&": return "band";
            case "|": return "bor";
            case "^": return "bxor";
            case "~": return "bnot";
            case "<<": return "shl";
            case ">>": return "shr";
            case "[": return "lbracket";
            case "]": return "rbracket";
            case "(": return "lparen";
            case ")": return "rparen";
            default:
                StringBuilder sb = new StringBuilder();
                for (char c : op.toCharArray()) {
                    if (Character.isLetterOrDigit(c)) sb.append(c);
                    else sb.append("_").append((int)c);
                }
                return sb.toString();
        }
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
        int angle = type.indexOf("<");
        if (angle != -1) return type.substring(0, angle);
        return type;
    }

    private String stripPointer(String type) {
        if (type == null) return null;
        String t = stripGeneric(type);
        return t.endsWith("*") ? t.substring(0, t.length() - 1).trim() : t;
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
            if (bin.operator.equals("+") && config.getBoolean("no-heap")) {
                reporter.report(new Token(TokenType.PLUS, "+", bin.line, bin.column, 0), "String concatenation is forbidden in no-heap mode");
            }
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
        int size = Integer.parseInt(config.get("indent-size"));
        String style = config.get("indent-style");
        String indent = style.equals("tab") ? "\t" : " ";
        for (int i = 0; i < indentLevel * size; i++) {
            builder.append(indent);
        }
    }

    private void indent() {
        indentLevel++;
    }

    private void outdent() {
        indentLevel--;
        if (indentLevel < 0) indentLevel = 0;
    }

    private String getBraceStart() {
        String style = config.get("brace-style");
        if (style.equalsIgnoreCase("allman")) {
            return "\n" + getIndentString() + "{";
        }
        return " {";
    }

    private String getIndentString() {
        int size = Integer.parseInt(config.get("indent-size"));
        String style = config.get("indent-style");
        String indent = style.equals("tab") ? "\t" : " ";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < indentLevel * size; i++) {
            sb.append(indent);
        }
        return sb.toString();
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

    private String getMangledType(String type) {
        if (type == null) return "v";
        type = stripPointer(type);
        switch (type) {
            case "int": return "i";
            case "float": return "f";
            case "char": return "c";
            case "string": return "s";
            case "bool": return "b";
            case "void": return "v";
            default:
                return type.length() + toCName(type);
        }
    }

    public String visitRawCNode(RawCNode node) {
        builder.append(node.content);
        // stupid: if content looks like struct definition but missing semicolon, add it.
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

    @Override
    public String visitSwitchStatement(SwitchStatement node) {
        emitTrace(node);
        builder.append("switch (");
        node.expression.accept(this);
        builder.append(") ");
        node.body.accept(this);
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

            if (sym.typeName.equals("string")) {
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
        if (config.getBoolean("no-heap")) {
            reporter.report(new Token(TokenType.KEYWORD, "new", node.line, node.column, 0), "Use of 'new' is forbidden in no-heap mode");
        }
        String fullTypeName = resolveTypeName(node.typeName);
        String cTypeName = toCName(fullTypeName);

        Symbol classSym = symbols.get(fullTypeName);
        if (classSym != null && classSym.members.containsKey("init")) {
            List<String> argTypes = new ArrayList<>();
            if (node.arguments != null) {
                for (ASTNode arg : node.arguments) argTypes.add(resolveType(arg));
            }
            String methodName = mangleIdentifier("init", argTypes, fullTypeName);
            builder.append(methodName).append("(*(").append(cTypeName).append("*)malloc(sizeof(").append(cTypeName).append("))");
            if (node.arguments != null && !node.arguments.isEmpty()) {
                builder.append(", ");
                for (int i = 0; i < node.arguments.size(); i++) {
                    node.arguments.get(i).accept(this);
                    if (i < node.arguments.size() - 1) builder.append(", ");
                }
            }
            builder.append(")");
        } else {
            builder.append("(*(").append(cTypeName).append("*)malloc(sizeof(").append(cTypeName).append(")))");
        }
        return null;
    }

    @Override
    public String visit(DeleteExpression node) {
        if (config.getBoolean("no-heap")) {
            reporter.report(new Token(TokenType.KEYWORD, "delete", node.line, node.column, 0), "Use of 'delete' is forbidden in no-heap mode");
        }
        String type = "unknown";
        if (node.target instanceof Identifier id) {
            Symbol sym = currentScope.get(id.name);
            if (sym != null) type = sym.typeName;
        } else if (node.target instanceof MemberAccess ma) {
            type = resolveType(ma);
        } else {
            type = resolveType(node.target);
        }

        Symbol classSym = symbols.get(stripPointer(type));
        if (classSym != null && classSym.members.containsKey("dinit")) {
            String methodName = true ? toCName(stripPointer(type)) + "_dinit" : "dinit";
            builder.append("free(").append(methodName).append("((").append(toCName(stripPointer(type))).append("*)");
            node.target.accept(this);
            builder.append("))");
        } else {
            builder.append("free(");
            node.target.accept(this);
            builder.append(")");
        }

        return null;
    }

    @Override
    public String visit(ImportDeclaration node) {
        String path = node.path;
        if (path.equals("std.io")) {
            builder.append("#include <stdio.h>\n");
        } else if (path.equals("std.stdlib")) {
            builder.append("#include <stdlib.h>\n");
        } else if (path.equals("std.math")) {
            builder.append("#include <math.h>\n");
        } else if (path.equals("std.string")) {
            builder.append("#include <string.h>\n");
        } else if (path.equals("std.time")) {
            builder.append("#include <time.h>\n");
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
        currentOperatorName = opName;
        node.code.accept(this);
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
        currentOperatorName = opName;
        node.code.accept(this);
        currentOperatorName = prevOp;
        
        builder.append("\n");
        return null;
    }

    private void collectStringAddends(ASTNode node, List<ASTNode> addends) {
        if (node instanceof BinaryExpression bin && bin.operator.equals("+") && resolveType(bin.left).equals("string") && resolveType(bin.right).equals("string")) {
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
        String opName = "__bolt_operator_" + operatorToName(node.operator) + "_" + toCName(t1) + "_" + toCName(t2);
        if (symbols.contains(opName) && !opName.equals(currentOperatorName)) {
            builder.append(opName).append("(");
            node.left.accept(this);
            builder.append(", ");
            node.right.accept(this);
            builder.append(")");
        } else {
            // standard C operator fallback
            node.left.accept(this);
            builder.append(" ").append(node.operator).append(" ");
            node.right.accept(this);
        }
        return null;
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

    // gets the root object name
    private String getObjectName(ASTNode node) {
        if (node instanceof Identifier id) {
            return id.name;
        } else if (node instanceof MemberAccess ma) {
            return getObjectName(ma.object);
        }
        return node.toString();
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
            if (typeName.contains("_")) { // simple mangled generic name
                baseTypeName = typeName.substring(0, typeName.indexOf("_"));
            }

            Symbol classSym = symbols.get(typeName);
            if (classSym == null) classSym = symbols.get(baseTypeName);

            boolean mangle = true;
            if (classSym != null && classSym.members.containsKey(ma.member)) {
                mangle = classSym.members.get(ma.member).mangle;
            }

            if (mangle) {
                System.out.println("Mangling " + ma.member + " with args " + argTypes + " and type " + typeName + " to " + mangleIdentifier(ma.member, argTypes, typeName));
                builder.append(mangleIdentifier(ma.member, argTypes, typeName));
            } else {
                builder.append(toCName(typeName)).append("_").append(ma.member);
            }
            
            // Recursion check
            if (!config.getBoolean("allow-recursion") && ma.member.equals(currentFunctionName)) {
                reporter.report(new Token(TokenType.IDENTIFIER, ma.member, node.line, node.column, 0), 
                    "Recursion is forbidden by configuration");
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
                builder.append(mangleIdentifier(id.name, argTypes, null));
            } else {
                boolean hasNonStdImport = imports.stream().anyMatch(imp -> !imp.startsWith("std."));
                if (sym == null && hasNonStdImport) {
                    builder.append(id.name);
                } else {
                    builder.append(mangleGlobalName(id.name));
                }
            }
            
            if (!config.getBoolean("allow-recursion") && id.name.equals(currentFunctionName)) {
                reporter.report(new Token(TokenType.IDENTIFIER, id.name, node.line, node.column, 0), 
                    "Recursion is forbidden by configuration");
            }

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
        String opName = "__bolt_operator_" + operatorToName(node.operator) + "_" + toCName(type);

        if (symbols.contains(opName) && !opName.equals(currentOperatorName)) {
            builder.append(opName).append("(");
            node.operand.accept(this);
            builder.append(")");
        } else {
            builder.append(node.operator);
            node.operand.accept(this);
        }
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
            node.value.accept(this);
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

        if (config.getBoolean("no-heap") && baseType.equals("string")) {
             reporter.report(new Token(TokenType.IDENTIFIER, node.name, node.line, node.column, 0), "Managed 'string' is forbidden in no-heap mode. Use 'char*' for raw buffers.");
        }

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
            if (baseType.equals("string") && !(node.initializer instanceof FunctionCall)) {
                builder.append("__bolt_string_copy(");
                node.initializer.accept(this);
                builder.append(")");
            } else {
                node.initializer.accept(this);
            }
        } else if (baseType.equals("string")) {
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
            builder.append(" = ").append(initCode);
        }
        builder.append(";\n");

        Symbol classSym = symbols.get(baseType);
        if (baseType.equals("string")) {
            allActiveInstances.add(node.name);
        } else if (classSym != null && classSym.kind == Symbol.Kind.CLASS && !(node.type instanceof PointerType)) {
            allActiveInstances.add(node.name);
            if (node.initializer == null && classSym.members.containsKey("init")) {
                boolean mangle = config.getBoolean("mangle");
                String method = mangle ? mangleIdentifier("init", new ArrayList<>(), baseType) : toCName(baseType) + "_init";
                emitIndent();
                builder.append(method).append("(&").append(node.name).append(");\n");
            }
        }

        return null;
    }

    @Override
    public String visitPrimitiveType(PrimitiveType node) {
        if (node.name.equals("string")) {
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
        if (node.name.equals("string")) {
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

        builder.append(node.name);
        return node.name;
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
        currentFunctionName = node.name;

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
                boolean mangle = config.getBoolean("mangle");
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