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


def inf_comparison(x: float):
    assert x < float('inf')


def infinity_ops(x: float):
    plus_inf = float('inf')
    assert x < plus_inf
    minus_inf = float('-inf')
    assert x > minus_inf
    assert plus_inf * minus_inf < 0
    plus_inf /= x * x
    assert plus_inf == float('inf')
    if x < 0:
        assert (plus_inf * x < 0)
        assert (x * plus_inf < 0)
        assert (plus_inf / x < 0)
        assert (x / plus_inf == 0)
        assert (plus_inf + x == plus_inf)
        assert (x + plus_inf == plus_inf)
        assert (plus_inf - x == plus_inf)
        assert (x - plus_inf == minus_inf)
        assert (minus_inf * x > 0)
        assert (x * minus_inf > 0)
        assert (minus_inf / x > 0)
        assert (x / minus_inf == 0)
        assert (minus_inf + x == minus_inf)
        assert (x + minus_inf == minus_inf)
        assert (minus_inf - x == minus_inf)
        assert (x - minus_inf == plus_inf)
        assert (plus_inf - plus_inf != plus_inf - plus_inf)
        return 1
    elif x > 0:
        assert plus_inf * x > 0
        assert x * plus_inf > 0
        assert plus_inf / x > 0
        assert x / plus_inf == 0
        assert minus_inf * x < 0
        assert x * minus_inf < 0
        assert minus_inf / x < 0
        assert x / minus_inf == 0
        return 2
    return "Unreachable"


def int_true_div(x: int, y: int):
    assert x / y == 10.5


def unary_ops(x: float):
    if -x == 10:
        return 1
    elif +x == 15:
        return 2
    return 3