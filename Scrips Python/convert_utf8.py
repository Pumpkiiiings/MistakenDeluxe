import os

def convert_to_utf8(filepath):
    with open(filepath, 'rb') as f:
        data = f.read()

    # Decode assuming Windows-1252
    try:
        text = data.decode('cp1252')
    except UnicodeDecodeError:
        text = data.decode('cp1252', errors='replace')

    # Re-encode to UTF-8
    with open(filepath, 'wb') as f:
        f.write(text.encode('utf-8'))

base_path = r"c:\Users\L900m\OneDrive\Desktop\MistakenDeluxe\MistakenDeluxe\MistakenDeluxe-Core\src\main\resources\langs\es"
for file_name in os.listdir(base_path):
    if file_name.endswith('.yml'):
        convert_to_utf8(os.path.join(base_path, file_name))

print("Conversion complete!")
