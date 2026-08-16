import os
import re
import sys
import shutil
import string
from collections import Counter

# --- CONFIGURATION ---
TARGET_EXTENSIONS = {'.kt', '.java'}
IGNORE_DIRS = {'build', '.gradle', 'out', '.git', '.idea', 'libs', 'src/test', '.cleanup-backup'}
REMOVE_THRESHOLD = 0.90

# Safe tokenizer to avoid matching comments inside strings or chars
TOKENIZER_REGEX = re.compile(
    r'(?P<raw_string>\"\"\"[\s\S]*?\"\"\")|'
    r'(?P<string>"(?:\\.|[^"\\])*")|'
    r'(?P<char>\'(?:\\.|[^\'\\])\')|'
    r'(?P<block_comment>/\*[\s\S]*?\*/)|'
    r'(?P<line_comment>//.*?$)',
    re.MULTILINE
)

# Words indicating reasoning or non-obvious technical context
REASONING_WORDS = {'because', 'due to', 'required', 'workaround', 'instead of', 'prevent', 'must', 'ensure', 'vital', 'important', 'avoid'}
TECHNICAL_WORDS = {'entityscheduler', 'packetevents', 'paper', 'nms', 'thread', 'async', 'block', 'database', 'coroutine'}
DOC_TAGS = {'@param', '@return', '@throws', '@see'}

def extract_meaningful_words(text):
    text = text.lower()
    for p in string.punctuation:
        text = text.replace(p, ' ')
    words = text.split()
    stop_words = {'the', 'a', 'an', 'to', 'is', 'for', 'of', 'in', 'on', 'and', 'with', 'if', 'it', 'we', 'this'}
    return {w for w in words if w not in stop_words and len(w) > 2}

def classify_comment(comment_text, next_code_line, frequency):
    """
    Returns (classification, confidence, reason)
    """
    clean_comment = comment_text.strip()
    is_kdoc = clean_comment.startswith('/**')
    lower_comment = clean_comment.lower()
    
    # 1. KDoc / Javadoc analysis
    if is_kdoc:
        if any(tag in clean_comment for tag in DOC_TAGS):
            return 'KEEP', 0.95, 'Public API documentation with standard tags'
        
        # Check for decorative banners like [LIRIC-MISTAKEN 2.0] with historical noise
        if '[liric-mistaken' in lower_comment and 'fix:' in lower_comment:
            if not any(w in lower_comment for w in REASONING_WORDS):
                return 'REMOVE', 0.95, 'Decorative historical banner / changelog'
        
        # Proper descriptive KDoc
        if len(clean_comment.splitlines()) > 2:
            return 'KEEP', 0.90, 'Looks like structural public documentation'

    # 2. Reasoning and Technical context
    if any(w in lower_comment for w in REASONING_WORDS):
        return 'KEEP', 0.98, 'Explains why or contains reasoning/constraints'
        
    if any(w in lower_comment for w in TECHNICAL_WORDS):
        return 'KEEP', 0.96, 'Explains specific API, Threading or Engine mechanics'

    if 'todo' in lower_comment and len(lower_comment.split()) > 3:
        return 'KEEP', 0.95, 'Actionable TODO with context'

    # 3. Debugging / Temporary markers
    if re.match(r'^/?\*?\s*//\s*(debug|temp|test|remove this|testing)\s*\*?/?$', lower_comment, re.IGNORECASE):
        return 'REMOVE', 0.98, 'Stray debugging/temporary marker without context'
        
    # 4. Historical / Label noise
    if re.match(r'^//\s*(FIX|NOTE|UPDATED|NEW|CHANGE):\s*.*', clean_comment, re.IGNORECASE):
        if len(clean_comment.split()) < 8 and not any(w in lower_comment for w in REASONING_WORDS):
            return 'REMOVE', 0.92, 'Historical tag/label lacking current technical context'
        else:
            return 'KEEP', 0.90, 'Tag contains significant explanation'

    # 5. Decorative / Banners
    if re.match(r'^//\s*[-=*\~_]{5,}\s*$', clean_comment) or re.match(r'^/\*\*?\s*[-=*\~_]{5,}\s*\*/$', clean_comment):
        return 'REMOVE', 0.99, 'Purely decorative banner'

    # 6. Repetitive noise
    if frequency > 20 and len(clean_comment.split()) < 10:
        return 'REMOVE', 0.95, f'Highly repetitive noise (appears {frequency} times)'

    # 7. Literal Code Translation
    if next_code_line:
        comment_words = extract_meaningful_words(clean_comment)
        code_words = extract_meaningful_words(next_code_line)
        
        # Specific obvious patterns
        if re.match(r'^//\s*set\s+\w+', lower_comment) and '=' in next_code_line:
            return 'REMOVE', 0.95, 'Literally translates a variable assignment'
            
        if 'if' in next_code_line and 'si ' in lower_comment:
            return 'REMOVE', 0.92, 'Literally describes an if-condition'

        # Vocabulary overlap check for short comments
        if len(comment_words) > 0 and len(comment_words) < 8:
            overlap = comment_words.intersection(code_words)
            if len(overlap) / len(comment_words) > 0.7:
                return 'REMOVE', 0.94, 'Directly describes the next line of code without adding new info'

    # Fallback to uncertain
    return 'UNCERTAIN', 0.50, 'Does not strongly match any KEEP or REMOVE rule'

def scan_files(directory):
    file_paths = []
    for root, dirs, files in os.walk(directory):
        dirs[:] = [d for d in dirs if d not in IGNORE_DIRS]
        for f in files:
            if any(f.endswith(ext) for ext in TARGET_EXTENSIONS):
                file_paths.append(os.path.join(root, f))
    return file_paths

def parse_file(file_path):
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    tokens = []
    for match in TOKENIZER_REGEX.finditer(content):
        kind = match.lastgroup
        value = match.group(kind)
        start = match.start()
        end = match.end()
        line_num = content.count('\n', 0, start) + 1
        
        if kind in ('block_comment', 'line_comment'):
            tokens.append({
                'type': kind,
                'content': value,
                'start': start,
                'end': end,
                'line': line_num
            })
            
    return content, tokens

def get_next_code_line(content, comment_end_idx):
    remainder = content[comment_end_idx:]
    lines = remainder.split('\n')
    for line in lines:
        stripped = line.strip()
        if stripped and not stripped.startswith('//') and not stripped.startswith('/*'):
            return stripped
    return ""

def main():
    args = sys.argv[1:]
    apply_mode = '--apply' in args
    
    print(f"Starting Intelligent Comment Cleaner in {'APPLY' if apply_mode else 'DRY-RUN'} mode...\n")

    files = scan_files('.')
    
    # Pass 1: Build frequency map
    comment_frequencies = Counter()
    file_data_map = {}
    
    for fpath in files:
        content, comments = parse_file(fpath)
        file_data_map[fpath] = (content, comments)
        for c in comments:
            stripped = c['content'].strip()
            comment_frequencies[stripped] += 1

    # Pass 2: Classify and process
    stats = {'KEEP': 0, 'REMOVE': 0, 'UNCERTAIN': 0}
    removals_log = []
    
    if apply_mode:
        backup_dir = '.cleanup-backup'
        if not os.path.exists(backup_dir):
            os.makedirs(backup_dir)

    for fpath, (content, comments) in file_data_map.items():
        new_content = ""
        last_idx = 0
        file_modified = False
        
        for c in comments:
            next_code = get_next_code_line(content, c['end'])
            freq = comment_frequencies[c['content'].strip()]
            
            classification, confidence, reason = classify_comment(c['content'], next_code, freq)
            
            # Append code before comment
            new_content += content[last_idx:c['start']]
            
            if classification == 'REMOVE' and confidence >= REMOVE_THRESHOLD:
                stats['REMOVE'] += 1
                removals_log.append({
                    'file': fpath,
                    'line': c['line'],
                    'classification': classification,
                    'confidence': confidence,
                    'comment': c['content'],
                    'reason': reason,
                    'context': next_code
                })
                file_modified = True
                # Skip appending comment to new_content
            else:
                if classification == 'KEEP':
                    stats['KEEP'] += 1
                else:
                    stats['UNCERTAIN'] += 1
                new_content += c['content']
                
            last_idx = c['end']
            
        new_content += content[last_idx:]
        
        if apply_mode and file_modified:
            # Create backup maintaining directory structure
            rel_path = os.path.relpath(fpath, '.')
            bak_path = os.path.join('.cleanup-backup', rel_path)
            os.makedirs(os.path.dirname(bak_path), exist_ok=True)
            shutil.copy2(fpath, bak_path)
            
            # Write modifications
            with open(fpath, 'w', encoding='utf-8') as f:
                f.write(new_content)

    # Generate Report
    with open('COMMENT_CLEANUP_REPORT.md', 'w', encoding='utf-8') as f:
        f.write("# Comment Cleanup Report\n\n")
        f.write(f"**Mode:** {'APPLY' if apply_mode else 'DRY-RUN'}\n")
        f.write("## Statistics\n")
        f.write("```text\n")
        f.write(f"Files scanned: {len(files)}\n")
        total_comments = sum(stats.values())
        f.write(f"Comments detected: {total_comments}\n")
        f.write(f"Safe to remove (Conf >= {REMOVE_THRESHOLD}): {stats['REMOVE']}\n")
        f.write(f"Kept: {stats['KEEP']}\n")
        f.write(f"Uncertain (Ignored): {stats['UNCERTAIN']}\n")
        f.write("```\n\n")
        
        f.write("## Removals Log\n\n")
        for r in removals_log:
            f.write(f"[{r['classification']}]\n")
            f.write(f"File: {r['file']}\n")
            f.write(f"Line: {r['line']}\n")
            f.write(f"Confidence: {r['confidence']:.2f}\n\n")
            f.write("Comment:\n```kotlin\n")
            f.write(f"{r['comment']}\n")
            f.write("```\n\n")
            f.write("Context (Next Line):\n```kotlin\n")
            f.write(f"{r['context']}\n")
            f.write("```\n\n")
            f.write(f"**Reason:** {r['reason']}\n\n")
            f.write("---\n\n")

    print("Done!")
    print(f"Scanned {len(files)} files. Generated COMMENT_CLEANUP_REPORT.md")

if __name__ == "__main__":
    main()
