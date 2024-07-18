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


def input_list_of_pairs(x):
    result = 0
    assert len(x) <= 4
    for p in x:
        assert isinstance(p, tuple)
        a, b = p
        result += a - b
    if result == 12345:
        return 1
    return 2


def length(x: tuple):
    assert len(x) == 11


def sum_of_tuple(x: tuple):
    sum_ = 0
    for elem in x:
        sum_ += elem
    assert sum_ == 239


def get_item_sample(x):
    t = (1, 2, 3)
    if x == t[0]:
        return 1
    elif x == t[-2]:
        return 2
    elif x == t[2]:
        return 3
    else:
        return 4


def get_item_of_input(x, t):
    assert isinstance(t, tuple)
    if x == t[0]:
        return 1
    elif x == t[-2]:
        return 2
    elif x == t[2]:
        return 3
    else:
        return 4


def use_count(x):
    lst = (1, 3, 3, 2)
    assert lst.count(x) == 2


def use_index(x: tuple):
    assert x.index(0) == 1