package com.uaf.neo4j.plugin;

import com.uaf.neo4j.plugin.export.ExportResult;

import java.io.IOException;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Accumulates timestamped export-progress lines and writes them to
 * ~/uaf-neo4j-logs/uaf-export-&lt;timestamp&gt;.log when the export finishes.
 */
public class ExportLog {

    private static final Logger LOG = Logger.getLogger(ExportLog.class.getName());
    private static final DateTimeFormatter TS      = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final String RULE = dashes(60);

    private final List<String> lines = new ArrayList<>();
    private final LocalDateTime startedAt = LocalDateTime.now();
    private Path logFile;

    public ExportLog(String projectName) {
        lines.add("UAF Neo4j Export Log");
        lines.add("Project  : " + projectName);
        lines.add("Started  : " + startedAt.format(TS));
        lines.add(RULE);
    }

    /** Append a timestamped progress message. */
    public void add(String message) {
        lines.add(LocalDateTime.now().format(TS) + "  " + message);
    }

    /** Append the final counts, any errors, and flush to disk. */
    public void finish(ExportResult result) {
        lines.add(RULE);
        lines.add("Nodes written         : " + result.nodesWritten);
        if (!result.languageCounts.isEmpty()) {
            result.languageCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(e -> lines.add("  " + e.getKey() + "              : " + e.getValue()));
        }
        lines.add("Relationships written  : " + result.relationshipsWritten);
        lines.add("INSTANCE_OF links      : " + result.instanceLinksWritten);
        lines.add("DEFINES links          : " + result.definesLinksWritten);
        lines.add("Errors                 : " + result.errors.size());
        for (String err : result.errors) {
            lines.add("  ERROR: " + err);
        }
        lines.add("Finished : " + LocalDateTime.now().format(TS));
        writeFile();
    }

    /** Append a failure message and flush to disk (used when an exception escapes). */
    public void finishWithException(String message) {
        lines.add(RULE);
        lines.add("FAILED   : " + message);
        lines.add("Finished : " + LocalDateTime.now().format(TS));
        writeFile();
    }

    public String getText() {
        return String.join(System.lineSeparator(), lines);
    }

    /** Returns the path of the written log file, or null if writing failed. */
    public Path getLogFile() {
        return logFile;
    }

    private void writeFile() {
        try {
            Path dir = Paths.get(System.getProperty("user.home"), "uaf-neo4j-logs");
            Files.createDirectories(dir);
            logFile = dir.resolve("uaf-export-" + startedAt.format(FILE_TS) + ".log");
            Files.write(logFile, lines);
            LOG.info("Export log written to " + logFile);
        } catch (IOException e) {
            LOG.warning("Could not write export log: " + e.getMessage());
        }
    }

    private static String dashes(int n) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) sb.append('-');
        return sb.toString();
    }
}
