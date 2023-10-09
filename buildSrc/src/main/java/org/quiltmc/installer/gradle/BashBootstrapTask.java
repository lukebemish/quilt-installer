package org.quiltmc.installer.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;

public abstract class BashBootstrapTask extends DefaultTask {
    @InputFile
    public abstract RegularFileProperty getInstallerJar();

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @Input
    public abstract Property<String> getHeader();

    public BashBootstrapTask() {
        getHeader().convention(HEADER);
    }

    @TaskAction
    public void run() {
        try {
            Files.deleteIfExists(getOutputFile().get().getAsFile().toPath());
            Files.createDirectories(getOutputFile().get().getAsFile().toPath().getParent());
            try (var os = new FileOutputStream(getOutputFile().get().getAsFile(), true)) {
                os.write(HEADER.getBytes());
                Files.copy(getInstallerJar().get().getAsFile().toPath(), os);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static final String HEADER = """
            #!/bin/bash

            echo "Quilt Installer Bootstrap"

            export TMPFILE=/tmp/quilt-installer-$RANDOM.jar

            ARCHIVE=`awk '/^__ARCHIVE_BELOW__/ {print NR + 1; exit 0; }' $0`

            tail -n+$ARCHIVE $0 > $TMPFILE

            CDIR=`pwd`
            
            PATHS=(
                $HOME/.minecraft/runtime/java-runtime-gamma/linux/java-runtime-gamma/bin/java
                $HOME/.minecraft/runtime/java-runtime-beta/linux/java-runtime-beta/bin/java
            )
            
            JAVAPATH=""
            
            for i in "${PATHS[@]}"; do
                if [ -f "$i" ]; then
                    JAVAPATH="$i"
                    break
                fi
            done
            
            if [ -z "$JAVAPATH" ] && type -p java; then
                _java=java
                version=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')
                if [ "$version" > 17.0 ]; then
                    JAVAPATH=$_java
                fi
            fi
            
            if [ -z "$JAVAPATH" ] && [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ];  then
                _java="$JAVA_HOME/bin/java"
                version=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')
                if [ "$version" > 17.0 ]; then
                    JAVAPATH=$_java
                fi
            fi
            
            if [ -z "$JAVAPATH" ]; then
                echo "No Java installation found!"
                echo "Please install Java 17 and try again."
                exit 1
            fi
            
            $JAVAPATH -jar $TMPFILE "$@"

            cd $CDIR
            rm $TMPFILE

            echo "Finishing up..."

            exit 0

            __ARCHIVE_BELOW__
            """;
}
