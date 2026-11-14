# AI Server (Android)

An Android app that runs an OpenAI-compatible HTTP server on your phone, so other devices on your Wi-Fi can send it chat requests. It also serves a small web dashboard and shows request stats and logs. Built with Kotlin and Jetpack Compose, and originally generated in Google AI Studio.

## Read this first: what it actually does

**No AI model runs on the phone.** Here is how the models in `/v1/models` really behave:

| Model id | What you get |
|---|---|
| `gemini-*` (for example `gemini-2.5-flash`) | A real Gemini response, proxied through the phone, **only if** you set an upstream Gemini API key in the app. Without a key it falls back to the canned engine below. |
| `local-device-ai`, `deepseek-r1-mobile`, `llama-3.2-3b-instruct`, `phi-3-mini-4k`, `gpt-4o-mini` | Built-in profiles. They return template replies picked by keyword matching (greetings, "python", math like `2 + 2`, "summarize"...). They are not real Llama, Phi or DeepSeek models. |

Also:

- `/v1/embeddings` returns random numbers, not real embeddings.
- `/v1/completions` uses the same canned engine as the local profiles.
- Token counts are rough (word counts), not real tokenizer counts.

So today this is useful as a Gemini proxy and as a stand-in server for testing OpenAI-style clients, not as a way to run models on your phone.

## Endpoints

| Route | What it does |
|---|---|
| `GET /` | Web dashboard with a prompt tester |
| `GET /health` (or `/v1/health`) | Status, uptime, device model |
| `GET /v1/models` | List of the model ids above |
| `POST /v1/chat/completions` | Chat, with `"stream": true` supported (SSE) |
| `POST /v1/completions` | Text completion (canned engine) |
| `POST /v1/embeddings` | Random placeholder vector |

## Settings

| Setting | Default |
|---|---|
| Port | `8080` |
| Require API key | off (when on, `/v1` routes need `Authorization: Bearer <key>`; the default key is `sk-android-ai-server`, change it) |
| Default model | `local-device-ai` |
| Upstream Gemini key | empty |
| Custom system prompt | empty |
| Keep awake | on |

The server runs as a foreground service, so Android shows a notification while it is running.

## Try it

From a laptop on the same Wi-Fi, using the IP address the app shows:

```bash
curl -X POST "http://<phone-ip>:8080/v1/chat/completions" \
  -H "Content-Type: application/json" \
  -d '{"model": "local-device-ai", "messages": [{"role": "user", "content": "hello"}]}'
```

## Security

The server speaks plain HTTP, allows requests from any origin, and has no API key requirement until you turn it on. Anyone on the same network can use it, and if you set a Gemini key they can spend it. Turn on the API key and only run it on a network you trust.

## Build

1. Open the project in Android Studio.
2. Optional: copy `.env.example` to `.env` and uncomment `GEMINI_API_KEY` if you want a key packaged into the build. `.env` is git-ignored.
3. Run the `app` configuration on a device or emulator (minSdk 24, target and compile SDK 36).
