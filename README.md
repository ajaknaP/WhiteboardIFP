# IFP Whiteboard — Native Android Kotlin/XML

A fully offline whiteboard assignment implementation for 65"–85" Interactive Flat Panels.

## Features

- Native Android using Kotlin + XML.
- Landscape-only IFP experience.
- Custom `Canvas`, `Paint`, and `Path` drawing.
- Touch and stylus-compatible pointer input through `MotionEvent`.
- Freehand strokes with stored points, color, and width.
- Adjustable stroke width.
- Six-color palette.
- Eraser with continuous touch-drag hit testing.
- Rectangle, circle, line and 4+ sided polygon.
- Text insertion with an Android `EditText` editor.
- Text is stored as structured data and rendered with Canvas.
- Full-text deletion and eraser hit testing.
- Undo/redo (50 snapshots).
- Local JSON save/load using Gson.
- Filename format `whiteboard_yyyyMMdd_HHmmss.json`.
- PNG export.
- MVVM + `StateFlow`.
- No network permission and no internet dependency.
- App-specific internal storage, so no runtime storage permission is required.

## Architecture

```
models/
    PointModel
    StrokeModel
    ShapeModel
    TextModel
    WhiteboardModel

services/
    WhiteboardStorage

views/
    WhiteboardCanvasView

viewmodels/
    WhiteboardViewModel

MainActivity
```

### Data flow

`Touch -> WhiteboardCanvasView -> WhiteboardViewModel -> StateFlow<WhiteboardModel> -> Canvas redraw`

Persistence:

`WhiteboardModel -> Gson -> /data/data/<package>/files/*.json`

## JSON format

```json
{
  "version": 1,
  "canvasWidth": 1600,
  "canvasHeight": 800,
  "strokes": [
    {
      "points": [
        {"x": 10.0, "y": 20.0},
        {"x": 15.0, "y": 25.0}
      ],
      "color": "#EF4444",
      "width": 5.0
    }
  ],
  "shapes": [
    {
      "type": "RECTANGLE",
      "left": 50.0,
      "top": 50.0,
      "right": 300.0,
      "bottom": 180.0,
      "color": "#2563EB",
      "strokeWidth": 5.0,
      "sides": 5
    }
  ],
  "texts": [
    {
      "text": "Hello IFP!",
      "x": 300.0,
      "y": 400.0,
      "color": "#111827",
      "size": 24.0,
      "width": 420.0,
      "height": 80.0
    }
  ]
}
```

## IFP deployment

1. Open the project in Android Studio.
2. Use JDK 17.
3. Sync Gradle.
4. Connect the IFP using USB debugging or install the generated APK using the panel's package installer.
5. The application locks itself to landscape.
6. For a 65"–85" panel, use Android's display scaling so the 72dp toolbar and 52–100dp controls remain easy to touch.

## Offline behavior

There are no network APIs, INTERNET permissions, remote databases, or cloud services. JSON is written to the application's internal files directory.

## Important implementation notes

### Eraser
The eraser is model-based rather than bitmap-only: it continuously checks the touch location against stroke points and removes touched portions. This makes erasing persistable in JSON.

For text and shapes, the eraser performs hit testing and removes the selected object. This satisfies full-object deletion while keeping the JSON representation structured.

### Scaling
Stroke and object coordinates are stored in view coordinates. If the saved board is restored onto a differently sized IFP, the current implementation preserves the saved coordinates. For a production version, normalize coordinates to 0..1 or apply a canvas scale transform using `canvasWidth/canvasHeight`.

### Text editing
Text is entered with a temporary Android `EditText`, then committed into `TextModel` and rendered on the custom canvas.

## Suggested production improvements

- Normalize coordinates for different IFP resolutions.
- Add object selection/move/resize handles.
- Add a saved-board thumbnail gallery.
- Add JSON import/export using the Android Storage Access Framework.
- Add a true per-pixel bitmap eraser layer if pixel-perfect erasing of text is required.
- Add multi-touch zoom/pan with a transformation matrix.
