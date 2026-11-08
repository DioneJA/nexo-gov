#!/usr/bin/env python3
import socket

ports = []
while len(ports) < 5:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as listener:
        listener.bind(("127.0.0.1", 0))
        port = listener.getsockname()[1]
    if port not in ports:
        ports.append(port)
print(*ports)
