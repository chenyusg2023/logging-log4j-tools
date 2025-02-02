/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.logging.log4j.changelog.releaser;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.changelog.ChangelogFiles;
import org.apache.logging.log4j.changelog.ChangelogRelease;
import org.apache.logging.log4j.changelog.util.FileUtils;

import static java.time.format.DateTimeFormatter.ISO_DATE;

public final class ChangelogReleaser {

    private ChangelogReleaser() {}

    public static void performRelease(final ChangelogReleaserArgs args) {

        // Read the release date and version
        final String releaseDate = ISO_DATE.format(args.releaseDate != null ? args.releaseDate : LocalDate.now());
        System.out.format("using `%s` for the release date%n", releaseDate);

        try {

            // Populate the changelog entry files in the release directory
            final Path unreleasedDirectory =
                    ChangelogFiles.unreleasedDirectory(args.changelogDirectory, args.releaseVersionMajor);
            final Path releaseDirectory = ChangelogFiles.releaseDirectory(args.changelogDirectory, args.releaseVersion);
            populateChangelogEntryFiles(unreleasedDirectory, releaseDirectory);

            // Write the release information
            populateReleaseXmlFiles(releaseDate, args.releaseVersion, releaseDirectory);

            // Write the release changelog template
            populateReleaseChangelogTemplateFile(unreleasedDirectory, releaseDirectory);

        } catch (final IOException error) {
            throw new UncheckedIOException(error);
        }

    }

    private static void populateChangelogEntryFiles(
            final Path unreleasedDirectory,
            final Path releaseDirectory)
            throws IOException {
        if (Files.exists(releaseDirectory)) {
            System.out.format(
                    "release directory `%s` already exists, only moving the changelog entry files from `%s`%n",
                    releaseDirectory, unreleasedDirectory);
            moveUnreleasedChangelogEntryFiles(unreleasedDirectory, releaseDirectory);
        } else {
            System.out.format(
                    "release directory `%s` doesn't exist, simply renaming the unreleased directory `%s`%n",
                    releaseDirectory, unreleasedDirectory);
            moveUnreleasedDirectory(unreleasedDirectory, releaseDirectory);
        }
    }

    private static void moveUnreleasedChangelogEntryFiles(final Path unreleasedDirectory, final Path releaseDirectory) {
        FileUtils.findAdjacentFiles(unreleasedDirectory, true, paths -> {
            paths.forEach(unreleasedChangelogEntryFile -> {
                final String fileName = unreleasedChangelogEntryFile.getFileName().toString();
                final Path releasedChangelogEntryFile = releaseDirectory.resolve(fileName);
                System.out.format(
                        "moving changelog entry file `%s` to `%s`%n",
                        unreleasedChangelogEntryFile, releasedChangelogEntryFile);
                try {
                    Files.move(unreleasedChangelogEntryFile, releasedChangelogEntryFile);
                } catch (final IOException error) {
                    throw new UncheckedIOException(error);
                }
            });
            return 1;
        });
    }

    private static void moveUnreleasedDirectory(
            final Path unreleasedDirectory,
            final Path releaseDirectory)
            throws IOException {
        if (!Files.exists(unreleasedDirectory)) {
            final String message = String.format(
                    "`%s` does not exist! A release without any changelogs don't make sense!",
                    unreleasedDirectory);
            throw new IllegalStateException(message);
        }
        System.out.format("moving changelog directory `%s` to `%s`%n", unreleasedDirectory, releaseDirectory);
        Files.move(unreleasedDirectory, releaseDirectory);
        Files.createDirectories(unreleasedDirectory);
    }

    private static void populateReleaseXmlFiles(
            final String releaseDate,
            final String releaseVersion,
            final Path releaseDirectory)
            throws IOException {
        final Path releaseXmlFile = ChangelogFiles.releaseXmlFile(releaseDirectory);
        System.out.format("writing release information to `%s`%n", releaseXmlFile);
        final ChangelogRelease changelogRelease = new ChangelogRelease(releaseVersion, releaseDate);
        Files.deleteIfExists(releaseXmlFile);
        changelogRelease.writeToXmlFile(releaseXmlFile);
    }

    private static void populateReleaseChangelogTemplateFile(
            final Path unreleasedDirectory,
            final Path releaseDirectory)
            throws IOException {
        Set<String> releaseChangelogTemplateFileNames = releaseChangelogTemplateFileNames(unreleasedDirectory);
        for (final String releaseChangelogTemplateFileName : releaseChangelogTemplateFileNames) {
            final Path targetFile = releaseDirectory.resolve(releaseChangelogTemplateFileName);
            if (Files.exists(targetFile)) {
                System.out.format("keeping the existing changelog template file: `%s`%n", targetFile);
            } else {
                final Path sourceFile = unreleasedDirectory.resolve(releaseChangelogTemplateFileName);
                System.out.format("moving the changelog template file `%s` to `%s`%n", sourceFile, targetFile);
                Files.move(sourceFile, targetFile);
            }
        }
    }

    private static Set<String> releaseChangelogTemplateFileNames(final Path releaseDirectory) {
        final String templateFileNameSuffix = '.' + ChangelogFiles.templateFileNameExtension();
        return FileUtils.findAdjacentFiles(
                releaseDirectory,
                false,
                paths -> paths
                        .filter(path -> path.endsWith(templateFileNameSuffix))
                        .map(path -> path.getFileName().toString())
                        .collect(Collectors.toSet()));
    }

}
