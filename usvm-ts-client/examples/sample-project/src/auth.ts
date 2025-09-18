// Authentication module for sample project

export interface User {
    id: string;
    username: string;
    email: string;
    role: 'admin' | 'user' | 'guest';
}

export class AuthService {
    private users: User[] = [
        { id: '1', username: 'admin', email: 'admin@example.com', role: 'admin' },
        { id: '2', username: 'user1', email: 'user1@example.com', role: 'user' },
        { id: '3', username: 'guest', email: 'guest@example.com', role: 'guest' }
    ];

    private currentUser: User | null = null;

    async login(username: string, password: string): Promise<User | null> {
        // Simulate async authentication
        await this.delay(100);

        const user = this.users.find(u => u.username === username);
        if (user && this.validateCredentials(username, password)) {
            this.currentUser = user;
            this.logSecurityEvent('LOGIN_SUCCESS', user.id);
            return user;
        }

        this.logSecurityEvent('LOGIN_FAILED', username);
        return null;
    }

    logout(): void {
        if (this.currentUser) {
            this.logSecurityEvent('LOGOUT', this.currentUser.id);
            this.currentUser = null;
        }
    }

    getCurrentUser(): User | null {
        return this.currentUser;
    }

    hasPermission(requiredRole: 'admin' | 'user' | 'guest'): boolean {
        if (!this.currentUser) {
            return false;
        }

        const roleHierarchy = { admin: 3, user: 2, guest: 1 };
        return roleHierarchy[this.currentUser.role] >= roleHierarchy[requiredRole];
    }

    private validateCredentials(username: string, password: string): boolean {
        // Simple validation - in real app this would hash/compare properly
        return password.length >= 6;
    }

    private logSecurityEvent(event: string, userId: string): void {
        console.log(`[SECURITY] ${event}: ${userId} at ${new Date().toISOString()}`);
    }

    private async delay(ms: number): Promise<void> {
        return new Promise(resolve => setTimeout(resolve, ms));
    }
}
