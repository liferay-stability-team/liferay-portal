#!/usr/bin/env python3
"""Minimal HTTP and HTTPS CONNECT proxy on 127.0.0.1:8118.

The reviewer routes the sandboxed Claude through this proxy so all egress from
the bubblewrap sandbox leaves through a single, observable point. It tunnels
CONNECT requests for TLS and forwards plain HTTP. Swap it for privoxy or
tinyproxy with an allowlist when you want real egress filtering.
"""
import select
import socket
import threading

LISTEN = ("127.0.0.1", 8118)


def main():
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind(LISTEN)
    server.listen(128)
    print(f"proxy listening on {LISTEN[0]}:{LISTEN[1]}", flush=True)

    while True:
        client, _ = server.accept()
        threading.Thread(target=_handle, args=(client,), daemon=True).start()


def _handle(client):
    try:
        client.settimeout(30)
        header = b""
        while b"\r\n\r\n" not in header:
            chunk = client.recv(4096)
            if not chunk:
                client.close()
                return
            header += chunk
        request_line = header.split(b"\r\n", 1)[0].decode("latin1")
        method, target, _ = request_line.split(" ", 2)

        if method.upper() == "CONNECT":
            host, port = target.split(":")
            upstream = socket.create_connection((host, int(port)), timeout=30)
            client.sendall(b"HTTP/1.1 200 Connection Established\r\n\r\n")
            client.settimeout(None)
            _pipe(client, upstream)
        else:
            hostport = target.split("://", 1)[-1].split("/", 1)[0]
            host = hostport.split(":")[0]
            port = int(hostport.split(":")[1]) if ":" in hostport else 80
            upstream = socket.create_connection((host, port), timeout=30)
            upstream.sendall(header)
            _pipe(client, upstream)

        upstream.close()
    except Exception:
        pass
    finally:
        client.close()


def _pipe(a, b):
    try:
        while True:
            readable, _, _ = select.select([a, b], [], [], 60)
            if not readable:
                break
            for source in readable:
                data = source.recv(65536)
                if not data:
                    return
                destination = b if source is a else a
                destination.sendall(data)
    except OSError:
        pass


if __name__ == "__main__":
    main()
