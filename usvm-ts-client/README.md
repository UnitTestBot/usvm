# USVM Reachability Client

A TypeScript client library for interacting with the USVM reachability analysis CLI from external projects.

## Installation

```bash
npm install @usvm/reachability-client
```

## Quick Start

```typescript
import { USVMReachabilityClient, TargetBuilder, AnalysisMode } from '@usvm/reachability-client';

// Initialize the client
const client = new USVMReachabilityClient({
  usvmRoot: '/path/to/usvm',
  autoBuild: true
});

// Run analysis
const report = await client.analyze({
  projectPath: './my-typescript-project',
  analysisMode: AnalysisMode.PUBLIC_METHODS,
  outputDir: './analysis-results'
});

console.log(`Analysis complete: ${report.summary.reachableTargets}/${report.summary.totalTargets} targets reachable`);
```

## Features

- 🎯 **Type-safe API** - Full TypeScript support with Zod validation
- 🔧 **Flexible Configuration** - Support for all USVM CLI options
- 📊 **Rich Results** - Detailed analysis reports with statistics
- 🎨 **Target Builder** - Utilities for creating complex target configurations
- 🔄 **Auto-build** - Automatic USVM binary building when needed
- 📝 **Comprehensive Logging** - Detailed error messages and progress tracking

## API Reference

### USVMReachabilityClient

The main client class for running reachability analysis.

#### Constructor Options

```typescript
interface USVMClientOptions {
    usvmRoot: string;           // Path to USVM project root
    defaultConfig?: Partial<AnalysisConfig>;
    autoBuild?: boolean;        // Auto-build binaries if missing (default: true)
}
```

#### Methods

- `analyze(config)` - Run reachability analysis
- `createTargetsFile(targets, filePath)` - Save targets configuration to file
- `loadTargetsFile(filePath)` - Load targets from file
- `checkBinaries(executionMode?)` - Check if USVM binaries are available
- `buildBinaries(executionMode?)` - Build USVM binaries

### TargetBuilder

Utility class for building target configurations.

```typescript
const targets = new TargetBuilder()
    .addMethod('method1', 'src/app.ts', 10, 'calculateTotal')
    .addStatement('stmt1', 'src/utils.ts', 25)
    .addLinearSequence([
        { id: 'step1', type: TargetType.METHOD, location: { file: 'src/a.ts', line: 1 }, methodName: 'start' },
        { id: 'step2', type: TargetType.STATEMENT, location: { file: 'src/b.ts', line: 15 } }
    ])
    .build();
```

### Configuration Options

```typescript
interface AnalysisConfig {
    projectPath: string;              // Required: Project directory
    targetsFile?: string;             // Optional: Custom targets file
    outputDir: string;                // Output directory (default: './reachability-results')
    analysisMode: AnalysisMode;       // ALL_METHODS | PUBLIC_METHODS | ENTRY_POINTS
    methodFilters: string[];          // Method name patterns
    solverType: SolverType;           // YICES | Z3 | CVC5
    timeout: number;                  // Analysis timeout in seconds
    stepsLimit: number;               // Max steps limit
    verbose: boolean;                 // Verbose output
    includeStatements: boolean;       // Include statement details
    executionMode: ExecutionMode;     // shadow | dist
}
```

## Examples

### Basic Analysis

```typescript
import { USVMReachabilityClient, AnalysisMode } from '@usvm/reachability-client';

const client = new USVMReachabilityClient({
    usvmRoot: '/path/to/usvm'
});

const report = await client.analyze({
    projectPath: './my-project',
    analysisMode: AnalysisMode.PUBLIC_METHODS,
    verbose: true
});

// Process results
for (const result of report.results) {
    console.log(`Target ${result.targetId}: ${result.reachable ? 'REACHABLE' : 'UNREACHABLE'}`);
}
```

### Custom Targets

```typescript
import { TargetBuilder, TargetType } from '@usvm/reachability-client';

// Build custom targets
const targets = new TargetBuilder()
    .addMethod('login', 'src/auth.ts', 15, 'authenticate', {
        description: 'User authentication method'
    })
    .addStatement('validation', 'src/auth.ts', 25, {
        description: 'Input validation check'
    })
    .build();

// Save targets to file
await client.createTargetsFile(targets, './custom-targets.json');

// Use in analysis
const report = await client.analyze({
    projectPath: './my-project',
    targetsFile: './custom-targets.json'
});
```

### Execution Path Analysis

```typescript
// Analyze a specific execution path
const executionPath = TargetPresets.executionPath([
    { id: 'start', file: 'src/main.ts', line: 1, type: TargetType.METHOD, methodName: 'main' },
    { id: 'init', file: 'src/app.ts', line: 10, type: TargetType.METHOD, methodName: 'initialize' },
    { id: 'process', file: 'src/processor.ts', line: 50, type: TargetType.STATEMENT },
    { id: 'finish', file: 'src/main.ts', line: 100, type: TargetType.METHOD, methodName: 'cleanup' }
]);

const report = await client.analyze({
    projectPath: './my-project',
    targetsFile: await client.createTargetsFile(executionPath.build(), './execution-path.json')
});
```

### Advanced Configuration

```typescript
const report = await client.analyze({
    projectPath: './my-project',
    analysisMode: AnalysisMode.ALL_METHODS,
    methodFilters: ['process*', '*Handler'],
    solverType: SolverType.Z3,
    timeout: 600,
    stepsLimit: 5000,
    verbose: true,
    includeStatements: true,
    executionMode: ExecutionMode.DIST
});
```

## Error Handling

The library provides specific error types for different failure scenarios:

```typescript
import { AnalysisError, ConfigurationError, ExecutionError } from '@usvm/reachability-client';

try {
    const report = await client.analyze(config);
} catch (error) {
    if (error instanceof ConfigurationError) {
        console.error('Configuration error:', error.message);
    } else if (error instanceof ExecutionError) {
        console.error('Execution failed:', error.message, 'Exit code:', error.exitCode);
    } else if (error instanceof AnalysisError) {
        console.error('Analysis failed for target:', error.targetId, error.message);
    }
}
```

## CLI Integration

The library uses the USVM reachability CLI script (
`examples/reachability/reachability-cli.sh`) under the hood. You can use either execution mode:

- **Shadow JAR mode** (default): Uses `java -cp usvm-ts-all.jar`
- **Distribution mode**: Uses Gradle distribution binary

```typescript
// Use distribution mode for cleaner execution
const report = await client.analyze({
    projectPath: './my-project',
    executionMode: ExecutionMode.DIST
});
```

## License

Apache 2.0 - See LICENSE file for details.
