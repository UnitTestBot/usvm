def expect_dict(x):
    assert isinstance(x, dict)


def input_dict_int_get_item(d: dict):
    assert d[15] == 10


def input_dict_virtual_get_item(d: dict, i):
    assert d[i] == 10


def input_dict_str_get_item(d: dict):
    assert d["my_key"] == 155


def allocate_dict(x, y):
    d = {x: 15}
    assert d[x] == y


def allocate_dict_with_int_key(x, y):
    d = {25: 15, y: 0}
    assert d[x] == 15


def allocate_const_key_dict(x):
    d = {25: 15, 10: 0}
    assert d[x] == 15


def dict_int_set_item(x, y):
    d = {}
    d[10] = 10
    assert d[x] == y


def dict_str_set_item(x, y):
    d = {}
    d["hello"] = 10
    assert d[x] == y


def dict_virtual_set_item(x, y):
    d = {}
    d[x] = 10
    assert d[155] == y