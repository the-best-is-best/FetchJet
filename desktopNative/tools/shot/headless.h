#pragma once

#ifndef NOMINMAX
#    define NOMINMAX
#endif
#ifndef WIN32_LEAN_AND_MEAN
#    define WIN32_LEAN_AND_MEAN
#endif

#include <slint-platform.h>

#include <windows.h>
#include <objbase.h>
#include <wincodec.h>

#include <cmath>
#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>

namespace shot {

    inline std::wstring widen(const std::string &s) {
        if (s.empty())
            return {};
        int n = MultiByteToWideChar(CP_UTF8, 0, s.data(), int(s.size()), nullptr, 0);
        std::wstring w(size_t(n), L'\0');
        MultiByteToWideChar(CP_UTF8, 0, s.data(), int(s.size()), w.data(), n);
        return w;
    }

    inline std::string narrow(const std::wstring &w) {
        if (w.empty())
            return {};
        int n = WideCharToMultiByte(CP_UTF8, 0, w.data(), int(w.size()), nullptr, 0, nullptr,
                                    nullptr);
        std::string s(size_t(n), '\0');
        WideCharToMultiByte(CP_UTF8, 0, w.data(), int(w.size()), s.data(), n, nullptr, nullptr);
        return s;
    }

    class HeadlessAdapter : public slint::platform::WindowAdapter {
    public:
        slint::platform::SoftwareRenderer software{
                slint::platform::SoftwareRenderer::RepaintBufferType::NewBuffer
        };
        slint::PhysicalSize physical{{0, 0}};
        std::optional <slint::LogicalSize> preferred;
        std::optional <slint::LogicalSize> minimum;
        std::optional <slint::LogicalSize> maximum;
        std::string title;
        bool visible = false;

        slint::platform::AbstractRenderer &renderer() override { return software; }

        slint::PhysicalSize size() override { return physical; }

        void set_visible(bool v) override { visible = v; }

        void set_size(slint::PhysicalSize s) override {
            physical = s;
            float f = window().scale_factor();
            window().dispatch_resize_event(
                    slint::LogicalSize({float(s.width) / f, float(s.height) / f}));
        }

        void update_window_properties(const WindowProperties &p) override {
            auto t = p.title();
            title.assign(t.data(), t.size());
            auto c = p.layout_constraints();
            preferred = c.preferred;
            minimum = c.min;
            maximum = c.max;
        }

        void resize(float logicalWidth, float logicalHeight, float scale) {
            window().dispatch_scale_factor_change_event(scale);
            physical = slint::PhysicalSize({uint32_t(std::lround(logicalWidth * scale)),
                                            uint32_t(std::lround(logicalHeight * scale))});
            window().dispatch_resize_event(slint::LogicalSize({logicalWidth, logicalHeight}));
        }

        std::vector <slint::Rgb8Pixel> render() {
            slint::platform::update_timers_and_animations();
            std::vector <slint::Rgb8Pixel> pixels(size_t(physical.width) * physical.height);
            software.render(std::span<slint::Rgb8Pixel>(pixels.data(), pixels.size()),
                            physical.width);
            return pixels;
        }
    };

    class HeadlessPlatform : public slint::platform::Platform {
    public:
        std::vector<HeadlessAdapter *> *adapters;

        explicit HeadlessPlatform(std::vector<HeadlessAdapter *> *list) : adapters(list) {}

        std::unique_ptr <slint::platform::WindowAdapter> create_window_adapter() override {
            auto a = std::make_unique<HeadlessAdapter>();
            adapters->push_back(a.get());
            return a;
        }
    };

    inline bool
    writePng(const std::wstring &path, uint32_t width, uint32_t height, const uint8_t *bgra,
             bool withAlpha, std::string *error) {
        HRESULT hr = CoInitializeEx(nullptr, COINIT_APARTMENTTHREADED);
        bool uninit = SUCCEEDED(hr);
        IWICImagingFactory *factory = nullptr;
        IWICStream *stream = nullptr;
        IWICBitmapEncoder *encoder = nullptr;
        IWICBitmapFrameEncode *frame = nullptr;
        auto fail = [&](const char *what) {
            if (error)
                *error = std::string(what) + " failed, hr=" + std::to_string(unsigned(hr));
            if (frame)
                frame->Release();
            if (encoder)
                encoder->Release();
            if (stream)
                stream->Release();
            if (factory)
                factory->Release();
            if (uninit)
                CoUninitialize();
            return false;
        };
        hr = CoCreateInstance(CLSID_WICImagingFactory, nullptr, CLSCTX_INPROC_SERVER,
                              IID_PPV_ARGS(&factory));
        if (FAILED(hr))
            return fail("WIC factory");
        hr = factory->CreateStream(&stream);
        if (FAILED(hr))
            return fail("CreateStream");
        hr = stream->InitializeFromFilename(path.c_str(), GENERIC_WRITE);
        if (FAILED(hr))
            return fail("open output file");
        hr = factory->CreateEncoder(GUID_ContainerFormatPng, nullptr, &encoder);
        if (FAILED(hr))
            return fail("CreateEncoder");
        hr = encoder->Initialize(stream, WICBitmapEncoderNoCache);
        if (FAILED(hr))
            return fail("encoder Initialize");
        hr = encoder->CreateNewFrame(&frame, nullptr);
        if (FAILED(hr))
            return fail("CreateNewFrame");
        hr = frame->Initialize(nullptr);
        if (FAILED(hr))
            return fail("frame Initialize");
        hr = frame->SetSize(width, height);
        if (FAILED(hr))
            return fail("SetSize");
        WICPixelFormatGUID wanted = withAlpha ? GUID_WICPixelFormat32bppBGRA
                                              : GUID_WICPixelFormat24bppBGR;
        WICPixelFormatGUID format = wanted;
        hr = frame->SetPixelFormat(&format);
        if (FAILED(hr) || !IsEqualGUID(format, wanted)) {
            if (SUCCEEDED(hr))
                hr = E_FAIL;
            return fail("SetPixelFormat");
        }
        UINT bytesPerPixel = withAlpha ? 4 : 3;
        UINT stride = width * bytesPerPixel;
        hr = frame->WritePixels(height, stride, stride * height, const_cast<BYTE *>(bgra));
        if (FAILED(hr))
            return fail("WritePixels");
        hr = frame->Commit();
        if (FAILED(hr))
            return fail("frame Commit");
        hr = encoder->Commit();
        if (FAILED(hr))
            return fail("encoder Commit");
        frame->Release();
        encoder->Release();
        stream->Release();
        factory->Release();
        if (uninit)
            CoUninitialize();
        return true;
    }

    inline bool writePngRgb(const std::wstring &path, uint32_t width, uint32_t height,
                            const std::vector <slint::Rgb8Pixel> &pixels, std::string *error) {
        std::vector <uint8_t> bgr(size_t(width) * height * 3);
        for (size_t i = 0; i < pixels.size(); i++) {
            bgr[i * 3] = pixels[i].b;
            bgr[i * 3 + 1] = pixels[i].g;
            bgr[i * 3 + 2] = pixels[i].r;
        }
        return writePng(path, width, height, bgr.data(), false, error);
    }

    inline bool writePngRgba(const std::wstring &path, uint32_t width, uint32_t height,
                             const slint::Rgba8Pixel *pixels, std::string *error) {
        std::vector <uint8_t> bgra(size_t(width) * height * 4);
        for (size_t i = 0; i < size_t(width) * height; i++) {
            bgra[i * 4] = pixels[i].b;
            bgra[i * 4 + 1] = pixels[i].g;
            bgra[i * 4 + 2] = pixels[i].r;
            bgra[i * 4 + 3] = pixels[i].a;
        }
        return writePng(path, width, height, bgra.data(), true, error);
    }

}
