import json
import os
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from winproc import Watched  # noqa: E402

code = "import sys,time; sys.path.insert(0, r'%s'); import corehost; print('up', flush=True); time.sleep(3)" % (HERE + "/../corehost")
env = dict(os.environ)
env["FETCHJET_NO_USAGE_PING"] = "1"
w = Watched([sys.executable, "-c", code], env=env, cwd="E:/tools/bench/out")
time.sleep(2.0)
now = w.now_mb()
w.proc.wait()
print(json.dumps({"python_with_corehost_import_idle": now, **w.stats()}))
