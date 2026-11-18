#!/bin/bash
set -e
ABI=$1
ROOT=${FFROOT:-/e/tools/ffbuild}
NDK=${ANDROID_NDK_MSYS:-/c/Users/miche/AppData/Local/Android/Sdk/ndk/28.2.13676358}
TC="$NDK/toolchains/llvm/prebuilt/windows-x86_64"
case $ABI in
  arm64-v8a) TRIPLE=aarch64-linux-android24; HOST=aarch64-linux-gnu;;
  armeabi-v7a) TRIPLE=armv7a-linux-androideabi24; HOST=arm-linux-gnueabi;;
  x86) TRIPLE=i686-linux-android24; HOST=i686-linux-gnu;;
  x86_64) TRIPLE=x86_64-linux-android24; HOST=x86_64-linux-gnu;;
esac
export TMP=${FFTMP:-C:/Users/miche/AppData/Local/Temp} TEMP=${FFTMP:-C:/Users/miche/AppData/Local/Temp}
export PATH="$TC/bin:$NDK/prebuilt/windows-x86_64/bin:/c/msys64/usr/bin:/c/Windows/System32"
W=$ROOT/work/$ABI; PREFIX=$ROOT/out/$ABI
mkdir -p $W $PREFIX $ROOT/bin
cat > $ROOT/bin/cc-$ABI <<EOT
#!/bin/sh
exec clang.exe --target=$TRIPLE "\$@"
EOT
chmod +x $ROOT/bin/cc-$ABI
export CC="$ROOT/bin/cc-$ABI" AR=llvm-ar.exe RANLIB=llvm-ranlib.exe STRIP=llvm-strip.exe
cd $W
rm -rf lame-3.100 opus-1.5.2
tar xzf $ROOT/src/lame.tar.gz; tar xzf $ROOT/src/opus.tar.gz
cd lame-3.100
./configure --host=$HOST --prefix=$PREFIX --disable-shared --enable-static --disable-frontend --disable-decoder --disable-gtktest > $W/lame.log 2>&1
make -j8 >> $W/lame.log 2>&1 && make install >> $W/lame.log 2>&1
cd ../opus-1.5.2
./configure --host=$HOST --prefix=$PREFIX --disable-shared --enable-static --disable-doc --disable-extra-programs > $W/opus.log 2>&1
make -j8 >> $W/opus.log 2>&1 && make install >> $W/opus.log 2>&1
ls $PREFIX/lib
