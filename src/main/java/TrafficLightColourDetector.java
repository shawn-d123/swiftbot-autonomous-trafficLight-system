package org.example;

import swiftbot.*;
import java.awt.image.BufferedImage;

public class TrafficLightColourDetector {

    private SwiftBotAPI swiftBot;

    public TrafficLightColourDetector(SwiftBotAPI swiftBot) {
        this.swiftBot = swiftBot;
    }

    public TrafficLightColourHolder detectColour() {
        try {

            BufferedImage image = swiftBot.takeStill(ImageSize.SQUARE_480x480);

            int[] averageRgb = calculateAverageRgbValues(image);

            int r = averageRgb[0];
            int g = averageRgb[1];
            int b = averageRgb[2];

            System.out.println("RGB values: " + r + ", " + g + ", " + b);

            return classifyColour(r, g, b);

        } catch (Exception e) {

            System.out.println("Error detecting colour");
            return TrafficLightColourHolder.UNKNOWN;

        }
    }

    private int[] calculateAverageRgbValues(BufferedImage image) {

        long totalRed = 0;
        long totalGreen = 0;
        long totalBlue = 0;

        int width = image.getWidth();
        int height = image.getHeight();

        for (int y = 0; y < height; y++) {

            for (int x = 0; x < width; x++) {

                int rgb = image.getRGB(x, y);

                int r = (rgb >> 16) & 0xFF; /// check what this does
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                totalRed += r;
                totalGreen += g;
                totalBlue += b;
            }
        }

        int pixelCount = width * height;

        int averageR = (int) (totalRed / pixelCount);
        int averageG = (int) (totalGreen / pixelCount);
        int averageB = (int) (totalBlue / pixelCount);

        return new int[]{averageR, averageG, averageB};
    }

    private TrafficLightColourHolder classifyColour(int r, int g, int b) {

        // RED range
        if (r >= 90 && r <= 255 && g >= 0 && g <= 120 && b >= 0 && b <= 120) {
            return TrafficLightColourHolder.RED;
        }
        // GREEN range
        if (g >= 90 && g <= 255 && r >= 0 && r <= 120 && b >= 0 && b <= 120) {
            return TrafficLightColourHolder.GREEN;
        }
        // BLUE range
        if (b >= 80 && b <= 255 && r >= 0 && r <= 130 && g >= 0 && g <= 130) {
            return TrafficLightColourHolder.BLUE;
        }

        return TrafficLightColourHolder.UNKNOWN;
    }
}
