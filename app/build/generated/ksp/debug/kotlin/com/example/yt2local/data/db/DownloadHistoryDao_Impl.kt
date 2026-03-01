package com.example.yt2local.`data`.db

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import javax.`annotation`.processing.Generated
import kotlin.Boolean
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
public class DownloadHistoryDao_Impl(
  __db: RoomDatabase,
) : DownloadHistoryDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfDownloadHistoryEntity: EntityInsertAdapter<DownloadHistoryEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfDownloadHistoryEntity = object : EntityInsertAdapter<DownloadHistoryEntity>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `download_history` (`id`,`file_name`,`platform`,`is_audio`,`timestamp`,`media_uri`) VALUES (nullif(?, 0),?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: DownloadHistoryEntity) {
        statement.bindLong(1, entity.id)
        statement.bindText(2, entity.fileName)
        statement.bindText(3, entity.platform)
        val _tmp: Int = if (entity.isAudio) 1 else 0
        statement.bindLong(4, _tmp.toLong())
        statement.bindLong(5, entity.timestamp)
        val _tmpMediaUri: String? = entity.mediaUri
        if (_tmpMediaUri == null) {
          statement.bindNull(6)
        } else {
          statement.bindText(6, _tmpMediaUri)
        }
      }
    }
  }

  public override suspend fun insert(item: DownloadHistoryEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfDownloadHistoryEntity.insert(_connection, item)
  }

  public override fun getRecent(): Flow<List<DownloadHistoryEntity>> {
    val _sql: String = "SELECT * FROM download_history ORDER BY timestamp DESC LIMIT 10"
    return createFlow(__db, false, arrayOf("download_history")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfFileName: Int = getColumnIndexOrThrow(_stmt, "file_name")
        val _columnIndexOfPlatform: Int = getColumnIndexOrThrow(_stmt, "platform")
        val _columnIndexOfIsAudio: Int = getColumnIndexOrThrow(_stmt, "is_audio")
        val _columnIndexOfTimestamp: Int = getColumnIndexOrThrow(_stmt, "timestamp")
        val _columnIndexOfMediaUri: Int = getColumnIndexOrThrow(_stmt, "media_uri")
        val _result: MutableList<DownloadHistoryEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: DownloadHistoryEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpFileName: String
          _tmpFileName = _stmt.getText(_columnIndexOfFileName)
          val _tmpPlatform: String
          _tmpPlatform = _stmt.getText(_columnIndexOfPlatform)
          val _tmpIsAudio: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsAudio).toInt()
          _tmpIsAudio = _tmp != 0
          val _tmpTimestamp: Long
          _tmpTimestamp = _stmt.getLong(_columnIndexOfTimestamp)
          val _tmpMediaUri: String?
          if (_stmt.isNull(_columnIndexOfMediaUri)) {
            _tmpMediaUri = null
          } else {
            _tmpMediaUri = _stmt.getText(_columnIndexOfMediaUri)
          }
          _item = DownloadHistoryEntity(_tmpId,_tmpFileName,_tmpPlatform,_tmpIsAudio,_tmpTimestamp,_tmpMediaUri)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun pruneOld() {
    val _sql: String = "DELETE FROM download_history WHERE id NOT IN (SELECT id FROM download_history ORDER BY timestamp DESC LIMIT 10)"
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
