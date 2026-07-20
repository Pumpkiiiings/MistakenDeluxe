const fs = require('fs');
const path = require('path');
const yaml = require('js-yaml');

const CORE_DIR = path.join(__dirname, '..', 'MistakenDeluxe-Core', 'src', 'main');
const CONFIG_FILE = path.join(CORE_DIR, 'resources', 'config.yml');
const MESSAGES_FILE = path.join(CORE_DIR, 'resources', 'langs', 'es', 'messages.yml');

const OUT_DIR = path.join(CORE_DIR, 'java', 'liric', 'mistaken', 'config');

function flattenKeys(data, prefix = '') {
    let keys = [];
    if (typeof data === 'object' && data !== null && !Array.isArray(data)) {
        for (const [key, value] of Object.entries(data)) {
            const newPrefix = prefix ? `${prefix}.${key}` : key;
            keys = keys.concat(flattenKeys(value, newPrefix));
        }
    } else {
        keys.push(prefix);
    }
    return keys;
}

function generateKotlinObject(objectName, keys, packageName = 'liric.mistaken.config') {
    let content = `package ${packageName}\n\n`;
    content += `object ${objectName} {\n`;
    
    for (const key of keys) {
        if (!key) continue;
        const constName = key.toUpperCase().replace(/[-.]/g, '_');
        content += `    const val ${constName} = "${key}"\n`;
    }
    
    content += `}\n`;
    return content;
}

function processYamlFile(filePath, objectName) {
    if (!fs.existsSync(filePath)) {
        console.error(`File not found: ${filePath}`);
        return;
    }
    
    try {
        let fileContent = fs.readFileSync(filePath, 'utf8');
        // Strip BOM if present
        if (fileContent.charCodeAt(0) === 0xFEFF) {
            fileContent = fileContent.slice(1);
        }
        const data = yaml.load(fileContent);
        const keys = flattenKeys(data);
        
        // Remove duplicates and sort
        const uniqueKeys = [...new Set(keys)].sort();
        
        const ktContent = generateKotlinObject(objectName, uniqueKeys);
        
        if (!fs.existsSync(OUT_DIR)) {
            fs.mkdirSync(OUT_DIR, { recursive: true });
        }
        
        const outPath = path.join(OUT_DIR, `${objectName}.kt`);
        fs.writeFileSync(outPath, ktContent, 'utf8');
        console.log(`Successfully generated ${objectName}.kt with ${uniqueKeys.length} keys.`);
    } catch (e) {
        console.error(`Error processing ${filePath}:`, e);
    }
}

console.log("Generating Kotlin config keys...");
processYamlFile(CONFIG_FILE, 'Config');
processYamlFile(MESSAGES_FILE, 'Messages');

const KILLERS_INFO_FILE = path.join(CORE_DIR, 'resources', 'langs', 'es', 'killers_info.yml');
const SURVIVORS_INFO_FILE = path.join(CORE_DIR, 'resources', 'langs', 'es', 'survivors_info.yml');
const MUSIC_FILE = path.join(CORE_DIR, 'resources', 'music.yml');

processYamlFile(KILLERS_INFO_FILE, 'KillersInfo');
processYamlFile(SURVIVORS_INFO_FILE, 'SurvivorsInfo');
processYamlFile(MUSIC_FILE, 'Music');

console.log("Done!");
