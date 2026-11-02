package net.conduits_prevent_drowned;

import com.google.gson.JsonParser;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;

/** Invalid files are preserved; a failed reload keeps the last working value. */
public final class ConfigStore {
    private static final String DEFAULT_JSON = "{\n  \"preventDrownedInRange\": 400\n}\n";
    private final Path path;
    private volatile int range = 400;

    public ConfigStore(Path path) { this.path = path; }
    public int range() { return range; }

    public synchronized void reload() throws IOException {
        if (Files.notExists(path)) {
            Files.createDirectories(path.getParent());
            Files.writeString(path, DEFAULT_JSON, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        }
        range = parse(Files.readString(path, StandardCharsets.UTF_8));
    }

    /** Reads a detached GUI draft without changing the running server's setting. */
    public synchronized int readForEditing() throws IOException {
        return Files.notExists(path) ? range : parse(Files.readString(path, StandardCharsets.UTF_8));
    }

    /** Explicit user save: preserve unknown options and publish only after disk commit. */
    public synchronized void saveRange(int value) throws IOException {
        if (value < 0 || value > 400) throw new IllegalArgumentException("Range must be between 0 and 400.");
        Path target = path.toAbsolutePath();
        var root = new com.google.gson.JsonObject();
        if (Files.exists(target)) {
            String existing = Files.readString(target, StandardCharsets.UTF_8);
            parse(existing); // Never replace an invalid existing file, including externally changed files.
            root = JsonParser.parseString(existing).getAsJsonObject();
        }
        root.addProperty("preventDrownedInRange", value);
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
        try {
            Files.writeString(temporary, new GsonBuilder().setPrettyPrinting().create().toJson(root) + "\n",
                    StandardCharsets.UTF_8);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            range = value;
        } finally { Files.deleteIfExists(temporary); }
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
