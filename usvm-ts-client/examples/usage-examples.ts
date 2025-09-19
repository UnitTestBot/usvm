import { AnalysisMode, ExecutionMode, SolverType, TargetBuilder, TargetTypeDto, USVMReachabilityClient } from '../src';
import * as path from 'path';

/**
 * Auto-determine USVM root directory
 * This function looks for the USVM project root by searching upwards from the current directory
 */
function findUSVMRoot(): string {
    let currentDir = __dirname;

    // Start from the examples directory and go up to find USVM root
    // USVM root should contain gradlew file
    while (currentDir !== path.dirname(currentDir)) {
        try {
            const gradlewPath = path.join(currentDir, 'gradlew');
            const fs = require('fs');
            if (fs.existsSync(gradlewPath)) {
                // Also check if it contains usvm-ts directory to be sure
                const usvmTsPath = path.join(currentDir, 'usvm-ts');
                if (fs.existsSync(usvmTsPath)) {
                    return currentDir;
                }
            }
        } catch (error) {
            // Continue searching
        }
        currentDir = path.dirname(currentDir);
    }

    // Fallback: assume we're in '.../usvm/usvm-ts-client' which is inside usvm root.
    const fallbackPath = path.resolve(__dirname, '..', '..');
    console.warn(`⚠️ Could not auto-detect USVM root, using fallback: ${fallbackPath}`);
    return fallbackPath;
}

// Auto-determined USVM root
const USVM_ROOT = findUSVMRoot();
console.log(`🔍 Using USVM root: ${USVM_ROOT}`);

// Path to our self-contained sample project
const SAMPLE_PROJECT_PATH = path.join(__dirname, 'sample-project');
const TARGETS_DIR = path.join(__dirname, 'targets');

// Example 1: Basic Analysis
async function basicAnalysis() {
    console.log('🔍 Running basic reachability analysis...');

    const client = new USVMReachabilityClient({
        usvmRoot: USVM_ROOT,
        autoBuild: true,
    });

    try {
        const report = await client.analyze({
            projectPath: SAMPLE_PROJECT_PATH,
            analysisMode: AnalysisMode.PUBLIC_METHODS,
            executionMode: ExecutionMode.DIST,
            outputDir: path.join(__dirname, 'results', 'basic'),
        });

        console.log(`✅ Analysis complete!`);
        console.log(`📊 Results: ${report.summary.reachableTargets}/${report.summary.totalTargets} targets reachable`);
        console.log(`⏱️ Total time: ${report.totalTime}ms`);

        // Print detailed results
        report.results.forEach(result => {
            const status = result.reachable ? '✅ REACHABLE' : '❌ UNREACHABLE';
            console.log(`  ${result.targetId}: ${status} (${result.executionTime}ms)`);
        });

    } catch (error) {
        console.error('❌ Analysis failed:', error.message);
    }
}

// Example 2: Custom Targets Configuration
async function customTargetsAnalysis() {
    console.log('🎯 Running analysis with custom targets...');

    const client = new USVMReachabilityClient({
        usvmRoot: USVM_ROOT,
    });

    // Use the pre-defined custom targets file directly
    const targetsFile = path.join(TARGETS_DIR, 'custom-targets.json');
    console.log(`📋 Using targets file: ${targetsFile}`);

    try {
        const report = await client.analyze({
            projectPath: SAMPLE_PROJECT_PATH,
            targetsFile,
            analysisMode: AnalysisMode.ALL_METHODS,
            verbose: true,
            includeStatements: true,
            outputDir: path.join(__dirname, 'results', 'custom-targets'),
        });

        console.log(`✅ Custom targets analysis complete!`);
        console.log(`📋 Analyzed ${report.summary.totalTargets} custom targets`);

    } catch (error) {
        console.error('❌ Custom targets analysis failed:', error.message);
    }
}

// Example 3: Execution Path Analysis
async function executionPathAnalysis() {
    console.log('🛤️ Running execution path analysis...');

    const client = new USVMReachabilityClient({
        usvmRoot: USVM_ROOT,
    });

    // Use the pre-defined execution path targets file directly
    const targetsFile = path.join(TARGETS_DIR, 'execution-path.json');
    console.log(`📋 Using execution path targets file: ${targetsFile}`);

    try {
        const report = await client.analyze({
            projectPath: SAMPLE_PROJECT_PATH,
            targetsFile,
            solverType: SolverType.Z3,
            timeout: 600,
            stepsLimit: 5000,
            outputDir: path.join(__dirname, 'results', 'execution-path'),
        });

        console.log(`✅ Execution path analysis complete!`);
        console.log(`🛤️ Path reachability verified through ${report.summary.totalTargets} steps`);

    } catch (error) {
        console.error('❌ Execution path analysis failed:', error.message);
    }
}

// Example 4: Advanced Configuration
async function advancedAnalysis() {
    console.log('⚙️ Running advanced analysis with custom configuration...');

    const client = new USVMReachabilityClient({
        usvmRoot: USVM_ROOT,
        defaultConfig: {
            solverType: SolverType.YICES,
            timeout: 300,
            verbose: false,
        },
    });

    try {
        const report = await client.analyze({
            projectPath: SAMPLE_PROJECT_PATH,
            analysisMode: AnalysisMode.ALL_METHODS,
            methodFilters: ['process*', '*Handler', 'validate*', 'auth*'],
            solverType: SolverType.Z3, // Override default
            timeout: 900, // Override default
            stepsLimit: 7500,
            verbose: true,
            includeStatements: true,
            executionMode: ExecutionMode.DIST,
            outputDir: path.join(__dirname, 'results', 'advanced'),
        });

        console.log(`✅ Advanced analysis complete!`);

        // Generate summary report
        const summary = {
            project: 'Sample TypeScript Project',
            timestamp: report.timestamp,
            mode: report.analysisMode,
            solver: report.solverType,
            duration: `${report.totalTime}ms`,
            targets: {
                total: report.summary.totalTargets,
                reachable: report.summary.reachableTargets,
                unreachable: report.summary.unreachableTargets,
                failed: report.summary.failedTargets,
                reachabilityRate: `${Math.round((report.summary.reachableTargets / report.summary.totalTargets) * 100)}%`,
            },
        };

        console.log('📊 Analysis Summary:', JSON.stringify(summary, null, 2));

    } catch (error) {
        console.error('❌ Advanced analysis failed:', error.message);
    }
}

// Example 5: Batch Analysis (using pre-defined targets)
async function batchAnalysis() {
    console.log('📦 Running batch analysis with different configurations...');

    const client = new USVMReachabilityClient({
        usvmRoot: USVM_ROOT,
    });

    const analysisConfigs = [
        {
            name: 'Quick Analysis',
            config: {
                analysisMode: AnalysisMode.PUBLIC_METHODS,
                timeout: 60,
                solverType: SolverType.YICES,
            },
        },
        {
            name: 'Thorough Analysis',
            config: {
                analysisMode: AnalysisMode.ALL_METHODS,
                timeout: 300,
                solverType: SolverType.Z3,
                stepsLimit: 10000,
            },
        },
        {
            name: 'Custom Targets',
            config: {
                targetsFile: path.join(TARGETS_DIR, 'custom-targets.json'),
                timeout: 180,
                includeStatements: true,
            },
        },
    ];

    const results = [];

    for (const { name, config } of analysisConfigs) {
        console.log(`🔍 Running ${name}...`);

        try {
            const report = await client.analyze({
                projectPath: SAMPLE_PROJECT_PATH,
                outputDir: path.join(__dirname, 'results', 'batch', name.toLowerCase().replace(' ', '-')),
                ...config,
            });

            results.push({
                configuration: name,
                success: true,
                reachabilityRate: Math.round((report.summary.reachableTargets / report.summary.totalTargets) * 100),
                totalTargets: report.summary.totalTargets,
                duration: report.totalTime,
            });

            console.log(`  ✅ ${name}: ${report.summary.reachableTargets}/${report.summary.totalTargets} reachable`);

        } catch (error) {
            results.push({
                configuration: name,
                success: false,
                error: error.message,
            });

            console.log(`  ❌ ${name}: Analysis failed - ${error.message}`);
        }
    }

    console.log('\n📊 Batch Analysis Summary:');
    console.table(results);
}

// Example 6: Dynamic Target Generation (demonstrates TargetBuilder usage)
async function dynamicTargetGeneration() {
    console.log('🔧 Running analysis with dynamically generated targets...');

    const client = new USVMReachabilityClient({
        usvmRoot: USVM_ROOT,
    });

    // Build custom targets using TargetBuilder for specific analysis
    const dynamicTargets = new TargetBuilder()
        .addMethodAsTree('Calculator', 'add', 'src/calculator.ts', {
            type: TargetTypeDto.INITIAL,
        })
        .addMethodAsTree('Calculator', 'multiply', 'src/calculator.ts', {
            type: TargetTypeDto.INTERMEDIATE,
        })
        .addMethodAsTree('Calculator', 'divide', 'src/calculator.ts', {
            type: TargetTypeDto.FINAL,
        })
        .build();

    // Generate targets file for this specific analysis
    const dynamicTargetsFile = path.join(TARGETS_DIR, 'runtime-generated-targets.json');
    await client.createTargetsFile(dynamicTargets, dynamicTargetsFile);
    console.log(`📋 Generated dynamic targets file: ${dynamicTargetsFile}`);

    try {
        const report = await client.analyze({
            projectPath: SAMPLE_PROJECT_PATH,
            targetsFile: dynamicTargetsFile,
            analysisMode: AnalysisMode.ALL_METHODS,
            verbose: true,
            outputDir: path.join(__dirname, 'results', 'dynamic-targets'),
        });

        console.log(`✅ Dynamic targets analysis complete!`);
        console.log(`🔧 Generated and analyzed ${report.summary.totalTargets} dynamic targets`);

        // Clean up the generated file after use
        await import('fs').then(fs => fs.promises.unlink(dynamicTargetsFile));
        console.log(`🧹 Cleaned up generated targets file`);

    } catch (error) {
        console.error('❌ Dynamic targets analysis failed:', error.message);
    }
}

// Main execution
async function runExamples() {
    console.log('🚀 USVM Reachability Client Examples\n');

    try {
        await basicAnalysis();
        console.log('\n' + '='.repeat(50) + '\n');

        // await customTargetsAnalysis();
        // console.log('\n' + '='.repeat(50) + '\n');
        //
        // await executionPathAnalysis();
        // console.log('\n' + '='.repeat(50) + '\n');
        //
        // await advancedAnalysis();
        // console.log('\n' + '='.repeat(50) + '\n');
        //
        // await batchAnalysis();
        // console.log('\n' + '='.repeat(50) + '\n');
        //
        // await dynamicTargetGeneration();

    } catch (error) {
        console.error('💥 Example execution failed:', error);
    }
}

// Run examples if this file is executed directly
if (require.main === module) {
    runExamples();
}

export {
    basicAnalysis,
    customTargetsAnalysis,
    executionPathAnalysis,
    advancedAnalysis,
    batchAnalysis,
    dynamicTargetGeneration,
};
