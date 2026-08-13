package hydro.bolt.parser;

import hydro.bolt.tokens.Token;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ErrorReporter {
    private final List<Diagnostic> diagnostics = new ArrayList<>();
    private String fileName = "";
    private String[] sourceLines = null;
    private int maxErrors = 0;
    private int errorCount = 0;
    private int warningCount = 0;
    private boolean truncated = false;
    public ErrorReporter() {
    }

    public ErrorReporter(String fileName, String source) {
        setSource(fileName, source);
    }

    public void setSource(String fileName, String source) {
        this.fileName = fileName != null ? fileName : "";
        this.sourceLines = source != null ? source.split("\n", -1) : null;
    }

    public String getFileName() {
        return fileName;
    }

    public void setMaxErrors(int maxErrors) {
        this.maxErrors = Math.max(0, maxErrors);
    }

    public void error(ErrorCode code, int line, int column, int length, String message) {
        add(Diagnostic.Severity.ERROR, code, line, column, length, message);
    }

    public void error(ErrorCode code, Token token, String message) {
        if (token == null) {
            add(Diagnostic.Severity.ERROR, code, 0, 0, 1, message);
        } else {
            add(Diagnostic.Severity.ERROR, code, token.line, token.column,
                token.lexeme != null ? token.lexeme.length() : 1, message);
        }
    }

    public void error(ErrorCode code, String message) {
        add(Diagnostic.Severity.ERROR, code, 0, 0, 1, message);
    }

    public void warn(ErrorCode code, int line, int column, int length, String message) {
        add(Diagnostic.Severity.WARNING, code, line, column, length, message);
    }

    public void warn(ErrorCode code, Token token, String message) {
        if (token == null) {
            add(Diagnostic.Severity.WARNING, code, 0, 0, 1, message);
        } else {
            add(Diagnostic.Severity.WARNING, code, token.line, token.column,
                token.lexeme != null ? token.lexeme.length() : 1, message);
        }
    }

    public void warn(ErrorCode code, String message) {
        add(Diagnostic.Severity.WARNING, code, 0, 0, 1, message);
    }

    private void add(Diagnostic.Severity severity, ErrorCode code,
                     int line, int column, int length, String message) {
        if (severity == Diagnostic.Severity.ERROR) {
            if (maxErrors > 0 && errorCount >= maxErrors) {
                truncated = true;
                return;
            }
            errorCount++;
        } else if (severity == Diagnostic.Severity.WARNING) {
            warningCount++;
        }
        diagnostics.add(new Diagnostic(severity, code, message, fileName, line, column, length));
    }

    public void report(Token token, String message) {
        error(ErrorCode.UNEXPECTED_TOKEN, token, message);
    }

    public void report(String message) {
        error(ErrorCode.INTERNAL_ERROR, message);
    }

    public boolean hasErrors() {
        return errorCount > 0;
    }

    public boolean hasWarnings() {
        return warningCount > 0;
    }

    public int errorCount() {
        return errorCount;
    }

    public int warningCount() {
        return warningCount;
    }

    public boolean wasTruncated() {
        return truncated;
    }

    public List<Diagnostic> getDiagnostics() {
        return Collections.unmodifiableList(diagnostics);
    }

    public List<String> getErrors() {
        List<String> out = new ArrayList<>();
        for (Diagnostic d : sorted()) {
            out.add(d.header());
        }
        return out;
    }

    public void printErrors() {
        System.err.print(render());
    }

    private List<Diagnostic> sorted() {
        List<Diagnostic> out = new ArrayList<>(diagnostics);
        out.sort(Comparator.comparingInt((Diagnostic d) -> d.line)
                           .thenComparingInt(d -> d.column));
        return out;
    }

    public String render() {
        StringBuilder sb = new StringBuilder();
        for (Diagnostic d : sorted()) {
            sb.append(d.header()).append('\n');
            appendSnippet(sb, d);
        }
        if (truncated) {
            sb.append("note: too many errors emitted, stopping now ")
              .append("(raise the 'max-errors' setting to see more)\n");
        }
        if (errorCount > 0 || warningCount > 0) {
            sb.append(summary()).append('\n');
        }
        return sb.toString();
    }

    public String summary() {
        StringBuilder sb = new StringBuilder();
        if (errorCount > 0) {
            sb.append(errorCount).append(errorCount == 1 ? " error" : " errors");
        }
        if (warningCount > 0) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(warningCount).append(warningCount == 1 ? " warning" : " warnings");
        }
        sb.append(" generated.");
        return sb.toString();
    }

    private void appendSnippet(StringBuilder sb, Diagnostic d) {
        if (sourceLines == null || !d.hasLocation()) return;
        if (d.line > sourceLines.length) return;
        String text = sourceLines[d.line - 1];
        if (text.endsWith("\r")) {
            text = text.substring(0, text.length() - 1);
        }
        String lineNo = String.valueOf(d.line);
        String pad = " ".repeat(lineNo.length());

        sb.append(pad).append(" |\n");
        sb.append(lineNo).append(" | ").append(text).append('\n');
        sb.append(pad).append(" | ").append(caret(text, d)).append('\n');
    }

    private String caret(String text, Diagnostic d) {
        int col = Math.max(1, d.column);
        StringBuilder marker = new StringBuilder();
        for (int i = 0; i < col - 1; i++) {
            marker.append(i < text.length() && text.charAt(i) == '\t' ? '\t' : ' ');
        }
        int span = d.length;
        if (col - 1 + span > text.length()) {
            span = Math.max(1, text.length() - (col - 1));
        }
        marker.append("^".repeat(span));
        return marker.toString();
    }
}
