package com.linuxpkgmgr.tool;

import lombok.extern.slf4j.Slf4j;
import com.linuxpkgmgr.tool.IntentRole;
import com.linuxpkgmgr.tool.PkgTool;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Tool for listing running processes, optionally sorted by CPU or memory usage.
 */
@Slf4j
@Component
public class ProcessTools implements ToolBean {

    private static final int MAX_RESULTS = 20;

    @PkgTool(name = "list_running_processes", role = IntentRole.START, description = """
            Lists currently running processes on the system, showing PID, name, CPU%, memory%,
            RSS (resident memory), and the owning user.
            Use this when the user asks things like "what's running", "show processes",
            "what's using my CPU", "what's eating memory", "top processes", etc.
            sortBy: "cpu" to sort by CPU usage (default), "mem" to sort by memory usage.
            Returns the top 20 processes in descending order.
            """)
    public String listRunningProcesses(String sortBy) {
        String field = (sortBy != null && sortBy.trim().equalsIgnoreCase("mem")) ? "%mem" : "%cpu";
        log.debug("listRunningProcesses — sortBy: '{}'", field);

        List<ProcessRow> rows;
        try {
            rows = runPs(field);
        } catch (Exception e) {
            log.error("listRunningProcesses failed", e);
            return "Failed to list processes: " + e.getMessage();
        }

        if (rows.isEmpty()) return "No processes found.";

        String sortLabel = field.equals("%mem") ? "memory" : "CPU";
        StringBuilder sb = new StringBuilder();
        sb.append("Running processes — sorted by ").append(sortLabel)
          .append(" (top ").append(rows.size()).append("):\n\n");
        sb.append("  %-7s  %-24s  %5s  %5s  %-9s  %s\n"
                .formatted("PID", "NAME", "CPU%", "MEM%", "RSS", "USER"));
        sb.append("  ").append("─".repeat(65)).append("\n");

        for (ProcessRow r : rows) {
            sb.append("  %-7d  %-24s  %5.1f  %5.1f  %-9s  %s\n".formatted(
                    r.pid(), r.name(), r.cpu(), r.mem(), humanRss(r.rssKb()), r.user()));
        }

        return sb.toString();
    }

    // -------------------------------------------------------------------------

    private List<ProcessRow> runPs(String sortField) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                "ps", "-eo", "pid,comm,%cpu,%mem,rss,user",
                "--sort=-" + sortField, "--no-headers");
        pb.redirectErrorStream(true);
        Process proc = pb.start();

        List<ProcessRow> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null && rows.size() < MAX_RESULTS) {
                ProcessRow row = parse(line);
                if (row != null) rows.add(row);
            }
        }
        proc.waitFor();
        return rows;
    }

    /** Parses one line of `ps` output: pid comm %cpu %mem rss user */
    private ProcessRow parse(String line) {
        String[] parts = line.trim().split("\\s+", 6);
        if (parts.length < 6) return null;
        try {
            int    pid   = Integer.parseInt(parts[0]);
            String name  = parts[1];
            double cpu   = Double.parseDouble(parts[2]);
            double mem   = Double.parseDouble(parts[3]);
            long   rssKb = Long.parseLong(parts[4]);
            String user  = parts[5];
            return new ProcessRow(pid, name, cpu, mem, rssKb, user);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String humanRss(long kb) {
        if (kb >= 1_048_576) return "%.1f GB".formatted(kb / 1_048_576.0);
        if (kb >= 1_024)     return "%.0f MB".formatted(kb / 1_024.0);
        return kb + " KB";
    }

    private record ProcessRow(int pid, String name, double cpu, double mem, long rssKb, String user) {}
}
