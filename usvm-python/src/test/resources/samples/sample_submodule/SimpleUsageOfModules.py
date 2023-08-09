import sample_submodule.structures as structures


def construct_class_instance(x: int):
    if x < 0:
        x = -x
    return structures.SimpleClass(x)


def inner_import(x: int):
    import sample_submodule.sample_functions as module
    return module.my_abs(x)