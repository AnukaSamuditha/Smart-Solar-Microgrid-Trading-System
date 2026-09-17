package com.example.smart_solar_mgt_app.core.db.dao

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.example.smart_solar_mgt_app.core.db.DatabaseContract.SessionTable
import com.example.smart_solar_mgt_app.domain.model.LoginState
import com.example.smart_solar_mgt_app.domain.model.Role
import com.example.smart_solar_mgt_app.domain.model.Session

/**
 * Non-authoritative device-local mirror of login state - the authoritative source is
 * SecureSessionStore (EncryptedSharedPreferences, added in the Security Manager phase).
 * Nothing reads this table to make an authorization decision.
 */
class SessionDao {

    fun save(db: SQLiteDatabase, session: Session) {
        db.delete(SessionTable.TABLE, null, null) // single active row on this device
        val values = ContentValues().apply {
            put(SessionTable.COL_USER_ID, session.userId)
            put(SessionTable.COL_ROLE, session.role.name)
            put(SessionTable.COL_LOGIN_STATE, session.loginState.name)
        }
        db.insertOrThrow(SessionTable.TABLE, null, values)
    }

    fun get(db: SQLiteDatabase): Session? {
        db.query(SessionTable.TABLE, null, null, null, null, null, null, "1").use { cursor ->
            return if (cursor.moveToFirst()) cursor.toSession() else null
        }
    }

    fun clear(db: SQLiteDatabase) {
        db.delete(SessionTable.TABLE, null, null)
    }

    private fun Cursor.toSession(): Session = Session(
        userId = getString(getColumnIndexOrThrow(SessionTable.COL_USER_ID)),
        role = Role.valueOf(getString(getColumnIndexOrThrow(SessionTable.COL_ROLE))),
        loginState = LoginState.valueOf(getString(getColumnIndexOrThrow(SessionTable.COL_LOGIN_STATE)))
    )
}
