#define NOMINMAX

#include <windows.h>

#include <winrt/base.h>
#include <winrt/Windows.Foundation.h>
#include <winrt/Windows.Security.Credentials.UI.h>

#include <userconsentverifierinterop.h>

#include <string>
#include <thread>

namespace fjhost::os {

    void *main_window();

    namespace {

        using winrt::Windows::Foundation::IAsyncOperation;
        using winrt::Windows::Security::Credentials::UI::UserConsentVerificationResult;
        using winrt::Windows::Security::Credentials::UI::UserConsentVerifier;
        using winrt::Windows::Security::Credentials::UI::UserConsentVerifierAvailability;

        bool available_here() {
            return UserConsentVerifier::CheckAvailabilityAsync().get() == UserConsentVerifierAvailability::Available;
        }

        bool verify_here(const std::string &reason) {
            winrt::hstring message = winrt::to_hstring(reason);
            HWND window = static_cast<HWND>(main_window());
            IAsyncOperation<UserConsentVerificationResult> request{nullptr};
            if (window) {
                auto interop = winrt::get_activation_factory<UserConsentVerifier, IUserConsentVerifierInterop>();
                winrt::check_hresult(interop->RequestVerificationForWindowAsync(
                        window, static_cast<HSTRING>(winrt::get_abi(message)),
                        winrt::guid_of<IAsyncOperation<UserConsentVerificationResult>>(), winrt::put_abi(request)));
            } else {
                request = UserConsentVerifier::RequestVerificationAsync(message);
            }
            return request.get() == UserConsentVerificationResult::Verified;
        }

        template<class Work>
        bool on_worker(Work work) {
            bool answer = false;
            std::thread worker([&answer, &work] {
                try {
                    winrt::init_apartment(winrt::apartment_type::multi_threaded);
                    answer = work();
                } catch (...) {
                    answer = false;
                }
                winrt::uninit_apartment();
            });
            worker.join();
            return answer;
        }

    }

    bool auth_available() {
        return on_worker([] { return available_here(); });
    }

    bool auth_verify(const std::string &reason) {
        return on_worker([&reason] { return verify_here(reason); });
    }

}
