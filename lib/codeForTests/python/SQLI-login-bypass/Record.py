from dataclasses import dataclass

@dataclass(frozen=True)
class SimpleRecord:
    t: str


from dataclasses import dataclass

@dataclass(frozen=True)
class NestedRecord:
    a: SimpleRecord
    b: SimpleRecord