package com.linuxpkgmgr.tool;

/**
 * Marker interface implemented by all tool beans.
 * Allows Spring to auto-collect them as List<ToolBean> — no registry updates
 * needed when adding new tool classes.
 */
public interface ToolBean {}
