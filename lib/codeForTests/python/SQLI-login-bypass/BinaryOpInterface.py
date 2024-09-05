from abc import ABC, abstractmethod


class BinaryOpInterface(ABC):
    @abstractmethod
    def interface_call(self, l: str, r: str) -> str:
        raise NotImplementedError