def simple_generator(x: int):
    if x == 1:
        yield "one"
    elif x == 2:
        yield "two"
    else:
        yield "other"


def generator_usage(x: int):
    for elem in simple_generator(x):
        return elem