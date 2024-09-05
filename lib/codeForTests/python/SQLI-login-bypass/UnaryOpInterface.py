from abc import ABC, abstractmethod


class UnaryOpInterface(ABC):
    @abstractmethod
    def interface_call(self, t: str) -> str:
        raise NotImplementedError