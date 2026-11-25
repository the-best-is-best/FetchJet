#ifdef _WIN32
#define NOMINMAX
#define WIN32_LEAN_AND_MEAN
#endif
#include "pdf_reader.h"

#include "app_host.h"
#include "json.h"

#include <algorithm>
#include <chrono>
#include <cmath>
#include <condition_variable>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <deque>
#include <map>
#include <memory>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

#ifdef _WIN32
#include <windows.h>
#else
#include <dlfcn.h>
#endif

namespace fjhost::pdf {

    namespace {

        struct FileAccess {
            unsigned long length;
            int (*get_block)(void *param, unsigned long position, unsigned char *buffer, unsigned long size);
            void *param;
        };

        struct SizeF {
            float width;
            float height;
        };

        struct Api {
            void (*init)() = nullptr;
            void (*destroy)() = nullptr;
            void *(*load_custom)(FileAccess *, const char *) = nullptr;
            void (*close_document)(void *) = nullptr;
            int (*page_count)(void *) = nullptr;
            int (*page_size)(void *, int, SizeF *) = nullptr;
            unsigned long (*last_error)() = nullptr;
            void *(*load_page)(void *, int) = nullptr;
            void (*close_page)(void *) = nullptr;
            void *(*bitmap_create)(int, int, int, void *, int) = nullptr;
            int (*bitmap_fill)(void *, int, int, int, int, unsigned long) = nullptr;
            void (*bitmap_destroy)(void *) = nullptr;
            void (*render)(void *, void *, int, int, int, int, int, int) = nullptr;
        };

        const unsigned long kErrorPassword = 4;
        const int kBitmapBgra = 4;
        const int kRenderAnnot = 0x01;
        const int kRenderReverseBytes = 0x10;
        const double kPoint = 96.0 / 72.0;
        const double kGap = 16.0;
        const double kSide = 24.0;
        const size_t kCacheLimit = 14;
        const double kMaxPixels = 40.0 * 1000 * 1000;

        enum JobKind { JobOpen, JobRender, JobClose };

        struct Job {
            JobKind kind = JobOpen;
            unsigned tag = 0;
            int page = 0;
            int width = 0;
            int height = 0;
            std::string path;
            std::vector<std::string> libraries;
        };

        struct OpenResult {
            bool ok = false;
            bool library = true;
            unsigned long error = 0;
            std::vector<SizeF> sizes;
        };

        std::mutex g_mutex;
        std::condition_variable g_wake;
        std::deque<Job> g_jobs;
        std::deque<Job> g_renders;
        bool g_quit = false;
        std::thread g_thread;

        Api w_api;
        void *w_lib = nullptr;
        bool w_initialized = false;
        void *w_doc = nullptr;
        FILE *w_file = nullptr;
        FileAccess w_access{};
        unsigned w_tag = 0;

        struct Box {
            double top = 0;
            double left = 0;
            double width = 0;
            double height = 0;
        };

        struct Cached {
            int width = 0;
            int height = 0;
            slint::Image image;
            uint64_t used = 0;
        };

        bool g_wired = false;
        bool g_open = false;
        unsigned g_tag = 0;
        std::string g_id;
        std::string g_title;
        std::string g_path;
        bool g_hidden = false;
        int g_start_page = 0;
        bool g_loaded = false;
        bool g_restored = false;
        std::string g_error;
        std::vector<SizeF> g_sizes;
        std::vector<Box> g_boxes;
        double g_content_w = 0;
        double g_content_h = 0;
        double g_zoom = 1.0;
        bool g_fit = true;
        bool g_have_view = false;
        double g_vx = 0;
        double g_vy = 0;
        double g_vw = 0;
        double g_vh = 0;
        int g_page = 0;
        int g_first = 0;
        int g_last = -1;
        int g_remembered = -1;
        std::shared_ptr<slint::VectorModel<PdfPageRow>> g_model;
        std::map<int, Cached> g_cache;
        uint64_t g_clock = 0;
        int g_goto_tick = 0;
        int g_jump_tick = 0;
        slint::Timer g_remember_timer;
        int g_stat_asked = 0;
        int g_stat_done = 0;
        int g_stat_empty = 0;
        std::string g_stat_last;

        std::string quote(const std::string &text) {
            std::string out = "\"";
            for (unsigned char c: text) {
                if (c == '"' || c == '\\') {
                    out += '\\';
                    out += char(c);
                } else if (c < 0x20) {
                    char buffer[8];
                    std::snprintf(buffer, sizeof buffer, "\\u%04x", c);
                    out += buffer;
                } else {
                    out += char(c);
                }
            }
            return out + "\"";
        }

#ifdef _WIN32
        std::wstring wide(const std::string &text) {
            int length = MultiByteToWideChar(CP_UTF8, 0, text.c_str(), -1, nullptr, 0);
            std::wstring out(length > 0 ? length - 1 : 0, L'\0');
            if (length > 1)
                MultiByteToWideChar(CP_UTF8, 0, text.c_str(), -1, out.data(), length);
            return out;
        }
#endif

        void *symbol(const char *name) {
#ifdef _WIN32
            return reinterpret_cast<void *>(GetProcAddress(static_cast<HMODULE>(w_lib), name));
#else
            return dlsym(w_lib, name);
#endif
        }

        void unload_library() {
            if (!w_lib)
                return;
            if (w_initialized && w_api.destroy)
                w_api.destroy();
            w_initialized = false;
#ifdef _WIN32
            FreeLibrary(static_cast<HMODULE>(w_lib));
#else
            dlclose(w_lib);
#endif
            w_lib = nullptr;
            w_api = Api();
        }

        bool load_library(const std::vector<std::string> &names) {
            if (w_lib)
                return true;
            for (const auto &name: names) {
#ifdef _WIN32
                w_lib = LoadLibraryW(wide(name).c_str());
#else
                w_lib = dlopen(name.c_str(), RTLD_NOW | RTLD_LOCAL);
#endif
                if (w_lib)
                    break;
            }
            if (!w_lib)
                return false;
#define FJ_SYM(field, name) \
            w_api.field = reinterpret_cast<decltype(w_api.field)>(symbol(name)); \
            if (!w_api.field) { \
                unload_library(); \
                return false; \
            }
            FJ_SYM(init, "FPDF_InitLibrary")
            FJ_SYM(destroy, "FPDF_DestroyLibrary")
            FJ_SYM(load_custom, "FPDF_LoadCustomDocument")
            FJ_SYM(close_document, "FPDF_CloseDocument")
            FJ_SYM(page_count, "FPDF_GetPageCount")
            FJ_SYM(page_size, "FPDF_GetPageSizeByIndexF")
            FJ_SYM(last_error, "FPDF_GetLastError")
            FJ_SYM(load_page, "FPDF_LoadPage")
            FJ_SYM(close_page, "FPDF_ClosePage")
            FJ_SYM(bitmap_create, "FPDFBitmap_CreateEx")
            FJ_SYM(bitmap_fill, "FPDFBitmap_FillRect")
            FJ_SYM(bitmap_destroy, "FPDFBitmap_Destroy")
            FJ_SYM(render, "FPDF_RenderPageBitmap")
#undef FJ_SYM
            w_api.init();
            w_initialized = true;
            return true;
        }

        int read_block(void *param, unsigned long position, unsigned char *buffer, unsigned long size) {
            FILE *file = static_cast<FILE *>(param);
#ifdef _WIN32
            if (_fseeki64(file, static_cast<long long>(position), SEEK_SET) != 0)
                return 0;
#else
            if (fseeko(file, static_cast<off_t>(position), SEEK_SET) != 0)
                return 0;
#endif
            return std::fread(buffer, 1, size, file) == size ? 1 : 0;
        }

        void close_document() {
            if (w_doc && w_api.close_document)
                w_api.close_document(w_doc);
            w_doc = nullptr;
            if (w_file)
                std::fclose(w_file);
            w_file = nullptr;
        }

        void on_opened(unsigned tag, OpenResult result);

        void on_rendered(unsigned tag, int page, int width, int height, std::shared_ptr<std::vector<uint8_t>> pixels);

        OpenResult open_document(const Job &job) {
            OpenResult result;
            close_document();
            if (!load_library(job.libraries)) {
                result.library = false;
                return result;
            }
#ifdef _WIN32
            w_file = _wfopen(wide(job.path).c_str(), L"rb");
#else
            w_file = std::fopen(job.path.c_str(), "rb");
#endif
            if (!w_file)
                return result;
            long long length = 0;
#ifdef _WIN32
            if (_fseeki64(w_file, 0, SEEK_END) == 0)
                length = _ftelli64(w_file);
#else
            if (fseeko(w_file, 0, SEEK_END) == 0)
                length = static_cast<long long>(ftello(w_file));
#endif
            if (length <= 0 || length > 0xFFFFFFFFLL) {
                close_document();
                return result;
            }
            w_access.length = static_cast<unsigned long>(length);
            w_access.get_block = read_block;
            w_access.param = w_file;
            w_doc = w_api.load_custom(&w_access, nullptr);
            if (!w_doc) {
                result.error = w_api.last_error();
                close_document();
                return result;
            }
            int count = w_api.page_count(w_doc);
            for (int i = 0; i < count; i++) {
                SizeF size{612, 792};
                if (!w_api.page_size(w_doc, i, &size) || size.width <= 0 || size.height <= 0)
                    size = SizeF{612, 792};
                result.sizes.push_back(size);
            }
            result.ok = count > 0;
            if (!result.ok)
                close_document();
            return result;
        }

        std::shared_ptr<std::vector<uint8_t>> render_page(const Job &job) {
            if (!w_doc || job.width <= 0 || job.height <= 0)
                return nullptr;
            void *page = w_api.load_page(w_doc, job.page);
            if (!page)
                return nullptr;
            auto pixels = std::make_shared<std::vector<uint8_t>>(size_t(job.width) * size_t(job.height) * 4);
            void *bitmap = w_api.bitmap_create(job.width, job.height, kBitmapBgra, pixels->data(), job.width * 4);
            if (!bitmap) {
                w_api.close_page(page);
                return nullptr;
            }
            w_api.bitmap_fill(bitmap, 0, 0, job.width, job.height, 0xFFFFFFFFul);
            w_api.render(bitmap, page, 0, 0, job.width, job.height, 0, kRenderAnnot | kRenderReverseBytes);
            w_api.bitmap_destroy(bitmap);
            w_api.close_page(page);
            return pixels;
        }

        void worker() {
            while (true) {
                Job job;
                {
                    std::unique_lock<std::mutex> lock(g_mutex);
                    g_wake.wait(lock, [] { return g_quit || !g_jobs.empty() || !g_renders.empty(); });
                    if (g_quit)
                        break;
                    if (!g_jobs.empty()) {
                        job = g_jobs.front();
                        g_jobs.pop_front();
                    } else {
                        job = g_renders.front();
                        g_renders.pop_front();
                    }
                }
                if (job.kind == JobOpen) {
                    w_tag = job.tag;
                    OpenResult result = open_document(job);
                    unsigned tag = job.tag;
                    slint::invoke_from_event_loop([tag, result] { on_opened(tag, result); });
                } else if (job.kind == JobRender) {
                    if (job.tag != w_tag)
                        continue;
                    auto pixels = render_page(job);
                    unsigned tag = job.tag;
                    int page = job.page, width = job.width, height = job.height;
                    slint::invoke_from_event_loop([tag, page, width, height, pixels] { on_rendered(tag, page, width, height, pixels); });
                } else {
                    close_document();
                    w_tag = 0;
#ifndef __APPLE__
                    unload_library();
#endif
                }
            }
            close_document();
            unload_library();
        }

        void post_job(Job job) {
            {
                std::lock_guard<std::mutex> lock(g_mutex);
                if (job.kind != JobRender)
                    g_renders.clear();
                g_jobs.push_back(std::move(job));
            }
            g_wake.notify_one();
        }

        void start_worker() {
            if (g_thread.joinable())
                return;
            g_quit = false;
            g_thread = std::thread(worker);
        }

        std::vector<std::string> library_names() {
            std::string folder = os::exe_folder();
            std::string tools = core_call("home", "");
            if (!tools.empty()) {
                char last = tools.back();
                if (last != '/' && last != '\\')
                    tools += '/';
                tools += "tools/";
            }
#ifdef _WIN32
            return {tools + "pdfium.dll", folder + "pdfium.dll", "pdfium.dll"};
#elif defined(__APPLE__)
            return {tools + "libpdfium.dylib", folder + "libpdfium.dylib", folder + "../Frameworks/libpdfium.dylib", "libpdfium.dylib"};
#else
            return {tools + "libpdfium.so", folder + "libpdfium.so", "libpdfium.so"};
#endif
        }

        const PdfUi &reader() {
            return ui().global<PdfUi>();
        }

        double scale() {
            float value = ui().window().scale_factor();
            return value > 0 ? double(value) : 1.0;
        }

        void pixel_size(int page, int &width, int &height) {
            const Box &box = g_boxes[page];
            double s = scale();
            double w = box.width * s, h = box.height * s;
            double area = w * h;
            if (area > kMaxPixels) {
                double shrink = std::sqrt(kMaxPixels / area);
                w *= shrink;
                h *= shrink;
            }
            width = std::max(1, int(std::lround(w)));
            height = std::max(1, int(std::lround(h)));
        }

        PdfPageRow row_for(int page) {
            PdfPageRow row;
            const Box &box = g_boxes[page];
            row.top = float(box.top);
            row.left = float(box.left);
            row.width = float(box.width);
            row.height = float(box.height);
            auto found = g_cache.find(page);
            row.ready = found != g_cache.end();
            if (row.ready)
                row.image = found->second.image;
            return row;
        }

        void publish_zoom() {
            char text[16];
            std::snprintf(text, sizeof text, "%d%%", int(std::lround(g_zoom * 100)));
            reader().set_zoom_label(slint::SharedString(text));
            reader().set_fit(g_fit);
        }

        void layout() {
            double widest = 0;
            for (const auto &size: g_sizes)
                widest = std::max(widest, double(size.width));
            if (widest <= 0)
                widest = 612;
            if (g_fit && g_vw > 0)
                g_zoom = std::clamp((g_vw - 2 * kSide) / (widest * kPoint), 0.05, 5.0);
            g_boxes.assign(g_sizes.size(), Box());
            double y = kGap;
            double content_w = 0;
            for (size_t i = 0; i < g_sizes.size(); i++) {
                Box &box = g_boxes[i];
                box.width = double(g_sizes[i].width) * kPoint * g_zoom;
                box.height = double(g_sizes[i].height) * kPoint * g_zoom;
                box.top = y;
                y += box.height + kGap;
                content_w = std::max(content_w, box.width + 2 * kSide);
            }
            g_content_w = content_w;
            g_content_h = y;
            double frame = std::max(g_vw, g_content_w);
            for (auto &box: g_boxes)
                box.left = std::max(kSide, (frame - box.width) / 2);
            std::vector<PdfPageRow> rows;
            rows.reserve(g_boxes.size());
            for (size_t i = 0; i < g_boxes.size(); i++)
                rows.push_back(row_for(int(i)));
            g_model = std::make_shared<slint::VectorModel<PdfPageRow>>(rows);
            auto &p = reader();
            p.set_pages(g_model);
            p.set_content_width(float(g_content_w));
            p.set_content_height(float(g_content_h));
            publish_zoom();
        }

        void jump_to(double x, double y) {
            double max_x = std::max(0.0, std::max(g_vw, g_content_w) - g_vw);
            double max_y = std::max(0.0, std::max(g_vh, g_content_h) - g_vh);
            g_vx = std::clamp(x, 0.0, max_x);
            g_vy = std::clamp(y, 0.0, max_y);
            auto &p = reader();
            p.set_jump_x(float(g_vx));
            p.set_jump_y(float(g_vy));
            p.set_jump_tick(++g_jump_tick);
        }

        void jump_to_page(int page) {
            if (g_boxes.empty())
                return;
            page = std::clamp(page, 0, int(g_boxes.size()) - 1);
            jump_to(g_vx, g_boxes[page].top - kGap / 2);
        }

        void remember_now() {
            g_remember_timer.stop();
            if (g_id.empty() || g_hidden || !g_loaded || g_page == g_remembered)
                return;
            g_remembered = g_page;
            core_call("pdf.page", "{\"id\":" + quote(g_id) + ",\"page\":" + std::to_string(g_page) + "}");
        }

        void publish_page() {
            auto &p = reader();
            p.set_page(g_page);
            p.set_count(int(g_boxes.size()));
            p.set_page_label(slint::SharedString(core_call("pdf.label", "{\"page\":" + std::to_string(g_page) + ",\"count\":" +
                                                                             std::to_string(g_boxes.size()) + "}")));
        }

        int current_page() {
            int count = int(g_boxes.size());
            if (count == 0)
                return 0;
            if (g_vy > 0 && g_vy + g_vh >= g_content_h - 2)
                return count - 1;
            for (int i = 0; i < count; i++) {
                const Box &box = g_boxes[i];
                double top = std::max(box.top, g_vy);
                double bottom = std::min(box.top + box.height, g_vy + g_vh);
                if (bottom - top >= 0.5 * std::min(box.height, g_vh) - 0.5)
                    return i;
            }
            return count - 1;
        }

        void evict(int keep_first, int keep_last) {
            while (g_cache.size() > kCacheLimit) {
                auto oldest = g_cache.end();
                for (auto it = g_cache.begin(); it != g_cache.end(); ++it) {
                    if (it->first >= keep_first && it->first <= keep_last)
                        continue;
                    if (oldest == g_cache.end() || it->second.used < oldest->second.used)
                        oldest = it;
                }
                if (oldest == g_cache.end())
                    break;
                int page = oldest->first;
                g_cache.erase(oldest);
                if (g_model && page < int(g_model->row_count()))
                    g_model->set_row_data(size_t(page), row_for(page));
            }
        }

        void update_view() {
            if (!g_loaded || g_boxes.empty() || g_vh <= 0)
                return;
            int count = int(g_boxes.size());
            int first = count, last = -1;
            for (int i = 0; i < count; i++) {
                const Box &box = g_boxes[i];
                if (box.top + box.height >= g_vy && box.top <= g_vy + g_vh) {
                    first = std::min(first, i);
                    last = std::max(last, i);
                }
            }
            if (last < 0) {
                first = last = std::clamp(current_page(), 0, count - 1);
            }
            g_first = first;
            g_last = last;
            int want_first = std::max(0, first - 1);
            int want_last = std::min(count - 1, last + 1);
            std::vector<Job> wanted;
            for (int i = first; i <= last; i++)
                wanted.push_back(Job{JobRender, g_tag, i});
            if (want_first < first)
                wanted.push_back(Job{JobRender, g_tag, want_first});
            if (want_last > last)
                wanted.push_back(Job{JobRender, g_tag, want_last});
            std::vector<Job> jobs;
            for (Job &job: wanted) {
                pixel_size(job.page, job.width, job.height);
                auto found = g_cache.find(job.page);
                if (found != g_cache.end()) {
                    found->second.used = ++g_clock;
                    if (found->second.width == job.width && found->second.height == job.height)
                        continue;
                }
                jobs.push_back(job);
            }
            {
                std::lock_guard<std::mutex> lock(g_mutex);
                g_renders.assign(jobs.begin(), jobs.end());
            }
            g_stat_asked += int(jobs.size());
            if (!jobs.empty())
                g_wake.notify_one();
            evict(want_first, want_last);
            int page = current_page();
            if (page != g_page || plain(reader().get_page_label()).empty()) {
                g_page = page;
                publish_page();
                if (!g_hidden)
                    g_remember_timer.start(slint::TimerMode::SingleShot, std::chrono::milliseconds(700), [] { remember_now(); });
            }
        }

        void relayout_keeping_place() {
            if (!g_loaded || g_boxes.empty()) {
                layout();
                return;
            }
            int page = std::clamp(g_page, 0, int(g_boxes.size()) - 1);
            double fraction = (g_vy - g_boxes[page].top) / std::max(1.0, g_boxes[page].height);
            double center = g_content_w > 0 ? (g_vx + g_vw / 2) / std::max(g_vw, g_content_w) : 0.5;
            layout();
            double y = g_boxes[page].top + fraction * g_boxes[page].height;
            double x = center * std::max(g_vw, g_content_w) - g_vw / 2;
            jump_to(x, y);
            update_view();
        }

        void reset_state() {
            g_remember_timer.stop();
            g_loaded = false;
            g_restored = false;
            g_error.clear();
            g_sizes.clear();
            g_boxes.clear();
            g_cache.clear();
            g_content_w = 0;
            g_content_h = 0;
            g_zoom = 1.0;
            g_fit = true;
            g_page = 0;
            g_first = 0;
            g_last = -1;
            g_remembered = -1;
            g_model = std::make_shared<slint::VectorModel<PdfPageRow>>();
            auto &p = reader();
            p.set_pages(g_model);
            p.set_content_width(0);
            p.set_content_height(0);
            p.set_jump_x(0);
            p.set_jump_y(0);
            p.set_page(0);
            p.set_count(0);
            p.set_page_label(slint::SharedString());
            p.set_error(slint::SharedString());
            p.set_error_system(false);
            p.set_goto_bad(false);
            publish_zoom();
        }

        void show_error(bool password) {
            auto &tr = ui().global<Tr>();
            g_error = plain(password ? tr.get_pdfPasswordProtected() : tr.get_pdfOpenFailed());
            auto &p = reader();
            p.set_loading(false);
            p.set_error(slint::SharedString(g_error));
            p.set_error_system(true);
        }

        void on_opened(unsigned tag, OpenResult result) {
            if (!g_open || tag != g_tag)
                return;
            if (!result.ok) {
                show_error(result.library && result.error == kErrorPassword);
                return;
            }
            g_sizes = std::move(result.sizes);
            g_loaded = true;
            reader().set_loading(false);
            layout();
            g_page = std::clamp(g_start_page, 0, int(g_boxes.size()) - 1);
            g_remembered = g_start_page;
            publish_page();
            if (g_have_view) {
                g_restored = true;
                jump_to_page(g_start_page);
                update_view();
            }
        }

        void on_rendered(unsigned tag, int page, int width, int height, std::shared_ptr<std::vector<uint8_t>> pixels) {
            if (!g_open || tag != g_tag)
                return;
            g_stat_done++;
            if (!pixels)
                g_stat_empty++;
            g_stat_last = std::to_string(page) + ":" + std::to_string(width);
            if (!pixels || page < 0 || page >= int(g_boxes.size()))
                return;
            slint::SharedPixelBuffer<slint::Rgba8Pixel> buffer(uint32_t(width), uint32_t(height),
                                                              reinterpret_cast<const slint::Rgba8Pixel *>(pixels->data()));
            Cached &entry = g_cache[page];
            entry.width = width;
            entry.height = height;
            entry.image = slint::Image(buffer);
            entry.used = ++g_clock;
            if (g_model && page < int(g_model->row_count()))
                g_model->set_row_data(size_t(page), row_for(page));
            evict(std::max(0, g_first - 1), std::min(int(g_boxes.size()) - 1, g_last + 1));
        }

        void on_viewport(float x, float y, float w, float h) {
            if (!g_open)
                return;
            bool width_changed = std::fabs(double(w) - g_vw) > 0.5;
            g_vx = x;
            g_vy = y;
            g_vw = w;
            g_vh = h;
            g_have_view = w > 0 && h > 0;
            if (!g_loaded || !g_have_view)
                return;
            if (!g_restored) {
                g_restored = true;
                layout();
                jump_to_page(g_start_page);
                update_view();
                return;
            }
            if (width_changed) {
                relayout_keeping_place();
                return;
            }
            update_view();
        }

        void zoom(int direction) {
            if (!g_loaded || direction == 0)
                return;
            char number[32];
            std::snprintf(number, sizeof number, "%.4f", g_zoom);
            std::string answer = core_call("pdf.zoom", std::string("{\"zoom\":\"") + number + "\",\"in\":\"" + (direction > 0 ? "true" : "false") + "\"}");
            double next = answer.empty() ? g_zoom : std::atof(answer.c_str());
            if (next <= 0)
                return;
            if (std::fabs(next - g_zoom) < 1e-4 && !g_fit)
                return;
            g_zoom = next;
            g_fit = false;
            relayout_keeping_place();
        }

        void fit_width() {
            if (!g_loaded)
                return;
            g_fit = true;
            relayout_keeping_place();
        }

        void go_to(const std::string &text) {
            if (!g_loaded)
                return;
            std::string answer = core_call("pdf.goto", "{\"text\":" + quote(text) + ",\"count\":" + std::to_string(g_boxes.size()) + "}");
            int page = answer.empty() ? -1 : std::atoi(answer.c_str());
            auto &p = reader();
            p.set_goto_bad(page < 0);
            p.set_goto_tick(++g_goto_tick);
            if (page >= 0) {
                jump_to_page(page);
                update_view();
            }
        }

        void step(int delta) {
            if (!g_loaded)
                return;
            jump_to_page(g_page + delta);
            update_view();
        }

        void edge(bool end) {
            if (!g_loaded)
                return;
            jump_to(g_vx, end ? g_content_h : 0);
            update_view();
        }

        void open_system() {
            std::string args = "{\"id\":" + quote(g_id) + ",\"path\":" + quote(g_path) + ",\"hidden\":\"" + (g_hidden ? "true" : "false") + "\"}";
            close();
            core_call("pdf.system", args);
        }

    }

    void wire() {
        if (g_wired)
            return;
        g_wired = true;
        auto &p = reader();
        p.on_close([] { close(); });
        p.on_zoom([](int direction) { zoom(direction); });
        p.on_fit_width([] { fit_width(); });
        p.on_viewport([](float x, float y, float w, float h) { on_viewport(x, y, w, h); });
        p.on_go_to([](slint::SharedString text) { go_to(plain(text)); });
        p.on_step([](int delta) { step(delta); });
        p.on_edge([](bool end) { edge(end); });
        p.on_system([] { open_system(); });
    }

    void open(const std::string &json_text) {
        wire();
        fj::Json json;
        std::string error;
        if (!fj::parse_json(json_text, json, error))
            return;
        if (g_open)
            close();
        g_id.clear();
        g_title.clear();
        g_path.clear();
        g_hidden = false;
        g_start_page = 0;
        if (auto *v = json.find("id")) g_id = v->text;
        if (auto *v = json.find("title")) g_title = v->text;
        if (auto *v = json.find("path")) g_path = v->text;
        if (auto *v = json.find("hidden")) g_hidden = v->boolean;
        if (auto *v = json.find("page")) g_start_page = std::max(0, int(v->number));
        if (g_path.empty())
            return;
        start_worker();
        g_tag++;
        g_open = true;
        reset_state();
        auto &p = reader();
        p.set_title(slint::SharedString(g_title));
        p.set_loading(true);
        p.set_open(true);
        Job job;
        job.kind = JobOpen;
        job.tag = g_tag;
        job.path = g_path;
        job.libraries = library_names();
        post_job(std::move(job));
    }

    void close() {
        if (!g_open)
            return;
        remember_now();
        g_open = false;
        g_tag++;
        Job job;
        job.kind = JobClose;
        post_job(std::move(job));
        if (g_hidden)
            core_call("pdf.closed", "{}");
        reset_state();
        g_have_view = false;
        g_vw = g_vh = g_vx = g_vy = 0;
        auto &p = reader();
        p.set_loading(false);
        p.set_open(false);
        g_model.reset();
    }

    void shutdown() {
        close();
        if (!g_thread.joinable())
            return;
        {
            std::lock_guard<std::mutex> lock(g_mutex);
            g_quit = true;
            g_jobs.clear();
            g_renders.clear();
        }
        g_wake.notify_one();
        g_thread.join();
    }

    void debug(const std::string &name, double value) {
        if (name == "close") {
            close();
        } else if (name == "goto") {
            go_to(std::to_string(long(value)));
        } else if (name == "gotobad") {
            go_to("abc");
        } else if (name == "zoomin") {
            zoom(1);
        } else if (name == "zoomout") {
            zoom(-1);
        } else if (name == "fit") {
            fit_width();
        } else if (name == "step") {
            step(int(value));
        } else if (name == "end") {
            edge(true);
        } else if (name == "home") {
            edge(false);
        } else if (name == "scroll") {
            jump_to(g_vx, value);
            update_view();
        } else if (name == "system") {
            open_system();
        }
        const char *path = std::getenv("FJ_PDF_LOG");
        if (!path)
            return;
        if (FILE *file = std::fopen(path, "a")) {
            int ready = 0, visible = 0;
            for (int i = g_first; i <= g_last && i < int(g_boxes.size()); i++) {
                visible++;
                int w = 0, h = 0;
                pixel_size(i, w, h);
                auto found = g_cache.find(i);
                if (found != g_cache.end() && found->second.width == w)
                    ready++;
            }
            int page_pixels = 0;
            auto current = g_cache.find(g_page);
            if (current != g_cache.end())
                page_pixels = current->second.width;
            std::fprintf(file, "%s %g -> open=%d loading=%d count=%zu page=%d label=%s zoom=%.4f fit=%d visible=%d ready=%d pw=%d view=%.0fx%.0f y=%.1f content=%.0fx%.0f scale=%.2f asked=%d done=%d empty=%d last=%s hidden=%d error=%s id=%s path=%s\n",
                         name.c_str(), value, g_open ? 1 : 0, reader().get_loading() ? 1 : 0, g_boxes.size(), g_page,
                         plain(reader().get_page_label()).c_str(), g_zoom, g_fit ? 1 : 0, visible, ready, page_pixels, g_vw, g_vh, g_vy,
                         g_content_w, g_content_h, scale(), g_stat_asked, g_stat_done, g_stat_empty, g_stat_last.c_str(), g_hidden ? 1 : 0, g_error.c_str(), g_id.c_str(), g_path.c_str());
            std::fclose(file);
        }
    }

}
