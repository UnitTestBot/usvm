def bool_input(x):
    if x:
        return 1
    else:
        return 2


def two_args(x, y):
    if x and y:
        return 1
    elif x:
        return 2
    elif y:
        return 3
    else:
        return 4


def list_of_int(x):
    if x[0] == 10:
        return 1
    elif x[-1] == 150:
        return 2
    return 3


def double_subscript(x):
    if x[0][0]:
        return 1
    return 2


def simple_comparison(x, y):
    if x > y:
        return 1
    elif x == y:
        return 2
    return 3


def isinstance_sample(x):
    if isinstance(x, bool):
        return 1
    if isinstance(x, int):
        return 2
    elif isinstance(x, list):
        return 3
    elif isinstance(x, object):
        return 4
    return "Not reachable"


def list_concat_usage(x, y):
    z = x + y
    z += []
    assert z


def len_usage(x):
    if len(x) == 5:
        return 1
    return 2


def iteration(x):
    sum_ = 0
    for elem in x:
        sum_ += elem

    assert sum_ % 153 == 152


def add_and_compare(x, y):
    x[0] += 1
    assert x < y


def multiply_and_compare(x, y):
    y[10] *= 5
    assert x[0] < y


def subscript_and_isinstance(x):
    if isinstance(x[5], bool):
        return 1
    elif isinstance(x[5], int):
        return 2
    elif isinstance(x[3], type(None)):
        return 3
    return 4


def range_loop(x):
    sum_ = 0
    for i in range(x):
        sum_ += i
    if sum_ > 15:
        return 1
    return 2


def sum_usage(x):
    s = sum(x)
    assert s == 10


def use_str_eq(x):
    assert x == "aaaaa"


def use_str_neq(x):
    assert x != "aaaa"