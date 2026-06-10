package pumpking.lib.config

import java.io.File

interface ConfigProvider {
    val file: File
    fun load()
    fun save()
    fun getString(path: String, def: String = ""): String
    fun getInt(path: String, def: Int = 0): Int
    fun getBoolean(path: String, def: Boolean = false): Boolean
    fun getStringList(path: String): List<String>
    fun contains(path: String): Boolean
    fun set(path: String, value: Any?)
    fun get(path: String): Any?
    fun getKeys(deep: Boolean): Set<String>
}
