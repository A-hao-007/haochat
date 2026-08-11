import gc
import json
import os
import tempfile
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from threading import Lock, Timer

from fastembed import TextEmbedding
from fastembed.common.model_description import ModelSource, PoolingType
from faster_whisper import WhisperModel


MODEL_NAME = os.getenv("EMBEDDING_MODEL", "BAAI/bge-small-zh-v1.5")
CACHE_DIR = os.getenv("EMBEDDING_CACHE_DIR", "/models")
API_KEY = os.getenv("EMBEDDING_API_KEY", "")
THREADS = max(1, int(os.getenv("EMBEDDING_THREADS", "2")))
WHISPER_MODEL_NAME = os.getenv("WHISPER_MODEL", "base")
WHISPER_CACHE_DIR = os.getenv("WHISPER_CACHE_DIR", os.path.join(CACHE_DIR, "whisper"))
WHISPER_THREADS = max(1, int(os.getenv("WHISPER_THREADS", str(THREADS))))
MAX_AUDIO_BYTES = 10 * 1024 * 1024

RUNTIME_MODEL_NAME = MODEL_NAME
if MODEL_NAME == "BAAI/bge-small-zh-v1.5":
    RUNTIME_MODEL_NAME = "haochat/bge-small-zh-v1.5"
    TextEmbedding.add_custom_model(
        model=RUNTIME_MODEL_NAME,
        pooling=PoolingType.CLS,
        normalization=True,
        sources=ModelSource(
            url="https://storage.googleapis.com/qdrant-fastembed/fast-bge-small-zh-v1.5.tar.gz",
            _deprecated_tar_struct=True,
        ),
        dim=512,
        model_file="model_optimized.onnx",
    )
else:
    supported = {item["model"] for item in TextEmbedding.list_supported_models()}
    if MODEL_NAME not in supported:
        TextEmbedding.add_custom_model(
            model=MODEL_NAME,
            pooling=PoolingType.MEAN,
            normalization=True,
            sources=ModelSource(hf=MODEL_NAME),
            dim=384,
            model_file="onnx/model_O4.onnx",
        )

model = TextEmbedding(
    model_name=RUNTIME_MODEL_NAME,
    cache_dir=CACHE_DIR,
    threads=THREADS,
)
model_lock = Lock()
whisper_lock = Lock()
whisper_model = None
whisper_cleanup_timer = None


def get_whisper_model():
    global whisper_model
    if whisper_model is None:
        whisper_model = WhisperModel(
            WHISPER_MODEL_NAME,
            device="cpu",
            compute_type="int8",
            cpu_threads=WHISPER_THREADS,
            download_root=WHISPER_CACHE_DIR,
        )
    return whisper_model


def schedule_whisper_cleanup():
    global whisper_cleanup_timer
    if whisper_cleanup_timer is not None:
        whisper_cleanup_timer.cancel()
    whisper_cleanup_timer = Timer(300, unload_whisper_model)
    whisper_cleanup_timer.daemon = True
    whisper_cleanup_timer.start()


def unload_whisper_model():
    global whisper_model, whisper_cleanup_timer
    with whisper_lock:
        whisper_model = None
        whisper_cleanup_timer = None
        gc.collect()
        print("Speech model unloaded after 5 minutes idle", flush=True)


class Handler(BaseHTTPRequestHandler):
    server_version = "HaoChatEmbedding/1.0"

    def do_GET(self):
        if self.path == "/healthz":
            self.send_json(200, {"status": "ok", "model": MODEL_NAME})
        else:
            self.send_json(404, {"error": "not_found"})

    def do_POST(self):
        if self.path not in ("/v1/embeddings", "/embeddings", "/v1/audio/transcriptions/raw"):
            self.send_json(404, {"error": "not_found"})
            return
        if API_KEY and self.headers.get("Authorization") != f"Bearer {API_KEY}":
            self.send_json(401, {"error": {"message": "unauthorized"}})
            return
        if self.path == "/v1/audio/transcriptions/raw":
            self.transcribe_audio()
            return
        self.create_embeddings()

    def create_embeddings(self):
        try:
            length = int(self.headers.get("Content-Length", "0"))
            body = json.loads(self.rfile.read(length))
            inputs = body.get("input", [])
            if isinstance(inputs, str):
                inputs = [inputs]
            if not isinstance(inputs, list) or not inputs or any(not isinstance(value, str) for value in inputs):
                raise ValueError("input must be a non-empty string or string array")
            if any(len(value) > 8000 for value in inputs) or len(inputs) > 32:
                raise ValueError("embedding request is too large")
            with model_lock:
                vectors = [vector.tolist() for vector in model.embed(inputs, batch_size=min(32, len(inputs)))]
            data = [
                {"object": "embedding", "index": index, "embedding": vector}
                for index, vector in enumerate(vectors)
            ]
            self.send_json(200, {
                "object": "list",
                "model": MODEL_NAME,
                "data": data,
                "usage": {"prompt_tokens": 0, "total_tokens": 0},
            })
        except (ValueError, json.JSONDecodeError) as exc:
            self.send_json(400, {"error": {"message": str(exc)}})
        except Exception:
            self.send_json(500, {"error": {"message": "embedding_failed"}})

    def transcribe_audio(self):
        temp_path = None
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if length <= 0 or length > MAX_AUDIO_BYTES:
                raise ValueError("audio must be between 1 byte and 10 MB")
            content_type = self.headers.get("Content-Type", "application/octet-stream")
            if not (content_type.startswith("audio/") or content_type == "application/octet-stream"):
                raise ValueError("unsupported audio content type")
            filename = os.path.basename(self.headers.get("X-Filename", "voice.webm"))
            suffix = os.path.splitext(filename)[1] or ".webm"
            with tempfile.NamedTemporaryFile(prefix="haochat-voice-", suffix=suffix, delete=False) as audio_file:
                temp_path = audio_file.name
                remaining = length
                while remaining:
                    chunk = self.rfile.read(min(64 * 1024, remaining))
                    if not chunk:
                        raise ValueError("incomplete audio payload")
                    audio_file.write(chunk)
                    remaining -= len(chunk)
            with whisper_lock:
                segments, info = get_whisper_model().transcribe(
                    temp_path,
                    language="zh",
                    beam_size=3,
                    # 聊天语音通常很短，VAD 容易把轻声或开头裁掉；直接识别完整音频更稳妥。
                    vad_filter=False,
                    condition_on_previous_text=False,
                )
                text = "".join(segment.text for segment in segments).strip()
                schedule_whisper_cleanup()
            self.send_json(200, {
                "text": text,
                "language": info.language,
                "duration": round(float(info.duration or 0), 2),
                "model": WHISPER_MODEL_NAME,
            })
        except ValueError as exc:
            self.send_json(400, {"error": {"message": str(exc)}})
        except Exception as exc:
            print(f"voice transcription failed: {exc}", flush=True)
            self.send_json(500, {"error": {"message": "transcription_failed"}})
        finally:
            if temp_path:
                try:
                    os.remove(temp_path)
                except OSError:
                    pass

    def log_message(self, fmt, *args):
        print(f"{self.address_string()} - {fmt % args}", flush=True)

    def send_json(self, status, payload):
        encoded = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)


if __name__ == "__main__":
    print(f"Embedding model ready: {MODEL_NAME}; speech model loads on demand: {WHISPER_MODEL_NAME}", flush=True)
    ThreadingHTTPServer(("0.0.0.0", 8000), Handler).serve_forever()
