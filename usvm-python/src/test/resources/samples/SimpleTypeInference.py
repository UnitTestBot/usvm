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
