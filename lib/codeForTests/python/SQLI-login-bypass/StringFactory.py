class StringFactory:
    def __init__(self, value: str):
        self.val = value

    @classmethod
    def create_instance(cls, value: str = ""):
        return cls(value)