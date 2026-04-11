package service;



import domain.GitCommit;
import domain.JavaClass;
import domain.ProjectVersion;

import java.util.List;

public class LabelingService {

    public void assignLabels(List<JavaClass> classes,
                             List<GitCommit> commits,
                             List<ProjectVersion> windowedReleases,
                             String ticketId,
                             ProjectVersion injectionVersion,
                             ProjectVersion fixVersion) {

        List<String> touchedClasses = findTouchedClasses(commits, ticketId);

        for (JavaClass javaClass : classes) {
            if (!touchedClasses.contains(javaClass.getPath())) {
                continue;
            }

            if (!javaClass.isBuggy()) {
                boolean isBuggy = isBuggyInRelease(javaClass, injectionVersion,
                        fixVersion, windowedReleases);
                javaClass.setBuggy(isBuggy);
            }
        }
    }

    private List<String> findTouchedClasses(List<GitCommit> commits, String ticketId) {
        return commits.stream()
                .filter(c -> c.getMessage().contains(ticketId))
                .flatMap(c -> c.getTouchedPaths().stream())
                .distinct()
                .toList();
    }

    private boolean isBuggyInRelease(JavaClass javaClass,
                                     ProjectVersion injectionVersion,
                                     ProjectVersion fixVersion,
                                     List<ProjectVersion> windowedReleases) {

        ProjectVersion classRelease = windowedReleases.stream()
                .filter(r -> r.getName().equals(javaClass.getRelease()))
                .findFirst()
                .orElse(null);

        if (classRelease == null) {
            return false;
        }

        boolean afterOrAtInjection = !classRelease.getReleaseDate()
                .isBefore(injectionVersion.getReleaseDate());
        boolean beforeFix = classRelease.getReleaseDate()
                .isBefore(fixVersion.getReleaseDate());

        return afterOrAtInjection && beforeFix;
    }
}