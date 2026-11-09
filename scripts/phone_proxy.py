"""Lets a phone on the same Wi-Fi reach the emulator's photo page for testing.

The phone connects to this PC; traffic goes on to 127.0.0.1:8765, which adb forwards to the
emulator. Logs request paths and photo timestamps, never picture contents or the session token.
Usage: python phone_proxy.py <wifi-ip> <log-file>
"""
import json
import re
import socket
import sys
import threading
import time

LISTEN_PORT = 8766
TARGET = ("127.0.0.1", 8765)


def log(path, text):
    with open(path, "a", encoding="utf8") as f:
        f.write(f"{time.strftime('%H:%M:%S')} {text}\n")


def pump(source, sink, on_data=None):
    try:
        while True:
            data = source.recv(65536)
            if not data:
                break
            if on_data:
                on_data(data)
            sink.sendall(data)
    except OSError:
        pass
    finally:
        for s in (source, sink):
            try:
                s.shutdown(socket.SHUT_RDWR)
            except OSError:
                pass


def handle(client, address, log_path):
    server = socket.create_connection(TARGET)
    seen = {"head": False, "select": False, "buffer": b""}

    def inspect(data):
        if not seen["head"]:
            seen["head"] = True
            line = data.split(b"\r\n", 1)[0].decode("latin1")
            agent = re.search(rb"User-Agent: ([^\r]*)", data)
            line = re.sub(r"t=[0-9a-f]+", "t=***", line)
            log(log_path, f"{address[0]} {line} | {agent.group(1).decode('latin1')[:70] if agent else ''}")
            seen["select"] = "/api/select" in line
        if seen["select"]:
            seen["buffer"] += data
            body = seen["buffer"].split(b"\r\n\r\n", 1)[-1]
            try:
                rows = json.loads(body)
            except ValueError:
                return
            seen["select"] = False
            for row in rows:
                when = time.strftime("%Y-%m-%d %H:%M", time.localtime(row["takenAt"] / 1000))
                log(log_path, f"  photo {row['id']} takenAt={when} driver={row['driverPhone']}")

    threading.Thread(target=pump, args=(client, server, inspect), daemon=True).start()
    pump(server, client)


def main():
    ip, log_path = sys.argv[1], sys.argv[2]
    listener = socket.create_server((ip, LISTEN_PORT))
    while True:
        client, address = listener.accept()
        threading.Thread(target=handle, args=(client, address, log_path), daemon=True).start()


if __name__ == "__main__":
    main()
