class NestedStringHolder:
    def __init__(self, value: str = ""):
        self.inner_object = self.InnerStringHolder(value)
        pass

    def get_value(self) -> str:
        return self.inner_object.inner_value

    class InnerStringHolder:
        def __init__(self, value: str):
            self.inner_value = value