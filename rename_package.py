import os
import shutil

root_dir = r"C:\Users\L900m\OneDrive\Desktop\Proyectos\Mistaken\MistakenDeluxe\MistakenDeluxe"
old_pkg_path = os.path.join(root_dir, r"MistakenDeluxe-Core\src\main\java\liric\mistaken\characters")
new_pkg_path = os.path.join(root_dir, r"MistakenDeluxe-Core\src\main\java\liric\mistaken\models")

# 1. Rename the directory if it exists
if os.path.exists(old_pkg_path):
    print(f"Renaming {old_pkg_path} to {new_pkg_path}")
    os.rename(old_pkg_path, new_pkg_path)
else:
    print(f"Directory {old_pkg_path} not found. Skipping rename.")

# 2. Replace occurrences in all .kt and .java files
old_str = "liric.mistaken.characters"
new_str = "liric.mistaken.models"
count = 0
changed_files = 0

for root, _, files in os.walk(root_dir):
    for file in files:
        if file.endswith(".kt") or file.endswith(".java"):
            file_path = os.path.join(root, file)
            try:
                with open(file_path, "r", encoding="utf-8") as f:
                    content = f.read()
                
                if old_str in content:
                    content = content.replace(old_str, new_str)
                    with open(file_path, "w", encoding="utf-8") as f:
                        f.write(content)
                    changed_files += 1
                    count += content.count(new_str)
            except Exception as e:
                print(f"Error reading {file_path}: {e}")

print(f"Successfully updated {changed_files} files.")
