#include "tray_linux.h"

#include "../app_host.h"

#include <dlfcn.h>
#include <unistd.h>

#include <atomic>
#include <chrono>
#include <condition_variable>
#include <cstddef>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <mutex>
#include <thread>

namespace fjhost::os::tray {

    namespace {

        struct GError {
            uint32_t domain;
            int code;
            char *message;
        };

        using MethodCall = void (*)(void *, const char *, const char *, const char *, const char *, void *, void *, void *);
        using GetProperty = void *(*)(void *, const char *, const char *, const char *, const char *, GError **, void *);
        using SetProperty = int (*)(void *, const char *, const char *, const char *, const char *, void *, GError **, void *);

        struct VTable {
            MethodCall method_call;
            GetProperty get_property;
            SetProperty set_property;
            void *padding[8];
        };

        struct Glib {
            void *(*main_context_new)();
            void (*main_context_push_thread_default)(void *);
            void *(*main_loop_new)(void *, int);
            void (*main_loop_run)(void *);
            void (*main_loop_quit)(void *);
            void (*main_context_invoke)(void *, int (*)(void *), void *);
            void *(*bus_get_sync)(int, void *, GError **);
            void *(*node_info_new_for_xml)(const char *, GError **);
            void *(*node_info_lookup_interface)(void *, const char *);
            unsigned (*register_object)(void *, const char *, void *, const VTable *, void *, void (*)(void *), GError **);
            unsigned (*watch_name)(void *, const char *, int, void (*)(void *, const char *, const char *, void *),
                                   void (*)(void *, const char *, void *), void *, void (*)(void *));
            void *(*call_sync)(void *, const char *, const char *, const char *, const char *, void *, const void *, int, int,
                               void *, GError **);
            int (*emit_signal)(void *, const char *, const char *, const char *, const char *, void *, GError **);
            void (*return_value)(void *, void *);
            void *(*variant_parse)(const void *, const char *, const char *, const char **, GError **);
            void (*variant_unref)(void *);
            void *(*variant_get_child_value)(void *, size_t);
            int (*variant_get_int32)(void *);
            const char *(*variant_get_string)(void *, size_t *);
            size_t (*variant_n_children)(void *);
            void (*error_free)(GError *);
        };

        Glib g{};
        std::atomic<void *> g_context{nullptr};
        void *g_loop = nullptr;
        void *g_bus = nullptr;
        std::atomic<bool> g_on{false};
        std::atomic<bool> g_started{false};
        std::mutex g_lock;
        std::condition_variable g_decided_signal;
        bool g_decided = false;
        Menu g_menu;
        uint32_t g_revision = 1;
        std::string g_icon_folder;
        std::string g_name;

        constexpr int WHEN_DONE_BASE = 100;
        constexpr const char *ITEM_PATH = "/StatusNotifierItem";
        constexpr const char *MENU_PATH = "/MenuBar";
        constexpr const char *ITEM_IFACE = "org.kde.StatusNotifierItem";
        constexpr const char *MENU_IFACE = "com.canonical.dbusmenu";

        constexpr const char *INTROSPECTION = R"XML(<node>
 <interface name="org.kde.StatusNotifierItem">
  <property name="Category" type="s" access="read"/>
  <property name="Id" type="s" access="read"/>
  <property name="Title" type="s" access="read"/>
  <property name="Status" type="s" access="read"/>
  <property name="WindowId" type="i" access="read"/>
  <property name="IconName" type="s" access="read"/>
  <property name="IconThemePath" type="s" access="read"/>
  <property name="ToolTip" type="(sa(iiay)ss)" access="read"/>
  <property name="ItemIsMenu" type="b" access="read"/>
  <property name="Menu" type="o" access="read"/>
  <method name="ContextMenu"><arg name="x" type="i" direction="in"/><arg name="y" type="i" direction="in"/></method>
  <method name="Activate"><arg name="x" type="i" direction="in"/><arg name="y" type="i" direction="in"/></method>
  <method name="SecondaryActivate"><arg name="x" type="i" direction="in"/><arg name="y" type="i" direction="in"/></method>
  <method name="Scroll"><arg name="delta" type="i" direction="in"/><arg name="orientation" type="s" direction="in"/></method>
  <signal name="NewTitle"/>
  <signal name="NewIcon"/>
  <signal name="NewToolTip"/>
  <signal name="NewStatus"><arg name="status" type="s"/></signal>
 </interface>
 <interface name="com.canonical.dbusmenu">
  <property name="Version" type="u" access="read"/>
  <property name="TextDirection" type="s" access="read"/>
  <property name="Status" type="s" access="read"/>
  <property name="IconThemePath" type="as" access="read"/>
  <method name="GetLayout">
   <arg type="i" name="parentId" direction="in"/><arg type="i" name="recursionDepth" direction="in"/>
   <arg type="as" name="propertyNames" direction="in"/><arg type="u" name="revision" direction="out"/>
   <arg type="(ia{sv}av)" name="layout" direction="out"/>
  </method>
  <method name="GetGroupProperties">
   <arg type="ai" name="ids" direction="in"/><arg type="as" name="propertyNames" direction="in"/>
   <arg type="a(ia{sv})" name="properties" direction="out"/>
  </method>
  <method name="GetProperty">
   <arg type="i" name="id" direction="in"/><arg type="s" name="name" direction="in"/><arg type="v" name="value" direction="out"/>
  </method>
  <method name="Event">
   <arg type="i" name="id" direction="in"/><arg type="s" name="eventId" direction="in"/>
   <arg type="v" name="data" direction="in"/><arg type="u" name="timestamp" direction="in"/>
  </method>
  <method name="EventGroup"><arg type="a(isvu)" name="events" direction="in"/><arg type="ai" name="idErrors" direction="out"/></method>
  <method name="AboutToShow"><arg type="i" name="id" direction="in"/><arg type="b" name="needUpdate" direction="out"/></method>
  <method name="AboutToShowGroup">
   <arg type="ai" name="ids" direction="in"/><arg type="ai" name="updatesNeeded" direction="out"/>
   <arg type="ai" name="idErrors" direction="out"/>
  </method>
  <signal name="ItemsPropertiesUpdated"><arg type="a(ia{sv})" name="updatedProps"/><arg type="a(ias)" name="removedProps"/></signal>
  <signal name="LayoutUpdated"><arg type="u" name="revision"/><arg type="i" name="parent"/></signal>
  <signal name="ItemActivationRequested"><arg type="i" name="id"/><arg type="u" name="timestamp"/></signal>
 </interface>
</node>)XML";

        template<typename T>
        bool load(void *library, T &target, const char *name) {
            target = reinterpret_cast<T>(dlsym(library, name));
            return target != nullptr;
        }

        bool load_glib() {
            void *library = dlopen("libgio-2.0.so.0", RTLD_NOW | RTLD_LOCAL);
            if (!library)
                return false;
            return load(library, g.main_context_new, "g_main_context_new") &&
                   load(library, g.main_context_push_thread_default, "g_main_context_push_thread_default") &&
                   load(library, g.main_loop_new, "g_main_loop_new") && load(library, g.main_loop_run, "g_main_loop_run") &&
                   load(library, g.main_loop_quit, "g_main_loop_quit") &&
                   load(library, g.main_context_invoke, "g_main_context_invoke") &&
                   load(library, g.bus_get_sync, "g_bus_get_sync") &&
                   load(library, g.node_info_new_for_xml, "g_dbus_node_info_new_for_xml") &&
                   load(library, g.node_info_lookup_interface, "g_dbus_node_info_lookup_interface") &&
                   load(library, g.register_object, "g_dbus_connection_register_object") &&
                   load(library, g.watch_name, "g_bus_watch_name_on_connection") &&
                   load(library, g.call_sync, "g_dbus_connection_call_sync") &&
                   load(library, g.emit_signal, "g_dbus_connection_emit_signal") &&
                   load(library, g.return_value, "g_dbus_method_invocation_return_value") &&
                   load(library, g.variant_parse, "g_variant_parse") && load(library, g.variant_unref, "g_variant_unref") &&
                   load(library, g.variant_get_child_value, "g_variant_get_child_value") &&
                   load(library, g.variant_get_int32, "g_variant_get_int32") &&
                   load(library, g.variant_get_string, "g_variant_get_string") &&
                   load(library, g.variant_n_children, "g_variant_n_children") && load(library, g.error_free, "g_error_free");
        }

        void drop(GError *error) {
            if (error) {
                std::fprintf(stderr, "FetchJet tray: %s\n", error->message ? error->message : "error");
                g.error_free(error);
            }
        }

        void *parse(const std::string &text) {
            GError *error = nullptr;
            void *value = g.variant_parse(nullptr, text.c_str(), nullptr, nullptr, &error);
            if (!value)
                std::fprintf(stderr, "FetchJet tray: bad value %s\n", text.c_str());
            drop(error);
            return value;
        }

        std::string gtext(const std::string &text) {
            std::string out = "'";
            for (char c: text) {
                if (c == '\\' || c == '\'')
                    out += '\\';
                out += c;
            }
            return out + "'";
        }

        std::string label(const std::string &text) {
            std::string out;
            for (char c: text) {
                out += c;
                if (c == '_')
                    out += '_';
            }
            return gtext(out);
        }

        std::string item(int id, const std::string &props, const std::string &children) {
            return "(" + std::to_string(id) + ", " + (props.empty() ? "@a{sv} {}" : "{" + props + "}") + ", " +
                   (children.empty() ? "@av []" : "[" + children + "]") + ")";
        }

        struct Entry {
            int id;
            std::string props;
        };

        std::vector<Entry> entries(const Menu &menu) {
            std::vector<Entry> list;
            list.push_back({1, "'label': <" + label(menu.show) + ">"});
            if (menu.when_done_on) {
                list.push_back({2, "'label': <" + label(menu.when_done) + ">, 'children-display': <'submenu'>"});
                for (size_t i = 0; i < menu.options.size(); i++)
                    list.push_back({WHEN_DONE_BASE + int(i), "'label': <" + label(menu.options[i]) +
                                                             ">, 'toggle-type': <'radio'>, 'toggle-state': <" +
                                                             (int(i) == menu.index ? "1" : "0") + ">"});
            }
            list.push_back({3, "'type': <'separator'>"});
            list.push_back({4, "'label': <" + label(menu.close) + ">"});
            return list;
        }

        std::string layout_text(const Menu &menu, uint32_t revision) {
            std::string choices;
            std::string children;
            for (const auto &entry: entries(menu)) {
                std::string one = "<" + item(entry.id, entry.props, "") + ">";
                if (entry.id >= WHEN_DONE_BASE)
                    choices += std::string(choices.empty() ? "" : ", ") + one;
            }
            for (const auto &entry: entries(menu)) {
                if (entry.id >= WHEN_DONE_BASE)
                    continue;
                std::string one = "<" + item(entry.id, entry.props, entry.id == 2 ? choices : "") + ">";
                children += std::string(children.empty() ? "" : ", ") + one;
            }
            return "(uint32 " + std::to_string(revision) + ", " + item(0, "'children-display': <'submenu'>", children) + ")";
        }

        Menu current_menu() {
            std::lock_guard<std::mutex> guard(g_lock);
            return g_menu;
        }

        void clicked(int id) {
            if (id == 1) {
                slint::invoke_from_event_loop([] { show_window(true); });
            } else if (id == 4) {
                slint::invoke_from_event_loop([] { quit_app(); });
            } else if (id >= WHEN_DONE_BASE) {
                int index = id - WHEN_DONE_BASE;
                slint::invoke_from_event_loop([index] {
                    send_action("{\"type\":\"SetWhenDone\",\"index\":" + std::to_string(index) + "}");
                });
            }
        }

        void handle_event(void *event) {
            void *id_value = g.variant_get_child_value(event, 0);
            void *name_value = g.variant_get_child_value(event, 1);
            int id = g.variant_get_int32(id_value);
            std::string name = g.variant_get_string(name_value, nullptr);
            g.variant_unref(id_value);
            g.variant_unref(name_value);
            if (name == "clicked")
                clicked(id);
        }

        void reply(void *invocation, const std::string &text) {
            void *value = parse(text);
            if (value) {
                g.return_value(invocation, value);
                g.variant_unref(value);
            }
        }

        void item_call(void *, const char *, const char *, const char *, const char *method, void *, void *invocation, void *) {
            std::string name = method;
            if (name == "Activate")
                slint::invoke_from_event_loop([] { toggle_window(); });
            else if (name == "SecondaryActivate")
                slint::invoke_from_event_loop([] { show_window(true); });
            reply(invocation, "()");
        }

        void *item_property(void *, const char *, const char *, const char *, const char *property, GError **, void *) {
            std::string name = property;
            Menu menu = current_menu();
            if (name == "Category")
                return parse("'ApplicationStatus'");
            if (name == "Id")
                return parse("'fetchjet'");
            if (name == "Title")
                return parse("'FetchJet'");
            if (name == "Status")
                return parse("'Active'");
            if (name == "WindowId")
                return parse("0");
            if (name == "IconName")
                return parse("'fetchjet'");
            if (name == "IconThemePath")
                return parse(gtext(g_icon_folder));
            if (name == "ToolTip")
                return parse("('fetchjet', @a(iiay) [], 'FetchJet', " + gtext(menu.tooltip) + ")");
            if (name == "ItemIsMenu")
                return parse("false");
            if (name == "Menu")
                return parse(std::string("objectpath '") + MENU_PATH + "'");
            return nullptr;
        }

        void menu_call(void *, const char *, const char *, const char *, const char *method, void *parameters, void *invocation,
                       void *) {
            std::string name = method;
            Menu menu = current_menu();
            uint32_t revision;
            {
                std::lock_guard<std::mutex> guard(g_lock);
                revision = g_revision;
            }
            if (name == "GetLayout") {
                reply(invocation, layout_text(menu, revision));
                return;
            }
            if (name == "GetGroupProperties") {
                std::vector<int> wanted;
                void *ids = g.variant_get_child_value(parameters, 0);
                for (size_t i = 0; i < g.variant_n_children(ids); i++) {
                    void *one = g.variant_get_child_value(ids, i);
                    wanted.push_back(g.variant_get_int32(one));
                    g.variant_unref(one);
                }
                g.variant_unref(ids);
                std::string list;
                for (const auto &entry: entries(menu)) {
                    bool asked = wanted.empty();
                    for (int id: wanted)
                        asked = asked || id == entry.id;
                    if (asked)
                        list += std::string(list.empty() ? "" : ", ") + "(" + std::to_string(entry.id) + ", {" + entry.props + "})";
                }
                reply(invocation, list.empty() ? "(@a(ia{sv}) [],)" : "([" + list + "],)");
                return;
            }
            if (name == "GetProperty") {
                reply(invocation, "(<''>,)");
                return;
            }
            if (name == "Event") {
                handle_event(parameters);
                reply(invocation, "()");
                return;
            }
            if (name == "EventGroup") {
                void *events = g.variant_get_child_value(parameters, 0);
                for (size_t i = 0; i < g.variant_n_children(events); i++) {
                    void *one = g.variant_get_child_value(events, i);
                    handle_event(one);
                    g.variant_unref(one);
                }
                g.variant_unref(events);
                reply(invocation, "(@ai [],)");
                return;
            }
            if (name == "AboutToShow") {
                reply(invocation, "(false,)");
                return;
            }
            if (name == "AboutToShowGroup") {
                reply(invocation, "(@ai [], @ai [])");
                return;
            }
            reply(invocation, "()");
        }

        void *menu_property(void *, const char *, const char *, const char *, const char *property, GError **, void *) {
            std::string name = property;
            if (name == "Version")
                return parse("uint32 3");
            if (name == "TextDirection")
                return parse(current_menu().rtl ? "'rtl'" : "'ltr'");
            if (name == "Status")
                return parse("'normal'");
            if (name == "IconThemePath")
                return parse("@as []");
            return nullptr;
        }

        const VTable ITEM_TABLE{item_call, item_property, nullptr, {}};
        const VTable MENU_TABLE{menu_call, menu_property, nullptr, {}};

        void decide(bool on) {
            {
                std::lock_guard<std::mutex> guard(g_lock);
                g_decided = true;
            }
            g_on = on;
            g_decided_signal.notify_all();
        }

        void watcher_appeared(void *, const char *, const char *, void *) {
            GError *error = nullptr;
            void *params = parse("(" + gtext(g_name) + ",)");
            void *answer = g.call_sync(g_bus, "org.kde.StatusNotifierWatcher", "/StatusNotifierWatcher",
                                       "org.kde.StatusNotifierWatcher", "RegisterStatusNotifierItem", params, nullptr, 0, 3000,
                                       nullptr, &error);
            if (params)
                g.variant_unref(params);
            bool ok = answer != nullptr;
            if (answer)
                g.variant_unref(answer);
            drop(error);
            decide(ok);
        }

        void watcher_vanished(void *, const char *, void *) {
            bool was_on = g_on.exchange(false);
            decide(false);
            if (was_on)
                slint::invoke_from_event_loop([] { show_window(false); });
        }

        bool own_name() {
            GError *error = nullptr;
            g_name = "org.kde.StatusNotifierItem-" + std::to_string(getpid()) + "-1";
            void *params = parse("(" + gtext(g_name) + ", uint32 4)");
            void *answer = g.call_sync(g_bus, "org.freedesktop.DBus", "/org/freedesktop/DBus", "org.freedesktop.DBus",
                                       "RequestName", params, nullptr, 0, 3000, nullptr, &error);
            if (params)
                g.variant_unref(params);
            bool ok = answer != nullptr;
            if (answer)
                g.variant_unref(answer);
            drop(error);
            return ok;
        }

        void run_tray() {
            void *context = g.main_context_new();
            g.main_context_push_thread_default(context);
            GError *error = nullptr;
            g_bus = g.bus_get_sync(2, nullptr, &error);
            drop(error);
            error = nullptr;
            void *node = g_bus ? g.node_info_new_for_xml(INTROSPECTION, &error) : nullptr;
            drop(error);
            if (!node || !own_name()) {
                decide(false);
                return;
            }
            error = nullptr;
            unsigned item = g.register_object(g_bus, ITEM_PATH, g.node_info_lookup_interface(node, ITEM_IFACE), &ITEM_TABLE,
                                              nullptr, nullptr, &error);
            drop(error);
            error = nullptr;
            unsigned menu = g.register_object(g_bus, MENU_PATH, g.node_info_lookup_interface(node, MENU_IFACE), &MENU_TABLE,
                                              nullptr, nullptr, &error);
            drop(error);
            if (item == 0 || menu == 0) {
                decide(false);
                return;
            }
            g.watch_name(g_bus, "org.kde.StatusNotifierWatcher", 0, watcher_appeared, watcher_vanished, nullptr, nullptr);
            g_loop = g.main_loop_new(context, 0);
            g_context = context;
            g.main_loop_run(g_loop);
        }

        void emit(const char *path, const char *iface, const char *signal, const std::string &args) {
            if (!g_bus)
                return;
            void *params = parse(args);
            if (!params)
                return;
            GError *error = nullptr;
            g.emit_signal(g_bus, nullptr, path, iface, signal, params, &error);
            g.variant_unref(params);
            drop(error);
        }

        int emit_changes(void *) {
            uint32_t revision;
            {
                std::lock_guard<std::mutex> guard(g_lock);
                revision = g_revision;
            }
            emit(MENU_PATH, MENU_IFACE, "LayoutUpdated", "(uint32 " + std::to_string(revision) + ", 0)");
            emit(ITEM_PATH, ITEM_IFACE, "NewToolTip", "()");
            return 0;
        }

    }

    bool start(const std::string &icon_folder) {
        if (std::getenv("FETCHJET_NO_TRAY") != nullptr || g_started.exchange(true))
            return g_on;
        g_icon_folder = icon_folder;
        if (!load_glib())
            return false;
        std::thread(run_tray).detach();
        std::unique_lock<std::mutex> guard(g_lock);
        g_decided_signal.wait_for(guard, std::chrono::milliseconds(1500), [] { return g_decided; });
        return g_on;
    }

    bool on() {
        return g_on;
    }

    void update(const Menu &menu) {
        {
            std::lock_guard<std::mutex> guard(g_lock);
            if (g_menu == menu)
                return;
            g_menu = menu;
            g_revision++;
        }
        void *context = g_context;
        if (context)
            g.main_context_invoke(context, emit_changes, nullptr);
    }

    void stop() {
        g_on = false;
        if (g_loop)
            g.main_loop_quit(g_loop);
    }

}
