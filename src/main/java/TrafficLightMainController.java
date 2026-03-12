/*
 * @author: Shawn Santan D'Souza
 * Last updated: March 2026
 * CS1814 Software Implementation — SwiftBot Traffic Light
 *
 * Main controller for the SwiftBot traffic light program.
 * Coordinates detection, behaviour, checkpoint, and termination subsystems.
 */

package org.example;

import java.util.Scanner;
import swiftbot.*;

/**
 * Entry point and main controller for the SwiftBot Traffic Light System.
 * Runs the driving mode selection, startup sequence, and main detection loop.
 * Delegates light behaviour, checkpoint logic, and termination handling to
 * their corresponding handler classes.
 */
public class TrafficLightMainController {

    /* Speed and interval attributes which are set by the selected driving mode. */
    private static int initialSpeed;
    private static int greenPassSpeed;
    private static int detectionInterval;

    // Maximum distance in cm at which we consider a traffic light detected
    private static final double DETECTION_DISTANCE = 30.0;

    /// change this later
    /*
     * Volatile because these flags are written by button-callback threads
     * and read by the main loop thread. Without volatile, changes may not
     * be visible across threads due to CPU caching.
     */
    private static volatile boolean isSystemRunning   = true;
    private static volatile boolean terminationCalled = false;
    private static volatile boolean isHandlingLight   = false;

    // These are timing constants
    private static final int UI_SCREEN_PAUSE = 3000;
    private static final int REDETECTION_PAUSE = 800;
    private static final int BUTTON_WAIT_DELAY= 200;

    // Cautious driving mode related values
    private static final int CAUTIOUS_INITIAL_SPEED      = 40;
    private static final int CAUTIOUS_GREEN_PASS_SPEED   = 50;
    private static final int CAUTIOUS_DETECTION_INTERVAL = 1000;

    // Normal driving mode related values
    private static final int NORMAL_INITIAL_SPEED        = 50;
    private static final int NORMAL_GREEN_PASS_SPEED     = 60;
    private static final int NORMAL_DETECTION_INTERVAL   = 600;

    // Fast driving mode related values
    private static final int FAST_INITIAL_SPEED          = 60;
    private static final int FAST_GREEN_PASS_SPEED       = 70;
    private static final int FAST_DETECTION_INTERVAL     = 400;

    // ANSI colour codes for CLI UI output
    private static final String RESET  = "\u001B[0m";
    private static final String WHITE  = "\u001B[37m";
    private static final String GREEN  = "\u001B[32m";
    private static final String RED    = "\u001B[31m";
    private static final String CYAN   = "\u001B[36m";
    private static final String YELLOW = "\u001B[33m";


    /**
     * This is the main Entry point. It Runs driving mode selection, the startup screen, and then the
     * main detection loop until termination is requested.
     */
    public static void main(String[] args) throws InterruptedException {
        // Get the singleton instance of the SwiftBot API
        SwiftBotAPI swiftBot = SwiftBotAPI.INSTANCE;

        // Initialize state flags
        isSystemRunning   = true;
        terminationCalled = false;
        isHandlingLight   = false;

        // Prompts the user to select a driving mode
        selectDrivingMode();
        Thread.sleep(UI_SCREEN_PAUSE);

        // Show the start screen and wait for Button A or X
        displayStartUpScreen();
        waitForStartScreenInput(swiftBot);

        // If the user pressed X on the start screen, exit immediately
        if (terminationCalled) {
            swiftBot.disableAllButtons();
            swiftBot.disableUnderlights();
            System.out.println("Termination selected on start screen. Exiting program.");
            System.exit(0);
        }

        // Timer starts here so elapsed time reflects the actual run, not mode selection
        TerminationHandler terminationHandler = new TerminationHandler();
        terminationHandler.startTimer();

        // Creating the handler objects for detection, behaviour, and checkpoints
        TrafficLightColourDetector detector = new TrafficLightColourDetector(swiftBot);
        TrafficLightBehaviourHandler behaviour = new TrafficLightBehaviourHandler(swiftBot, initialSpeed, greenPassSpeed);
        ThirdLightCheckHandler thirdLightHandler = new ThirdLightCheckHandler(swiftBot);

        // Setting underlights to yellow and starting forward movement
        int[] yellow = {255, 255, 0};
        swiftBot.fillUnderlights(yellow);

        enableRuntimeTermination(swiftBot);
        swiftBot.startMove(initialSpeed, initialSpeed);

        // --- Main detection loop ---
        while (isSystemRunning) {

            // Check if termination was requested via Button X
            if (terminationCalled) {
                isSystemRunning = false;
                break;
            }

            // Skip detection while a light is already being handled
            if (isHandlingLight) {
                Thread.sleep(detectionInterval);
                continue;
            }

            // Read ultrasound distance
            double distance = swiftBot.useUltrasound();

            // Object detected within range — could be a traffic light
            if (distance > 0 && distance <= DETECTION_DISTANCE) {
                isHandlingLight = true;
                try {
                    // Capture image and detect the colour
                    TrafficLightColourHolder colour = detector.detectColour();

                    // Only count and display if it's a known colour
                    if (colour != TrafficLightColourHolder.UNKNOWN) {
                        terminationHandler.incrementLightCount(colour);

                        // Show the colour-specific screen
                        if (colour == TrafficLightColourHolder.GREEN) {
                            displayGreenLightHandlingScreen(distance);
                        } else if (colour == TrafficLightColourHolder.RED) {
                            displayRedLightHandlingScreen(distance);
                        } else if (colour == TrafficLightColourHolder.BLUE) {
                            displayBlueLightHandlingScreen(distance);
                        }
                    }

                    // Execute the movement behaviour for the detected colour
                    behaviour.callLightBehaviours(colour);

                    // Check if we've hit every third light
                    boolean terminate = false;
                    if (colour != TrafficLightColourHolder.UNKNOWN) {
                        terminate = thirdLightHandler.thridLightHandler();
                    }

                    if (terminate) {
                        terminationCalled = true;
                    } else if (!terminationCalled) {
                        // Resume forward movement and detection
                        if (colour != TrafficLightColourHolder.UNKNOWN) {
                            displayResumingDetectionScreen();
                        }
                        swiftBot.fillUnderlights(yellow);
                        swiftBot.startMove(initialSpeed, initialSpeed);
                    }

                    // Brief cooldown to prevent the same light being re-detected immediately
                    Thread.sleep(REDETECTION_PAUSE);

                } finally {
                    // Always reset — ensures the loop can continue even if an exception occurs
                    isHandlingLight = false;
                    enableRuntimeTermination(swiftBot);
                }
            }

            Thread.sleep(detectionInterval);
        }

        // Run the termination screen flow
        boolean hasTerminated = terminationHandler.terminationScreen(swiftBot);

        if (hasTerminated) {
            System.exit(0);
        }
    }

    /**
     * Prompts the user to select a driving mode via CLI.
     * Sets initialSpeed, greenPassSpeed, and detectionInterval accordingly.
     * Re-prompts on invalid input.
     */
    private static void selectDrivingMode() {

        Scanner scanner = new Scanner(System.in);
        displayDrivingModeSelectionScreen();

        // Keep looping until we get a valid choice
        while (true) {
            String input = scanner.nextLine().trim();

            if (input.equals("1")) {
                initialSpeed      = CAUTIOUS_INITIAL_SPEED;
                greenPassSpeed    = CAUTIOUS_GREEN_PASS_SPEED;
                detectionInterval = CAUTIOUS_DETECTION_INTERVAL;
                displayDrivingModeConfirmationScreen("Cautious");
                break;
            } else if (input.equals("2")) {
                initialSpeed      = NORMAL_INITIAL_SPEED;
                greenPassSpeed    = NORMAL_GREEN_PASS_SPEED;
                detectionInterval = NORMAL_DETECTION_INTERVAL;
                displayDrivingModeConfirmationScreen("Normal");
                break;
            } else if (input.equals("3")) {
                initialSpeed      = FAST_INITIAL_SPEED;
                greenPassSpeed    = FAST_GREEN_PASS_SPEED;
                detectionInterval = FAST_DETECTION_INTERVAL;
                displayDrivingModeConfirmationScreen("Fast");
                break;
            } else {
                displayDrivingModeInvalidInputScreen();
            }
        }
    }

    /**
     * Blocks until Button A (start) or Button X (exit) is pressed on the SwiftBot.
     * Buttons B and Y are mapped to an invalid-input screen rather than left silent.
     * @param swiftBot takes the SwiftBot API instance.
     * @throws InterruptedException if the polling sleep is interrupted.
     */
    private static void waitForStartScreenInput(SwiftBotAPI swiftBot) throws InterruptedException {

        /*
         * Single-element arrays used here because lambda callbacks cannot
         * write to plain local variables (they must be effectively final).
         */
        final boolean[] started = {false};
        final boolean[] waitingForStartInput = {true};

        swiftBot.disableAllButtons();

        // Button A starts the run
        swiftBot.enableButton(Button.A, () -> {
            started[0]              = true;
            waitingForStartInput[0] = false;
        });

        // Button X calls termination immediately
        swiftBot.enableButton(Button.X, () -> {
            terminationCalled       = true;
            waitingForStartInput[0] = false;
        });

        // Map invalid buttons to the error screen
        swiftBot.enableButton(Button.B, () -> {
            displayStartScreenInvalidInputScreen();
        });
        swiftBot.enableButton(Button.Y, () -> {
            displayStartScreenInvalidInputScreen();
        });

        // Wait until a valid button is pressed
        while (waitingForStartInput[0] && !terminationCalled) {
            Thread.sleep(BUTTON_WAIT_DELAY);
        }

        swiftBot.disableAllButtons();

        // If A was pressed, show the run started screen.
        if (started[0]) {
            displayRunStartedScreen();
        }
    }

    /**
     * Registers Button X as the runtime termination trigger.
     * Disables any existing handler first to prevent duplicate callback bindings.
     * @param swiftBot takes the SwiftBot API instance.
     */
    private static void enableRuntimeTermination(SwiftBotAPI swiftBot) {

        // Disable any existing Button X handler to prevent multiple callbacks stacking up
        try {
            swiftBot.disableButton(Button.X);
        } catch (Exception ignored) {
            // ignore
        }

        // enable Button X to trigger termination when pressed
        swiftBot.enableButton(Button.X, () -> {
            terminationCalled = true; // Set the termination flag to signal to main loop
            swiftBot.stopMove();
            System.out.println("TERMINATION REQUESTED");
        });
    }

    // -----------------------------------------------------------------------------------//
    // ----------------------- BELOW ARE ALL THE UI HELPER METHODS ----------------------//
    // ---------------------------------------------------------------------------------//

    /** Displays the driving mode selection screen. */
    private static void displayDrivingModeSelectionScreen() {
        System.out.println();
        System.out.println(CYAN + " ________  _______    ______   ________  ________  ______   ______         __        ______   ______   __    __  ________ " + RESET);
        System.out.println(CYAN + "/        |/       \\  /      \\ /        |/        |/      | /      \\       /  |      /      | /      \\ /  |  /  |/        |" + RESET);
        System.out.println(CYAN + "$$$$$$$$/ $$$$$$$  |/$$$$$$  |$$$$$$$$/ $$$$$$$$/ $$$$$$/ /$$$$$$  |      $$ |      $$$$$$/ /$$$$$$  |$$ |  $$ |$$$$$$$$/ " + RESET);
        System.out.println(CYAN + "   $$ |   $$ |__$$ |$$ |__$$ |$$ |__    $$ |__      $$ |  $$ |  $$/       $$ |        $$ |  $$ | _$$/ $$ |__$$ |   $$ |   " + RESET);
        System.out.println(CYAN + "   $$ |   $$    $$< $$    $$ |$$    |   $$    |     $$ |  $$ |            $$ |        $$ |  $$ |/    |$$    $$ |   $$ |   " + RESET);
        System.out.println(CYAN + "   $$ |   $$$$$$$  |$$$$$$$$ |$$$$$/    $$$$$/      $$ |  $$ |   __       $$ |        $$ |  $$ |$$$$ |$$$$$$$$ |   $$ |   " + RESET);
        System.out.println(CYAN + "   $$ |   $$ |  $$ |$$ |  $$ |$$ |      $$ |       _$$ |_ $$ \\__/  |      $$ |_____  _$$ |_ $$ \\__$$ |$$ |  $$ |   $$ |   " + RESET);
        System.out.println(CYAN + "   $$ |   $$ |  $$ |$$ |  $$ |$$ |      $$ |      / $$   |$$    $$/       $$       |/ $$   |$$    $$/ $$ |  $$ |   $$ |   " + RESET);
        System.out.println(CYAN + "   $$/    $$/   $$/ $$/   $$/ $$/       $$/       $$$$$$/  $$$$$$/        $$$$$$$$/ $$$$$$/  $$$$$$/  $$/   $$/    $$/    " + RESET);
        System.out.println();
        System.out.println(CYAN   + "=========================================================================================================================" + RESET);
        System.out.println(YELLOW + "                                                SELECT DRIVING MODE" + RESET);
        System.out.println(CYAN   + "=========================================================================================================================" + RESET);
        System.out.println("1) Cautious");
        System.out.println("2) Normal");
        System.out.println("3) Fast");
        System.out.println();
        System.out.print(YELLOW + "Enter choice (1/2/3): " + RESET);
    }

    /** Displays the invalid input screen for driving mode selection. */
    private static void displayDrivingModeInvalidInputScreen() {
        System.out.println();
        System.out.println(RED + "==================================================================================" + RESET);
        System.out.println();
        System.out.println(RED + "    _____ _   _ _____  _    _ _______    ______ _____  _____   ____  _____  " + RESET);
        System.out.println(RED + "   |_   _| \\ | |  __ \\| |  | |__   __|  |  ____|  __ \\|  __ \\ / __ \\|  __ \\ " + RESET);
        System.out.println(RED + "     | | |  \\| | |__) | |  | |  | |     | |__  | |__) | |__) | |  | | |__) |" + RESET);
        System.out.println(RED + "     | | | . ` |  ___/| |  | |  | |     |  __| |  _  /|  _  /| |  | |  _  / " + RESET);
        System.out.println(RED + "    _| |_| |\\  | |    | |__| |  | |     | |____| | \\ \\| | \\ \\| |__| | | \\ \\ " + RESET);
        System.out.println(RED + "   |_____|_| \\_|_|     \\____/   |_|     |______|_|  \\_\\_|  \\_\\\\____/|_|  \\_\\" + RESET);
        System.out.println();
        System.out.println(RED + "==================================================================================" + RESET);
        System.out.println(WHITE + "ERROR! Invalid Driving Mode Selected." + RESET);
        System.out.println(WHITE + "Please Enter one of the following modes:" + RESET);
        System.out.println();
        System.out.println(WHITE + "Enter '1' to select Cautious" + RESET);
        System.out.println(WHITE + "Enter '2' to select Normal" + RESET);
        System.out.println(WHITE + "Enter '3' to select Fast" + RESET);
        System.out.println();
        System.out.print(YELLOW + "Enter choice (1/2/3): " + RESET);
    }

    /**
     * Displays the driving mode confirmation screen.
     * @param modeName the name of the selected mode.
     */
    private static void displayDrivingModeConfirmationScreen(String modeName) {
        System.out.println();
        System.out.println(GREEN + "=============================================================================================================================================================================================================================================" + RESET);
        System.out.println();
        System.out.println(GREEN + "  _____  _____  _______      _______ _   _  _____    __  __  ____  _____  ______     _____ _    _  _____ _____ ______  _____ _____ ______ _    _ _      _  __     __    _____ ______ _      ______ _____ _______ ______ _____  " + RESET);
        System.out.println(GREEN + " |  __ \\|  __ \\|_   _\\ \\    / /_   _| \\ | |/ ____|  |  \\/  |/ __ \\|  __ \\|  ____|   / ____| |  | |/ ____/ ____|  ____|/ ____/ ____|  ____| |  | | |    | | \\ \\   / /   / ____|  ____| |    |  ____/ ____|__   __|  ____|  __ \\ " + RESET);
        System.out.println(GREEN + " | |  | | |__) | | |  \\ \\  / /  | | |  \\| | |  __   | \\  / | |  | | |  | | |__     | (___ | |  | | |   | |    | |__  | (___| (___ | |__  | |  | | |    | |  \\ \\_/ /   | (___ | |__  | |    | |__ | |       | |  | |__  | |  | |" + RESET);
        System.out.println(GREEN + " | |  | |  _  /  | |   \\ \\/ /   | | | . ` | | |_ |  | |\\/| | |  | | |  | |  __|     \\___ \\| |  | | |   | |    |  __|  \\___ \\\\___ \\|  __| | |  | | |    | |   \\   /     \\___ \\|  __| | |    |  __|| |       | |  |  __| | |  | |" + RESET);
        System.out.println(GREEN + " | |__| | | \\ \\ _| |_   \\  /   _| |_| |\\  | |__| |  | |  | | |__| | |__| | |____    ____) | |__| | |___| |____| |____ ____) |___) | |    | |__| | |____| |____| |      ____) | |____| |____| |___| |____   | |  | |____| |__| |" + RESET);
        System.out.println(GREEN + " |_____/|_|  \\_\\_____|   \\/   |_____|_| \\_|\\_____|  |_|  |_|\\____/|_____/|______|  |_____/ \\____/ \\_____\\_____|______|_____/_____/|_|     \\____/|______|______|_|     |_____/|______|______|______\\_____|  |_|  |______|_____/ " + RESET);
        System.out.println();
        System.out.println(GREEN + "=============================================================================================================================================================================================================================================" + RESET);
        System.out.println();
        System.out.println(WHITE + "Selected mode      : " + GREEN + modeName + RESET);
        System.out.println(WHITE + "Initial speed      : " + GREEN + initialSpeed + RESET);
        System.out.println(WHITE + "Detection interval : " + GREEN + detectionInterval + "ms" + RESET);
        System.out.println();
    }

    /** Displays the startup screen prompting the user to press Button A or X. */
    private static void displayStartUpScreen() {
        System.out.println();
        System.out.println(CYAN + "======================================================================================" + RESET);
        System.out.println();
        System.out.println(CYAN + "  _____  ______          _______     __   _______ ____     _____  _    _ _   _ " + RESET);
        System.out.println(CYAN + " |  __ \\|  ____|   /\\   |  __ \\ \\   / /  |__   __/ __ \\   |  __ \\| |  | | \\ | |" + RESET);
        System.out.println(CYAN + " | |__) | |__     /  \\  | |  | \\ \\_/ /      | | | |  | |  | |__) | |  | |  \\| |" + RESET);
        System.out.println(CYAN + " |  _  /|  __|   / /\\ \\ | |  | |\\   /       | | | |  | |  |  _  /| |  | | . ` |" + RESET);
        System.out.println(CYAN + " | | \\ \\| |____ / ____ \\| |__| | | |        | | | |__| |  | | \\ \\| |__| | |\\  |" + RESET);
        System.out.println(CYAN + " |_|  \\_\\______/_/    \\_\\_____/  |_|        |_|  \\____/   |_|  \\_\\\\____/|_| \\_|" + RESET);
        System.out.println();
        System.out.println(CYAN + "======================================================================================" + RESET);
        System.out.println();
        System.out.println(YELLOW + "Press Button A On SwiftBot To Start The Run." + RESET);
        System.out.println(YELLOW + "Press Button X On SwiftBot To Exit At Anytime." + RESET);
        System.out.println();
        System.out.println(YELLOW + "Waiting For Button Press..." + RESET);
    }

    /** Displays the invalid input screen for the startup screen (Button B or Y pressed). */
    private static void displayStartScreenInvalidInputScreen() {
        System.out.println();
        System.out.println(RED + "==================================================================================" + RESET);
        System.out.println();
        System.out.println(RED + "    _____ _   _ _____  _    _ _______    ______ _____  _____   ____  _____  " + RESET);
        System.out.println(RED + "   |_   _| \\ | |  __ \\| |  | |__   __|  |  ____|  __ \\|  __ \\ / __ \\|  __ \\ " + RESET);
        System.out.println(RED + "     | | |  \\| | |__) | |  | |  | |     | |__  | |__) | |__) | |  | | |__) |" + RESET);
        System.out.println(RED + "     | | | . ` |  ___/| |  | |  | |     |  __| |  _  /|  _  /| |  | |  _  / " + RESET);
        System.out.println(RED + "    _| |_| |\\  | |    | |__| |  | |     | |____| | \\ \\| | \\ \\| |__| | | \\ \\ " + RESET);
        System.out.println(RED + "   |_____|_| \\_|_|     \\____/   |_|     |______|_|  \\_\\_|  \\_\\\\____/|_|  \\_\\" + RESET);
        System.out.println();
        System.out.println(RED + "==================================================================================" + RESET);
        System.out.println(WHITE + "ERROR! Invalid Button Was Pressed." + RESET);
        System.out.println(WHITE + "Press Button A On SwiftBot To Start." + RESET);
        System.out.println();
        System.out.println(YELLOW + "Waiting For Button A Press..." + RESET);
        System.out.println();
    }

    /** Displays the run started confirmation screen after Button A is pressed. */
    private static void displayRunStartedScreen() {
        System.out.println();
        System.out.println(GREEN + "========================================================================================" + RESET);
        System.out.println();
        System.out.println(GREEN + "  _____  _    _ _   _     _____ _______       _____ _______ ______ _____  " + RESET);
        System.out.println(GREEN + " |  __ \\| |  | | \\ | |   / ____|__   __|/\\   |  __ \\__   __|  ____|  __ \\ " + RESET);
        System.out.println(GREEN + " | |__) | |  | |  \\| |  | (___    | |  /  \\  | |__) | | |  | |__  | |  | |" + RESET);
        System.out.println(GREEN + " |  _  /| |  | | . ` |   \\___ \\   | | / /\\ \\ |  _  /  | |  |  __| | |  | |" + RESET);
        System.out.println(GREEN + " | | \\ \\| |__| | |\\  |   ____) |  | |/ ____ \\| | \\ \\  | |  | |____| |__| |" + RESET);
        System.out.println(GREEN + " |_|  \\_\\\\____/|_| \\_|  |_____/   |_/_/    \\_\\_|  \\_\\ |_|  |______|_____/ " + RESET);
        System.out.println();
        System.out.println(GREEN + "========================================================================================" + RESET);
        System.out.println();
        System.out.println(WHITE + "Button A was Pressed." + RESET);
        System.out.println(WHITE + "Underlights: Yellow" + RESET);
        System.out.println(WHITE + "Timer Started" + RESET);
        System.out.println();
        System.out.println(WHITE + "Initial speed: " + GREEN + initialSpeed + RESET);
        System.out.println();
        System.out.println(WHITE + "Moving Forward..." + RESET);
        System.out.println(WHITE + "Looking For Traffic Lights..." + RESET);
    }

    /**
     * Displays the green light handling screen.
     * @param distance the measured distance to the green traffic light in cm.
     */
    private static void displayGreenLightHandlingScreen(double distance) {
        System.out.println();
        System.out.println(GREEN + "=====================================================================================================================================" + RESET);
        System.out.println();
        System.out.println(GREEN + "  _____         _____ _____ _____ _   _  _____     _____ _____  ______ ______ _   _    _      _____ _____ _    _ _______ " + RESET);
        System.out.println(GREEN + " |  __ \\ /\\    / ____/ ____|_   _| \\ | |/ ____|   / ____|  __ \\|  ____|  ____| \\ | |  | |    |_   _/ ____| |  | |__   __|" + RESET);
        System.out.println(GREEN + " | |__) /  \\  | (___| (___   | | |  \\| | |  __   | |  __| |__) | |__  | |__  |  \\| |  | |      | || |  __| |__| |  | |   " + RESET);
        System.out.println(GREEN + " |  ___/ /\\ \\  \\___ \\\\___ \\  | | | . ` | | |_ |  | | |_ |  _  /|  __| |  __| | . ` |  | |      | || | |_ |  __  |  | |   " + RESET);
        System.out.println(GREEN + " | |  / ____ \\ ____) |___) |_| |_| |\\  | |__| |  | |__| | | \\ \\| |____| |____| |\\  |  | |____ _| || |__| | |  | |  | |   " + RESET);
        System.out.println(GREEN + " |_| /_/    \\_\\_____/_____/|_____|_| \\_|\\_____|   \\_____|_|  \\_\\______|______|_| \\_|  |______|_____\\_____|_|  |_|  |_|   " + RESET);
        System.out.println();
        System.out.println(GREEN + "=====================================================================================================================================" + RESET);
        System.out.println();
        System.out.println(WHITE + "GREEN RESPONSE IN ACTION..." + RESET);
        System.out.println();
        System.out.println(WHITE + "Detected colour : " + GREEN + "GREEN" + RESET);
        System.out.println(WHITE + "Distance        : " + GREEN + String.format("%.2f", distance) + " cm" + RESET);
        System.out.println();
    }

    /**
     * Displays the red light handling screen.
     * @param distance the measured distance to the red traffic light in cm.
     */
    private static void displayRedLightHandlingScreen(double distance) {
        System.out.println();
        System.out.println(RED + "===========================================================================================================================================" + RESET);
        System.out.println();
        System.out.println(RED + "   _____ _______ ____  _____  _____ _____ _   _  _____          _______    _____  ______ _____     _      _____ _____ _    _ _______ " + RESET);
        System.out.println(RED + "  / ____|__   __/ __ \\|  __ \\|  __ \\_   _| \\ | |/ ____|      /\\|__   __|  |  __ \\|  ____|  __ \\   | |    |_   _/ ____| |  | |__   __|" + RESET);
        System.out.println(RED + " | (___    | | | |  | | |__) | |__) || | |  \\| | |  __      /  \\  | |     | |__) | |__  | |  | |  | |      | || |  __| |__| |  | |   " + RESET);
        System.out.println(RED + "  \\___ \\   | | | |  | |  ___/|  ___/ | | | . ` | | |_ |    / /\\ \\ | |     |  _  /|  __| | |  | |  | |      | || | |_ |  __  |  | |   " + RESET);
        System.out.println(RED + "  ____) |  | | | |__| | |    | |    _| |_| |\\  | |__| |   / ____ \\| |     | | \\ \\| |____| |__| |  | |____ _| || |__| | |  | |  | |   " + RESET);
        System.out.println(RED + " |_____/   |_|  \\____/|_|    |_|   |_____|_| \\_|\\_____|  /_/    \\_\\_|     |_|  \\_\\______|_____/   |______|_____\\_____|_|  |_|  |_|   " + RESET);
        System.out.println();
        System.out.println(RED + "===========================================================================================================================================" + RESET);
        System.out.println();
        System.out.println(WHITE + "RED RESPONSE IN ACTION..." + RESET);
        System.out.println();
        System.out.println(WHITE + "Detected colour : " + RED + "RED" + RESET);
        System.out.println(WHITE + "Distance        : " + RED + String.format("%.2f", distance) + " cm" + RESET);
        System.out.println();
    }

    /**
     * Displays the blue light handling screen.
     * @param distance the measured distance to the blue traffic light in cm.
     */
    private static void displayBlueLightHandlingScreen(double distance) {
        System.out.println();
        System.out.println(CYAN + "=================================================================================================================================================" + RESET);
        System.out.println();
        System.out.println(CYAN + " __     _______ ______ _      _____ _____ _   _  _____          _______    ____  _     _    _ ______    _      _____ _____ _    _ _______ " + RESET);
        System.out.println(CYAN + " \\ \\   / /_   _|  ____| |    |  __ \\_   _| \\ | |/ ____|      /\\|__   __|  |  _ \\| |   | |  | |  ____|  | |    |_   _/ ____| |  | |__   __|" + RESET);
        System.out.println(CYAN + "  \\ \\_/ /  | | | |__  | |    | |  | || | |  \\| | |  __      /  \\  | |     | |_) | |   | |  | | |__     | |      | || |  __| |__| |  | |   " + RESET);
        System.out.println(CYAN + "   \\   /   | | |  __| | |    | |  | || | | . ` | | |_ |    / /\\ \\ | |     |  _ <| |   | |  | |  __|    | |      | || | |_ |  __  |  | |   " + RESET);
        System.out.println(CYAN + "    | |   _| |_| |____| |____| |__| || |_| |\\  | |__| |   / ____ \\| |     | |_) | |___| |__| | |____   | |____ _| || |__| | |  | |  | |   " + RESET);
        System.out.println(CYAN + "    |_|  |_____|______|______|_____/_____|_| \\_|\\_____|  /_/    \\_\\_|     |____/|______\\____/|______|  |______|_____\\_____|_|  |_|  |_|   " + RESET);
        System.out.println();
        System.out.println(CYAN + "=================================================================================================================================================" + RESET);
        System.out.println();
        System.out.println(WHITE + "BLUE RESPONSE IN ACTION..." + RESET);
        System.out.println();
        System.out.println(WHITE + "Detected colour : " + CYAN + "BLUE" + RESET);
        System.out.println(WHITE + "Distance        : " + CYAN + String.format("%.2f", distance) + " cm" + RESET);
        System.out.println();
    }

    /** Displays the resuming detection screen shown after a light response completes. */
    private static void displayResumingDetectionScreen() {
        System.out.println();
        System.out.println(YELLOW + "=================================================================================================================================" + RESET);
        System.out.println();
        System.out.println(YELLOW + "  _____  ______  _____ _    _ __  __ _____ _   _  _____    _____  ______ _______ ______ _____ _______ _____ ____  _   _ " + RESET);
        System.out.println(YELLOW + " |  __ \\|  ____|/ ____| |  | |  \\/  |_   _| \\ | |/ ____|  |  __ \\|  ____|__   __|  ____/ ____|__   __|_   _/ __ \\| \\ | |" + RESET);
        System.out.println(YELLOW + " | |__) | |__  | (___ | |  | | \\  / | | | |  \\| | |  __   | |  | | |__     | |  | |__ | |       | |    | || |  | |  \\| |" + RESET);
        System.out.println(YELLOW + " |  _  /|  __|  \\___ \\| |  | | |\\/| | | | | . ` | | |_ |  | |  | |  __|    | |  |  __|| |       | |    | || |  | | . ` |" + RESET);
        System.out.println(YELLOW + " | | \\ \\| |____ ____) | |__| | |  | |_| |_| |\\  | |__| |  | |__| | |____   | |  | |___| |____   | |   _| || |__| | |\\  |" + RESET);
        System.out.println(YELLOW + " |_|  \\_\\______|_____/ \\____/|_|  |_|_____|_| \\_|\\_____|  |_____/|______|  |_|  |______\\_____|  |_|  |_____\\____/|_| \\_|" + RESET);
        System.out.println();
        System.out.println(YELLOW + "=================================================================================================================================" + RESET);
        System.out.println();
        System.out.println(WHITE + "Resuming SwiftBot forward Movement." + RESET);
        System.out.println(WHITE + "Resuming Traffic light detection." + RESET);
        System.out.println();
    }

    /** Displays the third light checkpoint screen, prompting the user to continue or terminate. */
    public static void displayThirdLightCheckpointScreen() {
        System.out.println();
        System.out.println(YELLOW + "=====================================================================================================================================================" + RESET);
        System.out.println();
        System.out.println(YELLOW + "  _______ _    _ _____ _____  _____     _      _____ _____ _    _ _______     _____ _    _ ______ _____ _  _______   ____ _____ _   _ _______ " + RESET);
        System.out.println(YELLOW + " |__   __| |  | |_   _|  __ \\|  __ \\   | |    |_   _/ ____| |  | |__   __|   / ____| |  | |  ____/ ____| |/ /  __ \\ / __ \\_   _| \\ | |__   __|" + RESET);
        System.out.println(YELLOW + "    | |  | |__| | | | | |__) | |  | |  | |      | || |  __| |__| |  | |     | |    | |__| | |__ | |    | ' /| |__) | |  | || | |  \\| |  | |   " + RESET);
        System.out.println(YELLOW + "    | |  |  __  | | | |  _  /| |  | |  | |      | || | |_ |  __  |  | |     | |    |  __  |  __|| |    |  < |  ___/| |  | || | | . ` |  | |   " + RESET);
        System.out.println(YELLOW + "    | |  | |  | |_| |_| | \\ \\| |__| |  | |____ _| || |__| | |  | |  | |     | |____| |  | | |___| |____| . \\| |    | |__| || |_| |\\  |  | |   " + RESET);
        System.out.println(YELLOW + "    |_|  |_|  |_|_____|_|  \\_\\_____/   |______|_____\\_____|_|  |_|  |_|      \\_____|_|  |_|______\\_____|_|\\_\\_|     \\____/_____|_| \\_|  |_|   " + RESET);
        System.out.println();
        System.out.println(YELLOW + "=====================================================================================================================================================" + RESET);
        System.out.println();
        System.out.println(WHITE + "You Have Encountered THREE TRAFFIC LIGHTS." + RESET);
        System.out.println(WHITE + "Choose Whether To Continue Or Go To Program Termination." + RESET);
        System.out.println();
        System.out.println(WHITE + "Enter " + GREEN + "'C'" + WHITE + " to CONTINUE" + RESET);
        System.out.println(WHITE + "Enter " + RED + "'X'" + WHITE + " to Go To Program TERMINATION" + RESET);
        System.out.println();
        System.out.print(YELLOW + "Enter choice (C/X): " + RESET);
    }

    /** Displays the invalid input screen for the third light checkpoint. */
    public static void displayThirdLightCheckpointInvalidInputScreen() {
        System.out.println();
        System.out.println(RED + "==================================================================================" + RESET);
        System.out.println();
        System.out.println(RED + "    _____ _   _ _____  _    _ _______    ______ _____  _____   ____  _____  " + RESET);
        System.out.println(RED + "   |_   _| \\ | |  __ \\| |  | |__   __|  |  ____|  __ \\|  __ \\ / __ \\|  __ \\ " + RESET);
        System.out.println(RED + "     | | |  \\| | |__) | |  | |  | |     | |__  | |__) | |__) | |  | | |__) |" + RESET);
        System.out.println(RED + "     | | | . ` |  ___/| |  | |  | |     |  __| |  _  /|  _  /| |  | |  _  / " + RESET);
        System.out.println(RED + "    _| |_| |\\  | |    | |__| |  | |     | |____| | \\ \\| | \\ \\| |__| | | \\ \\ " + RESET);
        System.out.println(RED + "   |_____|_| \\_|_|     \\____/   |_|     |______|_|  \\_\\_|  \\_\\\\____/|_|  \\_\\" + RESET);
        System.out.println();
        System.out.println(RED + "==================================================================================" + RESET);
        System.out.println();
        System.out.println(WHITE + "ERROR! Invalid Input Was Entered." + RESET);
        System.out.println(WHITE + "Enter one of the following valid options:" + RESET);
        System.out.println();
        System.out.println(WHITE + "Enter " + GREEN + "'C'" + WHITE + " to CONTINUE" + RESET);
        System.out.println(WHITE + "Enter " + RED + "'X'" + WHITE + " to Go To Program TERMINATION" + RESET);
        System.out.println();
        System.out.print(YELLOW + "Enter choice (C/X): " + RESET);
    }

    /** Displays the termination requested screen, prompting whether to show the execution log. */
    public static void displayTerminationRequestedScreen() {
        System.out.println();
        System.out.println(RED + "===================================================================================================================================================================" + RESET);
        System.out.println();
        System.out.println(RED + "  _______ ______ _____  __  __ _____ _   _       _______ _____ ____  _   _    _____ _   _    _____  _____   ____   _____ _____  ______  _____ _____ " + RESET);
        System.out.println(RED + " |__   __|  ____|  __ \\|  \\/  |_   _| \\ | |   /\\|__   __|_   _/ __ \\| \\ | |  |_   _| \\ | |  |  __ \\|  __ \\ / __ \\ / ____|  __ \\|  ____|/ ____/ ____|" + RESET);
        System.out.println(RED + "    | |  | |__  | |__) | \\  / | | | |  \\| |  /  \\  | |    | || |  | |  \\| |    | | |  \\| |  | |__) | |__) | |  | | |  __| |__) | |__  | (___| (___  " + RESET);
        System.out.println(RED + "    | |  |  __| |  _  /| |\\/| | | | | . ` | / /\\ \\ | |    | || |  | | . ` |    | | | . ` |  |  ___/|  _  /| |  | | | |_ |  _  /|  __|  \\___ \\\\___ \\ " + RESET);
        System.out.println(RED + "    | |  | |____| | \\ \\| |  | |_| |_| |\\  |/ ____ \\| |   _| || |__| | |\\  |   _| |_| |\\  |  | |    | | \\ \\| |__| | |__| | | \\ \\| |____ ____) |___) |" + RESET);
        System.out.println(RED + "    |_|  |______|_|  \\_\\_|  |_|_____|_| \\_/_/    \\_\\_|  |_____\\____/|_| \\_|  |_____|_| \\_|  |_|    |_|  \\_\\\\____/ \\_____|_|  \\_\\______|_____/_____/ " + RESET);
        System.out.println();
        System.out.println(RED + "===================================================================================================================================================================" + RESET);
        System.out.println();
        System.out.println(WHITE + "Button X Was Pressed." + RESET);
        System.out.println(WHITE + "SwiftBot Has Stopped..." + RESET);
        System.out.println();
        System.out.println(WHITE + "Would You Like To Display The Execution Log?" + RESET);
        System.out.println(WHITE + "Press " + GREEN + "Button Y" + WHITE + " to DISPLAY EXECUTION LOG" + RESET);
        System.out.println(WHITE + "Press " + RED + "Button X" + WHITE + " to TERMINATE WITHOUT DISPLAYING EXECUTION LOG" + RESET);
        System.out.println();
        System.out.println(YELLOW + "Waiting for button press..." + RESET);
        System.out.println();
    }

    /** Displays the invalid input screen for the termination screen (wrong button pressed). */
    public static void displayTerminationRequestedInvalidInputScreen() {
        System.out.println();
        System.out.println(RED + "==================================================================================" + RESET);
        System.out.println();
        System.out.println(RED + "    _____ _   _ _____  _    _ _______    ______ _____  _____   ____  _____  " + RESET);
        System.out.println(RED + "   |_   _| \\ | |  __ \\| |  | |__   __|  |  ____|  __ \\|  __ \\ / __ \\|  __ \\ " + RESET);
        System.out.println(RED + "     | | |  \\| | |__) | |  | |  | |     | |__  | |__) | |__) | |  | | |__) |" + RESET);
        System.out.println(RED + "     | | | . ` |  ___/| |  | |  | |     |  __| |  _  /|  _  /| |  | |  _  / " + RESET);
        System.out.println(RED + "    _| |_| |\\  | |    | |__| |  | |     | |____| | \\ \\| | \\ \\| |__| | | \\ \\ " + RESET);
        System.out.println(RED + "   |_____|_| \\_|_|     \\____/   |_|     |______|_|  \\_\\_|  \\_\\\\____/|_|  \\_\\" + RESET);
        System.out.println();
        System.out.println(RED + "==================================================================================" + RESET);
        System.out.println();
        System.out.println(WHITE + "ERROR! Invalid Button Was Pressed." + RESET);
        System.out.println(WHITE + "Press one of the valid buttons below:" + RESET);
        System.out.println();
        System.out.println(WHITE + "Press " + GREEN + "Button Y" + WHITE + " to DISPLAY EXECUTION LOG" + RESET);
        System.out.println(WHITE + "Press " + RED + "Button X" + WHITE + " to TERMINATE WITHOUT DISPLAYING EXECUTION LOG" + RESET);
        System.out.println();
        System.out.println(YELLOW + "Waiting for button press..." + RESET);
        System.out.println();
    }

    /**
     * Displays the execution log screen with the session statistics.
     * @param finalLogInfo the formatted log string from TerminationHandler.
     */
    public static void displayExecutionLogScreen(String finalLogInfo) {
        System.out.println();
        System.out.println(CYAN + "==============================================================================================" + RESET);
        System.out.println();
        System.out.println(CYAN + "  ________   ________ _____ _    _ _______ _____ ____  _   _    _      ____   _____ " + RESET);
        System.out.println(CYAN + " |  ____\\ \\ / /  ____/ ____| |  | |__   __|_   _/ __ \\| \\ | |  | |    / __ \\ / ____|" + RESET);
        System.out.println(CYAN + " | |__   \\ V /| |__ | |    | |  | |  | |    | || |  | |  \\| |  | |   | |  | | |  __ " + RESET);
        System.out.println(CYAN + " |  __|   > < |  __|| |    | |  | |  | |    | || |  | | . ` |  | |   | |  | | | |_ |" + RESET);
        System.out.println(CYAN + " | |____ / . \\| |___| |____| |__| |  | |   _| || |__| | |\\  |  | |___| |__| | |__| |" + RESET);
        System.out.println(CYAN + " |______/_/ \\_\\______\\_____|\\____/   |_|  |_____\\____/|_| \\_|  |______\\____/ \\_____|" + RESET);
        System.out.println();
        System.out.println(CYAN  + "==============================================================================================" + RESET);
        System.out.println(WHITE + finalLogInfo + RESET);
        System.out.println(CYAN  + "==============================================================================================" + RESET);
        System.out.println();
    }

    /**
     * Displays the final termination screen with the log file path.
     * @param filePath the path where the execution log was saved.
     */
    public static void displayTerminationScreen(String filePath) {
        System.out.println();
        System.out.println(RED + "====================================================================================" + RESET);
        System.out.println();
        System.out.println(RED + "  _______ ______ _____  __  __ _____ _   _       _______ _____ _   _  _____ " + RESET);
        System.out.println(RED + " |__   __|  ____|  __ \\|  \\/  |_   _| \\ | |   /\\|__   __|_   _| \\ | |/ ____|" + RESET);
        System.out.println(RED + "    | |  | |__  | |__) | \\  / | | | |  \\| |  /  \\  | |    | | |  \\| | |  __ " + RESET);
        System.out.println(RED + "    | |  |  __| |  _  /| |\\/| | | | | . ` | / /\\ \\ | |    | | | . ` | | |_ |" + RESET);
        System.out.println(RED + "    | |  | |____| | \\ \\| |  | |_| |_| |\\  |/ ____ \\| |   _| |_| |\\  | |__| |" + RESET);
        System.out.println(RED + "    |_|  |______|_|  \\_\\_|  |_|_____|_| \\_/_/    \\_\\_|  |_____|_| \\_|\\_____|" + RESET);
        System.out.println();
        System.out.println(RED + "====================================================================================" + RESET);
        System.out.println();
        System.out.println(WHITE + "Log Saved Successfully." + RESET);
        System.out.println();
        System.out.println(WHITE + "Execution log file location:" + RESET);
        System.out.println(GREEN + filePath + RESET);
        System.out.println();
        System.out.println(WHITE + "PROGRAM TERMINATED." + RESET);
        System.out.println();
        System.out.println(WHITE + "            _____                         _____              " + RESET);
        System.out.println(WHITE + "           (o   o)                       (o   o)             " + RESET);
        System.out.println(WHITE + "          (   V   )       GOODBYE!      (   V   )            " + RESET);
        System.out.println(WHITE + "         ----m-m---------------------------m-m----         "  + RESET);
        System.out.println();
    }
}