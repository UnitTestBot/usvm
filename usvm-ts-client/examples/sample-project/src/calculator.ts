// Simple TypeScript sample project for testing reachability analysis

export class Calculator {
    private history: number[] = [];

    add(a: number, b: number): number {
        const result = a + b;
        this.history.push(result);
        return result;
    }

    multiply(a: number, b: number): number {
        const result = a * b;
        this.history.push(result);
        return result;
    }

    divide(a: number, b: number): number {
        if (b === 0) {
            throw new Error('Division by zero');
        }
        const result = a / b;
        this.history.push(result);
        return result;
    }

    getHistory(): number[] {
        return [...this.history];
    }

    clear(): void {
        this.history = [];
    }
}

export function processNumbers(numbers: number[]): number {
    const calc = new Calculator();
    let result = 0;

    for (const num of numbers) {
        if (num > 0) {
            result = calc.add(result, num);
        } else if (num < 0) {
            result = calc.multiply(result, Math.abs(num));
        }
    }

    return result;
}
