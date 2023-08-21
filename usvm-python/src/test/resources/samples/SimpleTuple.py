def tuple_construct_and_iter(x):
    t = 1, 2, 3, x
    res = 0
    for y in t:
        res += y

    assert res == 10


def tuple_unpack(x):
    t = 1, x
    a, b = t
    assert a == b