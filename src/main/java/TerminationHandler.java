package org.example;

import swiftbot.*;

import java.io.FileWriter;
import java.io.IOException;

public class TerminationHandler {

    private long startTime;

    private int redCount;
    private int greenCount;
    private int blueCount;
    private int totalLightCount;

    private boolean isChoiceMade = false;
    private String userChoice = "";


    public void terminationScreen(SwiftBotAPI swiftBot){

        isChoiceMade = false;
        userChoice = "";

        swiftBot.disableAllButtons();

        System.out.println("\n========================================");
        System.out.println("           TERMINATION SCREEN");
        System.out.println("========================================");
        System.out.println("Press Y to DISPLAY LOG DATA");
        System.out.println("Press X to TERMINATE PROGRAM");
        System.out.println("========================================\n");

        swiftBot.enableButton(Button.X, () -> {
            System.out.println("Terminating program...");
            userChoice = "X";
            isChoiceMade = true;
        });

        swiftBot.enableButton(Button.Y, () -> {
            userChoice = "Y";
            isChoiceMade = true;
        });

        while(isChoiceMade == false){
            try {
                Thread.sleep(300);
            } catch (InterruptedException ignored) {}
        }
        swiftBot.disableAllButtons();

        if (userChoice.equals("Y")) {

            System.out.println("\n----- LOG INFORMATION -----");
            System.out.println(createLogInfo());

            try { Thread.sleep(2000);
            } catch (InterruptedException ignored) {}

            finalTermination(swiftBot);
        }

        if (userChoice.equals("X")) {
            // Final termination: write log + shutdown + exit
            finalTermination(swiftBot);
        }

    }

    public void startTimer() {
        startTime = System.currentTimeMillis();
    }

    public void incrementLightCount(TrafficLightColourHolder colour) {

        if (colour == TrafficLightColourHolder.RED){
            redCount++;
            totalLightCount++;
        }
        else if(colour == TrafficLightColourHolder.GREEN){
            greenCount++;
            totalLightCount++;
        }
        else if(colour == TrafficLightColourHolder.BLUE){
            blueCount++;
            totalLightCount++;
        }
    }

    private String createLogInfo() {

        long endTime = System.currentTimeMillis();
        long totalDuration = (endTime - startTime);
        double totalDurationSecs = totalDuration / 1000.0; // convert to seconds

        String mostFrequentColourInfo = calculateMostFrequentColour();

        String logInformation = "";
        logInformation += "===== TRAFFIC LIGHT LOG =====\n";
        logInformation += "Total Traffic lights Encountered: " + totalLightCount + "\n";
        logInformation += mostFrequentColourInfo + "\n";
        logInformation += "Total execution time (s): " + String.format("%.2f", totalDurationSecs) + "\n";
        logInformation += "=============================\n";

        return logInformation;
    }

    private String calculateMostFrequentColour() {

        if (totalLightCount == 0) {
            return "Most frequent colour: N/A (no lights recorded)";
        }

        // calculate the most frequent colour
        String mostFrequentColour = "RED";
        int highestCount = redCount;

        if (greenCount > highestCount) {
            mostFrequentColour = "GREEN";
            highestCount = greenCount;
        }
        if (blueCount > highestCount) {
            mostFrequentColour = "BLUE";
            highestCount = blueCount;
        }

        return "Most frequent colour was " + mostFrequentColour + " encountered " + highestCount + " times";
    }

    public String logging(){

        String filePath = "/data/home/pi/trafficLight_log.txt";
        String logInformation = createLogInfo();

        try{
            //for appending to existing log file.
           // FileWriter writer = new FileWriter(filePath, true);
            FileWriter writer = new FileWriter(filePath); // overwrite existing file
            writer.write(logInformation);
            writer.write("\n");
            writer.close();
        }catch (IOException e) {
            System.out.println("ERROR: Could not write log file.");
        }
        return filePath;
    }


    public void finalTermination(SwiftBotAPI swiftBot) {

        // write log to file
        String filePath = logging();
        System.out.println("Log file stored at: " + filePath);

        try {
            swiftBot.stopMove();
            swiftBot.disableUnderlights();
            swiftBot.disableAllButtons();
            System.exit(0);
        } catch (Exception e) {
            System.out.println("Termination error");
        }

    }

}
