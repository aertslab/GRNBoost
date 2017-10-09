package ml.dmlc.xgboost4j.java;

import java.util.ArrayList;
import java.util.List;

/**
 * A Java helper class necessary to prevent XGBoost crashes that happen when the XGBoost native code is called from
 * Scala. My guess is that the native code must be called from a static Java method. Nesting calls to native XGBoost
 * routines from within Scala functions breaks in unpredictable ways.
 *
 * @author Thomas Moerman
 */
public class GRNBoostExtras {

    /**
     * Generic predicate-like interface to control how many updates the booster needs until it reaches an inflection
     * point on the test-rmse curve.
     *
     * @param <E> The generic return type.
     */
    public interface Predicate<E> {

        /**
         * @param evaluationHistory
         * @return Returns an instance of E. Considered true if it is defined (see next method), otherwise false.
         */
        E apply(List<String> evaluationHistory);

        /**
         * @param e E
         * @return Returns whether the instance of E is defined. A defined E is considered true, otherwise false.
         */
        boolean isDefined(E e);

    }

    /**
     * @param cvPack The CVPack to update until the predicate is satisfied.
     * @param maxRounds The maximum number of boosting rounds to apply and evaluate.
     * @param batchSize The batch size.
     * @param predicate The predicate. In practice, the predicate will return a defined (index) value when an inflection
     *                  point (i.e. several consecutive points are more or less collinear with the last point) is reached.
     *                  The inflection point is where the test error flattens out with increasing boosting rounds, that's
     *                  where we can stop. From the perspective of this class, the inflection point logic is abstracted
     *                  away behind this "predicate" function.
     * @param <E> Generic return type.
     * @return Returns the generic result of the predicate.
     * @throws XGBoostError
     */
    public static <E> E updateWhile(final CVPack cvPack,
                                    final int maxRounds,
                                    final int batchSize,
                                    final Predicate<E> predicate) throws XGBoostError {

        final List<String> evaluationHistory = new ArrayList<>();

        int nrRoundsCompleted = 0;

        E result;

        do {

            for (int i = 0; i < batchSize; i++) {
                final int currentRound = nrRoundsCompleted + i;
                final String eval = cvPack.updateAndEval(currentRound);

                evaluationHistory.add(eval);
            }

            nrRoundsCompleted += batchSize;

            result = predicate.apply(evaluationHistory);

        } while ((! predicate.isDefined(result)) && nrRoundsCompleted < maxRounds);

        return result;
    }

}