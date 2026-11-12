import ctypes
import os
import shutil
import subprocess
import sys
import threading
import time
import urllib.parse

from gi.repository import Gio, GLib

HOST = os.environ.get("FJ_HOST_EXE", os.path.expanduser("~/fetchjet-native/host-build/FetchJet"))
WORK = os.environ.get("FJ_TRAY_WORK", os.path.expanduser("~/fetchjet-native/work/tray"))
PICKED = os.path.join(WORK, "مجلد مختار")

WATCHER_XML = """<node><interface name="org.kde.StatusNotifierWatcher">
<method name="RegisterStatusNotifierItem"><arg type="s" name="service" direction="in"/></method>
<method name="RegisterStatusNotifierHost"><arg type="s" name="service" direction="in"/></method>
<property name="RegisteredStatusNotifierItems" type="as" access="read"/>
<property name="IsStatusNotifierHostRegistered" type="b" access="read"/>
<property name="ProtocolVersion" type="i" access="read"/>
</interface></node>"""

PORTAL_XML = """<node><interface name="org.freedesktop.portal.FileChooser">
<method name="OpenFile"><arg type="s" name="parent" direction="in"/><arg type="s" name="title" direction="in"/>
<arg type="a{sv}" name="options" direction="in"/><arg type="o" name="handle" direction="out"/></method>
<property name="version" type="u" access="read"/>
</interface></node>"""

checks = 0
failures = []
registered = []
portal_calls = []


def check(name, ok, detail=""):
    global checks
    checks += 1
    if not ok:
        failures.append(name)
    print(("  ok    " if ok else "  FAIL  ") + name + ("" if ok or detail == "" else "  [%s]" % (detail,)))
    sys.stdout.flush()


def wait_for(condition, seconds):
    end = time.time() + seconds
    while time.time() < end:
        if condition():
            return True
        time.sleep(0.1)
    return condition()


class Services:
    def __init__(self):
        self.bus = Gio.bus_get_sync(Gio.BusType.SESSION, None)
        self.context = GLib.MainContext.new()
        self.loop = GLib.MainLoop.new(self.context, False)
        ready = threading.Event()
        threading.Thread(target=self.run, args=(ready,), daemon=True).start()
        ready.wait(5)

    def run(self, ready):
        self.context.push_thread_default()
        watcher = Gio.DBusNodeInfo.new_for_xml(WATCHER_XML).interfaces[0]
        portal = Gio.DBusNodeInfo.new_for_xml(PORTAL_XML).interfaces[0]
        self.bus.register_object("/StatusNotifierWatcher", watcher, self.watcher_call, self.watcher_property, None)
        self.bus.register_object("/org/freedesktop/portal/desktop", portal, self.portal_call, self.portal_property, None)
        for name in ("org.kde.StatusNotifierWatcher", "org.freedesktop.portal.Desktop"):
            self.bus.call_sync("org.freedesktop.DBus", "/org/freedesktop/DBus", "org.freedesktop.DBus", "RequestName",
                               GLib.Variant("(su)", (name, 4)), None, Gio.DBusCallFlags.NONE, 3000, None)
        ready.set()
        self.loop.run()

    def watcher_call(self, connection, sender, path, interface, method, params, invocation):
        if method == "RegisterStatusNotifierItem":
            registered.append((sender, params.unpack()[0]))
        invocation.return_value(None)

    def watcher_property(self, connection, sender, path, interface, name):
        if name == "RegisteredStatusNotifierItems":
            return GLib.Variant("as", [item[1] for item in registered])
        if name == "IsStatusNotifierHostRegistered":
            return GLib.Variant("b", True)
        return GLib.Variant("i", 0)

    def portal_call(self, connection, sender, path, interface, method, params, invocation):
        parent, title, options = params.unpack()
        portal_calls.append((title, options))
        handle = "/org/freedesktop/portal/desktop/request/%s/%s" % (
            sender[1:].replace(".", "_"), options.get("handle_token", "t"))
        invocation.return_value(GLib.Variant("(o)", (handle,)))
        uri = "file://" + urllib.parse.quote(PICKED)

        def respond():
            self.bus.emit_signal(None, handle, "org.freedesktop.portal.Request", "Response",
                                 GLib.Variant("(ua{sv})", (0, {"uris": GLib.Variant("as", [uri])})))
            return False

        source = GLib.timeout_source_new(700)
        source.set_callback(lambda *_: respond())
        source.attach(self.context)

    def portal_property(self, connection, sender, path, interface, name):
        return GLib.Variant("u", 4)

    def call(self, name, path, interface, method, args, reply=None):
        return self.bus.call_sync(name, path, interface, method, args,
                                  GLib.VariantType.new(reply) if reply else None, Gio.DBusCallFlags.NONE, 5000, None)

    def prop(self, name, path, interface, prop):
        return self.call(name, path, "org.freedesktop.DBus.Properties", "Get", GLib.Variant("(ss)", (interface, prop)),
                         "(v)").unpack()[0]


class Attrs(ctypes.Structure):
    _fields_ = [("x", ctypes.c_int), ("y", ctypes.c_int), ("width", ctypes.c_int), ("height", ctypes.c_int),
                ("border_width", ctypes.c_int), ("depth", ctypes.c_int), ("visual", ctypes.c_void_p),
                ("root", ctypes.c_ulong), ("class_", ctypes.c_int), ("bit_gravity", ctypes.c_int),
                ("win_gravity", ctypes.c_int), ("backing_store", ctypes.c_int), ("backing_planes", ctypes.c_ulong),
                ("backing_pixel", ctypes.c_ulong), ("save_under", ctypes.c_int), ("colormap", ctypes.c_ulong),
                ("map_installed", ctypes.c_int), ("map_state", ctypes.c_int), ("all_event_masks", ctypes.c_long),
                ("your_event_mask", ctypes.c_long), ("do_not_propagate_mask", ctypes.c_long),
                ("override_redirect", ctypes.c_int), ("screen", ctypes.c_void_p)]


def window_visible():
    try:
        x = ctypes.CDLL("libX11.so.6")
    except OSError:
        return None
    x.XOpenDisplay.restype = ctypes.c_void_p
    x.XOpenDisplay.argtypes = [ctypes.c_char_p]
    x.XDefaultRootWindow.restype = ctypes.c_ulong
    x.XDefaultRootWindow.argtypes = [ctypes.c_void_p]
    x.XQueryTree.argtypes = [ctypes.c_void_p, ctypes.c_ulong, ctypes.POINTER(ctypes.c_ulong), ctypes.POINTER(ctypes.c_ulong),
                             ctypes.POINTER(ctypes.POINTER(ctypes.c_ulong)), ctypes.POINTER(ctypes.c_uint)]
    x.XFetchName.argtypes = [ctypes.c_void_p, ctypes.c_ulong, ctypes.POINTER(ctypes.c_char_p)]
    x.XGetWindowAttributes.argtypes = [ctypes.c_void_p, ctypes.c_ulong, ctypes.POINTER(Attrs)]
    x.XCloseDisplay.argtypes = [ctypes.c_void_p]
    display = x.XOpenDisplay(None)
    if not display:
        return None

    def walk(window):
        name = ctypes.c_char_p()
        if x.XFetchName(display, window, ctypes.byref(name)) and name.value and b"FetchJet" in name.value:
            attrs = Attrs()
            x.XGetWindowAttributes(display, window, ctypes.byref(attrs))
            if attrs.map_state == 2 and attrs.width > 100:
                return True
        root, parent = ctypes.c_ulong(), ctypes.c_ulong()
        children = ctypes.POINTER(ctypes.c_ulong)()
        count = ctypes.c_uint()
        if x.XQueryTree(display, window, ctypes.byref(root), ctypes.byref(parent), ctypes.byref(children), ctypes.byref(count)):
            for i in range(count.value):
                if walk(children[i]):
                    return True
        return False

    try:
        return walk(x.XDefaultRootWindow(display))
    finally:
        x.XCloseDisplay(display)


def labels(layout):
    found = []

    def walk(node):
        item_id, props, children = node
        found.append((item_id, props))
        for child in children:
            walk(child)

    walk(layout)
    return found


def main():
    if not os.environ.get("DBUS_SESSION_BUS_ADDRESS"):
        raise SystemExit("run inside dbus-run-session")
    shutil.rmtree(WORK, ignore_errors=True)
    home = os.path.join(WORK, "home")
    os.makedirs(os.path.join(home, "dl"))
    os.makedirs(PICKED)
    services = Services()
    env = dict(os.environ)
    env.pop("WAYLAND_DISPLAY", None)
    env.setdefault("FETCHJET_THEME", "light")
    log = open(os.path.join(WORK, "host.log"), "w")
    process = subprocess.Popen([HOST, "--home", home, "--downloads", os.path.join(home, "dl"), "--no-bridge", "--no-focus",
                                "--no-auto-downloads", "--action", '{"type":"SettingClick","id":"chooseFolder"}'],
                               stdout=log, stderr=subprocess.STDOUT, env=env)
    try:
        check("the item registers with the watcher", wait_for(lambda: len(registered) > 0, 15), registered)
        service = registered[0][1] if registered else ""
        check("the item owns a StatusNotifierItem name", service.startswith("org.kde.StatusNotifierItem-%d-" % process.pid),
              service)
        item = ("org.kde.StatusNotifierItem", "/StatusNotifierItem")
        title = services.prop(service, item[1], item[0], "Title")
        check("Title is FetchJet", title == "FetchJet", title)
        check("Id is fetchjet", services.prop(service, item[1], item[0], "Id") == "fetchjet")
        menu_path = services.prop(service, item[1], item[0], "Menu")
        check("Menu points at the dbusmenu object", menu_path == "/MenuBar", menu_path)
        tip = services.prop(service, item[1], item[0], "ToolTip")
        check("the tooltip carries the app's text", tip[2] == "FetchJet" and "FetchJet" in tip[3], tip)
        check("the window is shown at start", wait_for(lambda: window_visible() is True, 10), window_visible())

        dbusmenu = "com.canonical.dbusmenu"
        check("dbusmenu Version 3", services.prop(service, "/MenuBar", dbusmenu, "Version") == 3)
        revision, layout = services.call(service, "/MenuBar", dbusmenu, "GetLayout", GLib.Variant("(iias)", (0, -1, [])),
                                         "(u(ia{sv}av))").unpack()
        items = labels(layout)
        texts = [props.get("label", "") for _, props in items]
        check("menu has Show, a separator and Close", texts[1:2] == ["Show App"] and
              any(props.get("type") == "separator" for _, props in items) and items[-1][1].get("label") == "Close App", texts)
        choices = [(item_id, props) for item_id, props in items if props.get("toggle-type") == "radio"]
        check("the When done submenu lists its choices as radio items", len(choices) >= 3 and choices[0][1].get("toggle-state") == 1,
              choices)
        props = services.call(service, "/MenuBar", dbusmenu, "GetGroupProperties", GLib.Variant("(aias)", ([1, 4], [])),
                              "(a(ia{sv}))").unpack()[0]
        check("GetGroupProperties answers for the asked ids", sorted(p[0] for p in props) == [1, 4], props)

        def settings_text():
            for root, _, names in os.walk(home):
                if "settings.properties" in names:
                    return open(os.path.join(root, "settings.properties"), encoding="utf-8").read()
            return ""
        check("the portal was asked for a folder", wait_for(lambda: len(portal_calls) > 0, 10), portal_calls)
        if portal_calls:
            check("the portal call asks for a directory", portal_calls[0][1].get("directory") is True, portal_calls[0][1])
        check("the folder picked in the portal becomes the download folder",
              wait_for(lambda: "destinationPath=" + PICKED in settings_text(), 10),
              settings_text()[:300] or [os.path.join(r, n) for r, _, ns in os.walk(home) for n in ns][:20])

        services.call(service, item[1], item[0], "Activate", GLib.Variant("(ii)", (0, 0)))
        check("Activate hides the window (the tray keeps the app)", wait_for(lambda: window_visible() is False, 5), window_visible())
        check("the app is still running while hidden", process.poll() is None)
        time.sleep(0.6)
        services.call(service, item[1], item[0], "Activate", GLib.Variant("(ii)", (0, 0)))
        check("Activate again shows it", wait_for(lambda: window_visible() is True, 5), window_visible())
        services.call(service, "/MenuBar", dbusmenu, "Event", GLib.Variant("(isvu)", (1, "clicked", GLib.Variant("i", 0), 0)))
        time.sleep(0.5)
        check("Show from the menu keeps it shown", window_visible() is True and process.poll() is None)

        services.call(service, "/MenuBar", dbusmenu, "Event", GLib.Variant("(isvu)", (4, "clicked", GLib.Variant("i", 0), 0)))
        check("Close from the menu quits the app", wait_for(lambda: process.poll() is not None, 10), process.poll())
        check("it exits with code 0", process.poll() == 0, process.poll())
    finally:
        if process.poll() is None:
            process.kill()
            process.wait()
        log.close()
    print(open(os.path.join(WORK, "host.log"), encoding="utf-8", errors="replace").read()[-1500:])
    print("%d checks, %d failed" % (checks, len(failures)))
    sys.exit(1 if failures else 0)


if __name__ == "__main__":
    main()
