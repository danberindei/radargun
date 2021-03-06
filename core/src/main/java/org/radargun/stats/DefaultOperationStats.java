package org.radargun.stats;

import org.radargun.config.DefinitionElement;
import org.radargun.stats.representation.BoxAndWhiskers;
import org.radargun.stats.representation.DefaultOutcome;
import org.radargun.stats.representation.MeanAndDev;
import org.radargun.stats.representation.OperationThroughput;

/**
 * Underlying statistical data gathered for single operation type.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@DefinitionElement(name = "default", doc = "Operations statistics with fixed memory footprint.")
public class DefaultOperationStats implements OperationStats {
   private static final double INVERSE_NORMAL_95 = 1.96;
   private static final double INVERSE_NORMAL_50 = 0.67448;
   private long requests;
   private long responseTimeMax = Long.MIN_VALUE;
   private long responseTimeSum;
   private double responseTimeMean; // first moment
   private double responseTimeM2; // second moment, var = M2 / (n - 1)
   private long errors;

   @Override
   public DefaultOperationStats newInstance() {
      return new DefaultOperationStats();
   }

   @Override
   public DefaultOperationStats copy() {
      DefaultOperationStats copy = newInstance();
      copy.requests = requests;
      copy.responseTimeMax = responseTimeMax;
      copy.responseTimeSum = responseTimeSum;
      copy.responseTimeMean = responseTimeMean;
      copy.responseTimeM2 = responseTimeM2;
      copy.errors = errors;
      return copy;
   }

   @Override
   public void merge(OperationStats o) {
      if (!(o instanceof DefaultOperationStats)) throw new IllegalArgumentException(o.toString());
      DefaultOperationStats other = (DefaultOperationStats) o;
      responseTimeM2 = mergeM2(responseTimeMean, responseTimeM2, requests, other.responseTimeMean, other.responseTimeM2, other.requests);
      responseTimeMean = mergeMean(responseTimeMean, requests, other.responseTimeMean, other.requests);
      requests += other.requests;
      responseTimeMax = Math.max(responseTimeMax, other.responseTimeMax);
      responseTimeSum += other.responseTimeSum;
      errors += other.errors;
   }

   private static double mergeMean(double myMean, double myN, double otherMean, double otherN) {
      if (myN + otherN == 0) return .0;
      return (myMean * myN + otherMean * otherN) / (myN + otherN);
   }

   private static double mergeM2(double myMean, double myM2, double myN, double otherMean, double otherM2, double otherN) {
      if (myN + otherN == 0) return .0;
      double delta = myMean - otherMean;
      return myM2 + otherM2 + delta * delta * otherN * myN / (otherN + myN);
   }

   public String toString() {
      return requests == 0 ? "requests=0" : String.format("requests=%d, responseTimeMax=%d, responseTimeSum=%d, errors=%d",
         requests, responseTimeMax, responseTimeSum, errors);
   }

   @Override
   public void registerRequest(long responseTime) {
      requests++;
      responseTimeMax = Math.max(responseTimeMax, responseTime);
      responseTimeSum += responseTime;
      // see http://en.wikipedia.org/wiki/Algorithms_for_calculating_variance#Online_algorithm
      double delta = (double) responseTime - responseTimeMean;
      responseTimeMean += delta / (double) requests;
      responseTimeM2 += delta * ((double) responseTime - responseTimeMean);
   }

   @Override
   public void registerError(long responseTime) {
      registerRequest(responseTime);
      errors++;
   }

   public BoxAndWhiskers getBoxAndWhiskers() {
      if (requests < 2) {
         return new BoxAndWhiskers(responseTimeMean, responseTimeMean, responseTimeMean, responseTimeMean, responseTimeMean);
      }
      double stddev = Math.sqrt(responseTimeM2 / (double) (requests - 1));
      return new BoxAndWhiskers(responseTimeMean + INVERSE_NORMAL_95 * stddev, responseTimeMean + INVERSE_NORMAL_50 * stddev,
         responseTimeMean, responseTimeMean - INVERSE_NORMAL_50 * stddev, responseTimeMean - INVERSE_NORMAL_95 * stddev);
   }

   public MeanAndDev getMeanAndDev() {
      if (requests < 2) return new MeanAndDev(responseTimeMean, 0);
      double stddev = Math.sqrt(responseTimeM2 / (double) (requests - 1));
      return new MeanAndDev(responseTimeMean, stddev);
   }

   @Override
   public <T> T getRepresentation(Class<T> clazz, Object... args) {
      if (clazz == DefaultOutcome.class) {
         return (T) new DefaultOutcome(requests, errors, responseTimeMean, responseTimeMax);
      } else if (clazz == MeanAndDev.class) {
         return (T) getMeanAndDev();
      } else if (clazz == OperationThroughput.class) {
         return (T) OperationThroughput.compute(requests, errors, args);
      } else if (clazz == BoxAndWhiskers.class) {
         return (T) getBoxAndWhiskers();
      } else {
         return null;
      }
   }

   @Override
   public boolean isEmpty() {
      return requests == 0;
   }
}
