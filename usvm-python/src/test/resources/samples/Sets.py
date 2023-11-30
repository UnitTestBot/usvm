def expect_set(x):
    assert isinstance(x, set)


def use_constructor_with_arg(x):
    set(x)