import os
import re

# Regex for emojis translated from JS
EMOJI_REGEX = re.compile(
    "["
    "\U0001F300-\U0001F9FF"
    "\u2600-\u26FF"
    "\u2700-\u27BF"
    "\U0001F1E6-\U0001F1FF"
    "\U0001F200-\U0001F251"
    "\U0001F600-\U0001F64F"
    "\U0001F680-\U0001F6FF"
    "\u2B50\u231A\u23F0\u23F3\u231B\u25B6\u23E9\u23EB\u23EC\u23EA"
    "\u2194\u2195\u23F1\u23F2\u23F8\u23F9\u23FA\u23A9\uFE0F"
    "]+"
)

def fix_utf8_file(filepath):
    """Fixes Mojibake corruption in source files"""
    try:
        with open(filepath, "r", encoding="utf-8") as f:
            content = f.read()
            
        try:
            fixed_content = content.encode("cp1252").decode("utf-8")
            if content != fixed_content:
                with open(filepath, "w", encoding="utf-8") as f:
                    f.write(fixed_content)
                print(f"Fixed UTF-8: {filepath}")
        except Exception:
            pass # Not mojibake or cant fix
    except Exception:
        pass

def remove_emojis_file(filepath):
    """Removes emojis from markdown files"""
    try:
        with open(filepath, "r", encoding="utf-8") as f:
            content = f.read()
            
        if EMOJI_REGEX.search(content):
            content = EMOJI_REGEX.sub('', content)
            content = re.sub(r'  +', ' ', content) # Clean double spaces left by emojis
            
            with open(filepath, "w", encoding="utf-8") as f:
                f.write(content)
            print(f"Cleaned Emojis: {filepath}")
    except Exception:
        pass

# 1. Process UTF-8 fixes in current directory (MistakenDeluxe)
for root, _, files in os.walk("."):
    for file in files:
        if file.endswith(".kt") or file.endswith(".java") or file.endswith(".yml"):
            fix_utf8_file(os.path.join(root, file))

# 2. Process Emoji removals in the mistaken-docs directory
docs_dir = os.path.abspath(os.path.join("..", "..", "mistaken-docs", "content", "docs"))
if os.path.exists(docs_dir):
    for root, _, files in os.walk(docs_dir):
        for file in files:
            if file.endswith(".mdx"):
                remove_emojis_file(os.path.join(root, file))

print("Pre-build checks and fixes completed!")
