# Code Trace Tree data format

Root document (`version="4"`) stored under global central storage.

## Project document

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project version="4">
  <projectId>uuid</projectId>
  <path>/absolute/project/root</path>
  <updatedAt>1722340000000</updatedAt>
  <activeProfileName>main</activeProfileName>
  <highlightingEnabled>true</highlightingEnabled>
  <traceProfiles>
    <traceProfile>
      <name>main</name>
      <tracePointNodes><!-- roots --></tracePointNodes>
      <expandedTracePointIds>
        <id>uuid</id>
      </expandedTracePointIds>
    </traceProfile>
  </traceProfiles>
  <descriptionAreaOpened>false</descriptionAreaOpened>
</project>
```

## Trace point node

```xml
<tracePointNode>
  <id>3d41c2d1-93ae-4ed3-b11d-f8a338bc388c</id>
  <parentId /><!-- empty for roots; otherwise parent uuid -->
  <tracePoint>
    <name>optional label</name>
    <fileName>Foo.java</fileName>
    <filePath>src/main/java/Foo.java</filePath>
    <lineNumber>29</lineNumber>
    <lineContent>exact line text (trimmed)</lineContent>
    <totalOccurrences>1</totalOccurrences>
    <occurrenceIndex>1</occurrenceIndex>
    <description>optional</description>
  </tracePoint>
  <children>
    <!-- nested tracePointNode elements -->
  </children>
</tracePointNode>
```

## Field notes

| Field | Notes |
|-------|--------|
| `filePath` | Relative to project root |
| `lineNumber` | 1-based |
| `lineContent` | Trimmed line text (no leading/trailing whitespace); used to re-validate / re-locate after edits |
| `totalOccurrences` / `occurrenceIndex` | Disambiguate duplicate line text in a file (1-based index among trimmed matches) |
| `description` | Omit element when empty (plugin does) |
| `isValid` | Never persist (runtime-only; see below) |

## `isValid` (runtime)

`isValid` is computed in memory. Do not write it to XML.

### Comparison rule

All line comparisons use **trimmed** strings:

```text
documentLine.trim() == lineContent.trim()
```

`getLineOccurrences` lists every 1-based line whose trimmed text equals the stored (trimmed) `lineContent`. That list size is `totalOccurrences`; `occurrenceIndex` picks which match (1-based).

### On load / refresh

For each node, the plugin:

1. Marks **invalid** if `id` is empty, `filePath` is empty, `lineContent` is null, the file is missing under the project root, or the document cannot be opened.
2. Marks **valid (unchanged)** if `lineNumber` is in range and the line at that number matches `lineContent` after trim.
3. Otherwise looks up occurrences of `lineContent`:
   - If `totalOccurrences` equals the current match count and `occurrenceIndex` is in `1..total`, updates `lineNumber` to `matches[occurrenceIndex - 1]` and sets **valid**.
   - Else sets **invalid**, updates `totalOccurrences` to the current match count, and clears `occurrenceIndex` to `0`.

Invalid nodes are not highlighted until their stored line content matches the file again (e.g. after an undo or fix).

### While the user edits a file

If a node is already invalid, it becomes valid again only when the text at its stored `lineNumber` matches `lineContent` (trimmed).

If a node is valid, live edits may update `lineNumber` / `lineContent` / occurrence fields (Enter at line start, edit on the same line, or line shifts below an insert/delete). `isValid` stays true when the new line text exists.

### Agent guidance

When adding or moving a trace point in XML:

1. Read the source line and store **trimmed** `lineContent`.
2. Count how many trimmed lines equal that text → `totalOccurrences`.
3. Set `occurrenceIndex` to the 1-based position of the intended line among those matches.
4. Set `lineNumber` to that line.

After IDE reload, validation should then mark the node valid.

## Import/export (not the global store)

- Single profile: root `<traceProfile>`
- Multi profile: root `<traceProfiles>` with `<activeProfileName>` + multiple `<traceProfile>`

Agents should edit the **global project XML**, not export files, unless the user asks to produce an importable export.
