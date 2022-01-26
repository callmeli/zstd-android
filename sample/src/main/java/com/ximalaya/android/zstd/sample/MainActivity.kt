package com.ximalaya.android.zstd.sample

import android.content.Context
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
const val ZSTD_DICT = "zstd_dict"
const val GZIP = "gzip"
const val COMPRESS_FILE_PREFIX = "compress_"
const val DECOMPRESS_FILE_PREFIX = "decompress_"

class MainActivity : IFileKeeper, AppCompatActivity() {
    val testUrl =
        "http://192.168.3.52:3238/ad-exchange/ting/loading/ts-1641785674047?appid=0&device=iPhone&idfaLimit=1&name=loading_v2&network=WIFI&operator=3&osUpdateTime=1601020993.539255&positionId=1&preRequestAdIds=&scale=3&secure=0&startType=0&systemIDFA=C6C6906F-5F97-4B5A-96DC-8545AB31B877&version=9.0.12&xt=1641785674048"
    val testNoDictUrl =
        "http://192.168.3.52:3238/discovery-feed-mobile/v3/mix/ts-1641551085462?device=android&deviceId=bdb32567-cb76-3397-8af2-b9acf9d7022e&adModuleNum=0&onlyBody=false&offset=0&categoryId=-2&appid=0&gender=0&giftTag=0&code=43_310000_3100&click=false&topBuzzVersion=3&network=wifi&operator=3&vertical_stream=1&version=9.0.3&countyCode=310115&scale=1&hotPlayModuleShowTimes=1&guessPageId=0"
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
        findViewById<View>(R.id.tv_test_dict_result_save_std).setOnClickListener {
            dictResultSaveToStd()
        }
        findViewById<View>(R.id.tv_test_dict_http).setOnClickListener {
            httpTest(testUrl, "gzip,deflate,zstd", true)
        }
        findViewById<View>(R.id.tv_test_zstd_http).setOnClickListener {
            httpTest(testNoDictUrl, "gzip,deflate,zstd")
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

    private fun httpTest(
        url: String = testUrl,
        encodingType: String = "gzip, deflate",
        useZstdDictParse: Boolean = false
    ) {
        okHttpClient.newCall(
            Request.Builder().url(url).header("Accept-Encoding", encodingType)
                .header(ZSTD_DICT, useZstdDictParse.toString()).build()
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

    private fun dictResultSaveToStd() {
        var parentDir = "$cacheSavePath/$ZSTD_DICT"
        var compressList = File(parentDir).list().takeIf { it.isNotEmpty() }?.filter {
            it.startsWith(DECOMPRESS_FILE_PREFIX)
        }
        compressList?.takeIf { it.isNotEmpty() }?.get(0)?.let {
            val sourcePath = File(parentDir, it).absolutePath
            val destDir = File("$cacheSavePath/$ZSTD")
            if (!destDir.exists()) {
                destDir.mkdirs()
            } else if (destDir.list().isNotEmpty()) {
                destDir.listFiles().all {
                    it.delete()
                }
            }
            val desFile = File(
                destDir.absolutePath,
                COMPRESS_FILE_PREFIX + it.substring(DECOMPRESS_FILE_PREFIX.length)
            )
            val deCompressDestFile = File(destDir.absolutePath, it)
            var deFis: FileInputStream? = null
            var deFos: FileOutputStream? = null
            try {
                deFis = FileInputStream(File(sourcePath))
                deFos = FileOutputStream(deCompressDestFile)
                deFos.write(deFis.readBytes())
            } finally {
                deFis?.close()
                deFos?.close()
            }
            var fis: FileInputStream? = null
            var fos: FileOutputStream? = null
            try {
                fis = FileInputStream(File(sourcePath))
                fos = FileOutputStream(desFile.absolutePath)
                val compress = cost {
                    fos.write(Zstd.compress(fis.readBytes()))
                }
                resultTv.text =
                    "compress:$compress"
            } finally {
                fis?.close()
                fos?.close()
            }
        } ?: "未找到字典解压文件内容".toast(MainActivity@ this)

    }

    class ZstdDecompress(
        var saveDecompressResponse: Boolean = true,
        var saveCompressResponse: Boolean = true,
        val decompressDict: ByteArray,
        val fileKeeper: IFileKeeper
    ) : Interceptor {

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            var response = chain.proceed(request)
            val encode = response.header("content-encoding")
            if (encode.equals(ZSTD)) {
                val useDict = request.header(ZSTD_DICT)?.let { it.toBoolean() } ?: false
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
                        val savePath = useDict.takeIf { it }?.let {
                            fileKeeper.cacheDir() + "/" + ZSTD_DICT + "/$COMPRESS_FILE_PREFIX${name}"
                        } ?: "${fileKeeper.cacheDir()}/$ZSTD/$COMPRESS_FILE_PREFIX${name}"
                        fileKeeper.save(
                            savePath,
                            inputBytes.copyOf()
                        )
                    }
                }

                val result = takeIf { useDict }.let {
                    Zstd.decompress(
                        inputBytes,
                        decompressDict,
                        Zstd.decompressedSize(inputBytes).toInt()
                    )
                } ?: Zstd.okdecompress(
                    inputBytes,
                    Zstd.decompressedSize(inputBytes).toInt()
                )

                saveDecompressResponse.takeIf { it }.let {
                    if (inputBytes != null) {
                        val savePath = useDict.takeIf { it }?.let {
                            fileKeeper.cacheDir() + "/" + ZSTD_DICT + "/$DECOMPRESS_FILE_PREFIX${name}"
                        } ?: fileKeeper.cacheDir() + "/" + ZSTD + "/$DECOMPRESS_FILE_PREFIX${name}"
                        fileKeeper.save(
                            savePath,
                            result.copyOf()
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
                    val bytes = source.readByteArray()
                    saveCompressResponse.takeIf { it }.let {
                        val name = BigInteger(
                            1, MessageDigest.getInstance("MD5")
                                .digest(chain.request().url().toString().toByteArray())
                        ).toString(16)
                        fileKeeper.save(
                            fileKeeper.cacheDir() + "/" + GZIP + "/compress_${name}",
                            bytes.clone()
                        )
                    }
                    val responseBody: Source = GzipSource(Okio.source(ByteArrayInputStream(bytes)))
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
        File(path).let {
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

    fun testDecompress(
        dict: ByteArray?,
        testTimes: Int = 10000,
        context: Context = MainActivity@ this
    ) {
        GlobalScope.launch(Dispatchers.IO) {
            val compressList = ArrayList<String>()
            Log.e("cost", ZSTD)
            "测试解压缩${testTimes}ZSTD次开始".toast(context)
            var parentDir = "$cacheSavePath/$ZSTD"
            File(parentDir).list().takeIf { it.isNotEmpty() }?.forEach {
                it.takeIf { it.startsWith("compress_") }
                    ?.let { path -> compressList.add(File(parentDir, path).absolutePath) }
            }
            var zstdContent: String? = null
            var zstdRatio = 0f
            val zstdCost = compressList.takeIf { it.isNotEmpty() }?.get(0)?.let { item ->
                costOnly {
                    for (i in 0..testTimes) {
                        if (zstdContent == null) {
                            val sizeList = ArrayList<Int>()
                            zstdContent = item.zstdDecompress(null, sizeList)?.let { String(it) }
                            zstdRatio = sizeList.takeIf { it.size == 2 }?.let {
                                sizeList[1] / (sizeList[0]).toFloat()
                            } ?: 0f
                        } else {
                            item.zstdDecompress(null)
                        }
                    }
                }
            }
            compressList.clear()
            "测试解压缩${testTimes}次ZSTD_DICT开始".toast(context)
            parentDir = "$cacheSavePath/$ZSTD_DICT"
            File(parentDir).list().takeIf { it.isNotEmpty() }?.forEach {
                it.takeIf { it.startsWith("compress_") }
                    ?.let { path -> compressList.add(File(parentDir, path).absolutePath) }
            }
            var zstdDictContent: String? = null
            var zstdDictRatio = 0f
            val zstdDictCost = compressList.takeIf { it.isNotEmpty() }?.get(0)?.let { item ->
                costOnly {
                    for (i in 0..testTimes) {
                        if (zstdDictContent == null) {
                            val sizeList = ArrayList<Int>()
                            zstdDictContent =
                                item.zstdDecompress(dict, sizeList)?.let { String(it) }
                            zstdDictRatio = sizeList.takeIf { it.size == 2 }?.let {
                                sizeList[1] / (sizeList[0]).toFloat()
                            } ?: 0f
                        } else {
                            item.zstdDecompress(dict)
                        }
                    }
                }
            }
            compressList.clear()
            "测试解压缩${testTimes}次GZIP开始".toast(context)
            val gzipDir = "$cacheSavePath/$GZIP"
            File(gzipDir).list().takeIf { it.isNotEmpty() }?.forEach {
                it.takeIf { it.startsWith("compress_") }
                    ?.let { path -> compressList.add(File(gzipDir, path).absolutePath) }
            }
            Log.e("cost", GZIP)
            var contentGzip: String? = null
            var gzipRatio = 0f
            val gzipCost = compressList.takeIf { it.isNotEmpty() }?.get(0)?.let { item ->
                costOnly {
                    for (i in 0..testTimes) {
                        if (contentGzip == null) {
                            val inputStream = FileInputStream(item)
                            val inputByteArray = inputStream.readBytes()
                            inputStream.close()
                            val gzipSource =
                                Okio.buffer(
                                    GzipSource(
                                        Okio.source(
                                            ByteArrayInputStream(
                                                inputByteArray
                                            )
                                        )
                                    )
                                )
                            val byteArray = gzipSource.readByteArray()
                            contentGzip = String(byteArray)
                            gzipRatio = byteArray.size / inputByteArray.size.toFloat()
                            gzipSource.close()
                        } else {
                            val gzipSource =
                                Okio.buffer(GzipSource(Okio.source(FileInputStream(item))))
                            gzipSource.readByteArray()
                            gzipSource.close()
                        }
                    }
                }
            }
            val contentDetails = "zstdContent:\n$zstdContent\n\n" +
                    "zstdDictContent:\n$zstdDictContent\n\n" +
                    "contentGzip:\n$contentGzip "
            withContext(Dispatchers.Main) {
                resultTv.text =
                    "测试解压缩${testTimes}次对比\nzstd\n\t\tcost:$zstdCost,ratio:$zstdRatio,length:${zstdContent?.length}\nzstdDict\n\t\tcost:$zstdDictCost,ratio:$zstdDictRatio,length:${zstdDictContent?.length}\ngzip\n\t\tcost:$gzipCost,ratio:$gzipRatio,length:${contentGzip?.length}\n\n$contentDetails}"
            }
        }
    }

}

fun Any.cost(method: () -> Any): Any {
    val start = SystemClock.currentThreadTimeMillis()
    val result = method()
    return arrayListOf(SystemClock.currentThreadTimeMillis() - start, result)
}

fun Any.costOnly(method: () -> Any): Any {
    val start = SystemClock.currentThreadTimeMillis()
    val result = method()
    return (SystemClock.currentThreadTimeMillis() - start)
}

val handler = Handler(Looper.getMainLooper())
fun Any.runOnUiThread(method: () -> Unit) {
    handler.post(method)
}

fun String.zstdDecompress(
    dict: ByteArray? = null,
    inputOutputLen: ArrayList<Int>? = null
): ByteArray? {
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
            inputOutputLen?.add(bytes.size)
            val dstBytes = ByteArray(Zstd.decompressedSize(bytes).toInt())
            Zstd.decompress(dstBytes, bytes, it)
            inputOutputLen?.add(dstBytes.size)
            return dstBytes
        } catch (e: Exception) {
            throw e
        } finally {
            inputStream?.apply { close() }
        }
        return null
    }

    var inputStream: FileInputStream? = null
    try {
        inputStream = FileInputStream(this)
        val bytes = inputStream!!.readBytes()
        inputOutputLen?.add(bytes.size)
        byteArray = Zstd.okdecompress(bytes, Zstd.decompressedSize(bytes).toInt())
        inputOutputLen?.add(byteArray.size)
    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        inputStream?.apply { close() }
    }
    return byteArray
}

fun String.toast(context: Context) {
    runOnUiThread {
        Toast.makeText(context, String@ this, Toast.LENGTH_SHORT).show()
    }
}

interface IFileKeeper {
    fun save(path: String, data: ByteArray)
    fun get(path: String): InputStream?
    fun cached(path: String): Boolean
    fun cacheDir(): String
}