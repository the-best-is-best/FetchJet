#define NOMINMAX

#include <windows.h>

#include <winrt/base.h>
#include <winrt/Windows.ApplicationModel.h>
#include <winrt/Windows.Foundation.h>
#include <winrt/Windows.Foundation.Collections.h>
#include <winrt/Windows.Services.Store.h>

#include <chrono>
#include <cstdio>
#include <string>
#include <thread>

namespace fjhost::os {

    namespace {

        std::string ask_store() {
            using namespace winrt::Windows::Foundation;
            using namespace winrt::Windows::Services::Store;
            auto context = StoreContext::GetDefault();
            auto request = context.GetAppAndOptionalStorePackageUpdatesAsync();
            if (request.wait_for(std::chrono::seconds(60)) == AsyncStatus::Started) {
                request.Cancel();
                return "FJERR the Store did not answer in time";
            }
            std::string versions;
            for (const auto &update: request.GetResults()) {
                auto version = update.Package().Id().Version();
                if (!versions.empty())
                    versions += ",";
                versions += std::to_string(version.Major) + "." + std::to_string(version.Minor) + "." +
                            std::to_string(version.Build);
            }
            return "FJSTORE " + versions;
        }

    }

    std::string store_updates() {
        std::string answer;
        std::thread worker([&answer] {
            try {
                winrt::init_apartment(winrt::apartment_type::multi_threaded);
                answer = ask_store();
            } catch (const winrt::hresult_error &error) {
                char code[16];
                std::snprintf(code, sizeof(code), "0x%08X", static_cast<unsigned int>(error.code().value));
                std::string text = winrt::to_string(error.message());
                answer = "FJERR " + (text.empty() ? std::string(code) : text + " (" + code + ")");
            } catch (...) {
                answer = "FJERR unknown error";
            }
            winrt::uninit_apartment();
        });
        worker.join();
        return answer;
    }

}
