# lists shared by build-ffmpeg.sh (Windows) and build-linux.sh (CI)
DEMUX="mov,matroska,ogg,mp3,flac,wav,aac,srt,webvtt,ass,image2,concat,ffmetadata,mpegts,hls,dash,mjpeg,png_pipe,webp_pipe,jpeg_pipe,ac3,eac3,pcm_s16le,pcm_f32le,pcm_s16be,aiff,asf,ape,caf,w64,tta,wv,amr,dsf,gif"
MUX="mp4,ipod,matroska,matroska_audio,webm,ogg,opus,mp3,flac,wav,adts,srt,webvtt,ass,mov,image2,mjpeg,null,ffmetadata,segment,tee,concat,data,latm,aiff,ac3"
DEC="h264,hevc,vp9,vp8,mpeg4,mpeg2video,aac,aac_latm,mp3,mp3float,opus,libopus,vorbis,flac,alac,ac3,eac3,wmav1,wmav2,ape,wavpack,tta,amrnb,amrwb,mp2,mp2float,dca,truehd,pcm_s16le,pcm_s16be,pcm_s24le,pcm_s32le,pcm_f32le,pcm_f64le,pcm_u8,pcm_alaw,pcm_mulaw,mjpeg,png,webp,gif,bmp,mov_text,srt,subrip,webvtt,ass,ssa,text,movtext,dvdsub,dvbsub"
ENC="aac,libmp3lame,libopus,vorbis,flac,pcm_s16le,pcm_s16be,pcm_s24le,pcm_s32le,pcm_f32le,pcm_u8,mjpeg,png,movtext,srt,subrip,webvtt,ass,ssa,text"
PARS="aac,aac_latm,mpegaudio,opus,vorbis,flac,ac3,mjpeg,png,webp,h264,hevc,vp9,av1,vp8,mpeg4video,cook,dca"
BSF="aac_adtstoasc,null,extract_extradata,h264_mp4toannexb,hevc_mp4toannexb,vp9_superframe,opus_metadata,mov2textsub,text2movsub,noise,setts,pcm_rechunk,remove_extradata,av1_frame_merge,av1_frame_split"
FILT="aresample,anull,null,atrim,trim,asetpts,setpts,aformat,format,concat,amerge,amix,volume,loudnorm,atempo,apad,afade,areverse,areverse,scale,anullsrc,anullsink,nullsink,aselect,select,asplit,split,showinfo,ametadata,pan,channelmap,channelsplit,join,aeval,highpass,lowpass,dynaudnorm,silencedetect,silenceremove,astats,sidechaincompress"
