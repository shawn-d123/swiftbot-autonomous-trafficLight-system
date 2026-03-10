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


    public boolean terminationScreen(SwiftBotAPI swiftBot){

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

        while(isChoiceMade == false){ /// might use a flag instead of while loop to wait for user input
            try {
                Thread.sleep(300);
            } catch (InterruptedException ignored) {}
        }
        swiftBot.disableAllButtons();
        String finalLogInfo = createLogInfo();

        if (userChoice.equals("Y")) {

            System.out.println("\n----- LOG INFORMATION -----");
            System.out.println(finalLogInfo);

            try { Thread.sleep(2000);
            } catch (InterruptedException ignored) {}

            finalTermination(swiftBot, finalLogInfo);
        }

        if (userChoice.equals("X")) {
            // Final termination: write log + shutdown + exit
            finalTermination(swiftBot, finalLogInfo);
        }

        return true;
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

    public String logging(String logInformation){

        String filePath = "/data/home/pi/trafficLight_log.txt";

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


    public void finalTermination(SwiftBotAPI swiftBot, String logInformation) {

        // write log to file and get file path
        String filePath = logging(logInformation);
        System.out.println("Log file stored at: " + filePath);

        try {
            swiftBot.stopMove();
            swiftBot.disableUnderlights();
            swiftBot.disableAllButtons();
            System.out.println("Program terminated. Goodbye.");
        } catch (Exception e) {
            System.out.println("Termination error");
        }

    }

}
