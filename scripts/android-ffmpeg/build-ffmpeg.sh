#!/bin/bash
set -e
ABI=$1
ROOT=${FFROOT:-/e/tools/ffbuild}
NDK=${ANDROID_NDK_MSYS:-/c/Users/miche/AppData/Local/Android/Sdk/ndk/28.2.13676358}
TC="$NDK/toolchains/llvm/prebuilt/windows-x86_64"
case $ABI in
  arm64-v8a) TRIPLE=aarch64-linux-android24; ARCH=aarch64;;
  armeabi-v7a) TRIPLE=armv7a-linux-androideabi24; ARCH=arm;;
  x86) TRIPLE=i686-linux-android24; ARCH=x86; EXTRA=--disable-asm;;
  x86_64) TRIPLE=x86_64-linux-android24; ARCH=x86_64;;
esac
export TMP=${FFTMP:-C:/Users/miche/AppData/Local/Temp} TEMP=${FFTMP:-C:/Users/miche/AppData/Local/Temp}
export PATH="$TC/bin:$NDK/prebuilt/windows-x86_64/bin:/c/msys64/usr/bin:/c/Windows/System32"
ROOT_WIN=${FFROOT_WIN:-E:/tools/ffbuild}
export FFPREFIX=$ROOT/out/$ABI
PREFIX=$ROOT/out/$ABI; W=$ROOT/work/$ABI
cd $W; rm -rf ffmpeg-7.1.1 ffb; tar xJf $ROOT/src/ffmpeg.tar.xz
mkdir ffb; cd ffb
DEMUX="mov,matroska,ogg,mp3,flac,wav,aac,srt,webvtt,ass,image2,concat,ffmetadata,mpegts,hls,dash,mjpeg,png_pipe,webp_pipe,jpeg_pipe,ac3,eac3,pcm_s16le,pcm_f32le,pcm_s16be,aiff,asf,ape,caf,w64,tta,wv,amr,dsf,gif"
MUX="mp4,ipod,matroska,matroska_audio,webm,ogg,opus,mp3,flac,wav,adts,srt,webvtt,ass,mov,image2,mjpeg,null,ffmetadata,segment,tee,concat,data,latm,aiff,ac3"
DEC="aac,aac_latm,mp3,mp3float,opus,libopus,vorbis,flac,alac,ac3,eac3,wmav1,wmav2,ape,wavpack,tta,amrnb,amrwb,mp2,mp2float,dca,truehd,pcm_s16le,pcm_s16be,pcm_s24le,pcm_s32le,pcm_f32le,pcm_f64le,pcm_u8,pcm_alaw,pcm_mulaw,mjpeg,png,webp,gif,bmp,mov_text,srt,subrip,webvtt,ass,ssa,text,movtext,dvdsub,dvbsub"
ENC="aac,libmp3lame,libopus,vorbis,flac,pcm_s16le,pcm_s16be,pcm_s24le,pcm_s32le,pcm_f32le,pcm_u8,mjpeg,png,movtext,srt,subrip,webvtt,ass,ssa,text"
PARS="aac,aac_latm,mpegaudio,opus,vorbis,flac,ac3,mjpeg,png,webp,h264,hevc,vp9,av1,vp8,mpeg4video,cook,dca"
BSF="aac_adtstoasc,null,extract_extradata,h264_mp4toannexb,hevc_mp4toannexb,vp9_superframe,opus_metadata,mov2textsub,text2movsub,noise,setts,pcm_rechunk,remove_extradata,av1_frame_merge,av1_frame_split"
FILT="aresample,anull,null,atrim,trim,asetpts,setpts,aformat,format,concat,amerge,amix,volume,loudnorm,atempo,apad,afade,areverse,areverse,scale,anullsrc,anullsink,nullsink,aselect,select,asplit,split,showinfo,ametadata,pan,channelmap,channelsplit,join,aeval,highpass,lowpass,dynaudnorm,silencedetect,silenceremove,astats,sidechaincompress"
PROT="file,pipe,fd,cache,crypto,data,concat,subfile,hls,http,https,tcp,tls"
$ROOT_WIN/work/$ABI/ffmpeg-7.1.1/configure --prefix=$ROOT/ffout/$ABI --target-os=android --arch=$ARCH \
  --enable-cross-compile --host-cc=/c/msys64/clang64/bin/clang.exe --cc="$ROOT/bin/cc-$ABI" --ar=llvm-ar.exe --ranlib=llvm-ranlib.exe --strip=llvm-strip.exe --nm=llvm-nm.exe \
  --disable-everything --disable-doc --disable-shared --enable-static --disable-autodetect --enable-zlib \
  --disable-avdevice --disable-network --disable-debug --enable-small --enable-pic $EXTRA \
  --enable-ffmpeg --enable-ffprobe --disable-ffplay \
  --enable-libmp3lame --enable-libopus \
  --extra-cflags="-I$PREFIX/include -Os -ffunction-sections -fdata-sections" --extra-ldexeflags="-pie -Wl,--export-dynamic-symbol=main" --extra-ldflags="-L$PREFIX/lib -Wl,--gc-sections -Wl,-z,max-page-size=16384" \
  --pkg-config=$ROOT/bin/pkg-config \
  --enable-protocol=file,pipe,fd,cache,data,concat,subfile \
  --enable-demuxer=$DEMUX --enable-muxer=$MUX --enable-decoder=$DEC --enable-encoder=$ENC \
  --enable-parser=$PARS --enable-bsf=$BSF --enable-filter=$FILT > $W/configure.log 2>&1 || { tail -20 $W/configure.log; exit 1; }
tail -8 $W/configure.log
sed -i "s#$ROOT#$ROOT_WIN#g" Makefile ffbuild/config.mak
make V=1 -j8 > $W/make.log 2>&1 || { tail -30 $W/make.log; exit 1; }
ls -l ffmpeg* ffprobe*
