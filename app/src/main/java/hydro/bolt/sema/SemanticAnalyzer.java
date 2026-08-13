package hydro.bolt.sema;

import hydro.bolt.Config;
import hydro.bolt.ast.*;
import hydro.bolt.ast.bolt.*;
import hydro.bolt.ast.decl.*;
import hydro.bolt.ast.expr.*;
import hydro.bolt.ast.misc.*;
import hydro.bolt.ast.statement.*;
import hydro.bolt.ast.type.*;
import hydro.bolt.parser.ErrorCode;
import hydro.bolt.parser.ErrorReporter;

import java.util.*;

public class SemanticAnalyzer {
    private final ASTTree tree;
    private final Config config;
    private final ErrorReporter reporter;
    private final List<String> imports;
    private final Map<String, ClassInfo> classes = new HashMap<>();
    private final Map<String, InterfaceDeclaration> interfaces = new HashMap<>();
    private final Map<String, List<FunctionDeclaration>> functions = new HashMap<>();
    private final Map<String, String> globals = new HashMap<>();
    private final Set<String> knownTypes = new HashSet<>();
    private final Set<String> enumConstants = new HashSet<>();
    private final Map<String, String> typedefs = new HashMap<>();
    private final Set<String> externalSymbols = new HashSet<>();
    private final Set<String> rawIdentifiers = new HashSet<>();
    private boolean openWorld = false;
    private final boolean strict;
    private final boolean noHeap;
    private final boolean allowRecursion;
    private final boolean allowLambdas;
    private final boolean allowOperatorOverloading;
    private Scope scope = new Scope();
    private FunctionDeclaration currentFunction;
    private String currentReturnType;
    private ClassInfo currentClass;
    private int loopDepth;
    private int switchDepth;
    private final Deque<List<String>> genericParamStack = new ArrayDeque<>();
    public SemanticAnalyzer(ASTTree tree, Config config, ErrorReporter reporter, List<String> imports) {
        this.tree = tree;
        this.config = config;
        this.reporter = reporter;
        this.imports = imports != null ? imports : List.of();
        this.strict = config.getBoolean("strict-typing");
        this.noHeap = config.getBoolean("no-heap");
        this.allowRecursion = config.getBoolean("allow-recursion");
        this.allowLambdas = config.getBoolean("lambdas");
        this.allowOperatorOverloading = config.getBoolean("operator-overloading");
    }

    public void analyze() {
        collectImports();
        collect();
        for (ASTNode node : tree) {
            checkDeclaration(node);
        }
    }

    private void collectImports() {
        for (String path : imports) {
            if (CStdlib.isKnownHeader(path)) {
                externalSymbols.addAll(CStdlib.symbolsFor(path));
            } else {
                openWorld = true;
            }
        }
        externalSymbols.addAll(CStdlib.ALWAYS_AVAILABLE);

        if (!config.getBoolean("no-std-includes")) {
            for (String header : List.of("stdio", "stdlib", "string", "stdarg")) {
                externalSymbols.addAll(CStdlib.symbolsFor(header));
            }
        }
    }

    private void collect() {
        knownTypes.addAll(Set.of(
            "void", "bool", "char", "short", "int", "long", "float", "double",
            "signed", "unsigned", "string", "size_t", "ssize_t", "ptrdiff_t",
            "int8_t", "int16_t", "int32_t", "int64_t",
            "uint8_t", "uint16_t", "uint32_t", "uint64_t",
            "FILE", "va_list", "time_t", "clock_t"
        ));
        for (ASTNode node : tree) {
            collectRawIdentifiers(node);
        }
        for (ASTNode node : tree) {
            if (node instanceof ClassDeclaration cls) {
                if (classes.containsKey(cls.name)) {
                    error(ErrorCode.DUPLICATE_DECLARATION, cls, cls.name.length(),
                        "class '" + cls.name + "' is declared more than once");
                    continue;
                }
                classes.put(cls.name, buildClassInfo(cls));
                knownTypes.add(cls.name);
            } else if (node instanceof InterfaceDeclaration iface) {
                interfaces.put(iface.name, iface);
                knownTypes.add(iface.name);
            } else if (node instanceof StructDeclaration s) {
                knownTypes.add(s.name);
            } else if (node instanceof UnionDeclaration u) {
                knownTypes.add(u.name);
            } else if (node instanceof EnumDeclaration e) {
                knownTypes.add(e.name);
                if (e.members != null) {
                    for (EnumMember m : e.members) {
                        enumConstants.add(m.name);
                    }
                }
            } else if (node instanceof TypedefDeclaration t) {
                knownTypes.add(t.name);
                String aliased = typeToString(t.type);
                if (aliased != null) typedefs.put(t.name, aliased);
            }
        }
        for (ASTNode node : tree) {
            if (node instanceof ImplDeclaration impl) {
                ClassInfo info = classes.get(impl.targetType);
                if (info == null) continue;
                for (ASTNode member : impl.members) {
                    addMember(info, member);
                }
            }
        }
        for (ASTNode node : tree) {
            if (node instanceof FunctionDeclaration func) {
                functions.computeIfAbsent(func.name, k -> new ArrayList<>()).add(func);
            } else if (node instanceof VariableDeclaration var) {
                globals.put(var.name, typeToString(var.type));
            }
        }
        for (Map.Entry<String, List<FunctionDeclaration>> e : functions.entrySet()) {
            reportDuplicateOverloads(e.getKey(), e.getValue());
        }
    }

    private void collectRawIdentifiers(ASTNode node) {
        if (node == null) return;
        if (node instanceof RawCNode raw) {
            extractIdentifiers(raw.content, rawIdentifiers);
            return;
        }
        if (node instanceof Block b) {
            forEach(b.statements, this::collectRawIdentifiers);
        } else if (node instanceof FunctionDeclaration f) {
            collectRawIdentifiers(f.body);
        } else if (node instanceof ClassDeclaration c) {
            if (c.inner != null) {
                for (ASTNode m : c.inner) collectRawIdentifiers(m);
            }
        } else if (node instanceof ImplDeclaration i) {
            forEach(i.members, this::collectRawIdentifiers);
        } else if (node instanceof IfStatement s) {
            collectRawIdentifiers(s.thenBranch);
            collectRawIdentifiers(s.elseBranch);
        } else if (node instanceof WhileStatement s) {
            collectRawIdentifiers(s.body);
        } else if (node instanceof DoWhileStatement s) {
            collectRawIdentifiers(s.body);
        } else if (node instanceof ForStatement s) {
            collectRawIdentifiers(s.initializer);
            collectRawIdentifiers(s.body);
        } else if (node instanceof SwitchStatement s) {
            collectRawIdentifiers(s.body);
        } else if (node instanceof CaseStatement s) {
            forEach(s.statements, this::collectRawIdentifiers);
        } else if (node instanceof DefaultStatement s) {
            forEach(s.statements, this::collectRawIdentifiers);
        } else if (node instanceof LabeledStatement s) {
            collectRawIdentifiers(s.statement);
        } else if (node instanceof LambdaExpression l) {
            collectRawIdentifiers(l.body);
        } else if (node instanceof OverloadNode o) {
            collectRawIdentifiers(o.code);
        }
    }

    static void extractIdentifiers(String content, Set<String> out) {
        if (content == null) return;
        int i = 0;
        while (i < content.length()) {
            char c = content.charAt(i);
            if (c == '"' || c == '\'') {
                char quote = c;
                i++;
                while (i < content.length() && content.charAt(i) != quote) {
                    if (content.charAt(i) == '\\') i++;
                    i++;
                }
                i++;
                continue;
            }
            if (Character.isJavaIdentifierStart(c)) {
                int start = i;
                while (i < content.length() && Character.isJavaIdentifierPart(content.charAt(i))) {
                    i++;
                }
                out.add(content.substring(start, i));
                continue;
            }
            i++;
        }
    }

    private ClassInfo buildClassInfo(ClassDeclaration cls) {
        ClassInfo info = new ClassInfo(cls.name, cls);
        info.genericParams.addAll(cls.genericParams);
        info.interfaces.addAll(cls.interfaces);
        if (cls.inner != null) {
            for (ASTNode member : cls.inner) {
                addMember(info, member);
            }
        }
        return info;
    }

    private void addMember(ClassInfo info, ASTNode member) {
        if (member instanceof VariableDeclaration var) {
            info.fieldTypes.put(var.name, typeToString(var.type));
            info.fieldVisibility.put(var.name, var.visibility);
        } else if (member instanceof FunctionDeclaration func) {
            info.methods.put(func.name, func);
            info.methodVisibility.put(func.name, func.visibility);
        }
    }

    private void reportDuplicateOverloads(String name, List<FunctionDeclaration> overloads) {
        if (overloads.size() < 2) return;
        Set<String> seen = new HashSet<>();
        for (FunctionDeclaration func : overloads) {
            if (func.body == null) continue;
            String signature = name + "(" + String.join(",", parameterTypes(func)) + ")";
            if (!seen.add(signature)) {
                error(ErrorCode.DUPLICATE_DECLARATION, func, name.length(),
                    "'" + name + "' is already defined with these parameter types");
            }
        }
    }

    private List<String> parameterTypes(FunctionDeclaration func) {
        List<String> out = new ArrayList<>();
        if (func.parameters != null) {
            for (Parameter p : func.parameters) {
                String t = typeToString(p.type);
                out.add(t != null ? t : "?");
            }
        }
        return out;
    }

    private void checkDeclaration(ASTNode node) {
        if (node instanceof FunctionDeclaration func) {
            checkFunction(func, null);
        } else if (node instanceof ClassDeclaration cls) {
            checkClass(cls);
        } else if (node instanceof ImplDeclaration impl) {
            checkImpl(impl);
        } else if (node instanceof VariableDeclaration var) {
            checkTypeExists(var.type);
            if (var.initializer != null) {
                String declared = typeToString(var.type);
                String actual = inferType(var.initializer);
                checkAssignable(actual, declared, var, "initializer for '" + var.name + "'");
            }
            checkNoHeapDeclaration(var);
        } else if (node instanceof UnaryOverloadNode || node instanceof BinaryOverloadNode) {
            checkOperatorOverload(node);
        } else if (node instanceof StructDeclaration s) {
            if (s.members != null) {
                for (StructMember m : s.members) checkTypeExists(m.type);
            }
        } else if (node instanceof UnionDeclaration u) {
            if (u.members != null) {
                for (StructMember m : u.members) checkTypeExists(m.type);
            }
        } else if (node instanceof TypedefDeclaration t) {
            checkTypeExists(t.type);
        }
    }

    private void checkClass(ClassDeclaration cls) {
        ClassInfo info = classes.get(cls.name);
        if (info == null) return;
        checkInterfaceConformance(cls, info);
        ClassInfo previousClass = currentClass;
        currentClass = info;
        genericParamStack.push(info.genericParams);
        try {
            if (cls.inner != null) {
                for (ASTNode member : cls.inner) {
                    if (member instanceof FunctionDeclaration func) {
                        checkFunction(func, info);
                    } else if (member instanceof VariableDeclaration var) {
                        checkTypeExists(var.type);
                    }
                }
            }
        } finally {
            genericParamStack.pop();
            currentClass = previousClass;
        }
    }

    private void checkImpl(ImplDeclaration impl) {
        ClassInfo info = classes.get(impl.targetType);
        if (info == null) {
            if (!openWorld && !knownTypes.contains(impl.targetType)) {
                error(ErrorCode.UNKNOWN_CLASS, impl, impl.targetType.length(),
                    "no type named '" + impl.targetType + "' to implement methods on");
            }
            return;
        }
        ClassInfo previousClass = currentClass;
        currentClass = info;
        genericParamStack.push(info.genericParams);
        try {
            for (ASTNode member : impl.members) {
                if (member instanceof FunctionDeclaration func) {
                    checkFunction(func, info);
                }
            }
        } finally {
            genericParamStack.pop();
            currentClass = previousClass;
        }
    }

    private void checkInterfaceConformance(ClassDeclaration cls, ClassInfo info) {
        for (String interfaceName : info.interfaces) {
            InterfaceDeclaration iface = interfaces.get(interfaceName);
            if (iface == null) {
                if (!openWorld && !knownTypes.contains(interfaceName)) {
                    error(ErrorCode.UNKNOWN_TYPE, cls, interfaceName.length(),
                        "unknown interface '" + interfaceName + "'");
                }
                continue;
            }
            if (iface.inner == null) continue;
            for (ASTNode member : iface.inner) {
                if (!(member instanceof FunctionDeclaration required)) continue;
                FunctionDeclaration provided = info.methods.get(required.name);
                if (provided == null) {
                    error(ErrorCode.INTERFACE_NOT_IMPLEMENTED, cls, cls.name.length(),
                        "class '" + cls.name + "' does not implement '" + required.name
                            + "' required by interface '" + interfaceName + "'");
                    continue;
                }
                int requiredCount = required.parameters == null ? 0 : required.parameters.size();
                int providedCount = provided.parameters == null ? 0 : provided.parameters.size();
                if (requiredCount != providedCount) {
                    error(ErrorCode.INTERFACE_NOT_IMPLEMENTED, provided, required.name.length(),
                        "'" + required.name + "' takes " + requiredCount + " parameter"
                            + (requiredCount == 1 ? "" : "s") + " in interface '" + interfaceName
                            + "' but " + providedCount + " here");
                    continue;
                }
                String requiredReturn = typeToString(required.returnType);
                String providedReturn = typeToString(provided.returnType);
                if (!Types.isUnknown(requiredReturn) && !Types.isUnknown(providedReturn)
                        && !requiredReturn.equals(providedReturn)) {
                    error(ErrorCode.INTERFACE_NOT_IMPLEMENTED, provided, required.name.length(),
                        "'" + required.name + "' returns '" + requiredReturn + "' in interface '"
                            + interfaceName + "' but '" + providedReturn + "' here");
                }

                if (info.methodVisibility.get(required.name) == Visibility.PRIVATE) {
                    error(ErrorCode.INTERFACE_NOT_IMPLEMENTED, provided, required.name.length(),
                        "'" + required.name + "' implements interface '" + interfaceName
                            + "' so it must be public");
                }
            }
        }
    }

    private void checkFunction(FunctionDeclaration func, ClassInfo owner) {
        FunctionDeclaration previousFunction = currentFunction;
        String previousReturn = currentReturnType;
        Scope previousScope = scope;
        int previousLoopDepth = loopDepth;
        currentFunction = func;
        currentReturnType = typeToString(func.returnType);
        scope = new Scope();
        loopDepth = 0;
        genericParamStack.push(func.genericParams);
        try {
            checkTypeExists(func.returnType);
            if (owner != null) {
                String selfType = owner.isGeneric() ? owner.name : Types.pointerTo(owner.name);
                scope.declare("self", selfType, func.line, func.column, false);
            }
            if (func.parameters != null) {
                for (Parameter p : func.parameters) {
                    checkTypeExists(p.type);
                    scope.declare(p.name, typeToString(p.type), p.line, p.column, false);
                }
            }

            if (func.body != null) {
                checkStatement(func.body);
                checkMissingReturn(func);
            }
        } finally {
            genericParamStack.pop();
            loopDepth = previousLoopDepth;
            scope = previousScope;
            currentReturnType = previousReturn;
            currentFunction = previousFunction;
        }
    }

    private void checkMissingReturn(FunctionDeclaration func) {
        String returnType = currentReturnType;
        if (Types.isUnknown(returnType) || Types.isVoid(returnType)) return;
        if (func.name.equals("main")) return;
        if (func.name.equals("init") || func.name.equals("dinit")) return;
        if (containsValueReturn(func.body)) return;
        if (containsRawC(func.body)) return;
        error(ErrorCode.MISSING_RETURN, func, func.name.length(),
            "'" + func.name + "' returns '" + returnType + "' but never returns a value");
    }

    private void checkOperatorOverload(ASTNode node) {
        if (!allowOperatorOverloading) {
            String op = node instanceof UnaryOverloadNode u ? u.operator
                      : node instanceof BinaryOverloadNode b ? b.operator : "?";
            error(ErrorCode.OPERATOR_OVERLOADING_FORBIDDEN, node, Math.max(1, op.length()),
                "operator overloading is disabled; set 'operator-overloading=true' in bolt.cfg "
                    + "to overload '" + op + "'");
        }
    }

    private void checkStatement(ASTNode node) {
        if (node == null) return;
        if (node instanceof Block block) {
            Scope previous = scope;
            scope = new Scope(previous);
            try {
                checkBlockStatements(block.statements);
                reportUnusedLocals(scope);
            } finally {
                scope = previous;
            }
        } else if (node instanceof VariableDeclaration var) {
            checkLocalVariable(var);
        } else if (node instanceof ExpressionStatement stmt) {
            inferType(stmt.expression);
        } else if (node instanceof IfStatement stmt) {
            checkCondition(stmt.condition, "if");
            checkStatement(stmt.thenBranch);
            checkStatement(stmt.elseBranch);
        } else if (node instanceof WhileStatement stmt) {
            checkCondition(stmt.condition, "while");
            loopDepth++;
            checkStatement(stmt.body);
            loopDepth--;
        } else if (node instanceof DoWhileStatement stmt) {
            loopDepth++;
            checkStatement(stmt.body);
            loopDepth--;
            checkCondition(stmt.condition, "do-while");
        } else if (node instanceof ForStatement stmt) {
            Scope previous = scope;
            scope = new Scope(previous);
            try {
                checkForClause(stmt.initializer);
                if (stmt.condition != null) checkCondition(stmt.condition, "for");
                checkForClause(stmt.update);
                loopDepth++;
                checkStatement(stmt.body);
                loopDepth--;
            } finally {
                scope = previous;
            }
        } else if (node instanceof SwitchStatement stmt) {
            inferType(stmt.expression);
            switchDepth++;
            checkStatement(stmt.body);
            switchDepth--;
        } else if (node instanceof CaseStatement stmt) {
            inferType(stmt.value);
            checkBlockStatements(stmt.statements);
        } else if (node instanceof DefaultStatement stmt) {
            checkBlockStatements(stmt.statements);
        } else if (node instanceof ReturnStatement stmt) {
            checkReturn(stmt);
        } else if (node instanceof BreakStatement) {
            if (loopDepth == 0 && switchDepth == 0) {
                error(ErrorCode.BREAK_OUTSIDE_LOOP, node, 5,
                    "'break' outside of a loop or switch");
            }
        } else if (node instanceof ContinueStatement) {
            if (loopDepth == 0) {
                error(ErrorCode.CONTINUE_OUTSIDE_LOOP, node, 8, "'continue' outside of a loop");
            }
        } else if (node instanceof LabeledStatement stmt) {
            checkStatement(stmt.statement);
        } else if (node instanceof RawCNode) {
        } else if (node instanceof ClassDeclaration || node instanceof InterfaceDeclaration
                || node instanceof FunctionDeclaration) {
            checkDeclaration(node);
        } else {
            inferType(node);
        }
    }

    private void checkForClause(ASTNode clause) {
        if (clause == null) return;
        if (clause instanceof Block block) {
            forEach(block.statements, this::checkStatement);
            return;
        }
        checkStatement(clause);
    }

    private void checkBlockStatements(List<ASTNode> statements) {
        if (statements == null) return;
        boolean reportedUnreachable = false;
        boolean terminated = false;
        for (ASTNode stmt : statements) {
            if (terminated && !reportedUnreachable && !(stmt instanceof LabeledStatement)) {
                warn(ErrorCode.UNREACHABLE_CODE, stmt, 1,
                    "unreachable code");
                reportedUnreachable = true;
            }
            checkStatement(stmt);
            if (terminatesFlow(stmt)) {
                terminated = true;
            }
        }
    }

    private void checkLocalVariable(VariableDeclaration var) {
        checkTypeExists(var.type);
        String declared = typeToString(var.type);
        if (var.initializer != null) {
            String actual = inferType(var.initializer);
            checkAssignable(actual, declared, var, "initializer for '" + var.name + "'");
        }
        checkNoHeapDeclaration(var);
        if (scope.lookupLocal(var.name) != null) {
            error(ErrorCode.DUPLICATE_DECLARATION, var, var.name.length(),
                "'" + var.name + "' is already declared in this scope");
        } else if (scope.lookupEnclosing(var.name) != null) {
            warn(ErrorCode.SHADOWED_DECLARATION, var, var.name.length(),
                "'" + var.name + "' shadows a declaration from an enclosing scope");
        }

        scope.declare(var.name, declared, var.line, var.column, true);
    }

    private void checkNoHeapDeclaration(VariableDeclaration var) {
        if (!noHeap) return;
        if (Types.isString(typeToString(var.type))) {
            error(ErrorCode.NO_HEAP_VIOLATION, var, var.name.length(),
                "managed 'string' allocates; use 'char*' for raw buffers in no-heap mode");
        }
    }

    private void checkCondition(ASTNode condition, String construct) {
        String type = norm(inferType(condition));
        if (Types.isUnknown(type)) return;
        if (!Types.isScalar(type)) {
            typeDiagnostic(ErrorCode.INVALID_CONDITION, condition, 1,
                "'" + construct + "' condition has type '" + type
                    + "', which cannot be tested for truth");
        }
    }

    private void checkReturn(ReturnStatement stmt) {
        if (stmt.value == null) {
            if (currentReturnType != null && !Types.isUnknown(currentReturnType)
                    && !Types.isVoid(currentReturnType) && currentFunction != null
                    && !currentFunction.name.equals("main")) {
                typeDiagnostic(ErrorCode.RETURN_TYPE_MISMATCH, stmt, 6,
                    "returning no value from '" + currentFunction.name
                        + "', which returns '" + currentReturnType + "'");
            }
            return;
        }
        String actual = inferType(stmt.value);
        if (Types.isVoid(currentReturnType)) {
            if (!Types.isUnknown(actual) && !Types.isVoid(actual)) {
                typeDiagnostic(ErrorCode.RETURN_TYPE_MISMATCH, stmt, 6,
                    "returning a value from '" + safeFunctionName() + "', which returns 'void'");
            }
            return;
        }
        if (!isCompatible(actual, currentReturnType)) {
            typeDiagnostic(ErrorCode.RETURN_TYPE_MISMATCH, stmt, 6,
                "returning '" + actual + "' from '" + safeFunctionName()
                    + "', which returns '" + currentReturnType + "'");
        }
    }

    private String safeFunctionName() {
        return currentFunction != null ? currentFunction.name : "this function";
    }

    private void reportUnusedLocals(Scope block) {
        if (!strict) return;
        for (Scope.Entry entry : block.entries()) {
            if (rawIdentifiers.contains(entry.name)) continue;
            if (!Types.isPrimitive(entry.type)) continue;
            if (entry.reportUnused && !entry.used) {
                reporter.warn(ErrorCode.UNUSED_VARIABLE, entry.line, entry.column,
                    entry.name.length(), "'" + entry.name + "' is declared but never used");
            }
        }
    }

    private String inferType(ASTNode node) {
        if (node == null) return Types.UNKNOWN;
        if (node instanceof IntegerLiteral) return "int";
        if (node instanceof FloatLiteral) return "float";
        if (node instanceof CharLiteral) return "char";
        if (node instanceof StringLiteral) return "string";
        if (node instanceof Identifier id) return inferIdentifier(id);
        if (node instanceof VariableExpression ve) return inferName(ve.name, ve, ve.name.length());
        if (node instanceof BinaryExpression bin) return inferBinary(bin);
        if (node instanceof UnaryExpression un) return inferUnary(un);
        if (node instanceof AssignmentExpression assign) return inferAssignment(assign);
        if (node instanceof TernaryExpression ternary) return inferTernary(ternary);
        if (node instanceof FunctionCall call) return inferCall(call);
        if (node instanceof MemberAccess ma) return inferMemberAccess(ma.object, ma.member, ma, false);
        if (node instanceof PointerMemberAccess pma) return inferMemberAccess(pma.pointer, pma.member, pma, true);
        if (node instanceof ArrayAccess aa) return inferArrayAccess(aa);
        if (node instanceof CastExpression cast) {
            inferType(cast.expression);
            checkTypeExists(cast.type);
            return typeToString(cast.type);
        }
        if (node instanceof SizeofExpression sizeof) {
            if (sizeof.operand != null && !(sizeof.operand instanceof Identifier)) {
                inferType(sizeof.operand);
            }
            return "size_t";
        }
        if (node instanceof NewExpression ne) return inferNew(ne);
        if (node instanceof DeleteExpression de) return inferDelete(de);
        if (node instanceof LambdaExpression lambda) return inferLambda(lambda);
        if (node instanceof InitializerList list) {
            if (list.elements != null) {
                for (ASTNode element : list.elements) inferType(element);
            }
            return Types.UNKNOWN;
        }
        if (node instanceof RawCNode raw) return literalType(raw.content);

        return Types.UNKNOWN;
    }

    static String literalType(String content) {
        if (content == null) return Types.UNKNOWN;
        String text = content.trim();
        if (text.isEmpty()) return Types.UNKNOWN;
        if (text.startsWith("\"")) return "string";
        if (text.startsWith("'")) return "char";
        if (text.equals("true") || text.equals("false")) return "int";
        if (text.equals("NULL")) return "void*";
        String lowerText = text.toLowerCase();
        if (lowerText.startsWith("0x") || lowerText.startsWith("0b")) {
            boolean hex = lowerText.startsWith("0x");
            String digits = text.substring(2);
            int digitEnd = digits.length();
            while (digitEnd > 0 && "uUlL".indexOf(digits.charAt(digitEnd - 1)) >= 0
                    && !(hex && Character.digit(digits.charAt(digitEnd - 1), 16) >= 0)) {
                digitEnd--;
            }
            String radixSuffix = digits.substring(digitEnd);
            digits = digits.substring(0, digitEnd);
            if (digits.isEmpty()) return Types.UNKNOWN;
            for (int i = 0; i < digits.length(); i++) {
                char c = digits.charAt(i);
                boolean ok = hex ? Character.digit(c, 16) >= 0 : (c == '0' || c == '1');
                if (!ok) return Types.UNKNOWN;
            }
            return radixSuffix.toLowerCase().contains("l") ? "long" : "int";
        }
        String body = text;
        int end = body.length();
        while (end > 0 && "uUlLfF".indexOf(body.charAt(end - 1)) >= 0) {
            end--;
        }
        String suffix = body.substring(end);
        body = body.substring(0, end);
        if (body.isEmpty()) return Types.UNKNOWN;
        boolean digitsOnly = body.chars().allMatch(Character::isDigit);
        if (digitsOnly) {
            if (suffix.toLowerCase().contains("f")) return "float";
            return suffix.toLowerCase().contains("l") ? "long" : "int";
        }
        if (body.matches("\\d*\\.\\d+([eE][+-]?\\d+)?") || body.matches("\\d+\\.\\d*([eE][+-]?\\d+)?")) {
            return "float";
        }
        return Types.UNKNOWN;
    }

    private String inferIdentifier(Identifier id) {
        checkGenericArity(id);
        return inferName(id.name, id, id.name.length());
    }

    private static final Set<String> PASSTHROUGH_KEYWORDS = Set.of(
        "break", "continue", "goto", "static", "const", "volatile",
        "register", "auto", "extern", "inline", "restrict", "sizeof"
    );
    private String inferName(String name, ASTNode node, int length) {
        if (PASSTHROUGH_KEYWORDS.contains(name)) {
            checkJumpKeyword(name, node);
            return Types.UNKNOWN;
        }
        Scope.Entry local = scope.lookup(name);
        if (local != null) {
            scope.markUsed(name);
            return local.type;
        }
        if (name.equals("self")) {
            if (currentClass == null) {
                error(ErrorCode.SELF_OUTSIDE_METHOD, node, 4,
                    "'self' is only available inside a class method");
            }
            return currentClass != null ? Types.pointerTo(currentClass.name) : Types.UNKNOWN;
        }
        if (globals.containsKey(name)) return globals.get(name);
        if (enumConstants.contains(name)) return "int";
        if (classes.containsKey(name) || interfaces.containsKey(name)) return name;
        if (functions.containsKey(name)) {
            List<FunctionDeclaration> overloads = functions.get(name);
            return overloads.size() == 1 ? typeToString(overloads.get(0).returnType) : Types.UNKNOWN;
        }
        if (externalSymbols.contains(name)) return Types.UNKNOWN;
        if (rawIdentifiers.contains(name)) return Types.UNKNOWN;
        if (currentGenericParams().contains(name)) return Types.UNKNOWN;
        if (knownTypes.contains(name)) return name;
        if (!openWorld) {
            error(ErrorCode.UNDEFINED_SYMBOL, node, length,
                "cannot find '" + name + "' in this scope");
        }
        return Types.UNKNOWN;
    }

    private void checkJumpKeyword(String name, ASTNode node) {
        if (name.equals("break") && loopDepth == 0 && switchDepth == 0) {
            error(ErrorCode.BREAK_OUTSIDE_LOOP, node, name.length(),
                "'break' outside of a loop or switch");
        } else if (name.equals("continue") && loopDepth == 0) {
            error(ErrorCode.CONTINUE_OUTSIDE_LOOP, node, name.length(),
                "'continue' outside of a loop");
        }
    }

    private String norm(String type) {
        String current = type;
        for (int depth = 0; depth < 8; depth++) {
            if (current == null) return null;
            String base = Types.baseName(current);
            String aliased = typedefs.get(base);
            if (aliased == null) return current;
            String suffix = current.substring(base.length());
            current = aliased + suffix;
        }
        return current;
    }

    private String inferBinary(BinaryExpression bin) {
        String left = norm(inferType(bin.left));
        String right = norm(inferType(bin.right));
        String op = bin.operator;
        if (op.equals("=")) {
            checkAssignable(right, left, bin, "assignment");
            return left;
        }
        switch (op) {
            case "==": case "!=": case "<": case "<=": case ">": case ">=":
            case "&&": case "||":
                return "int";
            default:
                break;
        }
        boolean stringInvolved = Types.isString(left) || Types.isString(right);
        if (stringInvolved && op.equals("+")) {
            if (noHeap) {
                error(ErrorCode.NO_HEAP_VIOLATION, bin, op.length(),
                    "string concatenation allocates and is forbidden in no-heap mode");
            }
            return "string";
        }

        if (hasOperatorOverload(op, left, right)) {
            return Types.UNKNOWN;
        }
        if (!Types.isUnknown(left) && !Types.isUnknown(right)) {
            boolean bitwise = op.equals("&") || op.equals("|") || op.equals("^")
                || op.equals("<<") || op.equals(">>") || op.equals("%");
            if (bitwise && (Types.isFloating(left) || Types.isFloating(right))) {
                typeDiagnostic(ErrorCode.TYPE_MISMATCH, bin, op.length(),
                    "operator '" + op + "' cannot be applied to floating-point operands '"
                        + left + "' and '" + right + "'");
            } else if (!Types.isScalar(left) || !Types.isScalar(right)) {
                typeDiagnostic(ErrorCode.TYPE_MISMATCH, bin, op.length(),
                    "operator '" + op + "' cannot be applied to '" + left + "' and '" + right + "'");
            }
        }
        return Types.arithmeticResult(left, right);
    }

    private boolean hasOperatorOverload(String op, String left, String right) {
        for (ASTNode node : tree) {
            if (node instanceof BinaryOverloadNode b && b.operator.equals(op)) {
                if (Types.isUnknown(left) || Types.isUnknown(right)) return true;
                if (b.operand1.name.equals(Types.baseName(left))
                        && b.operand2.name.equals(Types.baseName(right))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasUnaryOverload(String op, String operand) {
        for (ASTNode node : tree) {
            if (node instanceof UnaryOverloadNode u && u.operator.equals(op)) {
                if (Types.isUnknown(operand)) return true;
                if (u.operand.name.equals(Types.baseName(operand))) return true;
            }
        }
        return false;
    }

    private String inferUnary(UnaryExpression un) {
        String operand = norm(inferType(un.operand));
        String op = un.operator;

        if (hasUnaryOverload(op, operand)) return Types.UNKNOWN;
        switch (op) {
            case "!":
                return "int";
            case "&":
                return Types.isUnknown(operand) ? Types.UNKNOWN : Types.pointerTo(operand);
            case "*":
                if (Types.isUnknown(operand)) return Types.UNKNOWN;
                if (!Types.isPointer(operand)) {
                    typeDiagnostic(ErrorCode.TYPE_MISMATCH, un, 1,
                        "cannot dereference '" + operand + "', which is not a pointer");
                    return Types.UNKNOWN;
                }
                return Types.deref(operand);
            case "~":
                if (!Types.isUnknown(operand) && Types.isFloating(operand)) {
                    typeDiagnostic(ErrorCode.TYPE_MISMATCH, un, 1,
                        "operator '~' cannot be applied to '" + operand + "'");
                }
                return operand;
            default:
                return operand;
        }
    }

    private String inferAssignment(AssignmentExpression assign) {
        String target = inferType(assign.target);
        String value = inferType(assign.value);

        if (assign.operator.equals("+=") && Types.isString(target)) {
            if (noHeap) {
                error(ErrorCode.NO_HEAP_VIOLATION, assign, 2,
                    "string concatenation allocates and is forbidden in no-heap mode");
            }
            return target;
        }
        if (!isAssignable(assign.target)) {
            typeDiagnostic(ErrorCode.TYPE_MISMATCH, assign, assign.operator.length(),
                "left-hand side of '" + assign.operator + "' is not assignable");
            return target;
        }

        checkAssignable(value, target, assign, "assignment");
        return target;
    }

    private boolean isAssignable(ASTNode target) {
        return !(target instanceof Literal)
            && !(target instanceof FunctionCall)
            && !(target instanceof NewExpression)
            && !(target instanceof BinaryExpression);
    }

    private String inferTernary(TernaryExpression ternary) {
        checkCondition(ternary.condition, "ternary");
        String left = inferType(ternary.trueExpression);
        String right = inferType(ternary.falseExpression);
        if (Types.isUnknown(left)) return right;
        if (Types.isUnknown(right)) return left;
        if (!isCompatible(right, left)) {
            typeDiagnostic(ErrorCode.TYPE_MISMATCH, ternary, 1,
                "ternary branches have incompatible types '" + left + "' and '" + right + "'");
        }
        return Types.arithmeticResult(left, right);
    }

    private String inferArrayAccess(ArrayAccess aa) {
        String array = norm(inferType(aa.array));
        String index = norm(inferType(aa.index));
        if (!Types.isUnknown(index) && !Types.isInteger(index) && !Types.isUnknown(array)) {
            typeDiagnostic(ErrorCode.TYPE_MISMATCH, aa, 1,
                "array index has type '" + index + "', expected an integer");
        }
        if (Types.isUnknown(array)) return Types.UNKNOWN;
        if (Types.isString(array)) return "char";
        if (Types.isPointer(array)) return Types.deref(array);

        typeDiagnostic(ErrorCode.TYPE_MISMATCH, aa, 1,
            "cannot index into '" + array + "'");
        return Types.UNKNOWN;
    }

    private String inferMemberAccess(ASTNode objectNode, String member, ASTNode site, boolean arrow) {
        String objectType = inferType(objectNode);
        if (Types.isUnknown(objectType)) return Types.UNKNOWN;
        String base = Types.baseName(objectType);
        ClassInfo info = classes.get(base);
        if (info == null) {
            InterfaceDeclaration iface = interfaces.get(base);
            if (iface != null) return interfaceMemberType(iface, member, site);
            return Types.UNKNOWN;
        }
        if (info.isGeneric()) return Types.UNKNOWN;
        if (!info.hasMember(member)) {
            error(ErrorCode.UNKNOWN_MEMBER, site, member.length(),
                "'" + base + "' has no member named '" + member + "'");
            return Types.UNKNOWN;
        }
        if (info.visibilityOf(member) == Visibility.PRIVATE && currentClass != info) {
            error(ErrorCode.PRIVATE_MEMBER, site, member.length(),
                "'" + member + "' is private to '" + base + "'");
        }
        if (info.fieldTypes.containsKey(member)) return info.fieldTypes.get(member);
        FunctionDeclaration method = info.methods.get(member);
        return method != null ? typeToString(method.returnType) : Types.UNKNOWN;
    }

    private String interfaceMemberType(InterfaceDeclaration iface, String member, ASTNode site) {
        if (iface.inner != null) {
            for (ASTNode m : iface.inner) {
                if (m instanceof FunctionDeclaration f && f.name.equals(member)) {
                    return typeToString(f.returnType);
                }
            }
        }
        error(ErrorCode.UNKNOWN_MEMBER, site, member.length(),
            "interface '" + iface.name + "' has no method named '" + member + "'");
        return Types.UNKNOWN;
    }

    private String inferNew(NewExpression ne) {
        if (noHeap) {
            error(ErrorCode.NO_HEAP_VIOLATION, ne, 3,
                "'new' allocates and is forbidden in no-heap mode");
        }
        if (ne.arguments != null) {
            for (ASTNode arg : ne.arguments) inferType(arg);
        }
        String base = Types.baseName(ne.typeName);
        ClassInfo info = classes.get(base);
        if (info == null && !openWorld && !knownTypes.contains(base)
                && !currentGenericParams().contains(base)) {
            error(ErrorCode.UNKNOWN_CLASS, ne, base.length(),
                "cannot allocate unknown type '" + base + "'");
            return Types.UNKNOWN;
        }
        if (info != null) {
            FunctionDeclaration init = info.methods.get("init");
            if (init != null && !info.isGeneric()) {
                checkArguments(init, ne.arguments, ne, "init");
            }
        }

        return Types.pointerTo(ne.typeName);
    }

    private String inferDelete(DeleteExpression de) {
        if (noHeap) {
            error(ErrorCode.NO_HEAP_VIOLATION, de, 6,
                "'delete' frees heap memory and is forbidden in no-heap mode");
        }
        String target = inferType(de.target);
        if (!Types.isUnknown(target) && Types.isPrimitive(target) && !Types.isString(target)) {
            error(ErrorCode.DELETE_NON_POINTER, de, 6,
                "cannot delete '" + target + "'; only heap-allocated values can be deleted");
        }
        return "void";
    }

    private String inferLambda(LambdaExpression lambda) {
        if (!allowLambdas) {
            error(ErrorCode.LAMBDAS_FORBIDDEN, lambda, 2,
                "lambdas are disabled; set 'lambdas=true' in bolt.cfg to use them");
        }
        Scope previous = scope;
        scope = new Scope(previous);
        try {
            if (lambda.parameters != null) {
                for (Parameter p : lambda.parameters) {
                    checkTypeExists(p.type);
                    scope.declare(p.name, typeToString(p.type), p.line, p.column, false);
                }
            }
            if (lambda.body instanceof Block block) {
                checkBlockStatements(block.statements);
            } else if (lambda.body != null) {
                inferType(lambda.body);
            }
        } finally {
            scope = previous;
        }
        return Types.UNKNOWN;
    }

    private String inferCall(FunctionCall call) {
        if (call.arguments != null) {
            for (ASTNode arg : call.arguments) inferType(arg);
        }
        if (call.function instanceof Identifier id) {
            return inferDirectCall(id, call);
        }
        if (call.function instanceof MemberAccess ma) {
            return inferMethodCall(ma.object, ma.member, ma, call);
        }
        if (call.function instanceof PointerMemberAccess pma) {
            return inferMethodCall(pma.pointer, pma.member, pma, call);
        }
        if (call.function instanceof LambdaExpression lambda) {
            return inferLambda(lambda);
        }
        return inferType(call.function);
    }

    private String inferDirectCall(Identifier id, FunctionCall call) {
        checkGenericArity(id);
        String name = id.name;
        if (name.equals("sizeof")) return "size_t";
        if (PASSTHROUGH_KEYWORDS.contains(name)) return Types.UNKNOWN;
        Scope.Entry local = scope.lookup(name);
        if (local != null) {
            scope.markUsed(name);
            return Types.UNKNOWN;
        }
        List<FunctionDeclaration> overloads = functions.get(name);
        if (overloads == null) {
            if (classes.containsKey(name)) {
                ClassInfo info = classes.get(name);
                FunctionDeclaration init = info.methods.get("init");
                if (init != null && !info.isGeneric()) {
                    checkArguments(init, call.arguments, call, "init");
                }
                return name;
            }
            if (!externalSymbols.contains(name) && !openWorld
                    && !enumConstants.contains(name) && !knownTypes.contains(name)) {
                error(ErrorCode.UNDEFINED_SYMBOL, id, name.length(),
                    "cannot find function '" + name + "' in this scope");
            }
            return Types.UNKNOWN;
        }

        checkRecursion(name, id);
        if (overloads.size() == 1) {
            FunctionDeclaration func = overloads.get(0);
            if (func.genericParams.isEmpty()) {
                checkArguments(func, call.arguments, id, name);
            }
            return typeToString(func.returnType);
        }

        int argCount = call.arguments == null ? 0 : call.arguments.size();
        boolean anyArityMatches = overloads.stream()
            .anyMatch(f -> (f.parameters == null ? 0 : f.parameters.size()) == argCount);
        if (!anyArityMatches) {
            error(ErrorCode.ARGUMENT_COUNT_MISMATCH, id, name.length(),
                "no overload of '" + name + "' takes " + argCount + " argument"
                    + (argCount == 1 ? "" : "s"));
        }
        return Types.UNKNOWN;
    }

    private String inferMethodCall(ASTNode objectNode, String member, ASTNode site, FunctionCall call) {
        String objectType = inferType(objectNode);
        if (Types.isUnknown(objectType)) return Types.UNKNOWN;
        String base = Types.baseName(objectType);
        ClassInfo info = classes.get(base);
        if (info == null) {
            InterfaceDeclaration iface = interfaces.get(base);
            if (iface != null) return interfaceMemberType(iface, member, site);
            return Types.UNKNOWN;
        }
        if (info.isGeneric()) return Types.UNKNOWN;
        FunctionDeclaration method = info.methods.get(member);
        if (method == null) {
            if (info.fieldTypes.containsKey(member)) {
                return Types.UNKNOWN;
            }
            error(ErrorCode.UNKNOWN_MEMBER, site, member.length(),
                "'" + base + "' has no method named '" + member + "'");
            return Types.UNKNOWN;
        }
        if (info.methodVisibility.get(member) == Visibility.PRIVATE && currentClass != info) {
            error(ErrorCode.PRIVATE_MEMBER, site, member.length(),
                "'" + member + "' is private to '" + base + "'");
        }
        checkRecursion(member, site);
        if (method.genericParams.isEmpty()) {
            checkArguments(method, call.arguments, site, member);
        }
        return typeToString(method.returnType);
    }

    private void checkRecursion(String calleeName, ASTNode site) {
        if (allowRecursion) return;
        if (currentFunction == null) return;
        if (!currentFunction.name.equals(calleeName)) return;
        error(ErrorCode.RECURSION_FORBIDDEN, site, calleeName.length(),
            "'" + calleeName + "' calls itself, but recursion is disabled "
                + "('allow-recursion=false')");
    }

    private void checkArguments(FunctionDeclaration func, List<ASTNode> args, ASTNode site, String name) {
        int expected = func.parameters == null ? 0 : func.parameters.size();
        int actual = args == null ? 0 : args.size();

        if (expected != actual) {
            error(ErrorCode.ARGUMENT_COUNT_MISMATCH, site, name.length(),
                "'" + name + "' takes " + expected + " argument" + (expected == 1 ? "" : "s")
                    + " but " + actual + " " + (actual == 1 ? "was" : "were") + " given");
            return;
        }
        if (args == null) return;
        for (int i = 0; i < expected; i++) {
            Parameter param = func.parameters.get(i);
            String declared = typeToString(param.type);
            String supplied = inferType(args.get(i));
            if (!isCompatible(supplied, declared)) {
                typeDiagnostic(ErrorCode.ARGUMENT_TYPE_MISMATCH, args.get(i), 1,
                    "argument " + (i + 1) + " of '" + name + "' expects '" + declared
                        + "' but got '" + supplied + "'");
            }
        }
    }

    private String typeToString(ASTNode type) {
        if (type == null) return Types.UNKNOWN;
        if (type instanceof PrimitiveType p) return p.name;
        if (type instanceof Identifier id) return id.toString();
        if (type instanceof PointerType pt) {
            String base = typeToString(pt.baseType);
            return base == null ? Types.UNKNOWN : base + "*";
        }
        if (type instanceof ArrayType at) {
            String element = typeToString(at.elementType);
            return element == null ? Types.UNKNOWN : element + "*";
        }
        if (type instanceof StructType st) return st.name;
        if (type instanceof UnionType ut) return ut.name;
        if (type instanceof EnumType et) return et.name;
        return Types.UNKNOWN;
    }

    private void checkTypeExists(ASTNode typeNode) {
        if (typeNode == null) return;
        if (typeNode instanceof PointerType pt) {
            checkTypeExists(pt.baseType);
            return;
        }
        if (typeNode instanceof ArrayType at) {
            checkTypeExists(at.elementType);
            return;
        }
        if (!(typeNode instanceof Identifier id)) return;
        checkGenericArity(id);
        String name = id.name;
        if (knownTypes.contains(name)) return;
        if (Types.isBuiltin(name)) return;
        if (currentGenericParams().contains(name)) return;
        if (externalSymbols.contains(name)) return;
        if (openWorld) return;
        error(ErrorCode.UNKNOWN_TYPE, id, name.length(), "unknown type '" + name + "'");
    }

    private void checkGenericArity(Identifier id) {
        if (id.genericArguments.isEmpty()) return;
        ClassInfo info = classes.get(id.name);
        int declared;
        if (info != null) {
            declared = info.genericParams.size();
        } else {
            List<FunctionDeclaration> overloads = functions.get(id.name);
            if (overloads == null || overloads.size() != 1) return;
            declared = overloads.get(0).genericParams.size();
        }
        if (declared == 0) return;
        int supplied = id.genericArguments.size();
        if (supplied != declared) {
            error(ErrorCode.GENERIC_ARITY_MISMATCH, id, id.name.length(),
                "'" + id.name + "' takes " + declared + " type argument"
                    + (declared == 1 ? "" : "s") + " but " + supplied
                    + " " + (supplied == 1 ? "was" : "were") + " given");
        }
        for (ASTNode arg : id.genericArguments) {
            checkTypeExists(arg);
        }
    }

    private List<String> currentGenericParams() {
        List<String> all = new ArrayList<>();
        for (List<String> frame : genericParamStack) {
            all.addAll(frame);
        }
        return all;
    }

    private boolean isCompatible(String rawFrom, String rawTo) {
        String from = norm(rawFrom);
        String to = norm(rawTo);
        if (Types.assignable(from, to)) return true;
        String fromBase = Types.baseName(from);
        String toBase = Types.baseName(to);

        if (fromBase.equals(toBase) && classes.containsKey(fromBase)) return true;
        if (!interfaces.containsKey(toBase)) return false;
        ClassInfo info = classes.get(fromBase);
        if (info == null) return true;
        return info.interfaces.contains(toBase);
    }

    private void checkAssignable(String from, String to, ASTNode site, String context) {
        if (Types.isUnknown(from) || Types.isUnknown(to)) return;

        if (!isCompatible(from, to)) {
            typeDiagnostic(ErrorCode.TYPE_MISMATCH, site, 1,
                "cannot use '" + from + "' as '" + to + "' in " + context);
            return;
        }
        if (strict && Types.narrows(from, to)) {
            warn(ErrorCode.IMPLICIT_NARROWING, site, 1,
                "'" + from + "' is narrowed to '" + to + "' in " + context
                    + "; add an explicit cast to silence this");
        }
    }

    private boolean terminatesFlow(ASTNode stmt) {
        return stmt instanceof ReturnStatement
            || stmt instanceof BreakStatement
            || stmt instanceof ContinueStatement
            || stmt instanceof GotoStatement;
    }

    private boolean containsValueReturn(ASTNode node) {
        if (node == null) return false;
        if (node instanceof ReturnStatement r) return r.value != null;
        if (node instanceof Block b) {
            return anyMatch(b.statements, this::containsValueReturn);
        }
        if (node instanceof IfStatement s) {
            return containsValueReturn(s.thenBranch) || containsValueReturn(s.elseBranch);
        }
        if (node instanceof WhileStatement s) return containsValueReturn(s.body);
        if (node instanceof DoWhileStatement s) return containsValueReturn(s.body);
        if (node instanceof ForStatement s) return containsValueReturn(s.body);
        if (node instanceof SwitchStatement s) return containsValueReturn(s.body);
        if (node instanceof CaseStatement s) return anyMatch(s.statements, this::containsValueReturn);
        if (node instanceof DefaultStatement s) return anyMatch(s.statements, this::containsValueReturn);
        if (node instanceof LabeledStatement s) return containsValueReturn(s.statement);
        return false;
    }

    private boolean containsRawC(ASTNode node) {
        if (node == null) return false;
        if (node instanceof RawCNode) return true;
        if (node instanceof Block b) return anyMatch(b.statements, this::containsRawC);
        if (node instanceof IfStatement s) {
            return containsRawC(s.thenBranch) || containsRawC(s.elseBranch);
        }
        if (node instanceof WhileStatement s) return containsRawC(s.body);
        if (node instanceof DoWhileStatement s) return containsRawC(s.body);
        if (node instanceof ForStatement s) return containsRawC(s.body);
        if (node instanceof SwitchStatement s) return containsRawC(s.body);
        if (node instanceof CaseStatement s) return anyMatch(s.statements, this::containsRawC);
        if (node instanceof DefaultStatement s) return anyMatch(s.statements, this::containsRawC);
        if (node instanceof LabeledStatement s) return containsRawC(s.statement);
        return false;
    }

    private void forEach(List<ASTNode> nodes, java.util.function.Consumer<ASTNode> action) {
        if (nodes == null) return;
        for (ASTNode n : nodes) {
            action.accept(n);
        }
    }

    private boolean anyMatch(List<ASTNode> nodes, java.util.function.Predicate<ASTNode> test) {
        if (nodes == null) return false;
        for (ASTNode n : nodes) {
            if (test.test(n)) return true;
        }
        return false;
    }

    private void error(ErrorCode code, ASTNode node, int length, String message) {
        reporter.error(code, node.line, node.column, length, message);
    }

    private void warn(ErrorCode code, ASTNode node, int length, String message) {
        reporter.warn(code, node.line, node.column, length, message);
    }

    private void typeDiagnostic(ErrorCode code, ASTNode node, int length, String message) {
        if (strict) {
            error(code, node, length, message);
        } else {
            warn(code, node, length, message);
        }
    }
}
