package org.example;

import java.util.Scanner;
import swiftbot.*;

public class TrafficLightMainController {

    private static int initialSpeed;
    private static int greenPassSpeed;
    private static int detectionInterval; // ms (how often we check)

    private static final double detectionDistance = 30.0; // cm (fixed requirement)

    public static void main(String[] args) throws InterruptedException {

        SwiftBotAPI swiftBot = SwiftBotAPI.INSTANCE;

        // Additional functionality FIRST
        selectDrivingMode();

        // Welcome + start gate
        displayWelcomeScreen();
        buttonAPause(swiftBot);

        // Start timer only when the run begins
        TerminationHandler terminationHandler = new TerminationHandler();
        terminationHandler.startTimer();

        TrafficLightColourDetector detector = new TrafficLightColourDetector(swiftBot);

        // Pass speeds into behaviour handler so driving mode actually affects behaviour
        TrafficLightBehaviourHandler behaviour = new TrafficLightBehaviourHandler(swiftBot, initialSpeed, greenPassSpeed);

        ThirdLightCheckHandler thirdlighthandler = new ThirdLightCheckHandler(swiftBot);

        int[] yellow = {255, 255, 0};
        swiftBot.fillUnderlights(yellow);

        // Begin forward movement
        swiftBot.startMove(initialSpeed, initialSpeed);

        while (true) {

            double distance = swiftBot.useUltrasound();

            // Only process when within 30cm
            if (distance > 0 && distance <= detectionDistance) {

                swiftBot.stopMove();
                Thread.sleep(300); // stabilize camera reading

                TrafficLightColourHolder colour = detector.detectColour();

                System.out.println("Distance: " + String.format("%.2f", distance) + " cm");
                System.out.println("Colour: " + colour);

                if (colour != TrafficLightColourHolder.Null) {
                    terminationHandler.incrementLightCount(colour);
                }

                behaviour.callLightBehaviours(colour);

                boolean terminate = false;

                if (colour != TrafficLightColourHolder.Null) {
                    terminate = thirdlighthandler.thridLightHandler();
                }

                if (terminate == true) {
                    terminationHandler.terminationScreen(swiftBot);
                }
                else {
                    swiftBot.startMove(initialSpeed, initialSpeed);
                }

                // avoid re-detecting the same light instantly
                Thread.sleep(800);
            }

            // This is the DRIVING MODE detection frequency
            Thread.sleep(detectionInterval);
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
}