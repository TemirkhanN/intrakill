package me.nasukhov.intrakill.storage.dao

import android.content.ContentValues
import me.nasukhov.intrakill.content.Attachment
import me.nasukhov.intrakill.content.Content
import me.nasukhov.intrakill.storage.MAX_CHUNK_SIZE
import me.nasukhov.intrakill.storage.asEnumeration
import net.sqlcipher.database.SQLiteDatabase
import java.io.FilterInputStream
import java.io.SequenceInputStream
import kotlin.use

class AttachmentRepository(private val dbResolver: () -> SQLiteDatabase) {
    private val db: SQLiteDatabase
        get() = dbResolver()

    fun addToEntry(entryId: String, attachments: List<Attachment>) {
        attachments.forEach { a ->
            require(!a.isPersisted) { "Attachment already exists" }
            db.execSQL(
                """
                    INSERT INTO attachment
                    (id, entry_id, preview, mime_type, hashsum, position)
                    VALUES (?,?,?,?,?,?)
                    """.trimIndent(),
                arrayOf(
                    a.id,
                    entryId,
                    a.preview,
                    a.mimeType,
                    a.hashsum,
                    a.position
                )
            )
            writeContent(a.id, a.content)
        }
    }

    fun deleteAttachments(attachmentsIds: List<String>) {
        val bindings = attachmentsIds.toTypedArray()
        db.execSQL("DELETE FROM attachment WHERE id IN (${attachmentsIds.placeholders()})", bindings)
    }

    fun listAttachments(entryId: String): List<Attachment> {
        val result = mutableListOf<Attachment>()

        db.rawQuery(
            "SELECT id, mime_type, preview, `size`, hashsum, position FROM attachment WHERE entry_id = ? ORDER BY position ASC",
            arrayOf(entryId)
        ).use { c ->
            while (c.moveToNext()) {
                val attachmentId = c.getString(c.getColumnIndexOrThrow("id"))
                result += Attachment(
                    id = attachmentId,
                    mimeType = c.getString(c.getColumnIndexOrThrow("mime_type")),
                    content = getContent(attachmentId),
                    preview = c.getBlob(c.getColumnIndexOrThrow("preview")),
                    size = c.getLong(c.getColumnIndexOrThrow("size")),
                    position = c.getInt(c.getColumnIndexOrThrow("position")),
                    hashsum = c.getBlob(c.getColumnIndexOrThrow("hashsum")),
                    isPersisted = true,
                )
            }
        }

        return result
    }

    private fun getContent(attachmentId: String): Content = Content {
        val cursor = db.rawQuery(
            "SELECT data FROM attachment_chunk WHERE attachment_id = ? ORDER BY sequence_number ASC",
            arrayOf(attachmentId)
        )

        val streamSequence = sequence {
            try {
                if (cursor.count == 0) {
                    throw IllegalStateException("Attachment $attachmentId is missing content entirely")
                }

                while (cursor.moveToNext()) {
                    val blob = cursor.getBlob(0)
                    yield(blob.inputStream())
                }
            } catch (e: Exception) {
                cursor.close()
                throw e
            }
        }

        val mergedStream = SequenceInputStream(streamSequence.asEnumeration())

        object : FilterInputStream(mergedStream) {
            private var isClosed = false

            override fun close() {
                if (isClosed) return
                try {
                    super.close()
                } finally {
                    cursor.close()
                    isClosed = true
                }
            }
        }
    }

    private fun writeContent(attachmentId: String, content: Content) {
        val buffer = ByteArray(MAX_CHUNK_SIZE)

        content.use { input ->
            var sequenceNumber = 0
            var totalBytesWritten = 0L

            while (true) {
                val bytesRead = input.read(buffer)
                if (bytesRead == -1) break

                val dataToInsert = if (bytesRead == MAX_CHUNK_SIZE) {
                    buffer
                } else {
                    buffer.copyOf(bytesRead)
                }

                val values = ContentValues().apply {
                    put("attachment_id", attachmentId)
                    put("sequence_number", sequenceNumber)
                    put("data", dataToInsert)
                }

                val rowId = db.insertOrThrow("attachment_chunk", null, values)
                if (rowId == -1L) throw IllegalStateException("Failed to insert chunk $sequenceNumber")

                sequenceNumber++
                totalBytesWritten += bytesRead
            }
        }
    }
}