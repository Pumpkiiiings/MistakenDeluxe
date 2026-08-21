import os

def fix_file(filepath):
    try:
        with open(filepath, "r", encoding="utf-8") as f:
            content = f.read()
            
        # Try to decode mojibake
        try:
            fixed_content = content.encode("cp1252").decode("utf-8")
            if content != fixed_content:
                with open(filepath, "w", encoding="utf-8") as f:
                    f.write(fixed_content)
                print(f"Fixed: {filepath}")
        except Exception:
            pass # Not mojibake or cant fix
    except Exception as e:
        pass

for root, _, files in os.walk("."):
    for file in files:
        if file.endswith(".kt") or file.endswith(".java") or file.endswith(".yml"):
            fix_file(os.path.join(root, file))
print("Done!")
