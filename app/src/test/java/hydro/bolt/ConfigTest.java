package hydro.bolt;

import hydro.bolt.parser.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ConfigTest {

    private Config configFrom(Path dir, String contents) throws IOException {
        File file = dir.resolve("bolt.cfg").toFile();
        Files.writeString(file.toPath(), contents);
        return new Config(file);
    }

    private boolean reported(Config config, ErrorCode code) {
        return config.diagnostics().getDiagnostics().stream().anyMatch(d -> d.code == code);
    }

    @Test
    void usesDeclaredDefaultsWhenNoFileExists() {
        Config config = new Config(new File("definitely-not-here.cfg"));
        assertEquals("true", config.get("mangle"));
        assertEquals("c99", config.get("c-standard"));
        assertEquals(80, config.getInt("line-width"));
        assertFalse(config.getBoolean("no-heap"));
        assertFalse(config.diagnostics().hasErrors());
    }

    @Test
    void readsValidSettings(@TempDir Path dir) throws IOException {
        Config config = configFrom(dir, """
            # a comment
            mangle=false
            indent-size=2
            c-standard=c11
            """);
        assertEquals("false", config.get("mangle"));
        assertEquals(2, config.getInt("indent-size"));
        assertEquals("c11", config.get("c-standard"));
        assertFalse(config.diagnostics().hasErrors());
    }

    @Test
    @DisplayName("unknown keys warn instead of being silently swallowed")
    void unknownKeyWarns(@TempDir Path dir) throws IOException {
        Config config = configFrom(dir, "not-a-real-setting=1\n");
        assertTrue(reported(config, ErrorCode.UNKNOWN_CONFIG_KEY));
    }

    @Test
    @DisplayName("a near-miss key suggests the intended one")
    void unknownKeySuggestsAlternative(@TempDir Path dir) throws IOException {
        Config config = configFrom(dir, "strict_typing=true\n");
        var diagnostic = config.diagnostics().getDiagnostics().stream()
            .filter(d -> d.code == ErrorCode.UNKNOWN_CONFIG_KEY)
            .findFirst()
            .orElseThrow();
        assertTrue(diagnostic.message.contains("did you mean 'strict-typing'"), diagnostic.message);
    }

    @Test
    void nonBooleanValueForABooleanSettingIsAnError(@TempDir Path dir) throws IOException {
        Config config = configFrom(dir, "mangle=yes\n");
        assertTrue(reported(config, ErrorCode.INVALID_CONFIG_VALUE));
        assertEquals("true", config.get("mangle"), "the default should survive a bad value");
    }

    @Test
    void nonIntegerValueForAnIntegerSettingIsAnError(@TempDir Path dir) throws IOException {
        Config config = configFrom(dir, "indent-size=wide\n");
        assertTrue(reported(config, ErrorCode.INVALID_CONFIG_VALUE));
        assertEquals(4, config.getInt("indent-size"));
    }

    @Test
    void unsupportedCStandardIsAnError(@TempDir Path dir) throws IOException {
        Config config = configFrom(dir, "c-standard=c95\n");
        assertTrue(reported(config, ErrorCode.UNSUPPORTED_C_STANDARD));
    }

    @Test
    void malformedLineWarns(@TempDir Path dir) throws IOException {
        Config config = configFrom(dir, "this line has no equals sign\n");
        assertTrue(reported(config, ErrorCode.MALFORMED_CONFIG_LINE));
    }

    @Test
    void commentsAndBlankLinesAreIgnored(@TempDir Path dir) throws IOException {
        Config config = configFrom(dir, "\n# just a comment\n\n");
        assertFalse(config.diagnostics().hasErrors());
        assertFalse(config.diagnostics().hasWarnings());
    }

    @Test
    void commandLineOverridesAreValidatedToo() {
        Config config = new Config(new File("definitely-not-here.cfg"));
        config.put("made-up-key", "1");
        assertTrue(reported(config, ErrorCode.UNKNOWN_CONFIG_KEY));

        config.put("max-errors", "not-a-number");
        assertTrue(reported(config, ErrorCode.INVALID_CONFIG_VALUE));
        assertEquals(10, config.getInt("max-errors"));
    }

    @Test
    void everyDefaultIsAValidValueForItsOwnSetting() {
        Config config = new Config(new File("definitely-not-here.cfg"));
        for (String key : Config.knownKeys()) {
            assertNotNull(config.get(key), key + " has no default");
        }
        assertFalse(config.diagnostics().hasErrors());
    }
}
