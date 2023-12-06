from approximations.api import *


class IndexApproximation(ApproximationForMethod):
    @staticmethod
    def accept(self, *args) -> bool:
        return len(args) == 1

    @staticmethod
    def run(self: tuple, *args) -> Any:
        target = args[0]
        for i in range(len(self)):
            if self[i] == target:
                return i
        raise ValueError()


class CountApproximation(ApproximationForMethod):
    @staticmethod
    def accept(self, *args) -> bool:
        return len(args) == 1

    @staticmethod
    def run(self: tuple, *args) -> Any:
        elem = args[0]
        result = 0
        for x in self:
            if x == elem:
                result += 1
        return result