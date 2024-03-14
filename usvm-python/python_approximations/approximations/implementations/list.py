from typing import Any, Tuple

from approximations.api import *


class ConstructorApproximation(ApproximationForFunction):
    @staticmethod
    def accept(*args) -> bool:
        return len(args) <= 1

    @staticmethod
    def run(*args) -> list:
        if len(args) == 0:
            return []
        result = []
        for elem in args[0]:
            result.append(elem)
        return result


# TODO: optional arguments
class IndexApproximation(ApproximationForMethod):
    @staticmethod
    def accept(self, *args) -> bool:
        return len(args) == 1

    @staticmethod
    def run(self: list, *args) -> Any:
        target = args[0]
        for i in range(len(self)):
            if self[i] == target:
                return i
        raise ValueError()


class ReverseApproximation(ApproximationForMethod):
    @staticmethod
    def accept(self, *args) -> bool:
        return len(args) == 0

    @staticmethod
    def run(self: list, *args) -> Any:
        n = len(self)
        for i in range(n // 2):
            self[i], self[n - i - 1] = self[n - i - 1], self[i]


class MultiplyApproximation(SpecialApproximation):
    @staticmethod
    def list_multiply_impl(x: list, y: int):
        result = []
        for _ in range(y):
            result += x
        return result

    run = list_multiply_impl


class SliceGetItemApproximation(SpecialApproximation):
    @staticmethod
    def slice_unpack_impl(s: slice) -> Tuple[int, int, int]:
        start: int
        stop: int
        step: int
        min_, max_ = -10**18, 10**18
        if s.step is None:
            step = 1
        else:
            step = s.step
            if step == 0:
                raise ValueError('slice step cannot be zero')
        if s.start is None:
            start = max_ if step < 0 else 0
        else:
            start = s.start
        if s.stop is None:
            stop = min_ if step < 0 else max_
        else:
            stop = s.stop

        return start, stop, step

    @staticmethod
    def slice_adjust_indices_impl(length, start, stop, step):
        result_length = 0
        if start < 0:
            start += length
            if start < 0:
                start = -1 if step < 0 else 0
        elif start >= length:
            start = length - 1 if step < 0 else length
        if stop < 0:
            stop += length
            if stop < 0:
                stop = -1 if step < 0 else 0
        elif stop >= length:
            stop = length - 1 if step < 0 else length
        if step < 0:
            if stop < start:
                result_length = (start - stop - 1) // (-step) + 1
        else:
            if start < stop:
                result_length = (stop - start - 1) // step + 1
        return result_length, start, stop, step

    @staticmethod
    def slice_get_item_impl(self: list, item: slice):
        start, stop, step = SliceGetItemApproximation.slice_unpack_impl(item)
        slice_length, start, stop, step = SliceGetItemApproximation.slice_adjust_indices_impl(len(self), start, stop, step)
        if slice_length <= 0:
            return []
        else:
            result = []
            cur = start
            for i in range(slice_length):
                result.append(self[cur])
                cur += step
            return result

    run = slice_get_item_impl


class SortApproximation(ApproximationForMethod):
    @staticmethod
    def accept(self, *args) -> bool:
        return len(args) == 0

    @staticmethod
    def run(self: list, *args) -> Any:
        for i in range(len(self)):
            new_elem = self[i]
            for j in range(i):
                if new_elem < self[j]:
                    for k in range(i, j, -1):
                        self[k] = self[k - 1]
                    self[j] = new_elem
                    break


class CopyApproximation(ApproximationForMethod):
    @staticmethod
    def accept(self, *args) -> bool:
        return len(args) == 0

    @staticmethod
    def run(self: list, *args) -> Any:
        result = []
        for elem in self:
            result.append(elem)
        return result


class RemoveApproximation(ApproximationForMethod):
    @staticmethod
    def accept(self, *args) -> bool:
        return len(args) == 1

    @staticmethod
    def run(self: list, *args) -> Any:
        elem = args[0]
        cnt = -1
        for x in self:
            cnt += 1
            if x == elem:
                self.pop(cnt)
                return
        raise ValueError()


class CountApproximation(ApproximationForMethod):
    @staticmethod
    def accept(self, *args) -> bool:
        return len(args) == 1

    @staticmethod
    def run(self: list, *args) -> Any:
        elem = args[0]
        result = 0
        for x in self:
            if x == elem:
                result += 1
        return result