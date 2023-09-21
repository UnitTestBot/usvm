def float_input(x):
    assert isinstance(x, float)


def comparison(x):
    if x == 4.7:
        return 1
    elif x > 5.1:
        return 2
    elif x < -10.5:
        return 3
    elif x >= 3.77:
        return 4
    elif x <= -3.553:
        return 5
    elif x != 0.5:
        return 6
    else:
        return 7


def simple_operations(x):
    if x + 0.5 > 3.1:
        return 1
    elif x * 2 == 0.5:
        return 2
    elif x - 100 < -500.5:
        return 3
    elif x * True == 1:
        return 4
    elif x / 2.5 == 1:
        return 5
    return 6


def round(x: float):
    assert int(x / 2) == 5