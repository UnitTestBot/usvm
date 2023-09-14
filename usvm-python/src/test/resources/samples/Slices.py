def field_start(x):
    assert x.start == 157


def field_stop(x):
    assert x.stop == 157


def field_step(x):
    assert x.step == 157


def none_fields(x):
    if x.start is None:
        return 1
    elif x.stop is not None and x.stop + 10 > 100 and x.step is None:
        return 2
    elif x.stop is None and x.step is not None and x.step < 0:
        return 3
    elif x.stop is not None:
        return 4
    elif x.step is None:
        return 5
    return 6
