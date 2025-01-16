class Equality {
    truthyTypes() {
        if ([] == true) {
            return -1 // unreachable
        }

        if ([] == ![]) {
            if (![] == false) {
                return 1
            }
        }

        return -1 // unreachable
    }
}