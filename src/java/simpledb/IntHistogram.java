package simpledb;

import org.junit.Assert;

import java.util.ArrayList;
import java.util.List;

/**
 * A class to represent a fixed-width histogram over a single integer-based field.
 *
 * finish in lab3 exercise1
 */
public class IntHistogram {

    public static void main(String[] args) {
        IntHistogram h1 = new IntHistogram(7, 3, 89);
        System.out.println(h1.width);
        System.out.println(h1.getIndex(14));
        System.out.println(h1.getIndex(15));
        System.out.println(h1.getIndex(16));
        System.out.println(h1.getIndex(89));
        System.out.println(h1.getIndex(78));


        IntHistogram h2 = new IntHistogram(10, 1, 100);
        System.out.println(h2.getIndex(1));
        System.out.println(h2.getIndex(10));
        System.out.println(h2.getIndex(11));

//        // Set some values
//        h.addValue(3);
//        h.addValue(3);
//        h.addValue(3);
//        h.addValue(1);
//        h.addValue(10);
//
//        // Be conservative in case of alternate implementations
//        System.out.println(h.estimateSelectivity(Predicate.Op.GREATER_THAN_OR_EQ, -1));
//        System.out.println(h.estimateSelectivity(Predicate.Op.GREATER_THAN_OR_EQ, 2));
//        System.out.println(h.estimateSelectivity(Predicate.Op.GREATER_THAN_OR_EQ, 3));
//        System.out.println(h.estimateSelectivity(Predicate.Op.GREATER_THAN_OR_EQ, 4));
//        System.out.println(h.estimateSelectivity(Predicate.Op.GREATER_THAN_OR_EQ, 12));
    }

    /**
     * The number of buckets to split the input value into
     */
    private int numBuckets;
    /**
     * The minimum integer value that will ever be passed to this class for histogramming
     */
    private int minValue;
    /**
     * The maximum integer value that will ever be passed to this class for histogramming
     */
    private int maxValue;

    private double width;

    private List<Bucket> buckets;

    private int ntup;

    /**
     * Create a new IntHistogram.
     * <p>
     * This IntHistogram should maintain a histogram of integer values that it receives.
     * It should split the histogram into "buckets" buckets.
     * <p>
     * The values that are being histogrammed will be provided one-at-a-time through the "addValue()" function.
     * <p>
     * Your implementation should use space and have execution time that are both
     * constant with respect to the number of values being histogrammed.  For example, you shouldn't
     * simply store every value that you see in a sorted list.
     *
     * @param buckets The number of buckets to split the input value into.
     * @param min     The minimum integer value that will ever be passed to this class for histogramming
     * @param max     The maximum integer value that will ever be passed to this class for histogramming
     */
    public IntHistogram(int buckets, int min, int max) {
        this.numBuckets = buckets;
        this.minValue = min;
        this.maxValue = max;
        this.width = (1.0 + max - min) / (buckets);
        this.buckets = new ArrayList<>();
        for (int i = 0; i < numBuckets; i++) {
            int left = (int) Math.ceil(min + i * width);
            int right = (int) Math.ceil (min + (i + 1) * width)-1;
            if(right<left){
                right = left;
            }
//            System.out.println("left:" + left + "   right:" + right);
            this.buckets.add(new Bucket(left, right));
        }
    }

    private int getIndex(int v) {
        int res;
        res = (int) ((v - minValue) / width);
        return res;
    }

    /**
     * Add a value to the set of values that you are keeping a histogram of.
     *
     * @param v Value to add to the histogram
     */
    public void addValue(int v) {
        int index = getIndex(v);
        buckets.get(index).addCount();
        ntup++;
    }

    /**
     * Estimate the selectivity of a particular predicate and operand on this table.
     * <p>
     * For example, if "op" is "GREATER_THAN" and "v" is 5,
     * return your estimate of the fraction of elements that are greater than 5.
     *
     * @param op Operator
     * @param v  Value
     * @return Predicted selectivity of this particular operator and value
     */
    public double estimateSelectivity(Predicate.Op op, int v) {
        int index;
        double sum;
        Bucket bucket;
        switch (op) {
            case EQUALS:
                index = getIndex(v);
                if(index<0||index>=numBuckets){
                    return 0;
                }else{
                    bucket = buckets.get(index);
                    return (1.0*bucket.getCount() / bucket.getWidth()) / ntup;
                }
            case GREATER_THAN:
                index = getIndex(v);
                if (index < 0) {
                    return 1;
                } else if (index >= numBuckets) {
                    return 0;
                } else {
                    bucket = buckets.get(index);
                    sum = 1.0*bucket.getCount() * (bucket.getRight() - v) / bucket.getWidth();
                    for (int i = index+1; i < numBuckets; i++) {
                        sum += buckets.get(i).getCount();
                    }
                    return sum / ntup;
                }
            case LESS_THAN:
                index = getIndex(v);
                if (index < 0) {
                    return 0;
                } else if (index >= numBuckets) {
                    return 1;
                } else {
                    bucket = buckets.get(index);
                    sum = 1.0*bucket.getCount() * (v - bucket.getLeft()) / bucket.getWidth();
                    for (int i = index - 1; i >= 0; i--) {
                        sum += buckets.get(i).getCount();
                    }
                    return sum / ntup;
                }
            case GREATER_THAN_OR_EQ:
                return estimateSelectivity(Predicate.Op.GREATER_THAN,v-1);
            case NOT_EQUALS:
                return 1-estimateSelectivity(Predicate.Op.EQUALS,v);
            case LESS_THAN_OR_EQ:
                return estimateSelectivity(Predicate.Op.LESS_THAN,v+1);
            default:
                throw new UnsupportedOperationException();
        }
    }

    /**
     * @return the average selectivity of this histogram.
     * <p>
     * This is not an indispensable method to implement the basic
     * join optimization. It may be needed if you want to
     * implement a more efficient optimization
     */
    public double avgSelectivity() {
        int cnt = 0;
        for (Bucket bucket : buckets) {
            cnt += bucket.getCount();
        }
        return cnt * 1.0 / numBuckets;
    }

    /**
     * @return A string describing this histogram, for debugging purposes
     */
    public String toString() {
        StringBuilder res = new StringBuilder("|| ");
        for (Bucket bucket : buckets) {
            res.append(bucket);
            res.append(" || ");
        }
        return res.toString();
    }

    private class Bucket {
        private int left;
        private int right;
        private int count;

        public Bucket(int left, int right) {
            this.left = left;
            this.right = right;
        }

        public int getLeft() {
            return left;
        }

        public void setLeft(int left) {
            this.left = left;
        }

        public int getRight() {
            return right;
        }

        public void setRight(int right) {
            this.right = right;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        public void addCount() {
            this.count++;
        }

        public int getWidth(){
            return right-left+1;
        }
        @Override
        public String toString() {
            return "<" + left + "," + right + ">:" + count;
        }
    }
}
