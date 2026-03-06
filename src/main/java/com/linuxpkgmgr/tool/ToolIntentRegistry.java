package com.linuxpkgmgr.tool;

import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Scans all tool beans for {@link PkgTool}-annotated methods at startup and
 * builds a lookup table from canonical tool name → {@link IntentRole}.
 */
@Component
public class ToolIntentRegistry {

    private final Map<String, IntentRole> rolesByName;

    public ToolIntentRegistry(List<ToolBean> toolBeans) {
        rolesByName = new HashMap<>();
        toolBeans.forEach(this::scanBean);
    }

    private void scanBean(ToolBean bean) {
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
