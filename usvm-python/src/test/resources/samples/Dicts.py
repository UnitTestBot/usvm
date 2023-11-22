def expect_dict(x):
    assert isinstance(x, dict)


def input_dict_int_get_item(d: dict):
    assert d[15] == 10


def input_dict_virtual_get_item(d: dict, i):
    assert d[i] == 10


def input_dict_str_get_item(d: dict):
    assert d["my_key"] == 155


def allocate_dict(x, y: int):
    d = {x: 15}
    assert d[x] == y