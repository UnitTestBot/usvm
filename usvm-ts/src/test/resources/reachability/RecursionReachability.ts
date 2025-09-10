// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

/**
 * Recursion reachability scenarios.
 * Tests reachability through recursive function calls with controlled depth limits.
 */
class RecursionReachability {

    // Recursive factorial with depth limit
    factorial(n: number): number {
        if (n <= 1) {
            return 1; // Base case reachable
        }
        
        if (n > 5) {
            return -1; // Note: depth limit to prevent infinite loops
        }
        
        return n * this.factorial(n - 1); // Recursive case with depth limit
    }

    factorialReachable(input: number): number {
        const result = this.factorial(input);
        if (result === 24) { // 4! = 24
            return 1; // Reachable when input = 4
        }
        return 0;
    }

    // Tail recursion with accumulator
    countdown(n: number, accumulator: number = 0): number {
        if (n === 0) {
            if (accumulator > 10) {
                return 1; // Reachable when initial n leads to accumulator > 10
            }
            return 0;
        }
        
        if (n > 5) {
            return 0; // Note: depth limit to prevent excessive recursion
        }

        return this.countdown(n - 1, accumulator + n);
    }

    // Mutual recursion with even/odd check
    isEven(n: number): boolean {
        if (n === 0) return true;
        if (n === 1) return false;
        if (n > 4) return false; // Note: depth limit
        return this.isOdd(n - 1);
    }

    isOdd(n: number): boolean {
        if (n === 0) return false;
        if (n === 1) return true;
        if (n > 4) return true; // Note: depth limit
        return this.isEven(n - 1);
    }

    mutualRecursionReachable(input: number): number {
        if (input > 0 && input < 5) {
            const evenResult = this.isEven(input);
            if (evenResult && input === 4) {
                return 1; // Reachable for even number 4
            }
        }
        return 0;
    }

    // Iterative fibonacci to avoid deep recursion
    fibonacciIterative(n: number): number {
        if (n <= 1) return n;
        if (n > 10) return -1;

        let a = 0, b = 1;
        for (let i = 2; i <= n; i++) {
            const temp = a + b;
            a = b;
            b = temp;
        }

        if (b === 13) { // fib(7) = 13
            return 1; // Special case
        }
        
        return b;
    }

    // Tree traversal using direct property access
    treeTraversalReachable(): number {
        const treeNode = {
            value: 10,
            left: { value: 5 },
            right: { value: 15 }
        };

        const target = 15;

        // Direct search without recursion
        if (treeNode.value === target) {
            return 1;
        }
        if (treeNode.left && treeNode.left.value === target) {
            return 1;
        }
        if (treeNode.right && treeNode.right.value === target) {
            return 1; // Reachable: 15 exists in the tree
        }

        return 0;
    }

    // Simple recursive sum with depth control
    sumToN(n: number): number {
        if (n <= 0) {
            return 0; // Base case
        }
        if (n > 5) {
            return -1; // Note: depth limit
        }

        return n + this.sumToN(n - 1);
    }

    sumRecursionReachable(input: number): number {
        const result = this.sumToN(input);
        if (result === 15) { // sum(5) = 5+4+3+2+1 = 15
            return 1; // Reachable when input = 5
        }
        return 0;
    }

    // Binary search with depth control
    binarySearchSimple(arr: number[], target: number, start: number = 0, end: number = -1): boolean {
        if (end === -1) end = arr.length - 1;

        if (start > end) {
            return false; // Base case: not found
        }

        if (end - start > 8) {
            return false; // Note: depth limit to prevent excessive recursion
        }

        const mid = Math.floor((start + end) / 2);

        if (arr[mid] === target) {
            return true; // Found
        }

        if (arr[mid] > target) {
            return this.binarySearchSimple(arr, target, start, mid - 1);
        } else {
            return this.binarySearchSimple(arr, target, mid + 1, end);
        }
    }

    binarySearchReachable(): number {
        const sortedArray = [1, 3, 5, 7, 9, 11, 13, 15];
        const found = this.binarySearchSimple(sortedArray, 7);

        if (found) {
            return 1; // Reachable when target 7 is found
        }
        return 0;
    }
}
