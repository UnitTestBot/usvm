def expect_set(x):
    assert isinstance(x, set)


def use_constructor_with_arg(x):
    set(x)


def input_set_int_check(s: set):
    assert 1 in s


def input_set_str_check(s: set):
    assert "aaa" in s


def input_set_virtual_check(s: set, x):
    assert x in s


def construct_set_with_call(x: int):
    s = set([1, 2, 3])
    assert x in s


def add_str_to_set(x):
    s = set()
    s.add("bbb")
    assert x in s

# TODO: from frosetset
def construct_set_with_syntax(x, y):
    s = {y}
    assert x in s


def empty_check(s: set):
    assert s