#!/usr/bin/env python3
"""Exercise the real packaged premain, authenticated connection, input ordering and watchdog."""
import secrets
import socket
import subprocess
import sys


def main():
    java, jar, classes = sys.argv[1:]
    token = secrets.token_hex(32)
    with socket.socket() as endpoint:
        endpoint.bind(("127.0.0.1", 0))
        endpoint.listen(1)
        endpoint.settimeout(8)
        process = subprocess.Popen(
            [java, "-javaagent:" + jar + "=" + str(endpoint.getsockname()[1]) + ":" + token,
             "-cp", classes, "com.minecraftjogger.touch.AgentSocketProbe"],
            stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        try:
            with endpoint.accept()[0] as client:
                client.settimeout(4)
                wire = client.makefile("rwb", buffering=0)
                hello = wire.readline().decode().strip()
                if hello != "JOGGER1 " + token:
                    stdout, stderr = process.communicate(timeout=6)
                    raise AssertionError("Agent handshake failed; JVM output: " + stdout + stderr)
                wire.write(b"READY\n")
                wire.write(b"K 1 87 119 0 0 1\nM 1 0 0 1\n")
                assert wire.readline() == b"K 1 87 119 0 0 1\n"
                assert wire.readline() == b"M 1 0 0 1\n"
                wire.write(b"K 1 87 119 0 0 1\nR 2\nK 1 87 119 0 0 1\nM 2 1 0 1\n")
                assert wire.readline() == b"K 2 87 119 0 0 0\n"
                assert wire.readline() == b"M 2 0 0 0\n"
                assert wire.readline() == b"M 2 1 0 1\n"
                # No heartbeat: the JVM watchdog must release the held use button.
                assert wire.readline() == b"M 2 1 0 0\n"
            stdout, stderr = process.communicate(timeout=6)
            assert process.returncode == 0, stderr
            assert "Classic touch agent connected" in stdout, stdout
            print("Classic packaged premain + authenticated socket + ordered events + stale-epoch rejection + watchdog passed.")
        finally:
            if process.poll() is None:
                process.kill()
                process.wait()


if __name__ == "__main__":
    main()
