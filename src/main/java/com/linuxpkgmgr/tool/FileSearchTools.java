package com.linuxpkgmgr.tool;

import com.linuxpkgmgr.service.CommandExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Tool for searching files and directories recursively in the user's home directory.
 * Delegates to the system `find` command for fast native filesystem traversal.
 */
@Slf4j
@Component
public class FileSearchTools implements ToolBean {

    private static final int MAX_RESULTS = 50;
    private static final String HOME = System.getProperty("user.home");

    private final CommandExecutor executor;

    public FileSearchTools(CommandExecutor executor) {
        this.executor = executor;
    }

    @PkgTool(name = "search_files", role = IntentRole.START, description = """
            Searches for files or directories recursively in the user's home directory (~/).
            Use this when the user asks things like "find my resume", "where are my PDFs",
            "search for files named config", "find all mp3 files", etc.
            namePattern: partial or full filename to match (case-insensitive).
                         Supports wildcards: "report*" matches anything starting with "report".
                         Leave blank to match any name (combine with extension to list by type).
            extension:   file extension without the dot (e.g. "pdf", "txt", "mp3").
                         Leave blank to include all extensions.
            type:        "file" to return files only, "dir" to return directories only,
                         leave blank for both.
            Returns up to 50 matching paths relative to the home directory.
            Requires at least one of namePattern or extension.
            """)
    public String searchFiles(String namePattern, String extension, String type) {
        log.debug("search_files — namePattern='{}' extension='{}' type='{}'",
                namePattern, extension, type);

        boolean blankName = namePattern == null || namePattern.isBlank();
        boolean blankExt  = extension   == null || extension.isBlank();
        if (blankName && blankExt) {
            return "Please provide at least a namePattern or an extension to search for.";
        }

        List<String> cmd = buildFindCommand(namePattern, extension, type);
        log.debug("search_files — command: {}", cmd);

        String raw;
        try {
            raw = executor.execute(cmd);
        } catch (Exception e) {
            log.error("search_files failed", e);
            return "File search failed: " + e.getMessage();
        }

        List<String> results = raw.lines()
                .filter(l -> !l.isBlank() && !l.contains("Permission denied"))
                .limit(MAX_RESULTS)
                .toList();

        if (results.isEmpty()) {
            return "No matches found in ~/ for the given criteria.";
        }

        boolean capped = results.size() == MAX_RESULTS;
        StringBuilder sb = new StringBuilder();
        sb.append("Found ").append(results.size()).append(capped ? "+" : "")
          .append(" result(s) in ~/:\n\n");
        for (String path : results) {
            String rel = path.startsWith(HOME) ? "~" + path.substring(HOME.length()) : path;
            sb.append("  ").append(rel).append("\n");
        }
        if (capped) {
            sb.append("\n(Showing first ").append(MAX_RESULTS)
              .append(" matches. Refine with a more specific pattern.)");
        }
        return sb.toString();
    }

    private List<String> buildFindCommand(String namePattern, String extension, String type) {
        List<String> cmd = new ArrayList<>();
        cmd.add("find");
        cmd.add(HOME);

        if ("file".equalsIgnoreCase(type))     { cmd.add("-type"); cmd.add("f"); }
        else if ("dir".equalsIgnoreCase(type)) { cmd.add("-type"); cmd.add("d"); }

        String name = (namePattern == null || namePattern.isBlank()) ? "*" : namePattern.trim();
        String ext  = (extension   == null || extension.isBlank())   ? ""  : "." + extension.trim();

        String glob = (name.contains("*") || name.contains("?"))
                ? name + ext
                : "*" + name + "*" + ext;

        cmd.add("-iname");
        cmd.add(glob);

        return cmd;
    }
}
