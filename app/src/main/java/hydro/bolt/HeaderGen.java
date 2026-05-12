package hydro.bolt;

import hydro.bolt.ast.*;
import hydro.bolt.ast.bolt.ImportDeclaration;
import hydro.bolt.ast.bolt.PackageDeclaration;
import hydro.bolt.parser.*;
import hydro.bolt.tokens.*;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class HeaderGen {

    public static void main(String[] args) throws IOException {
        if (args.length == 0 || args[0].equals("-h") || args[0].equals("--help")) {
            System.out.println("Bolt Header Generator");
            System.out.println("Usage: headergen <input.bolt> [output.h] [--<key>=<value>] [--<flag>]");
            System.out.println("```");
            System.out.println("bolt-headergen mylib/helpers.bolt mylib/helpers.h");
            System.out.println("```");
            System.out.println("Options are the same as bolt: e.g. --mangle=false");
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

        // determine package directory
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
                outputFile = packageDir + "/" + baseName + ".h";
            } else {
                outputFile = baseName + ".h";
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

        String generatedHeader;
        try {
            generatedHeader = generator.generateHeader();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
            return;
        }

        if (reporter.hasErrors()) {
            reporter.printErrors();
            System.exit(1);
        }

        try (FileWriter writer = new FileWriter(outputFile)) {
            writer.write(generatedHeader);
        }

        System.out.println("Header generation successful. Output written to " + outputFile);
    }
}
