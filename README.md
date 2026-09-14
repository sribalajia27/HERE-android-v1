# HERE — V1 (Android, Jetpack Compose)

See where you are in the universe.

## What's implemented

- Single-screen experience, no login/nav/settings — matches the V1 scope in the spec.
- Pinch-out or one-finger drag to zoom out through 10 scientifically-grounded scale levels (You → Your world →
  Earth → Moon → Sun → Solar System → Milky Way → Local Group → Cosmic Web → Observable Universe),
  combining realistic public-domain imagery with procedural cosmic scenes and cross-fading as you move
  between levels. Distances are shown in kilometres, light-seconds, light-minutes, light-hours, and light-years.
- A tappable scale indicator (`10ⁿ m`) that stays out of the way until you want it.
- The "YOU" marker that fades out as the scale stops being able to contain a point.
- The full emotional arc at the top: reaching the universe → silence → "Look for Earth" →
  pinch back in → the two closing lines → **COME HOME**, which animates the zoom back to
  zero.
- Final screen with **EXPLORE AGAIN** and **SHARE MY JOURNEY** (native Android share sheet,
  plain text — swap in a rendered share *image* post-V1 per the spec).
- No login, social features, chat/AI assistant, ads, gamification, or settings screens.

## Project layout

```
app/src/main/java/com/example/here/
  CosmicLevel.kt        the 10 scale stops + exponent labels and distance facts
  CosmicVisuals.kt      realistic image/procedural scene per scale, with cross-fade
  CosmicZoomScreen.kt    state machine (intro → journey → arrived → seeking earth →
                          earth found → returning → final) + gesture handling
  MainActivity.kt        entry point
  ui/theme/Theme.kt       minimal black Compose theme
```

## Build it

1. Open this folder in **Android Studio (Koala or newer)**. It will generate the Gradle
   wrapper jar automatically on first sync (network access to `services.gradle.org` and the
   Google/Maven repos is required, same as any new Android project).
2. Run on a device or emulator with **API 26+**.
3. Try it: pinch outward anywhere on screen to begin the zoom-out, or drag down with one finger. Pinch inward once you've
   reached the universe to come back and find Earth.

## Tuning knobs

- `PINCH_SENSITIVITY` in `CosmicZoomScreen.kt` — how fast a pinch moves you between levels.
- `SETTLE_WINDOW` — how close to an integer level you need to be before the title/subtitle
  fade in.
- Level content (titles, subtitles, size facts, order-of-magnitude exponents) lives entirely
  in `CosmicLevel.kt` — easy to correct or extend without touching UI code.

## Deliberately left out of V1 (per spec)

Login, social graph, chat/AI assistant, in-experience ads, an astronomy article library,
gamification, and complex settings. The only question that should gate a new feature:
**does this make me feel the scale?**
