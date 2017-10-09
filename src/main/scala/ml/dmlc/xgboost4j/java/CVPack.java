package ml.dmlc.xgboost4j.java;

import java.util.Map;

/**
 * @author Thomas Moerman
 */
public class CVPack {

    final DMatrix dtrain;
    final DMatrix dtest;
    final DMatrix[] dmats;
    final String[] names;
    final Booster booster;

    /**
     * Constructor. Creates a Booster internally.
     *
     * @param dtrain
     * @param dtest
     * @param params
     * @throws XGBoostError
     */
    public CVPack(final DMatrix dtrain,
                  final DMatrix dtest,
                  final Map<String, Object> params) throws XGBoostError {

        this.dmats      = new DMatrix[]{dtrain, dtest};
        this.booster    = new Booster(params, dmats);
        this.names      = new String[]{"train", "test"};
        this.dtrain     = dtrain;
        this.dtest      = dtest;
    }

    /**
     * @param iteration
     * @return Returns the evaluation of the boosting iteration.
     * @throws XGBoostError
     */
    public String updateAndEval(final int iteration) throws XGBoostError {
        booster.update(dtrain, iteration);

        return booster.evalSet(dmats, names, iteration);
    }

    public void dispose() throws XGBoostError {
        booster.dispose();
        dtrain.dispose();
        dtest.dispose();
    }

    public Booster getBooster() {
        return booster;
    }

}