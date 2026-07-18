package ai.aegis.validation;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class WalkForwardValidationService {

    public WalkForwardResult validate(List<Double> strategyReturns, int folds, double trainFraction) {
        if (strategyReturns == null || strategyReturns.size() < 100) {
            throw new IllegalArgumentException("at least one hundred chronological returns are required");
        }
        if (folds < 3 || folds > 20) throw new IllegalArgumentException("folds must be between 3 and 20");
        if (trainFraction < 0.50 || trainFraction > 0.90) {
            throw new IllegalArgumentException("trainFraction must be between 0.50 and 0.90");
        }

        int foldSize = strategyReturns.size() / folds;
        if (foldSize < 10) throw new IllegalArgumentException("each fold must contain at least ten returns");

        List<Double> inSample = new ArrayList<>();
        List<Double> outOfSample = new ArrayList<>();
        int profitableOutOfSampleFolds = 0;

        for (int fold = 0; fold < folds; fold++) {
            int start = fold * foldSize;
            int end = fold == folds - 1 ? strategyReturns.size() : start + foldSize;
            List<Double> segment = strategyReturns.subList(start, end);
            int split = Math.max(1, Math.min(segment.size() - 1, (int) Math.floor(segment.size() * trainFraction)));
            double trainMean = mean(segment.subList(0, split));
            double testMean = mean(segment.subList(split, segment.size()));
            inSample.add(trainMean);
            outOfSample.add(testMean);
            if (testMean > 0) profitableOutOfSampleFolds++;
        }

        double inMean = mean(inSample);
        double outMean = mean(outOfSample);
        double efficiency = Math.abs(inMean) < 1e-12 ? 0 : outMean / inMean;
        double profitableRatio = (double) profitableOutOfSampleFolds / folds;

        List<String> warnings = new ArrayList<>();
        if (outMean <= 0) warnings.add("Out-of-sample expectancy is non-positive");
        if (efficiency < 0.50) warnings.add("Less than half of in-sample performance survived out of sample");
        if (efficiency > 1.50) warnings.add("Out-of-sample uplift is unusually large and may indicate unstable folds");
        if (profitableRatio < 0.60) warnings.add("Fewer than 60% of out-of-sample folds were profitable");

        boolean passed = outMean > 0 && efficiency >= 0.50 && efficiency <= 1.50 && profitableRatio >= 0.60;
        return new WalkForwardResult(folds, bd(inMean), bd(outMean), bd(efficiency), bd(profitableRatio),
                passed, List.copyOf(warnings));
    }

    private double mean(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    private BigDecimal bd(double value) {
        return BigDecimal.valueOf(value).setScale(8, RoundingMode.HALF_UP);
    }
}
