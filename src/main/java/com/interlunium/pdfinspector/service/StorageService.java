package com.interlunium.pdfinspector.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Manages the filesystem storage of uploaded PDF documents and their cached analysis results.
 *
 * <p>Every uploaded document receives a random UUID. The following files are kept on disk:</p>
 * <ul>
 *   <li>{@code <uuid>.pdf} - the original uploaded bytes</li>
 *   <li>{@code <uuid>-analysis.json} - cached analysis bundle (metadata, signatures, widgets, revisions)</li>
 * </ul>
 */
@ApplicationScoped
public class StorageService {

    private final Path root;

    /**
     * Creates the storage service using the configured directory.
     *
     * @param directoryPath configured {@code app.storage.directory}
     */
    public StorageService(@ConfigProperty(name = "app.storage.directory") String directoryPath) {
        this.root = Path.of(directoryPath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create storage directory " + root, e);
        }
    }

    /**
     * Stores raw bytes and returns a fresh document id.
     *
     * @param filename original file name (kept for reference only)
     * @param content  raw PDF bytes
     * @return the generated document id
     * @throws IOException if the file cannot be written
     */
    public String store(String filename, byte[] content) throws IOException {
        String id = UUID.randomUUID().toString();
        Path target = pdfPath(id);
        Files.write(target, content);
        return id;
    }

    /**
     * @return the directory where documents are stored
     */
    public Path root() {
        return root;
    }

    /**
     * @param documentId document id
     * @return path of the stored PDF
     */
    public Path pdfPath(String documentId) {
        return root.resolve(Objects.requireNonNull(documentId) + ".pdf");
    }

    /**
     * @param documentId document id
     * @return path of the cached analysis bundle
     */
    public Path analysisPath(String documentId) {
        return root.resolve(Objects.requireNonNull(documentId) + "-analysis.json");
    }

    /**
     * @param documentId document id
     * @return the raw PDF bytes
     * @throws IOException if the file cannot be read
     */
    public byte[] read(String documentId) throws IOException {
        Path path = pdfPath(documentId);
        if (!Files.isRegularFile(path)) {
            throw new NotFoundException("Document " + documentId + " not found");
        }
        return Files.readAllBytes(path);
    }

    /**
     * @param documentId document id
     * @return the raw PDF bytes
     * @throws IOException if the file cannot be read
     */
    public byte[] readAnalysis(String documentId) throws IOException {
        Path path = analysisPath(documentId);
        if (!Files.isRegularFile(path)) {
            return null;
        }
        return Files.readAllBytes(path);
    }

    /**
     * Writes the analysis bundle for a document.
     *
     * @param documentId document id
     * @param json       serialized analysis bundle
     * @throws IOException if the file cannot be written
     */
    public void writeAnalysis(String documentId, byte[] json) throws IOException {
        Files.write(analysisPath(documentId), json);
    }

    /**
     * @param documentId document id
     * @return the creation timestamp of the stored PDF
     * @throws IOException if the file cannot be read
     */
    public Instant createdAt(String documentId) throws IOException {
        return Files.getLastModifiedTime(pdfPath(documentId)).toInstant();
    }
}
