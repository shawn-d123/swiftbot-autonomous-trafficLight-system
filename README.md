# SwiftBot Traffic Light Navigation

A Java application for the SwiftBot Raspberry Pi platform that autonomously detects traffic light colours through camera image processing and responds with the appropriate movement behaviour.

The robot continuously monitors its ultrasound sensor and, once a traffic light falls within range (≤ 30 cm), captures a still image, classifies the signal colour via average RGB analysis, and executes the corresponding action — no manual input required during a run.

---

## Table of Contents

- [Hardware Requirements](#hardware-requirements)
- [Project Structure](#project-structure)
- [Behaviour Reference](#behaviour-reference)
- [Getting Started](#getting-started)
- [Configuration](#configuration)
- [Logging](#logging)
- [Known Limitations](#known-limitations)
- [License](#license)

---

## Hardware Requirements

This project targets the SwiftBot platform exclusively. The following must be enabled before running:

- Raspberry Pi (via SwiftBot)
- Camera module
- Ultrasound sensor
- Underlights

---

## Project Structure

```
org.example/
├── TrafficLightMainController.java     # Entry point, driving mode selection, main control loop
├── TrafficLightColourDetector.java     # Image capture and RGB colour classification
├── TrafficLightBehaviourHandler.java   # Movement and underlight logic per colour
├── ThirdLightCheckHandler.java         # Prompts user to continue or stop every 3 detections
├── TerminationHandler.java             # Tracks stats, writes log, shuts down safely
└── TrafficLightColourHolder.java       # Enum: RED, GREEN, BLUE, Null
```

**`TrafficLightMainController`** — Handles driving mode selection at launch, waits for Button A to begin, then runs the main loop: ultrasound check → stop → classify colour → execute behaviour → prompt on every 3rd detection.

**`TrafficLightColourDetector`** — Captures a `SQUARE_480x480` still and computes average RGB across the full frame, returning `RED`, `GREEN`, `BLUE`, or `Null`.

**`TrafficLightBehaviourHandler`** — Runs the movement and underlight sequence for each colour. Red uses live ultrasound feedback to approach within ~7 cm before waiting. Green drives forward at `greenPassSpeed` for 2 seconds. Blue performs the full yield manoeuvre before returning to the original heading.

**`ThirdLightCheckHandler`** — Counts valid detections and prompts the operator (`Y` / `X`) on every third.

**`TerminationHandler`** — Starts a timer at run begin, accumulates colour counts, writes the summary log, and performs a clean shutdown.

**`TrafficLightColourHolder`** — Enum with four values: `RED`, `GREEN`, `BLUE`, `Null`.

---

## Behaviour Reference

| Signal | Underlights | Movement |
|--------|-------------|----------|
| Red | Solid red → yellow | Stop, approach to ~7 cm gap, wait, resume |
| Green | Solid green → yellow | Move forward for 2 s at pass speed, pause, resume |
| Blue | Brief blue → off → yellow | Turn left, creep forward, reverse, return to heading, resume |
| Null / Unknown | No change | Logs unrecognised signal and continues |

---

## Getting Started

### Prerequisites

- JDK installed on the Raspberry Pi
- `SwiftBot-API-6.0.0.jar` in your working directory
- Camera, ultrasound sensor, and underlights enabled on the SwiftBot

### Compile

Run this from the directory containing your `.java` source files:

```bash
javac -cp SwiftBot-API-6.0.0.jar -d . *.java
```

### Run

```bash
java -cp ".:SwiftBot-API-6.0.0.jar" org.example.TrafficLightMainController
```

Select a driving mode when prompted:

```
1 — Cautious
2 — Normal
3 — Fast
```

Then press **Button A** on the SwiftBot to start the run.

---

## Configuration

Driving mode parameters are defined in `TrafficLightMainController`:

| Parameter | Cautious | Normal | Fast |
|-----------|----------|--------|------|
| `initialSpeed` | 40 | 50 | 60 |
| `greenPassSpeed` | 50 | 60 | 70 |
| `detectionInterval` (ms) | 1000 | 600 | 400 |

Detection is triggered when ultrasound distance is within:

```java
detectionDistance = 30.0; // cm
```

Colour thresholds can be adjusted in `TrafficLightColourDetector.classifyColour()` to account for different lighting conditions.

---

## Logging

On termination the program writes a summary to:

```
/data/home/pi/trafficLight_log.txt
```

The log contains total lights encountered, the most frequently detected colour and its count, and total execution time in seconds.

---

## Known Limitations

- Classification uses the average RGB of the entire image frame, so accuracy degrades in poor lighting or when the traffic light occupies a small portion of the frame. Tune the thresholds in `classifyColour()` for your environment if needed.
- The system is built for and tested on the SwiftBot platform only and is not intended to be portable to other hardware without significant rework.

---

## License

MIT — see [LICENSE](LICENSE) for details.

---

## Author

[Shawn Santan D'Souza](https://github.com/shawn-d123)
