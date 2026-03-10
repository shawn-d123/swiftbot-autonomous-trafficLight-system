package org.example;

import java.util.Scanner;
import swiftbot.*;

public class TrafficLightMainController {

    private static int initialSpeed;
    private static int greenPassSpeed;
    private static int detectionInterval; // ms (how often we check)

    private static final double detectionDistance = 30.0; // cm (fixed requirement)
    private static boolean isSystemRunning = true;
    private static boolean terminationCalled = false;
    private static boolean isHandlingLight = false;

    public static void main(String[] args) throws InterruptedException {

        SwiftBotAPI swiftBot = SwiftBotAPI.INSTANCE;
        isSystemRunning = true;
        terminationCalled = false;
        isHandlingLight = false;

        // Additional functionality FIRST
        selectDrivingMode();

        // Welcome + start gate
        displayWelcomeScreen();
        buttonAPause(swiftBot); /// what does this do???

        // Start timer only when the run begins
        TerminationHandler terminationHandler = new TerminationHandler();
        terminationHandler.startTimer();

        TrafficLightColourDetector detector = new TrafficLightColourDetector(swiftBot);

        // Pass speeds into behaviour handler so driving mode actually affects behaviour
        TrafficLightBehaviourHandler behaviour = new TrafficLightBehaviourHandler(swiftBot, initialSpeed, greenPassSpeed);

        ThirdLightCheckHandler thirdlighthandler = new ThirdLightCheckHandler(swiftBot);

        int[] yellow = {255, 255, 0};
        swiftBot.fillUnderlights(yellow);

        enableRuntimeTermination(swiftBot);

        // Begin forward movement
        swiftBot.startMove(initialSpeed, initialSpeed);

        while (isSystemRunning) {
            if (terminationCalled == true) {
                isSystemRunning = false;
                break;
            }

            if (isHandlingLight == true) {
                Thread.sleep(detectionInterval);
                continue;
            }

            double distance = swiftBot.useUltrasound();

            // Only process when within 30cm
            if (distance > 0 && distance <= detectionDistance) {
                isHandlingLight = true;
                try {
                    // swiftBot.stopMove();
                    // Thread.sleep(300); // stabilize camera reading

                    TrafficLightColourHolder colour = detector.detectColour();

                    System.out.println("Distance: " + String.format("%.2f", distance) + " cm");
                    System.out.println("Colour: " + colour);

                    /// might be better to call incrementation from the behaviour handler after the behaviour is executed, in case we want to have different counting logic for different behaviours in the future (e.g. only count if we successfully passed a green light, or only count red lights that we stopped at, etc.)
                    if (colour != TrafficLightColourHolder.UNKNOWN) {
                        terminationHandler.incrementLightCount(colour);
                    }

                    behaviour.callLightBehaviours(colour);

                    boolean terminate = false;

                    if (colour != TrafficLightColourHolder.UNKNOWN) {
                        terminate = thirdlighthandler.thridLightHandler();
                    }

                    if (terminate == true) {
                        terminationCalled = true;
                    }
                    else if (terminationCalled == false) {
                        swiftBot.fillUnderlights(yellow);
                        swiftBot.startMove(initialSpeed, initialSpeed);
                    }

                    // avoid re-detecting the same light instantly
                    Thread.sleep(800);
                    /// use a flag to indicate we are in the "post-detection cooldown" instead of sleeping the thread,
                    ////which also blocks button presses. This is a problem because we want to be able to terminate at any time, even during cooldown.
                }
                finally {
                    isHandlingLight = false;
                    enableRuntimeTermination(swiftBot);
                }
            }

            // This is the DRIVING MODE detection frequency
            Thread.sleep(detectionInterval); /// use the flag
        }

        boolean hasTerminated = terminationHandler.terminationScreen(swiftBot);

        if (hasTerminated == true) {
            System.exit(0);
        }
    }

    private static void selectDrivingMode() {

        Scanner scanner = new Scanner(System.in);

        while (true) {

            System.out.println("========================================");
            System.out.println("          TRAFFIC LIGHT SYSTEM");
            System.out.println("========================================");
            System.out.println("Select Driving Mode:");
            System.out.println("1) Cautious");
            System.out.println("2) Normal");
            System.out.println("3) Fast");
            System.out.print("Enter choice (1/2/3): ");

            String input = scanner.nextLine().trim();

            // validation but needs to be more robust (e.g. handle non-integer input without crashing)
            if (input.equals("1")) {
                initialSpeed = 40;
                greenPassSpeed = 50;
                detectionInterval = 1000;
                displayDrivingModeConfirmation("Cautious");
                break;
            }
            else if (input.equals("2")) {
                initialSpeed = 50;
                greenPassSpeed = 60;
                detectionInterval = 600;
                displayDrivingModeConfirmation("Normal");
                break;
            }
            else if (input.equals("3")) {
                initialSpeed = 60;
                greenPassSpeed = 70;
                detectionInterval = 400;
                displayDrivingModeConfirmation("Fast");
                break;
            }
            else {
                System.out.println("\n----------------------------------------");
                System.out.println("ERROR: Invalid driving mode selected.");
                System.out.println("Try again by entering 1, 2, or 3.");
                System.out.println("----------------------------------------\n");
            }
        }
    }

    private static void displayDrivingModeConfirmation(String modeName) {

        System.out.println("\n========================================");
        System.out.println("     DRIVING MODE SUCCESSFULLY SELECTED");
        System.out.println("========================================");
        System.out.println("Selected mode: " + modeName);
        System.out.println("Initial speed: " + initialSpeed);
        System.out.println("Detection interval: " + detectionInterval + "ms");
        System.out.println("========================================\n");
    }

    private static void displayWelcomeScreen() {
        System.out.println("\n========================================");
        System.out.println("        SWIFTBOT TRAFFIC LIGHT");
        System.out.println("========================================");
        System.out.println("Press Button A to START");
        System.out.println("========================================\n");
    }

    /// explain this one - it waits for button A to be pressed before starting the main loop, which is important because we don't want the robot to start moving before the user is ready. It also disables all buttons first to ensure that only button A can be used to start the system, preventing any accidental presses of other buttons from interfering with the startup process.
    private static void buttonAPause(SwiftBotAPI swiftBot) throws InterruptedException {

        final boolean[] started = {false};

        swiftBot.disableAllButtons();

        swiftBot.enableButton(Button.A, () -> started[0] = true);

        while (started[0] == false) {
            Thread.sleep(200);
        }

        swiftBot.disableAllButtons();
        System.out.println("Button A pressed. Starting...\n");
    }

    private static void enableRuntimeTermination(SwiftBotAPI swiftBot) {
        try {
            // Prevent duplicate callback binding when this method is called multiple times.
            swiftBot.disableButton(Button.X);
        } catch (Exception ignored) {
            // Safe to ignore if Button X is already disabled.
        }

        swiftBot.enableButton(Button.X, () -> {
            terminationCalled = true;
            swiftBot.stopMove();
            System.out.println("Termination requested. Preparing to stop...");
        });
    }
}
