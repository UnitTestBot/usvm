def simple_list_sample(y: list, i: int):
    if y[i] == 0:
        return 1
    elif y[i] == 167:
        return 2
    return 3


def allocated_list_sample(y: int):
    arr = [1, 2, 3, 4]
    if arr[y] == 1:
        return 1
    elif arr[y] == 2:
        return 2
    elif arr[y] == 3:
        return 3
    elif arr[y] == 4:
        return 4


def mixed_allocation(x: int, i: int):
    arr = [1, x, 10, 11, 12]
    if arr[i] == 1 and i != 1 and i >= 0:
        return 1
    elif arr[i] == 1:
        return 2
    elif arr[i] >= 10 and i != 1 and i >= 0:
        return 3
    elif arr[i] == 3:
        return 4
    else:
        return 5


def negative_index(x: int):
    arr = [1, x]
    if arr[-1] == 10:
        return 1
    return 2


def long_list(x: int):
    arr = [1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, x]
    if arr[-1] == 10 ** 9:
        return 1
    return 2


def memory_model(arr1: list, arr2: list):
    arr1[0] = 1
    arr2[0] = 2
    if arr1[0] == arr2[0]:
        return 1
    return 2


def set_item(arr: list, x: int):
    arr[0] = x
    if arr[0] < 0:
        return 0
    elif arr[1] > arr[0] + 500:
        return 1
    return 2


def positive_and_negative_index(y: list, i: int):
    if y[i] == 0:
        if i >= 0:
            return 1
        else:
            return 2
    elif y[i] == 167:
        if i % 10 == 7:
            return 3
        elif i >= 0:
            return 4
        else:
            return 5
    if i >= 0:
        return 6
    else:
        return 7


def len_usage(x: list):
    if len(x) > 5:
        return 1
    return 2


def sum_of_elements(x: list):
    i = 0
    sum_ = 0
    while i < len(x):
        sum_ += x[i]
        i += 1
    if sum_ == 100:
        return 1
    elif sum_ % 200 == 153:
        return 2
    elif sum_ < -100:
        return 3
    return 4


def for_loop(x: list):
    sum_ = 0
    for elem in x:
        sum_ += elem

    if sum_ == 10 ** 5:
        return 1
    elif len(x) == 3 and sum_ < -100:
        return 2
    return 3


def simple_assertion(x: list):
    assert len(x) == 10


def lt(x: list, y: list):
    assert x < y


def gt(x: list, y: list):
    assert x > y


def eq(x: list, y: list):
    assert x == y


def ne(x: list, y: list):
    assert x != y


def le(x: list, y: list):
    assert x <= y


def ge(x: list, y: list):
    assert x >= y


def add_and_compare(x: list, y: list):
    x[0] += 1
    assert x < y


def double_subscript_and_compare(x: list, y: list):
    x[0][0] += 1
    assert x < y


def list_append(x):
    res = []
    res.append(x)
    assert res[-1] == 127


def repeat_1(n: int):
    lst = [10] * n
    assert lst[n - 1] == 10


def repeat_2(x: int):
    lst = [1, 2] * 10
    if lst[x] == 1:
        return 1
    return 2


def input_list_of_float_pairs(x):
    result = 0
    assert len(x) <= 4
    for a, b in x:
        result += a * 0.5 + b
    if result == 10000.5:
        return 1
    return 2


def list_concat(x: list, y: list):
    z = x + y
    assert len(z) == 5


def pop_usage(x: list):
    assert x.pop() == 239


def pop_usage_with_index(x: list):
    assert x.pop(5) == 239


def insert_usage(x: list, elem):
    x.insert(0, elem)
    assert x[0] == 239


def extend_usage(x: list, elem):
    x.extend([1, 2, 3])
    assert x[-1] == elem


def clear_usage(x: int):
    y = [1, 2, 3]
    y.clear()
    assert len(y) == x


def index_usage(x: list):
    if x.index(0) == 1:
        return 1
    return 2


def reverse_usage(x: list):
    x.reverse()
    result = 0
    for i in range(len(x) // 2):
        if x[i] > x[-1]:
            result = 1
        else:
            result = 2
    return result, x


def contains_op(x: list):
    assert 1 in x


def use_constructor(x):
    y = list(x)
    assert y[2] == 15


def list_from_range(x, y, z):
    a = list(range(x, y, z))
    assert a[0] == -239


def use_sort(x: list):
    assert len(x) == 3
    assert x != [1, 2, 3]
    x.sort()
    assert x == [1, 2, 3]


def use_copy(x: list):
    y = x.copy()
    assert y[0] == 239


def use_remove(x):
    lst = [1, 2, 3]
    lst.remove(x)
    assert lst == [1, 2]


def use_count(x):
    lst = [1, 3, 3, 2]
    assert lst.count(x) == 2