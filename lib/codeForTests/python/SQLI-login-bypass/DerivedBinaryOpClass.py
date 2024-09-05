class DerivedBinaryOpClass1(BaseBinaryOpClass):
    def virtual_call(self, l: str, r: str) -> str:
        return l


class DerivedBinaryOpClass2(BaseBinaryOpClass):
    def virtual_call(self, l: str, r: str) -> str:
        return r