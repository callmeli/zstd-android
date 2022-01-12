package com.ximalaya.android.zstd.sample

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.monstertoss.zstd_android.ZstdInputStream
import com.monstertoss.zstd_android.ZstdOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {
    val testContent =
        "1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val destFilePath = File(filesDir.absolutePath, "ztsd.txt").absolutePath
        cost { testOutput(destFilePath, testContent.encodeToByteArray()) }
        cost { testInput(destFilePath) }
    }


    fun testOutput(filePath: String, bytes: ByteArray): Any {
        val len = bytes.size
        var outputStream: ZstdOutputStream? = null
        try {
            outputStream = ZstdOutputStream(FileOutputStream(filePath))
            outputStream.write(bytes)
            outputStream.flush()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            outputStream?.apply { close() }
            val file = File(filePath)
            return if (file.exists() && file.length() > 0) "src:$len,dest:${file.length()}，ratio:${len /file.length().toFloat()}" else "file not exists"
        }
    }

    fun testInput(filePath: String): Any {
        var byteArray: ByteArray? = null
        val file = File(filePath)
        val len = file.takeIf { it.exists() }?.length() ?: -1
        if (len <= 0) {
            return -1
        }
        var inputStream: ZstdInputStream? = null
        try {
            inputStream = ZstdInputStream(FileInputStream(filePath))
            byteArray = inputStream.readBytes()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            inputStream?.apply { close() }
            return byteArray?.let { "src:$len,dest:${it.size}，ratio:${it.size / len.toFloat()}" }
                ?: "file read failed"
        }
    }

}

fun Any.cost(method: () -> Any) {
    val start = System.currentTimeMillis()
    val result = method()
    println("${method.hashCode()},cost:${System.currentTimeMillis() - start},result:${result}")
}