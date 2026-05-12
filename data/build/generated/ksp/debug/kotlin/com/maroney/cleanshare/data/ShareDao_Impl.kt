package com.maroney.cleanshare.`data`

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import javax.`annotation`.processing.Generated
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class ShareDao_Impl(
  __db: RoomDatabase,
) : ShareDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfShareRecord: EntityInsertAdapter<ShareRecord>
  init {
    this.__db = __db
    this.__insertAdapterOfShareRecord = object : EntityInsertAdapter<ShareRecord>() {
      protected override fun createQuery(): String = "INSERT OR ABORT INTO `share_history` (`id`,`originalText`,`cleanedText`,`sharedAt`,`notes`) VALUES (nullif(?, 0),?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: ShareRecord) {
        statement.bindLong(1, entity.id)
        statement.bindText(2, entity.originalText)
        statement.bindText(3, entity.cleanedText)
        statement.bindLong(4, entity.sharedAt)
        val _tmpNotes: String? = entity.notes
        if (_tmpNotes == null) {
          statement.bindNull(5)
        } else {
          statement.bindText(5, _tmpNotes)
        }
      }
    }
  }

  public override suspend fun insert(record: ShareRecord): Long = performSuspending(__db, false, true) { _connection ->
    val _result: Long = __insertAdapterOfShareRecord.insertAndReturnId(_connection, record)
    _result
  }

  public override fun getAll(): Flow<List<ShareRecord>> {
    val _sql: String = "SELECT * FROM share_history ORDER BY sharedAt DESC"
    return createFlow(__db, false, arrayOf("share_history")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfOriginalText: Int = getColumnIndexOrThrow(_stmt, "originalText")
        val _columnIndexOfCleanedText: Int = getColumnIndexOrThrow(_stmt, "cleanedText")
        val _columnIndexOfSharedAt: Int = getColumnIndexOrThrow(_stmt, "sharedAt")
        val _columnIndexOfNotes: Int = getColumnIndexOrThrow(_stmt, "notes")
        val _result: MutableList<ShareRecord> = mutableListOf()
        while (_stmt.step()) {
          val _item: ShareRecord
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpOriginalText: String
          _tmpOriginalText = _stmt.getText(_columnIndexOfOriginalText)
          val _tmpCleanedText: String
          _tmpCleanedText = _stmt.getText(_columnIndexOfCleanedText)
          val _tmpSharedAt: Long
          _tmpSharedAt = _stmt.getLong(_columnIndexOfSharedAt)
          val _tmpNotes: String?
          if (_stmt.isNull(_columnIndexOfNotes)) {
            _tmpNotes = null
          } else {
            _tmpNotes = _stmt.getText(_columnIndexOfNotes)
          }
          _item = ShareRecord(_tmpId,_tmpOriginalText,_tmpCleanedText,_tmpSharedAt,_tmpNotes)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun getById(id: Long): Flow<ShareRecord?> {
    val _sql: String = "SELECT * FROM share_history WHERE id = ?"
    return createFlow(__db, false, arrayOf("share_history")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, id)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfOriginalText: Int = getColumnIndexOrThrow(_stmt, "originalText")
        val _columnIndexOfCleanedText: Int = getColumnIndexOrThrow(_stmt, "cleanedText")
        val _columnIndexOfSharedAt: Int = getColumnIndexOrThrow(_stmt, "sharedAt")
        val _columnIndexOfNotes: Int = getColumnIndexOrThrow(_stmt, "notes")
        val _result: ShareRecord?
        if (_stmt.step()) {
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpOriginalText: String
          _tmpOriginalText = _stmt.getText(_columnIndexOfOriginalText)
          val _tmpCleanedText: String
          _tmpCleanedText = _stmt.getText(_columnIndexOfCleanedText)
          val _tmpSharedAt: Long
          _tmpSharedAt = _stmt.getLong(_columnIndexOfSharedAt)
          val _tmpNotes: String?
          if (_stmt.isNull(_columnIndexOfNotes)) {
            _tmpNotes = null
          } else {
            _tmpNotes = _stmt.getText(_columnIndexOfNotes)
          }
          _result = ShareRecord(_tmpId,_tmpOriginalText,_tmpCleanedText,_tmpSharedAt,_tmpNotes)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun updateNotes(id: Long, notes: String?) {
    val _sql: String = "UPDATE share_history SET notes = ? WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        if (notes == null) {
          _stmt.bindNull(_argIndex)
        } else {
          _stmt.bindText(_argIndex, notes)
        }
        _argIndex = 2
        _stmt.bindLong(_argIndex, id)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteById(id: Long) {
    val _sql: String = "DELETE FROM share_history WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, id)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteAll() {
    val _sql: String = "DELETE FROM share_history"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
