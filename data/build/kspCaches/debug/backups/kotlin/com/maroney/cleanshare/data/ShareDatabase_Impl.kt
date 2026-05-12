package com.maroney.cleanshare.`data`

import androidx.room.InvalidationTracker
import androidx.room.RoomOpenDelegate
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.room.util.TableInfo
import androidx.room.util.TableInfo.Companion.read
import androidx.room.util.dropFtsSyncTriggers
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import javax.`annotation`.processing.Generated
import kotlin.Lazy
import kotlin.String
import kotlin.Suppress
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.MutableList
import kotlin.collections.MutableMap
import kotlin.collections.MutableSet
import kotlin.collections.Set
import kotlin.collections.mutableListOf
import kotlin.collections.mutableMapOf
import kotlin.collections.mutableSetOf
import kotlin.reflect.KClass

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class ShareDatabase_Impl : ShareDatabase() {
  private val _shareDao: Lazy<ShareDao> = lazy {
    ShareDao_Impl(this)
  }

  private val _linkMetadataDao: Lazy<LinkMetadataDao> = lazy {
    LinkMetadataDao_Impl(this)
  }

  protected override fun createOpenDelegate(): RoomOpenDelegate {
    val _openDelegate: RoomOpenDelegate = object : RoomOpenDelegate(3, "fbf989901af7fd36921ba00cf943093e", "272ff0ffd7df3b899e3ff60ec9ddf625") {
      public override fun createAllTables(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `share_history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `originalText` TEXT NOT NULL, `cleanedText` TEXT NOT NULL, `sharedAt` INTEGER NOT NULL, `notes` TEXT)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `link_metadata` (`shareRecordId` INTEGER NOT NULL, `title` TEXT, `thumbnailUrl` TEXT, `description` TEXT, `articleSnippet` TEXT, `contentType` TEXT NOT NULL, `fetchStatus` TEXT NOT NULL, PRIMARY KEY(`shareRecordId`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        connection.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'fbf989901af7fd36921ba00cf943093e')")
      }

      public override fun dropAllTables(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS `share_history`")
        connection.execSQL("DROP TABLE IF EXISTS `link_metadata`")
      }

      public override fun onCreate(connection: SQLiteConnection) {
      }

      public override fun onOpen(connection: SQLiteConnection) {
        internalInitInvalidationTracker(connection)
      }

      public override fun onPreMigrate(connection: SQLiteConnection) {
        dropFtsSyncTriggers(connection)
      }

      public override fun onPostMigrate(connection: SQLiteConnection) {
      }

      public override fun onValidateSchema(connection: SQLiteConnection): RoomOpenDelegate.ValidationResult {
        val _columnsShareHistory: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsShareHistory.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsShareHistory.put("originalText", TableInfo.Column("originalText", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsShareHistory.put("cleanedText", TableInfo.Column("cleanedText", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsShareHistory.put("sharedAt", TableInfo.Column("sharedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsShareHistory.put("notes", TableInfo.Column("notes", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysShareHistory: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesShareHistory: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoShareHistory: TableInfo = TableInfo("share_history", _columnsShareHistory, _foreignKeysShareHistory, _indicesShareHistory)
        val _existingShareHistory: TableInfo = read(connection, "share_history")
        if (!_infoShareHistory.equals(_existingShareHistory)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |share_history(com.maroney.cleanshare.data.ShareRecord).
              | Expected:
              |""".trimMargin() + _infoShareHistory + """
              |
              | Found:
              |""".trimMargin() + _existingShareHistory)
        }
        val _columnsLinkMetadata: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsLinkMetadata.put("shareRecordId", TableInfo.Column("shareRecordId", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsLinkMetadata.put("title", TableInfo.Column("title", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsLinkMetadata.put("thumbnailUrl", TableInfo.Column("thumbnailUrl", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsLinkMetadata.put("description", TableInfo.Column("description", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsLinkMetadata.put("articleSnippet", TableInfo.Column("articleSnippet", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsLinkMetadata.put("contentType", TableInfo.Column("contentType", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsLinkMetadata.put("fetchStatus", TableInfo.Column("fetchStatus", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysLinkMetadata: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesLinkMetadata: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoLinkMetadata: TableInfo = TableInfo("link_metadata", _columnsLinkMetadata, _foreignKeysLinkMetadata, _indicesLinkMetadata)
        val _existingLinkMetadata: TableInfo = read(connection, "link_metadata")
        if (!_infoLinkMetadata.equals(_existingLinkMetadata)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |link_metadata(com.maroney.cleanshare.data.LinkMetadata).
              | Expected:
              |""".trimMargin() + _infoLinkMetadata + """
              |
              | Found:
              |""".trimMargin() + _existingLinkMetadata)
        }
        return RoomOpenDelegate.ValidationResult(true, null)
      }
    }
    return _openDelegate
  }

  protected override fun createInvalidationTracker(): InvalidationTracker {
    val _shadowTablesMap: MutableMap<String, String> = mutableMapOf()
    val _viewTables: MutableMap<String, Set<String>> = mutableMapOf()
    return InvalidationTracker(this, _shadowTablesMap, _viewTables, "share_history", "link_metadata")
  }

  public override fun clearAllTables() {
    super.performClear(false, "share_history", "link_metadata")
  }

  protected override fun getRequiredTypeConverterClasses(): Map<KClass<*>, List<KClass<*>>> {
    val _typeConvertersMap: MutableMap<KClass<*>, List<KClass<*>>> = mutableMapOf()
    _typeConvertersMap.put(ShareDao::class, ShareDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(LinkMetadataDao::class, LinkMetadataDao_Impl.getRequiredConverters())
    return _typeConvertersMap
  }

  public override fun getRequiredAutoMigrationSpecClasses(): Set<KClass<out AutoMigrationSpec>> {
    val _autoMigrationSpecsSet: MutableSet<KClass<out AutoMigrationSpec>> = mutableSetOf()
    return _autoMigrationSpecsSet
  }

  public override fun createAutoMigrations(autoMigrationSpecs: Map<KClass<out AutoMigrationSpec>, AutoMigrationSpec>): List<Migration> {
    val _autoMigrations: MutableList<Migration> = mutableListOf()
    return _autoMigrations
  }

  public override fun shareDao(): ShareDao = _shareDao.value

  public override fun linkMetadataDao(): LinkMetadataDao = _linkMetadataDao.value
}
