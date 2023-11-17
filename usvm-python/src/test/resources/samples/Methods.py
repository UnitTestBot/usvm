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

    @property
    def my_property(self):
        return self.x + 10


def external_function(p):
    return p.get_info()


def set_attribute(p, x: int):
    if p.x != 128:
        return 1
    p.x = x
    if p.x == 239:
        return 2
    return 3


def use_property(x):
    assert isinstance(x, Point)
    assert x.my_property == 25


class ClassWithoutInit:
    field: int


def call_of_object_constructor(value: int):
    obj = ClassWithoutInit()
    obj.field = value
    assert obj.field == 239


def call_of_slot_constructor(x: int, y: int):
    p = Point(x, y)
    return p.get_info()


def call_of_slot_constructor_with_named_args(x: int, y: int):
    p = Point(y=y, x=x)
    return p.get_info()


class ClassWithDefaultValues:
    def __init__(self, a, b=1):
        self.a = a
        self.b = b


def constructor_with_default_values(a: int, x: int):
    obj = ClassWithDefaultValues(a)
    if obj.a == 123:
        return 1
    elif obj.b == x:
        return 2
    return 3


class Point2:
    def get_info(self):
        return ""

    def func(self):
        return ""


def point2_inference(p):
    s = p.get_info()
    return p.func()