package hydro.bolt.parser;

public class Diagnostic {
    public enum Severity {
        ERROR("error"),
        WARNING("warning"),
        NOTE("note");
        private final String label;

        Severity(String label) {
            this.label = label;
        }
        public String label() {
            return label;
        }
    }

    public final Severity severity;
    public final ErrorCode code;
    public final String message;
    public final String file;
    public final int line;
    public final int column;
    public final int length;
    public Diagnostic(Severity severity, ErrorCode code, String message,
                      String file, int line, int column, int length) {
        this.severity = severity;
        this.code = code;
        this.message = message;
        this.file = file;
        this.line = line;
        this.column = column;
        this.length = Math.max(1, length);
    }

    public boolean hasLocation() {
        return line > 0;
    }

    public String header() {
        StringBuilder sb = new StringBuilder();
        if (file != null && !file.isEmpty()) {
            sb.append(file);
            if (hasLocation()) {
                sb.append(':').append(line).append(':').append(column);
            }
            sb.append(": ");
        }
        sb.append(severity.label());
        if (code != null) {
            sb.append('[').append(code.id()).append(']');
        }
        sb.append(": ").append(message);
        return sb.toString();
    }

    @Override
    public String toString() {
        return header();
    }
}
