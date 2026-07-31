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
    <lineContent>exact line text</lineContent>
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
| `lineContent` | Used to re-validate / re-locate after edits |
| `totalOccurrences` / `occurrenceIndex` | Disambiguate duplicate line text in a file |
| `description` | Omit element when empty (plugin does) |
| `isValid` | Never persist |

## Import/export (not the global store)

- Single profile: root `<traceProfile>`
- Multi profile: root `<traceProfiles>` with `<activeProfileName>` + multiple `<traceProfile>`

Agents should edit the **global project XML**, not export files, unless the user asks to produce an importable export.
