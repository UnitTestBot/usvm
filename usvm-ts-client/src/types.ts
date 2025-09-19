// Core types for USVM Reachability Analysis
import { z } from 'zod';

// Enums
export enum AnalysisMode {
    ALL_METHODS = 'ALL_METHODS',
    PUBLIC_METHODS = 'PUBLIC_METHODS',
    ENTRY_POINTS = 'ENTRY_POINTS'
}

export enum SolverType {
    YICES = 'YICES',
    Z3 = 'Z3',
    CVC5 = 'CVC5'
}

export enum ExecutionMode {
    SHADOW = 'shadow',
    DIST = 'dist'
}

export enum TargetTypeDto {
    INITIAL = 'initial',
    INTERMEDIATE = 'intermediate',
    FINAL = 'final'
}

// Location and Target DTOs - Updated to match Kotlin structure
export const LocationDtoSchema = z.object({
    fileName: z.string().describe('File name'),
    className: z.string().describe('Class name'),
    methodName: z.string().describe('Method name'),
    stmtType: z.string().optional().describe('Statement type (optional)'),
    block: z.number().int().optional().describe('Block number (optional)'),
    index: z.number().int().optional().describe('Statement index (optional)'),
});

export const TargetDtoSchema = z.object({
    type: z.enum(TargetTypeDto).default(TargetTypeDto.INTERMEDIATE).describe('Target type'),
    location: LocationDtoSchema.describe('Source location of the target'),
});

// Define the type first without circular reference
export type TargetTreeNodeDto = {
    target: z.infer<typeof TargetDtoSchema>;
    children: TargetTreeNodeDto[];
};

export const TargetTreeNodeDtoSchema: z.ZodType<TargetTreeNodeDto> = z.lazy(() => z.object({
    target: TargetDtoSchema.describe('The target at this tree node'),
    children: z.array(TargetTreeNodeDtoSchema).default([]).describe('Child targets in the execution tree'),
}));

// Updated container types to match Kotlin sealed interface structure
export const LinearTraceSchema = z.object({
    targets: z.array(TargetDtoSchema).describe('List of targets in linear sequence'),
});

export const TreeTraceSchema = z.object({
    root: TargetTreeNodeDtoSchema.describe('Root of the target tree'),
});

export const TraceListSchema = z.object({
    traces: z.array(z.union([LinearTraceSchema, TreeTraceSchema])).describe('List of traces'),
});

export const TargetsContainerDtoSchema = z.union([
    LinearTraceSchema,
    TreeTraceSchema,
    TraceListSchema,
    z.array(z.union([LinearTraceSchema, TreeTraceSchema])), // Array at top level
]);

// Type exports from schemas
export type LocationDto = z.infer<typeof LocationDtoSchema>;
export type TargetDto = z.infer<typeof TargetDtoSchema>;
// TargetTreeNodeDto is already defined above

export type LinearTrace = z.infer<typeof LinearTraceSchema>;
export type TreeTrace = z.infer<typeof TreeTraceSchema>;
export type TraceList = z.infer<typeof TraceListSchema>;
export type TargetsContainerDto = z.infer<typeof TargetsContainerDtoSchema>;

// Analysis Configuration
export const AnalysisConfigSchema = z.object({
    projectPath: z.string().describe('Path to TypeScript project directory'),
    targetsFile: z.string().optional().describe('Path to JSON file with target definitions'),
    outputDir: z.string().default('./reachability-results').describe('Output directory for analysis results'),
    analysisMode: z.enum(AnalysisMode).default(AnalysisMode.PUBLIC_METHODS).describe('Analysis scope'),
    methodFilters: z.array(z.string()).default([]).describe('Filter methods by name patterns'),
    solverType: z.enum(SolverType).default(SolverType.YICES).describe('SMT solver to use'),
    timeout: z.number().int().positive().default(300).describe('Analysis timeout in seconds'),
    stepsLimit: z.number().int().positive().default(3500).describe('Maximum steps from last covered statement'),
    verbose: z.boolean().default(false).describe('Enable verbose output'),
    includeStatements: z.boolean().default(false).describe('Include statement details in output'),
    executionMode: z.enum(ExecutionMode).default(ExecutionMode.SHADOW).describe('CLI execution mode'),
});

export type AnalysisConfig = z.infer<typeof AnalysisConfigSchema>;

// Analysis Results
export const AnalysisResultSchema = z.object({
    targetId: z.string().describe('ID of the target that was analyzed'),
    reachable: z.boolean().describe('Whether the target is reachable'),
    executionTime: z.number().describe('Time taken for analysis in milliseconds'),
    coveredStatements: z.number().int().nonnegative().describe('Number of statements covered'),
    totalPaths: z.number().int().nonnegative().describe('Total number of execution paths explored'),
    errorMessage: z.string().optional().describe('Error message if analysis failed'),
});

export const AnalysisReportSchema = z.object({
    projectPath: z.string().describe('Path of analyzed project'),
    analysisMode: z.enum(AnalysisMode).describe('Analysis mode used'),
    solverType: z.enum(SolverType).describe('SMT solver used'),
    totalTime: z.number().describe('Total analysis time in milliseconds'),
    timestamp: z.string().describe('ISO timestamp of analysis'),
    results: z.array(AnalysisResultSchema).describe('Individual target analysis results'),
    summary: z.object({
        totalTargets: z.number().int().nonnegative(),
        reachableTargets: z.number().int().nonnegative(),
        unreachableTargets: z.number().int().nonnegative(),
        failedTargets: z.number().int().nonnegative(),
    }).describe('Summary statistics'),
});

export type AnalysisResult = z.infer<typeof AnalysisResultSchema>;
export type AnalysisReport = z.infer<typeof AnalysisReportSchema>;

// Error types
export class USVMError extends Error {
    constructor(message: string, public readonly code?: string) {
        super(message);
        this.name = 'USVMError';
    }
}

export class AnalysisError extends USVMError {
    constructor(message: string, public readonly targetId?: string) {
        super(message, 'ANALYSIS_ERROR');
        this.name = 'AnalysisError';
    }
}

export class ConfigurationError extends USVMError {
    constructor(message: string) {
        super(message, 'CONFIGURATION_ERROR');
        this.name = 'ConfigurationError';
    }
}

export class ExecutionError extends USVMError {
    constructor(message: string, public readonly exitCode?: number) {
        super(message, 'EXECUTION_ERROR');
        this.name = 'ExecutionError';
    }
}
