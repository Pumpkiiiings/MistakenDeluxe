import os
import glob
import re

base_dir = r"C:\Users\L900m\OneDrive\Desktop\MistakenDeluxe\MistakenDeluxe\MistakenDeluxe-Core\src\main\java\liric\mistaken"

for path in glob.glob(f"{base_dir}/**/*.kt", recursive=True):
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()

    changed = False

    if "getAsesinos()" in content:
        content = content.replace("plugin.configManager.getAsesinos()", "plugin.configManager.getKillerConfig(this.id)")
        content = content.replace("config.getString(\"asesinos.$pathBase.", "config.getString(\"")
        content = content.replace("config.getString(\"$pathBase.", "config.getString(\"")
        content = content.replace("configMecanica.getInt(\"asesinos.$pathBase.", "configMecanica.getInt(\"")
        content = content.replace("configMecanica.getInt(\"$pathBase.", "configMecanica.getInt(\"")
        content = content.replace("config.getInt(\"asesinos.$pathBase.", "config.getInt(\"")
        content = content.replace("config.getInt(\"$pathBase.", "config.getInt(\"")
        content = content.replace("config.getString(\"asesinos.${this.id}.", "config.getString(\"")
        content = content.replace("configMecanica.getString(\"asesinos.${this.id}.", "configMecanica.getString(\"")
        changed = True

    if "getSupervivientes()" in content:
        content = content.replace("plugin.configManager.getSupervivientes()", "plugin.configManager.getSurvivorConfig(this.id)")
        content = content.replace("config.getString(\"supervivientes.$pathBase.", "config.getString(\"")
        content = content.replace("config.getString(\"$pathBase.", "config.getString(\"")
        content = content.replace("configMecanica.getInt(\"supervivientes.$pathBase.", "configMecanica.getInt(\"")
        content = content.replace("configMecanica.getInt(\"$pathBase.", "configMecanica.getInt(\"")
        content = content.replace("mechConfig.getInt(\"supervivientes.$pathBase.", "mechConfig.getInt(\"")
        content = content.replace("mechConfig.getInt(\"$pathBase.", "mechConfig.getInt(\"")
        content = content.replace("config.getInt(\"supervivientes.$pathBase.", "config.getInt(\"")
        content = content.replace("config.getInt(\"$pathBase.", "config.getInt(\"")
        content = content.replace("config.getString(\"supervivientes.${this.id}.", "config.getString(\"")
        changed = True

    if changed:
        with open(path, "w", encoding="utf-8") as f:
            f.write(content)
        print(f"Updated {path}")
