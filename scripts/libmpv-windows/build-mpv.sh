. /e/tools/mpv-slim/env.sh
cd /e/tools/mpv-slim/src/mpv-0.42.0
meson setup ../../build/mpv --buildtype=release -Db_ndebug=true -Dstrip=true -Dauto_features=disabled -Dgpl=false -Dlibmpv=true -Dcplayer=false -Dtests=false -Dwasapi=enabled -Dgl=disabled -Dwin32-threads=enabled -Dc_args="-Os -ffunction-sections -fdata-sections" -Dc_link_args="-Wl,--gc-sections" -Dprefer_static=true --default-library=shared --wrap-mode=nodownload --prefix=/e/tools/mpv-slim/out/mpv
ninja -C ../../build/mpv
llvm-strip --strip-unneeded -o /e/tools/mpv-slim/dist/libmpv-2.dll ../../build/mpv/libmpv-2.dll
