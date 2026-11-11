#include "../app_host.h"

#import <AppKit/AppKit.h>
#import <Foundation/Foundation.h>
#import <UniformTypeIdentifiers/UniformTypeIdentifiers.h>

#include <dlfcn.h>
#include <fcntl.h>
#include <mach-o/dyld.h>
#include <signal.h>
#include <spawn.h>
#include <sys/file.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <unistd.h>

#include <climits>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <fstream>
#include <sstream>
#include <string>
#include <thread>
#include <vector>

extern char **environ;

namespace fjhost::os {
    void tray_clicked(int id);
}

@interface FJTrayTarget : NSObject
- (void)itemClicked:(id)sender;
- (void)reopen:(NSAppleEventDescriptor *)event withReply:(NSAppleEventDescriptor *)reply;
- (void)openURL:(NSAppleEventDescriptor *)event withReply:(NSAppleEventDescriptor *)reply;
- (void)openDocuments:(NSAppleEventDescriptor *)event withReply:(NSAppleEventDescriptor *)reply;
- (void)downloadWithFetchJet:(NSPasteboard *)pboard userData:(NSString *)data error:(NSString **)error;
@end

namespace fjhost::os {

    namespace {

        constexpr int MENU_SHOW = 1;
        constexpr int MENU_CLOSE = 4;
        constexpr int WHEN_DONE_BASE = 100;

        struct TrayMenu {
            std::string tooltip;
            std::string show;
            std::string close;
            std::string when_done;
            std::vector<std::string> options;
            int index = 0;
            bool when_done_on = false;

            bool operator==(const TrayMenu &other) const = default;
        };

        NSStatusItem *g_status = nil;
        FJTrayTarget *g_target = nil;
        FJTrayTarget *g_events = nil;

        void install_apple_events() {
            if (!g_events)
                g_events = [[FJTrayTarget alloc] init];
            NSAppleEventManager *events = [NSAppleEventManager sharedAppleEventManager];
            [events setEventHandler:g_events andSelector:@selector(reopen:withReply:)
                      forEventClass:kCoreEventClass andEventID:kAEReopenApplication];
            [events setEventHandler:g_events andSelector:@selector(openURL:withReply:)
                      forEventClass:kInternetEventClass andEventID:kAEGetURL];
            [events setEventHandler:g_events andSelector:@selector(openDocuments:withReply:)
                      forEventClass:kCoreEventClass andEventID:kAEOpenDocuments];
        }
        TrayMenu g_menu;
        bool g_menu_built = false;

        NSString *ns(const std::string &text) {
            NSString *value = [[NSString alloc] initWithBytes:text.data() length:text.size() encoding:NSUTF8StringEncoding];
            return value ? value : @"";
        }

        std::string utf8(NSString *text) {
            const char *bytes = text ? [text UTF8String] : nullptr;
            return bytes ? std::string(bytes) : std::string();
        }

        std::string executable_folder() {
            uint32_t size = 0;
            _NSGetExecutablePath(nullptr, &size);
            std::vector<char> raw(size + 1, 0);
            if (_NSGetExecutablePath(raw.data(), &size) != 0)
                return "./";
            char resolved[PATH_MAX + 1];
            std::string full = realpath(raw.data(), resolved) ? std::string(resolved) : std::string(raw.data());
            return full.substr(0, full.find_last_of('/') + 1);
        }

        std::string home_folder() {
            const char *home = std::getenv("HOME");
            if (home && *home)
                return home;
            return utf8(NSHomeDirectory());
        }

        template<typename T>
        T symbol(void *library, const char *name) {
            return reinterpret_cast<T>(dlsym(library, name));
        }

        void run_detached(std::vector<std::string> command) {
            std::vector<char *> argv;
            for (auto &part: command)
                argv.push_back(part.data());
            argv.push_back(nullptr);
            posix_spawn_file_actions_t actions;
            posix_spawn_file_actions_init(&actions);
            posix_spawn_file_actions_addopen(&actions, 0, "/dev/null", O_RDONLY, 0);
            posix_spawn_file_actions_addopen(&actions, 1, "/dev/null", O_WRONLY, 0);
            posix_spawn_file_actions_addopen(&actions, 2, "/dev/null", O_WRONLY, 0);
            pid_t pid = 0;
            if (posix_spawnp(&pid, argv[0], &actions, nullptr, argv.data(), environ) == 0) {
                std::thread([pid] {
                    int status = 0;
                    waitpid(pid, &status, 0);
                }).detach();
            }
            posix_spawn_file_actions_destroy(&actions);
        }

        std::string apple_script_text(const std::string &text) {
            std::string out = "\"";
            for (char c: text) {
                if (c == '\\' || c == '"')
                    out.push_back('\\');
                out.push_back(c == '\n' || c == '\r' ? ' ' : c);
            }
            return out + "\"";
        }

        void on_main(void (^block)(void)) {
            if ([NSThread isMainThread])
                block();
            else
                dispatch_sync(dispatch_get_main_queue(), block);
        }

        NSImage *tray_image() {
            std::string folder = executable_folder();
            for (const char *name: {"fetchjet-tray.png", "fetchjet.png", "../Resources/fetchjet-tray.png"}) {
                NSImage *image = [[NSImage alloc] initWithContentsOfFile:ns(folder + name)];
                if (image) {
                    [image setSize:NSMakeSize(18, 18)];
                    return image;
                }
            }
            NSImage *symbol = [NSImage imageWithSystemSymbolName:@"arrow.down.circle" accessibilityDescription:@"FetchJet"];
            [symbol setTemplate:YES];
            return symbol;
        }

        NSMenuItem *menu_item(const std::string &title, int tag) {
            NSMenuItem *item = [[NSMenuItem alloc] initWithTitle:ns(title) action:@selector(itemClicked:) keyEquivalent:@""];
            [item setTarget:g_target];
            [item setTag:tag];
            return item;
        }

        void build_menu() {
            if (!g_status)
                return;
            NSMenu *menu = [[NSMenu alloc] init];
            [menu setAutoenablesItems:NO];
            [menu addItem:menu_item(g_menu.show.empty() ? "Show" : g_menu.show, MENU_SHOW)];
            if (g_menu.when_done_on) {
                NSMenuItem *parent = [[NSMenuItem alloc] initWithTitle:ns(g_menu.when_done) action:nil keyEquivalent:@""];
                NSMenu *choices = [[NSMenu alloc] init];
                [choices setAutoenablesItems:NO];
                for (size_t i = 0; i < g_menu.options.size(); i++) {
                    NSMenuItem *choice = menu_item(g_menu.options[i], WHEN_DONE_BASE + int(i));
                    [choice setState:int(i) == g_menu.index ? NSControlStateValueOn : NSControlStateValueOff];
                    [choices addItem:choice];
                }
                [parent setSubmenu:choices];
                [menu addItem:parent];
            }
            [menu addItem:[NSMenuItem separatorItem]];
            [menu addItem:menu_item(g_menu.close.empty() ? "Close" : g_menu.close, MENU_CLOSE)];
            [g_status setMenu:menu];
            [[g_status button] setToolTip:ns(g_menu.tooltip)];
            g_menu_built = true;
        }

        std::vector<std::string> split_patterns(const std::string &pattern) {
            std::vector<std::string> out;
            std::string current;
            for (char c: pattern + ";") {
                if (c == ';' || c == ' ') {
                    if (!current.empty())
                        out.push_back(current);
                    current.clear();
                } else {
                    current.push_back(c);
                }
            }
            return out;
        }

        NSArray<UTType *> *content_types(const std::string &pattern) {
            NSMutableArray<UTType *> *types = [NSMutableArray array];
            for (const auto &one: split_patterns(pattern)) {
                size_t dot = one.find_last_of('.');
                if (dot == std::string::npos || dot + 1 >= one.size())
                    return nil;
                std::string extension = one.substr(dot + 1);
                if (extension == "*")
                    return nil;
                UTType *type = [UTType typeWithFilenameExtension:ns(extension)];
                if (type)
                    [types addObject:type];
            }
            return types.count ? types : nil;
        }

    }

    void tray_clicked(int id) {
        if (id == MENU_SHOW) {
            slint::invoke_from_event_loop([] { show_window(true); });
        } else if (id == MENU_CLOSE) {
            slint::invoke_from_event_loop([] { quit_app(); });
        } else if (id >= WHEN_DONE_BASE) {
            int index = id - WHEN_DONE_BASE;
            slint::invoke_from_event_loop([index] {
                send_action("{\"type\":\"SetWhenDone\",\"index\":" + std::to_string(index) + "}");
            });
        }
    }

    int child_main(int, char **, bool &handled) {
        handled = false;
        return 0;
    }

    void prepare_process() {
        signal(SIGPIPE, SIG_IGN);
        install_apple_events();
        for (NSNotificationName name in @[NSApplicationWillFinishLaunchingNotification, NSApplicationDidFinishLaunchingNotification]) {
            [[NSNotificationCenter defaultCenter] addObserverForName:name
                                                              object:nil
                                                               queue:nil
                                                          usingBlock:^(NSNotification *) {
                                                              install_apple_events();
                                                              if (NSApp && g_events)
                                                                  [NSApp setServicesProvider:g_events];
                                                          }];
        }
        if (!std::getenv("SLINT_BACKEND"))
            setenv("SLINT_BACKEND", "winit-software", 0);
    }

    bool load_core(CoreApi &core) {
        void *library = dlopen((executable_folder() + "libfjcore.dylib").c_str(), RTLD_NOW | RTLD_LOCAL);
        if (!library)
            library = dlopen("@executable_path/libfjcore.dylib", RTLD_NOW | RTLD_LOCAL);
        if (!library) {
            std::fprintf(stderr, "FetchJet: %s\n", dlerror());
            return false;
        }
        core.prepare = symbol<fj_prepare_fn>(library, "fj_prepare");
        core.start = symbol<fj_start_fn>(library, "fj_start");
        core.snapshot = symbol<fj_snapshot_fn>(library, "fj_snapshot");
        core.action = symbol<fj_action_fn>(library, "fj_action");
        core.call = symbol<fj_call_fn>(library, "fj_call");
        core.release = symbol<fj_free_fn>(library, "fj_free");
        core.shutdown = symbol<fj_shutdown_fn>(library, "fj_shutdown");
        return core.prepare && core.start && core.snapshot && core.action && core.call && core.release && core.shutdown;
    }

    void fatal(const std::string &text) {
        std::fprintf(stderr, "FetchJet: %s\n", text.c_str());
    }

    bool system_uses_dark() {
        const char *forced = std::getenv("FETCHJET_THEME");
        if (forced && *forced)
            return std::strcmp(forced, "dark") == 0;
        @autoreleasepool {
            NSString *style = [[NSUserDefaults standardUserDefaults] stringForKey:@"AppleInterfaceStyle"];
            return style && [style caseInsensitiveCompare:@"Dark"] == NSOrderedSame;
        }
    }

    std::string read_text_file(const std::string &utf8_path) {
        std::ifstream file(utf8_path, std::ios::binary);
        std::ostringstream text;
        text << file.rdbuf();
        return text.str();
    }

    void *claim_instance(const std::string &bridge_port, bool &second) {
        std::string folder = home_folder() + "/.fetch_jet";
        mkdir(folder.c_str(), 0700);
        std::string path = folder + "/fetchjet-native" + bridge_port + ".lock";
        int fd = open(path.c_str(), O_RDWR | O_CREAT | O_CLOEXEC, 0600);
        if (fd < 0)
            return nullptr;
        second = flock(fd, LOCK_EX | LOCK_NB) != 0;
        return reinterpret_cast<void *>(intptr_t(fd) + 1);
    }

    void release_instance(void *instance) {
        if (instance)
            close(int(reinterpret_cast<intptr_t>(instance) - 1));
    }

    void apply_title_bar(bool) {}

    void bring_to_front(bool focus) {
        if (!focus)
            return;
        dispatch_async(dispatch_get_main_queue(), ^{
            if (@available(macOS 14.0, *))
                [NSApp activate];
            else
                [NSApp activateIgnoringOtherApps:YES];
            for (NSWindow *window in [NSApp windows]) {
                if ([window isVisible] || [window isMiniaturized]) {
                    if ([window isMiniaturized])
                        [window deminiaturize:nil];
                    [window makeKeyAndOrderFront:nil];
                }
            }
        });
    }

    void window_hidden() {}

    bool window_minimized() {
        __block bool minimized = false;
        on_main(^{
            for (NSWindow *window in [NSApp windows])
                if ([window isMiniaturized])
                    minimized = true;
        });
        return minimized;
    }

    bool toggle_too_soon() {
        return false;
    }

    void start_shell() {
        if (std::getenv("FETCHJET_NO_TRAY"))
            return;
        on_main(^{
            if (g_status)
                return;
            g_target = [[FJTrayTarget alloc] init];
            g_status = [[NSStatusBar systemStatusBar] statusItemWithLength:NSSquareStatusItemLength];
            NSStatusBarButton *button = [g_status button];
            [button setImage:tray_image()];
            [button setToolTip:ns(g_menu.tooltip)];
            build_menu();
        });
    }

    bool tray_on() {
        return g_status != nil;
    }

    void update_tray_tip(const std::string &tip) {
        auto &window = ui();
        ShellDto shell = window.global<Core>().get_shell();
        TrayMenu menu;
        menu.tooltip = tip;
        menu.show = plain(shell.trayShowLabel);
        menu.close = plain(shell.trayCloseLabel);
        menu.when_done = plain(shell.whenDoneLabel);
        menu.when_done_on = shell.whenDoneSupported && shell.whenDoneOptions;
        if (shell.whenDoneOptions) {
            for (size_t i = 0; i < shell.whenDoneOptions->row_count(); i++) {
                auto option = shell.whenDoneOptions->row_data(i);
                menu.options.push_back(option ? plain(*option) : std::string());
            }
        }
        menu.index = shell.whenDoneIndex;
        if (g_menu_built && menu == g_menu)
            return;
        g_menu = menu;
        on_main(^{
            build_menu();
        });
    }

    void remove_tray() {
        on_main(^{
            if (g_status)
                [[NSStatusBar systemStatusBar] removeStatusItem:g_status];
            g_status = nil;
            g_target = nil;
            g_menu_built = false;
        });
    }

    void show_notice(const std::string &title, const std::string &text) {
        run_detached({"/usr/bin/osascript", "-e",
                      "display notification " + apple_script_text(text) + " with title " + apple_script_text(title)});
    }

    void start_emoji(const std::string &) {}

    slint::Image emoji_fallback(const slint::SharedString &, int) {
        return slint::Image();
    }

    std::string exe_folder() {
        return executable_folder();
    }

    std::string pick_path(bool folder, const std::string &title, const std::string &initial,
                          const std::string &filter_name, const std::string &pattern) {
        __block std::string chosen;
        on_main(^{
            @autoreleasepool {
                NSOpenPanel *panel = [NSOpenPanel openPanel];
                [panel setCanChooseFiles:!folder];
                [panel setCanChooseDirectories:folder];
                [panel setCanCreateDirectories:folder];
                [panel setAllowsMultipleSelection:NO];
                [panel setResolvesAliases:YES];
                if (!title.empty()) {
                    [panel setMessage:ns(title)];
                    [panel setTitle:ns(title)];
                }
                if (!initial.empty())
                    [panel setDirectoryURL:[NSURL fileURLWithPath:ns(initial) isDirectory:YES]];
                if (!folder) {
                    NSArray<UTType *> *types = content_types(pattern);
                    if (types)
                        [panel setAllowedContentTypes:types];
                    if (!filter_name.empty() && title.empty())
                        [panel setMessage:ns(filter_name)];
                }
                if (@available(macOS 14.0, *))
                    [NSApp activate];
                else
                    [NSApp activateIgnoringOtherApps:YES];
                if ([panel runModal] == NSModalResponseOK) {
                    NSURL *url = [[panel URLs] firstObject];
                    if (url)
                        chosen = utf8([url path]);
                }
            }
        });
        return chosen;
    }

    char *copy_for_core(const std::string &text) {
        return strdup(text.c_str());
    }

}

@implementation FJTrayTarget
- (void)itemClicked:(id)sender {
    fjhost::os::tray_clicked(int([(NSMenuItem *) sender tag]));
}
- (void)reopen:(NSAppleEventDescriptor *)event withReply:(NSAppleEventDescriptor *)reply {
    slint::invoke_from_event_loop([] { fjhost::show_window(true); });
}
- (void)openURL:(NSAppleEventDescriptor *)event withReply:(NSAppleEventDescriptor *)reply {
    NSString *text = [[event paramDescriptorForKeyword:keyDirectObject] stringValue];
    if (!text)
        return;
    std::string url = [text UTF8String] ?: "";
    slint::invoke_from_event_loop([url] { fjhost::open_external(url); });
}
- (void)openDocuments:(NSAppleEventDescriptor *)event withReply:(NSAppleEventDescriptor *)reply {
    NSAppleEventDescriptor *list = [event paramDescriptorForKeyword:keyDirectObject];
    NSInteger count = list ? [list numberOfItems] : 0;
    for (NSInteger i = 1; i <= count; i++) {
        NSAppleEventDescriptor *item = [[list descriptorAtIndex:i] coerceToDescriptorType:typeFileURL];
        NSString *raw = item ? [[NSString alloc] initWithData:[item data] encoding:NSUTF8StringEncoding] : nil;
        NSURL *fileUrl = raw ? [NSURL URLWithString:raw] : nil;
        if (!fileUrl.path)
            continue;
        std::string path = [fileUrl.path UTF8String] ?: "";
        slint::invoke_from_event_loop([path] { fjhost::open_external(path); });
    }
}
- (void)downloadWithFetchJet:(NSPasteboard *)pboard userData:(NSString *)data error:(NSString **)error {
    NSMutableArray<NSString *> *links = [NSMutableArray array];
    NSArray<NSURL *> *urls = [pboard readObjectsForClasses:@[[NSURL class]] options:nil];
    for (NSURL *url in urls) {
        NSString *scheme = url.scheme.lowercaseString ?: @"";
        if (url.isFileURL && [url.path.lowercaseString hasSuffix:@".torrent"])
            [links addObject:url.path];
        else if ([scheme isEqualToString:@"http"] || [scheme isEqualToString:@"https"] || [scheme isEqualToString:@"magnet"])
            [links addObject:url.absoluteString];
    }
    if (links.count == 0) {
        NSString *text = [pboard stringForType:NSPasteboardTypeString];
        if (text) {
            NSRegularExpression *pattern = [NSRegularExpression
                regularExpressionWithPattern:@"(?:https?://|magnet:\\?)[^\\s<>\"']+"
                                     options:NSRegularExpressionCaseInsensitive
                                       error:nil];
            for (NSTextCheckingResult *match in [pattern matchesInString:text options:0 range:NSMakeRange(0, text.length)]) {
                NSString *link = [[text substringWithRange:match.range]
                    stringByTrimmingCharactersInSet:[NSCharacterSet characterSetWithCharactersInString:@".,;:)]}"]];
                if (link.length > 0 && ![links containsObject:link])
                    [links addObject:link];
            }
        }
    }
    if (links.count == 0) {
        if (error)
            *error = @"No link found";
        return;
    }
    for (NSString *link in links) {
        std::string url = [link UTF8String] ?: "";
        slint::invoke_from_event_loop([url] { fjhost::open_external(url); });
    }
}
@end
