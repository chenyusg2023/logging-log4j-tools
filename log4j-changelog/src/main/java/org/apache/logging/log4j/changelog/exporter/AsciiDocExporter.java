/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache license, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the license for the specific language governing permissions and
 * limitations under the license.
 */
package org.apache.logging.log4j.changelog.exporter;

import org.apache.logging.log4j.AsciiDocUtils;
import org.apache.logging.log4j.FileUtils;
import org.apache.logging.log4j.changelog.ChangelogEntry;
import org.apache.logging.log4j.changelog.ChangelogFiles;
import org.apache.logging.log4j.changelog.ChangelogRelease;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public final class AsciiDocExporter {

    private static final String AUTO_GENERATION_WARNING_ASCIIDOC = "////\n" +
            "*DO NOT EDIT THIS FILE!*\n" +
            "This file is automatically generated from the release changelog directory!\n" +
            "////\n";

    private AsciiDocExporter() {}

    public static void main(final String[] mainArgs) {

        // Read arguments.
        final AsciiDocExporterArgs args = AsciiDocExporterArgs.fromSystemProperties();

        // Find release directories.
        final List<Path> releaseDirectories = FileUtils
                .findAdjacentFiles(args.changelogDirectory)
                .filter(file -> file.toFile().isDirectory())
                .sorted(Comparator.comparing(releaseDirectory -> {
                    final Path releaseXmlFile = ChangelogFiles.releaseXmlFile(releaseDirectory);
                    final ChangelogRelease changelogRelease = ChangelogRelease.readFromXmlFile(releaseXmlFile);
                    return changelogRelease.date;
                }))
                .collect(Collectors.toList());
        final int releaseDirectoryCount = releaseDirectories.size();

        // Read the release information files.
        final List<ChangelogRelease> changelogReleases = releaseDirectories
                .stream()
                .map(releaseDirectory -> {
                    final Path releaseXmlFile = ChangelogFiles.releaseXmlFile(releaseDirectory);
                    return ChangelogRelease.readFromXmlFile(releaseXmlFile);
                })
                .collect(Collectors.toList());

        // Export releases.
        if (releaseDirectoryCount > 0) {

            // Export each release directory.
            for (int releaseIndex = 0; releaseIndex < releaseDirectories.size(); releaseIndex++) {
                final Path releaseDirectory = releaseDirectories.get(releaseIndex);
                final ChangelogRelease changelogRelease = changelogReleases.get(releaseIndex);
                try {
                    exportRelease(args.outputDirectory, releaseDirectory, changelogRelease);
                } catch (final Exception error) {
                    final String message =
                            String.format("failed exporting release from directory `%s`", releaseDirectory);
                    throw new RuntimeException(message, error);
                }
            }

            // Report the operation.
            if (releaseDirectoryCount == 1) {
                System.out.format("exported a single release directory: `%s`%n", releaseDirectories.get(0));
            } else {
                System.out.format(
                        "exported %d release directories: ..., `%s`%n",
                        releaseDirectories.size(),
                        releaseDirectories.get(releaseDirectoryCount - 1));
            }

        }

        // Export unreleased.
        ChangelogFiles
                .unreleasedDirectoryVersionMajors(args.changelogDirectory)
                .stream()
                .sorted(Comparator.reverseOrder())
                .forEach(upcomingReleaseVersionMajor -> {
                    final Path upcomingReleaseDirectory =
                            ChangelogFiles.unreleasedDirectory(args.changelogDirectory, upcomingReleaseVersionMajor);
                    final ChangelogRelease upcomingRelease = upcomingRelease(upcomingReleaseVersionMajor);
                    System.out.format("exporting upcoming release directory: `%s`%n", upcomingReleaseDirectory);
                    exportUnreleased(args.outputDirectory, upcomingReleaseDirectory, upcomingRelease);
                    changelogReleases.add(upcomingRelease);
                });

        // Export the release index.
        exportReleaseIndex(args.outputDirectory, changelogReleases);

    }

    private static void exportRelease(
            final Path outputDirectory,
            final Path releaseDirectory,
            final ChangelogRelease changelogRelease) {
        final String introAsciiDoc = readIntroAsciiDoc(releaseDirectory);
        final List<ChangelogEntry> changelogEntries = readChangelogEntries(releaseDirectory);
        try {
            exportRelease(outputDirectory, changelogRelease, introAsciiDoc, changelogEntries);
        } catch (final IOException error) {
            final String message = String.format("failed exporting release from directory `%s`", releaseDirectory);
            throw new UncheckedIOException(message, error);
        }
    }

    private static String readIntroAsciiDoc(final Path releaseDirectory) {

        // Determine the file to be read.
        final Path introAsciiDocFile = ChangelogFiles.introAsciiDocFile(releaseDirectory);
        if (!Files.exists(introAsciiDocFile)) {
            return "";
        }

        // Read the file.
        final List<String> introAsciiDocLines;
        try {
            introAsciiDocLines = Files.readAllLines(introAsciiDocFile, StandardCharsets.UTF_8);
        } catch (final IOException error) {
            final String message = String.format("failed reading intro AsciiDoc file: `%s`", introAsciiDocFile);
            throw new UncheckedIOException(message, error);
        }

        // Erase comment blocks.
        final boolean[] inCommentBlock = {false};
        return introAsciiDocLines
                .stream()
                .filter(line -> {
                    final boolean commentBlock = "////".equals(line);
                    if (commentBlock) {
                        inCommentBlock[0] = !inCommentBlock[0];
                        return false;
                    }
                    return !inCommentBlock[0];
                })
                .collect(Collectors.joining("\n"))
                + "\n";

    }

    private static List<ChangelogEntry> readChangelogEntries(final Path releaseDirectory) {
        return FileUtils
                .findAdjacentFiles(releaseDirectory)
                // Sorting is needed to generate the same output between different runs.
                .sorted()
                .map(ChangelogEntry::readFromXmlFile)
                .collect(Collectors.toList());
    }

    private static void exportRelease(
            final Path outputDirectory,
            final ChangelogRelease release,
            final String introAsciiDoc,
            final List<ChangelogEntry> entries)
            throws IOException {
        final String asciiDocFilename = changelogReleaseAsciiDocFilename(release);
        final Path asciiDocFile = outputDirectory.resolve(asciiDocFilename);
        Files.createDirectories(asciiDocFile.getParent());
        final String asciiDoc = exportReleaseToAsciiDoc(release, introAsciiDoc, entries);
        final byte[] asciiDocBytes = asciiDoc.getBytes(StandardCharsets.UTF_8);
        Files.write(asciiDocFile, asciiDocBytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static String exportReleaseToAsciiDoc(
            final ChangelogRelease release,
            final String introAsciiDoc,
            final List<ChangelogEntry> entries) {

        // Write the header.
        final StringBuilder stringBuilder = new StringBuilder();
        stringBuilder
                .append(AsciiDocUtils.LICENSE_COMMENT_BLOCK)
                .append('\n')
                .append(AUTO_GENERATION_WARNING_ASCIIDOC)
                .append('\n')
                .append("= ")
                .append(release.version);
        if (release.date != null) {
            stringBuilder
                    .append(" (")
                    .append(release.date)
                    .append(")\n")
                    .append(introAsciiDoc)
                    .append("\n");
        } else {
            stringBuilder.append("\n\nChanges staged for the next version that is yet to be released.\n\n");
        }

        if (!entries.isEmpty()) {

            stringBuilder.append("== Changes\n");

            // Group entries by type.
            final Map<ChangelogEntry.Type, List<ChangelogEntry>> entriesByType = entries
                    .stream()
                    .collect(Collectors.groupingBy(changelogEntry -> changelogEntry.type));

            // Write entries for each type.
            entriesByType
                    .keySet()
                    .stream()
                    // Sorting is necessary for a consistent layout across different runs.
                    .sorted()
                    .forEach(type -> {
                        stringBuilder.append('\n');
                        appendEntryTypeHeader(stringBuilder, type);
                        entriesByType.get(type).forEach(entry -> appendEntry(stringBuilder, entry));
                    });

        }

        // Return the accumulated document so far.
        return stringBuilder.toString();

    }

    private static void appendEntryTypeHeader(final StringBuilder stringBuilder, final ChangelogEntry.Type type) {
        final String typeName = type.toString().toLowerCase(Locale.US);
        final String header = typeName.substring(0, 1).toUpperCase(Locale.US) + typeName.substring(1);
        stringBuilder
                .append("=== ")
                .append(header)
                .append("\n\n");
    }

    private static void appendEntry(final StringBuilder stringBuilder, final ChangelogEntry entry) {
        stringBuilder.append("* ");
        appendEntryDescription(stringBuilder, entry.description);
        final boolean containingIssues = !entry.issues.isEmpty();
        final boolean containingAuthors = !entry.authors.isEmpty();
        if (containingIssues || containingAuthors) {
            stringBuilder.append(" (");
            if (containingIssues) {
                appendEntryIssues(stringBuilder, entry.issues);
            }
            if (containingIssues && containingAuthors) {
                stringBuilder.append(' ');
            }
            if (containingAuthors) {
                appendEntryAuthors(stringBuilder, entry.authors);
            }
            stringBuilder.append(")");
        }
        stringBuilder.append('\n');
    }

    private static void appendEntryDescription(
            final StringBuilder stringBuilder,
            final ChangelogEntry.Description description) {
        if (!"asciidoc".equals(description.format)) {
            final String message = String.format("unsupported description format: `%s`", description.format);
            throw new RuntimeException(message);
        }
        stringBuilder.append(description.text);
    }

    private static void appendEntryIssues(
            final StringBuilder stringBuilder,
            final List<ChangelogEntry.Issue> issues) {
        stringBuilder.append("for ");
        final int issueCount = issues.size();
        for (int issueIndex = 0; issueIndex < issueCount; issueIndex++) {
            final ChangelogEntry.Issue issue = issues.get(issueIndex);
            appendEntryIssue(stringBuilder, issue);
            if ((issueIndex + 1) != issueCount) {
                stringBuilder.append(", ");
            }
        }
    }

    private static void appendEntryIssue(final StringBuilder stringBuilder, final ChangelogEntry.Issue issue) {
        stringBuilder
                .append(issue.link)
                .append('[')
                .append(issue.id)
                .append(']');
    }

    private static void appendEntryAuthors(
            final StringBuilder stringBuilder,
            final List<ChangelogEntry.Author> authors) {
        stringBuilder.append("by ");
        final int authorCount = authors.size();
        for (int authorIndex = 0; authorIndex < authors.size(); authorIndex++) {
            final ChangelogEntry.Author author = authors.get(authorIndex);
            appendEntryAuthor(stringBuilder, author);
            if ((authorIndex + 1) != authorCount) {
                stringBuilder.append(", ");
            }
        }
    }

    private static void appendEntryAuthor(final StringBuilder stringBuilder, final ChangelogEntry.Author author) {
        if (author.id != null) {
            stringBuilder
                    .append('`')
                    .append(author.id)
                    .append('`');
        } else {
            // Normalize author names written in `Doe, John` form.
            if (author.name.contains(",")) {
                String[] nameFields = author.name.split(",", 2);
                stringBuilder.append(nameFields[1].trim());
                stringBuilder.append(nameFields[0].trim());
            } else {
                stringBuilder.append(author.name);
            }
        }
    }

    private static void exportUnreleased(
            final Path outputDirectory,
            final Path upcomingReleaseDirectory,
            final ChangelogRelease upcomingRelease) {
        final List<ChangelogEntry> changelogEntries = readChangelogEntries(upcomingReleaseDirectory);
        try {
            exportRelease(outputDirectory, upcomingRelease, null, changelogEntries);
        } catch (final IOException error) {
            throw new UncheckedIOException("failed exporting unreleased changes", error);
        }
    }

    private static ChangelogRelease upcomingRelease(final int versionMajor) {
        final String releaseVersion = versionMajor + ".x.x";
        return new ChangelogRelease(releaseVersion, null);
    }

    private static void exportReleaseIndex(
            final Path outputDirectory,
            final List<ChangelogRelease> changelogReleases) {
        final String asciiDoc = exportReleaseIndexToAsciiDoc(changelogReleases);
        final byte[] asciiDocBytes = asciiDoc.getBytes(StandardCharsets.UTF_8);
        final Path asciiDocFile = outputDirectory.resolve("index.adoc");
        System.out.format("exporting release index to `%s`%n", asciiDocFile);
        try {
            Files.write(asciiDocFile, asciiDocBytes);
        } catch (final IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    private static String exportReleaseIndexToAsciiDoc(final List<ChangelogRelease> changelogReleases) {
        final StringBuilder stringBuilder = new StringBuilder();
        stringBuilder
                .append(AsciiDocUtils.LICENSE_COMMENT_BLOCK)
                .append('\n')
                .append(AUTO_GENERATION_WARNING_ASCIIDOC)
                .append("\n= Release changelogs\n\n");
        for (int releaseIndex = changelogReleases.size() - 1; releaseIndex >= 0; releaseIndex--) {
            final ChangelogRelease changelogRelease = changelogReleases.get(releaseIndex);
            final String asciiDocFilename = changelogReleaseAsciiDocFilename(changelogRelease);
            final String asciiDocBulletDateSuffix = changelogRelease.date != null
                    ? (" (" + changelogRelease.date + ')')
                    : "";
            final String asciiDocBullet = String.format(
                    "* xref:%s[%s]%s\n",
                    asciiDocFilename,
                    changelogRelease.version,
                    asciiDocBulletDateSuffix);
            stringBuilder.append(asciiDocBullet);
        }
        return stringBuilder.toString();
    }

    private static String changelogReleaseAsciiDocFilename(final ChangelogRelease changelogRelease) {
        // Using only the version (that is, avoiding the date) in the filename so that one can determine the link to the changelog of a particular release with only version information.
        return String.format("%s.adoc", changelogRelease.version);
    }

}
