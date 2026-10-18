"""Windows desktop diagnostic: a screen shortcut must not show a console.

Runs the supplied .lnk once, watches native window-show events for 3 seconds,
and reports only window classes/PIDs (no window titles or screen contents).
"""
import ctypes as c
from ctypes import wintypes as w
from pathlib import Path
import sys
import time

user = c.WinDLL("user32", use_last_error=True)
shell = c.WinDLL("shell32", use_last_error=True)
callback_type = c.WINFUNCTYPE(None, w.HANDLE, w.DWORD, w.HWND, w.LONG, w.LONG, w.DWORD, w.DWORD)
user.SetWinEventHook.argtypes = [w.DWORD, w.DWORD, w.HMODULE, callback_type, w.DWORD, w.DWORD, w.DWORD]
user.SetWinEventHook.restype = w.HANDLE
user.UnhookWinEvent.argtypes = [w.HANDLE]
user.GetClassNameW.argtypes = [w.HWND, w.LPWSTR, c.c_int]
user.GetWindowThreadProcessId.argtypes = [w.HWND, c.POINTER(w.DWORD)]
shell.ShellExecuteW.argtypes = [w.HWND, w.LPCWSTR, w.LPCWSTR, w.LPCWSTR, w.LPCWSTR, c.c_int]
shell.ShellExecuteW.restype = w.HINSTANCE
observed = set()

@callback_type
def on_show(hook, event, hwnd, object_id, child_id, thread_id, stamp):
    if object_id != 0 or child_id != 0:
        return
    name = c.create_unicode_buffer(256)
    user.GetClassNameW(hwnd, name, len(name))
    if name.value in ("ConsoleWindowClass", "CASCADIA_HOSTING_WINDOW_CLASS"):
        pid = w.DWORD()
        user.GetWindowThreadProcessId(hwnd, c.byref(pid))
        observed.add((name.value, pid.value))

link = str(Path(sys.argv[1]).resolve(strict=True))
hook = user.SetWinEventHook(0x8002, 0x8002, None, on_show, 0, 0, 0)
if not hook:
    raise c.WinError(c.get_last_error())
try:
    result = shell.ShellExecuteW(None, "open", link, None, None, 1)
    assert result and result > 32, f"Shortcut launch failed: {result}"
    deadline = time.monotonic() + 3
    message = w.MSG()
    while time.monotonic() < deadline:
        while user.PeekMessageW(c.byref(message), None, 0, 0, 1):
            user.TranslateMessage(c.byref(message))
            user.DispatchMessageW(c.byref(message))
        time.sleep(0.001)
finally:
    user.UnhookWinEvent(hook)
print(f"visible_console_windows={len(observed)} {sorted(observed)}")
assert not observed, "A console window appeared while selecting a screen"
