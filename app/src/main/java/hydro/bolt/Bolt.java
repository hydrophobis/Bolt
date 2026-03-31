package hydro.bolt;

import hydro.bolt.parser.*;

import java.io.IOException;
import java.util.List;

import hydro.bolt.ast.*;
import hydro.bolt.ast.bolt.PackageDeclaration;
import hydro.bolt.tokens.*;

import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Bolt {

    public static void main(String[] args) throws IOException {
        if (args.length == 0 || args[0].equals("-h") || args[0].equals("--help")) {
            System.out.println("Bolt Compiler");
            System.out.println("Usage: bolt <input.bolt> [output.c] [options]");
            System.out.println("\nOptions:");
            System.out.println("  -h, --help            Show this help message");
            System.out.println("  --<key>=<value>      Override configuration setting");
            System.out.println("  --<flag>             Set boolean configuration setting to true");
            return;
        }

        Config config = new Config();
        String inputFile = null;
        String outputFile = null;

        for (String arg : args) {
            if (arg.startsWith("--")) {
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

        if (inputFile == null) {
            System.err.println("Error: No input file specified");
            System.exit(1);
        }

        if (!Files.exists(Paths.get(inputFile))) {
            System.err.println("Error: Input file not found: " + inputFile);
            System.exit(1);
        }

        if (config.getBoolean("verbose")) {
            System.out.println("Bolt Compiler - Configuration:");
            for (String key : new String[]{"mangle", "mangle-prefix", "indent-size", "brace-style", "allow-recursion"}) {
                System.out.println("  " + key + ": " + config.get(key));
            }
        }

        String code = Files.readString(Paths.get(inputFile));
        ErrorReporter reporter = new ErrorReporter();
        Tokenizer tokenizer = new Tokenizer(code, reporter);
        List<Token> tokens = tokenizer.tokenize();
        
        Parser parser = new Parser(tokens, config);
        parser.reporter = reporter;
        ASTTree ast = parser.parse();

        if (reporter.hasErrors()) {
            reporter.printErrors();
            System.exit(1);
        }

        // package path for output placement
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
        } else if (!packageDir.isEmpty()) {
            java.nio.file.Path explicitPath = Paths.get(outputFile);
            String explicitPathNormalized = explicitPath.toString().replace('\\', '/');
            if (explicitPath.getParent() == null || !explicitPathNormalized.contains(packageDir + "/")) {
                outputFile = packageDir + "/" + explicitPath.getFileName().toString();
            }
        }

        // Ensure output directory exists
        java.nio.file.Path outputPath = Paths.get(outputFile);
        if (outputPath.getParent() != null) {
            Files.createDirectories(outputPath.getParent());
        }

        Codegen generator = new Codegen(ast, config);
        generator.symbols = parser.symbols;
        generator.imports = parser.imports;
        generator.reporter = reporter;
        String generatedCode = "";
        try {
            generatedCode = generator.generate();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }

        if (reporter.hasErrors()) {
            reporter.printErrors();
            System.exit(1);
        }

        if (outputFile != null) {
            try (FileWriter writer = new FileWriter(outputFile)) {
                writer.write(generatedCode);
            }
            System.out.println("Transpilation successful. Output written to " + outputFile);

            String headerFile;
            if (outputFile.endsWith(".c")) {
                headerFile = outputFile.substring(0, outputFile.length() - 2) + ".h";
            } else {
                headerFile = outputFile + ".h";
            }

            java.nio.file.Path headerPath = Paths.get(headerFile);
            if (headerPath.getParent() != null) {
                Files.createDirectories(headerPath.getParent());
            }

            try {
                String headerCode = generator.generateHeader();
                try (FileWriter headerWriter = new FileWriter(headerFile)) {
                    headerWriter.write(headerCode);
                }
                System.out.println("Header generation successful. Output written to " + headerFile);
            } catch (Exception e) {
                System.err.println("Warning: failed to generate header: " + e.getMessage());
                e.printStackTrace();
                System.exit(1);
            }
        } else {
            System.out.println(generatedCode);
        }
    }
}
