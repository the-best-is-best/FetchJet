#pragma once

#ifndef NOMINMAX
#    define NOMINMAX
#endif
#ifndef WIN32_LEAN_AND_MEAN
#    define WIN32_LEAN_AND_MEAN
#endif

#include <slint.h>

#include <windows.h>
#include <objbase.h>
#include <d2d1.h>
#include <dwrite.h>
#include <wincodec.h>

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <string>

namespace fj {

    struct EmojiMetrics {
        float layoutWidth = 0;
        float layoutHeight = 0;
        float inkLeft = 0;
        float inkTop = 0;
        float inkWidth = 0;
        float inkHeight = 0;
    };

    class EmojiRasterizer {
    public:
        explicit EmojiRasterizer(bool sharedFontCache = true, bool reuseCanvas = true)
                : shared(sharedFontCache), reuse(reuseCanvas) {
        }

        EmojiRasterizer(const EmojiRasterizer &) = delete;

        EmojiRasterizer &operator=(const EmojiRasterizer &) = delete;

        ~EmojiRasterizer() { close(); }

        bool open() {
            if (d2d)
                return true;
            HRESULT hr = CoInitializeEx(nullptr, COINIT_APARTMENTTHREADED);
            comOwned = SUCCEEDED(hr);
            hr = D2D1CreateFactory(D2D1_FACTORY_TYPE_SINGLE_THREADED, __uuidof(ID2D1Factory),
                                   nullptr,
                                   reinterpret_cast<void **>(&d2d));
            if (FAILED(hr))
                return fail();
            hr = DWriteCreateFactory(
                    shared ? DWRITE_FACTORY_TYPE_SHARED : DWRITE_FACTORY_TYPE_ISOLATED,
                    __uuidof(IDWriteFactory), reinterpret_cast<IUnknown **>(&dwrite));
            if (FAILED(hr))
                return fail();
            hr = CoCreateInstance(CLSID_WICImagingFactory, nullptr, CLSCTX_INPROC_SERVER,
                                  IID_PPV_ARGS(&wic));
            if (FAILED(hr))
                return fail();
            return true;
        }

        void close() {
            dropCanvas();
            if (wic)
                wic->Release();
            if (dwrite)
                dwrite->Release();
            if (d2d)
                d2d->Release();
            wic = nullptr;
            dwrite = nullptr;
            d2d = nullptr;
            if (comOwned)
                CoUninitialize();
            comOwned = false;
        }

        bool
        measure(const std::wstring &text, float fontPx, const wchar_t *family, EmojiMetrics &out) {
            IDWriteTextLayout *layout = makeLayout(text, fontPx, family);
            if (!layout)
                return false;
            read(layout, out);
            layout->Release();
            return true;
        }

        slint::SharedPixelBuffer <slint::Rgba8Pixel>
        renderText(const std::wstring &text, float fontPx,
                   const wchar_t *family = L"Segoe UI Emoji",
                   uint32_t monoColor = 0xFFFFFFFF) {
            IDWriteTextLayout *layout = makeLayout(text, fontPx, family);
            if (!layout)
                return {};
            EmojiMetrics m;
            read(layout, m);
            uint32_t w = uint32_t(std::max(1.0f, std::ceil(m.layoutWidth)));
            uint32_t h = uint32_t(std::max(1.0f, std::ceil(m.layoutHeight)));
            auto result = draw(layout, w, h, 0, 0, monoColor);
            layout->Release();
            return result;
        }

        slint::SharedPixelBuffer <slint::Rgba8Pixel>
        renderIcon(const std::wstring &text, uint32_t boxPx,
                   const wchar_t *family = L"Segoe UI Emoji",
                   uint32_t monoColor = 0xFFFFFFFF) {
            if (boxPx == 0)
                return {};
            EmojiMetrics probe;
            float fontPx = float(boxPx);
            if (!measure(text, fontPx, family, probe) || probe.inkWidth <= 0 ||
                probe.inkHeight <= 0)
                return {};
            fontPx = fontPx * float(boxPx) / std::max(probe.inkWidth, probe.inkHeight);
            IDWriteTextLayout *layout = makeLayout(text, fontPx, family);
            if (!layout)
                return {};
            EmojiMetrics m;
            read(layout, m);
            float x = (float(boxPx) - m.inkWidth) / 2 - m.inkLeft;
            float y = (float(boxPx) - m.inkHeight) / 2 - m.inkTop;
            auto result = draw(layout, boxPx, boxPx, x, y, monoColor);
            layout->Release();
            return result;
        }

    private:
        ID2D1Factory *d2d = nullptr;
        IDWriteFactory *dwrite = nullptr;
        IWICImagingFactory *wic = nullptr;
        IWICBitmap *canvas = nullptr;
        ID2D1RenderTarget *target = nullptr;
        ID2D1SolidColorBrush *brush = nullptr;
        uint32_t canvasWidth = 0;
        uint32_t canvasHeight = 0;
        bool comOwned = false;
        bool shared = true;
        bool reuse = true;

        static constexpr float kMaxBox = 8192.0f;

        bool fail() {
            close();
            return false;
        }

        void dropCanvas() {
            if (brush)
                brush->Release();
            if (target)
                target->Release();
            if (canvas)
                canvas->Release();
            brush = nullptr;
            target = nullptr;
            canvas = nullptr;
            canvasWidth = 0;
            canvasHeight = 0;
        }

        bool ensureCanvas(uint32_t w, uint32_t h) {
            if (target && reuse && w <= canvasWidth && h <= canvasHeight)
                return true;
            uint32_t newWidth = reuse ? std::max({w, canvasWidth, 64u}) : w;
            uint32_t newHeight = reuse ? std::max({h, canvasHeight, 64u}) : h;
            dropCanvas();
            HRESULT hr = wic->CreateBitmap(newWidth, newHeight, GUID_WICPixelFormat32bppPBGRA,
                                           WICBitmapCacheOnLoad, &canvas);
            if (FAILED(hr))
                return false;
            D2D1_RENDER_TARGET_PROPERTIES props = D2D1::RenderTargetProperties(
                    D2D1_RENDER_TARGET_TYPE_SOFTWARE,
                    D2D1::PixelFormat(DXGI_FORMAT_B8G8R8A8_UNORM, D2D1_ALPHA_MODE_PREMULTIPLIED),
                    96.0f,
                    96.0f);
            hr = d2d->CreateWicBitmapRenderTarget(canvas, props, &target);
            if (FAILED(hr)) {
                dropCanvas();
                return false;
            }
            target->SetTextAntialiasMode(D2D1_TEXT_ANTIALIAS_MODE_GRAYSCALE);
            target->CreateSolidColorBrush(D2D1::ColorF(1, 1, 1, 1), &brush);
            canvasWidth = newWidth;
            canvasHeight = newHeight;
            return brush != nullptr;
        }

        IDWriteTextLayout *
        makeLayout(const std::wstring &text, float fontPx, const wchar_t *family) {
            if (!open())
                return nullptr;
            IDWriteTextFormat *format = nullptr;
            HRESULT hr = dwrite->CreateTextFormat(family, nullptr, DWRITE_FONT_WEIGHT_NORMAL,
                                                  DWRITE_FONT_STYLE_NORMAL,
                                                  DWRITE_FONT_STRETCH_NORMAL,
                                                  fontPx, L"", &format);
            if (FAILED(hr))
                return nullptr;
            format->SetWordWrapping(DWRITE_WORD_WRAPPING_NO_WRAP);
            IDWriteTextLayout *layout = nullptr;
            hr = dwrite->CreateTextLayout(text.data(), UINT32(text.size()), format, kMaxBox,
                                          kMaxBox,
                                          &layout);
            format->Release();
            return SUCCEEDED(hr) ? layout : nullptr;
        }

        static void read(IDWriteTextLayout *layout, EmojiMetrics &out) {
            DWRITE_TEXT_METRICS tm{};
            layout->GetMetrics(&tm);
            DWRITE_OVERHANG_METRICS oh{};
            layout->GetOverhangMetrics(&oh);
            out.layoutWidth = tm.widthIncludingTrailingWhitespace;
            out.layoutHeight = tm.height;
            out.inkLeft = -oh.left;
            out.inkTop = -oh.top;
            out.inkWidth = (kMaxBox + oh.right) - out.inkLeft;
            out.inkHeight = (kMaxBox + oh.bottom) - out.inkTop;
        }

        slint::SharedPixelBuffer <slint::Rgba8Pixel> draw(IDWriteTextLayout *layout, uint32_t w,
                                                          uint32_t h, float x, float y,
                                                          uint32_t monoColor) {
            if (!ensureCanvas(w, h))
                return {};
            brush->SetColor(D2D1::ColorF(float((monoColor >> 16) & 0xFF) / 255.0f,
                                         float((monoColor >> 8) & 0xFF) / 255.0f,
                                         float(monoColor & 0xFF) / 255.0f,
                                         float((monoColor >> 24) & 0xFF) / 255.0f));
            target->BeginDraw();
            target->Clear(D2D1::ColorF(0, 0, 0, 0));
            target->PushAxisAlignedClip(D2D1::RectF(0, 0, float(w), float(h)),
                                        D2D1_ANTIALIAS_MODE_ALIASED);
            target->DrawTextLayout(D2D1::Point2F(x, y), layout, brush,
                                   static_cast<D2D1_DRAW_TEXT_OPTIONS>(0x00000004));
            target->PopAxisAlignedClip();
            HRESULT hr = target->EndDraw();
            slint::SharedPixelBuffer <slint::Rgba8Pixel> result;
            if (FAILED(hr)) {
                dropCanvas();
                return result;
            }
            WICRect rect{0, 0, INT(w), INT(h)};
            IWICBitmapLock *lock = nullptr;
            if (SUCCEEDED(canvas->Lock(&rect, WICBitmapLockRead, &lock))) {
                UINT stride = 0, size = 0;
                BYTE *data = nullptr;
                lock->GetStride(&stride);
                lock->GetDataPointer(&size, &data);
                result = slint::SharedPixelBuffer<slint::Rgba8Pixel>(w, h);
                slint::Rgba8Pixel *dst = result.begin();
                for (uint32_t row = 0; row < h; row++) {
                    const BYTE *src = data + size_t(row) * stride;
                    for (uint32_t col = 0; col < w; col++) {
                        unsigned b = src[col * 4], g = src[col * 4 + 1], r = src[col * 4 + 2],
                                a = src[col * 4 + 3];
                        if (a != 0 && a != 255) {
                            r = std::min(255u, (r * 255 + a / 2) / a);
                            g = std::min(255u, (g * 255 + a / 2) / a);
                            b = std::min(255u, (b * 255 + a / 2) / a);
                        }
                        dst[size_t(row) * w + col] =
                                slint::Rgba8Pixel{uint8_t(r), uint8_t(g), uint8_t(b), uint8_t(a)};
                    }
                }
                lock->Release();
            }
            if (!reuse)
                dropCanvas();
            return result;
        }
    };

}
