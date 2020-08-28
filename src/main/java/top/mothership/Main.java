package top.mothership;

import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import okhttp3.*;
import top.mothership.entity.BiliBiliResponse;
import top.mothership.entity.Data;
import top.mothership.entity.OBSSetting;
import top.mothership.entity.Settings;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {

    public static void main(String[] args) throws InterruptedException {
        if (args.length == 0) {
            init(0);
        } else if (args[0].equals("--stop")) {
            init(1);
        } else {
            System.err.println("Error: invalid args, no arg to start, --stop to stop.");
        }
    }

    static void stopLive(String roomId, String areaV2, String csrfToken, String cookies) throws InterruptedException {
        OkHttpClient client = new OkHttpClient();
        MediaType mediaType = MediaType.parse("application/x-www-form-urlencoded");
        String content = "room_id=" + roomId
                + "&platform=pc"
                + "&csrf_token=" + csrfToken
                + "&csrf=" + csrfToken;
        System.out.println("body: " + content);
        RequestBody body = RequestBody.create(mediaType, content);
        Request request = new Request.Builder()
                .url("https://api.live.bilibili.com/room/v1/Room/stopLive")
                .post(body)
                .addHeader("Cookie", cookies)
                .build();
        Response response;
        try {
            response = client.newCall(request).execute();
            String responseBody = response.body().string();
            BiliBiliResponse biliBiliResponse = new GsonBuilder().create().fromJson(responseBody, BiliBiliResponse.class);
            switch (biliBiliResponse.getCode()) {
                case 0:
                    System.out.println("下播成功！");
                    TimeUnit.SECONDS.sleep(10);
                    break;

                default:
                    System.err.println(Instant.now()
                            + " 请求 Bilibili 开播接口失败，错误码：" + biliBiliResponse.getCode()
                            + " 错误信息：" + biliBiliResponse.getMsg() + "，程序即将退出；");
                    TimeUnit.SECONDS.sleep(10);
            }
        } catch (IOException e) {
            System.err.println("下播失败， IOException: " + e.getMessage());
            TimeUnit.SECONDS.sleep(10);
        }
    }

    static void startLive(String roomId, String areaV2, String csrfToken, String cookies, OBSSetting obsSetting, File obsServiceJson) throws InterruptedException {
        OkHttpClient client = new OkHttpClient();
        MediaType mediaType = MediaType.parse("application/x-www-form-urlencoded");
        String content = "room_id=" + roomId
                + "&platform=pc&area_v2=" + areaV2
                + "&csrf_token=" + csrfToken
                + "&csrf=" + csrfToken;
        System.out.println("body: " + content);
        RequestBody body = RequestBody.create(mediaType, content);
        Request request = new Request.Builder()
                .url("https://api.live.bilibili.com/room/v1/Room/startLive")
                .post(body)
                .addHeader("Cookie", cookies)
                .build();
        Response response;
        try {
            response = client.newCall(request).execute();
            String responseBody = response.body().string();
            BiliBiliResponse biliBiliResponse = new GsonBuilder().create().fromJson(responseBody, BiliBiliResponse.class);
            switch (biliBiliResponse.getCode()) {
                case 0:
                    System.out.println(Instant.now() + " 一键开播成功！");
                    //返回码是0的时候，Data是对象（其他情况是数组，直接按对象反序列化会出异常），因此重新反序列化一次
                    BiliBiliResponse<Data> biliBiliResponse2 =
                            new GsonBuilder().create().fromJson(responseBody, new TypeToken<BiliBiliResponse<Data>>(){}.getType());
                    if (biliBiliResponse2.getData().getRtmp().getCode()
                            .equals(obsSetting.getSettings().getKey() )
                            && biliBiliResponse2.getData().getRtmp().getAddr()
                            .equals(obsSetting.getSettings().getServer())) {

                        System.out.println(Instant.now() + " 直播地址、直播码校验通过，无需修改，程序即将退出！");
                    } else {
                        obsSetting.getSettings().setKey( biliBiliResponse2.getData().getRtmp().getCode());
                        obsSetting.getSettings().setServer(biliBiliResponse2.getData().getRtmp().getAddr());
                        try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(obsServiceJson)))) {
                            bw.write(new GsonBuilder().create().toJson(obsSetting));
                            System.out.println(Instant.now() + " 检测到直播码不匹配，修改OBS配置文件成功，重启OBS生效。程序即将退出！");
                        } catch (IOException e) {
                            System.err.println(Instant.now() + " 检测到直播码不匹配，写入直播码配置文件时发生异常：" + e.getMessage() + "，程序即将退出；");
                            TimeUnit.DAYS.sleep(10);
                            return;
                        }
                    }
                    TimeUnit.SECONDS.sleep(1);
                    break;
                default:
                    System.err.println(Instant.now()
                            + " 请求 Bilibili 开播接口失败，错误码：" + biliBiliResponse.getCode()
                            + " 错误信息：" + biliBiliResponse.getMsg() + "，程序即将退出；");
                    TimeUnit.SECONDS.sleep(10);
            }
        } catch (IOException e) {
            System.err.println(Instant.now() + " 请求 Bilibili 接口时发生异常：" + e.getMessage() + "，程序即将退出；");
            TimeUnit.SECONDS.sleep(10);
        }
    }

    static void init(int type) throws InterruptedException {
        String jarFilePath = System.getProperty("user.dir");
        String userProfilePath = System.getProperty("user.home");
        Properties syncConfig = new Properties();
        Properties obsConfig = new Properties();
        OBSSetting obsSetting = null;
        File obsServiceJson = null;
        String obsProfileName = "未命名";
        Pattern bilibiliUid = Pattern.compile("DedeUserID=(\\d*);");
//        //这一串是获取当前目录的
//        java.net.URL url = Main.class.getProtectionDomain().getCodeSource().getLocation();
//        String jarFilePath = null;
//        try {
//            jarFilePath = java.net.URLDecoder.decode(url.getPath(), "utf-8");
//        } catch (Exception e) {
//            System.err.println(Instant.now() + " 解析JAR运行路径失败。程序即将退出，请手动关闭并提交Issue");
//            TimeUnit.DAYS.sleep(100);
//            return;
//        }
//        if (jarFilePath.endsWith(".exe") || jarFilePath.endsWith(".jar"))
//            jarFilePath = jarFilePath.substring(0, jarFilePath.lastIndexOf("/") + 1);
//        java.io.File file = new java.io.File(jarFilePath);
//        jarFilePath = file.getAbsolutePath();
        //读取配置
        File syncConfigFile = new File(jarFilePath + "\\config.ini");
        System.out.println("Try to read config from " + syncConfigFile);
        if (syncConfigFile.exists()) {
            try (InputStream in =
                         new FileInputStream(syncConfigFile)) {
                syncConfig.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            } catch (IOException e) {
                System.err.println(Instant.now() + " 读取Sync配置文件时发生IO异常：" + e.getMessage() + "，程序即将退出；");
                TimeUnit.DAYS.sleep(10);
                return;
            }
            if (syncConfig.getProperty("RoomID") != null && syncConfig.getProperty("Cookies") != null) {
                Matcher m = bilibiliUid.matcher(syncConfig.getProperty("Cookies"));
                m.find();
                System.out.println(Instant.now() + " 读取Sync配置文件成功，房间号是：" + syncConfig.getProperty("RoomID")+"，B站uid是："+m.group(1));
            } else {
                System.out.println(Instant.now() + " 错误：解析config.ini失败，程序即将退出。" +
                        "\n请确认本程序与Sync.exe处于同一目录下，并且Default Plug-ins.BiliBili插件已启动！");
                TimeUnit.SECONDS.sleep(10);
                return;
            }
        } else {
            System.out.println(Instant.now() + " 错误：没有找到config.ini，程序即将退出。" +
                    "\n请确认本程序与Sync.exe处于同一目录下，并且Default Plug-ins.BiliBili插件已启动！");
            TimeUnit.SECONDS.sleep(10);
            return;
        }
        File obsConfigFile = new File(userProfilePath + "\\AppData\\Roaming\\obs-studio\\global.ini");
        if (obsConfigFile.exists()) {
            try (InputStream in =
                         new FileInputStream(obsConfigFile)) {
                obsConfig.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            } catch (IOException e) {
                System.err.println(Instant.now() + " 读取OBS配置文件时发生IO异常：" + e.getMessage() + "，程序即将退出；");
                TimeUnit.SECONDS.sleep(10);
                return;
            }
            if (obsConfig.getProperty("ProfileDir") != null) {
                System.out.println(Instant.now() + " 读取OBS配置文件成功，当前所用配置文件名：" + obsConfig.getProperty("ProfileDir"));
                obsProfileName = obsConfig.getProperty("ProfileDir");
            }
            obsServiceJson = new File(userProfilePath + "\\AppData\\Roaming\\obs-studio\\basic\\profiles\\" + obsProfileName + "\\service.json");
            if (obsServiceJson.exists()) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(obsServiceJson)))
                ) {
                    obsSetting = new GsonBuilder().create().fromJson(br, OBSSetting.class);
                    if (obsSetting == null || obsSetting.getSettings() == null) {
                        obsSetting = new OBSSetting();
                        obsSetting.setSettings(new Settings());
                    }
                } catch (IOException e) {
                    System.err.println(Instant.now() + " 读取直播码配置文件时发生IO异常：" + e.getMessage() + "，程序即将退出；");
                    TimeUnit.SECONDS.sleep(10);
                    return;
                }
                System.out.println(Instant.now() + " 读取当前直播码配置成功。");
            } else {
                System.err.println(Instant.now() + " 读取OBS配置文件成功，但没有找到对应的直播码配置文件，程序即将退出；");
                TimeUnit.SECONDS.sleep(10);
                return;
            }

        } else {
            System.err.println(Instant.now() + " 没有检测到OBS Studio，将不会自动校验直播码/直播地址！");
        }

        switch (type) {
            case 0:
                startLive(syncConfig.getProperty("RoomID"),
                        syncConfig.getProperty("area_v2"),
                        syncConfig.getProperty("csrf_token"),
                        syncConfig.getProperty("Cookies"),
                        obsSetting,
                        obsServiceJson);
                break;

            case 1:
                stopLive(syncConfig.getProperty("RoomID"),
                        syncConfig.getProperty("area_v2"),
                        syncConfig.getProperty("csrf_token"),
                        syncConfig.getProperty("Cookies"));
                break;
        }
    }
}
