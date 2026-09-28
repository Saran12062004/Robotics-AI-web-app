# SafeRoute AI — Safe Route System (Robotics & AI)

An Android app that helps students move around campus safely. Patrol robots sense crowd, lighting and obstacles on every path, and an AI route planner picks the **safest** route instead of just the shortest one.

## Features

| Screen | What it does |
| --- | --- |
| 🗺️ **Map** | Live campus map. Paths are coloured by AI safety score (green / amber / red). Tap a place to set the destination, long-press to set the start. Compares the AI safest route with the shortest route and gives step-by-step directions. |
| 📍 **Places** | All campus locations: Gate 1, 2, 3 · Canteen 1, 2, 3 · Main Ground · Sports Ground · Parking · Hospital · Library · Admin Block · Hostel · Lab Block. Filter by type, see live safety, crowd and distance, and navigate in one tap. |
| 🤖 **Robots** | Robot fleet simulator: 5 patrol robots with battery, status and live sensor readings. Controls for night mode, crowd surges and obstacles. |
| 📊 **Dashboard** | Active robots, obstacles, active users, SOS alerts, campus safety index, top risk hotspots and a live alert feed. |
| 🚨 **SOS** | 3-second countdown, then vibration. The nearest robot is dispatched, the safest path to the nearest gate or hospital is shown, and you can call 112 or share the alert. |

Routes re-plan automatically when robots report new obstacles, crowds or streetlight failures.

## How the AI scores a path

```
risk   = 0.5 × darkness + 0.3 × crowd risk + 0.2 × (no CCTV)
safety = 100 × (1 − risk)
```

- **Crowd risk is U-shaped:** an empty path (isolated) and an overcrowded path are both unsafe.
- **Safest route:** Dijkstra with edge cost `length × (1 + 6 × risk)`.
- **Shortest route:** Dijkstra with edge cost `length`.
- **Blocked paths:** paths with an obstacle are skipped in both modes.

## Project structure

```
app/src/main/java/com/saferoute/app/
├── MainActivity.kt            bottom navigation + top bar
├── SafeRouteViewModel.kt      live state, ticks the simulation every 1.5 s
├── model/Campus.kt            places, paths (length, lamps, CCTV)
├── model/Models.kt            robots, alerts, routes, SOS
├── engine/SafetyAI.kt         risk scoring + Dijkstra route planner
├── engine/CampusSimulator.kt  robots, crowd, lighting, obstacles, day/night
└── ui/                        Map, Places, Robots, Dashboard, SOS screens
```

**Tech:** Kotlin · Jetpack Compose · Material 3 · MVVM (ViewModel + StateFlow) · minSdk 26

## Run it

1. Install **Android Studio**.
2. `git clone https://github.com/Saran12062004/Robotics-AI-web-app.git`
3. In Android Studio, choose **File → Open** and select the cloned folder. Wait for the Gradle sync to finish.
4. Pick an emulator or a connected phone and press **Run ▶**.

**Command line:** `./gradlew assembleDebug` builds `app/build/outputs/apk/debug/app-debug.apk`.
