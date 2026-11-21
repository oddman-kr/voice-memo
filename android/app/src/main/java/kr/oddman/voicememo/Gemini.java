package kr.oddman.voicememo;

import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
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
    static final String BASE = "https://generativelanguage.googleapis.com";
    static final String MIME = "audio/aac";
    static final long INLINE_MAX = 3_500_000;

    public static class Action {
        public String type, due, text;
        public long itemId;
    }

    public static class Result {
        public String transcript = "", title = null, summary = null;
        public List<Db.Item> items = new ArrayList<>();
        public List<Action> actions = new ArrayList<>();
    }

    static class HttpError extends Exception {
        final int code;
        HttpError(int code, String msg) { super(msg); this.code = code; }
    }

    private static JSONObject s(String type) throws Exception { return new JSONObject().put("type", type); }

    public static Result analyze(String apiKey, String model, File audio, List<String> cats, long createdMs,
                                 boolean meeting, List<Db.Item> open) throws Exception {
        String when = new SimpleDateFormat("yyyy-MM-dd (E) HH:mm", Locale.KOREAN).format(new Date(createdMs));
        StringBuilder catList = new StringBuilder();
        for (String c : cats) catList.append("- ").append(c).append('\n');

        StringBuilder p = new StringBuilder();
        if (meeting) {
            p.append("너는 회의록 비서야. 첨부한 한국어 회의 녹음을 듣고 아래 JSON으로 정리해.\n");
        } else {
            p.append("너는 개인 음성 메모 정리 도우미야. 첨부한 한국어 녹음을 듣고 아래 JSON으로 정리해.\n");
        }
        p.append("녹음한 시각: ").append(when).append("\n\n");
        p.append("카테고리(반드시 이 중 하나를 그대로 써):\n").append(catList).append('\n');
        p.append("공통 규칙:\n")
                .append("- transcript: 녹음 내용을 받아 적어. 말버릇(음, 어)은 빼도 돼.")
                .append(meeting ? " 말하는 사람이 바뀌면 줄을 바꾸고, 구분되면 '화자1:', '화자2:'처럼 앞에 붙여.\n" : "\n")
                .append("- items의 text는 짧고 분명하게 (예: '건전지 AA 4개', '엄마한테 5만원 보내기', '정형외과 예약').\n")
                .append("- 쿠팡에서 살 것은 '쿠팡', 다이소에서 살 것은 '다이소', 언제까지 해야 하는 일이나 일정은 '할 일', 누군가에게 돈을 보내거나 갚아야 하면 '송금'. 그 밖의 카테고리는 이름 뜻에 맞게 고르고, 어디에도 안 맞으면 '").append(Prefs.OTHER).append("'.\n")
                .append("- due: 기한·날짜가 나오면 녹음한 시각 기준으로 계산해서 'YYYY-MM-DD' (시간도 있으면 'YYYY-MM-DD HH:mm', 24시간제). 없으면 빈 문자열.\n")
                .append("- repeat: '매일/매주/매달/매년' 같은 반복이면 daily, weekly, monthly, yearly 중 하나. due에는 다가오는 첫 날짜. 반복이 아니면 빈 문자열.\n")
                .append("- amount: 금액은 '50,000원'처럼. person: 관련된 사람의 이름이나 호칭(엄마, 팀장님 등). place: 병원·가게·관공서 같은 장소 이름이 나오면 그 이름(예: '정형외과', '주민센터'). 없으면 빈 문자열.\n");
        if (meeting) {
            p.append("회의 규칙:\n")
                    .append("- title: 회의 내용을 나타내는 짧은 제목 (20자 이내).\n")
                    .append("- summary: 아래 형식의 글. 각 줄은 '• '로 시작.\n  요약\n  • 핵심 내용 3~6줄\n\n  결정한 것\n  • 결정 사항 (없으면 이 부분 생략)\n")
                    .append("- items: 회의에서 나온 할 일(누가 언제까지 무엇을). 담당자는 person에. 대부분 '할 일'이지만 살 것·송금은 맞는 카테고리로.\n")
                    .append("- actions는 빈 배열.\n");
        } else {
            p.append("메모 규칙:\n")
                    .append("- items: 녹음 속 새로 생긴 살 것·할 일·보낼 돈을 하나씩 나눠. 여러 개면 여러 항목으로, 같은 가게에서 살 물건도 물건마다 따로.\n")
                    .append("- title, summary는 빈 문자열.\n")
                    .append("- 녹음이 비었거나 알아들을 수 없으면 items는 빈 배열.\n");
            if (open != null && !open.isEmpty()) {
                p.append("\n지금 남아 있는 항목 (id | 카테고리 | 내용 | 기한):\n");
                for (Db.Item it : open) {
                    p.append(it.id).append(" | ").append(it.category).append(" | ").append(it.text)
                            .append(" | ").append(it.due == null ? "-" : it.due).append('\n');
                }
                p.append("\n기존 항목 처리 규칙 (actions):\n")
                        .append("- 위 목록의 항목을 이미 했다/샀다/보냈다/끝냈다고 말하면 {type:'complete', item_id}.\n")
                        .append("- 기한이나 내용을 바꾸라고 하면 {type:'update', item_id, due(바뀐 기한, 위 형식), text(내용이 바뀌면 새 내용, 아니면 빈 문자열)}.\n")
                        .append("- 취소하거나 필요 없어졌다고 하면 {type:'delete', item_id}.\n")
                        .append("- 기존 항목에 대한 말은 items에 다시 넣지 마. 어느 항목인지 확실하지 않으면 새 항목으로 넣어.\n");
            } else {
                p.append("- actions는 빈 배열.\n");
            }
        }

        JSONObject itemProps = new JSONObject()
                .put("category", s("STRING")).put("text", s("STRING")).put("due", s("STRING"))
                .put("amount", s("STRING")).put("person", s("STRING")).put("repeat", s("STRING")).put("place", s("STRING"));
        JSONObject actProps = new JSONObject()
                .put("type", s("STRING")).put("item_id", s("INTEGER")).put("due", s("STRING")).put("text", s("STRING"));
        JSONObject schema = new JSONObject()
                .put("type", "OBJECT")
                .put("properties", new JSONObject()
                        .put("transcript", s("STRING"))
                        .put("title", s("STRING"))
                        .put("summary", s("STRING"))
                        .put("items", s("ARRAY").put("items", s("OBJECT").put("properties", itemProps)
                                .put("required", new JSONArray().put("category").put("text"))))
                        .put("actions", s("ARRAY").put("items", s("OBJECT").put("properties", actProps)
                                .put("required", new JSONArray().put("type").put("item_id")))))
                .put("required", new JSONArray().put("transcript").put("items").put("actions"));

        JSONObject audioPart;
        if (audio.length() > INLINE_MAX) {
            String uri = upload(apiKey, audio);
            audioPart = new JSONObject().put("file_data", new JSONObject().put("mime_type", MIME).put("file_uri", uri));
        } else {
            byte[] bytes = Files.readAllBytes(audio.toPath());
            audioPart = new JSONObject().put("inline_data", new JSONObject().put("mime_type", MIME)
                    .put("data", Base64.encodeToString(bytes, Base64.NO_WRAP)));
        }

        JSONArray parts = new JSONArray().put(new JSONObject().put("text", p.toString())).put(audioPart);
        JSONObject body = new JSONObject()
                .put("contents", new JSONArray().put(new JSONObject().put("role", "user").put("parts", parts)))
                .put("generationConfig", new JSONObject()
                        .put("temperature", 0.2)
                        .put("responseMimeType", "application/json")
                        .put("responseSchema", schema));

        String resp = generate(meeting ? Prefs.MEETING_MODEL : model, apiKey, body, meeting);
        JSONObject root = new JSONObject(resp);
        JSONArray cands = root.optJSONArray("candidates");
        if (cands == null || cands.length() == 0) throw new Exception("AI가 답을 주지 않았어요. 다시 시도해 주세요");
        JSONObject content = cands.getJSONObject(0).optJSONObject("content");
        if (content == null) throw new Exception("AI가 답을 끝내지 못했어요. 다시 시도해 주세요");
        JSONArray outParts = content.getJSONArray("parts");
        StringBuilder txt = new StringBuilder();
        for (int i = 0; i < outParts.length(); i++) {
            JSONObject op = outParts.getJSONObject(i);
            if (!op.optBoolean("thought", false)) txt.append(op.optString("text", ""));
        }
        JSONObject data;
        try {
            data = new JSONObject(txt.toString().trim());
        } catch (Exception e) {
            throw new Exception("AI 답을 읽지 못했어요. 녹음이 너무 길면 나눠서 녹음해 주세요");
        }

        Result r = new Result();
        r.transcript = data.optString("transcript", "").trim();
        r.title = nz(data.optString("title", ""));
        r.summary = nz(data.optString("summary", ""));
        JSONArray arr = data.optJSONArray("items");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Db.Item it = new Db.Item();
                it.text = o.optString("text", "").trim();
                if (it.text.isEmpty()) continue;
                String cat = o.optString("category", "").trim();
                it.category = cats.contains(cat) ? cat : Prefs.OTHER;
                it.due = nz(o.optString("due", ""));
                it.amount = nz(o.optString("amount", ""));
                it.person = nz(o.optString("person", ""));
                it.place = nz(o.optString("place", ""));
                String rep = o.optString("repeat", "").trim().toLowerCase(Locale.ROOT);
                it.repeat = Recur.valid(rep) && it.due != null ? rep : null;
                r.items.add(it);
            }
        }
        JSONArray acts = data.optJSONArray("actions");
        if (acts != null && !meeting) {
            for (int i = 0; i < acts.length(); i++) {
                JSONObject o = acts.getJSONObject(i);
                Action a = new Action();
                a.type = o.optString("type", "").trim().toLowerCase(Locale.ROOT);
                a.itemId = o.optLong("item_id", 0);
                a.due = nz(o.optString("due", ""));
                a.text = nz(o.optString("text", ""));
                if (a.itemId > 0 && ("complete".equals(a.type) || "update".equals(a.type) || "delete".equals(a.type)))
                    r.actions.add(a);
            }
        }
        return r;
    }

    /** 모델이 없으면(404) 다음 후보로, 생각 설정을 모르면(400) 빼고 다시 */
    static String generate(String model, String key, JSONObject body, boolean meeting) throws Exception {
        List<String> models = new ArrayList<>();
        models.add(model);
        for (String f : Prefs.FALLBACKS) if (!models.contains(f)) models.add(f);
        HttpError last = null;
        for (String m : models) {
            JSONObject gen = body.getJSONObject("generationConfig");
            if (meeting) gen.put("thinkingConfig", new JSONObject().put("thinkingLevel", "low"));
            else gen.remove("thinkingConfig");
            try {
                return post(m, key, body.toString());
            } catch (HttpError e) {
                last = e;
                String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
                if (e.code == 400 && msg.contains("think") && gen.has("thinkingConfig")) {
                    gen.remove("thinkingConfig");
                    try { return post(m, key, body.toString()); } catch (HttpError e2) { last = e2; }
                }
                if (last.code != 404 && !(last.code == 400 && msg.contains("model"))) throw friendly(last);
            }
        }
        throw friendly(last);
    }

    static String nz(String s) {
        s = s == null ? "" : s.trim();
        return s.isEmpty() || s.equalsIgnoreCase("null") || s.equals("-") ? null : s;
    }

    static Exception friendly(HttpError e) {
        String m = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
        if (e.code == 400 && m.contains("api key")) return new Exception("API 키가 올바르지 않아요. 설정에서 확인해 주세요");
        if (e.code == 401 || e.code == 403) return new Exception("API 키를 쓸 수 없어요. 설정에서 확인해 주세요");
        if (e.code == 413 || m.contains("too large")) return new Exception("녹음이 너무 커요");
        if (e.code == 429) return new Exception("무료 사용량 한도에 걸렸어요. 잠시 후 다시 정리해 주세요");
        if (e.code >= 500) return new Exception("구글 서버가 잠시 불안정해요. 나중에 다시 정리해 주세요");
        return new Exception("HTTP " + e.code + ": " + e.getMessage());
    }

    private static HttpURLConnection open(String url, String key, String method) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(20000);
        c.setReadTimeout(method.equals("GET") ? 30000 : 180000);
        c.setRequestMethod(method);
        c.setRequestProperty("x-goog-api-key", key);
        return c;
    }

    private static String body(HttpURLConnection c) throws Exception {
        int code = c.getResponseCode();
        InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
        String b = is == null ? "" : readAll(is);
        if (code >= 400) {
            String msg = b;
            try { msg = new JSONObject(b).getJSONObject("error").optString("message", b); } catch (Exception ignored) { }
            if (msg.length() > 200) msg = msg.substring(0, 200);
            c.disconnect();
            throw new HttpError(code, msg);
        }
        return b;
    }

    static String post(String model, String key, String json) throws Exception {
        HttpURLConnection c = open(BASE + "/v1beta/models/" + model + ":generateContent", key, "POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        byte[] data = json.getBytes(StandardCharsets.UTF_8);
        c.setFixedLengthStreamingMode(data.length);
        try (OutputStream os = c.getOutputStream()) { os.write(data); }
        String b = body(c);
        c.disconnect();
        return b;
    }

    /** 큰 녹음은 Files API로 올리고 file uri를 돌려줘요 */
    static String upload(String key, File f) throws Exception {
        HttpURLConnection c = open(BASE + "/upload/v1beta/files", key, "POST");
        c.setDoOutput(true);
        c.setRequestProperty("X-Goog-Upload-Protocol", "resumable");
        c.setRequestProperty("X-Goog-Upload-Command", "start");
        c.setRequestProperty("X-Goog-Upload-Header-Content-Length", String.valueOf(f.length()));
        c.setRequestProperty("X-Goog-Upload-Header-Content-Type", MIME);
        c.setRequestProperty("Content-Type", "application/json");
        byte[] meta = new JSONObject().put("file", new JSONObject().put("display_name", f.getName())).toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream os = c.getOutputStream()) { os.write(meta); }
        body(c);
        String upUrl = c.getHeaderField("x-goog-upload-url");
        c.disconnect();
        if (upUrl == null) throw new Exception("녹음을 올리지 못했어요");

        HttpURLConnection u = (HttpURLConnection) new URL(upUrl).openConnection();
        u.setConnectTimeout(20000);
        u.setReadTimeout(300000);
        u.setRequestMethod("POST");
        u.setDoOutput(true);
        u.setFixedLengthStreamingMode(f.length());
        u.setRequestProperty("X-Goog-Upload-Offset", "0");
        u.setRequestProperty("X-Goog-Upload-Command", "upload, finalize");
        try (OutputStream os = u.getOutputStream(); InputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
        }
        JSONObject file = new JSONObject(body(u)).getJSONObject("file");
        u.disconnect();
        String uri = file.getString("uri");
        String name = file.getString("name");
        String state = file.optString("state", "ACTIVE");
        for (int i = 0; i < 150 && "PROCESSING".equals(state); i++) {
            Thread.sleep(2000);
            HttpURLConnection g = open(BASE + "/v1beta/" + name, key, "GET");
            state = new JSONObject(body(g)).optString("state", "ACTIVE");
            g.disconnect();
        }
        if ("FAILED".equals(state)) throw new Exception("구글에서 녹음 파일을 처리하지 못했어요");
        return uri;
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
