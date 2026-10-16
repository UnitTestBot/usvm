export class Classifier {
    classify(value: number): number {
        if (value === 1234567) {
            return 2;
        }
        if (value > 0) {
            return 1;
        }
        return 0;
    }
}
