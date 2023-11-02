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


def external_function(p):
    return p.get_info()