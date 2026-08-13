package hydro.bolt.format;

public final class LineWrapper {
    private LineWrapper() {
    }

    public static String wrap(String source, int maxWidth, String indentUnit) {
        if (source == null || maxWidth <= 0) return source;
        StringBuilder out = new StringBuilder(source.length() + 256);
        String[] lines = source.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            out.append(wrapLine(lines[i], maxWidth, indentUnit));
            if (i < lines.length - 1) out.append('\n');
        }
        return out.toString();
    }

    private static String wrapLine(String line, int maxWidth, String indentUnit) {
        if (line.length() <= maxWidth) return line;

        String trimmed = line.stripLeading();
        if (trimmed.startsWith("#") || trimmed.startsWith("//") || trimmed.startsWith("/*")) {
            return line;
        }
        int lastBreak = findBreakPoint(line, maxWidth);
        if (lastBreak < 0) return line;

        String leadingWhitespace = line.substring(0, line.length() - trimmed.length());
        String continuation = leadingWhitespace + indentUnit;

        StringBuilder out = new StringBuilder();
        int start = 0;
        int breakAt = lastBreak;
        while (breakAt >= 0) {
            out.append(line, start, breakAt + 1).append('\n').append(continuation);
            start = skipSpaces(line, breakAt + 1);
            int remainingBudget = maxWidth - continuation.length();
            if (line.length() - start <= remainingBudget) break;
            breakAt = findBreakPoint(line.substring(start), remainingBudget);
            if (breakAt < 0) break;
            breakAt += start;
        }
        out.append(line, start, line.length());
        return out.toString();
    }

    private static int skipSpaces(String line, int from) {
        int i = from;
        while (i < line.length() && line.charAt(i) == ' ') i++;
        return i;
    }

    private static int findBreakPoint(String line, int maxWidth) {
        int depth = 0;
        int best = -1;
        int limit = Math.min(line.length(), maxWidth);

        for (int i = 0; i < limit; i++) {
            char c = line.charAt(i);
            if (c == '"' || c == '\'') {
                char quote = c;
                i++;
                while (i < line.length() && line.charAt(i) != quote) {
                    if (line.charAt(i) == '\\') i++;
                    i++;
                }
                continue;
            }
            if (c == '(' || c == '[' || c == '{') depth++;
            else if (c == ')' || c == ']' || c == '}') depth--;
            else if (c == ',' && depth > 0) best = i;
        }

        return best;
    }
}
