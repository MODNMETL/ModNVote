package com.modnmetl.modnvote.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Safe, testable loader for linked-offices definition files supplied by admins.
 *
 * Linked-offices definitions can be long, so admins may drop a JSON file into a
 * dedicated definitions folder (for example {@code plugins/ModNVote/definitions})
 * and import it by filename. This loader is responsible only for resolving the
 * filename safely inside the configured base directory and reading its UTF-8
 * contents.
 *
 * Boundaries:
 * - it performs no JSON parsing, validation, persistence, or lifecycle work
 * - it rejects path traversal so a filename can never escape the base directory
 * - it has no Bukkit dependency, so it is unit-testable in isolation
 */
public final class LinkedOfficesDefinitionFileLoader {

    private final Path baseDirectory;

    public LinkedOfficesDefinitionFileLoader(Path baseDirectory) {
        this.baseDirectory = Objects.requireNonNull(baseDirectory, "baseDirectory").normalize();
    }

    /**
     * Resolves a filename to an absolute path strictly inside the base directory.
     *
     * @throws PollServiceException if the filename is blank, contains path
     *                              separators or traversal segments, is absolute,
     *                              or would resolve outside the base directory
     */
    public Path resolveSafely(String filename) throws PollServiceException {
        if (filename == null || filename.isBlank()) {
            throw new PollServiceException("Definition filename must not be blank.");
        }

        String trimmed = filename.trim();
        if (trimmed.contains("/") || trimmed.contains("\\")
                || trimmed.contains("..")
                || trimmed.startsWith("~")) {
            throw new PollServiceException("Definition filename must be a plain file name inside the definitions folder.");
        }

        Path candidate = baseDirectory.resolve(trimmed).normalize();
        if (!candidate.startsWith(baseDirectory)) {
            throw new PollServiceException("Definition filename must resolve inside the definitions folder.");
        }
        return candidate;
    }

    /**
     * Resolves and reads a definition file as UTF-8 text.
     *
     * @throws PollServiceException if the path is unsafe or the file cannot be read
     */
    public String read(String filename) throws PollServiceException {
        Path path = resolveSafely(filename);
        if (!Files.isRegularFile(path)) {
            throw new PollServiceException("Definition file '" + filename + "' was not found in the definitions folder.");
        }
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new PollServiceException("Failed to read definition file '" + filename + "': " + e.getMessage(), e);
        }
    }

    public Path baseDirectory() {
        return baseDirectory;
    }
}
