package org.example;

import swiftbot.*;

public class TrafficLightBehaviourHandler {

    private SwiftBotAPI swiftBot;

    private int initialSpeed;
    private int greenPassSpeed;
    private int blueLowSpeed = 30;

    public TrafficLightBehaviourHandler(SwiftBotAPI swiftBot, int initialSpeed, int greenPassSpeed){

        this.swiftBot = swiftBot;
        this.initialSpeed = initialSpeed;
        this.greenPassSpeed = greenPassSpeed;
    }

    public void callLightBehaviours(TrafficLightColourHolder colour) throws InterruptedException {

        if (colour == TrafficLightColourHolder.RED) {
            redLightHandler();
        }

        else if (colour == TrafficLightColourHolder.GREEN) {
            greenLightHandler();
        }

        else if (colour == TrafficLightColourHolder.BLUE) {
            blueLightHandler();
        }

        else {
            System.out.println("Unknown colour detected. Continuing...");
        }
    }

    private void stopAtRedLight() throws InterruptedException {

        double gapDistance = 7.0;

        int initialSpeedOnApproach = initialSpeed;  // uses the mode speed (40/50/60)

        double speedCmPerSecond = 0.5 * initialSpeedOnApproach;

        double ultrasoundDistance = swiftBot.useUltrasound();
        System.out.println("Ultrasound distance: " + String.format("%.2f", ultrasoundDistance) + " cm");

        if (ultrasoundDistance <= 0) {
            System.out.println("Invalid ultrasound reading. Not moving.");
            return;
        }

        double distanceToMove = ultrasoundDistance - gapDistance;

        if (distanceToMove <= 0) {
            System.out.println("Already at or closer than " + gapDistance + " cm. Not moving.");
            swiftBot.stopMove();
            return;
        }

        int timeToMove = (int) Math.round((distanceToMove / speedCmPerSecond) * 1000.0);

        if (timeToMove < 100) {
            System.out.println("Very close to target. Not moving forward.");
            swiftBot.stopMove();
            return;
        }

        if (timeToMove > 4000) {
            timeToMove = 4000;
        }

        System.out.println("Moving forward about " + String.format("%.2f", distanceToMove) + " cm for " + timeToMove + " ms");

        swiftBot.move(initialSpeedOnApproach, initialSpeedOnApproach, timeToMove);
        swiftBot.stopMove();

        Thread.sleep(200);
    }

    private void redLightHandler() throws InterruptedException {

        System.out.println("Red light behaviour: STOP");

        int[] red = {255, 0, 0};

        swiftBot.fillUnderlights(red);

        stopAtRedLight();

        Thread.sleep(1000);
    }

    private void greenLightHandler() throws InterruptedException {

        System.out.println("Green light behaviour: PASS");

        int[] green = {0, 255, 0};

        swiftBot.fillUnderlights(green);

        swiftBot.move(greenPassSpeed, greenPassSpeed, 2000);

        swiftBot.stopMove();

        Thread.sleep(1000);
    }

    private void blueLightHandler() throws InterruptedException {

        System.out.println("Blue light behaviour: GIVE WAY");

        int[] blue = {0, 0, 255};

        swiftBot.stopMove();

        Thread.sleep(1000);

        swiftBot.fillUnderlights(blue);

        Thread.sleep(300);

        swiftBot.disableUnderlights();

        // turn left
        swiftBot.move(-40, 40, 650);
        swiftBot.stopMove();

        // move forward slowly
        swiftBot.move(blueLowSpeed, blueLowSpeed, 1000);
        swiftBot.stopMove();

        Thread.sleep(1000);

        // reverse
        swiftBot.move(-blueLowSpeed, -blueLowSpeed, 2000);
        swiftBot.stopMove();

        // turn back right
        swiftBot.move(40, -40, 650);
        swiftBot.stopMove();
    }

}
