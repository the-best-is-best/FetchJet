#!/bin/bash
# Static FFmpeg 7.1.1 for the macOS arm64 libmpv: only the formats the player needs (same lists as scripts/libmpv-windows).
set -euo pipefail
. "$(dirname "$0")/env.sh"
fj_unpack ffmpeg-7.1.1
mkdir -p "$FJ_MPV_BUILD/ffmpeg"
cd "$FJ_MPV_BUILD/ffmpeg"
DEMUX="mov,matroska,ogg,mp3,flac,wav,aac,mpegts,mpegps,ac3,eac3,aiff,ape,asf,avi,flv,wv,tta,caf,dts,w64,amr,pcm_s16le,pcm_s16be,pcm_f32le,pcm_u8,mpegvideo,h264,hevc,ivf,obu"
DEC="h264,hevc,vp9,vp8,mpeg4,mpeg2video,mpeg1video,libdav1d,aac,aac_latm,mp3,mp3float,mp2,mp2float,opus,vorbis,flac,alac,ac3,eac3,ape,wavpack,tta,dca,truehd,wmav1,wmav2,pcm_s16le,pcm_s16be,pcm_s24le,pcm_s24be,pcm_s32le,pcm_s32be,pcm_f32le,pcm_f32be,pcm_f64le,pcm_u8,pcm_alaw,pcm_mulaw,mjpeg,png,gif,bmp,webp"
PARS="aac,aac_latm,mpegaudio,opus,vorbis,flac,ac3,mjpeg,png,webp,h264,hevc,vp9,av1,vp8,mpeg4video,mpegvideo,dca,gif,bmp"
BSF="aac_adtstoasc,null,extract_extradata,h264_mp4toannexb,hevc_mp4toannexb,vp9_superframe,vp9_superframe_split,av1_frame_merge,av1_frame_split,av1_metadata,h264_metadata,hevc_metadata,mpeg4_unpack_bframes,noise,remove_extradata,dca_core"
FILT="aresample,anull,null,aformat,format,scale,atempo,volume,pan,hflip,vflip,transpose,rotate,setpts,asetpts,fps,anullsrc,loudnorm,dynaudnorm,equalizer,bass,treble,lowpass,highpass,afade,amix,astats,asetrate,aeval"
"$FJ_MPV_SRC/ffmpeg-7.1.1/configure" --prefix="$FJ_MPV_PREFIX" --cc=clang --cxx=clang++ \
    --arch=aarch64 --target-os=darwin --enable-cross-compile --pkg-config=pkg-config --pkg-config-flags=--static \
    --disable-everything --disable-autodetect --disable-doc --disable-programs --disable-shared --enable-static \
    --disable-avdevice --disable-network --disable-debug --disable-postproc --enable-version3 \
    --enable-libdav1d --enable-zlib --enable-pthreads \
    --extra-cflags="$CFLAGS" --extra-ldflags="$LDFLAGS" \
    --enable-protocol=file,pipe --enable-demuxer=$DEMUX --enable-decoder=$DEC --enable-parser=$PARS --enable-bsf=$BSF --enable-filter=$FILT
make -j"$(sysctl -n hw.ncpu)"
make install
