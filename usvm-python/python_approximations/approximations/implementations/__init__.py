from approximations.api import SpecialApproximation


class ContainsOpApproximation(SpecialApproximation):
    @staticmethod
    def run(storage, item) -> bool:
        for elem in storage:
            if elem == item:
                return True
        return False