package com.kosh.android.navigation

import android.net.Uri

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home" // the main screen with the Home and Scanner tabs
    const val SETTINGS = "settings"
    const val PRIVACY = "privacy"
    const val ABOUT = "about"
    const val READER = "reader/{uri}"
    const val NOTE = "note/{id}"
    const val CAMERA = "camera"
    const val CROP = "crop/{pageId}"
    const val ENHANCE = "enhance/{pageId}"
    const val TOOL = "tool/{toolId}"
    const val IMAGE_TOOL = "imgtool/{imgToolId}"
    const val EXPORT = "export"
    const val SUCCESS = "export_success"

    /** Pass [NEW_NOTE] to start a note; it is only saved once something has been written. */
    fun note(id: String): String = "note/$id"
    const val NEW_NOTE = "new"

    /** Start a note inside a folder: [NEW_NOTE_IN_FOLDER] followed by the folder id. */
    const val NEW_NOTE_IN_FOLDER = "new@"

    fun tool(toolId: String) = "tool/$toolId"
    fun imageTool(toolId: String) = "imgtool/$toolId"
    fun crop(pageId: String) = "crop/$pageId"
    fun enhance(pageId: String) = "enhance/$pageId"

    fun reader(uri: Uri): String = "reader/${Uri.encode(uri.toString())}"
}
