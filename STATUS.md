# Kosh: what is built

## App shell
- One shared top row on every tab: logo, app name, settings
- App settings contain only the theme and the privacy policy; every other setting lives in its own tab

## Scanner (sub-tabs: Scan, Edit)
- Scan: camera, import, recent scans, scan settings (default enhancement, paper size, quality, clear drafts)
- Edit: all photos of the current scan in one row, each with Rotate, Crop and Delete (More: enhance, rotate left, move, duplicate), name field, Save as PDF
- Camera capture (CameraX, permission flow, flash), import from gallery
- Manual four-corner crop with perspective correction; filters: Original, Document, Grayscale, Black and white, Color
- PDF step: "Images as pages" or "Images on pages" (white A4/Letter pages), paper size, orientation, quality
- JPEG-based PDF writer (small files), verified by reopening; success screen with Open, Share, Rename, Scan more
- Recent scans list (open, share, rename, delete)

## PDF Home
- All PDFs and Folders tabs; first-page preview at the left of every PDF; Share / Rename / Delete in a 3-dots menu

## Home tab
- Press and hold a PDF to pick it (blue row with a tick); tap more to add or remove. The three tabs are replaced by Share, Merge (two or more picked, opens the Merge tool with them in the order picked) and Delete (asks first). Back or the X leaves picking. Rename box no longer hides the end of the name (file type shown under the box)

## Tools tab (was the Scanner tab)
- Grid of tools. Image to PDF is the existing scanner (Scan + Edit, camera, import, filters, export); the others open one shared screen: choose file(s), set options, pick where to save, progress with cancel, result with Open/Share
- PDF to images (JPG/PNG, 100-300 dpi, all or chosen pages, saved into a folder you pick), Merge (reorder before joining), Split (page ranges, every page, every N pages; one file each into a folder), Extract pages, Rotate pages (chosen or all), Delete pages, Add page numbers (4 styles, 6 positions, 3 sizes, first number, skip first page)
- Pages are picked by tapping thumbnails or typing ranges like 1-3, 5
- Split can save page ranges as one file instead of one file per range. Merge, Split, Extract, Rotate, Delete, Reorder and Add page numbers now use PdfBox-Android (Apache 2.0, `com.tom-roush:pdfbox-android`, started in KoshApplication): pages are moved/removed/turned inside the PDF, so text, fonts, links and bookmarks are kept and files don't grow. Page numbers are real text, placed correctly on turned pages (maths checked by rendering rotated PDFs; see tools/viewer-smoke-test/pagenumber_check.py)
- Encrypt PDF (AES 128 or 256, password + confirm, allow print/copy/edit) and Decrypt PDF (needs the password; also removes restrictions from PDFs that open without one). Other tools still can't open password-protected PDFs: unlock them with Decrypt first
- PDF to images still uses Android's PdfRenderer; Compress uses pdf-lib
- Reorder pages (press, hold and drag; PdfBox) and Compress PDF (Light/Balanced/Strong; shows before/after size, saves nothing if it can't get smaller; pdf-lib in a private hidden WebView, MIT) keep text selectable
- Word to PDF (.docx): parsed on the device (text styling, headings, lists, tables, pictures; no headers/footers/text boxes/EMF/WMF), drawn with PdfDocument, then placed on A4 sheets with PdfBox: 1/2/4/6/9/16 pages per sheet, portrait or landscape, optional border with 0.5-8 pt width, live sheet preview
- Home tab lists .docx/.xlsx/.pptx beside PDFs (tile icon instead of page preview). Tap = convert on the device (cancelable dialog, cached) and open in the normal PDF reader. 3-dot menu has "Save as PDF" (writes Name.pdf next to the original, never overwrites, "Name (1).pdf" if taken). Converters: Word reuses the Word-to-PDF renderer; Excel = each visible sheet as a table on A4 landscape (cached values, no formula calc, no merges/charts, wide sheets split in column bands); PowerPoint = one page per slide (text, shapes fill, pictures, simple tables, layout placeholder positions; no charts/SmartArt/rotation/gradients). Merge is PDFs-only. Android code uncompiled here (no SDK); parsers covered by JVM tests (70 passing).
- Home search: magnifier button left of the tabs opens a search bar (keyboard up) that filters all files by name/folder words; Back or the arrow closes it and returns to All.
- Pages per sheet tool (PDF): 2/4/6/9/16 pages per A4 sheet, portrait or landscape, optional border 0.5-8 pt, live preview (reuses the N-up engine).
- Notes Read aloud: speaker icon in the note's top bar -> dialog with the phone's installed text-to-speech voices (Google etc., device language first) and a 0.5-2x speed slider; while reading the word being spoken is highlighted, the note scrolls to keep it in view, the note/title/checklists/images are read-only and a bottom bar offers pause/resume, stop, speed and voice. Markdown signs, bullets and picture lines are not spoken. Voices that don't report words highlight the whole sentence. Pause restarts from the current word (Android TTS has no pause). Needs the <queries> TTS_SERVICE entry in the manifest (added).
- Not built: watermark, password-protected PDFs
- Notes: pictures are inserted at the caret as their own line (`![image](kosh-image:<file>)` in the note text), so they sit between paragraphs; old attachments are moved into the text, at the top, the first time a note is opened. Exports and list previews skip the image lines; PDF export draws the pictures in place

## About
- Settings > About Kosh: logo, name, developer (Karthik Hegde), description, GPL-3.0 notice, third-party credits, GitHub link (github.com/karthikhegde25/Kosh)

## Reader (pdf.js viewer, built from PdfReaderPro_-_PDF_Viewer_Behavioral_Spec.md)
- Opens in a locked-down WebView running a bundled pdf.js 5.6 (legacy build, `app/src/main/assets/pdfviewer`); no network: the page only sees its own assets and the opened file
- Text search with match count and next/previous, text selection, outline (table of contents), attachments (save to a chosen location), document properties
- Highlights: select text, tap a colour (or "Highlight" in the selection menu); tap a highlight to recolour, add a note, copy or delete; list of app highlights plus highlights already stored in the PDF (read-only). Stored beside the PDF, the file is never changed
- Password-protected PDFs: password prompt, optional remembered password (AES-GCM, Android Keystore)
- Fast-scroll handle on the right edge (like Google Drive; bottom edge when scrolling sideways): shows while scrolling, drag it to jump, a bubble shows the page; the page number is shown under the title
- Pages are drawn at up to 2x pixel density and up to ~8M pixels each (pdf.js's extra screen-area cap is off), so zoomed pages stay sharp; the page cache shrinks when pages are big. Small local patch in web/pdf_viewer.mjs (koshCacheSize hook)
- Pinch zoom around the pinch point, double-tap zoom, zoom presets, fit width/page, rotate, vertical/horizontal scrolling, snap to pages, lock horizontal position, tap-to-turn, page slider, auto scroll
- Reading themes (light/dark/sepia/black), brightness, keep screen on, orientation lock, print (original file), share, bookmarks, resume page and zoom per file
- Doodle: draw with 8 colours and 3 pen sizes, eraser (removes a stroke instantly), undo. Save (top right) writes a NEW copy next to the original (name (doodle).pdf, or a save-as picker if that folder isn't writable) as standard Ink annotations, so the original is never changed and text stays selectable
- Not yet built: signatures, text editor tools, saving highlights into a copy of the PDF, saving an unlocked copy, wrapped/spread layouts
- The previous native reader (PdfRenderer, pen/highlighter annotations, annotated export) is still in the source (`ui/screens/PdfReaderScreen.kt`, `reader/`) but no longer reachable; switch `Routes.READER` in `AppNavigation.kt` back to use it
- Browser-level smoke test of the viewer page: `tools/viewer-smoke-test/` (needs Node + Playwright + Chromium)

## Reader (previous native reader, no longer used)
- Continuous or one-page layout, zoom/pan, fit width/page, rotate view, jump to page, reading position
- Bookmarks (with optional label), annotations (pen, highlighter, note, eraser, undo), annotation list
- Export annotated copy (original is never modified), print, share, document info
- Reading theme (system/light/dark), invert page colours, auto-scroll

## Notes
- Markdown notes, search, sort, pin, archive, trash, export to PDF / .md / .txt
- Images can be attached to a note (shown in a row, included in PDF export and in Share as PDF; never in .md or .txt)

## Not built (and why)
- Automatic edge detection and OCR: not included; corners are set by hand
- Drag-and-drop page reordering (move earlier/later buttons instead); horizontal page-swipe layout
- Drafts do not survive the app being killed
