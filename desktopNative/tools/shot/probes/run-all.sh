#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"
S=../build/Release/shot.exe
APP=../../../ui/app.slint
mkdir -p out

$S 00-basic.slint Basic --out out/00-basic.png --quiet

$S $APP AppWindow --json ../examples/app-state.json --width 1200 --height 760 --dark --out out/app-dark.png --quiet
$S $APP AppWindow --json ../examples/app-state.json --width 1200 --height 760 --light --out out/app-light.png --quiet
$S $APP AppWindow --json ../examples/app-state.json --width 1200 --height 760 --dark --scale 1.25 --out out/app-dark-125.png --quiet
$S $APP AppWindow --json ../examples/app-state.json --width 1200 --height 760 --dark --scale 1.5 --out out/app-dark-150.png --quiet
$S $APP AppWindow --json ../examples/app-state.json --width 1200 --height 760 --dark --set show-add=true --out out/app-add-dialog-dark.png --quiet
$S $APP AppWindow --json ../examples/app-state.json --width 1200 --height 760 --light --set show-add=true --out out/app-add-dialog-light.png --quiet
$S $APP AppWindow --json ../examples/app-state.json --width 1200 --height 760 --dark --scale 1 --crop 232,150,560,150 --zoom 3 --out out/app-zoom-100.png --quiet
$S $APP AppWindow --json ../examples/app-state.json --width 1200 --height 760 --dark --scale 1.25 --crop 232,150,560,150 --zoom 2 --out out/app-zoom-125.png --quiet
$S $APP AppWindow --json ../examples/app-state.json --width 1200 --height 760 --dark --scale 1.5 --crop 232,150,560,150 --zoom 2 --out out/app-zoom-150.png --quiet

$S 01-arabic-text.slint ArabicText --out out/01-arabic-text.png --quiet
$S 01-arabic-text.slint ArabicText --out out/01-arabic-zoom-a.png --crop 0,0,400,160 --zoom 3 --quiet
$S 01-arabic-text.slint ArabicText --out out/01-arabic-zoom-b.png --crop 0,460,400,190 --zoom 3 --quiet
$S 01-arabic-text.slint ArabicText --out out/01-arabic-zoom-c.png --crop 0,650,520,100 --zoom 3 --quiet
$S 01b-bidi-details.slint BidiDetails --out out/01b-bidi-details.png --click 200,720 --type "ملف abc 12 جديد" --quiet
$S 01b-bidi-details.slint BidiDetails --out out/01b-zoom-a.png --crop 0,50,330,110 --zoom 3 --quiet
$S 01b-bidi-details.slint BidiDetails --out out/01b-zoom-b.png --click 200,720 --type "ملف abc 12 جديد" --crop 0,690,460,110 --zoom 3 --quiet
$S 01b-bidi-details.slint BidiDetails --out out/01b-zoom-c.png --click 200,720 --type "ملف abc 12 جديد" --key Left --key Left --key Left --crop 0,690,460,110 --zoom 3 --quiet
$S 01c-rtl-elide-workaround.slint ElideWorkaround --out out/01c-elide-ltr.png --quiet
$S 01c-rtl-elide-workaround.slint ElideWorkaround --set Dir.rtl=true --out out/01c-elide-rtl.png --quiet

$S 02-rtl-layout.slint RtlLayout --out out/02-rtl-layout-ltr.png --quiet
$S 02-rtl-layout.slint RtlLayout --set Dir.rtl=true --out out/02-rtl-layout-rtl.png --quiet
$S 02b-hbox-features.slint HBoxFeatures --out out/02b-hbox-ltr.png --quiet
$S 02b-hbox-features.slint HBoxFeatures --set Dir.rtl=true --out out/02b-hbox-rtl.png --quiet

$S 03-font-fallback.slint FontFallback --out out/03-font-fallback.png --quiet
$S 03b-fallback-details.slint FallbackDetails --out out/03b-fallback-details.png --quiet
$S 03c-font-per-language.slint FontPerLanguage --out out/03c-font-segoe.png --quiet
$S 03c-font-per-language.slint FontPerLanguage --set "Fonts.ui=Microsoft YaHei UI" --out out/03c-font-yahei.png --quiet

$S 04-emoji.slint EmojiProbe --json 04-emoji.json --emoji-callback Emoji.icon --out out/04-emoji-100.png --quiet
$S 04-emoji.slint EmojiProbe --json 04-emoji.json --emoji-callback Emoji.icon --scale 1.5 --out out/04-emoji-150.png --quiet
$S 04-emoji.slint EmojiProbe --json 04-emoji.json --emoji-callback Emoji.icon --out out/04-emoji-zoom-dark.png --crop 0,0,420,110 --zoom 3 --quiet
$S 04-emoji.slint EmojiProbe --json 04-emoji.json --emoji-callback Emoji.icon --out out/04-emoji-zoom-light.png --crop 0,300,420,110 --zoom 3 --quiet

$S 05-renderer-limits.slint RendererLimits --out out/05-renderer-limits.png --quiet 2>/dev/null
$S 05-renderer-limits.slint RendererLimits --out out/05-zoom-clip.png --crop 10,10,600,122 --zoom 2 --quiet 2>/dev/null
$S 05-renderer-limits.slint RendererLimits --out out/05-zoom-shadow.png --crop 208,138,990,122 --zoom 2 --quiet 2>/dev/null
$S 05-renderer-limits.slint RendererLimits --out out/05-zoom-paths.png --crop 604,266,590,250 --zoom 2 --quiet 2>/dev/null
$S 05-renderer-limits.slint RendererLimits --out out/05-zoom-images.png --crop 10,394,850,122 --zoom 2 --quiet 2>/dev/null
$S 05-renderer-limits.slint RendererLimits --out out/05-zoom-text-dark.png --crop 14,546,600,180 --zoom 2 --quiet 2>/dev/null
$S 05-renderer-limits.slint RendererLimits --out out/05-zoom-text-light.png --crop 614,546,600,180 --zoom 2 --quiet 2>/dev/null
$S 05b-renderer-details.slint RendererDetails --out out/05b-renderer-details.png --quiet 2>/dev/null
$S 05b-renderer-details.slint RendererDetails --dark --out out/05b-renderer-details-darkscheme.png --quiet 2>/dev/null
$S 05b-renderer-details.slint RendererDetails --dark --click 400,198 --wait 300 --crop 320,130,420,180 --zoom 2 --out out/05b-combobox-popup.png --quiet 2>/dev/null

for s in 1 1.25 1.5 1.75 2; do
  $S 06-scale.slint ScaleProbe --scale $s --out out/06-scale-$s.png --quiet
done
echo "all probes rendered"
