# Spec: List Installed Applications by Category

## Problem

The existing `listInstalledPackages` tool returns all user-installed packages — including CLI tools, libraries, daemons, codecs, and fonts — mixed together. There is no way to browse the installed *applications* (GUI apps that appear in the app menu) by type such as "Media Players", "Editors", or "Browsers".

## Goal

Add a tool that:
1. Returns only **applications** (packages that have a `.desktop` entry and appear in app menus).
2. Optionally filters by **category** (e.g., `AudioVideo`, `Development`, `Graphics`).
3. Works across both Flatpak and native package manager installs.

---

## New Tool: `listInstalledApps(String category)`

### Tool signature

```java
@Tool(description = """
    Lists installed GUI applications visible in the app menu, optionally filtered by
    category. The category MUST be a single word — map the user's natural language
    request to one of these canonical values before calling this tool:
      AudioVideo, Development, Graphics, Network, Office, Game, Utility,
      Education, Science, System, Settings.
    Common mappings: "media player" → AudioVideo, "browser" → Network,
    "text editor" or "IDE" → Development, "image editor" → Graphics,
    "office suite" → Office, "games" → Game.
    Leave blank to list all installed apps.
    """)
public String listInstalledApps(String category)
```

### Input contract

- `category` is always a **single word**. The LLM resolves multi-word phrases
  ("media player", "video editor", "web browser") to a canonical category before
  invoking the tool. The tool itself does no phrase parsing.
- The script/tool additionally accepts short aliases (`media`, `dev`, `browser`, etc.)
  as a convenience for direct CLI use (see alias table below).

### Behavior

- `category` is case-insensitive; empty/blank means "all apps".
- Returns apps sorted alphabetically by display name.
- Result is capped at `pkg-mgr.list.max-results` (same config as existing tool).
- Each result line shows: source tag, display name, app-id/package, version, and matched categories.

### Example output

```
Installed applications [category: AudioVideo] — 4 found

[flatpak]  Rhythmbox        (org.gnome.Rhythmbox)    3.4.7   — Music player
[flatpak]  VLC media player (org.videolan.VLC)         3.0.20  — Multimedia player
[native ]  Audacity         audacity                  3.4.2   — Audio editor
[native ]  Shotwell         shotwell                  0.32.1  — Photo manager
```

---

## What counts as an "application"?

A package is considered an application if it provides at least one `.desktop` file that satisfies **all** of:

- `Type=Application` (not `Type=Link` or `Type=Directory`)
- Does **not** have `NoDisplay=true`

This mirrors what desktop environments use to populate the app menu.

---

## Data Sources

### Flatpak

Desktop files are exported by Flatpak to a known location:

```
/var/lib/flatpak/exports/share/applications/<app-id>.desktop    # system-wide
~/.local/share/flatpak/exports/share/applications/<app-id>.desktop  # per-user
```

The `Categories=` field in these files is the primary category source for Flatpak apps.

### Native packages

Desktop files for native packages live in:

```
/usr/share/applications/*.desktop
/usr/local/share/applications/*.desktop
```

**Linking desktop files back to packages:**

- Walk all `.desktop` files in the above directories.
- For each, look up the owning package via the package manager's file-ownership query:
  - DNF/RPM: `rpm -qf <path>`
  - APT/dpkg: `dpkg -S <path>`
  - Pacman: `pacman -Qo <path>`
  - Zypper/RPM: same as DNF
- If the owning package is in the user-installed list → include it.
- If the `.desktop` file is not owned by any package (manually placed) → include it anyway.

---

## Category Taxonomy

Use the [freedesktop.org Main Categories](https://specifications.freedesktop.org/menu-spec/latest/category-registry.html) as the canonical set. The tool accepts both the exact spec value and common aliases:

| Canonical category | Common aliases accepted |
|--------------------|------------------------|
| `AudioVideo`       | `media`, `mediaplayer`, `audio`, `video`, `music` |
| `Development`      | `dev`, `ide`, `editor`, `coding` |
| `Graphics`         | `image`, `photo`, `drawing` |
| `Network`          | `browser`, `internet`, `email`, `chat` |
| `Office`           | `productivity`, `word`, `spreadsheet` |
| `Game`             | `games`, `gaming` |
| `Utility`          | `tools`, `utilities` |
| `Education`        | `learning` |
| `Science`          | — |
| `System`           | `sysadmin` |
| `Settings`         | `preferences` |

Alias resolution happens before filtering. If an app's `Categories=` field contains the resolved canonical value, it matches.

An app may appear under multiple categories (e.g., `Categories=AudioVideo;Audio;Player;`). It is returned if **any** of its categories matches the filter.

---

## Implementation Plan

### Phase 1 — Desktop file scanner

New class: `DesktopFileService`

Responsibilities:
- Scan `.desktop` file directories (Flatpak export paths + native paths).
- Parse each file: extract `Name`, `Type`, `NoDisplay`, `Categories`, `Comment`.
- Return a `Map<String, DesktopEntry>` keyed by app-id or package name.
- Cache results with the same 60-second TTL as `SystemPackageService`.

### Phase 2 — Application model

New record: `AppInfo`

```java
public record AppInfo(
    String name,               // display name from .desktop Name=
    String id,                 // flatpak app-id or native package name
    String version,            // from existing PackageInfo
    String summary,            // from .desktop Comment= or PackageInfo summary
    List<String> categories,   // parsed from .desktop Categories=
    PackageInfo.Source source  // NATIVE or FLATPAK
)
```

### Phase 3 — Join logic

In a new `AppQueryTools` class:

1. Fetch user-installed packages from `SystemPackageService` → `List<PackageInfo>`.
2. Fetch desktop entries from `DesktopFileService` → `Map<String, DesktopEntry>`.
3. Join: for each `PackageInfo`, look up its desktop entry by package name / app-id.
4. Keep only packages that have a matching `Type=Application`, `NoDisplay != true` desktop entry.
5. Map matched pairs to `AppInfo`.
6. Apply category filter (alias resolution → canonical → match against `categories` list).
7. Sort alphabetically, cap at `max-results`, and format output.

### Phase 4 — Tool registration

Register `AppQueryTools` in `AgentConfig` alongside existing tools.

---

## Edge Cases

| Scenario | Handling |
|----------|----------|
| App ships multiple `.desktop` files | Merge categories from all files; use the first file's `Name`/`Comment`. |
| `.desktop` file has no `Categories=` field | Treat as category `Other`; included when filter is blank. |
| Flatpak export path doesn't exist | Skip silently. |
| Same app installed as both Flatpak and native | Show both entries separately (consistent with existing tool). |
| `rpm -qf` / `dpkg -S` is slow at scale | Only run ownership checks for `.desktop` files whose basename matches a known installed package name to limit subprocess calls. |

---

## Out of Scope (v1)

- CLI-only applications (no `.desktop` file).
- Sub-category filtering (e.g., `Audio` within `AudioVideo`).
- Sorting by install date, size, or usage frequency.
- Apps installed to non-standard paths.
