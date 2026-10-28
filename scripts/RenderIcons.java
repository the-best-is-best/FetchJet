import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import javax.imageio.ImageIO;

public class RenderIcons {

    static class IconTarget {
        int w, h;
        String dirPath;
        String fileName;
        IconTarget(int w, int h, String dirPath, String fileName) {
            this.w = w;
            this.h = h;
            this.dirPath = dirPath;
            this.fileName = fileName;
        }
    }

    public static void main(String[] args) {
        String iosDir = "iosApp/iosApp/Assets.xcassets/AppIcon.appiconset";

        IconTarget[] targets = {
            // Android Mipmaps
            new IconTarget(48, 48, "androidApp/src/main/res/mipmap-mdpi", "ic_launcher.png"),
            new IconTarget(72, 72, "androidApp/src/main/res/mipmap-hdpi", "ic_launcher.png"),
            new IconTarget(96, 96, "androidApp/src/main/res/mipmap-xhdpi", "ic_launcher.png"),
            new IconTarget(144, 144, "androidApp/src/main/res/mipmap-xxhdpi", "ic_launcher.png"),
            new IconTarget(192, 192, "androidApp/src/main/res/mipmap-xxxhdpi", "ic_launcher.png"),

            // Desktop Assets
            new IconTarget(512, 512, "desktopApp/src/main/resources", "launcher.png"),
            new IconTarget(512, 512, "desktopApp/src/main/resources", "launcher.ico"),

            // Extension Assets
            new IconTarget(16, 16, "extension", "icon16.png"),
            new IconTarget(48, 48, "extension", "icon48.png"),
            new IconTarget(128, 128, "extension", "icon128.png"),

            // iOS Complete AppIcon Set
            new IconTarget(20, 20, iosDir, "icon_20.png"),
            new IconTarget(40, 40, iosDir, "icon_20@2x.png"),
            new IconTarget(60, 60, iosDir, "icon_20@3x.png"),
            new IconTarget(29, 29, iosDir, "icon_29.png"),
            new IconTarget(58, 58, iosDir, "icon_29@2x.png"),
            new IconTarget(87, 87, iosDir, "icon_29@3x.png"),
            new IconTarget(40, 40, iosDir, "icon_40.png"),
            new IconTarget(80, 80, iosDir, "icon_40@2x.png"),
            new IconTarget(120, 120, iosDir, "icon_40@3x.png"),
            new IconTarget(120, 120, iosDir, "icon_60@2x.png"),
            new IconTarget(180, 180, iosDir, "icon_60@3x.png"),
            new IconTarget(76, 76, iosDir, "icon_76.png"),
            new IconTarget(152, 152, iosDir, "icon_76@2x.png"),
            new IconTarget(167, 167, iosDir, "icon_83.5@2x.png"),
            new IconTarget(1024, 1024, iosDir, "AppIcon.png"),
            new IconTarget(1024, 1024, iosDir, "AppIcon-Dark.png"),
            new IconTarget(1024, 1024, iosDir, "AppIcon-Tinted.png")
        };

        for (IconTarget t : targets) {
            File dir = new File(t.dirPath);
            if (!dir.exists()) dir.mkdirs();

            boolean isRound = t.dirPath.contains("mipmap") && t.fileName.contains("round");
            BufferedImage img = renderJetLogo(t.w, t.h, isRound);
            try {
                ImageIO.write(img, "PNG", new File(dir, t.fileName));
            } catch (Exception e) { e.printStackTrace(); }

            // Also render round android icon
            if (t.dirPath.contains("mipmap") && !t.fileName.contains("round")) {
                BufferedImage roundImg = renderJetLogo(t.w, t.h, true);
                try {
                    ImageIO.write(roundImg, "PNG", new File(dir, "ic_launcher_round.png"));
                } catch (Exception e) { e.printStackTrace(); }
            }
        }

        // Generate proper multi-resolution macOS .icns file
        try {
            File desktopDir = new File("desktopApp/src/main/resources");
            if (!desktopDir.exists()) desktopDir.mkdirs();
            BufferedImage masterIcnsImg = renderJetLogo(1024, 1024, false);
            writeIcns(masterIcnsImg, new File(desktopDir, "launcher.icns"));
            System.out.println("  [macOS] Generated valid multi-resolution launcher.icns");
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("ALL iOS, Android, and Desktop App Icons rendered successfully with the official Jet Logo!");
    }

    private static BufferedImage renderJetLogo(int width, int height, boolean isRound) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = img.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        // Background (Squircle or Ellipse)
        if (isRound) {
            g2d.setColor(new Color(0x08, 0x0D, 0x1A));
            g2d.fill(new Ellipse2D.Float(0, 0, width, height));
        } else {
            float corner = width * 0.234f;
            g2d.setPaint(new GradientPaint(0, 0, new Color(0x08, 0x0D, 0x1A), width, height, new Color(0x06, 0x0A, 0x14)));
            g2d.fill(new RoundRectangle2D.Float(0, 0, width, height, corner, corner));
        }

        double scale = width / 1024.0;
        g2d.scale(scale, scale);

        // Outer Speed Orbital Ring
        g2d.setStroke(new BasicStroke(2.5f));
        g2d.setColor(new Color(0x2500E5FF, true));
        g2d.draw(new Ellipse2D.Float(120, 120, 784, 784));

        // Left Contrail
        GradientPaint cp1 = new GradientPaint(450, 120, new Color(0x0000E5FF, true), 450, 370, new Color(0xFF00E5FF, true));
        g2d.setPaint(cp1);
        Path2D leftContrail = new Path2D.Float();
        leftContrail.moveTo(426, 120);
        leftContrail.lineTo(466, 120);
        leftContrail.lineTo(476, 370);
        leftContrail.lineTo(436, 370);
        leftContrail.closePath();
        g2d.fill(leftContrail);

        // Right Contrail
        GradientPaint cp2 = new GradientPaint(574, 120, new Color(0x00818CF8, true), 574, 370, new Color(0xFF818CF8, true));
        g2d.setPaint(cp2);
        Path2D rightContrail = new Path2D.Float();
        rightContrail.moveTo(558, 120);
        rightContrail.lineTo(598, 120);
        rightContrail.lineTo(588, 370);
        rightContrail.lineTo(548, 370);
        rightContrail.closePath();
        g2d.fill(rightContrail);

        // Left Delta Wing
        GradientPaint wp1 = new GradientPaint(210, 340, new Color(0xFF00F2FE), 512, 740, new Color(0xFF0C3D60));
        g2d.setPaint(wp1);
        Path2D leftWing = new Path2D.Float();
        leftWing.moveTo(512, 740);
        leftWing.lineTo(210, 430);
        leftWing.lineTo(380, 480);
        leftWing.lineTo(460, 340);
        leftWing.lineTo(512, 370);
        leftWing.closePath();
        g2d.fill(leftWing);

        // Right Delta Wing
        GradientPaint wp2 = new GradientPaint(814, 340, new Color(0xFF818CF8), 512, 740, new Color(0xFF2C2F63));
        g2d.setPaint(wp2);
        Path2D rightWing = new Path2D.Float();
        rightWing.moveTo(512, 740);
        rightWing.lineTo(814, 430);
        rightWing.lineTo(644, 480);
        rightWing.lineTo(564, 340);
        rightWing.lineTo(512, 370);
        rightWing.closePath();
        g2d.fill(rightWing);

        // Central Thrust Spine (Arrow)
        GradientPaint sp = new GradientPaint(512, 330, new Color(0xFFFFFFFF), 512, 740, new Color(0xFF0284C7));
        g2d.setPaint(sp);
        Path2D spine = new Path2D.Float();
        spine.moveTo(512, 330);
        spine.lineTo(465, 470);
        spine.lineTo(485, 470);
        spine.lineTo(512, 740);
        spine.lineTo(539, 470);
        spine.lineTo(559, 470);
        spine.closePath();
        g2d.fill(spine);

        // Cockpit Canopy
        GradientPaint cp = new GradientPaint(512, 380, new Color(0xFFFFFFFF), 512, 540, new Color(0xFF00E5FF));
        g2d.setPaint(cp);
        Path2D canopy = new Path2D.Float();
        canopy.moveTo(512, 380);
        canopy.lineTo(536, 460);
        canopy.lineTo(512, 540);
        canopy.lineTo(488, 460);
        canopy.closePath();
        g2d.fill(canopy);

        // Shockwave Arc 1
        GradientPaint sw1 = new GradientPaint(294, 770, new Color(0xFF00E5FF), 730, 895, new Color(0xFF818CF8));
        g2d.setPaint(sw1);
        Path2D shock1 = new Path2D.Float();
        shock1.moveTo(310, 770);
        shock1.curveTo(420, 860, 604, 860, 714, 770);
        shock1.lineTo(730, 795);
        shock1.curveTo(610, 895, 414, 895, 294, 795);
        shock1.closePath();
        g2d.fill(shock1);

        // Nose Tip Dot
        g2d.setColor(Color.WHITE);
        g2d.fill(new Ellipse2D.Float(502, 746, 20, 20));

        g2d.dispose();
        return img;
    }

    private static void writeIcns(BufferedImage masterImg, File outFile) throws Exception {
        int[] sizes = {16, 32, 64, 128, 256, 512, 1024};
        String[] types = {"icp4", "icp5", "icp6", "ic07", "ic08", "ic09", "ic10"};

        ByteArrayOutputStream dataStream = new ByteArrayOutputStream();

        for (int i = 0; i < sizes.length; i++) {
            int size = sizes[i];
            String type = types[i];

            BufferedImage resized = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = resized.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(masterImg, 0, 0, size, size, null);
            g.dispose();

            ByteArrayOutputStream pngStream = new ByteArrayOutputStream();
            ImageIO.write(resized, "PNG", pngStream);
            byte[] pngBytes = pngStream.toByteArray();

            dataStream.write(type.getBytes("US-ASCII"));
            int totalLen = 8 + pngBytes.length;
            dataStream.write(new byte[] {
                (byte)(totalLen >> 24),
                (byte)(totalLen >> 16),
                (byte)(totalLen >> 8),
                (byte)totalLen
            });
            dataStream.write(pngBytes);
        }

        byte[] iconData = dataStream.toByteArray();
        int fileLen = 8 + iconData.length;

        ByteArrayOutputStream finalStream = new ByteArrayOutputStream();
        finalStream.write("icns".getBytes("US-ASCII"));
        finalStream.write(new byte[] {
            (byte)(fileLen >> 24),
            (byte)(fileLen >> 16),
            (byte)(fileLen >> 8),
            (byte)fileLen
        });
        finalStream.write(iconData);

        try (FileOutputStream fos = new FileOutputStream(outFile)) {
            fos.write(finalStream.toByteArray());
        }
    }
}
