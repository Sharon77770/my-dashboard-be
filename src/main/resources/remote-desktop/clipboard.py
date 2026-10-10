"""Own the managed X11 clipboard in memory, without packages or text on disk."""
import ctypes as c
import json
import os
from pathlib import Path
import select
import sys
import time


class SelectionRequest(c.Structure):
    _fields_ = [("type", c.c_int), ("serial", c.c_ulong), ("send_event", c.c_int),
                ("display", c.c_void_p), ("owner", c.c_ulong), ("requestor", c.c_ulong),
                ("selection", c.c_ulong), ("target", c.c_ulong), ("property", c.c_ulong), ("time", c.c_ulong)]


class SelectionNotify(c.Structure):
    _fields_ = [("type", c.c_int), ("serial", c.c_ulong), ("send_event", c.c_int),
                ("display", c.c_void_p), ("requestor", c.c_ulong), ("selection", c.c_ulong),
                ("target", c.c_ulong), ("property", c.c_ulong), ("time", c.c_ulong)]


class Event(c.Union):
    _fields_ = [("type", c.c_int), ("request", SelectionRequest),
                ("notify", SelectionNotify), ("padding", c.c_long * 24)]


def managed_display(port):
    root = Path.home() / ".personal-dashboard-desktop"
    if root.is_symlink() or root.stat().st_uid != os.getuid():
        raise ValueError("Invalid desktop")
    state_path = root / "state.json"
    if state_path.is_symlink():
        raise ValueError("Invalid state")
    state = json.loads(state_path.read_text())
    if not 5920 <= port <= 5999 or state["port"] != port:
        raise ValueError("Invalid port")
    args = Path(f"/proc/{int(state['pid'])}/cmdline").read_bytes().split(b"\0")
    if str(root / "passwd").encode() not in args:
        raise ValueError("Desktop stopped")
    return f":{port - 5900}"


def serve(display_name, text, ready):
    """Exit on selection replacement or after 30 minutes; never log clipboard data."""
    x = c.CDLL("libX11.so.6")
    signatures = {
        "XOpenDisplay": ([c.c_char_p], c.c_void_p),
        "XDefaultRootWindow": ([c.c_void_p], c.c_ulong),
        "XCreateSimpleWindow": ([c.c_void_p, c.c_ulong, c.c_int, c.c_int, c.c_uint, c.c_uint, c.c_uint, c.c_ulong, c.c_ulong], c.c_ulong),
        "XInternAtom": ([c.c_void_p, c.c_char_p, c.c_int], c.c_ulong),
        "XSetSelectionOwner": ([c.c_void_p, c.c_ulong, c.c_ulong, c.c_ulong], c.c_int),
        "XGetSelectionOwner": ([c.c_void_p, c.c_ulong], c.c_ulong),
        "XChangeProperty": ([c.c_void_p, c.c_ulong, c.c_ulong, c.c_ulong, c.c_int, c.c_int, c.c_void_p, c.c_int], c.c_int),
        "XSendEvent": ([c.c_void_p, c.c_ulong, c.c_int, c.c_long, c.POINTER(Event)], c.c_int),
        "XPending": ([c.c_void_p], c.c_int),
        "XNextEvent": ([c.c_void_p, c.POINTER(Event)], c.c_int),
        "XConnectionNumber": ([c.c_void_p], c.c_int),
        "XFlush": ([c.c_void_p], c.c_int),
        "XCloseDisplay": ([c.c_void_p], c.c_int),
    }
    for name, (arguments, result) in signatures.items():
        function = getattr(x, name)
        function.argtypes, function.restype = arguments, result
    display = x.XOpenDisplay(display_name.encode())
    if not display:
        raise ValueError("Display unavailable")
    try:
        window = x.XCreateSimpleWindow(display, x.XDefaultRootWindow(display), 0, 0, 1, 1, 0, 0, 0)
        atoms = {name: x.XInternAtom(display, name.encode(), 0) for name in
                 ("CLIPBOARD", "TARGETS", "UTF8_STRING", "STRING", "TEXT", "text/plain;charset=utf-8", "ATOM")}
        x.XSetSelectionOwner(display, atoms["CLIPBOARD"], window, 0)
        if x.XGetSelectionOwner(display, atoms["CLIPBOARD"]) != window:
            raise ValueError("Clipboard unavailable")
        os.write(ready, b"READY\n")
        os.close(ready)
        deadline = time.monotonic() + 1800
        while time.monotonic() < deadline:
            if not x.XPending(display):
                select.select([x.XConnectionNumber(display)], [], [], 1)
                continue
            event = Event()
            x.XNextEvent(display, c.byref(event))
            if event.type == 29:  # SelectionClear: another app now owns the clipboard.
                break
            if event.type != 30:
                continue
            request = event.request
            property_atom = request.property or request.target
            if request.target == atoms["TARGETS"]:
                supported = (c.c_ulong * 5)(*(atoms[name] for name in ("TARGETS", "UTF8_STRING", "STRING", "TEXT", "text/plain;charset=utf-8")))
                x.XChangeProperty(display, request.requestor, property_atom, atoms["ATOM"], 32, 0, supported, 5)
            elif request.target in (atoms["UTF8_STRING"], atoms["STRING"], atoms["TEXT"], atoms["text/plain;charset=utf-8"]):
                data = text.encode("latin-1", errors="replace") if request.target == atoms["STRING"] else text.encode("utf-8")
                data_type = atoms["UTF8_STRING"] if request.target == atoms["TEXT"] else request.target
                x.XChangeProperty(display, request.requestor, property_atom, data_type, 8, 0, c.c_char_p(data), len(data))
            else:
                property_atom = 0
            reply = Event()
            reply.notify = SelectionNotify(31, 0, 1, display, request.requestor, request.selection, request.target, property_atom, request.time)
            x.XSendEvent(display, request.requestor, 0, 0, c.byref(reply))
            x.XFlush(display)
    finally:
        x.XCloseDisplay(display)


def main():
    request = json.loads(sys.stdin.readline(400000))
    text = request["text"]
    if not isinstance(text, str) or len(text) > 32000 or "\0" in text:
        raise ValueError("Invalid text")
    display = managed_display(request["port"])
    reader, writer = os.pipe()
    child = os.fork()
    if child == 0:
        os.close(reader)
        os.setsid()
        # Release SSH's stdio; clipboard bytes exist only in the child process memory.
        with open(os.devnull, "r+b", buffering=0) as null:
            for descriptor in (0, 1, 2):
                os.dup2(null.fileno(), descriptor)
        try:
            serve(display, text, writer)
        finally:
            os._exit(0)
    os.close(writer)
    if not select.select([reader], [], [], 5)[0] or os.read(reader, 32) != b"READY\n":
        os.kill(child, 15)
        raise ValueError("Clipboard unavailable")
    os.close(reader)
    print("READY", flush=True)


if __name__ == "__main__":
    try:
        main()
    except Exception:
        print("FAILED", flush=True)
        sys.exit(1)
