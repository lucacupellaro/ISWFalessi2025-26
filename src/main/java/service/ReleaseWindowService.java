package service;


import domain.ProjectVersion;
import java.util.List;

//ordina le release e taglia il 66%
public class ReleaseWindowService {

    private final int windowPercent;

    public ReleaseWindowService(int windowPercent) {
        this.windowPercent = windowPercent;
    }

    //tagli ail 33% delle realese
    public List<ProjectVersion> getWindowedReleases(List<ProjectVersion> allReleases) {
        List<ProjectVersion> sorted = allReleases.stream()
                .filter(v -> v.getReleaseDate() != null)
                .sorted((a, b) -> a.getReleaseDate().compareTo(b.getReleaseDate()))
                .toList();

        int cutoff = (int) Math.ceil(sorted.size() * windowPercent / 100.0);
        return sorted.subList(0, cutoff);
    }

    //dato un commit, trova la release a cui appartiene per data
    public ProjectVersion assignCommitToRelease(List<ProjectVersion> windowedReleases,
                                                java.time.LocalDate commitDate) {
        for (int i = 0; i < windowedReleases.size(); i++) {
            ProjectVersion current = windowedReleases.get(i);
            boolean isLast = (i == windowedReleases.size() - 1);
            ProjectVersion next = isLast ? null : windowedReleases.get(i + 1);

            boolean afterCurrent = !commitDate.isBefore(current.getReleaseDate());
            boolean beforeNext = next == null || commitDate.isBefore(next.getReleaseDate());

            if (afterCurrent && beforeNext) {
                return current;
            }
        }
        return null;
    }
}
