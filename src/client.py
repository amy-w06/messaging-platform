import socket
import threading
import struct
import os
import time
import random

s = socket.socket()
s.connect(("localhost", 5005))
print("Connected")

# P2P File Transfer Functions
class FileSender(threading.Thread):
    def __init__(self, file_path, listen_port):
        super().__init__()
        self.file_path = file_path
        self.listen_port = listen_port

    def run(self):
        try:
            with socket.socket() as ss:
                ss.bind(('', self.listen_port))
                ss.listen(1)
                print(f"FileSender listening on port {self.listen_port}")
                peer, addr = ss.accept()
                print(f"Peer connected from {addr}")

                with open(self.file_path, 'rb') as f:
                    filename = os.path.basename(self.file_path).encode()
                    filesize = os.path.getsize(self.file_path)

                    # Send metadata
                    peer.send(struct.pack(">H", len(filename)) + filename)
                    peer.send(struct.pack(">Q", filesize))  # 8-byte unsigned long

                    # Send file bytes
                    buffer = bytearray(4096)
                    while True:
                        count = f.readinto(buffer)
                        if count == 0:
                            break
                        peer.send(buffer[:count])
                    peer.close()
                print("File sent successfully!")
        except Exception as e:
            print(f"Send failed: {e}")

class FileReceiver(threading.Thread):
    def __init__(self, ip, port, save_path):
        super().__init__()
        self.ip = ip
        self.port = port
        self.save_path = save_path

    def run(self):
        try:
            with socket.socket() as sock:
                sock.connect((self.ip, self.port))
                print(f"Connected to {self.ip}:{self.port}")

                # Receive metadata
                len_filename = struct.unpack(">H", sock.recv(2))[0]
                filename = sock.recv(len_filename).decode()
                filesize = struct.unpack(">Q", sock.recv(8))[0]

                # Receive file
                with open(self.save_path, 'wb') as f:
                    remaining = filesize
                    buffer = bytearray(4096)
                    while remaining > 0:
                        to_read = min(4096, remaining)
                        read = sock.recv_into(buffer, to_read)
                        if read == 0:
                            break
                        f.write(buffer[:read])
                        remaining -= read
                print(f"Downloaded '{filename}' ({filesize} bytes) to {self.save_path}")
        except Exception as e:
            print(f"Download failed: {e}")

def receive():
    while True:
        try:
            length = struct.unpack(">H", s.recv(2))[0]
            print(s.recv(length).decode())
        except:
            break

threading.Thread(target=receive, daemon=True).start()

while True:
    try:
        msg = input()
        if msg == "Close":
            break

        # ========== FILE TRANSFER LOGIC ==========
        if msg.startswith("/sendfile "):
            parts = msg.split(" ", 3)
            if len(parts) == 3:
                target_user = parts[1]
                file_path = parts[2]
                if os.path.exists(file_path) and os.path.isfile(file_path):
                    port = 6000 + random.randint(0, 999)
                    FileSender(file_path, port).start()

                    # Send signalling command
                    cmd = f"/sendfile {target_user} {os.path.basename(file_path)} {os.path.getsize(file_path)} {port}"
                    encoded = cmd.encode()
                    s.send(struct.pack(">H", len(encoded)) + encoded)
                    print(f"File offer sent to {target_user} on port {port}")
                else:
                    print(f"❌ File not found: {file_path}")
            else:
                print("Usage: /sendfile <username> <filepath>")
            continue

        if msg.startswith("/acceptfile "):
            parts = msg.split(" ", 4)
            if len(parts) == 4:
                ip = parts[2]
                port = int(parts[3])
                save_path = f"received_{int(time.time())}.dat"
                FileReceiver(ip, port, save_path).start()
            # Forward to server
            encoded = msg.encode()
            s.send(struct.pack(">H", len(encoded)) + encoded)
            continue
        # =========================================

        # Normal chat
        encoded = msg.encode()
        s.send(struct.pack(">H", len(encoded)) + encoded)

    except KeyboardInterrupt:
        break
    except Exception as e:
        print(f"Error: {e}")
        break

s.close()
