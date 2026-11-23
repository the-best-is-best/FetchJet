package com.michelle.raouf.fetch_jet.feature.media_extractor

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MediaDrmTest {
    @Test
    fun hlsWithSampleAesOrADrmKeyFormatIsProtected() {
        assertTrue(MediaDrm.hlsIsProtected("#EXTM3U\n#EXT-X-KEY:METHOD=SAMPLE-AES,URI=\"skd://x\",KEYFORMAT=\"com.apple.streamingkeydelivery\"\n"))
        assertTrue(MediaDrm.hlsIsProtected("#EXTM3U\n#EXT-X-SESSION-KEY:METHOD=SAMPLE-AES-CTR,URI=\"data:x\",KEYFORMAT=\"urn:uuid:edef8ba9-79d6-4ace-a3c8-27dcd51d21ed\"\n"))
        assertTrue(MediaDrm.hlsIsProtected("#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"k\",KEYFORMAT=\"com.microsoft.playready\"\n"))
    }

    @Test
    fun plainOrAes128HlsIsNotProtected() {
        assertFalse(MediaDrm.hlsIsProtected("#EXTM3U\n#EXTINF:4,\nseg0.ts\n"))
        assertFalse(MediaDrm.hlsIsProtected("#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"https://x/key\",IV=0x01\n#EXTINF:4,\nseg0.ts\n"))
        assertFalse(MediaDrm.hlsIsProtected("#EXTM3U\n#EXT-X-KEY:METHOD=NONE\n#EXTINF:4,\nseg0.ts\n"))
    }

    @Test
    fun dashWithContentProtectionIsProtected() {
        assertTrue(MediaDrm.dashIsProtected("<MPD><Period><AdaptationSet><ContentProtection schemeIdUri=\"urn:mpeg:dash:mp4protection:2011\" value=\"cenc\"/></AdaptationSet></Period></MPD>"))
        assertTrue(MediaDrm.dashIsProtected("<mpd:MPD><mpd:ContentProtection/></mpd:MPD>"))
        assertFalse(MediaDrm.dashIsProtected("<MPD><Period><AdaptationSet><Representation id=\"1\"/></AdaptationSet></Period></MPD>"))
    }

    @Test
    fun ytDlpDrmErrorIsRecognised() {
        assertTrue(MediaDrm.errorSaysDrm("ERROR: [generic] x: This video is DRM protected"))
        assertFalse(MediaDrm.errorSaysDrm("ERROR: Unsupported URL: https://example.com/drmshop"))
        assertFalse(MediaDrm.errorSaysDrm(null))
    }
}
