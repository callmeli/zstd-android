package com.ximalaya.android.zstd.sample

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.monstertoss.zstd_android.Zstd
import com.monstertoss.zstd_android.ZstdInputStream
import com.monstertoss.zstd_android.ZstdOutputStream
import okhttp3.*
import okhttp3.internal.http.HttpHeaders
import okhttp3.internal.http.RealResponseBody
import okio.GzipSource
import okio.Okio
import okio.Source
import java.io.*
import java.math.BigInteger
import java.security.MessageDigest

const val ZSTD = "zstd"
const val GZIP = "gzip"

class MainActivity : IFileKeeper, AppCompatActivity() {
    val testUrl =
        "http://192.168.3.52:3238/ad-exchange/ting/loading/ts-1641785674047?appid=0&device=iPhone&idfaLimit=1&name=loading_v2&network=WIFI&operator=3&osUpdateTime=1601020993.539255&positionId=1&preRequestAdIds=&scale=3&secure=0&startType=0&systemIDFA=C6C6906F-5F97-4B5A-96DC-8545AB31B877&version=9.0.12&xt=1641785674048"
    val testContent =
        "1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901"
    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient().newBuilder().addInterceptor(
            ZstdDecompress(
                decompressDict = dictContent,
                fileKeeper = this@MainActivity
            )
        ).build()
    }

    val cacheSavePath by lazy {
        filesDir.absolutePath
    }

    val dictContent: ByteArray by lazy {
        assets.open("dict_1").readBytes()
    }
    val testAssets: String by lazy {
        String(assets.open("test").readBytes())
    }

    val resultTv: TextView by lazy {
        findViewById(R.id.tv_result)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<View>(R.id.tv_test_common).setOnClickListener {
            testZstdFile()
        }
        findViewById<View>(R.id.tv_test_dict_http).setOnClickListener {
            httpTest("gzip,deflate,zstd")
        }
        findViewById<View>(R.id.tv_test_gzip_http).setOnClickListener {
            httpTest()
        }
        findViewById<View>(R.id.tv_test_decompress).setOnClickListener {
            val etTimes = findViewById<EditText>(R.id.et_decompress_times)
            val times = etTimes.text.toString()
            testDecompress(dictContent, Integer.parseInt(times))
        }
    }

    private fun httpTest(encodingType:String = "gzip, deflate") {
        okHttpClient.newCall(
            Request.Builder().url(testUrl).header("Accept-Encoding", encodingType).build()
        ).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Log.e(
                        GZIP,
                        "error:$e"
                    )
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val headers = response.headers().toString()
                val result = response.body()?.string()
                runOnUiThread {
                    resultTv.text =
                        "contentType:$headers$result"
                    Log.e(GZIP, "contentType:$headers$result")
                }
            }

        })
    }

    private fun testZstdFile() {
        val destFilePath = File(filesDir.absolutePath, "ztsd.txt").absolutePath
        val compress = cost { testOutput(destFilePath, testContent.encodeToByteArray()) }
        val deCompress = cost { testInput(destFilePath) }
        resultTv.text =
            "compress:$compress,\nDecompress:$deCompress"
    }

    class ZstdDecompress(
        var saveDecompressResponse: Boolean = true,
        var saveCompressResponse: Boolean = true,
        val decompressDict: ByteArray,
        val fileKeeper: IFileKeeper
    ) : Interceptor {

        override fun intercept(chain: Interceptor.Chain): Response {
            var response = chain.proceed(chain.request())
            val encode = response.header("content-encoding")
            if (encode.equals(ZSTD)) {
                val name = BigInteger(
                    1, MessageDigest.getInstance("MD5")
                        .digest(chain.request().url().toString().toByteArray())
                ).toString(16)
                val responseBuilder = response.newBuilder()
                val inputBytes = response.body()?.bytes()
                val strippedHeaders: Headers = response.headers().newBuilder()
                    .removeAll("Content-Encoding")
                    .build()
                saveCompressResponse.takeIf { it }.let {
                    if (inputBytes != null) {
                        fileKeeper.save(
                            fileKeeper.cacheDir() + "/" + ZSTD + "/compress_${name}",
                            inputBytes.copyOf()
                        )
                    }
                }

                val result =
                    Zstd.decompress(
                        inputBytes,
                        decompressDict,
                        Zstd.decompressedSize(inputBytes).toInt()
                    )
                saveDecompressResponse.takeIf { it }.let {
                    if (inputBytes != null) {
                        fileKeeper.save(
                            fileKeeper.cacheDir() + "/" + ZSTD + "/decompress_${name}",
                            inputBytes.copyOf()
                        )
                    }
                }
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
            } else if (encode.equals(GZIP)) {
                val responseBuilder: Response.Builder = response.newBuilder()
                    .request(chain.request())
                if (HttpHeaders.hasBody(response)
                ) {
                    val source = response.body()!!.source()
                    val responseBody: Source = GzipSource(source)
                    saveCompressResponse.takeIf { it }.let {
                        val name = BigInteger(
                            1, MessageDigest.getInstance("MD5")
                                .digest(chain.request().url().toString().toByteArray())
                        ).toString(16)
                        fileKeeper.save(
                            fileKeeper.cacheDir() + "/" + GZIP + "/compress_${name}",
                            source.buffer().clone().readByteArray()
                        )
                    }
                    val strippedHeaders: Headers = response.headers().newBuilder()
                        .removeAll("Content-Encoding")
                        .removeAll("Content-Length")
                        .build()
                    responseBuilder.headers(strippedHeaders)
                    val contentType: String? = response.header("Content-Type")
                    responseBuilder.body(
                        RealResponseBody(
                            contentType,
                            -1L,
                            Okio.buffer(responseBody)
                        )
                    )
                    response = responseBuilder.build()
                }
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
            return if (file.exists() && file.length() > 0) "src:$len,dest:${file.length()},ratio:${
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
            return byteArray?.let { "src:$len,dest:${it.size},ratio:${it.size / len.toFloat()}" }
                ?: "file read failed"
        }
    }

    override fun save(path: String, data: ByteArray) {
        File(path).takeIf { !it.exists() }?.let {
            if (!it.parentFile.exists()) {
                it.parentFile.mkdirs()
            }
            var outputStream: FileOutputStream? = null
            try {
                outputStream = FileOutputStream(it)
                outputStream.write(data)
            } catch (e: IOException) {
                e.printStackTrace()
            } finally {
                outputStream?.apply { close() }
            }
        }
    }

    override fun get(path: String): InputStream? {
        return File(path).takeIf { it.exists() }?.let { FileInputStream(it) }
    }

    override fun cached(path: String): Boolean {
        return File(path).exists()
    }

    override fun cacheDir(): String {
        return cacheSavePath
    }

    fun testDecompress(dict: ByteArray?, testTimes: Int = 10000) {
        val compressList = ArrayList<String>()
        Log.e("cost", ZSTD)
        val parentDir = "$cacheSavePath/$ZSTD"
        File(parentDir).list().takeIf { it.isNotEmpty() }?.forEach {
            it.takeIf { it.startsWith("compress_") }
                ?.let { path -> compressList.add(File(parentDir, path).absolutePath) }
        }
        var zstdContent: String? = null
        val zstdCost = compressList.takeIf { it.isNotEmpty() }?.get(0)?.let { item ->
            cost {
                for (i in 0..testTimes) {
                    if (zstdContent == null) {
                        zstdContent = item.zstdDecompress(dict)?.let { String(it) }
                    } else {
                        item.zstdDecompress(dict)
                    }
                }
            }
        }
        compressList.clear()
        val gzipDir = "$cacheSavePath/$GZIP"
        File(gzipDir).list().takeIf { it.isNotEmpty() }?.forEach {
            it.takeIf { it.startsWith("compress_") }
                ?.let { path -> compressList.add(File(gzipDir, path).absolutePath) }
        }
        Log.e("cost", GZIP)
        var contentGzip: String? = null
        val gzipCost = compressList.takeIf { it.isNotEmpty() }?.get(0)?.let { item ->
            cost {
                for (i in 0..testTimes) {
                    if (contentGzip == null) {
                        val gzipSource = Okio.buffer(GzipSource(Okio.source(FileInputStream(item))))
                        contentGzip = String(gzipSource.readByteArray())
                        gzipSource.close()
                    } else {
                        val gzipSource = Okio.buffer(GzipSource(Okio.source(FileInputStream(item))))
                        gzipSource.readByteArray()
                        gzipSource.close()
                    }

                }
            }
        }
        val sameResult =
            "解析结果内容是否相同\n${zstdContent?.equals(contentGzip)}\n长度\nzstdContent:${zstdContent?.length},contentGzip:${contentGzip?.length}\nzstdContent:\n$zstdContent \n\ncontentGzip:\n$contentGzip "
        resultTv.text = "zstdCost:$zstdCost,gzipCost:$gzipCost\n$sameResult}"
    }

}

fun Any.cost(method: () -> Any): Any {
    val start = SystemClock.currentThreadTimeMillis()
    val result = method()
    return arrayListOf(SystemClock.currentThreadTimeMillis() - start, result)
}

val handler = Handler(Looper.getMainLooper())
fun Any.runOnUiThread(method: () -> Unit) {
    handler.post(method)
}

fun String.zstdDecompress(dict: ByteArray? = null): ByteArray? {
    var byteArray: ByteArray? = null
    val file = File(this)
    val len = file.takeIf { it.exists() }?.length() ?: -1
    if (len <= 0) {
        return byteArray
    }
    dict?.let {
        var inputStream: FileInputStream? = null
        try {
            inputStream = FileInputStream(this)
            val bytes = inputStream!!.readBytes()
            val dstBytes = ByteArray(Zstd.decompressedSize(bytes).toInt())
            Zstd.decompress(dstBytes, bytes, it)
            return dstBytes
        } catch (e: Exception) {
            throw e
        } finally {
            inputStream?.apply { close() }
        }
        return null
    }

    var inputStream: ZstdInputStream? = null
    try {
        inputStream = ZstdInputStream(FileInputStream(this))
        byteArray = inputStream!!.readBytes()
    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        inputStream?.apply { close() }
    }
    return byteArray
}

interface IFileKeeper {
    fun save(path: String, data: ByteArray)
    fun get(path: String): InputStream?
    fun cached(path: String): Boolean
    fun cacheDir(): String
}