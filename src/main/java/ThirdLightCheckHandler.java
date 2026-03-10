package org.example;

import swiftbot.*;
import java.util.Scanner;

public class ThirdLightCheckHandler {

    private SwiftBotAPI swiftBot;
    private int totalLightCount = 0;
    private final Scanner scanner;

    public ThirdLightCheckHandler(SwiftBotAPI swiftBot) {
        this.swiftBot = swiftBot;
        this.scanner = new Scanner(System.in);
    }

    public boolean thridLightHandler() throws InterruptedException {

        totalLightCount++;

        System.out.println("\n----------------------------------------");
        System.out.println("Traffic lights handled: " + totalLightCount);
        System.out.println("----------------------------------------\n");

        if (totalLightCount % 3 == 0) {

            swiftBot.stopMove();
            swiftBot.disableUnderlights();
            swiftBot.disableAllButtons();

            System.out.println("\n========================================");
            System.out.println("3 TRAFFIC LIGHTS COMPLETED");
            System.out.println("CHECKPOINT REACHED");
            System.out.println("Enter 'C' to CONTINUE");
            System.out.println("Enter 'X' to TERMINATE");
            System.out.println("========================================\n");

            while (true) {
                System.out.print("Checkpoint choice (C/X): ");
                String input = scanner.nextLine().trim().toUpperCase();

                if (input.equals("C")) {
                    System.out.println("\n========================================");
                    System.out.println("CONTINUE SELECTED");
                    System.out.println("Resuming normal operation...");
                    System.out.println("========================================\n");
                    return false;
                }

                if (input.equals("X")) {
                    System.out.println("\n========================================");
                    System.out.println("TERMINATION SELECTED");
                    System.out.println("Returning control to controller...");
                    System.out.println("========================================\n");
                    return true;
                }

                System.out.println("\n----------------------------------------");
                System.out.println("ERROR: Invalid checkpoint input.");
                System.out.println("Please enter C to continue or X to terminate.");
                System.out.println("----------------------------------------\n");
            }
        }

        return false;
    }
}
