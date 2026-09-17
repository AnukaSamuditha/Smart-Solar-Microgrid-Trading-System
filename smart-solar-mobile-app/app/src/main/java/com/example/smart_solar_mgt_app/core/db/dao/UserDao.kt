package com.example.smart_solar_mgt_app.core.db.dao

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.example.smart_solar_mgt_app.core.db.DatabaseContract.Users
import com.example.smart_solar_mgt_app.domain.model.AccountStatus
import com.example.smart_solar_mgt_app.domain.model.Role
import com.example.smart_solar_mgt_app.domain.model.User

/**
 * Table-scoped CRUD for `users`. Takes the SQLiteDatabase instance per call so LocalDbManager
 * can compose multiple DAOs into one atomic transaction.
 */
class UserDao {

    fun insert(db: SQLiteDatabase, user: User) {
        db.insertOrThrow(Users.TABLE, null, user.toContentValues())
    }

    fun update(db: SQLiteDatabase, user: User) {
        db.update(Users.TABLE, user.toContentValues(), "${Users.COL_NIC} = ?", arrayOf(user.nic))
    }

    fun updateAccountStatus(db: SQLiteDatabase, nic: String, status: AccountStatus) {
        val values = ContentValues().apply { put(Users.COL_ACCOUNT_STATUS, status.name) }
        db.update(Users.TABLE, values, "${Users.COL_NIC} = ?", arrayOf(nic))
    }

    fun getByNic(db: SQLiteDatabase, nic: String): User? {
        db.query(Users.TABLE, null, "${Users.COL_NIC} = ?", arrayOf(nic), null, null, null).use { cursor ->
            return if (cursor.moveToFirst()) cursor.toUser() else null
        }
    }

    fun getByEmail(db: SQLiteDatabase, email: String): User? {
        db.query(Users.TABLE, null, "${Users.COL_EMAIL} = ?", arrayOf(email), null, null, null).use { cursor ->
            return if (cursor.moveToFirst()) cursor.toUser() else null
        }
    }

    fun getByRole(db: SQLiteDatabase, role: Role): List<User> {
        db.query(Users.TABLE, null, "${Users.COL_ROLE} = ?", arrayOf(role.name), null, null, null).use { cursor ->
            val results = mutableListOf<User>()
            while (cursor.moveToNext()) results.add(cursor.toUser())
            return results
        }
    }

    fun getByRoleAndStatus(db: SQLiteDatabase, role: Role, status: AccountStatus): List<User> {
        val selection = "${Users.COL_ROLE} = ? AND ${Users.COL_ACCOUNT_STATUS} = ?"
        db.query(Users.TABLE, null, selection, arrayOf(role.name, status.name), null, null, null).use { cursor ->
            val results = mutableListOf<User>()
            while (cursor.moveToNext()) results.add(cursor.toUser())
            return results
        }
    }

    private fun User.toContentValues(): ContentValues = ContentValues().apply {
        put(Users.COL_NIC, nic)
        put(Users.COL_NAME, name)
        put(Users.COL_EMAIL, email)
        put(Users.COL_PHONE, phone)
        put(Users.COL_ADDRESS, address)
        put(Users.COL_PASSWORD_HASH, passwordHash)
        put(Users.COL_ROLE, role.name)
        put(Users.COL_ACCOUNT_STATUS, accountStatus.name)
    }

    private fun Cursor.toUser(): User = User(
        nic = getString(getColumnIndexOrThrow(Users.COL_NIC)),
        name = getString(getColumnIndexOrThrow(Users.COL_NAME)),
        email = getString(getColumnIndexOrThrow(Users.COL_EMAIL)),
        phone = getString(getColumnIndexOrThrow(Users.COL_PHONE)),
        address = getString(getColumnIndexOrThrow(Users.COL_ADDRESS)),
        passwordHash = getString(getColumnIndexOrThrow(Users.COL_PASSWORD_HASH)),
        role = Role.valueOf(getString(getColumnIndexOrThrow(Users.COL_ROLE))),
        accountStatus = AccountStatus.valueOf(getString(getColumnIndexOrThrow(Users.COL_ACCOUNT_STATUS)))
    )
}