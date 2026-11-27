#include <windows.h>

typedef VOID(WINAPI *fjt_time_fn)(LPFILETIME);

static VOID WINAPI fjt_precise_time(LPFILETIME out) {
    static fjt_time_fn real;
    static volatile LONG looked;
    if (!looked) {
        real = (fjt_time_fn) (void *) GetProcAddress(GetModuleHandleW(L"kernel32.dll"), "GetSystemTimePreciseAsFileTime");
        InterlockedExchange(&looked, 1);
    }
    if (real)
        real(out);
    else
        GetSystemTimeAsFileTime(out);
}

fjt_time_fn __imp_GetSystemTimePreciseAsFileTime = fjt_precise_time;
