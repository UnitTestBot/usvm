class ClassWithMatmulAndAdd:
    def __init__(self):
        pass

    def __matmul__(self, other):
        return self

    def __add__(self, other):
        return self


def matmul_usage(x):
    return x @ x


def matmul_and_add(x):
    y = x + 10
    return y @ x