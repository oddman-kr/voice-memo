package kr.oddman.voicememo;

import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class Gemini {
    public static class Result {
        public String transcript = "";
        public List<Db.Item> items = new ArrayList<>();
    }

    static class HttpError extends Exception {
        final int code;
        HttpError(int code, String msg) { super(msg); this.code = code; }
    }

    public static Result classify(String apiKey, String model, File audio, List<String> cats, long createdMs) throws Exception {
        byte[] bytes = Files.readAllBytes(audio.toPath());
        String b64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
        String when = new SimpleDateFormat("yyyy-MM-dd (E) HH:mm", Locale.KOREAN).format(new Date(createdMs));

        StringBuilder catList = new StringBuilder();
        for (String c : cats) catList.append("- ").append(c).append('\n');

        String prompt = "너는 개인 음성 메모 정리 도우미야. 첨부한 한국어 녹음을 듣고 아래 JSON으로 정리해.\n"
                + "녹음한 시각: " + when + "\n\n"
                + "카테고리(반드시 이 중 하나를 그대로 써):\n" + catList + "\n"
                + "규칙:\n"
                + "1. transcript: 녹음 내용을 그대로 받아 적어. 말버릇(음, 어)은 빼도 돼.\n"
                + "2. items: 녹음 속 살 것·할 일·보낼 돈을 하나씩 나눠. 한 녹음에 여러 개면 여러 항목으로 만들어. 같은 가게에서 살 물건이 여러 개여도 물건마다 따로 만들어.\n"
                + "3. 쿠팡에서 살 것은 '쿠팡', 다이소에서 살 것은 '다이소', 언제까지 해야 하는 일이나 일정은 '할 일', 누군가에게 돈을 보내거나 갚아야 하면 '송금'. 그 밖의 카테고리는 이름 뜻에 맞게 고르고, 어디에도 안 맞으면 '" + Prefs.OTHER + "'.\n"
                + "4. text는 짧고 분명하게 (예: '건전지 AA 4개', '엄마한테 5만원 보내기', '건강검진 예약').\n"
                + "5. due: 기한·날짜가 나오면 녹음한 시각 기준으로 계산해서 'YYYY-MM-DD' (시간도 있으면 'YYYY-MM-DD HH:mm'). 없으면 빈 문자열.\n"
                + "6. amount: 금액이 나오면 '50,000원'처럼. person: 사람이 나오면 그 이름이나 호칭. 없으면 빈 문자열.\n"
                + "7. 녹음이 비었거나 알아들을 수 없으면 items는 빈 배열.";

        JSONObject itemProps = new JSONObject()
                .put("category", new JSONObject().put("type", "STRING"))
                .put("text", new JSONObject().put("type", "STRING"))
                .put("due", new JSONObject().put("type", "STRING"))
                .put("amount", new JSONObject().put("type", "STRING"))
                .put("person", new JSONObject().put("type", "STRING"));
        JSONObject schema = new JSONObject()
                .put("type", "OBJECT")
                .put("properties", new JSONObject()
                        .put("transcript", new JSONObject().put("type", "STRING"))
                        .put("items", new JSONObject().put("type", "ARRAY").put("items",
                                new JSONObject().put("type", "OBJECT").put("properties", itemProps)
                                        .put("required", new JSONArray().put("category").put("text")))))
                .put("required", new JSONArray().put("transcript").put("items"));

        JSONArray parts = new JSONArray()
                .put(new JSONObject().put("text", prompt))
                .put(new JSONObject().put("inline_data", new JSONObject().put("mime_type", "audio/aac").put("data", b64)));
        JSONObject body = new JSONObject()
                .put("contents", new JSONArray().put(new JSONObject().put("role", "user").put("parts", parts)))
                .put("generationConfig", new JSONObject()
                        .put("temperature", 0.2)
                        .put("responseMimeType", "application/json")
                        .put("responseSchema", schema));

        String resp;
        try {
            resp = post(model, apiKey, body.toString());
        } catch (HttpError e) {
            if (e.code == 404 && !Prefs.FALLBACK_MODEL.equals(model)) resp = post(Prefs.FALLBACK_MODEL, apiKey, body.toString());
            else throw friendly(e);
        }

        JSONObject root = new JSONObject(resp);
        JSONArray cands = root.optJSONArray("candidates");
        if (cands == null || cands.length() == 0) throw new Exception("AI가 답을 주지 않았어요");
        JSONArray outParts = cands.getJSONObject(0).getJSONObject("content").getJSONArray("parts");
        StringBuilder txt = new StringBuilder();
        for (int i = 0; i < outParts.length(); i++) {
            JSONObject p = outParts.getJSONObject(i);
            if (!p.optBoolean("thought", false)) txt.append(p.optString("text", ""));
        }
        JSONObject data = new JSONObject(txt.toString().trim());

        Result r = new Result();
        r.transcript = data.optString("transcript", "").trim();
        JSONArray arr = data.optJSONArray("items");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Db.Item it = new Db.Item();
                it.text = o.optString("text", "").trim();
                if (it.text.isEmpty()) continue;
                String cat = o.optString("category", "").trim();
                it.category = cats.contains(cat) ? cat : Prefs.OTHER;
                it.due = emptyToNull(o.optString("due", ""));
                it.amount = emptyToNull(o.optString("amount", ""));
                it.person = emptyToNull(o.optString("person", ""));
                r.items.add(it);
            }
        }
        return r;
    }

    static String emptyToNull(String s) {
        s = s == null ? "" : s.trim();
        return s.isEmpty() || s.equalsIgnoreCase("null") ? null : s;
    }

    static Exception friendly(HttpError e) {
        if (e.code == 400 && e.getMessage() != null && e.getMessage().toLowerCase(Locale.ROOT).contains("api key"))
            return new Exception("API 키가 올바르지 않아요. 설정에서 확인해 주세요");
        if (e.code == 401 || e.code == 403) return new Exception("API 키를 쓸 수 없어요. 설정에서 확인해 주세요");
        if (e.code == 429) return new Exception("무료 사용량 한도에 걸렸어요. 잠시 후 '다시 정리'를 눌러 주세요");
        if (e.code >= 500) return new Exception("구글 서버가 잠시 불안정해요. 나중에 다시 정리해 주세요");
        return new Exception("HTTP " + e.code + ": " + e.getMessage());
    }

    static String post(String model, String key, String json) throws Exception {
        URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent");
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setConnectTimeout(20000);
        c.setReadTimeout(120000);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        c.setRequestProperty("x-goog-api-key", key);
        try (OutputStream os = c.getOutputStream()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }
        int code = c.getResponseCode();
        InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
        String body = is == null ? "" : readAll(is);
        c.disconnect();
        if (code >= 400) {
            String msg = body;
            try { msg = new JSONObject(body).getJSONObject("error").optString("message", body); } catch (Exception ignored) { }
            if (msg.length() > 200) msg = msg.substring(0, 200);
            throw new HttpError(code, msg);
        }
        return body;
    }

    static String readAll(InputStream is) throws Exception {
        try (InputStream in = is) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return bo.toString("UTF-8");
        }
    }
}
