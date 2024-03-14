class RawCommand:
    def __init__(self, cmd):
        self.cmd = cmd

    def run(self, globals_, locals_):
        assert globals_ is not None
        assert locals_ is not None
        eval(self.cmd, globals_, locals_)


def run(commands, globals_, locals_):
    for cmd in commands:
        cmd.run(globals_, locals_)