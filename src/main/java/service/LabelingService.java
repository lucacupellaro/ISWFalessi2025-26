package service;



import domain.GitCommit;
import domain.JavaClass;
import domain.ProjectVersion;

import java.util.List;

public class LabelingService {

    //riceve tutte le classi del progetto , i commit relativi per il ticket, le realese, la IV e la FV. estrae le classi Java modificate da tali commit,
    public void assignLabels(List<JavaClass> classes,
                             List<GitCommit> commits,
                             List<ProjectVersion> windowedReleases,
                             ProjectVersion injectionVersion,
                             ProjectVersion fixVersion) {

        List<String> touchedClasses = findTouchedClasses(commits);

        //controlla se per ogni classe e stata toccata dal ticket
        for (JavaClass javaClass : classes) {
            if (!touchedClasses.contains(javaClass.getPath())) {
                continue; //se non lo è la salta
            }
        //Se la classe è già stata etichettata buggy = true da un ticket precedente, non la tocca.
            if (!javaClass.isBuggy()) {
                boolean isBuggy = isBuggyInRelease(javaClass, injectionVersion,
                        fixVersion, windowedReleases);
                javaClass.setBuggy(isBuggy);
            }
        }
    }

    //per sapere quali classi sono state modificate, dati una lista di commits
    private List<String> findTouchedClasses(List<GitCommit> commits) {
        return commits.stream()
                .flatMap(c -> c.getTouchedPaths().stream())
                .distinct()
                .toList();
    }

    //controlla se data una calsse X, Se si verifica:IV<=X<FX->Buggy.
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