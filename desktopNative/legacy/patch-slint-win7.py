import shutil
import sys
from pathlib import Path

slint = Path(sys.argv[1])
registry = Path(sys.argv[2])
patched = Path(sys.argv[3])


def edit(path, old, new, marker):
    text = path.read_text(encoding="utf-8")
    if marker in text:
        return
    if text.count(old) != 1:
        sys.exit(f"patch-slint-win7: anchor not found once in {path}")
    path.write_text(text.replace(old, new), encoding="utf-8", newline="\n")


def crate(name):
    source = registry / name
    if not source.is_dir():
        sys.exit(f"patch-slint-win7: {source} missing (run cargo fetch first)")
    target = patched / name
    if not target.is_dir():
        shutil.copytree(source, target)
    return target


edit(
    slint / "internal/backends/winit/lib.rs",
    """            let ok = unsafe {
                SystemParametersInfoForDpi(
                    SPI_GETNONCLIENTMETRICS.0,
                    metrics.cbSize,
                    Some(&mut metrics as *mut _ as *mut core::ffi::c_void),
                    0,
                    96,
                )
            }
            .is_ok();
            // `lfMessageFont.lfHeight` is in pixels at 96 DPI = Slint logical pixels;
            // negative means em height, positive means cell height — magnitude is fine here.
            let height = metrics.lfMessageFont.lfHeight.unsigned_abs();""",
    """            #[cfg(not(target_vendor = "win7"))]
            let ok = unsafe {
                SystemParametersInfoForDpi(
                    SPI_GETNONCLIENTMETRICS.0,
                    metrics.cbSize,
                    Some(&mut metrics as *mut _ as *mut core::ffi::c_void),
                    0,
                    96,
                )
            }
            .is_ok();
            #[cfg(target_vendor = "win7")]
            let ok = unsafe {
                windows::Win32::UI::WindowsAndMessaging::SystemParametersInfoW(
                    SPI_GETNONCLIENTMETRICS,
                    metrics.cbSize,
                    Some(&mut metrics as *mut _ as *mut core::ffi::c_void),
                    Default::default(),
                )
            }
            .is_ok();
            #[cfg(target_vendor = "win7")]
            {
                let screen = unsafe { windows::Win32::Graphics::Gdi::GetDC(None) };
                let dpi = unsafe { windows::Win32::Graphics::Gdi::GetDeviceCaps(Some(screen), windows::Win32::Graphics::Gdi::LOGPIXELSY) };
                unsafe { windows::Win32::Graphics::Gdi::ReleaseDC(None, screen) };
                if dpi > 0 {
                    metrics.lfMessageFont.lfHeight = metrics.lfMessageFont.lfHeight * 96 / dpi;
                }
            }
            // `lfMessageFont.lfHeight` is in pixels at 96 DPI = Slint logical pixels;
            // negative means em height, positive means cell height — magnitude is fine here.
            let height = metrics.lfMessageFont.lfHeight.unsigned_abs();""",
    'target_vendor = "win7"',
)

core = crate("windows-core-0.62.2")
edit(
    core / "src/imp/marshaler.rs",
    'windows_link::link!("combase.dll"',
    'windows_link::link!("ole32.dll"',
    'link!("ole32.dll"',
)

result = crate("windows-result-0.4.1")
edit(
    result / "src/error.rs",
    """            let message: Vec<_> = message.encode_utf16().collect();
            unsafe {
                RoOriginateErrorW(code.0, message.len() as u32, message.as_ptr());
            }""",
    """            let message: Vec<_> = message.encode_utf16().collect();
            #[cfg(not(target_vendor = "win7"))]
            unsafe {
                RoOriginateErrorW(code.0, message.len() as u32, message.as_ptr());
            }
            #[cfg(target_vendor = "win7")]
            let _ = (code, message);""",
    'target_vendor = "win7"',
)

config = slint / ".cargo" / "config.toml"
text = config.read_text(encoding="utf-8")
if "[patch.crates-io]" not in text:
    text += (
        "\n[patch.crates-io]\n"
        f"windows-core = {{ path = '{core.as_posix()}' }}\n"
        f"windows-result = {{ path = '{result.as_posix()}' }}\n"
    )
    config.write_text(text, encoding="utf-8", newline="\n")
print("patch-slint-win7: ok")
