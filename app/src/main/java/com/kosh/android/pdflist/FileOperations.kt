package com.kosh.android.pdflist

import com.kosh.android.util.buildRenamedFileName
import java.io.File

sealed interface RenameOutcome {
    data class Renamed(val file: File) : RenameOutcome
    data class Unchanged(val file: File) : RenameOutcome
    data object InvalidName : RenameOutcome
    data object NameExists : RenameOutcome
    data object NotFound : RenameOutcome
    data object Failed : RenameOutcome
}

sealed interface DeleteOutcome {
    data object Deleted : DeleteOutcome
    data object AlreadyGone : DeleteOutcome
    data object Failed : DeleteOutcome
}

/** Plain java.io rename/delete with no Android dependencies, so it can be unit-tested on the JVM. */
object FileOperations {

    /** Renames [src] to [baseName] (extension kept). Never overwrites an existing file. */
    fun rename(src: File, baseName: String): RenameOutcome {
        val newName = buildRenamedFileName(baseName, src.extension) ?: return RenameOutcome.InvalidName
        if (!src.exists()) return RenameOutcome.NotFound
        val dst = File(src.absoluteFile.parentFile, newName)
        if (dst.name == src.name) return RenameOutcome.Unchanged(src)
        if (dst.exists() && dst.canonicalFile != src.canonicalFile) return RenameOutcome.NameExists
        return if (src.renameTo(dst)) RenameOutcome.Renamed(dst) else RenameOutcome.Failed
    }

    fun delete(file: File): DeleteOutcome = when {
        !file.exists() -> DeleteOutcome.AlreadyGone
        file.delete() -> DeleteOutcome.Deleted
        else -> DeleteOutcome.Failed
    }
}
