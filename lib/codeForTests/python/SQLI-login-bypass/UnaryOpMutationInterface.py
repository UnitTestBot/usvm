from abc import ABC, abstractmethod


class UnaryOpMutationInterface(ABC):
    @abstractmethod
    def mutate(self, t: StringHolder) -> None:
        raise NotImplementedError


class UnaryOpMutation(UnaryOpMutationInterface):

    def __init__(self, fun: Callable[[StringHolder], None]) -> None:
        self.mutate = fun

    def mutate(self, t: StringHolder) -> None:
        pass