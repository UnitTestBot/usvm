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
    for a, b in x:
        result += a - b
    assert result == 12345


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