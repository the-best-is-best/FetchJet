# PythonKit (vendored)

Copy of https://github.com/pvieito/PythonKit at commit fe7d97334600b0fc11fbef5e0525f93eaa68ef7e
(Apache License 2.0, see LICENSE.txt), with one fix in `PythonKit/Python.swift`:

Swift functions called from Python (`PythonFunction`, `PythonInstanceMethod`) received the
`args` tuple and `kwargs` dict with `PythonObject(consuming:)`. CPython passes both as borrowed
references, so every call released them one time too many. Python 3.13 survived it; Python 3.14
crashed (EXC_BAD_ACCESS in `PyObject_CallObject`). They are now taken with `PythonObject(_:)`,
which adds its own reference.
