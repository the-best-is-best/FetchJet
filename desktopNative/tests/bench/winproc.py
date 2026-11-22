import ctypes
import subprocess
import threading
import time
from ctypes import wintypes

k32 = ctypes.WinDLL("kernel32", use_last_error=True)
psapi = ctypes.WinDLL("psapi", use_last_error=True)
ntdll = ctypes.WinDLL("ntdll")

HANDLE = wintypes.HANDLE
SIZE_T = ctypes.c_size_t

k32.CreateJobObjectW.restype = HANDLE
k32.CreateJobObjectW.argtypes = [ctypes.c_void_p, wintypes.LPCWSTR]
k32.AssignProcessToJobObject.argtypes = [HANDLE, HANDLE]
k32.AssignProcessToJobObject.restype = wintypes.BOOL
k32.QueryInformationJobObject.argtypes = [HANDLE, ctypes.c_int, ctypes.c_void_p, wintypes.DWORD, ctypes.c_void_p]
k32.QueryInformationJobObject.restype = wintypes.BOOL
k32.TerminateJobObject.argtypes = [HANDLE, wintypes.UINT]
k32.TerminateJobObject.restype = wintypes.BOOL
k32.CloseHandle.argtypes = [HANDLE]
ntdll.NtResumeProcess.argtypes = [HANDLE]
ntdll.NtResumeProcess.restype = ctypes.c_long


class IO_COUNTERS(ctypes.Structure):
    _fields_ = [(n, ctypes.c_ulonglong) for n in ("ro", "wo", "oo", "rb", "wb", "ob")]


class BASIC_LIMIT(ctypes.Structure):
    _fields_ = [
        ("PerProcessUserTimeLimit", ctypes.c_longlong),
        ("PerJobUserTimeLimit", ctypes.c_longlong),
        ("LimitFlags", wintypes.DWORD),
        ("MinimumWorkingSetSize", SIZE_T),
        ("MaximumWorkingSetSize", SIZE_T),
        ("ActiveProcessLimit", wintypes.DWORD),
        ("Affinity", ctypes.c_size_t),
        ("PriorityClass", wintypes.DWORD),
        ("SchedulingClass", wintypes.DWORD),
    ]


class EXT_LIMIT(ctypes.Structure):
    _fields_ = [
        ("Basic", BASIC_LIMIT),
        ("Io", IO_COUNTERS),
        ("ProcessMemoryLimit", SIZE_T),
        ("JobMemoryLimit", SIZE_T),
        ("PeakProcessMemoryUsed", SIZE_T),
        ("PeakJobMemoryUsed", SIZE_T),
    ]


class BASIC_ACCOUNTING(ctypes.Structure):
    _fields_ = [
        ("TotalUserTime", ctypes.c_longlong),
        ("TotalKernelTime", ctypes.c_longlong),
        ("ThisPeriodTotalUserTime", ctypes.c_longlong),
        ("ThisPeriodTotalKernelTime", ctypes.c_longlong),
        ("TotalPageFaultCount", wintypes.DWORD),
        ("TotalProcesses", wintypes.DWORD),
        ("ActiveProcesses", wintypes.DWORD),
        ("TotalTerminatedProcesses", wintypes.DWORD),
    ]


class PMC(ctypes.Structure):
    _fields_ = [
        ("cb", wintypes.DWORD),
        ("PageFaultCount", wintypes.DWORD),
        ("PeakWorkingSetSize", SIZE_T),
        ("WorkingSetSize", SIZE_T),
        ("QuotaPeakPagedPoolUsage", SIZE_T),
        ("QuotaPagedPoolUsage", SIZE_T),
        ("QuotaPeakNonPagedPoolUsage", SIZE_T),
        ("QuotaNonPagedPoolUsage", SIZE_T),
        ("PagefileUsage", SIZE_T),
        ("PeakPagefileUsage", SIZE_T),
        ("PrivateUsage", SIZE_T),
    ]


psapi.GetProcessMemoryInfo.argtypes = [HANDLE, ctypes.POINTER(PMC), wintypes.DWORD]
psapi.GetProcessMemoryInfo.restype = wintypes.BOOL

CREATE_SUSPENDED = 0x4
CREATE_NO_WINDOW = 0x08000000
MB = 1024.0 * 1024.0


class Watched:
    """A child process started suspended inside its own job object, so its peak memory
    and CPU time are known even if it lives for only a few milliseconds."""

    def __init__(self, args, env=None, cwd=None, stdin=None, stdout=subprocess.PIPE, sample_ms=50):
        self.job = k32.CreateJobObjectW(None, None)
        self.proc = subprocess.Popen(
            args, env=env, cwd=cwd, stdin=stdin, stdout=stdout, stderr=subprocess.STDOUT,
            creationflags=CREATE_SUSPENDED | CREATE_NO_WINDOW,
        )
        handle = HANDLE(int(self.proc._handle))
        if not k32.AssignProcessToJobObject(self.job, handle):
            raise OSError("AssignProcessToJobObject failed %d" % ctypes.get_last_error())
        self.handle = handle
        self.spawned = time.time()
        ntdll.NtResumeProcess(handle)
        self.peak_ws = 0
        self.peak_private = 0
        self.samples = 0
        self._stop = False
        self._sample_s = sample_ms / 1000.0
        self._thread = threading.Thread(target=self._sample, daemon=True)
        self._thread.start()

    def memory_now(self):
        counters = PMC()
        counters.cb = ctypes.sizeof(PMC)
        if not psapi.GetProcessMemoryInfo(self.handle, ctypes.byref(counters), counters.cb):
            return None
        return counters

    def _sample(self):
        while not self._stop:
            counters = self.memory_now()
            if counters is not None:
                self.samples += 1
                self.peak_ws = max(self.peak_ws, counters.PeakWorkingSetSize)
                self.peak_private = max(self.peak_private, counters.PrivateUsage)
            time.sleep(self._sample_s)

    def now_mb(self):
        counters = self.memory_now()
        if counters is None:
            return None
        return {"ws": counters.WorkingSetSize / MB, "private": counters.PrivateUsage / MB}

    def stats(self):
        ext = EXT_LIMIT()
        k32.QueryInformationJobObject(self.job, 9, ctypes.byref(ext), ctypes.sizeof(ext), None)
        acc = BASIC_ACCOUNTING()
        k32.QueryInformationJobObject(self.job, 1, ctypes.byref(acc), ctypes.sizeof(acc), None)
        return {
            "peak_commit_mb": ext.PeakProcessMemoryUsed / MB,
            "peak_ws_sampled_mb": self.peak_ws / MB,
            "peak_private_sampled_mb": self.peak_private / MB,
            "cpu_s": (acc.TotalUserTime + acc.TotalKernelTime) / 1e7,
            "samples": self.samples,
        }

    def kill(self):
        k32.TerminateJobObject(self.job, 1)

    def close(self):
        self._stop = True
        self._thread.join(1.0)
        k32.CloseHandle(self.job)
