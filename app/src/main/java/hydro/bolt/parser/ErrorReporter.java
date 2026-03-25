package hydro.bolt.parser;

import hydro.bolt.tokens.Token;
import java.util.ArrayList;
import java.util.List;

public class ErrorReporter {
    private final List<String> errors = new ArrayList<>();

    public void report(Token token, String message) {
        String location = (token != null) ?
            "at line " + token.line + ", col " + token.column + " ('" + token.lexeme + "')" :
            "at end of file";
        errors.add("Error " + location + ": " + message);
    }

    public void report(String message) {
        errors.add("Error: " + message);
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public List<String> getErrors() {
        return errors;
    }

    public void printErrors() {
        for (String error : errors) {
            System.err.println(error);
        }
    }
}
