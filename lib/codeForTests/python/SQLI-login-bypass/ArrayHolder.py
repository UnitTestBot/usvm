class ArrayHolder:
    def __init__(self, values: list[str] | None = None, value: str | None = None):
        if values is not None:
            self.values = values
        elif value is not None:
            self.values = [value, ""]