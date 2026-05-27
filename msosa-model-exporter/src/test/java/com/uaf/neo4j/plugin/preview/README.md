# UI Preview Harness

Run and iterate on the workbench **without launching MSOSA**. Lives under `src/test/` and ships nowhere — purely a development aid.

## What's here

| File | Role |
|---|---|
| `UiPreview.java` | Launcher. Boots the workbench either interactively or in headless screenshot mode. |
| `PreviewUAFWorkbench.java` | Workbench subclass that overrides the dialog factories to inject sample-data subclasses. |
| `PreviewExportConfigDialog.java` | Subclass of `ExportConfigDialog` that supplies sample packages + elements via `topLevelPackageNames` / `scanModelElements` overrides. |
| `PreviewGraphInspectorDialog.java` | Subclass of `GraphInspectorDialog` that returns canned nodes + neighbourhood via `fetchAllNodes` / `fetchNeighbourhood` overrides. |
| `UiInspector.java` | Debug overlay. Hover any widget to see the field/class/source-line behind it; click prints the same as IDE-clickable stack-trace frames. |

The production classes are not modified beyond widening a few method modifiers so these subclasses can override them.

## Running

All commands run from `msosa-model-exporter/`. The `ui-preview` Maven profile (defined in `pom.xml`) wires `exec:java` to `UiPreview#main`.

### Interactive

Opens the workbench with the inspector overlay installed.

```powershell
mvn -Pui-preview test-compile exec:java
```

> PowerShell splits `-Dkey=value` flags on the `.` and feeds Maven the wrong arguments. Always quote `-D` flags. Bash users on Linux/CI can drop the quotes.

Closing the workbench window exits the JVM.

On Linux / CI without a real display, wrap with `xvfb-run`.

### Headless screenshots

Writes one PNG per rail item into the given directory; exits when done.

```powershell
mvn -Pui-preview test-compile exec:java "-Dpreview.screenshot=target/ui-preview"
```

Outputs: `workbench-export.png` · `workbench-inspect.png` · `workbench-validate.png` · `workbench-federate.png` · `workbench-insights.png` · `workbench-settings.png` — one per left-rail item.

### Inspector field demo (headless)

Highlights one named widget and writes a single PNG. Useful for "where is `X` in the UI?" without running interactively.

```powershell
mvn -Pui-preview test-compile exec:java `
    "-Dpreview.inspect=exportBtn" `
    "-Dpreview.screenshot=target/ui-preview"
```

`-Dpreview.inspect` accepts:

- A **field name** anywhere in the workbench (`exportBtn`, `uriField`, `mainTable`, …) — resolves via reflection across the workbench, all panels, and the embedded forms.
- A **text literal** of an inline widget (`"Test Connection"`, `"Connection"`, …) — resolves by grepping `src/main/java` and `src/test/java` for the string.

Output: `inspect-demo-workbench.png`.

## UiInspector — debug flags

Pass these alongside any other `-D` flags:

| Flag | Effect |
|---|---|
| `"-Dpreview.inspect.verbose=true"` | Print every hover to console (in addition to clicks). Hovering doesn't fire the widget's action, so this is safe for buttons. |
| `"-Dpreview.inspect.dump=true"` | At install time, print the full component tree of the workbench (indented, with field-ref annotations). |
| `"-Dpreview.srcRoot=path1,path2"` | Override source roots searched for field declarations and text literals (default `src/main/java,src/test/java`). |

## How the inspector resolves widgets

Three layers, all reflective / source-scanning, so production code needs no annotations:

1. **Direct field index** — every field declared on the workbench, its panels, and the embedded dialogs that holds a `Component` is indexed. Maps, Iterables, and arrays of components are walked recursively.
2. **Descendant index** — for each indexed component, the entire Swing subtree below it is also indexed under a `field.children[i].children[j]…` key. JScrollPane viewport views are peeked through explicitly. This stops deep widgets from falling through to a far ancestor.
3. **Inline-text fallback** — widgets with no field entry but a non-empty `getText()` / tooltip / `getName()` are looked up by grepping their text literal in the source roots.

The closest match on the component's ancestor chain wins.

## Click output format

When you click a widget, the inspector prints in a stack-trace format that IntelliJ / VS Code / Cursor terminals turn into a click-to-open hyperlink:

```
[ui-inspector] click: JButton "Save Config" — ExportConfigDialog.saveConfigBtn
    at com.uaf.neo4j.plugin.ui.ExportConfigDialog.<init>(ExportConfigDialog.java:74)
```

When verbose mode is on, every hover prints the same form. The on-screen overlay still shows a compact label; the console gets the clickable frames.

## What does **not** work here

This harness is for **visual layout iteration only**. The following buttons are no-ops because there's no MSOSA project, no Neo4j connection, and no Fuseki endpoint:

- Export
- Test Connection
- Refresh
- Locate in MSOSA

If you need to exercise those paths, build and deploy the plugin into MSOSA (`mvn package` → drop the zip into `<MSOSA_HOME>/plugins/`).

## Adding sample data

Sample model elements for the Export rail live in `PreviewExportConfigDialog#scanModelElements`. Sample graph nodes/edges for the Inspect rail live in `PreviewGraphInspectorDialog` (its `fetchAllNodes` / `fetchNeighbourhood` overrides). Edit those to mirror new fields or stereotypes as the production dialogs grow.

The plugin singleton (`UAFNeo4jPlugin.getInstance()`) is seeded reflectively by `UiPreview#seedPluginSingleton` so dialogs can read config in their constructors without calling `Plugin.init()` (which touches MSOSA). Defaults mirror `UAFNeo4jPlugin.loadConfig`.
