import { Classifier } from './Classifier';

export function scenario(value: number): { result: number } {
    const subject = new Classifier();
    const result = subject.classify(value);
    return { result };
}

export function correct(value: number): boolean {
    const observation = scenario(value);
    return observation.result >= 0 && observation.result <= 2;
}
