package com.danasea.backend.modules.ai.support;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Writes observations only after live acceptance assertions succeed. */
public final class AiAcceptanceRecorder {
    private AiAcceptanceRecorder() { }

    public static void write(String name, Map<String, Object> observation) throws Exception {
        String directory = System.getProperty("ai.acceptance.dir");
        if (directory == null) return;
        if (!name.matches("[a-z0-9_.-]+")) throw new IllegalArgumentException("Invalid observation name");
        Path root = Path.of(directory);
        Files.createDirectories(root);
        Map<String, Object> record = new LinkedHashMap<>(observation);
        record.put("case_name", name);
        record.put("status", "PASSED");
        new ObjectMapper().writeValue(root.resolve(name + ".json").toFile(), record);
    }
}
