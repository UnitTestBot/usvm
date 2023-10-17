from typing import Any

from approximations.api import ApproximationForMethod


# TODO: optional arguments
class IndexApproximation(ApproximationForMethod):
    @staticmethod
    def accept(self, *args) -> bool:
        return len(args) == 1

    @staticmethod
    def run(self: list, *args) -> Any:
        target = args[0]
        for i in range(len(self)):
            if self[i] == target:
                return i
        raise ValueError()
