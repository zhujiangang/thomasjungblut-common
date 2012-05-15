package de.jungblut.pgm;

import java.text.DecimalFormat;
import java.util.List;

import com.google.common.base.Preconditions;

import de.jungblut.math.dense.DenseDoubleMatrix;
import de.jungblut.math.dense.DenseDoubleVector;

/**
 * A hidden markov model trained by a Baum Welch algorithm. Based on the famous
 * implementation of Holger Wunsch.<br/>
 * TODO needs to be vectorized when working correctly!
 */
public final class HMM {

  // number of states
  private final int numStates;
  // sigma - output vocabulary
  private final int numOutputStates;

  // pi - initial state probabilities
  private final DenseDoubleVector initialProbability;
  // a - transition probabilities
  private final DenseDoubleMatrix transitionProbabilities;
  // b - emission probabilities
  private final DenseDoubleMatrix emissionProbabilities;

  public HMM(int numStates, int numOutputStates) {
    Preconditions.checkArgument(numStates > 1);
    Preconditions.checkArgument(numOutputStates > 1);
    this.numStates = numStates;
    this.numOutputStates = numOutputStates;
    this.initialProbability = new DenseDoubleVector(numStates, 1.0d / numStates);
    this.transitionProbabilities = new DenseDoubleMatrix(numStates, numStates,
        1.0d / numStates);
    this.emissionProbabilities = new DenseDoubleMatrix(numStates,
        numOutputStates);
  }

  public double getProbability(int time, int stateI, int stateJ,
      DenseDoubleVector observation, double[][] forward, double[][] backward) {

    double num;
    if (time == observation.getLength() - 1) {
      num = forward[stateI][time] * transitionProbabilities.get(stateI, stateJ);
    } else {
      num = forward[stateI][time] * transitionProbabilities.get(stateI, stateJ)
          * emissionProbabilities.get(stateJ, (int) observation.get(time + 1))
          * backward[stateJ][time + 1];
    }
    double denom = 0;

    for (int k = 0; k < numStates; k++) {
      denom += (forward[k][time] * backward[k][time]);
    }
    return denom != 0.0d ? num / denom : 0.0d;
  }

  public void trainBaumWelch(List<DenseDoubleVector> observations, int steps) {
    double pi1[] = new double[numStates];
    double a1[][] = new double[numStates][numStates];
    double b1[][] = new double[numStates][numOutputStates];

    for (int s = 0; s < steps; s++) {
      for (DenseDoubleVector o : observations) {
        // calculate forward and backward probabilities
        double[][] fwd = calculateForwardProbabilities(o);
        double[][] bwd = calculateBackwardProbabilities(o);

        // re-estimation of initial state probabilities
        for (int i = 0; i < numStates; i++) {
          pi1[i] = gamma(i, 0, fwd, bwd);
        }

        // re-estimation of transition probabilities
        for (int i = 0; i < numStates; i++) {
          for (int j = 0; j < numStates; j++) {
            double num = 0;
            double denom = 0;
            for (int t = 0; t <= o.getLength() - 1; t++) {
              num += getProbability(t, i, j, o, fwd, bwd);
              denom += gamma(i, t, fwd, bwd);
            }
            a1[i][j] = denom != 0.0d ? num / denom : 0.0d;
          }
        }

        // re-estimation of emission probabilities
        for (int i = 0; i < numStates; i++) {
          for (int k = 0; k < numOutputStates; k++) {
            double num = 0;
            double denom = 0;

            for (int t = 0; t <= o.getLength() - 1; t++) {
              double g = gamma(i, t, fwd, bwd);
              num += g * (k == o.get(t) ? 1.0d : 0.0d);
              denom += g;
            }
            b1[i][k] = denom != 0.0d ? num / denom : 0.0d;
          }
        }
      }
      // TODO calculate the kullback leibler divergence of the output
      // pi = pi1;
      // a = a1;
      // b = b1;
    }
  }

  public double[][] calculateForwardProbabilities(DenseDoubleVector o) {
    double[][] fwd = new double[numStates][o.getLength()];

    // initialization (time 0)
    for (int i = 0; i < numStates; i++)
      fwd[i][0] = initialProbability.get(i)
          * emissionProbabilities.get(i, ((int) o.get(0)));

    // induction
    for (int t = 0; t <= o.getLength() - 2; t++) {
      for (int j = 0; j < numStates; j++) {
        fwd[j][t + 1] = 0;
        for (int i = 0; i < numStates; i++)
          fwd[j][t + 1] += (fwd[i][t] * transitionProbabilities.get(i, j));
        fwd[j][t + 1] *= emissionProbabilities.get(j, (int) o.get(t + 1));
      }
    }

    return fwd;
  }

  public double[][] calculateBackwardProbabilities(DenseDoubleVector o) {
    double[][] bwd = new double[numStates][o.getLength()];

    // initialization (time 0)
    for (int i = 0; i < numStates; i++) {
      bwd[i][o.getLength() - 1] = 1;
    }

    // induction
    for (int t = o.getLength() - 2; t >= 0; t--) {
      for (int i = 0; i < numStates; i++) {
        bwd[i][t] = 0;
        for (int j = 0; j < numStates; j++)
          bwd[i][t] += (bwd[j][t + 1] * transitionProbabilities.get(i, j) * emissionProbabilities
              .get(j, (int) o.get(t + 1)));
      }
    }

    return bwd;
  }

  public double gamma(int i, int t, double[][] fwd, double[][] bwd) {
    double num = fwd[i][t] * bwd[i][t];
    double denom = 0;

    for (int j = 0; j < numStates; j++) {
      denom += fwd[j][t] * bwd[j][t];
    }

    return denom != 0.0d ? num / denom : 0.0d;
  }

  private void print() {
    DecimalFormat fmt = new DecimalFormat();
    fmt.setMinimumFractionDigits(5);
    fmt.setMaximumFractionDigits(5);

    for (int i = 0; i < numStates; i++)
      System.out.println("pi(" + i + ") = "
          + fmt.format(initialProbability.get(i)));
    System.out.println();

    for (int i = 0; i < numStates; i++) {
      for (int j = 0; j < numStates; j++)
        System.out.print("a(" + i + "," + j + ") = "
            + fmt.format(transitionProbabilities.get(i, j)) + "  ");
      System.out.println();
    }

    System.out.println();
    for (int i = 0; i < numStates; i++) {
      for (int k = 0; k < numOutputStates; k++)
        System.out.print("b(" + i + "," + k + ") = "
            + fmt.format(emissionProbabilities.get(i, k)) + "  ");
      System.out.println();
    }
  }

  public static void main(String[] args) {

  }

}
