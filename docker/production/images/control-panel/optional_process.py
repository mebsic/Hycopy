import os
from pathlib import Path
import signal
import subprocess
import threading

class OptionalProcess:
    def __init__(self, launcher):
        self.launcher = Path(launcher)
        self.stopping = threading.Event()
        self.lock = threading.Lock()
        self.lifecycle_lock = threading.Lock()
        self.process = None
        self.thread = None

    def start(self):
        with self.lifecycle_lock:
            return self._start()

    def _start(self):
        if self.thread is not None and self.thread.is_alive():
            return False
        if not self.launcher.is_file():
            return False
        self.stopping.clear()
        self.thread = threading.Thread(target=self._run, name="optional-private-process", daemon=True)
        self.thread.start()
        return True

    def restart(self):
        with self.lifecycle_lock:
            self._stop()
            return self._start()

    def _run(self):
        retry_seconds = 2
        while not self.stopping.is_set():
            if not self.launcher.is_file():
                return
            environment = {key: os.environ[key] for key in ("PATH", "LANG", "LC_ALL", "TZ") if key in os.environ}
            environment["PYTHONUNBUFFERED"] = "1"
            try:
                with self.lock:
                    if self.stopping.is_set():
                        return
                    process = subprocess.Popen(
                        ["python3", str(self.launcher)],
                        cwd=str(self.launcher.parent),
                        env=environment,
                        stdin=subprocess.DEVNULL,
                        start_new_session=True,
                    )
                    self.process = process
                exit_code = process.wait()
            except OSError:
                print("Optional private process could not start.", flush=True)
                exit_code = 1
            finally:
                with self.lock:
                    self.process = None
            if self.stopping.is_set() or exit_code == 0:
                return
            if self.stopping.wait(retry_seconds):
                return
            retry_seconds = min(retry_seconds * 2, 60)

    def stop(self):
        with self.lifecycle_lock:
            self._stop()

    def _stop(self):
        self.stopping.set()
        with self.lock:
            process = self.process
        if process is not None:
            try:
                os.killpg(process.pid, signal.SIGTERM)
            except ProcessLookupError:
                pass
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                try:
                    os.killpg(process.pid, signal.SIGKILL)
                except ProcessLookupError:
                    pass
                process.wait(timeout=5)
        if self.thread is not None:
            self.thread.join(timeout=2)
