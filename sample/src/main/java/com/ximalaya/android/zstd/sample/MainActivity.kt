package com.ximalaya.android.zstd.sample

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.monstertoss.zstd_android.Zstd
import com.monstertoss.zstd_android.ZstdInputStream
import com.monstertoss.zstd_android.ZstdOutputStream
import okhttp3.*
import okhttp3.internal.http.RealResponseBody
import okio.Okio
import java.io.*
import java.nio.ByteBuffer

class MainActivity : AppCompatActivity() {
    val testUrl =
        "http://192.168.3.52:3238/ad-exchange/ting/loading/ts-1641785674047?appid=0&device=iPhone&idfaLimit=1&name=loading_v2&network=WIFI&operator=3&osUpdateTime=1601020993.539255&positionId=1&preRequestAdIds=&scale=3&secure=0&startType=0&systemIDFA=C6C6906F-5F97-4B5A-96DC-8545AB31B877&version=9.0.12&xt=1641785674048"
    val testContent =
        "1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901"
    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient().newBuilder().addInterceptor(ZstdDecompress(dictContent)).build()
    }

    val dictContent: ByteArray by lazy {
        assets.open("dict_1").readBytes()
    }
    val testAssets: String by lazy {
       String( assets.open("test").readBytes())
    }

    val resultTv: TextView by lazy {
        findViewById(R.id.tv_result)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
//        testZstdFile()
//        Toast.makeText(this, "assets toast${testAssets}", Toast.LENGTH_SHORT).show()
        httpTest()
    }

    private fun httpTest() {
        okHttpClient.newCall(
            Request.Builder().url(testUrl).header("Accept-Encoding", "gzip,deflate,zstd").build()
        ).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    resultTv.text =
                        "error:$e"
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val headers = response.headers().toString()
                val result = response.body()?.string()
                runOnUiThread {
                    resultTv.text =
                        "contentType:$headers$result"
                }
            }

        })
    }

    private fun testZstdFile() {
        val destFilePath = File(filesDir.absolutePath, "ztsd.txt").absolutePath
        cost { testOutput(destFilePath, testContent.encodeToByteArray()) }
        cost { testInput(destFilePath) }


    }

    class ZstdDecompress(val dict: ByteArray) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val response = chain.proceed(chain.request())
            val encode = response.header("content-encoding")
            if (encode.equals("zstd")) {
                val responseBuilder = response.newBuilder()
                val conentLen = response.header("Content-Length")
                val inputBytes = response.body()?.bytes()
                val strippedHeaders: Headers = response.headers().newBuilder()
                    .removeAll("Content-Encoding")
                    .removeAll("Content-Length")
                    .build()
                val result =
                    Zstd.decompress(inputBytes, dict, Zstd.decompressedSize(inputBytes).toInt())
                val contentType: String? = response.header("Content-Type")
                responseBuilder.headers(strippedHeaders)
                responseBuilder.body(
                    RealResponseBody(
                        contentType,
                        -1L,
                        Okio.buffer(Okio.source(ByteArrayInputStream(result)))
                    )
                )
                return responseBuilder.build()
            }
            return response
        }

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
            return if (file.exists() && file.length() > 0) "src:$len,dest:${file.length()}，ratio:${
                len / file.length().toFloat()
            }" else "file not exists"
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

val handler = Handler(Looper.getMainLooper())
fun Any.runOnUiThread(method: () -> Unit) {
    handler.post(method)
}