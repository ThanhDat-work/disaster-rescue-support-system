package vn.edu.hcmute.drss.dtos;

import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.LinkedHashMap;

public record ProfileStatistics(
        Map<String, Long> cards,
        Map<String, Long> distribution,
        List<String> labels,
        Map<String, List<Long>> series,
        List<String> activity
) {
    public ProfileStatistics {
        cards = Collections.unmodifiableMap(new LinkedHashMap<>(cards));
        distribution = Collections.unmodifiableMap(new LinkedHashMap<>(distribution));
        labels = List.copyOf(labels);
        series = Collections.unmodifiableMap(new LinkedHashMap<>(series));
        activity = List.copyOf(activity);
    }
}