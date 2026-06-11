import os

def fix_file(filepath):
    with open(filepath, 'rb') as f:
        data = f.read()

    # 0x81 is the bad byte causing SnakeYAML to crash
    if b'\x81' in data:
        print(f"Found 0x81 in {filepath}, removing it.")
        data = data.replace(b'\x81', b'')
        
    # 'clǭsico' -> 'clásico' (C7 AD -> C3 A1)
    if b'\xc7\xad' in data:
        data = data.replace(b'\xc7\xad', b'\xc3\xa1')
        
    # 'fsica' -> 'física' (corrupted originally as fsica)
    # The previous script did these text replacements, so I will do text replacements after fixing bytes.
    try:
        text = data.decode('utf-8')
        text = text.replace('fsica', 'física')
        text = text.replace('mgico', 'mágico')
        text = text.replace('persecucin', 'persecución')
        text = text.replace('Fǭcil', 'Fácil')
        data = text.encode('utf-8')
    except Exception as e:
        print(f"Failed to decode {filepath}: {e}")

    with open(filepath, 'wb') as f:
        f.write(data)

base_path = r"c:\Users\L900m\OneDrive\Desktop\MistakenDeluxe\MistakenDeluxe\MistakenDeluxe-Core\src\main\resources\langs\es"
for f in ["messages.yml", "asesinos_info.yml", "supervivientes_info.yml"]:
    fix_file(os.path.join(base_path, f))

print("Done fixing files!")
