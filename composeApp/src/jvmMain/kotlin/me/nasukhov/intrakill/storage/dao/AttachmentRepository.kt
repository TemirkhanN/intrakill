package me.nasukhov.intrakill.storage.dao

import me.nasukhov.intrakill.content.Attachment
import me.nasukhov.intrakill.content.Content
import me.nasukhov.intrakill.storage.MAX_CHUNK_SIZE
import me.nasukhov.intrakill.storage.asEnumeration
import java.io.FilterInputStream
import java.io.SequenceInputStream
import java.sql.Connection
import kotlin.collections.forEach
import kotlin.use

class AttachmentRepository(private val dbResolver: () -> Connection) {
    private val db: Connection
        get() = dbResolver()

    private companion object {
        const val FIND_BY_ID = """
            SELECT id, mime_type, `size`, preview, hashsum, position FROM attachment
            WHERE entry_id = ? ORDER BY position ASC
        """
        const val REMOVE_DELETED_ATTACHMENTS = """
            DELETE FROM attachment
            WHERE entry_id=? AND id NOT IN (%s)
        """
        const val ADD_ATTACHMENT_TO_ENTRY = """
            INSERT INTO attachment(
                id, entry_id, preview, mime_type,
                hashsum, `size`, position
            ) VALUES (?,?,?,?,?,?,?)
        """
        const val DELETE_ALL_BY_ENTRY_ID = "DELETE FROM attachment WHERE entry_id=?"
        const val SET_ATTACHMENT_POSITION = "UPDATE attachment SET position=? WHERE id=?"
        const val FIX_POSITION_GAPS = """
            UPDATE attachment 
            SET position = (
                SELECT COUNT(*)
                FROM attachment a2
                WHERE a2.entry_id = attachment.entry_id AND a2.position < attachment.position
            )
            WHERE entry_id=?
        """
        const val FETCH_CONTENT_CHUNKS = "SELECT data FROM attachment_chunk WHERE attachment_id = ? ORDER BY sequence_number ASC"
        const val DELETE_CONTENT = "DELETE FROM attachment_chunk WHERE attachment_id = ?"
        const val WRITE_CONTENT_CHUNK = "INSERT INTO attachment_chunk (attachment_id, sequence_number, data) VALUES (?, ?, ?)"
    }

    fun getByEntryId(entryId: String): List<Attachment> {
        val result = mutableListOf<Attachment>()

        db.prepareStatement(FIND_BY_ID).use { stmt ->
            stmt.setString(1, entryId)
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    val attachmentId = rs.getString("id")
                    result.add(
                        Attachment(
                            id = attachmentId,
                            size = rs.getLong("size"),
                            mimeType = rs.getString("mime_type"),
                            content = getContent(attachmentId),
                            preview = rs.getBytes("preview"),
                            position = rs.getInt("position"),
                            hashsum = rs.getBytes("hashsum"),
                            isPersisted = true,
                        )
                    )
                }
            }
        }

        return result
    }

    fun addToEntry(entryId: String, attachments: List<Attachment>) {
        if (attachments.isEmpty()) return

        db.prepareStatement(ADD_ATTACHMENT_TO_ENTRY).use { stmt ->
            attachments.forEach { a ->
                stmt.setString(1, a.id)
                stmt.setString(2, entryId)
                stmt.setBytes(3, a.preview)
                stmt.setString(4, a.mimeType)
                stmt.setBytes(5, a.hashsum)
                stmt.setLong(6, a.size)
                stmt.setInt(7, a.position)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }

        // Save attachment's content
        for (a in attachments) {
            writeContent(a.id, a.content)
        }
    }

    fun deleteExcluding(entryId: String, excludedAttachmentsIds: Set<String>) {
        if (excludedAttachmentsIds.isEmpty()) {
            db.prepareStatement(DELETE_ALL_BY_ENTRY_ID).use {
                it.setString(1, entryId)
                it.executeUpdate()
            }
            return
        }

        db.prepareStatement(REMOVE_DELETED_ATTACHMENTS.format(excludedAttachmentsIds.placeholders())).use {
            it.setString(1, entryId)
            excludedAttachmentsIds.bind(it, 2)
            it.executeUpdate()
        }
    }

    fun updatePositions(changed: List<Attachment>) {
        if (changed.isEmpty()) return

        db.prepareStatement(SET_ATTACHMENT_POSITION).use { stmt ->
            changed.forEach {
                stmt.setInt(1, it.position)
                stmt.setString(2, it.id)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }

    fun normalizePositions(entryId: String) {
        db.prepareStatement(FIX_POSITION_GAPS).use {
            it.setString(1, entryId)
            it.executeUpdate()
        }
    }

    private fun getContent(attachmentId: String): Content = Content {
        val stmt = db.prepareStatement(FETCH_CONTENT_CHUNKS)
        stmt.setString(1, attachmentId)
        val rs = stmt.executeQuery()

        val streamSequence = sequence {
            var hasData = false
            while (rs.next()) {
                hasData = true
                yield(rs.getBinaryStream(1))
            }
            if (!hasData) throw IllegalStateException("Attachment $attachmentId is missing content entirely")
        }

        // Wrap sequence stream in filterInput so that result set is closed when the stream gets closed
        object : FilterInputStream(SequenceInputStream(streamSequence.asEnumeration())) {
            override fun close() {
                try {
                    super.close()
                } finally {
                    rs.close()
                    stmt.close()
                }
            }
        }
    }

    private fun writeContent(attachmentId: String, content: Content) {
        val buffer = ByteArray(MAX_CHUNK_SIZE)

        db.prepareStatement(DELETE_CONTENT).use { stmt ->
            stmt.setString(1, attachmentId)
            stmt.executeUpdate()
        }

        db.prepareStatement(WRITE_CONTENT_CHUNK).use { stmt ->
            content.use { input ->
                var sequence = 0

                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break

                    val chunk = if (read == MAX_CHUNK_SIZE) buffer else buffer.copyOf(read)

                    stmt.setString(1, attachmentId)
                    stmt.setInt(2, sequence++)
                    stmt.setBytes(3, chunk)
                    stmt.executeUpdate()
                }
            }
        }
    }
}