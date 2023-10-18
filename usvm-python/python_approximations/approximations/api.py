from abc import ABC, abstractmethod
from typing import Any, Callable


class ApproximationForMethod(ABC):
    @staticmethod
    @abstractmethod
    def accept(self, *args) -> bool:  # TODO: support kwargs
        ...

    @staticmethod
    @abstractmethod
    def run(self, *args) -> Any:  # TODO: support kwargs
        ...


class ApproximationForFunction(ABC):
    @staticmethod
    @abstractmethod
    def accept(*args) -> bool:  # TODO: support kwargs
        ...

    @staticmethod
    @abstractmethod
    def run(*args) -> Any:  # TODO: support kwargs
        ...


class SpecialApproximation(ABC):
    run: Callable