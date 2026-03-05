package org.example;

import swiftbot.*;

public class ThirdLightCheckHandler {

    private SwiftBotAPI swiftBot;

    private int totalLightCount = 0;

    private boolean hasUserSelected = false;
    private boolean isTerminationCalled = false;

    public ThirdLightCheckHandler(SwiftBotAPI swiftBot) {
        this.swiftBot = swiftBot;
    }

    public boolean thridLightHandler() throws InterruptedException {

        totalLightCount++;

        System.out.println("\n----------------------------------------");
        System.out.println("Traffic lights handled: " + totalLightCount);
        System.out.println("----------------------------------------\n");

        if (totalLightCount % 3 == 0) {

            swiftBot.stopMove();
            swiftBot.disableUnderlights();

            System.out.println("\n========================================");
            System.out.println("3 TRAFFIC LIGHTS COMPLETED");
            System.out.println("Press Button Y to CONTINUE");
            System.out.println("Press Button X to TERMINATE");
            System.out.println("========================================\n");

            hasUserSelected = false;
            isTerminationCalled = false;

            swiftBot.disableAllButtons();

            swiftBot.enableButton(Button.X, () -> {
                isTerminationCalled = true;
                hasUserSelected = true;
            });

            swiftBot.enableButton(Button.Y, () -> {
                isTerminationCalled = false;
                hasUserSelected = true;
            });

            while (hasUserSelected == false) {
                Thread.sleep(200);
            }

            swiftBot.disableAllButtons();

            if (isTerminationCalled == true) {
                System.out.println("[X] Termination selected. Going to termination screen...");
                return true;
            }

            System.out.println("[Y] Continue selected. Resuming movement...");
            return false;
        }

        return false;
    }
}