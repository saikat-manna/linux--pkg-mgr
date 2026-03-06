package com.linuxpkgmgr.tool;

public enum IntentRole {
    /** Search / discovery tools — mark the beginning of a new user goal. */
    START,
    /** Action tools (install / remove / update) — mark completion with a concrete outcome. */
    END,
    /** Info / query tools — mid-intent, carry no boundary signal. */
    NEUTRAL
}
