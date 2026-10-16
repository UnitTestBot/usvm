class MachineForkLimit {
    binary(flag: boolean): number {
        if (flag) return 1;

        return 2;
    }

    infeasible(flag: boolean): number {
        if (flag) {
            if (!flag) return 100;

            return 1;
        }

        return 2;
    }
}
