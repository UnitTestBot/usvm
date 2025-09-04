// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

/**
 * Complex reachability scenarios combining multiple language constructions:
 * arrays, objects, method calls, and conditional logic.
 */
class ComplexReachability {

    // Combined array and object field access
    arrayObjectCombinedReachable(): number {
        const objects = [
            new DataHolder(10),
            new DataHolder(20),
            new DataHolder(30),
        ];

        if (objects[0].value < objects[1].value) {
            objects[2].value = objects[0].value + objects[1].value;
            if (objects[2].value === 30) {
                return 1; // Reachable: 10 < 20, then objects[2].value = 10 + 20 = 30
            }
        }
        return 0;
    }

    // Method calls with array manipulation
    methodArrayManipulationReachable(input: number): number {
        const arr = this.createNumberArray(input);
        const processedArr = this.processArray(arr);

        if (processedArr.length > 0) {
            if (processedArr[0] > input) {
                return 1; // Reachable depending on input value
            }
        }
        return 0;
    }

    // Object method calls affecting reachability
    objectMethodCallReachable(): number {
        const calculator = new Calculator();
        calculator.setValue(15);

        const doubled = calculator.getDoubled();
        if (doubled === 30) {
            calculator.add(10);
            if (calculator.getValue() === 40) {
                return 1; // Reachable: 15 * 2 = 30, then 15 + 10 = 25, but getValue() returns current value
            }
        }
        return 0;
    }

    // Nested object and array access with method calls
    nestedComplexReachable(): number {
        const container = new ArrayContainer();
        container.numbers = [5, 10, 15];
        container.processor = new NumberProcessor();

        const result = container.processor.processNumbers(container.numbers);
        if (result > 25) {
            if (container.numbers[1] === 10) {
                return 1; // Reachable: sum of [5,10,15] is 30 > 25, and numbers[1] is 10
            }
        }
        return 0;
    }

    // Loop-based reachability with early termination
    loopBasedReachable(limit: number): number {
        const results = [];
        for (let i = 0; i < limit && i < 10; i++) {
            const computed = this.computeValue(i);
            results.push(computed);

            if (computed > 50) {
                if (results.length > 2) {
                    return 1; // Reachable when we find a value > 50 after at least 2 iterations
                }
            }
        }
        return 0;
    }

    // Conditional object creation and method chaining
    conditionalObjectReachable(createExpensive: boolean): number {
        let processor: any;

        if (createExpensive) {
            processor = new ExpensiveProcessor();
        } else {
            processor = new SimpleProcessor();
        }

        const result = processor.process(100);
        if (createExpensive && result === 200) {
            return 1; // Reachable when createExpensive is true and ExpensiveProcessor doubles the input
        }

        if (!createExpensive && result === 100) {
            return 2; // Reachable when createExpensive is false and SimpleProcessor returns input unchanged
        }

        return 0;
    }

    // Array of objects with cross-references
    crossReferenceReachable(): number {
        const nodeA = new GraphNode(1);
        const nodeB = new GraphNode(2);
        const nodeC = new GraphNode(3);

        nodeA.addConnection(nodeB);
        nodeB.addConnection(nodeC);
        nodeC.addConnection(nodeA);

        if (nodeA.connections.length === 1) {
            if (nodeA.connections[0].value === 2) {
                if (nodeA.connections[0].connections[0].value === 3) {
                    return 1; // Reachable: A->B, B->C, verify the chain
                }
            }
        }
        return 0;
    }

    // Helper methods
    createNumberArray(size: number): number[] {
        const arr = [];
        for (let i = 0; i < size && i < 5; i++) {
            arr.push(i * 2);
        }
        return arr;
    }

    processArray(arr: number[]): number[] {
        const result = [];
        for (let i = 0; i < arr.length; i++) {
            result.push(arr[i] + 1);
        }
        return result;
    }

    computeValue(input: number): number {
        return input * input + input * 10;
    }
}

// Helper classes for complex tests
class DataHolder {
    value: number;

    constructor(val: number) {
        this.value = val;
    }
}

class Calculator {
    private currentValue: number = 0;

    setValue(val: number): void {
        this.currentValue = val;
    }

    getValue(): number {
        return this.currentValue;
    }

    getDoubled(): number {
        return this.currentValue * 2;
    }

    add(val: number): void {
        this.currentValue += val;
    }
}

class ArrayContainer {
    numbers: number[];
    processor: NumberProcessor;
}

class NumberProcessor {
    processNumbers(arr: number[]): number {
        let sum = 0;
        for (let i = 0; i < arr.length; i++) {
            sum += arr[i];
        }
        return sum;
    }
}

class SimpleProcessor {
    process(input: number): number {
        return input;
    }
}

class ExpensiveProcessor {
    process(input: number): number {
        return input * 2;
    }
}

class GraphNode {
    value: number;
    connections: GraphNode[] = [];

    constructor(val: number) {
        this.value = val;
    }

    addConnection(node: GraphNode): void {
        this.connections.push(node);
    }
}
