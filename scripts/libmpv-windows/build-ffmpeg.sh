#!/bin/bash
set -e
. /e/tools/mpv-slim/env.sh
R=/e/tools/mpv-slim; mkdir -p $R/build/ffmpeg; cd $R/build/ffmpeg
DEMUX="mov,matroska,ogg,mp3,flac,wav,aac,mpegts,mpegps,ac3,eac3,aiff,ape,asf,avi,flv,wv,tta,caf,dts,w64,amr,pcm_s16le,pcm_s16be,pcm_f32le,pcm_u8,mpegvideo,h264,hevc,ivf,obu"
DEC="h264,hevc,vp9,vp8,mpeg4,mpeg2video,mpeg1video,libdav1d,aac,aac_latm,mp3,mp3float,mp2,mp2float,opus,vorbis,flac,alac,ac3,eac3,ape,wavpack,tta,dca,truehd,wmav1,wmav2,pcm_s16le,pcm_s16be,pcm_s24le,pcm_s24be,pcm_s32le,pcm_s32be,pcm_f32le,pcm_f32be,pcm_f64le,pcm_u8,pcm_alaw,pcm_mulaw,mjpeg,png,gif,bmp,webp"
PARS="aac,aac_latm,mpegaudio,opus,vorbis,flac,ac3,mjpeg,png,webp,h264,hevc,vp9,av1,vp8,mpeg4video,mpegvideo,dca,gif,bmp"
BSF="aac_adtstoasc,null,extract_extradata,h264_mp4toannexb,hevc_mp4toannexb,vp9_superframe,vp9_superframe_split,av1_frame_merge,av1_frame_split,av1_metadata,h264_metadata,hevc_metadata,mpeg4_unpack_bframes,noise,remove_extradata,dca_core"
FILT="aresample,anull,null,aformat,format,scale,atempo,volume,pan,hflip,vflip,transpose,rotate,setpts,asetpts,fps,aresample,anullsrc,loudnorm,dynaudnorm,equalizer,bass,treble,lowpass,highpass,afade,amix,astats,asetrate,aeval,drawtext_dummy"
FILT=${FILT/,drawtext_dummy/}
../../src/ffmpeg-7.1.1/configure --prefix=$R/out/ffmpeg --cc=clang --cxx=clang++ --ar=llvm-ar --ranlib=llvm-ranlib --nm=llvm-nm --strip=llvm-strip \
 --arch=x86_64 --target-os=mingw32 --pkg-config=pkg-config --pkg-config-flags=--static \
 --disable-everything --disable-autodetect --disable-doc --disable-programs --disable-shared --enable-static \
 --disable-avdevice --disable-network --disable-debug --disable-postproc --enable-version3 \
 --enable-libdav1d --enable-zlib --enable-w32threads \
 --extra-cflags="-O3 -ffunction-sections -fdata-sections" \
 --enable-protocol=file,pipe --enable-demuxer=$DEMUX --enable-decoder=$DEC --enable-parser=$PARS --enable-bsf=$BSF --enable-filter=$FILT
sed -i "s#/e/tools#E:/tools#g" Makefile ffbuild/config.mak config.h 2>/dev/null; mingw32-make -j16
mingw32-make install || true
