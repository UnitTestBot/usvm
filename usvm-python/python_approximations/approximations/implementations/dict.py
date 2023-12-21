from approximations.api import *


class ConstructorApproximation(ApproximationForFunction):
    @staticmethod
    def accept(*args) -> bool:
        return len(args) == 0

    @staticmethod
    def run(*args) -> dict:
        return {}


class GetApproximation(ApproximationForMethod):
    @staticmethod
    def accept(self, *args) -> bool:
        return 1 <= len(args) <= 2

    @staticmethod
    def run(self: dict, *args) -> Any:
        default = None
        if len(args) == 2:
            default = args[1]
        if args[0] not in self:
            return default
        return self[args[0]]


class SetdefaultApproximation(ApproximationForMethod):
    @staticmethod
    def accept(self, *args) -> bool:
        return 1 <= len(args) <= 2

    @staticmethod
    def run(self: dict, *args) -> Any:
        default = None
        if len(args) == 2:
            default = args[1]
        if args[0] in self:
            return self[args[0]]
        self[args[0]] = default
        return default