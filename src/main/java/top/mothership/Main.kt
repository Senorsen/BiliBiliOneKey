package top.mothership

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import okhttp3.*
import okhttp3.Request.Builder
import top.mothership.entity.*
import top.mothership.util.OSCheck.OSType.*
import top.mothership.util.OSCheck.getOperatingSystemType
import java.io.*
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Properties
import java.util.concurrent.TimeUnit.*
import java.util.regex.Pattern

fun main(args: Array<String>) {
    when {
        args.isEmpty() -> {
            init(0)
        }
        args[0] == "--stop" -> {
            init(1)
        }
        else -> {
            System.err.println("Error: invalid args, no arg to start, --stop to stop.")
        }
    }
}


fun stopLive(roomId: String, areaV2: String?, csrfToken: String, cookies: String?) {
    val client = OkHttpClient()
    val mediaType = MediaType.parse("application/x-www-form-urlencoded")
    val content = ("room_id=" + roomId
        + "&platform=pc"
        + "&csrf_token=" + csrfToken
        + "&csrf=" + csrfToken)
    println("body: $content")
    val body = RequestBody.create(mediaType, content)
    val request = Builder()
        .url("https://api.live.bilibili.com/room/v1/Room/stopLive")
        .post(body)
        .addHeader("Cookie", cookies)
        .build()
    val response: Response
    try {
        response = client.newCall(request).execute()
        val responseBody = response.body()!!.string()
        val (code, msg) = GsonBuilder().create().fromJson(responseBody, BiliBiliResponse::class.java)
        when (code) {
            0 -> {
                println("下播成功！")
                SECONDS.sleep(10)
            }
            else -> {
                System.err.println(
                    Instant.now()
                        .toString() + " 请求 Bilibili 开播接口失败，错误码：" + code
                        + " 错误信息：" + msg + "，程序即将退出；"
                )
                SECONDS.sleep(10)
            }
        }
    } catch (e: IOException) {
        System.err.println("下播失败， IOException: " + e.message)
        SECONDS.sleep(10)
    }
}

fun startLive(
    roomId: String,
    areaV2: String,
    csrfToken: String,
    cookies: String?,
    obsSetting: OBSSetting?,
    obsServiceJson: File?
) {
    val client = OkHttpClient()
    val mediaType = MediaType.parse("application/x-www-form-urlencoded")
    val content = ("room_id=" + roomId
        + "&platform=pc&area_v2=" + areaV2
        + "&csrf_token=" + csrfToken
        + "&csrf=" + csrfToken)
    println("body: $content")
    val body = RequestBody.create(mediaType, content)
    val request = Builder()
        .url("https://api.live.bilibili.com/room/v1/Room/startLive")
        .post(body)
        .addHeader("Cookie", cookies)
        .build()
    val response: Response
    try {
        response = client.newCall(request).execute()
        val responseBody = response.body()!!.string()
        val (code, msg) = GsonBuilder().create().fromJson(responseBody, BiliBiliResponse::class.java)
        when (code) {
            0 -> {
                println(Instant.now().toString() + " 一键开播成功！")
                //返回码是0的时候，Data是对象（其他情况是数组，直接按对象反序列化会出异常），因此重新反序列化一次
                val (_, _, data) = GsonBuilder().create().fromJson<BiliBiliResponse<Data>>(
                    responseBody,
                    object : TypeToken<BiliBiliResponse<Data?>?>() {}.type
                )
                if ((data!!.rtmp!!.code
                        == obsSetting!!.settings!!.key) && (data.rtmp!!.addr
                        == obsSetting.settings!!.server)
                ) {
                    println(Instant.now().toString() + " 直播地址、直播码校验通过，无需修改，程序即将退出！")
                } else {
                    obsSetting.settings!!.key = data.rtmp!!.code
                    obsSetting.settings!!.server = data.rtmp!!.addr
                    try {
                        BufferedWriter(OutputStreamWriter(FileOutputStream(obsServiceJson))).use { bw ->
                            bw.write(GsonBuilder().create().toJson(obsSetting))
                            println(Instant.now().toString() + " 检测到直播码不匹配，修改OBS配置文件成功，重启OBS生效。程序即将退出！")
                        }
                    } catch (e: IOException) {
                        System.err.println(
                            Instant.now().toString() + " 检测到直播码不匹配，写入直播码配置文件时发生异常：" + e.message + "，程序即将退出；"
                        )
                        DAYS.sleep(10)
                        return
                    }
                }
                SECONDS.sleep(1)
            }
            else -> {
                System.err.println(
                    Instant.now()
                        .toString() + " 请求 Bilibili 开播接口失败，错误码：" + code
                        + " 错误信息：" + msg + "，程序即将退出；"
                )
                SECONDS.sleep(10)
            }
        }
    } catch (e: IOException) {
        System.err.println(Instant.now().toString() + " 请求 Bilibili 接口时发生异常：" + e.message + "，程序即将退出；")
        SECONDS.sleep(10)
    }
}

fun init(type: Int) {
    val jarFilePath = System.getProperty("user.dir")
    val userProfilePath = System.getProperty("user.home")
    val syncConfig = Properties()
    val obsConfig = Properties()
    var obsSetting: OBSSetting? = null
    var obsServiceJson: File? = null
    var obsProfileName = "未命名"
    val bilibiliUid = Pattern.compile("DedeUserID=(\\d*);")
    val syncConfigFile = File(jarFilePath + File.separator + "config.ini")
    println("Try to read config from $syncConfigFile")
    if (syncConfigFile.exists()) {
        try {
            FileInputStream(syncConfigFile).use { `in` ->
                syncConfig.load(
                    InputStreamReader(
                        `in`,
                        StandardCharsets.UTF_8
                    )
                )
            }
        } catch (e: IOException) {
            System.err.println(Instant.now().toString() + " 读取 Sync 配置文件时发生 IO 异常：" + e.message + "，程序即将退出；")
            DAYS.sleep(10)
            return
        }
        if (syncConfig.getProperty("RoomID") != null && syncConfig.getProperty("Cookies") != null) {
            val m = bilibiliUid.matcher(syncConfig.getProperty("Cookies"))
            m.find()
            println(
                Instant.now()
                    .toString() + " 读取Sync配置文件成功，房间号是：" + syncConfig.getProperty("RoomID") + "，B站uid是：" + m.group(1)
            )
        } else {
            println(
                """${Instant.now()} 错误：解析 config.ini 失败，程序即将退出。
请确认本程序与 Sync.exe 处于同一目录下，并且 Default Plug-ins.BiliBili 插件已启动！"""
            )
            SECONDS.sleep(10)
            return
        }
    } else {
        println(
            """${Instant.now()} 错误：没有找到 config.ini ，程序即将退出。
请确认本程序与 Sync.exe 处于同一目录下，并且 Default Plug-ins.BiliBili 插件已启动！"""
        )
        SECONDS.sleep(10)
        return
    }
    val obsConfigFile: File
    obsConfigFile = when (getOperatingSystemType()) {
        Windows -> File(
            "$userProfilePath\\AppData\\Roaming\\obs-studio\\global.ini"
        )
        MacOS -> File("$userProfilePath/Library/Application Support/obs-studio/global.ini")
        else -> throw NotImplementedError()
    }
    if (obsConfigFile.exists()) {
        try {
            FileInputStream(obsConfigFile).use { `in` ->
                obsConfig.load(
                    InputStreamReader(
                        `in`,
                        StandardCharsets.UTF_8
                    )
                )
            }
        } catch (e: IOException) {
            System.err.println(Instant.now().toString() + " 读取 OBS 配置文件时发生 IO 异常：" + e.message + "，程序即将退出；")
            SECONDS.sleep(10)
            return
        }
        if (obsConfig.getProperty("ProfileDir") != null) {
            println(Instant.now().toString() + " 读取 OBS 配置文件成功，当前所用配置文件名：" + obsConfig.getProperty("ProfileDir"))
            obsProfileName = obsConfig.getProperty("ProfileDir")
        }
        obsServiceJson = when (getOperatingSystemType()) {
            Windows -> File(
                "$userProfilePath\\AppData\\Roaming\\obs-studio\\basic\\profiles\\$obsProfileName\\service.json"
            )
            MacOS -> File("$userProfilePath/Library/Application Support/obs-studio/basic/profiles/$obsProfileName/service.json")
            else -> throw NotImplementedError()
        }
        if (obsServiceJson.exists()) {
            try {
                BufferedReader(InputStreamReader(FileInputStream(obsServiceJson))).use { br ->
                    obsSetting = GsonBuilder().create().fromJson(br, OBSSetting::class.java)
                    if (obsSetting == null || obsSetting!!.settings == null) {
                        obsSetting = OBSSetting()
                        obsSetting!!.settings = Settings()
                    }
                }
            } catch (e: IOException) {
                System.err.println(Instant.now().toString() + " 读取直播码配置文件时发生 IO 异常：" + e.message + "，程序即将退出；")
                SECONDS.sleep(10)
                return
            }
            println(Instant.now().toString() + " 读取当前直播码配置成功。")
        } else {
            System.err.println(Instant.now().toString() + " 读取 OBS 配置文件成功，但没有找到对应的直播码配置文件，程序即将退出；")
            SECONDS.sleep(10)
            return
        }
    } else {
        System.err.println(Instant.now().toString() + " 没有检测到 OBS Studio ，将不会自动校验直播码/直播地址！")
    }
    when (type) {
        0 -> startLive(
            syncConfig.getProperty("RoomID"),
            syncConfig.getProperty("area_v2"),
            syncConfig.getProperty("csrf_token"),
            syncConfig.getProperty("Cookies"),
            obsSetting,
            obsServiceJson
        )
        1 -> stopLive(
            syncConfig.getProperty("RoomID"),
            syncConfig.getProperty("area_v2"),
            syncConfig.getProperty("csrf_token"),
            syncConfig.getProperty("Cookies")
        )
    }
}
