export class MathUtils {
    public static factorial(n: number): number {
        if (n < 0) {
            throw new Error("Factorial of negative number");
        }
        if (n === 0 || n === 1) {
            return 1;
        }
        return n * MathUtils.factorial(n - 1);
    }

    public static fibonacci(n: number): number {
        if (n < 0) {
            return -1;  // Error case
        }
        if (n <= 1) {
            return n;
        }
        return MathUtils.fibonacci(n - 1) + MathUtils.fibonacci(n - 2);
    }

    public static isPrime(num: number): boolean {
        if (num <= 1) {
            return false;
        }
        if (num === 2) {
            return true;
        }
        if (num % 2 === 0) {
            return false;  // Even numbers > 2 are not prime
        }

        for (let i = 3; i * i <= num; i += 2) {
            if (num % i === 0) {
                return false;  // Found a divisor
            }
        }
        return true;  // Prime number
    }

    public static gcd(a: number, b: number): number {
        if (b === 0) {
            return a;  // Base case for recursion
        }
        return MathUtils.gcd(b, a % b);
    }
}
