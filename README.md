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
   folder; `README.md` is regenerated on every save.
4. **Backdating** — every note's recorded date & time defaults to now but can be
   set to any date/time (useful for notes first written elsewhere). The chosen
   time drives both the `YYYY-MM-DD` folder prefix and a machine-readable
   ISO-8601 stamp in the README, so backdated times survive load/save exactly.
5. **Categories** — a note can be tagged against a category, whose emoji becomes
   the note's icon in the list and switcher and is shown when the note is open:
   ⚠️ Action required · 📘 Ideas and planning · ☎️ Meeting notes · 📗 Useful
   article. The tag is stored as a stable `Category:` key in the README.

All colour themes are **greyscale**, so category emojis stand out as the only
colour in the UI.

## Layout of the four bottom-bar tabs

**Home** opens straight to the list of notes. Its toolbar overflow menu holds
Search, Index status, Change workspace, and Settings. **View** shows a note
read-only, with a pencil in the top-right to edit it (Android back returns to
View). **Jump** is the multitasking switcher (swipe-to-close and reorder).
**New** starts a fresh note immediately. View is disabled until a note is open.

Only **one workspace** is used at a time; choosing a new folder replaces the
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
