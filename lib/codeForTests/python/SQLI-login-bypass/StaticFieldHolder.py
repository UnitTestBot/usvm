class StaticFieldHolder:
    default_value: str = ""

    def __init__(self, value: str | None = None):
        if value is None:
            self.value = self.default_value
        else:
            self.value = value