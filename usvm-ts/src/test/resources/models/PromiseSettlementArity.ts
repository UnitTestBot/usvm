// @ts-nocheck
export class PromiseSettlementArity {
    static async resolveMissing(): Promise<number> {
        const value = await Promise.resolve();
        return value === undefined ? 1 : 0;
    }

    static async rejectMissing(): Promise<number> {
        try { await Promise.reject(); } catch (value) { return value === undefined ? 1 : 0; }
        return 0;
    }

    static async resolveExtra(): Promise<number> {
        let effects = 0;
        const value = await Promise.resolve(7, ++effects);
        return value === 7 && effects === 1 ? 1 : 0;
    }

    static async rejectExtra(): Promise<number> {
        let effects = 0;
        try { await Promise.reject(7, ++effects); }
        catch (value) { return value === 7 && effects === 1 ? 1 : 0; }
        return 0;
    }

    static async callbackResolveMissing(): Promise<number> {
        const value = await new Promise<void>((resolve) => resolve());
        return value === undefined ? 1 : 0;
    }

    static async callbackRejectMissing(): Promise<number> {
        try { await new Promise<void>((resolve, reject) => reject()); }
        catch (value) { return value === undefined ? 1 : 0; }
        return 0;
    }

    static async firstSettlementWins(): Promise<number> {
        const value = await new Promise<number>((resolve: any) => { resolve(7); resolve(); });
        return value === 7 ? 1 : 0;
    }

    static async callbackExtra(): Promise<number> {
        const counter = { effects: 0 };
        const value = await new Promise<number>((resolve: any) => resolve(7, ++counter.effects));
        return value === 7 && counter.effects === 1 ? 1 : 0;
    }
}
