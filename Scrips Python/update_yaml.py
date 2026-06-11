import re
import os

filepath = r"c:\Users\L900m\Downloads\NextMistaken-proxy-2\NextMistaken-proxy-2\src\main\resources\asesinos.yml"
with open(filepath, 'r', encoding='utf-8') as f:
    content = f.read()

# For arma
content = re.sub(r'(\s+arma: "[^"]*")', r'\1\n      arma_slot: 8', content)
# For habilidades
for i in range(1, 5):
    content = re.sub(r'(\s+habilidad' + str(i) + r': "[^"]*")', r'\1\n      habilidad' + str(i) + r'_slot: ' + str(i), content)

with open(filepath, 'w', encoding='utf-8') as f:
    f.write(content)
print("Updated asesinos.yml successfully!")
