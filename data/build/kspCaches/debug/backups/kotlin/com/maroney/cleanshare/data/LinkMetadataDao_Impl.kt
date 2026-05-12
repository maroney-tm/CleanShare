package com.maroney.cleanshare.`data`

import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.EntityUpsertAdapter
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
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class LinkMetadataDao_Impl(
  __db: RoomDatabase,
) : LinkMetadataDao {
  private val __db: RoomDatabase

  private val __upsertAdapterOfLinkMetadata: EntityUpsertAdapter<LinkMetadata>

  private val __converters: Converters = Converters()
  init {
    this.__db = __db
    this.__upsertAdapterOfLinkMetadata = EntityUpsertAdapter<LinkMetadata>(object : EntityInsertAdapter<LinkMetadata>() {
      protected override fun createQuery(): String = "INSERT INTO `link_metadata` (`shareRecordId`,`title`,`thumbnailUrl`,`description`,`articleSnippet`,`contentType`,`fetchStatus`) VALUES (?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: LinkMetadata) {
        statement.bindLong(1, entity.shareRecordId)
        val _tmpTitle: String? = entity.title
        if (_tmpTitle == null) {
          statement.bindNull(2)
        } else {
          statement.bindText(2, _tmpTitle)
        }
        val _tmpThumbnailUrl: String? = entity.thumbnailUrl
        if (_tmpThumbnailUrl == null) {
          statement.bindNull(3)
        } else {
          statement.bindText(3, _tmpThumbnailUrl)
        }
        val _tmpDescription: String? = entity.description
        if (_tmpDescription == null) {
          statement.bindNull(4)
        } else {
          statement.bindText(4, _tmpDescription)
        }
        val _tmpArticleSnippet: String? = entity.articleSnippet
        if (_tmpArticleSnippet == null) {
          statement.bindNull(5)
        } else {
          statement.bindText(5, _tmpArticleSnippet)
        }
        val _tmp: String = __converters.fromContentType(entity.contentType)
        statement.bindText(6, _tmp)
        val _tmp_1: String = __converters.fromFetchStatus(entity.fetchStatus)
        statement.bindText(7, _tmp_1)
      }
    }, object : EntityDeleteOrUpdateAdapter<LinkMetadata>() {
      protected override fun createQuery(): String = "UPDATE `link_metadata` SET `shareRecordId` = ?,`title` = ?,`thumbnailUrl` = ?,`description` = ?,`articleSnippet` = ?,`contentType` = ?,`fetchStatus` = ? WHERE `shareRecordId` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: LinkMetadata) {
        statement.bindLong(1, entity.shareRecordId)
        val _tmpTitle: String? = entity.title
        if (_tmpTitle == null) {
          statement.bindNull(2)
        } else {
          statement.bindText(2, _tmpTitle)
        }
        val _tmpThumbnailUrl: String? = entity.thumbnailUrl
        if (_tmpThumbnailUrl == null) {
          statement.bindNull(3)
        } else {
          statement.bindText(3, _tmpThumbnailUrl)
        }
        val _tmpDescription: String? = entity.description
        if (_tmpDescription == null) {
          statement.bindNull(4)
        } else {
          statement.bindText(4, _tmpDescription)
        }
        val _tmpArticleSnippet: String? = entity.articleSnippet
        if (_tmpArticleSnippet == null) {
          statement.bindNull(5)
        } else {
          statement.bindText(5, _tmpArticleSnippet)
        }
        val _tmp: String = __converters.fromContentType(entity.contentType)
        statement.bindText(6, _tmp)
        val _tmp_1: String = __converters.fromFetchStatus(entity.fetchStatus)
        statement.bindText(7, _tmp_1)
        statement.bindLong(8, entity.shareRecordId)
      }
    })
  }

  public override suspend fun upsert(metadata: LinkMetadata): Unit = performSuspending(__db, false, true) { _connection ->
    __upsertAdapterOfLinkMetadata.upsert(_connection, metadata)
  }

  public override fun observeAll(): Flow<List<LinkMetadata>> {
    val _sql: String = "SELECT * FROM link_metadata ORDER BY shareRecordId DESC"
    return createFlow(__db, false, arrayOf("link_metadata")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfShareRecordId: Int = getColumnIndexOrThrow(_stmt, "shareRecordId")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfThumbnailUrl: Int = getColumnIndexOrThrow(_stmt, "thumbnailUrl")
        val _columnIndexOfDescription: Int = getColumnIndexOrThrow(_stmt, "description")
        val _columnIndexOfArticleSnippet: Int = getColumnIndexOrThrow(_stmt, "articleSnippet")
        val _columnIndexOfContentType: Int = getColumnIndexOrThrow(_stmt, "contentType")
        val _columnIndexOfFetchStatus: Int = getColumnIndexOrThrow(_stmt, "fetchStatus")
        val _result: MutableList<LinkMetadata> = mutableListOf()
        while (_stmt.step()) {
          val _item: LinkMetadata
          val _tmpShareRecordId: Long
          _tmpShareRecordId = _stmt.getLong(_columnIndexOfShareRecordId)
          val _tmpTitle: String?
          if (_stmt.isNull(_columnIndexOfTitle)) {
            _tmpTitle = null
          } else {
            _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          }
          val _tmpThumbnailUrl: String?
          if (_stmt.isNull(_columnIndexOfThumbnailUrl)) {
            _tmpThumbnailUrl = null
          } else {
            _tmpThumbnailUrl = _stmt.getText(_columnIndexOfThumbnailUrl)
          }
          val _tmpDescription: String?
          if (_stmt.isNull(_columnIndexOfDescription)) {
            _tmpDescription = null
          } else {
            _tmpDescription = _stmt.getText(_columnIndexOfDescription)
          }
          val _tmpArticleSnippet: String?
          if (_stmt.isNull(_columnIndexOfArticleSnippet)) {
            _tmpArticleSnippet = null
          } else {
            _tmpArticleSnippet = _stmt.getText(_columnIndexOfArticleSnippet)
          }
          val _tmpContentType: ContentType
          val _tmp: String
          _tmp = _stmt.getText(_columnIndexOfContentType)
          _tmpContentType = __converters.toContentType(_tmp)
          val _tmpFetchStatus: FetchStatus
          val _tmp_1: String
          _tmp_1 = _stmt.getText(_columnIndexOfFetchStatus)
          _tmpFetchStatus = __converters.toFetchStatus(_tmp_1)
          _item = LinkMetadata(_tmpShareRecordId,_tmpTitle,_tmpThumbnailUrl,_tmpDescription,_tmpArticleSnippet,_tmpContentType,_tmpFetchStatus)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun getById(shareRecordId: Long): Flow<LinkMetadata?> {
    val _sql: String = "SELECT * FROM link_metadata WHERE shareRecordId = ?"
    return createFlow(__db, false, arrayOf("link_metadata")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, shareRecordId)
        val _columnIndexOfShareRecordId: Int = getColumnIndexOrThrow(_stmt, "shareRecordId")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfThumbnailUrl: Int = getColumnIndexOrThrow(_stmt, "thumbnailUrl")
        val _columnIndexOfDescription: Int = getColumnIndexOrThrow(_stmt, "description")
        val _columnIndexOfArticleSnippet: Int = getColumnIndexOrThrow(_stmt, "articleSnippet")
        val _columnIndexOfContentType: Int = getColumnIndexOrThrow(_stmt, "contentType")
        val _columnIndexOfFetchStatus: Int = getColumnIndexOrThrow(_stmt, "fetchStatus")
        val _result: LinkMetadata?
        if (_stmt.step()) {
          val _tmpShareRecordId: Long
          _tmpShareRecordId = _stmt.getLong(_columnIndexOfShareRecordId)
          val _tmpTitle: String?
          if (_stmt.isNull(_columnIndexOfTitle)) {
            _tmpTitle = null
          } else {
            _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          }
          val _tmpThumbnailUrl: String?
          if (_stmt.isNull(_columnIndexOfThumbnailUrl)) {
            _tmpThumbnailUrl = null
          } else {
            _tmpThumbnailUrl = _stmt.getText(_columnIndexOfThumbnailUrl)
          }
          val _tmpDescription: String?
          if (_stmt.isNull(_columnIndexOfDescription)) {
            _tmpDescription = null
          } else {
            _tmpDescription = _stmt.getText(_columnIndexOfDescription)
          }
          val _tmpArticleSnippet: String?
          if (_stmt.isNull(_columnIndexOfArticleSnippet)) {
            _tmpArticleSnippet = null
          } else {
            _tmpArticleSnippet = _stmt.getText(_columnIndexOfArticleSnippet)
          }
          val _tmpContentType: ContentType
          val _tmp: String
          _tmp = _stmt.getText(_columnIndexOfContentType)
          _tmpContentType = __converters.toContentType(_tmp)
          val _tmpFetchStatus: FetchStatus
          val _tmp_1: String
          _tmp_1 = _stmt.getText(_columnIndexOfFetchStatus)
          _tmpFetchStatus = __converters.toFetchStatus(_tmp_1)
          _result = LinkMetadata(_tmpShareRecordId,_tmpTitle,_tmpThumbnailUrl,_tmpDescription,_tmpArticleSnippet,_tmpContentType,_tmpFetchStatus)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteByShareRecordId(shareRecordId: Long) {
    val _sql: String = "DELETE FROM link_metadata WHERE shareRecordId = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, shareRecordId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteAll() {
    val _sql: String = "DELETE FROM link_metadata"
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
