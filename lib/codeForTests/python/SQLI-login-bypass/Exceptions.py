class NoOpException(Exception):
    pass


class Exception1(Exception):
    pass


class Exception2(Exception):
    def __init__(self, value: str):
        super().__init__(value)
        self.args = ("",)