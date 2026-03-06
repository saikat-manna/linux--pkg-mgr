package com.linuxpkgmgr.cli;

import com.linuxpkgmgr.tool.AppQueryTools;
import com.linuxpkgmgr.tool.PackageInstallTools;
import com.linuxpkgmgr.tool.PackageQueryTools;
import com.linuxpkgmgr.tool.PackageSearchTools;
import com.linuxpkgmgr.tool.PackageUpdateTools;
import com.linuxpkgmgr.tool.ProcessTools;
import com.linuxpkgmgr.tool.ServiceManagementTools;
import org.springframework.stereotype.Component;

/**
 * Selects which tool beans to include for a given user query.
 *
 * Stub implementation — always returns all tools.
 * Replace {@link #select} with keyword/group-based filtering to reduce
 * the number of tool schemas sent to the model per request.
 */
@Component
public class ToolSelector {

    private final Object[] allTools;

    public ToolSelector(AppQueryTools appQueryTools,
                        PackageQueryTools packageQueryTools,
                        PackageSearchTools packageSearchTools,
                        PackageInstallTools packageInstallTools,
                        PackageUpdateTools packageUpdateTools,
                        ProcessTools processTools,
                        ServiceManagementTools serviceManagementTools) {
        this.allTools = new Object[]{
                appQueryTools, packageQueryTools, packageSearchTools,
                packageInstallTools, packageUpdateTools, processTools,
                serviceManagementTools
        };
    }

    /**
     * Returns the tool beans relevant to {@code userQuery}.
     * Current stub returns every registered tool.
     */
    public Object[] select(String userQuery) {
        return allTools;
    }
}
