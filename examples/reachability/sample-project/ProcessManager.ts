// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

// Process Manager - Sample TypeScript project for reachability analysis
// This project simulates basic process management operations using only SMT-solver friendly constructs:
// - Integer operations (no floating-point)
// - Array operations (length, indexing)
// - Object field access and updates
// - Conditional logic and control flow
// - Function calls without complex inheritance

export enum ProcessState {
    CREATED = 0,
    READY = 1,
    RUNNING = 2,
    BLOCKED = 3,
    TERMINATED = 4
}

export enum ProcessPriority {
    LOW = 1,
    NORMAL = 2,
    HIGH = 3,
    CRITICAL = 4
}

export class Process {
    public pid: number;
    public parentPid: number;
    public state: ProcessState;
    public priority: ProcessPriority;
    public memoryUsage: number;  // in KB, integer only
    public cpuTime: number;      // in milliseconds, integer only
    public children: number[];   // Array of child PIDs
    public isSystemProcess: boolean;

    constructor(pid: number, parentPid: number = 0) {
        this.pid = pid;
        this.parentPid = parentPid;
        this.state = ProcessState.CREATED;
        this.priority = ProcessPriority.NORMAL;
        this.memoryUsage = 1024; // Default 1MB
        this.cpuTime = 0;
        this.children = [];
        this.isSystemProcess = parentPid === 0;
    }

    public start(): boolean {
        if (this.state === ProcessState.CREATED || this.state === ProcessState.READY) {
            this.state = ProcessState.RUNNING;
            return true;
        }
        return false;
    }

    public pause(): boolean {
        if (this.state === ProcessState.RUNNING) {
            this.state = ProcessState.READY;
            return true;
        }
        return false;
    }

    public block(): boolean {
        if (this.state === ProcessState.RUNNING) {
            this.state = ProcessState.BLOCKED;
            return true;
        }
        return false;
    }

    public unblock(): boolean {
        if (this.state === ProcessState.BLOCKED) {
            this.state = ProcessState.READY;
            return true;
        }
        return false;
    }

    public terminate(): boolean {
        if (this.state !== ProcessState.TERMINATED) {
            this.state = ProcessState.TERMINATED;
            return true;
        }
        return false;
    }

    public addChild(childPid: number): boolean {
        // Check if child already exists
        for (let i = 0; i < this.children.length; i++) {
            if (this.children[i] === childPid) {
                return false; // Already exists
            }
        }
        this.children.push(childPid);
        return true;
    }

    public removeChild(childPid: number): boolean {
        for (let i = 0; i < this.children.length; i++) {
            if (this.children[i] === childPid) {
                // Remove by shifting elements
                for (let j = i; j < this.children.length - 1; j++) {
                    this.children[j] = this.children[j + 1];
                }
                this.children.pop();
                return true;
            }
        }
        return false;
    }

    public updateMemoryUsage(newUsage: number): boolean {
        if (newUsage < 0) {
            return false; // Invalid memory usage
        }
        this.memoryUsage = newUsage;
        return true;
    }

    public addCpuTime(additionalTime: number): void {
        if (additionalTime > 0) {
            this.cpuTime += additionalTime;
        }
    }

    public setPriority(newPriority: ProcessPriority): boolean {
        if (this.isSystemProcess && newPriority < ProcessPriority.HIGH) {
            return false; // System processes must have high priority
        }
        this.priority = newPriority;
        return true;
    }

    public canBeKilled(): boolean {
        if (this.isSystemProcess) {
            return false; // System processes cannot be killed
        }
        if (this.children.length > 0) {
            return false; // Cannot kill process with children
        }
        return this.state !== ProcessState.TERMINATED;
    }
}

export class ProcessManager {
    private processes: Process[];
    private nextPid: number;
    private maxProcesses: number;
    private runningCount: number;

    constructor(maxProcesses: number = 100) {
        this.processes = [];
        this.nextPid = 1;
        this.maxProcesses = maxProcesses;
        this.runningCount = 0;
    }

    public createProcess(parentPid: number = 0): number {
        if (this.processes.length >= this.maxProcesses) {
            return -1; // Too many processes
        }

        // Verify parent exists if specified
        if (parentPid !== 0) {
            const parent = this.findProcess(parentPid);
            if (parent === null || parent.state === ProcessState.TERMINATED) {
                return -1; // Invalid parent
            }
        }

        const newProcess = new Process(this.nextPid, parentPid);
        this.processes.push(newProcess);

        // Add to parent's children list
        if (parentPid !== 0) {
            const parent = this.findProcess(parentPid);
            if (parent !== null) {
                parent.addChild(this.nextPid);
            }
        }

        this.nextPid += 1;
        return newProcess.pid;
    }

    public startProcess(pid: number): boolean {
        const process = this.findProcess(pid);
        if (process === null) {
            return false;
        }

        if (process.start()) {
            this.runningCount += 1;
            return true;
        }
        return false;
    }

    public killProcess(pid: number): boolean {
        const process = this.findProcess(pid);
        if (process === null) {
            return false;
        }

        if (!process.canBeKilled()) {
            return false;
        }

        // Update running count if needed
        if (process.state === ProcessState.RUNNING) {
            this.runningCount -= 1;
        }

        // Remove from parent's children list
        if (process.parentPid !== 0) {
            const parent = this.findProcess(process.parentPid);
            if (parent !== null) {
                parent.removeChild(pid);
            }
        }

        return process.terminate();
    }

    public getProcessesByState(state: ProcessState): number[] {
        const result: number[] = [];
        for (let i = 0; i < this.processes.length; i++) {
            if (this.processes[i].state === state) {
                result.push(this.processes[i].pid);
            }
        }
        return result;
    }

    public getProcessesByPriority(priority: ProcessPriority): number[] {
        const result: number[] = [];
        for (let i = 0; i < this.processes.length; i++) {
            if (this.processes[i].priority === priority) {
                result.push(this.processes[i].pid);
            }
        }
        return result;
    }

    public getTotalMemoryUsage(): number {
        let total = 0;
        for (let i = 0; i < this.processes.length; i++) {
            if (this.processes[i].state !== ProcessState.TERMINATED) {
                total += this.processes[i].memoryUsage;
            }
        }
        return total;
    }

    public getSystemProcessCount(): number {
        let count = 0;
        for (let i = 0; i < this.processes.length; i++) {
            if (this.processes[i].isSystemProcess &&
                this.processes[i].state !== ProcessState.TERMINATED) {
                count += 1;
            }
        }
        return count;
    }

    public findProcess(pid: number): Process | null {
        for (let i = 0; i < this.processes.length; i++) {
            if (this.processes[i].pid === pid) {
                return this.processes[i];
            }
        }
        return null;
    }

    public scheduleNext(): number {
        // Simple priority-based scheduling
        let bestProcess: Process | null = null;
        let highestPriority = 0;

        for (let i = 0; i < this.processes.length; i++) {
            const process = this.processes[i];
            if (process.state === ProcessState.READY &&
                process.priority > highestPriority) {
                highestPriority = process.priority;
                bestProcess = process;
            }
        }

        if (bestProcess !== null) {
            if (bestProcess.start()) {
                this.runningCount += 1;
                return bestProcess.pid;
            }
        }

        return -1; // No process to schedule
    }

    public cleanupTerminated(): number {
        let cleanedCount = 0;
        let i = 0;

        while (i < this.processes.length) {
            if (this.processes[i].state === ProcessState.TERMINATED) {
                // Remove by shifting elements
                for (let j = i; j < this.processes.length - 1; j++) {
                    this.processes[j] = this.processes[j + 1];
                }
                this.processes.pop();
                cleanedCount += 1;
            } else {
                i += 1;
            }
        }

        return cleanedCount;
    }

    public getRunningCount(): number {
        return this.runningCount;
    }

    public getProcessCount(): number {
        return this.processes.length;
    }
}
