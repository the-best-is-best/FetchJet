import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed, ON_LINUX, ON_MAC, server_sha256, sha256_of_file

PROXY_LOGIN_HTTP = 18081
PROXY_LOGIN_SOCKS = 11081
PROXY_OPEN_HTTP = 18082
MAPPED = "files.fjtest.example"
HTTPS_PORT = int(os.environ.get("FJ_TEST_HTTPS_PORT", "18443"))
HTTP_PORT = 18200


def set_proxy(core, kind, port, user="", password=""):
    core.action("SettingPick", id="proxyKind", index=kind)
    core.pump(0.2)
    if kind:
        core.action("SettingText", id="proxyHost", text="127.0.0.1")
        core.action("SettingText", id="proxyPort", text=str(port))
        core.action("SettingText", id="proxyUser", text=user)
        core.pump(0.1)
        core.action("SettingText", id="proxyPassword", text=password)
    core.pump(0.2)
    core.action("SettingClick", id="proxySave")
    core.pump(0.5)


def fetch(core, url, name, expect_ok=True, timeout=60):
    core.check("addDirect accepted: " + name, core.debug("addDirect", {"url": url, "fileName": name}) == "ok")
    wanted = "COMPLETED" if expect_ok else ["FAILED", "ERROR"]
    try:
        return core.wait_task(wanted, timeout=timeout, file_name=name)
    except CheckFailed as failure:
        core.check(str(failure), False)
        return None


def same_file(core, task, size_name):
    if not task:
        return False
    path = os.path.join(task["destinationPath"], task["fileName"])
    return os.path.isfile(path) and sha256_of_file(path) == server_sha256(size_name)


def run():
    core = Core("network")
    try:
        core.action("ShowSettings", show=True)
        core.pump(0.3)

        print("HTTP proxy with a login, plain http to a name only the proxy knows")
        set_proxy(core, 1, PROXY_LOGIN_HTTP, "fj", "secret")
        task = fetch(core, "http://%s:%d/file/2MB.bin?key=net-http-login" % (MAPPED, HTTP_PORT), "net-http-login.bin")
        core.check("plain http through the HTTP proxy with login", same_file(core, task, "2MB.bin"))

        if ON_LINUX and not ON_MAC:
            print("HTTPS through the HTTP proxy with login (CONNECT)")
            task = fetch(core, "https://%s:%d/file/3MB.bin?key=net-https-login" % (MAPPED, HTTPS_PORT), "net-https-login.bin")
            core.check("https through the HTTP proxy with login", same_file(core, task, "3MB.bin"))
        else:
            print("  skip  https through the proxy: the test certificate is not trusted by WinHTTP (Linux only)")

        print("wrong proxy password fails")
        set_proxy(core, 1, PROXY_LOGIN_HTTP, "fj", "wrong")
        task = fetch(core, "http://%s:%d/file/1MB.bin?key=net-wrong" % (MAPPED, HTTP_PORT), "net-wrong.bin", expect_ok=False, timeout=90)
        core.check("wrong password: the download fails", task is not None and task["status"] != "COMPLETED", task and task["status"])

        print("SOCKS5 proxy with a login")
        set_proxy(core, 2, PROXY_LOGIN_SOCKS, "fj", "secret")
        task = fetch(core, "http://%s:%d/file/2MB.bin?key=net-socks" % (MAPPED, HTTP_PORT), "net-socks.bin")
        core.check("plain http through SOCKS5 with login", same_file(core, task, "2MB.bin"))
        if ON_LINUX and not ON_MAC:
            task = fetch(core, "https://%s:%d/file/1MB.bin?key=net-socks-tls" % (MAPPED, HTTPS_PORT), "net-socks-tls.bin")
            core.check("https through SOCKS5 with login", same_file(core, task, "1MB.bin"))

        print("HTTP proxy without a login")
        set_proxy(core, 1, PROXY_OPEN_HTTP)
        task = fetch(core, "http://%s:%d/file/1MB.bin?key=net-open" % (MAPPED, HTTP_PORT), "net-open.bin")
        core.check("plain http through the open HTTP proxy", same_file(core, task, "1MB.bin"))

        print("proxy off: the mapped name cannot be reached")
        set_proxy(core, 0, 0)
        task = fetch(core, "http://%s:%d/file/1MB.bin?key=net-off" % (MAPPED, HTTP_PORT), "net-off.bin", expect_ok=False, timeout=90)
        core.check("without the proxy the name does not resolve", task is not None and task["status"] != "COMPLETED")

        if ON_LINUX and not ON_MAC:
            print("HTTPS: the self-signed test server is refused without its certificate")
            os.environ.pop("FETCHJET_CA_BUNDLE", None)
            set_proxy(core, 1, PROXY_OPEN_HTTP)
            set_proxy(core, 0, 0)
            task = fetch(core, "https://127.0.0.1:%d/file/1MB.bin?key=net-untrusted" % HTTPS_PORT, "net-untrusted.bin", expect_ok=False, timeout=90)
            core.check("untrusted certificate: the download fails", task is not None and task["status"] != "COMPLETED")
    except CheckFailed as failure:
        core.check(str(failure), False)
        print(core.log_text()[-3000:])
    core.finish()


if __name__ == "__main__":
    run()
