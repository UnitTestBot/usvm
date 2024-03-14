import sample_submodule.structures as structures


def construct_class_instance(x: int):
    if x < 0:
        x = -x
    return structures.SimpleClass(x)


def inner_import(x: int):
    import sample_submodule.sample_functions as module
    return module.my_abs(x)


def simple_class_isinstance(x):
    if isinstance(x, structures.SimpleClass):
        return 1
    elif isinstance(x, int):
        assert x > 100
        return 2
    return 3
