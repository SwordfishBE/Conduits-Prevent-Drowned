package net.conduits_prevent_drowned;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Invalid files are preserved; a failed reload keeps the last working value. */
public final class ConfigStore {
    private static final String DEFAULT_JSON = "{\n  \"preventDrownedInRange\": 400\n}\n";
    private final Path path;
    private volatile int range = 400;

    public ConfigStore(Path path) { this.path = path; }
    public int range() { return range; }

    public void reload() throws IOException {
        if (Files.notExists(path)) {
            Files.createDirectories(path.getParent());
            Files.writeString(path, DEFAULT_JSON, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        }
        range = parse(Files.readString(path, StandardCharsets.UTF_8));
    }

    static int parse(String json) {
        var root = JsonParser.parseString(json);
        if (!root.isJsonObject()) throw new IllegalArgumentException("Config must be a JSON object.");
        var value = root.getAsJsonObject().get("preventDrownedInRange");
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("preventDrownedInRange must be an integer from 0 to 400.");
        }
        int result;
        try { result = value.getAsBigDecimal().intValueExact(); }
        catch (ArithmeticException e) { throw new IllegalArgumentException("Range must be an integer from 0 to 400.", e); }
        if (result < 0 || result > 400) throw new IllegalArgumentException("Range must be between 0 and 400.");
        return result;
    }
}
