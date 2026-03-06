package com.linuxpkgmgr.tool;

import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Scans all tool beans for {@link PkgTool}-annotated methods at startup and
 * builds a lookup table from canonical tool name → {@link IntentRole}.
 */
@Component
public class ToolIntentRegistry {

    private final Map<String, IntentRole> rolesByName;

    public ToolIntentRegistry(AppQueryTools appQueryTools,
                              PackageQueryTools packageQueryTools,
                              PackageSearchTools packageSearchTools,
                              PackageInstallTools packageInstallTools,
                              PackageUpdateTools packageUpdateTools,
                              ProcessTools processTools,
                              ServiceManagementTools serviceManagementTools) {
        rolesByName = new HashMap<>();
        Stream.of(appQueryTools, packageQueryTools, packageSearchTools,
                        packageInstallTools, packageUpdateTools, processTools,
                        serviceManagementTools)
                .forEach(this::scanBean);
    }

    private void scanBean(Object bean) {
        for (Method method : bean.getClass().getDeclaredMethods()) {
            PkgTool ann = method.getAnnotation(PkgTool.class);
            if (ann != null) {
                rolesByName.put(ann.name(), ann.role());
            }
        }
    }

    /** Returns the {@link IntentRole} for a tool, or {@link IntentRole#NEUTRAL} if unknown. */
    public IntentRole getRole(String toolName) {
        return rolesByName.getOrDefault(toolName, IntentRole.NEUTRAL);
    }
}
