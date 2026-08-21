with open('build.gradle.kts', 'a', encoding='utf-8') as f:
    f.write('\ntasks.register("fixUtf8", Exec::class) {\n')
    f.write('    commandLine("py", "8UTFFix.py")\n')
    f.write('    workingDir = rootProject.projectDir\n')
    f.write('    isIgnoreExitValue = true\n')
    f.write('}\n\n')
    f.write('tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {\n')
    f.write('    dependsOn("fixUtf8")\n')
    f.write('}\n')
