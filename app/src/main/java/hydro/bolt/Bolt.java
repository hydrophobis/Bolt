package hydro.bolt;

import hydro.bolt.parser.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import hydro.bolt.ast.*;
import hydro.bolt.ast.bolt.PackageDeclaration;
import hydro.bolt.sema.ModuleResolver;
import hydro.bolt.sema.SemanticAnalyzer;
import hydro.bolt.tokens.*;

import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Bolt {

    public static void main(String[] args) throws IOException {
        if (args.length == 0 || args[0].equals("-h") || args[0].equals("--help")) {
            printUsage();
            return;
        }

        Config config = new Config();
        String inputFile = null;
        String outputFile = null;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("-o") && i + 1 < args.length) {
                outputFile = args[++i];
            } else if (arg.startsWith("--")) {
                String kv = arg.substring(2);
                if (kv.contains("=")) {
                    String[] parts = kv.split("=", 2);
                    config.put(parts[0], parts[1]);
                } else {
                    config.put(kv, "true");
                }
            } else if (inputFile == null) {
                inputFile = arg;
            } else if (outputFile == null) {
                outputFile = arg;
            }
        }

        ErrorReporter configDiagnostics = config.diagnostics();
        configDiagnostics.printErrors();
        if (configDiagnostics.hasErrors()) {
            System.exit(1);
        }

        if (inputFile == null) {
            System.err.println("error: no input file specified");
            printUsage();
            System.exit(1);
        }

        if (!Files.exists(Paths.get(inputFile))) {
            System.err.println("error: input file not found: " + inputFile);
            System.exit(1);
        }

        if (config.getBoolean("verbose")) {
            System.out.println("Bolt Compiler - Configuration:");
            for (String key : Config.knownKeys().stream().sorted().toList()) {
                System.out.println("  " + key + ": " + config.get(key));
            }
        }

        String code = Files.readString(Paths.get(inputFile));

        ErrorReporter reporter = new ErrorReporter(inputFile, code);
        reporter.setMaxErrors(config.getInt("max-errors"));

        Tokenizer tokenizer = new Tokenizer(code, reporter);
        List<Token> tokens = tokenizer.tokenize();

        Parser parser = new Parser(tokens, config);
        parser.reporter = reporter;
        ASTTree ast = parser.parse();

        if (reporter.hasErrors()) {
            reporter.printErrors();
            System.exit(1);
        }

        ModuleResolver resolver = new ModuleResolver(Paths.get(inputFile), config);
        SemanticAnalyzer analyzer = new SemanticAnalyzer(ast, config, reporter, parser.imports);
        analyzer.setModules(resolver.resolveAll(parser.imports));
        for (Map.Entry<String, String> failure : resolver.failures().entrySet()) {
            reporter.warn(ErrorCode.MODULE_NOT_ANALYZED,
                "import '" + failure.getKey() + "' could not be analyzed: " + failure.getValue());
        }
        analyzer.analyze();

        if (reporter.hasErrors()) {
            reporter.printErrors();
            System.exit(1);
        }

        String packageName = null;
        for (ASTNode node : ast) {
            if (node instanceof PackageDeclaration pkg) {
                packageName = pkg.name;
                break;
            }
        }

        String packageDir = "";
        if (packageName != null && !packageName.isEmpty()) {
            packageDir = packageName.replace('.', '/');
        }

        if (outputFile == null) {
            String baseName = Paths.get(inputFile).getFileName().toString();
            if (baseName.endsWith(".bolt")) {
                baseName = baseName.substring(0, baseName.length() - 5);
            }
            if (!packageDir.isEmpty()) {
                outputFile = packageDir + "/" + baseName + ".c";
            } else {
                outputFile = baseName + ".c";
            }
        }

        java.nio.file.Path outputPath = Paths.get(outputFile);
        if (outputPath.getParent() != null) {
            Files.createDirectories(outputPath.getParent());
        }

        Codegen generator = new Codegen(ast, config);
        generator.symbols = parser.symbols;
        generator.imports = parser.imports;
        generator.reporter = reporter;

        String generatedCode;
        try {
            generatedCode = generator.generate();
        } catch (Exception e) {
            reportInternalError(reporter, config, "code generation", e);
            System.exit(1);
            return;
        }

        if (reporter.hasErrors()) {
            reporter.printErrors();
            System.exit(1);
        }

        String headerCode;
        try {
            headerCode = generator.generateHeader();
        } catch (Exception e) {
            reportInternalError(reporter, config, "header generation", e);
            System.exit(1);
            return;
        }

        if (reporter.hasErrors()) {
            reporter.printErrors();
            System.exit(1);
        }

        reporter.printErrors();

        try (FileWriter writer = new FileWriter(outputFile)) {
            writer.write(generatedCode);
        }
        System.out.println("Transpilation successful. Output written to " + outputFile);

        String headerFile = outputFile.endsWith(".c")
            ? outputFile.substring(0, outputFile.length() - 2) + ".h"
            : outputFile + ".h";

        java.nio.file.Path headerPath = Paths.get(headerFile);
        if (headerPath.getParent() != null) {
            Files.createDirectories(headerPath.getParent());
        }

        try (FileWriter headerWriter = new FileWriter(headerFile)) {
            headerWriter.write(headerCode);
        }
        System.out.println("Header generation successful. Output written to " + headerFile);
    }

    private static void reportInternalError(ErrorReporter reporter, Config config,
                                            String phase, Exception e) {
        reporter.printErrors();
        System.err.println("error[" + ErrorCode.INTERNAL_ERROR.id() + "]: internal compiler error during "
            + phase + ": " + e);
        System.err.println("note: this is a bug in Bolt. Please report it with the source that triggered it.");
        if (config.getBoolean("verbose")) {
            e.printStackTrace();
        } else {
            System.err.println("note: re-run with --verbose for a stack trace.");
        }
    }

    private static void printUsage() {
        System.out.println("Bolt Compiler");
        System.out.println("Usage: bolt <input.bolt> [output.c] [options]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -h, --help           Show this help message");
        System.out.println("  -o <file>            Write generated C to <file>");
        System.out.println("  --<key>=<value>      Override a bolt.cfg setting");
        System.out.println("  --<flag>             Set a boolean bolt.cfg setting to true");
        System.out.println();
        System.out.println("Settings:");
        for (String key : Config.knownKeys().stream().sorted().toList()) {
            System.out.println("  " + key);
        }
    }
}
