package hydro.bolt.parser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ErrorReporterTest {

    private static final String SOURCE = """
        int main() {
            int x = 1;
            return x;
        }
        """;

    @Test
    void headerCarriesFileLineColumnSeverityAndCode() {
        ErrorReporter reporter = new ErrorReporter("demo.bolt", SOURCE);
        reporter.error(ErrorCode.TYPE_MISMATCH, 2, 9, 1, "cannot use 'string' as 'int'");

        String rendered = reporter.render();
        assertTrue(rendered.contains("demo.bolt:2:9: error[E2003]: cannot use 'string' as 'int'"),
            rendered);
    }

    @Test
    @DisplayName("snippet quotes the source line and underlines the span")
    void snippetIncludesCaret() {
        ErrorReporter reporter = new ErrorReporter("demo.bolt", SOURCE);
        reporter.error(ErrorCode.UNDEFINED_SYMBOL, 2, 9, 1, "boom");

        String rendered = reporter.render();
        assertTrue(rendered.contains("2 |     int x = 1;"), rendered);
        assertTrue(rendered.contains("^"), rendered);
    }

    @Test
    void caretWidthMatchesTheReportedLength() {
        ErrorReporter reporter = new ErrorReporter("demo.bolt", "let value = 1;\n");
        reporter.error(ErrorCode.UNDEFINED_SYMBOL, 1, 5, 5, "boom");
        assertTrue(reporter.render().contains("^^^^^"), reporter.render());
    }

    @Test
    void warningsAndErrorsAreCountedSeparately() {
        ErrorReporter reporter = new ErrorReporter("demo.bolt", SOURCE);
        reporter.error(ErrorCode.TYPE_MISMATCH, 1, 1, 1, "an error");
        reporter.warn(ErrorCode.UNUSED_VARIABLE, 2, 1, 1, "a warning");

        assertTrue(reporter.hasErrors());
        assertTrue(reporter.hasWarnings());
        assertEquals(1, reporter.errorCount());
        assertEquals(1, reporter.warningCount());
        assertTrue(reporter.summary().contains("1 error, 1 warning generated."), reporter.summary());
    }

    @Test
    @DisplayName("warnings alone do not fail the compile")
    void warningsAreNotErrors() {
        ErrorReporter reporter = new ErrorReporter("demo.bolt", SOURCE);
        reporter.warn(ErrorCode.UNUSED_VARIABLE, 2, 9, 1, "unused");
        assertFalse(reporter.hasErrors());
    }

    @Test
    void maxErrorsCapsOutputAndSaysSo() {
        ErrorReporter reporter = new ErrorReporter("demo.bolt", SOURCE);
        reporter.setMaxErrors(2);
        for (int i = 0; i < 10; i++) {
            reporter.error(ErrorCode.TYPE_MISMATCH, 1, 1, 1, "error " + i);
        }

        assertEquals(2, reporter.errorCount());
        assertTrue(reporter.wasTruncated());
        assertTrue(reporter.render().contains("too many errors"), reporter.render());
    }

    @Test
    void maxErrorsOfZeroMeansUnlimited() {
        ErrorReporter reporter = new ErrorReporter("demo.bolt", SOURCE);
        reporter.setMaxErrors(0);
        for (int i = 0; i < 50; i++) {
            reporter.error(ErrorCode.TYPE_MISMATCH, 1, 1, 1, "error " + i);
        }
        assertEquals(50, reporter.errorCount());
        assertFalse(reporter.wasTruncated());
    }

    @Test
    @DisplayName("warnings are never capped by max-errors")
    void warningsAreNotCapped() {
        ErrorReporter reporter = new ErrorReporter("demo.bolt", SOURCE);
        reporter.setMaxErrors(1);
        reporter.error(ErrorCode.TYPE_MISMATCH, 1, 1, 1, "an error");
        reporter.warn(ErrorCode.UNUSED_VARIABLE, 2, 1, 1, "a warning");
        assertEquals(1, reporter.warningCount());
    }

    @Test
    @DisplayName("output is ordered by position, not by when it was raised")
    void diagnosticsAreSorted() {
        ErrorReporter reporter = new ErrorReporter("demo.bolt", SOURCE);
        reporter.error(ErrorCode.TYPE_MISMATCH, 3, 1, 1, "later");
        reporter.error(ErrorCode.TYPE_MISMATCH, 1, 1, 1, "earlier");

        String rendered = reporter.render();
        assertTrue(rendered.indexOf("earlier") < rendered.indexOf("later"), rendered);
    }

    @Test
    void worksWithoutASourceAttached() {
        ErrorReporter reporter = new ErrorReporter();
        reporter.error(ErrorCode.INTERNAL_ERROR, "something went wrong");
        assertTrue(reporter.render().contains("something went wrong"));
    }

    @Test
    @DisplayName("CRLF sources do not skew the caret")
    void handlesCarriageReturns() {
        ErrorReporter reporter = new ErrorReporter("demo.bolt", "int x = 1;\r\nint y = 2;\r\n");
        reporter.error(ErrorCode.UNDEFINED_SYMBOL, 2, 5, 1, "boom");
        assertFalse(reporter.render().contains("\r"), "stray CR would misalign the snippet");
    }
}
