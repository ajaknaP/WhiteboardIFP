package com.example.whiteboardifp.services

import android.content.Context
import com.example.whiteboardifp.models.WhiteboardModel
import com.google.gson.GsonBuilder
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WhiteboardStorage(
    private val context: Context
) {

    private val gson =
        GsonBuilder()
            .setPrettyPrinting()
            .create()

    // SAVE

    fun save(
        board: WhiteboardModel
    ): File {

        val name =
            "whiteboard_${
                SimpleDateFormat(
                    "yyyyMMdd_HHmmss",
                    Locale.US
                ).format(
                    Date()
                )
            }.json"

        val file =
            File(
                context.filesDir,
                name
            )

        file.writeText(
            gson.toJson(board)
        )

        return file
    }

    // LOAD

    fun load(
        fileName: String
    ): WhiteboardModel {

        val file =
            File(
                context.filesDir,
                fileName
            )

        return gson.fromJson(
            file.readText(),
            WhiteboardModel::class.java
        )
    }


    // LIST

    fun listFiles(): List<File> {

        return context.filesDir
            .listFiles { file ->

                file.extension.equals(
                    "json",
                    ignoreCase = true
                )
            }
            ?.sortedByDescending {
                it.lastModified()
            }
            ?: emptyList()
    }


    // DELETE


    fun delete(
        fileName: String
    ) {

        File(
            context.filesDir,
            fileName
        ).delete()
    }


    // EXPORT JSON


    fun exportJsonCopy(
        board: WhiteboardModel
    ): String {

        return gson.toJson(
            board
        )
    }
}