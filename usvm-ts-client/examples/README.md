# USVM TypeScript Client Examples

This directory contains comprehensive examples demonstrating how to use the USVM TypeScript Reachability Client.

## 📁 Structure

```
examples/
├── sample-project/          # Self-contained TypeScript project for testing
│   ├── src/
│   │   ├── index.ts        # Main entry point
│   │   ├── main.ts         # Application logic
│   │   ├── auth.ts         # Authentication service
│   │   └── calculator.ts   # Calculator class with operations
│   ├── package.json        # Project configuration
│   └── tsconfig.json       # TypeScript configuration
├── targets/                 # Pre-defined target configurations
│   ├── custom-targets.json      # Custom target definitions (tree format)
│   └── execution-path.json      # Execution path targets (hierarchical)
├── usage-examples.ts        # All example functions
└── README.md               # This file
```

## 🎯 Target Files Structure

All target files use the correct Kotlin DTO format:

### Tree Trace Format

```json
{
  "root": {
    "target": {
      "type": "initial|intermediate|final",
      "location": {
        "fileName": "src/file.ts",
        "className": "ClassName",
        "methodName": "methodName"
      }
    },
    "children": [
      // Additional target tree nodes...
    ]
  }
}
```

### Key Points

- **No duplicate files**: Previously had both `custom-targets.json` and `generated-custom-targets.json`
- **Unified approach**: Pre-defined files for common scenarios, dynamic generation for custom needs
- **Correct structure**: All JSON files match the Kotlin DTO requirements

## 📋 Available Examples

### 1. Basic Analysis (`basicAnalysis()`)

- Uses automatic target discovery
- Analyzes all public methods
- No custom target file needed

### 2. Custom Targets Analysis (`customTargetsAnalysis()`)

- Uses pre-defined `custom-targets.json`
- Demonstrates tree trace format with authentication flow

### 3. Execution Path Analysis (`executionPathAnalysis()`)

- Uses pre-defined `execution-path.json`
- Shows hierarchical application startup flow

### 4. Advanced Configuration (`advancedAnalysis()`)

- Demonstrates various configuration options
- Uses method filters and custom solver settings

### 5. Batch Analysis (`batchAnalysis()`)

- Runs multiple configurations in sequence
- Uses pre-defined target files for comparison

### 6. Dynamic Target Generation (`dynamicTargetGeneration()`)

- Shows how to use TargetBuilder programmatically
- Creates targets at runtime and cleans up afterward

## 🚀 Running Examples

```bash
# Run all examples
npm run dev

# Or run specific example functions programmatically
import { basicAnalysis } from './usage-examples';
await basicAnalysis();
```

## ✅ Unified Sample Data

- **No duplication**: Removed generated- prefixed files
- **Clear separation**: Pre-defined samples vs. runtime generation
- **Consistent format**: All files follow correct DTO structure
- **Self-contained**: No external dependencies on USVM examples
