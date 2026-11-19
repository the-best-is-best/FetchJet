import os
import sys
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from corehost import Core, CheckFailed, ENUMS, install_fake_ytdlp

STATE = {name: index for index, name in enumerate(ENUMS["MediaStateCode"])}
ENGINE = {name: index for index, name in enumerate(ENUMS["EngineCode"])}
PORT = int(os.environ.get("FJ_DRM_PORT", "18270"))
BASE = "http://127.0.0.1:%d" % PORT

FILES = {
    "/drm.mpd": ("application/dash+xml",
                 '<?xml version="1.0"?><MPD xmlns="urn:mpeg:dash:schema:mpd:2011" type="static" mediaPresentationDuration="PT10S">'
                 '<Period><AdaptationSet mimeType="video/mp4"><ContentProtection schemeIdUri="urn:mpeg:dash:mp4protection:2011" value="cenc"/>'
                 '<ContentProtection schemeIdUri="urn:uuid:edef8ba9-79d6-4ace-a3c8-27dcd51d21ed"/>'
                 '<Representation id="1" bandwidth="500000" width="640" height="360"><BaseURL>v.mp4</BaseURL></Representation>'
                 '</AdaptationSet></Period></MPD>'),
    "/drm.m3u8": ("application/vnd.apple.mpegurl",
                  '#EXTM3U\n#EXT-X-SESSION-KEY:METHOD=SAMPLE-AES,URI="skd://key",KEYFORMAT="com.apple.streamingkeydelivery"\n'
                  '#EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360\nv360.m3u8\n'),
    "/v360.m3u8": ("application/vnd.apple.mpegurl",
                   '#EXTM3U\n#EXT-X-TARGETDURATION:4\n#EXT-X-KEY:METHOD=SAMPLE-AES,URI="skd://key",KEYFORMAT="com.apple.streamingkeydelivery"\n'
                   '#EXTINF:4,\ns0.ts\n#EXT-X-ENDLIST\n'),
    "/plain.m3u8": ("application/vnd.apple.mpegurl",
                    '#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360\np360.m3u8\n'
                    '#EXT-X-STREAM-INF:BANDWIDTH=1600000,RESOLUTION=1280x720\np720.m3u8\n'),
    "/p360.m3u8": ("application/vnd.apple.mpegurl",
                   '#EXTM3U\n#EXT-X-TARGETDURATION:4\n#EXT-X-KEY:METHOD=AES-128,URI="key.bin"\n#EXTINF:4,\ns0.ts\n#EXT-X-ENDLIST\n'),
    "/p720.m3u8": ("application/vnd.apple.mpegurl",
                   '#EXTM3U\n#EXT-X-TARGETDURATION:4\n#EXT-X-KEY:METHOD=AES-128,URI="key.bin"\n#EXTINF:4,\ns0.ts\n#EXT-X-ENDLIST\n'),
}


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        path = self.path.split("?")[0]
        if path not in FILES:
            self.send_response(404)
            self.end_headers()
            return
        kind, body = FILES[path]
        data = body.encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", kind)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    do_HEAD = do_GET

    def log_message(self, *args):
        pass


def media(core):
    return core.snapshot("addMedia") or {}


def inspect(core, url):
    core.action("ShowAddDialog", show=False)
    core.pump(0.3)
    core.action("SetUrl", text=url)
    core.action("ShowAddDialog", show=True)
    core.pump(0.5)
    core.action("SetEngine", engine=ENGINE["MEDIA_STREAM"])
    core.pump(0.5)
    if media(core).get("state", 0) == STATE["NOT_INSPECTED"]:
        core.action("InspectMedia")
    core.wait_for("an answer for " + url, lambda: media(core).get("state", 0) in (STATE["QUALITIES"], STATE["ERROR"]), 60)
    core.pump(0.3)
    return media(core)


def run():
    server = ThreadingHTTPServer(("127.0.0.1", PORT), Handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    core = Core("drm", before_start=install_fake_ytdlp)
    try:
        core.pump(1.0)
        drm_text = core.strings()["sysDrmProtected"]

        print("a DASH manifest with ContentProtection")
        m = inspect(core, BASE + "/drm.mpd")
        core.check("it is reported as protected", m.get("state") == STATE["ERROR"] and m.get("error") == drm_text, m.get("error"))

        print("an HLS playlist with SAMPLE-AES (FairPlay)")
        m = inspect(core, BASE + "/drm.m3u8")
        core.check("it is reported as protected, no qualities", m.get("state") == STATE["ERROR"] and m.get("error") == drm_text, m.get("error"))

        print("an HLS playlist with plain AES-128")
        m = inspect(core, BASE + "/plain.m3u8")
        rows = core.rows("addQualities")
        core.check("it is not DRM: its two qualities are listed", m.get("state") == STATE["QUALITIES"] and len(rows) == 2, (m.get("error"), rows))

        core.action("ShowAddDialog", show=False)
        core.pump(0.3)
    except CheckFailed as failure:
        core.check(str(failure), False)
        print(core.log_text()[-2000:])
    finally:
        server.shutdown()
    core.finish()


if __name__ == "__main__":
    run()
