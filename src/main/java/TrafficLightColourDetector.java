/*
 * Captures a still image from the SwiftBot camera, computes the average RGB
 * values across all pixels, and classifies the result as RED, GREEN, BLUE,
 * or UNKNOWN based on configurable colour range thresholds.
 */

package org.example;

import swiftbot.*;
import java.awt.image.BufferedImage;

/**
 * Detects the colour of a traffic light using the SwiftBot's onboard camera.
 * A still image is captured, the average RGB values across all pixels are
 * computed, and the result is classified against predefined colour thresholds.
 */
public class TrafficLightColourDetector {

    private final SwiftBotAPI swiftBot;

    // Minimum value the dominant channel must reach to qualify as that colour
    private static final int RED_MIN   = 90;
    private static final int GREEN_MIN = 90;
    private static final int BLUE_MIN  = 80;

    // Max value either non-dominant channel may reach for a red or green match
    private static final int SECONDARY_RED_GREEN_MAX = 120;

    // Slightly higher for blue to account for ambient light affecting red and green
    private static final int BLUE_NON_DOMINANT_MAX = 130;

    /**
     * Creates a TrafficLightColourDetector using the given SwiftBot instance.
     * @param swiftBot the SwiftBot API instance used to capture camera images.
     */
    public TrafficLightColourDetector(SwiftBotAPI swiftBot) {
        this.swiftBot = swiftBot;
    }

    /**
     * Captures a still image, computes the average RGB values, and classifies
     * the result as a traffic light colour.
     * @return the detected TrafficLightColourHolder, or UNKNOWN if the
     * colour could not be classified or an error occurred.
     */
    public TrafficLightColourHolder detectColour() {
        try {
            // Taking a 480x480 image from the camera
            BufferedImage image = swiftBot.takeStill(ImageSize.SQUARE_480x480);

            // Computing the average RGB values across all pixels
            int[] averageRgb = calculateAverageRgbValues(image);

            int r = averageRgb[0];
            int g = averageRgb[1];
            int b = averageRgb[2];

            System.out.println("RGB values: " + r + ", " + g + ", " + b);

            // Classify the colour based on the threshold values
            return classifyColour(r, g, b);

        } catch (Exception e) {
            System.out.println("Error detecting colour");
            return TrafficLightColourHolder.UNKNOWN;
        }
    }

    /**
     * Iterates through every pixel in the image and returns the average R, G, and B
     * values as an array with three elements in the order [R, G, B].
     * @param image the image to process.
     * @return an int array containing the average [red, green, blue] values (0-255 each).
     */
    private int[] calculateAverageRgbValues(BufferedImage image) {

        long totalRed   = 0;
        long totalGreen = 0;
        long totalBlue  = 0;

        int width  = image.getWidth();
        int height = image.getHeight();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {

                /*
                 * getRGB() returns a packed 32-bit ARGB integer: 0xAARRGGBB.
                 * Bit-shifting and masking with 0xFF extracts each 8-bit channel:
                 *   red = bits 16-23
                 *   green = bits 8-15
                 *   blue = bits 0-7
                 */
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b =  rgb & 0xFF;

                totalRed   += r;
                totalGreen += g;
                totalBlue  += b;
            }
        }

        int pixelCount = width * height;

        return new int[]{ (int) (totalRed   / pixelCount), (int) (totalGreen / pixelCount), (int) (totalBlue  / pixelCount)};
    }

    /**
     * Classifies an average RGB reading against the defined colour thresholds.
     * A colour is matched when its dominant channel meets the minimum and both
     * other colourss stay below their respective non-dominant maximums.
     * @param r the average red channel value (0-255).
     * @param g the average green channel value (0-255).
     * @param b the average blue channel value (0-255).
     * @return the matching TrafficLightColourHolder, or UNKNOWN if no range matched.
     */
    private TrafficLightColourHolder classifyColour(int r, int g, int b) {

        if (r >= RED_MIN   && g <= SECONDARY_RED_GREEN_MAX && b <= SECONDARY_RED_GREEN_MAX) {
            return TrafficLightColourHolder.RED;
        }
        if (g >= GREEN_MIN && r <= SECONDARY_RED_GREEN_MAX && b <= SECONDARY_RED_GREEN_MAX) {
            return TrafficLightColourHolder.GREEN;
        }
        if (b >= BLUE_MIN  && r <= BLUE_NON_DOMINANT_MAX && g <= BLUE_NON_DOMINANT_MAX) {
            return TrafficLightColourHolder.BLUE;
        }

        return TrafficLightColourHolder.UNKNOWN;
    }
}