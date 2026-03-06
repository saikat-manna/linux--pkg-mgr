package com.linuxpkgmgr.tool;

import com.linuxpkgmgr.service.CommandExecutor;
import com.linuxpkgmgr.service.SudoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Tools for managing system services via systemctl.
 * The agent MUST confirm with the user before calling start or stop.
 *
 * start/stop require root — SudoService shows a graphical password popup
 * (zenity or kdialog) when sudo credentials are not already cached.
 * status and search are read-only and need no privilege elevation.
 */
@Slf4j
@Component
public class ServiceManagementTools implements ToolBean {

    private final SudoService sudoService;
    private final CommandExecutor executor;

    public ServiceManagementTools(SudoService sudoService, CommandExecutor executor) {
        this.sudoService = sudoService;
        this.executor = executor;
    }

    @PkgTool(name = "search_services", role = IntentRole.START, description = """
            Searches for systemd service units whose name contains the given query (case-insensitive).
            Use this before start_service or stop_service when the exact unit name is uncertain.
            For example, if the user says "start ssh", call search_services("ssh") first to confirm
            the real unit name (e.g. "sshd.service") before acting.
            query: partial or full service name to search for (e.g. "ssh", "nginx", "mysql").
            Returns a list of matching unit files and their enabled/disabled state.
            """)
    public String searchServices(String query) {
        log.debug("search_services: {}", query);
        try {
            String output = executor.execute(
                    List.of("systemctl", "list-unit-files", "--type=service", "--no-pager"));
            String matches = output.lines()
                    .filter(line -> line.toLowerCase().contains(query.toLowerCase()))
                    .reduce("", (a, b) -> a + b + "\n")
                    .strip();
            if (matches.isBlank()) {
                return "No services found matching: " + query;
            }
            return "Services matching \"" + query + "\":\n\n" + matches;
        } catch (Exception e) {
            log.error("search_services failed for query '{}'", query, e);
            return "Failed to search services: " + e.getMessage();
        }
    }

    @PkgTool(name = "service_status", role = IntentRole.NEUTRAL, description = """
            Returns the current status of a systemd service (active, inactive, failed, not-found, etc.).
            Use search_services first if you are not sure of the exact unit name.
            serviceName: the exact systemd unit name, e.g. "sshd", "nginx", "bluetooth".
            You may omit the ".service" suffix — systemctl resolves it automatically.
            """)
    public String serviceStatus(String serviceName) {
        log.debug("service_status: {}", serviceName);
        try {
            // execute() (non-throwing) is intentional: systemctl status exits 3 for
            // inactive and 4 for not-found — both produce useful human-readable output.
            String output = executor.execute(List.of("systemctl", "status", serviceName));
            return output.isBlank() ? "No output returned for service: " + serviceName : output.strip();
        } catch (Exception e) {
            log.error("service_status failed for {}", serviceName, e);
            return "Failed to get status for " + serviceName + ": " + e.getMessage();
        }
    }

    @PkgTool(name = "start_service", role = IntentRole.END, description = """
            Starts a systemd service.
            Use search_services first if you are not sure of the exact unit name.
            Call this only after the user has explicitly confirmed they want to start the service.
            Requires root privileges — a password popup will appear if needed.
            serviceName: the exact systemd unit name, e.g. "sshd", "nginx", "bluetooth".
            Returns a success message or an error if the service fails to start.
            """)
    public String startService(String serviceName) {
        log.info("start_service: {}", serviceName);
        try {
            sudoService.runWithSudo(List.of("systemctl", "start", serviceName));
            return "Service '" + serviceName + "' started successfully.";
        } catch (Exception e) {
            log.error("start_service failed for {}", serviceName, e);
            return "Failed to start " + serviceName + ": " + e.getMessage();
        }
    }

    @PkgTool(name = "stop_service", role = IntentRole.END, description = """
            Stops a running systemd service.
            Use search_services first if you are not sure of the exact unit name.
            Call this only after the user has explicitly confirmed they want to stop the service.
            Requires root privileges — a password popup will appear if needed.
            serviceName: the exact systemd unit name, e.g. "sshd", "nginx", "bluetooth".
            Returns a success message or an error if the service fails to stop.
            """)
    public String stopService(String serviceName) {
        log.info("stop_service: {}", serviceName);
        try {
            sudoService.runWithSudo(List.of("systemctl", "stop", serviceName));
            return "Service '" + serviceName + "' stopped successfully.";
        } catch (Exception e) {
            log.error("stop_service failed for {}", serviceName, e);
            return "Failed to stop " + serviceName + ": " + e.getMessage();
        }
    }
}
