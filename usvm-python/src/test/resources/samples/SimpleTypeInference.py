def bool_input(x):
    if x:
        return 1
    else:
        return 2


def int_convertation(x):
    y = int(x)
    return y


def two_args(x, y):
    if x and y:
        return 1
    elif x:
        return 2
    elif y:
        return 3
    else:
        return 4