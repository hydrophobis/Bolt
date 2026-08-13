package hydro.bolt;

import hydro.bolt.ast.ASTTree;
import hydro.bolt.parser.Diagnostic;
import hydro.bolt.parser.ErrorCode;
import hydro.bolt.parser.ErrorReporter;
import hydro.bolt.parser.Parser;
import hydro.bolt.sema.SemanticAnalyzer;
import hydro.bolt.tokens.Token;
import hydro.bolt.tokens.Tokenizer;

import java.io.File;
import java.util.List;
import java.util.function.Consumer;

public final class TestCompiler {
    private TestCompiler() {
    }

    public static final String NO_CONFIG_FILE = "bolt.cfg.does-not-exist";
    public static Config defaultConfig() {
        return new Config(new File(NO_CONFIG_FILE));
    }

    public static Config config(Consumer<Config> customize) {
        Config config = defaultConfig();
        customize.accept(config);
        return config;
    }

    public static List<Token> tokenize(String source) {
        return new Tokenizer(source, new ErrorReporter()).tokenize();
    }

    public record Result(ASTTree ast, ErrorReporter reporter) {
        public boolean hasErrors() {
            return reporter.hasErrors();
        }
        public boolean has(ErrorCode code) {
            return reporter.getDiagnostics().stream().anyMatch(d -> d.code == code);
        }
        public Diagnostic first(ErrorCode code) {
            return reporter.getDiagnostics().stream()
                .filter(d -> d.code == code)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                    "no " + code + " diagnostic; got:\n" + reporter.render()));
        }
        public String rendered() {
            return reporter.render();
        }
    }

    public static Result parse(String source) {
        return parse(source, defaultConfig());
    }

    public static Result parse(String source, Config config) {
        ErrorReporter reporter = new ErrorReporter("test.bolt", source);
        reporter.setMaxErrors(config.getInt("max-errors"));
        List<Token> tokens = new Tokenizer(source, reporter).tokenize();
        Parser parser = new Parser(tokens, config);
        parser.reporter = reporter;
        ASTTree ast = parser.parse();
        return new Result(ast, reporter);
    }

    public static Result analyze(String source) {
        return analyze(source, defaultConfig());
    }

    public static Result analyze(String source, Config config) {
        ErrorReporter reporter = new ErrorReporter("test.bolt", source);
        reporter.setMaxErrors(config.getInt("max-errors"));
        List<Token> tokens = new Tokenizer(source, reporter).tokenize();
        Parser parser = new Parser(tokens, config);
        parser.reporter = reporter;
        ASTTree ast = parser.parse();
        if (!reporter.hasErrors()) {
            new SemanticAnalyzer(ast, config, reporter, parser.imports).analyze();
        }
        return new Result(ast, reporter);
    }
}
