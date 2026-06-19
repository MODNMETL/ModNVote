package com.modnmetl.modnvote.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the safe, Bukkit-free definition file loader, focused on path
 * traversal rejection and UTF-8 reads.
 */
class LinkedOfficesDefinitionFileLoaderTest {

    @Test
    void readsPlainFileInsideBaseDirectory(@TempDir Path baseDir) throws Exception {
        String json = "{\"model\":\"LINKED_OFFICES\"}";
        Files.writeString(baseDir.resolve("definition.json"), json, StandardCharsets.UTF_8);

        LinkedOfficesDefinitionFileLoader loader = new LinkedOfficesDefinitionFileLoader(baseDir);
        assertEquals(json, loader.read("definition.json"));
    }

    @Test
    void rejectsParentTraversal(@TempDir Path baseDir) {
        LinkedOfficesDefinitionFileLoader loader = new LinkedOfficesDefinitionFileLoader(baseDir);
        PollServiceException ex = assertThrows(PollServiceException.class,
                () -> loader.resolveSafely("../evil.json"));
        assertTrue(ex.getMessage().toLowerCase().contains("file name")
                || ex.getMessage().toLowerCase().contains("folder"));
    }

    @Test
    void rejectsNestedPathSegments(@TempDir Path baseDir) {
        LinkedOfficesDefinitionFileLoader loader = new LinkedOfficesDefinitionFileLoader(baseDir);
        assertThrows(PollServiceException.class, () -> loader.resolveSafely("sub/definition.json"));
        assertThrows(PollServiceException.class, () -> loader.resolveSafely("sub\\definition.json"));
    }

    @Test
    void rejectsBlankFilename(@TempDir Path baseDir) {
        LinkedOfficesDefinitionFileLoader loader = new LinkedOfficesDefinitionFileLoader(baseDir);
        assertThrows(PollServiceException.class, () -> loader.resolveSafely("  "));
    }

    @Test
    void readingMissingFileThrows(@TempDir Path baseDir) {
        LinkedOfficesDefinitionFileLoader loader = new LinkedOfficesDefinitionFileLoader(baseDir);
        PollServiceException ex = assertThrows(PollServiceException.class,
                () -> loader.read("does-not-exist.json"));
        assertTrue(ex.getMessage().contains("was not found"));
    }
}
