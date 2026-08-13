package hydro.bolt.sema;

import hydro.bolt.ast.Visibility;
import hydro.bolt.ast.bolt.ClassDeclaration;
import hydro.bolt.ast.decl.FunctionDeclaration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ClassInfo {
    public final String name;
    public final ClassDeclaration declaration;
    public final Map<String, String> fieldTypes = new LinkedHashMap<>();
    public final Map<String, Visibility> fieldVisibility = new LinkedHashMap<>();
    public final Map<String, FunctionDeclaration> methods = new LinkedHashMap<>();
    public final Map<String, Visibility> methodVisibility = new LinkedHashMap<>();
    public final List<String> genericParams = new ArrayList<>();
    public final List<String> interfaces = new ArrayList<>();

    public ClassInfo(String name, ClassDeclaration declaration) {
        this.name = name;
        this.declaration = declaration;
    }

    public boolean isGeneric() {
        return !genericParams.isEmpty();
    }

    public boolean hasMember(String member) {
        return fieldTypes.containsKey(member) || methods.containsKey(member);
    }

    public Visibility visibilityOf(String member) {
        if (fieldVisibility.containsKey(member)) return fieldVisibility.get(member);
        return methodVisibility.get(member);
    }
}
