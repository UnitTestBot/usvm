def field_start(x):
    assert x.start == 157


def field_stop(x):
    assert x.stop == 157


def field_step(x):
    assert x.step == 157


def none_fields(x):
    if x.start is None:
        return 1
    elif x.stop is not None and x.stop + 10 > 100 and x.step is None:
        return 2
    elif x.stop is None and x.step is not None and x.step < 0:
        return 3
    elif x.stop is not None:
        return 4
    elif x.step is None:
        return 5
    return 6


def sum_of_sublist(x):
    lst = [1, 2, 3, 4]
    sum_ = sum(lst[x:])
    if sum_ == 0:
        return 1
    elif sum_ == 4:
        return 2
    elif sum_ == 7:
        return 3
    elif sum_ == 9:
        return 4
    elif sum_ == 10:
        return 5


def slice_usages(x: int, y: int, z: int):
    size = 10
    lst = []
    for i in range(size):
        lst.append(i)
    return lst[x:y:z]


def element_constraints_sample(sequence: list):
    for x in sequence:
        assert isinstance(x, int)
    return sequence[:-1]
