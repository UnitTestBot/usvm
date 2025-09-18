// Main application entry point

import { Calculator, processNumbers } from './calculator';
import { AuthService } from './auth';

export class Application {
    private authService: AuthService;
    private calculator: Calculator;

    constructor() {
        this.authService = new AuthService();
        this.calculator = new Calculator();
    }

    async initialize(): Promise<void> {
        console.log('Initializing application...');
        await this.loadConfiguration();
        this.setupErrorHandlers();
    }

    async start(): Promise<void> {
        await this.initialize();
        console.log('Application started successfully');
    }

    async processUserData(numbers: number[]): Promise<number> {
        const currentUser = this.authService.getCurrentUser();
        if (!currentUser) {
            throw new Error('User not authenticated');
        }

        if (!this.authService.hasPermission('user')) {
            throw new Error('Insufficient permissions');
        }

        return processNumbers(numbers);
    }

    async authenticateAndProcess(username: string, password: string, data: number[]): Promise<number> {
        const user = await this.authService.login(username, password);
        if (!user) {
            throw new Error('Authentication failed');
        }

        try {
            return await this.processUserData(data);
        } finally {
            this.authService.logout();
        }
    }

    private async loadConfiguration(): Promise<void> {
        // Simulate configuration loading
        await new Promise(resolve => setTimeout(resolve, 50));
    }

    private setupErrorHandlers(): void {
        process.on('uncaughtException', (error) => {
            console.error('Uncaught exception:', error);
        });
    }
}

// Entry point
export async function main(): Promise<void> {
    const app = new Application();
    await app.start();

    // Example usage
    try {
        const result = await app.authenticateAndProcess('admin', 'password123', [1, -2, 3, -4]);
        console.log('Processing result:', result);
    } catch (error) {
        if (error instanceof Error) {
            console.error('Application error:', error.message);
        } else {
            console.error('Unknown error:', error);
        }
    }
}

// Execute main if this file is run directly
if (require.main === module) {
    main().catch(console.error);
}
