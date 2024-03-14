def use_enumerate(x):
    sum_ = 0
    for i, a in enumerate(x):
        sum_ += i + a
    assert sum_ == 239