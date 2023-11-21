def expect_dict(x):
    assert isinstance(x, dict)


def input_dict_int_get_item(d: dict):
    assert d[15] == 10


def input_dict_virtual_get_item(d: dict, i):
    assert d[i] == 10


def input_dict_str_get_item(d: dict):
    assert d["my_key"] == 155


def allocate_dict(x: int, y: int):
    d = {x: 15}
    assert d[x] == y


def dfs(g, s):
    vis, _s = {s}, [s]
    print(s)
    while _s:
        flag = 0
        for i in g[_s[-1]]:
            if i not in vis:
                _s.append(i)
                vis.add(i)
                flag = 1
                print(i)
                break
        if not flag:
            _s.pop()