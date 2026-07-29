# QDVC NTS — Note to Self

A native Android app for capturing **notes to self**, backed by plain folders of
Markdown on the device. Built following the
[QDVC folder-backed plaintext editor specification](https://github.com/qdvc-apps/qdvc-android-app-specification).

- **applicationId:** `qdvc.notetoself.android.app`
- **Stack:** Kotlin + Jetpack Compose + Material 3, single-Activity, `minSdk 26`, `targetSdk 34`, JDK 17.

## What a note is

Each note is a folder inside a user-granted workspace, named
`YYYY-MM-DD-slugged-title`, e.g. `2026-07-29-we-should-visit-the-zoo`, containing:

```
2026-07-29-we-should-visit-the-zoo/
  README.md
  payloads/
    screenshot-zoo.png
    IMG_1234.HEIC
```

`README.md` is generated to this shape:

```markdown
# 2026-07-29 We should visit the zoo

Recorded Wed 29 Jul 2026 15:34:39 AWST

## Abstract

We should visit the zoo because the kids would really enjoy seeing some penguins.

## Payload

https://www.example.com/zoo

Attached images: [screenshot-zoo.png](payloads/screenshot-zoo.png); [IMG_1234.HEIC](payloads/IMG_1234.HEIC)
```

## Functionality

1. **Write a new note** — title (required), abstract, and a payload that can be
   pasted text/URLs and/or attached images. Images are copied into `payloads/`.
2. **Storage** — saved to the workspace folder chosen in the home screen, one
   folder per note with a `YYYY-MM-DD` prefix and `README.md` inside.
3. **View & edit** — browse all notes, open them into the multitasking switcher,
   view read-only, and edit any field or payload. Renaming the title renames the
   folder (date prefix preserved); `README.md` is regenerated on every save.

## Layout of the four bottom-bar tabs

**Home** (structure explorer + Settings), **View** (read-only note, with a pencil
in the top-right to enter edit mode), **Jump** (multitasking switcher with
swipe-to-close and reorder), and **New** (starts a fresh note straight away).
Edit is reached from View's pencil rather than being its own bottom-bar item.
View is disabled until a note is open.

Only **one workspace** is used at a time; picking a new folder replaces the
previous one (your files are never touched) and resets the open-note session.

## Deviations from the spec (and why)

- **Item is a folder, not a single file.** A note-to-self needs a body *and*
  image payloads, so the unit of storage is a folder containing `README.md` +
  `payloads/`. The SAF layer, index, and switcher therefore key off the note
  **folder** URI rather than a `.md` file URI.
- **Edit is a structured form, not a raw Markdown editor.** Because the README
  has a fixed schema (title / abstract / payload / images), the Edit surface
  exposes those fields directly and regenerates the Markdown, rather than
  offering free-form source editing with live syntax colouring. This trades the
  spec's `VisualTransformation` editor for correctness of the generated file.
- **Custom fonts / device-font discovery** are out of scope for this round;
  font **size** is adjustable per surface as the spec requires, and theming,
  system-bar matching, the hierarchy slide, and the back-button rule are all
  implemented.

## Build

```
./gradlew assembleDebug
```

Requires a standard Android SDK with platform 34 and JDK 17.
