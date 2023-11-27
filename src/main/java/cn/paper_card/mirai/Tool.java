package cn.paper_card.mirai;


import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import org.jetbrains.annotations.NotNull;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

class Tool {

    Tool() {
        this.gson = new Gson();
    }

    static byte @NotNull [] decodeHex(@NotNull String hexStr) {
        final HexFormat hexFormat = HexFormat.of().withUpperCase();
        return hexFormat.parseHex(hexStr);
    }

    static @NotNull String encodeHex(byte @NotNull [] bytes) {
        final HexFormat hexFormat = HexFormat.of().withUpperCase();
        return hexFormat.formatHex(bytes);
    }

    static byte @NotNull [] md5Digest(byte @NotNull [] bytes) {
        final MessageDigest md5;
        try {
            md5 = MessageDigest.getInstance("md5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }

        md5.update(bytes);

        return md5.digest();
    }

    private static void closeInputStream(@NotNull InputStream inputStream, @NotNull InputStreamReader reader,
                                         @NotNull BufferedReader bufferedReader) throws IOException {
        IOException exception = null;

        try {
            bufferedReader.close();
        } catch (IOException e) {
            exception = e;
        }

        try {
            reader.close();
        } catch (IOException e) {
            exception = e;
        }


        try {
            inputStream.close();
        } catch (IOException e) {
            exception = e;
        }

        if (exception != null) throw exception;
    }


    static @NotNull String readToString(@NotNull File file) throws IOException {
        final FileInputStream inputStream = new FileInputStream(file);

        final InputStreamReader inputStreamReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);

        final BufferedReader reader = new BufferedReader(inputStreamReader);

        final StringBuilder sb = new StringBuilder();

        String line;

        try {
            while ((line = reader.readLine()) != null) {
                sb.append(line);
                sb.append('\n' );
            }
        } catch (IOException e) {

            try {
                closeInputStream(inputStream, inputStreamReader, reader);
            } catch (IOException ignored) {
            }

            throw e;
        }

        closeInputStream(inputStream, inputStreamReader, reader);

        return sb.toString();
    }


    private final @NotNull Gson gson;


    @NotNull String getPublicIp() throws Exception {

        // https://openapi.lddgo.net/base/gtool/api/v1/GetIp
        final HttpsURLConnection connection = getHttpsURLConnection();

        final InputStream inputStream = connection.getInputStream();

        final InputStreamReader inputStreamReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);

        final BufferedReader bufferedReader = new BufferedReader(inputStreamReader);


        final StringBuilder sb = new StringBuilder();

        String line;
        try {
            while ((line = bufferedReader.readLine()) != null) {
                sb.append(line);
                sb.append('\n' );
            }

        } catch (IOException e) {
            try {
                closeInputStream(inputStream, inputStreamReader, bufferedReader);
            } catch (IOException ignored) {
            }
            throw e;
        }


        closeInputStream(inputStream, inputStreamReader, bufferedReader);

        connection.disconnect();

        final String json = sb.toString();

        final JsonObject jsonObject;
        try {
            jsonObject = this.gson.fromJson(json, JsonObject.class);
        } catch (JsonSyntaxException e) {
            throw new Exception("解析JSON时错误！", e);
        }


        final JsonElement code = jsonObject.get("code");
        if (code == null) throw new Exception("JSON对象中没有code元素！");

        final int codeInt = code.getAsInt();

        if (codeInt != 0) throw new Exception("状态码不为0");

        final JsonObject dataObject = jsonObject.get("data").getAsJsonObject();
        final JsonElement ipEle = dataObject.get("ip");
        return ipEle.getAsString();
    }

    @NotNull
    private static HttpsURLConnection getHttpsURLConnection() throws IOException {
        final URL url;
        try {
            url = new URL("https://openapi.lddgo.net/base/gtool/api/v1/GetIp");
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }

        final HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();

        connection.setDoInput(true);
        connection.setDoOutput(false);
        connection.setConnectTimeout(2000);
        connection.setReadTimeout(2000);

        connection.connect();
        return connection;
    }

}
