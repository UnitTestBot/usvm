# USVM TypeScript Reachability Analysis

A powerful tool for analyzing code reachability in TypeScript projects, supporting both source code and pre-compiled IR analysis.

## Quick Start

### Basic Commands

```bash
# Analyze TypeScript source project (traditional)
./reachability-cli.sh -p ./my-project

# Analyze pre-compiled IR files (fast, no setup)
./reachability-cli.sh -i ./project-ir --sdk ./stdlib-ir

# With custom targets and verbose output
./reachability-cli.sh -i ./project-ir -t targets.json -v
```

### Key Components

- **📁 Input Sources**: TypeScript source code OR pre-compiled IR JSON files
- **📋 Target Files**: JSON specifications of code points to analyze (optional)
- **📊 Analysis Reports**: Summary and detailed reachability results
- **🔧 CLI Options**: Flexible configuration for different analysis needs

### Essential Options

| Option | Purpose | Example |
|--------|---------|---------|
| `-p, --project` | Source code analysis | `-p ./src` |
| `-i, --input` | IR file analysis | `-i ./project-ir` |
| `--sdk` | Add SDK libraries | `--sdk ./stdlib-ir` |
| `-t, --targets` | Custom target file | `-t targets.json` |
| `-o, --output` | Results directory | `-o ./results` |

## Analysis Modes

### 🚀 IR Mode (Recommended)

**Fast analysis using pre-compiled IR files - no TypeScript setup required**

**Benefits:**

- ✅ No complex TypeScript environment setup
- ⚡ Faster startup and analysis
- 🔄 Reproducible results across environments
- 📚 Flexible SDK management
- 🤖 Perfect for CI/CD pipelines

**Usage:**

```bash
./reachability-cli.sh -i ./project-ir --sdk ./dom-ir --sdk ./node-ir
```

### 📝 Source Mode (Traditional)

**Direct analysis of TypeScript source files with auto-conversion**

**When to use:**

- Working with live TypeScript codebases
- No pre-compiled IR files available
- Development and debugging scenarios

**Usage:**

```bash
./reachability-cli.sh -p ./my-typescript-project
```

## Target File Formats

The tool supports flexible target specification through JSON files. **Targets are optional** - the tool can auto-generate them if none are provided.

### Format Overview

| Format | Structure | Use Case |
|--------|-----------|----------|
| **Linear** | `{"targets": [...]}` | Sequential execution paths |
| **Tree** | `{"target": {...}, "children": [...]}` | Hierarchical target structure |
| **Mixed** | `[{...}, {...}]` | Multiple traces of any type |

### Quick Examples

**Simple linear trace:**

```json
{
  "targets": [
    {
      "type": "initial",
      "location": {
        "className": "UserService",
        "methodName": "authenticate",
        "stmtType": "IfStmt"
      }
    },
    {
      "type": "final",
      "location": {
        "className": "UserService",
        "methodName": "validate",
        "stmtType": "ReturnStmt"
      }
    }
  ]
}
```

## Advanced Usage

### Common Workflows

```bash
# Full analysis with all options
./reachability-cli.sh -i ./project-ir \
  --sdk ./stdlib-ir --sdk ./dom-ir \
  -t custom-targets.json \
  -o ./detailed-results \
  --mode ALL_METHODS \
  --solver Z3 \
  --timeout 600 \
  -v --include-statements

# Quick public method analysis
./reachability-cli.sh -p ./src --mode PUBLIC_METHODS

# Filter specific methods
./reachability-cli.sh -i ./ir --method "authenticate" --method "validate"
```

### Performance Options

| Option | Default | Description |
|--------|---------|-------------|
| `--timeout` | 300 | Analysis timeout in seconds |
| `--steps` | 3500 | Max steps from last covered statement |
| `--solver` | YICES | SMT solver (YICES, Z3, CVC5) |
| `--mode` | PUBLIC_METHODS | Analysis scope |

---

## Detailed Reference

### Complete CLI Options

**Input Options:**

- `-p, --project PATH` - TypeScript project directory (source mode)
- `-i, --input PATH` - IR JSON directory (can use multiple times)
- `--sdk PATH` - SDK IR directory (can use multiple times)

**Analysis Options:**

- `-t, --targets FILE` - JSON target definitions file
- `-m, --mode MODE` - Analysis scope: ALL_METHODS, PUBLIC_METHODS, ENTRY_POINTS
- `--method PATTERN` - Filter methods by name pattern (can use multiple times)

**Performance Options:**

- `--solver SOLVER` - SMT solver: YICES, Z3, CVC5
- `--timeout SECONDS` - Analysis timeout (default: 300)
- `--steps LIMIT` - Max steps limit (default: 3500)

**Output Options:**

- `-o, --output DIR` - Output directory (default: ./reachability-results)
- `-v, --verbose` - Enable verbose output
- `--include-statements` - Include statement details in reports
- `--exec-mode MODE` - Execution mode: shadow, dist (default: shadow)

### Target File Format Details

#### 1. Linear Trace Format

Sequential execution path with entry and exit points:

<details>
<summary>Linear Trace Example</summary>

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

</details>

#### 2. Tree Trace Format

Hierarchical target structure with parent-child relationships:

<details>
<summary>Tree Trace Example</summary>

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

</details>

#### 3. Mixed Trace List Format

Array containing multiple traces (can mix linear and tree formats):

<details>
<summary>Mixed Trace List Example</summary>

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
          "stmtType": "IfStmt"
        }
      },
      {
        "type": "final",
        "location": {
          "fileName": "UserService.ts",
          "className": "UserService",
          "methodName": "validate",
          "stmtType": "ReturnStmt"
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
        "stmtType": "IfStmt"
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
            "stmtType": "ReturnStmt"
          }
        }
      }
    ]
  }
]
```

</details>

### Location Structure Reference

Each target location must specify:

**Required Fields:**

- `fileName` - TypeScript source file name
- `className` - Class containing the method
- `methodName` - Method name

**Optional Fields:**

- `stmtType` - IR statement type. Currently does nothing, and can be safely omitted.
- `block` - Control flow block number (id)
- `index` - Statement index (0-based) within block

### Target Types

- **`initial`** - Entry point (first target in linear traces)
- **`intermediate`** - Intermediate point (default, can be omitted)
- **`final`** - End point (last target in linear traces)

### Common Statement Types

- `IfStmt` - Conditional statements
- `AssignStmt` - Assignment operations
- `CallStmt` - Method/function calls
- `ReturnStmt` - Return statements
- `WhileStmt` - While loops
- `ForStmt` - For loops
- `ThrowStmt` - Exception throwing

### Input Validation Rules

✅ **Valid combinations:**

- `--project ./src` (source mode only)
- `--input ./ir` (IR mode only)
- `--input ./ir --sdk ./stdlib` (IR with SDK)

❌ **Invalid combinations:**

- `--project ./src --input ./ir` (conflicting modes)
- No input specified (missing input)

### Automatic Format Detection

The tool automatically detects target file format:

- Array at top level → Mixed Trace List
- Object with `"targets"` → Linear Trace
- Object with `"target"` → Tree Trace

No manual format specification needed!
