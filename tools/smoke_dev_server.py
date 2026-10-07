#!/usr/bin/env python3
"""Boot the loopback dev server to validate mixins, commands and runtime mods."""
import os
import pathlib
import queue
import signal
import subprocess
import threading
import time

ROOT = pathlib.Path(__file__).resolve().parents[1]
LOG = ROOT / 'build' / 'server-smoke.log'


def main():
    LOG.parent.mkdir(parents=True, exist_ok=True)
    process = subprocess.Popen(
        ['bash', './gradlew', 'runDevServer', '--no-daemon'], cwd=ROOT,
        stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
        text=True, bufsize=1, start_new_session=True,
    )
    lines = queue.Queue()

    def read_output():
        for line in process.stdout:
            lines.put(line)
        lines.put(None)

    threading.Thread(target=read_output, daemon=True).start()
    started = False
    deadline = time.monotonic() + 180
    try:
        with LOG.open('w') as log:
            while time.monotonic() < deadline:
                try:
                    line = lines.get(timeout=1)
                except queue.Empty:
                    continue
                if line is None:
                    break
                log.write(line)
                log.flush()
                if 'Done (' in line:
                    started = True
                    process.stdin.write('stop\n')
                    process.stdin.flush()
                if started and process.poll() is not None:
                    break
        if not started:
            raise RuntimeError('Dev server failed to start. See build/server-smoke.log')
        process.wait(timeout=30)
        if process.returncode:
            raise RuntimeError(f'Dev server exited with {process.returncode}; see build/server-smoke.log')
        print('PASS: loopback server booted with command alias mixin and pinned performance mods')
    finally:
        if process.poll() is None:
            os.killpg(process.pid, signal.SIGTERM)
            process.wait(timeout=15)


if __name__ == '__main__':
    main()
