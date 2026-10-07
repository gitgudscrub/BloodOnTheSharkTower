package com.sharktower.bloodonthesharktower.setup;

import com.google.gson.*;
import com.sharktower.bloodonthesharktower.core.Script;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Pattern;

/** Resolves the site's documented script-ID and version-ID APIs without HTML scraping. */
public final class BotcScriptImport {
    private BotcScriptImport() {}
    public static final int MAX_BYTES = 1_048_576;
    private static final Pattern PAGE = Pattern.compile("/script/([1-9][0-9]*)(?:/([0-9]+(?:\\.[0-9]+)*))?(?:/download)?/?");
    private static final Pattern API = Pattern.compile("/api/(scripts|script_ids)/([1-9][0-9]*)(?:/(json))?/?");
    public record Link(URI endpoint, String version, boolean scriptId) {}
    public static Link resolve(String input) {
        URI uri;
        try { uri = URI.create(input.trim()); } catch (RuntimeException e) { throw new IllegalArgumentException("Enter a valid BotC Scripts link."); }
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null
                || !(host.equalsIgnoreCase("botcscripts.com") || host.equalsIgnoreCase("www.botcscripts.com"))
                || uri.getUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443)
                || uri.getRawQuery() != null || uri.getRawFragment() != null)
            throw new IllegalArgumentException("Only HTTPS botcscripts.com script or JSON/API links are supported.");
        var page = PAGE.matcher(uri.getRawPath());
        if (page.matches()) return new Link(URI.create("https://botcscripts.com/api/script_ids/" + page.group(1) + "/"), page.group(2), true);
        var api = API.matcher(uri.getRawPath());
        if (api.matches() && !(api.group(1).equals("script_ids") && api.group(3) != null))
            return new Link(URI.create("https://botcscripts.com/api/" + api.group(1) + "/" + api.group(2) + (api.group(3) == null ? "/" : "/json/")), null, api.group(1).equals("script_ids"));
        throw new IllegalArgumentException("Unsupported link. Paste a script page or /api/scripts/<version-id>/json/ link.");
    }
    public static String download(String input) throws Exception {
        Link link = resolve(input);
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
        JsonElement response = fetch(client, link.endpoint());
        if (link.scriptId()) {
            if (!response.isJsonObject()) throw new IllegalArgumentException("BotC Scripts returned an unsupported script response.");
            JsonObject script = response.getAsJsonObject();
            JsonElement target = link.version() == null ? script.get("latest_version") :
                    (script.has("versions") && script.get("versions").isJsonObject() ? script.getAsJsonObject("versions").get(link.version()) : null);
            if (target == null || !target.isJsonPrimitive()) throw new IllegalArgumentException("That script/version was not found on BotC Scripts.");
            Link version = resolve(target.getAsString());
            if (version.scriptId()) throw new IllegalArgumentException("BotC Scripts returned an unsupported version link.");
            response = fetch(client, version.endpoint());
        }
        if (response.isJsonObject() && response.getAsJsonObject().has("content")) response = response.getAsJsonObject().get("content");
        String json = response.toString();
        validate(json);
        return json;
    }
    private static JsonElement fetch(HttpClient client, URI uri) throws Exception {
        var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20)).header("Accept", "application/json")
                .header("User-Agent", "BloodOnTheSharktower/1.0.2 (single-script import)").GET().build();
        for (int redirects = 0; redirects < 3; redirects++) {
            var response = client.send(request, info -> new LimitedBody());
            if (response.statusCode() >= 300 && response.statusCode() < 400) {
                URI next = uri.resolve(response.headers().firstValue("Location").orElseThrow(() -> new IllegalArgumentException("BotC Scripts redirected without a destination.")));
                resolve(next.toString()); // validate host and supported API path before following
                uri = next;
                request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20)).header("Accept", "application/json")
                        .header("User-Agent", "BloodOnTheSharktower/1.0.2 (single-script import)").GET().build();
                continue;
            }
            if (response.statusCode() != 200) throw new IllegalArgumentException("BotC Scripts returned HTTP " + response.statusCode() + ". Try again later or add the downloaded JSON to the custom-scripts folder.");
            try { return JsonParser.parseString(new String(response.body(), StandardCharsets.UTF_8)); }
            catch (RuntimeException e) { throw new IllegalArgumentException("BotC Scripts did not return valid JSON. The site may be unavailable."); }
        }
        throw new IllegalArgumentException("Too many BotC Scripts redirects.");
    }
    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final java.util.concurrent.CompletableFuture<byte[]> result = new java.util.concurrent.CompletableFuture<>();
        private final java.io.ByteArrayOutputStream data = new java.io.ByteArrayOutputStream();
        private java.util.concurrent.Flow.Subscription subscription;
        public java.util.concurrent.CompletionStage<byte[]> getBody() { return result; }
        public void onSubscribe(java.util.concurrent.Flow.Subscription s) { subscription=s; s.request(1); }
        public void onNext(java.util.List<java.nio.ByteBuffer> parts) {
            for (var part : parts) {
                if (data.size() + part.remaining() > MAX_BYTES) {
                    subscription.cancel(); result.completeExceptionally(new IllegalArgumentException("Downloaded script exceeds 1 MiB.")); return;
                }
                byte[] bytes = new byte[part.remaining()]; part.get(bytes); data.writeBytes(bytes);
            }
            subscription.request(1);
        }
        public void onError(Throwable error) { result.completeExceptionally(error); }
        public void onComplete() { result.complete(data.toByteArray()); }
    }
    public static Script validate(String json) {
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) throw new IllegalArgumentException("Script is too large (maximum 1 MiB).");
        Script script = Script.fromJson(json).orElseThrow(() -> new IllegalArgumentException("Invalid Clocktower script JSON."));
        if (script.allRoles().isEmpty()) throw new IllegalArgumentException("Script has no supported playable characters.");
        JsonElement root;
        try { root = JsonParser.parseString(json); } catch (RuntimeException e) { throw new IllegalArgumentException("Invalid script JSON."); }
        if (!root.isJsonArray()) throw new IllegalArgumentException("Expected a Clocktower JSON array.");
        for (JsonElement entry : root.getAsJsonArray()) {
            String id;
            if (entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isString()) id = entry.getAsString();
            else if (entry.isJsonObject() && entry.getAsJsonObject().has("id") && entry.getAsJsonObject().get("id").isJsonPrimitive()) id = entry.getAsJsonObject().get("id").getAsString();
            else throw new IllegalArgumentException("Unsupported script entry: every character needs an id.");
            if (!id.equals("_meta") && script.getScriptRole(id).isEmpty()) throw new IllegalArgumentException("Unsupported character: " + id + ". Supply its full homebrew definition.");
        }
        return script;
    }
    public static String filename(String name) {
        String safe = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        if (safe.isBlank()) safe = "custom-script";
        return safe.substring(0, Math.min(80, safe.length())) + ".json";
    }
}
