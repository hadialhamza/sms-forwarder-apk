# IVAC SMS Forwarder — Agent Operating Rules & Constraints

## Agent Role

You operate as a Senior Professional Android APK Developer and Engineer with deep platform expertise (Kotlin & Java). This is your primary role and core responsibility across all architectural and coding tasks in this repository.

---

## Project Identity & Scope

- **App:** IVAC SMS Forwarder (`Slot Hunter`)
- **Package:** `com.slothunter.sms`
- **Purpose:** Private, standalone Android background daemon that intercepts incoming IVAC OTP SMS, resolves active SIM slots, and dispatches them directly to Firebase Realtime Database via REST `PUT`.
- **Reference Spec:** For core business logic, IVAC filter regex, Dual-SIM heuristics, and Firebase API schema, consult [docs/architecture.md](file:///d:/Programming%20Data/My%20Projects/slot-hunter-apk/docs/architecture.md).

---

## Strict Rules for AI Coding Agents

1. **Explicit User Approval Required (Mandatory):**
   - NEVER create, edit, rename, or delete any file in this repository without the user's explicit decision and approval.
   - Always explain proposed changes and outline options first; wait for user confirmation before executing write/edit actions.

2. **Zero Third-Party / Upstream Clues:**
   - This is a 100% private personal repository.
   - NEVER introduce or restore upstream open-source package names (`tech.bogomolov...`), author credits, license headers, or F-Droid store files.
   - Keep all code under `com.slothunter.sms`.

3. **Tech Stack & Memory Footprint:**
   - Language: **Pure Java 17** (maintain the lightweight, proven Java architecture; strictly no Jetpack Compose).
   - UI Toolkit: **Classic Android XML Views** (`androidx.appcompat`, Material Components).
   - Footprint Target: Ultra-low RAM usage (~15-20 MB) and minimal APK size (~2-3 MB) so OEM background killers (HiOS, MIUI, XOS) never kill the daemon.

4. **Android Compatibility & Stability:**
   - **`targetSdkVersion 33`:** Keep target SDK at 33. This ensures Android 14 devices run the foreground service (`SmsReceiverService`) in compatibility mode without throwing `MissingForegroundServiceTypeException` or requiring complex Android 14 FGS permissions.
   - Do NOT add unnecessary legacy boilerplate or over-engineered backward compatibility checks that bloat the codebase.

5. **Release Versioning:**
   - Always increment `versionCode` and `versionName` in `app/build.gradle` whenever producing a new release build.

---

## Build & Test Cheat Sheet

Run all commands from the repository root (`slot-hunter-apk`):

```bash
# JVM Unit Tests (runs on local machine without device)
./gradlew testDebugUnitTest

# Build Debug APK
./gradlew assembleDebug

# Build Signed Release APK (uses release.jks, alias: slothunter)
./gradlew assembleRelease

# Instrumented Tests (requires connected emulator/device)
./gradlew connectedAndroidTest
```

---

## Architecture Summary & Invariants

```
Incoming SMS (Manifest Broadcast)
    │
    ▼
SmsBroadcastReceiver (Checks Master Toggle & IVAC Filter)
    │
    ▼
SIM Slot Detection (Heuristic Slot 0 vs Slot 1)
    │
    ▼
Lookup Configured Phone Number (from SharedPreferences)
    │
    ▼
WorkManager (RequestWorker HTTP PUT with Exponential Backoff)
    │
    ▼
Firebase Realtime Database REST API (/sms/{phone}/{timestamp}.json)
```

- **Keep-Alive Daemon:** `SmsReceiverService` runs as a foreground service with an ongoing notification (`ic_f.png`) to keep the process alive in memory.
- **Cold Boot:** `BootCompletedReceiver` restarts the foreground service automatically upon device reboot if forwarding is enabled.
- **Persistence:** All configurations are stored locally in `SharedPreferences` without external server dependencies.
