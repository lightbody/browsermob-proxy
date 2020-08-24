package net.lightbody.bmp.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * A Runnable that deletes the specified directory. Useful as a shutdown hook.
 */
public class DeleteDirectoryTask implements Runnable {
    // the static final logger is in this static inner class to allow the logger initialization code to use DeleteDirectoryTask
    // without prematurely initializing the logger when loading this class. since the 'log' field is in a static inner class, it will
    // only be initialized when it is actually used, instead of when the classloader loads the DeleteDirectoryTask class.
    private static class LogHolder {
        private static final Logger log = LoggerFactory.getLogger(DeleteDirectoryTask.class);
    }

    private final Path directory;

    public DeleteDirectoryTask(Path directory) {
        this.directory = directory;
    }

    @Override
    public void run() {
        try {
            Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    try {
                        Files.delete(file);
                    } catch (IOException e) {
                        LogHolder.log.warn("Unable to delete file or directory", e);
                    }

                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                    try {
                        Files.delete(dir);
                    } catch (IOException e) {
                        LogHolder.log.warn("Unable to delete file or directory", e);
                    }

                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            LogHolder.log.warn("Unable to delete file or directory", e);
        }
    }
}
