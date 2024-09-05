class ImplBinaryOpInterfaceClass1(BinaryOpInterface):
    def interface_call(self, l: str, r: str) -> str:
        return l


class ImplBinaryOpInterfaceClass2(BinaryOpInterface):
    def interface_call(self, l: str, r: str) -> str:
        return r