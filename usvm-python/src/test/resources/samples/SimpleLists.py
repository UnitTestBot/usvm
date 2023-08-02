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
    if arr[i] == 1 and i != 1:
        return 1
    elif arr[i] == 1:
        return 2
    elif arr[i] >= 10 and i != 1:
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


def memory_model(arr1: list[int], arr2: list[int]):
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
