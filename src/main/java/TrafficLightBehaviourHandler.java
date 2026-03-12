/*
 * Defines the SwiftBot movement behaviour for each detected traffic light colour:
 * stop and hold for red, pass at speed for green, and the give-way sequence for blue.
 */

package org.example;

import swiftbot.*;

/**
 * Executes the SwiftBot movement behaviour corresponding to each detected
 * traffic light colour. Behaviours are driven by the speeds configured in
 * the selected driving mode, which are passed in at construction time.
 */
public class TrafficLightBehaviourHandler {

    private final SwiftBotAPI swiftBot;

    /* Forward movement speed configured by the selected driving mode. */
    private final int initialSpeed;

    /* Speed used when passing through a green traffic light. */
    private final int greenPassSpeed;

    // Fixed low speed used during the blue light sequence
    private static final int BLUE_LOW_SPEED = 30;

    //---------Red light Behaviour constants-----------//
    private static final double RED_STOP_GAP_CM = 7.0;          // Target gap in cm from the red light
    private static final double SPEED_Scale_Per_Sec = 0.5;      // Approx 0.5 cm/s per speed unit
    private static final int RED_APPROACH_MAX_TIME = 4000;         // Max time the bot will move forward
    private static final int RED_APPROACH_MIN_TIME = 100;          // Min move time before we skip
    private static final int RED_POST_BEHAVIOUR_STOP  = 200;      // Settle pause after stopping

    //-------- Behaviour timing constants-------------//
    private static final int RED_WAIT_TIME = 1000;                 // Wait time at a red light
    private static final int GREEN_PASS_DURATION = 1000;      // Duration for the green pass move
    private static final int GREEN_WAIT_TIME = 1000;     // Wait time after passing green
    private static final int BLUE_INITIAL_STOP_TIME = 1000;        // Initial stop pause for blue
    private static final int BLUE_BLINK_DELAY = 300;                // Duration for each underlight blink
    private static final int BLUE_TURN_TIME = 650;                 // Duration for left/right turns
    private static final int BLUE_MOVE_FORWARD_TIME = 1000;             // Slow forward movement duration
    private static final int BLUE_FORWARD_PAUSE_TIME = 1000;        // Wait after stopping in give-way zone
    private static final int BLUE_REVERSE_TIME = 2000;             // Duration for reversing back
    private static final int BLUE_TURN_SPEED = 40;               // Motor speed for turns

    /**
     * Creates a TrafficLightBehaviourHandler with the given SwiftBot instance
     * and the speeds configured by the selected driving mode.
     * @param swiftBot       the SwiftBot API instance used to control movement and underlights.
     * @param initialSpeed   the forward movement speed for the current driving mode.
     * @param greenPassSpeed the pass-through speed for the current driving mode.
     */
    public TrafficLightBehaviourHandler(SwiftBotAPI swiftBot, int initialSpeed, int greenPassSpeed) {
        this.swiftBot = swiftBot;
        this.initialSpeed = initialSpeed;
        this.greenPassSpeed = greenPassSpeed;
    }

    /**
     * Calls the corresponding light behaviour based on the detected colour.
     * When UNKNOWN colour is detected — logs a message and continues.
     * @param colour the detected traffic light colour from the TrafficLightColourHolder.
     * @throws InterruptedException if the thread is interrupted during a sleep or move.
     */
    public void callLightBehaviours(TrafficLightColourHolder colour) throws InterruptedException {

        if (colour == TrafficLightColourHolder.RED) {
            redLightHandler();
        } else if (colour == TrafficLightColourHolder.GREEN) {
            greenLightHandler();
        } else if (colour == TrafficLightColourHolder.BLUE) {
            blueLightHandler();
        } else {
            System.out.println("Unknown colour detected. Continuing...");
        }
    }

    /**
     * Moves the SwiftBot forward to within RED_STOP_Distance of the red light and then stops.
     *  It Uses the current ultrasound reading and a speed-to-distance
     * estimate to calculate how long to drive forward - using speed = distance * time.
     * @throws InterruptedException if the thread is interrupted during the move.
     */
    private void stopAtRedLight() throws InterruptedException {

        // Getting the distance to the object ahead
        double ultrasoundDistance = swiftBot.useUltrasound();
        System.out.println("Ultrasound distance: " + String.format("%.2f", ultrasoundDistance) + " cm");

        // Invalid reading the SwiftBot will not move
        if (ultrasoundDistance <= 0) {
            System.out.println("Invalid ultrasound reading. Not moving.");
            return;
        }

        // How far we actually need to travel to be about 5-2 cm from the light
        // tuned to adjust to the SwiftBot Hardware variability
        double distanceToMove = ultrasoundDistance - RED_STOP_GAP_CM;

        // SwiftBot is already in the desired distance - SwiftBot stops
        if (distanceToMove <= 0) {
            System.out.println("Already at or closer than " + RED_STOP_GAP_CM + " cm. Not moving.");
            swiftBot.stopMove();
            return;
        }

        // calculate travel time from distance and approximate speed in cm/s
        double speedCmPerSecond = SPEED_Scale_Per_Sec * initialSpeed;
        int timeToMove = (int) Math.round((distanceToMove / speedCmPerSecond) * 1000.0);

        // Movement Time is negligible so stop moving
        if (timeToMove < RED_APPROACH_MIN_TIME) {
            System.out.println("Very close to target. Not moving forward.");
            swiftBot.stopMove();
            return;
        }

        // if the time is large it gets capped to 4 secs - error detection
        if (timeToMove > RED_APPROACH_MAX_TIME) {
            timeToMove = RED_APPROACH_MAX_TIME;
        }

        System.out.println("Moving forward about " + String.format("%.2f", distanceToMove) + " cm for " + timeToMove + " ms");

        // Move forward up to the light and stop
        swiftBot.move(initialSpeed, initialSpeed, timeToMove);
        swiftBot.stopMove();

        // small wait before returning to the redLightHandler() method
        Thread.sleep(RED_POST_BEHAVIOUR_STOP);
    }

    /**
     * Executes the red light behaviour: sets underlights to red, approaches the
     * stop position, waits, then returns (the caller resumes movement).
     * @throws InterruptedException if the thread is interrupted during the sequence.
     */
    private void redLightHandler() throws InterruptedException {

        System.out.println("Red light behaviour: STOP");

        // Setting underlights to red
        int[] red = {255, 0, 0};
        swiftBot.fillUnderlights(red);

        // Approach the red light and stop
        stopAtRedLight();

        // Wait before resuming
        Thread.sleep(RED_WAIT_TIME);
    }

    /**
     * Executes the green light behaviour: sets underlights to green, drives
     * forward at the pass speed for a fixed duration, then stops briefly.
     * @throws InterruptedException if the thread is interrupted during the sequence.
     */
    private void greenLightHandler() throws InterruptedException {

        System.out.println("Green light behaviour: PASS");

        // Setting underlights to green
        int[] green = {0, 255, 0};
        swiftBot.fillUnderlights(green);

        // Drive through at the pass speed
        swiftBot.move(greenPassSpeed, greenPassSpeed, GREEN_PASS_DURATION);
        swiftBot.stopMove();

        // small 200ms wait to avoid robot continuing to move before the main pause
        Thread.sleep(GREEN_WAIT_TIME);
    }

    /**
     * Executes the blue light give-way sequence: stops, blinks underlights blue,
     * turns left, drives forward slowly, stops, reverses back, then turns right
     * to restore the original direction.
     * @throws InterruptedException if the thread is interrupted during the sequence.
     */
    private void blueLightHandler() throws InterruptedException {

        System.out.println("Blue light behaviour: GIVE WAY");

        int[] blue = {0, 0, 255};

        // Stop and wait for 1 sec
        swiftBot.stopMove();
        Thread.sleep(BLUE_INITIAL_STOP_TIME);

        // Blink underlights blue once
        swiftBot.fillUnderlights(blue);
        Thread.sleep(BLUE_BLINK_DELAY);
        swiftBot.disableUnderlights();

        // Turn left about 90 degrees
        swiftBot.move(-BLUE_TURN_SPEED, BLUE_TURN_SPEED, BLUE_TURN_TIME);
        swiftBot.stopMove();

        // Move forward slowly and then stop
        swiftBot.move(BLUE_LOW_SPEED, BLUE_LOW_SPEED, BLUE_MOVE_FORWARD_TIME);
        swiftBot.stopMove();

        // wait for 1 sec
        Thread.sleep(BLUE_FORWARD_PAUSE_TIME);

        // Reverse back slowly and stop
        swiftBot.move(-BLUE_LOW_SPEED, -BLUE_LOW_SPEED, BLUE_REVERSE_TIME);
        swiftBot.stopMove();

        // Turn right to return to facing the original direction and stop
        swiftBot.move(BLUE_TURN_SPEED, -BLUE_TURN_SPEED, BLUE_TURN_TIME);
        swiftBot.stopMove();
    }
}