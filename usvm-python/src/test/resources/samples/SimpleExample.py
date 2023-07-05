import pickle


def f(x: int, y: int, z: int):
    if x + y > 100:
        return 0
    y += 10 ** 9
    if 0 < x + z + 1 < 100 and y > 0:
        return 1
    elif x + 3 < -2 - z and x < y:
        return 2
    elif x * 100 % 7 == 0 and z + y % 100 == 0:
        return 3
    elif x % 155 == 0 and x + y - z < 0:
        return 4
    elif (x + z) % 10 == 0 and x + y > 0:
        return 5
    elif (x - 10 ** 8 + y) * 50 % 9 == 0 and y // 88 == 5:
        return 6
    elif z == 15789 and y + x > 10 ** 9:
        return 7
    elif x + y + z == -10 ** 9 and x != 0 and z == 2598:
        return 8
    else:
        return 9


def my_abs(x: int):
    if x > 0:
        return x
    elif x == 0:
        return "zero"
    else:
        return -x


def pickle_sample(x):
    return pickle.dumps(x)

def call(x: int):
    return my_abs(x)


def zero_division(x: int):
    return x / 0