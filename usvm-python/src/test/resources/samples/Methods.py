class Point:
    def __init__(self, x, y):
        self.x = x
        self.y = y

    def get_info(self):
        if self.x == self.y:
            return "same"
        elif self.x < self.y:
            return "x is less that y"
        else:
            return "y is less that x"


def external_function(p):
    return p.get_info()


def set_attribute(p, x: int):
    p.x = x
    assert p.x == 239


class ClassWithoutInit:
    field: int


def call_of_object_constructor(value: int):
    obj = ClassWithoutInit()
    obj.field = value
    assert obj.field == 239


def call_of_slot_constructor(x: int, y: int):
    p = Point(x, y)
    return p.get_info()