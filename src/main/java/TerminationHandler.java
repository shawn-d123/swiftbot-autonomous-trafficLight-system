/*
 * Handles program termination: manages the session timer, tracks per-colour
 * light counts, builds the execution log, writes it to file, and drives the
 * termination UI flow on the SwiftBot.
 */

package org.example;

import swiftbot.*;

import java.io.FileWriter;
import java.io.IOException;

/**
 * Manages the termination phase of the SwiftBot Traffic Light Navigation System.
 * Responsibilities include starting and stopping the session timer, recording
 * the colour of each detected light, building the execution log string,
 * writing it to a file on the Pi, and coordinating the termination UI screens.
 */
public class TerminationHandler {

    /* System time in milliseconds at the point Button A was pressed. */
    private long startTime;

    // Per-colour counters incremented each time a light is detected
    private int redCount;
    private int greenCount;
    private int blueCount;

    /* Running total of all valid lights detected during the session. */
    private int totalLightCount;

    /*
     * Volatile flags used to coordinate state between the main thread's
     * polling loop and button-press callback threads. Without volatile,
     * writes from a callback thread may not be visible to the polling thread.
     */
    private volatile boolean isChoiceMade = false;
    private volatile String userChoice = "";

    // Polling interval while waiting for a button press on the termination screen
    private static final int BUTTON_POLL_INTERVAL_MS = 300;

    // Pause after displaying the execution log before proceeding to final termination
    private static final int LOG_DISPLAY_PAUSE_MS = 2000;

    // File path on the Raspberry Pi where the execution log is written
    private static final String LOG_FILE_PATH = "/data/home/pi/trafficLight_log.txt";

    /**
     * Displays the termination screen, waits for the user to choose whether to
     * show the execution log (Button Y) or skip straight to exit (Button X),
     * then writes the log to file and shuts the SwiftBot down.
     * @param swiftBot the SwiftBot API instance used to manage buttons and movement.
     * @return true once termination is complete.
     */
    public boolean terminationScreen(SwiftBotAPI swiftBot) {

        // Reset choice state in case this method is ever called more than once
        isChoiceMade = false;
        userChoice   = "";

        swiftBot.disableAllButtons();
        TrafficLightMainController.displayTerminationRequestedScreen();

        // Setting up valid buttons Y and X
        swiftBot.enableButton(Button.X, () -> {
            userChoice   = "X";
            isChoiceMade = true;
        });

        swiftBot.enableButton(Button.Y, () -> {
            userChoice   = "Y";
            isChoiceMade = true;
        });

        // Map unused buttons to the invalid-input screen
        swiftBot.enableButton(Button.A, () ->
                TrafficLightMainController.displayTerminationRequestedInvalidInputScreen());
        swiftBot.enableButton(Button.B, () ->
                TrafficLightMainController.displayTerminationRequestedInvalidInputScreen());

        // Poll until the user presses a valid button
        while (!isChoiceMade) {
            try {
                Thread.sleep(BUTTON_POLL_INTERVAL_MS);
            } catch (InterruptedException ignored) {}
        }

        swiftBot.disableAllButtons();
        String finalLogInfo = createLogInfo();

        if (userChoice.equals("Y")) {
            // User wants to see the log — display it, pause, then terminate
            TrafficLightMainController.displayExecutionLogScreen(finalLogInfo);

            try {
                Thread.sleep(LOG_DISPLAY_PAUSE_MS);
            } catch (InterruptedException ignored) {}

            finalTermination(swiftBot, finalLogInfo);
        }

        if (userChoice.equals("X")) {
            // User chose to skip the log display — terminate immediately
            finalTermination(swiftBot, finalLogInfo);
        }

        return true;
    }

    /**
     * Records the system time as the start of the session.
     * Should be called immediately after Button A is pressed to begin the run.
     */
    public void startTimer() {
        startTime = System.currentTimeMillis();
    }

    /**
     * Increments the counter for the given colour and the overall light total.
     * UNKNOWN colours are ignored and not counted.
     * @param colour the detected traffic light colour to record.
     */
    public void incrementLightCount(TrafficLightColourHolder colour) {

        if (colour == TrafficLightColourHolder.RED) {
            redCount++;
            totalLightCount++;
        } else if (colour == TrafficLightColourHolder.GREEN) {
            greenCount++;
            totalLightCount++;
        } else if (colour == TrafficLightColourHolder.BLUE) {
            blueCount++;
            totalLightCount++;
        }
    }

    /**
     * Builds and returns the execution log string containing the total light count,
     * most frequent colour, and total session duration in seconds.
     * @return the formatted log string ready for display or file output.
     */
    private String createLogInfo() {

        long endTime           = System.currentTimeMillis();
        double totalDurationSecs = (endTime - startTime) / 1000.0;

        String mostFrequentColourInfo = calculateMostFrequentColour();

        // Building the log string
        StringBuilder log = new StringBuilder();
        log.append("Total Traffic Lights Encountered: ").append(totalLightCount).append("\n");
        log.append(mostFrequentColourInfo).append("\n");
        log.append("Total execution time (s): ")
                .append(String.format("%.2f", totalDurationSecs)).append("\n");

        return log.toString();
    }

    /**
     * Determines the most frequently detected colour across the session.
     * In the event of a tie, the first colour checked wins (RED then GREEN then BLUE).
     * @return a human-readable string naming the most frequent colour and its count,
     * or a "N/A" message if no lights were recorded.
     */
    private String calculateMostFrequentColour() {

        if (totalLightCount == 0) {
            return "Most frequent colour: N/A (no lights recorded)";
        }

        String mostFrequentColour = "RED";
        int highestCount          = redCount;

        if (greenCount > highestCount) {
            mostFrequentColour = "GREEN";
            highestCount       = greenCount;
        }
        if (blueCount > highestCount) {
            mostFrequentColour = "BLUE";
            highestCount       = blueCount;
        }

        return "Most frequent colour was " + mostFrequentColour
                + " encountered " + highestCount + " times";
    }

    /**
     * Writes the execution log to a file on the Raspberry Pi and returns the file path.
     * Each call overwrites the previous log file.
     * @param logInformation the log string to write to the file.
     * @return the file path where the log was written.
     */
    // IMPROVEMENT: should use try-with-resources so the writer always gets closed
    public String logging(String logInformation) {

        try (FileWriter writer = new FileWriter(LOG_FILE_PATH)) {
            writer.write(logInformation);
            writer.write("\n");
        } catch (IOException e) {
            System.out.println("ERROR: Could not write log file.");
        }

        return LOG_FILE_PATH;
    }

    /**
     * Performs the final shutdown sequence: writes the log to file, stops all
     * SwiftBot movement and underlights, disables all buttons, and displays
     * the termination screen showing the log file path.
     * @param swiftBot       the SwiftBot API instance to shut down.
     * @param logInformation the execution log string to save and display.
     */
    public void finalTermination(SwiftBotAPI swiftBot, String logInformation) {

        // Save the log to file
        String filePath = logging(logInformation);

        try {
            // Shut everything down
            swiftBot.stopMove();
            swiftBot.disableUnderlights();
            swiftBot.disableAllButtons();
            TrafficLightMainController.displayTerminationScreen(filePath);
        } catch (Exception e) {
            System.out.println("Termination error");
        }
    }
}