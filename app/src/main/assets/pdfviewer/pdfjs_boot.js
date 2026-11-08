import * as pdfjsLib from "./build/pdf.min.mjs";
globalThis.pdfjsLib = pdfjsLib;
pdfjsLib.GlobalWorkerOptions.workerSrc = new URL("./build/pdf.worker.min.mjs", import.meta.url).href;
