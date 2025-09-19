import { spawn } from 'child_process';
import { promises as fs } from 'fs';
import * as path from 'path';
import {
    AnalysisConfig,
    AnalysisConfigSchema,
    AnalysisReport,
    AnalysisReportSchema,
    ConfigurationError,
    ExecutionError,
    ExecutionMode,
    TargetsContainerDto,
} from './types';

export interface USVMClientOptions {
    /** Path to USVM project root (where gradlew is located) */
    usvmRoot: string;
    /** Default configuration for analyses */
    defaultConfig?: Partial<AnalysisConfig>;
    /** Whether to automatically build USVM if binaries are missing */
    autoBuild?: boolean;
}

export class USVMReachabilityClient {
    private readonly usvmRoot: string;
    private readonly defaultConfig: Partial<AnalysisConfig>;
    private readonly autoBuild: boolean;

    constructor(options: USVMClientOptions) {
        this.usvmRoot = path.resolve(options.usvmRoot);
        this.defaultConfig = options.defaultConfig || {};
        this.autoBuild = options.autoBuild ?? true;
    }

    /**
     * Run reachability analysis with the specified configuration
     */
    async analyze(config: Partial<AnalysisConfig>): Promise<AnalysisReport> {
        // Merge with default config and validate
        const mergedConfig = { ...this.defaultConfig, ...config };
        const validatedConfig = AnalysisConfigSchema.parse(mergedConfig);

        // Ensure output directory exists
        await fs.mkdir(validatedConfig.outputDir, { recursive: true });

        // Build CLI arguments
        const args = await this.buildCliArguments(validatedConfig);

        // Execute analysis
        const result = await this.executeAnalysis(args, validatedConfig);

        // Parse and return results
        return this.parseResults(validatedConfig.outputDir);
    }

    /**
     * Create targets configuration and save to file
     */
    async createTargetsFile(
        targets: TargetsContainerDto,
        filePath: string,
    ): Promise<void> {
        const targetJson = JSON.stringify(targets, null, 2);
        await fs.writeFile(filePath, targetJson, 'utf8');
    }

    /**
     * Load existing targets configuration from file
     */
    async loadTargetsFile(filePath: string): Promise<TargetsContainerDto> {
        const content = await fs.readFile(filePath, 'utf8');
        return JSON.parse(content);
    }

    /**
     * Check if USVM binaries are available
     */
    async checkBinaries(executionMode: ExecutionMode = ExecutionMode.SHADOW): Promise<boolean> {
        try {
            const binaryPath = this.getBinaryPath(executionMode);
            await fs.access(binaryPath);
            return true;
        } catch {
            return false;
        }
    }

    /**
     * Build USVM binaries if needed
     */
    async buildBinaries(executionMode: ExecutionMode = ExecutionMode.SHADOW): Promise<void> {
        const gradleTask = executionMode === ExecutionMode.DIST ? 'installDist' : 'shadowJar';
        const task = `:usvm-ts:${gradleTask}`;

        console.log(`🔨 Building USVM binaries with task: ${task}`);

        return new Promise((resolve, reject) => {
            const gradlew = process.platform === 'win32' ? 'gradlew.bat' : './gradlew';
            const buildProcess = spawn(gradlew, [task], {
                cwd: this.usvmRoot,
                stdio: ['ignore', 'pipe', 'pipe'],
            });

            let stdout = '';
            let stderr = '';

            buildProcess.stdout?.on('data', (data) => {
                stdout += data.toString();
            });

            buildProcess.stderr?.on('data', (data) => {
                stderr += data.toString();
            });

            buildProcess.on('close', (code) => {
                if (code === 0) {
                    console.log('✅ Build completed successfully');
                    resolve();
                } else {
                    reject(new ExecutionError(`Build failed with exit code ${code}:\n${stderr}`, code ?? undefined));
                }
            });

            buildProcess.on('error', (error) => {
                reject(new ExecutionError(`Failed to start build process: ${error.message}`));
            });
        });
    }

    private async buildCliArguments(config: AnalysisConfig): Promise<string[]> {
        const args: string[] = [];

        // Required arguments
        args.push('--project', config.projectPath);
        args.push('--output', config.outputDir);

        // Optional arguments
        args.push('--mode', config.analysisMode);
        args.push('--solver', config.solverType);
        args.push('--timeout', config.timeout.toString());
        args.push('--steps', config.stepsLimit.toString());

        if (config.targetsFile) {
            // Validate targets file exists
            try {
                await fs.access(config.targetsFile);
                args.push('--targets', config.targetsFile);
            } catch {
                throw new ConfigurationError(`Targets file not found: ${config.targetsFile}`);
            }
        }

        // Method filters
        for (const filter of config.methodFilters) {
            args.push('--method', filter);
        }

        // Flags
        if (config.verbose) {
            args.push('--verbose');
        }

        if (config.includeStatements) {
            args.push('--include-statements');
        }

        // Execution mode
        args.push('--exec-mode', config.executionMode);

        return args;
    }

    private async executeAnalysis(args: string[], config: AnalysisConfig): Promise<void> {
        // Check if binaries are available, build if needed
        if (!await this.checkBinaries(config.executionMode)) {
            if (this.autoBuild) {
                await this.buildBinaries(config.executionMode);
            } else {
                throw new ExecutionError(
                    `USVM binaries not found for execution mode: ${config.executionMode}. ` +
                    'Set autoBuild: true or build manually.',
                );
            }
        }

        const cliPath = path.join(this.usvmRoot, 'examples', 'reachability', 'reachability-cli.sh');

        return new Promise((resolve, reject) => {
            const analysisProcess = spawn('bash', [cliPath, ...args], {
                cwd: this.usvmRoot,
                stdio: config.verbose ? 'inherit' : ['ignore', 'pipe', 'pipe'],
            });

            let stdout = '';
            let stderr = '';

            if (!config.verbose) {
                analysisProcess.stdout?.on('data', (data) => {
                    stdout += data.toString();
                });

                analysisProcess.stderr?.on('data', (data) => {
                    stderr += data.toString();
                });
            }

            analysisProcess.on('close', (code) => {
                if (code === 0) {
                    if (!config.verbose) {
                        console.log('✅ Analysis completed successfully');
                    }
                    resolve();
                } else {
                    const errorMessage = config.verbose
                        ? `Analysis failed with exit code ${code}`
                        : `Analysis failed with exit code ${code}:\n${stderr}`;
                    reject(new ExecutionError(errorMessage, code ?? undefined));
                }
            });

            analysisProcess.on('error', (error) => {
                reject(new ExecutionError(`Failed to start analysis process: ${error.message}`));
            });
        });
    }

    private async parseResults(outputDir: string): Promise<AnalysisReport> {
        // Look for analysis results in the output directory
        const resultFiles = await fs.readdir(outputDir);
        const reportFile = resultFiles.find(file => file.endsWith('.json') && file.includes('report'));

        if (!reportFile) {
            throw new ExecutionError('Analysis report not found in output directory');
        }

        const reportPath = path.join(outputDir, reportFile);
        const reportContent = await fs.readFile(reportPath, 'utf8');
        const reportData = JSON.parse(reportContent);

        // Validate and return the report
        return AnalysisReportSchema.parse(reportData);
    }

    private getBinaryPath(executionMode: ExecutionMode): string {
        if (executionMode === ExecutionMode.DIST) {
            return path.join(this.usvmRoot, 'usvm-ts', 'build', 'install', 'usvm-ts', 'bin', 'usvm-ts');
        } else {
            return path.join(this.usvmRoot, 'usvm-ts', 'build', 'libs', 'usvm-ts-all.jar');
        }
    }
}
