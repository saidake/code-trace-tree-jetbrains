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
  <namePromptEnabled>true</namePromptEnabled>
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

## Trace point kinds

Every `<tracePoint>` has `<traceType>`: `LINE` | `FILE` | `DIRECTORY`.

### LINE (editor caret)

```xml
<tracePoint>
  <traceName>optional label</traceName>
  <traceType>LINE</traceType>
  <baseName>Foo.java</baseName>
  <tracePath>src/main/java/Foo.java</tracePath>
  <lineNumber>29</lineNumber>
  <lineContent>exact line text (trimmed)</lineContent>
  <totalOccurrences>1</totalOccurrences>
  <occurrenceIndex>1</occurrenceIndex>
  <description>optional</description>
</tracePoint>
```

### FILE (Project View file)

```xml
<tracePoint>
  <traceName>optional label</traceName>
  <traceType>FILE</traceType>
  <baseName>Foo.java</baseName>
  <tracePath>src/main/java/Foo.java</tracePath>
  <description>optional</description>
</tracePoint>
```

### DIRECTORY (Project View folder)

```xml
<tracePoint>
  <traceName>optional label</traceName>
  <traceType>DIRECTORY</traceType>
  <baseName>dto</baseName>
  <tracePath>src/main/java/com/example/dto</tracePath>
  <description>optional</description>
</tracePoint>
```

## Node wrapper

```xml
<tracePointNode>
  <id>3d41c2d1-93ae-4ed3-b11d-f8a338bc388c</id>
  <parentId /><!-- empty for roots; otherwise parent uuid -->
  <tracePoint><!-- see above --></tracePoint>
  <children>
    <!-- nested tracePointNode elements -->
  </children>
</tracePointNode>
```

## Field notes

| Field | Notes |
|-------|--------|
| `traceName` | User label for the node |
| `traceType` | `LINE`, `FILE`, or `DIRECTORY` (required) |
| `baseName` | Last path segment (file or directory name) |
| `tracePath` | Relative to project root (file or directory) |
| `lineNumber` | 1-based; **LINE only** |
| `lineContent` | Trimmed line text; **LINE only** |
| `totalOccurrences` / `occurrenceIndex` | Disambiguate duplicate trimmed lines; **LINE only** |
| `description` | Optional for all kinds (`LINE`, `FILE`, `DIRECTORY`); omit element when empty |
| `isValid` | Never persist (runtime-only) |

## `isValid` (runtime)

| Kind | Valid when |
|------|------------|
| `LINE` | File exists; trimmed line at `lineNumber` matches `lineContent`, or occurrence rebinding succeeds (`totalOccurrences` / `occurrenceIndex`) |
| `FILE` | Path exists and is a file |
| `DIRECTORY` | Path exists and is a directory |

Line comparisons always use trimmed text: `documentLine.trim() == lineContent.trim()`.

For agent-written `LINE` nodes:

1. Store **trimmed** `lineContent`.
2. Count matching trimmed lines → `totalOccurrences`.
3. Set `occurrenceIndex` (1-based) and `lineNumber` to the intended match.

## Import/export

- Single profile: root `<traceProfile>`
- Multi profile: root `<traceProfiles>` with `<activeProfileName>` + multiple `<traceProfile>`

Same `<traceType>` rules as the global store. Prefer editing the **global project XML** unless the user asks for an export file.
