package com.michelle.raouf.fetch_jet.core.setup

/** macOS (JVM app): LaunchServices through JavaScript for Automation (osascript). */
internal object MacDefaultHandler {
    private const val SCRIPT = """
ObjC.import('CoreServices');
ObjC.import('Foundation');
function str(ref) { return ref ? ObjC.unwrap(ObjC.castRefToObject(ref)) : ''; }
function run(argv) {
  var uti = $.UTTypeCreatePreferredIdentifierForTag($('public.filename-extension'), $('torrent'), null);
  if (argv[0] === 'set') {
    var a = $.LSSetDefaultHandlerForURLScheme($('magnet'), $(argv[1]));
    var b = $.LSSetDefaultRoleHandlerForContentType(uti, 0xFFFFFFFF, $(argv[1]));
    return a + ' ' + b;
  }
  return str($.LSCopyDefaultHandlerForURLScheme($('magnet'))) + '\n' + str($.LSCopyDefaultRoleHandlerForContentType(uti, 0xFFFFFFFF));
}
"""

    fun isDefault(): Boolean? {
        val result = jvmRun("/usr/bin/osascript", "-l", "JavaScript", "-e", SCRIPT, "get") ?: return null
        if (result.code != 0) return null
        val lines = result.out.trim().lines()
        return lines.size == 2 && lines.all { it.trim().equals(FETCHJET_BUNDLE_ID, ignoreCase = true) }
    }

    fun makeDefault(): Boolean {
        val result = jvmRun("/usr/bin/osascript", "-l", "JavaScript", "-e", SCRIPT, "set", FETCHJET_BUNDLE_ID) ?: return false
        return result.code == 0 && result.out.trim() == "0 0"
    }
}
