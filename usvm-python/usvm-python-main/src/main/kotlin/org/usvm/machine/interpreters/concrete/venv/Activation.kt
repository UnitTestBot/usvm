package org.usvm.machine.interpreters.concrete.venv

// original: https://github.com/pypa/virtualenv/blob/main/src/virtualenv/activation/python/activate_this.py
fun activateThisScript(config: VenvConfig): String =
    """
        import os
        import site
        import sys

        bin_dir = r"${config.binPath.canonicalPath}"
        base = r"${config.basePath.canonicalPath}"

        # prepend bin to PATH (this file is inside the bin directory)
        os.environ["PATH"] = os.pathsep.join([bin_dir, *os.environ.get("PATH", "").split(os.pathsep)])
        os.environ["VIRTUAL_ENV"] = base  # virtual env is right above bin directory

        # add the virtual environments libraries to the host python import mechanism
        prev_length = len(sys.path)
        for lib in r"${config.binPath.toPath().relativize(config.libPath.toPath())}".split(os.pathsep):
            path = os.path.realpath(os.path.join(bin_dir, lib))
            site.addsitedir(path.decode("utf-8") if "" else path)
        sys.path[:] = sys.path[prev_length:] + sys.path[0:prev_length]

        sys.real_prefix = sys.prefix
        sys.prefix = base
    """.trimIndent()
