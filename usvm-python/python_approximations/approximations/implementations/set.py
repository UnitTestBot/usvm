from approximations.api import *


class ConstructorApproximation(ApproximationForFunction):
    @staticmethod
    def accept(*args) -> bool:
        return len(args) == 1

    @staticmethod
    def run(*args) -> set:
        result = set()
        x = args[0]
        for elem in x:
            result.add(elem)
        return result