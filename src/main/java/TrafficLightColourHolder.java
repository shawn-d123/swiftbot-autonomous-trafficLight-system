/*
 * Defines the set of traffic light colours the SwiftBot can detect and respond to.
 */

package org.example;

/**
 * Represents the possible traffic light colours detected by the SwiftBot camera.
 * UNKNOWN is returned when the average RGB reading does not match any defined colour range.
 */
public enum TrafficLightColourHolder {
    RED,
    GREEN,
    BLUE,
    UNKNOWN
}