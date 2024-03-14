class A:
    def __init__(self, left, right):
        self.left = left
        self.right = right


class B:
    def __init__(self, data):
        self.data_list = data


def f(x):
    # print("left data_list:", x.left.data_list, flush=True)
    if x.left.data_list and x.right.data_list:
        # print("left data_list:", x.left.data_list, flush=True)
        # print("right data_list:", x.right.data_list, flush=True)
        a = x.left.data_list.pop(0)
        b = x.right.data_list.pop(0)
        if a + b == 155:
            return 1
        # print("a:", a, flush=True)
        # print("b:", b, flush=True)
        return 2
    return 3


def g(x):
    # print("left:", x.left, flush=True)
    # print("right:", x.left, flush=True)
    while x.left.data_list and x.right.data_list:
        # print("left data_list:", x.left.data_list, flush=True)
        # print("right data_list:", x.right.data_list, flush=True)
        a = x.left.data_list.pop(0)
        b = x.right.data_list.pop(0)
        if a.pos + b.pos == 155:
            return 1
        # print("a.pos:", a.pos, flush=True)
        # print("b.pos:", b.pos, flush=True)
    # print("(2) left data_list:", x.left.data_list, flush=True)
    return 2