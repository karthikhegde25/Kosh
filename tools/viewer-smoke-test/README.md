Runs the viewer page (app/src/main/assets/pdfviewer) in real Chromium with a stubbed `JWI` bridge and checks load, outline,
zoom, search, selection quads, highlight overlay alignment (incl. rotation), taps, themes, auto scroll and passwords.

    NODE_PATH=$(npm root -g) node smoke.js      # edit the Chromium path inside if needed; password for enc.pdf is "secret"
