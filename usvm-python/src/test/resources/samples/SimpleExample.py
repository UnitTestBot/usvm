import pickle
import codecs
import sys


def many_branches(x: int, y: int, z: int):
    if x + y > 100:
        return 0
    y += 10 ** 9
    if 0 < x + z + 1 < 100 and y > 0:
        return 1
    elif x + 3 < -2 - z and x <= y:
        return 2
    elif x * 100 % 7 == 0 and z + y % 100 == 0:
        return 3
    elif x % 155 == 0 and x + y - z < 0:
        return 4
    elif (x + z) % 10 == 0 and x + y > 0:
        return 5
    elif (x - 10 ** 8 + y) * 50 % 9 == 0 and y // 88 == 5:
        return 6
    elif z == 15789 and y + x > 10 ** 9:
        return 7
    elif x + y + z == -10 ** 9 and x != 0 and z == 2598:
        return 8
    else:
        return 9


def my_abs(x: int):
    if x > 0:
        return x
    elif x == 0:
        return "zero"
    elif x < 0:
        return -x
    else:
        assert False, "Not reachable"


def pickle_sample(x):
    return pickle.dumps(x)


def call(x: int):
    return my_abs(x)


def zero_division(x: int):
    return x / 0


def zero_division_in_branch(x: int):
    if x > 100:
        return x / 0
    return x


def bool_input(x: bool):
    if x:
        return 1
    return 2


def mixed_input_types(x: bool, y: int):
    if x and y % 10 == 5:
        return 1
    elif not x:
        return 2
    else:
        return 3


def simple_condition(x):
    return x < 0


def symbolic_call(x):
    assert simple_condition(x)


def infinite_recursion():
    x = object.__new__(codecs.StreamReader)
    next(x)


def range_1(x: int):
    cnt = 0
    for _ in range(x):
        cnt += 1
    assert cnt == 3


def range_2(x: int):
    cnt = 0
    for _ in range(0, x):
        cnt += 1
    assert cnt == 3


def range_3(x: int):
    cnt = 0
    for _ in range(5, x, -1):
        cnt += 1
    assert cnt == 3


def range_4(x: int):
    cnt = 0
    for _ in range(0, x, -1):
        cnt += 1
    assert cnt == 3


def range_5(x: int):
    cnt = 0
    for _ in range(0, x, 5):
        cnt += 1
    assert cnt == 3


def range_6(x: int):
    cnt = 0
    for _ in range(0, 5, x):
        cnt += 1
    assert cnt == 0


def illegal_operation():
    input("Reading from stdin")


def settrace_usage():
    sys.settrace(lambda *args: None)


def remove_tracing():
    sys.settrace(None)


def simple_str(x):
    if isinstance(x, str):
        return 1
    return 2


def is_none(x):
    assert x is None


def is_not_none(x):
    assert x is not None


def f_with_default(x, y=1):
    return x + y


def call_with_default(x):
    assert f_with_default(x) == 10


def unary_int_ops(x: int):
    if -x == 10:
        return 1
    elif +x == 15:
        return 2
    return 3