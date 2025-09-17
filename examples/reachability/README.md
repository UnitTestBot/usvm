# USVM Reachability Analysis - Target File Formats

This document describes the supported target file formats for the USVM TypeScript Reachability Analysis tool.

## Supported Formats

The tool automatically detects and supports three different JSON formats for specifying targets:

### 1. Linear Trace Format - `{ "targets": [...] }`

A single linear trace containing a sequence of target points. In a linear trace:
- **Only the first target** should be marked as `"initial"` (entry point)
- **Only the last target** should be marked as `"final"` (end point)  
- **All intermediate targets** don't need a type field (defaults to `"intermediate"`)

**Example: `targets.json`**
```json
{
  "targets": [
    {
      "type": "initial",
      "location": {
        "fileName": "ProcessManager.ts",
        "className": "Process",
        "methodName": "start",
        "stmtType": "IfStmt",
        "block": 0,
        "index": 0
      }
    },
    {
      "location": {
        "fileName": "ProcessManager.ts",
        "className": "Process",
        "methodName": "start",
        "stmtType": "AssignStmt",
        "block": 1,
        "index": 3
      }
    },
    {
      "location": {
        "fileName": "ProcessManager.ts",
        "className": "Process",
        "methodName": "terminate",
        "stmtType": "CallStmt",
        "block": 0,
        "index": 0
      }
    },
    {
      "type": "final",
      "location": {
        "fileName": "ProcessManager.ts",
        "className": "Process",
        "methodName": "terminate",
        "stmtType": "ReturnStmt",
        "block": 2,
        "index": 7
      }
    }
  ]
}
```

### 2. Tree Trace Format - `{ "target": {...}, "children": [...] }`

A single tree-like trace with hierarchical target structure.

**Example: `targets-tree.json`**
```json
{
  "target": {
    "type": "initial",
    "location": {
      "fileName": "ProcessManager.ts",
      "className": "ProcessManager",
      "methodName": "createProcess",
      "stmtType": "IfStmt",
      "block": 0,
      "index": 0
    }
  },
  "children": [
    {
      "target": {
        "location": {
          "fileName": "ProcessManager.ts",
          "className": "ProcessManager",
          "methodName": "createProcess",
          "stmtType": "AssignStmt",
          "block": 1,
          "index": 3
        }
      },
      "children": [
        {
          "target": {
            "type": "final",
            "location": {
              "fileName": "ProcessManager.ts",
              "className": "ProcessManager",
              "methodName": "createProcess",
              "stmtType": "ReturnStmt",
              "block": 2,
              "index": 7
            }
          }
        }
      ]
    }
  ]
}
```

### 3. Trace List Format - `[ {...} ]`

An array of traces that can contain both linear and tree traces.

**Example: `targets-mixed.json`**
```json
[
  {
    "targets": [
      {
        "type": "initial",
        "location": {
          "fileName": "UserService.ts",
          "className": "UserService",
          "methodName": "authenticate",
          "stmtType": "IfStmt",
          "block": 0,
          "index": 0
        }
      },
      {
        "location": {
          "fileName": "UserService.ts",
          "className": "UserService",
          "methodName": "validate",
          "stmtType": "CallStmt",
          "block": 0,
          "index": 0
        }
      },
      {
        "type": "final",
        "location": {
          "fileName": "UserService.ts",
          "className": "UserService",
          "methodName": "validate",
          "stmtType": "ReturnStmt",
          "block": 3,
          "index": 8
        }
      }
    ]
  },
  {
    "target": {
      "type": "initial",
      "location": {
        "fileName": "DatabaseManager.ts",
        "className": "DatabaseManager",
        "methodName": "connect",
        "stmtType": "IfStmt",
        "block": 0,
        "index": 0
      }
    },
    "children": [
      {
        "target": {
          "type": "final",
          "location": {
            "fileName": "DatabaseManager.ts",
            "className": "DatabaseManager",
            "methodName": "establishConnection",
            "stmtType": "ReturnStmt",
            "block": 0,
            "index": 2
          }
        }
      }
    ]
  }
]
```

## Target Types

Each target can have one of three types:

- **`initial`**: Entry point of a trace (only first target in linear traces)
- **`intermediate`**: Intermediate point in execution (default - can be omitted)
- **`final`**: End point of a trace (only last target in linear traces)

## Location Structure

Each target must specify a location with the following fields:

- **`fileName`**: The TypeScript source file name
- **`className`**: The class containing the method
- **`methodName`**: The method name
- **`stmtType`**: IR statement type (e.g., "IfStmt", "AssignStmt", "CallStmt", "ReturnStmt")
- **`block`**: Control flow block number
- **`index`**: Statement index within the block

## Common Statement Types

The `stmtType` field should contain the IR name of the statement at the specified coordinates:

- **`IfStmt`**: Conditional if statement
- **`AssignStmt`**: Assignment statement
- **`CallStmt`**: Method/function call statement
- **`ReturnStmt`**: Return statement
- **`WhileStmt`**: While loop statement
- **`ForStmt`**: For loop statement
- **`ThrowStmt`**: Throw/exception statement

## Linear Trace Rules

For linear traces (both standalone and within trace lists):

1. **Single Initial Point**: Only the very first target should have `"type": "initial"`
2. **Single Final Point**: Only the very last target should have `"type": "final"`  
3. **Omit Intermediate Types**: All targets between first and last can omit the `"type"` field entirely (defaults to `"intermediate"`)
4. **Sequential Execution**: Targets represent a single execution path through the code

## Automatic Format Detection

The tool automatically detects which format is being used based on the JSON structure:

- If the JSON is an array at the top level -> Trace List Format
- If the JSON object contains a `"targets"` field -> Linear Trace Format  
- If the JSON object contains a `"target"` field -> Tree Trace Format

No manual format specification is required!

## Usage Examples

```bash
# Using linear trace format
./reachability -p ./my-project -t targets.json

# Using hierarchical (tree-like) format
./reachability -p ./my-project -t targets-tree.json

# Using mixed array format
./reachability -p ./my-project -t targets-mixed.json

# Auto-generate targets (no file needed)
./reachability -p ./my-project
```

## Legacy Format Support

The tool maintains backward compatibility with legacy target file formats using regex-based parsing as a fallback option.
