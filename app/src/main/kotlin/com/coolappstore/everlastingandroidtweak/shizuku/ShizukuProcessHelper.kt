package com.coolappstore.everlastingandroidtweak.shizuku

import rikka.shizuku.Shizuku
import java.lang.reflect.Method

object ShizukuProcessHelper {
    private var newProcessMethod: Method? = null

    init {
        try {
            newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            newProcessMethod?.isAccessible = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun newProcess(cmd: Array<String>, env: Array<String>? = null, dir: String? = null): Process? {
        return try {
            if (newProcessMethod != null) {
                newProcessMethod?.invoke(null, cmd, env, dir) as? Process
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun runCommand(command: String): Boolean {
        return try {
            val process = newProcess(arrayOf("sh", "-c", command)) ?: return false
            process.inputStream.readBytes()
            process.errorStream.readBytes()
            process.waitFor() == 0
        } catch (_: Exception) { false }
    }
}
