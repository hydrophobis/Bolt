package hydro.bolt.sema;

import hydro.bolt.Config;
import hydro.bolt.ast.ASTTree;
import hydro.bolt.parser.ErrorReporter;
import hydro.bolt.parser.Parser;
import hydro.bolt.tokens.Token;
import hydro.bolt.tokens.Tokenizer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ModuleResolver {

    public static final int MAX_DEPTH = 32;

    private final Config config;
    private final List<Path> searchPaths = new ArrayList<>();
    private final Map<String, ASTTree> resolved = new LinkedHashMap<>();
    private final Map<String, String> failures = new HashMap<>();
    private final Set<String> inProgress = new HashSet<>();

    public ModuleResolver(Path sourceFile, Config config) {
        this.config = config;

        Path dir = sourceFile != null ? sourceFile.toAbsolutePath().getParent() : null;
        if (dir != null) searchPaths.add(dir);

        Path cwd = Path.of("").toAbsolutePath();
        if (!searchPaths.contains(cwd)) searchPaths.add(cwd);
    }

    public Map<String, ASTTree> resolveAll(List<String> imports) {
        for (String path : imports) {
            resolve(path, 0);
        }
        return resolved;
    }

    public Map<String, String> failures() {
        return failures;
    }

    private void resolve(String importPath, int depth) {
        if (depth > MAX_DEPTH) return;
        if (resolved.containsKey(importPath) || failures.containsKey(importPath)) return;
        if (CStdlib.isKnownHeader(importPath)) return;
        if (!inProgress.add(importPath)) return;

        try {
            Path file = locate(importPath);
            if (file == null) return;

            String source;
            try {
                source = Files.readString(file);
            } catch (IOException e) {
                failures.put(importPath, "could not read " + file + ": " + e.getMessage());
                return;
            }

            ErrorReporter moduleReporter = new ErrorReporter(file.toString(), source);
            List<Token> tokens = new Tokenizer(source, moduleReporter).tokenize();
            Parser parser = new Parser(tokens, config);
            parser.reporter = moduleReporter;
            ASTTree tree = parser.parse();

            if (moduleReporter.hasErrors()) {
                failures.put(importPath, file + " does not parse cleanly");
                return;
            }

            resolved.put(importPath, tree);

            for (String nested : parser.imports) {
                resolve(nested, depth + 1);
            }
        } finally {
            inProgress.remove(importPath);
        }
    }

    private Path locate(String importPath) {
        String relative = importPath.replace('.', '/') + ".bolt";
        for (Path root : searchPaths) {
            Path candidate = root.resolve(relative);
            if (Files.isRegularFile(candidate)) return candidate;
        }
        return null;
    }
}
