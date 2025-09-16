# 🎯 TypeScript Reachability Analysis CLI

A powerful command-line tool for performing sophisticated reachability analysis on TypeScript projects using the USVM framework.

## Features

- ✅ **REACHABLE**: Paths confirmed to be executable with complete execution traces
- ❌ **UNREACHABLE**: Paths confirmed to be impossible under any conditions
- ❓ **UNKNOWN**: Paths that could not be determined due to timeout, errors, or approximations

## Sample Project

The included sample project demonstrates a **System Applications** simulator with:

- **ProcessManager.ts**: Process management with state transitions (CREATED → READY → RUNNING → BLOCKED/TERMINATED)
- **MemoryManager.ts**: Memory allocation, deallocation, compaction, and defragmentation
- **FileSystem.ts**: Basic file system operations and navigation

These examples use only SMT-solver friendly constructs:

- Integer operations (no floating-point or modulo operations)
- Array operations (length, indexing, push/pop)
- Object field access and updates
- Conditional logic and control flow
- Function calls without complex inheritance

## Installation & Setup

1. Ensure you have the USVM project built:

```bash
cd /path/to/usvm
./gradlew build
```

2. Build the shadow JAR for faster execution:

```bash
./gradlew :usvm-ts:shadowJar
```

3. The CLI is ready to use via the wrapper script or direct JAR execution.

## Usage

### Quick Start with Sample Project

```bash
# Analyze the sample system applications project
./reachability-cli.sh -p ./sample-project

# Analyze specific process management methods
./reachability-cli.sh -p ./sample-project --method Process --include-statements

# Focus on memory management operations with verbose output
./reachability-cli.sh -p ./sample-project --method MemoryManager -v
```

### Analyze Your Own Project

```bash
# Analyze any TypeScript project with default settings
./reachability-cli.sh -p ./my-typescript-project

# Use custom targets and verbose output  
./reachability-cli.sh -p ./my-project -t ./targets.json -v
```

### Direct JAR Execution (Faster)

```bash
java -jar usvm-ts/build/libs/usvm-ts-reachability.jar --project ./sample-project --verbose
```

## Command Line Options

### Required

- `-p, --project PATH` - Path to TypeScript project directory

### Optional Analysis Configuration

- `-t, --targets FILE` - JSON file with target definitions (uses auto-generated targets if not provided)
- `-m, --mode MODE` - Analysis scope: `ALL_METHODS`, `PUBLIC_METHODS`, `ENTRY_POINTS` (default: PUBLIC_METHODS)
- `--method PATTERN` - Filter methods by name pattern

### Solver & Performance Options

- `--solver SOLVER` - SMT solver: `YICES`, `Z3`, `CVC5` (default: YICES)
- `--timeout SECONDS` - Overall analysis timeout (default: 300)
- `--steps LIMIT` - Maximum steps from last covered statement (default: 3500)
- `--strategy STRATEGY` - Path selection strategy (default: TARGETED)

### Output Options

- `-o, --output DIR` - Output directory (default: ./reachability-results)
- `-v, --verbose` - Enable verbose logging
- `--include-statements` - Include detailed statement information in reports

## Target Definitions Format

Create a JSON file to specify custom reachability targets:

```json
[
  {
    "type": "initial",
    "method": "Calculator.add",
    "statement": 0
  },
  {
    "type": "intermediate",
    "method": "Calculator.add",
    "statement": 1
  },
  {
    "type": "final",
    "method": "Calculator.add",
    "statement": 4
  }
]
```

### Target Types

- **initial**: Entry point of the analysis path
- **intermediate**: Checkpoint along the execution path
- **final**: Target endpoint to reach

### Hierarchical Structure

Targets are automatically organized into hierarchical chains per method:
`Initial Point → Intermediate Point(s) → Final Point`

## Output Reports

The tool generates comprehensive analysis reports:

### Summary Report (`reachability_summary.txt`)

- Overall statistics and execution time
- Reachability status counts
- Per-target analysis results

### Detailed Report (`reachability_detailed.md`)

- Markdown-formatted comprehensive analysis
- Method-by-method breakdown
- Execution paths with statements (when `--include-statements` is used)

## Example Project Structure

```
my-typescript-project/
├── Calculator.ts          # TypeScript source files
├── MathUtils.ts          
└── ...

targets.jsonc             # Optional target definitions
reachability-results/     # Generated output directory
├── reachability_summary.txt
└── reachability_detailed.md
```

## Reachability Analysis Results

### Status Categories

1. **REACHABLE** ✅
    - Path is definitely executable
    - Includes complete execution trace with all statements
    - Shows path conditions and variable states

2. **UNREACHABLE** ❌
    - Path is proven to be impossible
    - No valid execution can reach this target
    - Useful for dead code detection

3. **UNKNOWN** ❓
    - Analysis couldn't determine reachability
    - Common causes: timeout, solver limitations, complex approximations
    - Requires further investigation or different analysis parameters

### Execution Paths

For REACHABLE targets, the tool provides:

- Complete statement sequence from execution start to target
- All intermediate statements traversed
- Path conditions (when available)
- Variable state information

## Example Usage Scenarios

### 1. Dead Code Detection

```bash
./reachability-cli.sh -p ./src --mode ALL_METHODS -v
```

### 2. Critical Path Analysis

```bash
./reachability-cli.sh -p ./src -t ./critical-paths.json --include-statements
```

### 3. Method-Specific Analysis

```bash  
./reachability-cli.sh -p ./src --method "authenticate" --solver Z3
```

### 4. High-Precision Analysis

```bash
./reachability-cli.sh -p ./src --timeout 600 --steps 10000 --solver CVC5
```

## Implementation Details

The CLI leverages the USVM framework's powerful reachability analysis capabilities:

- **Project Loading**: Uses `loadEtsFileAutoConvert()` for automatic TypeScript to IR conversion
- **Target Creation**: Builds hierarchical target structures using `TsReachabilityTarget` classes
- **Analysis Engine**: Utilizes `TsMachine` with configurable solver backends
- **Path Extraction**: Extracts execution paths from `TsState.pathNode.allStatements`

## Troubleshooting

### Common Issues

1. **"No TypeScript files found"**
    - Ensure the project path contains `.ts` or `.js` files
    - Check file permissions

2. **"Analysis timeout"**
    - Increase `--timeout` value
    - Reduce `--steps` limit
    - Try different `--solver`

3. **"Target not found"**
    - Verify method names in targets.jsonc match exactly
    - Check statement indices are within bounds

### Debug Mode

Enable verbose output with `-v` to see detailed analysis progress and any warnings.

## Contributing

The CLI is part of the USVM project. To extend functionality:

1. Add new options to `ReachabilityAnalyzer` class
2. Update the wrapper script with corresponding parameters
3. Add appropriate documentation

## License

This tool is part of the USVM project and follows the same licensing terms.
