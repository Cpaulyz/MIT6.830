package simpledb;

import java.io.Serializable;
import java.util.*;

/**
 * TupleDesc describes the schema of a tuple.
 */
public class TupleDesc implements Serializable {

    List<TDItem> items;

    /**
     * A help class to facilitate organizing the information of each field
     * */
    public static class TDItem implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * The type of the field
         * */
        public final Type fieldType;
        
        /**
         * The name of the field
         * */
        public final String fieldName;

        public TDItem(Type t, String n) {
            this.fieldName = n;
            this.fieldType = t;
        }

        public String toString() {
            return fieldName + "(" + fieldType + ")";
        }
    }

    /**
     * @return
     *        An iterator which iterates over all the field TDItems
     *        that are included in this TupleDesc
     * */
    public Iterator<TDItem> iterator() {
        return items.iterator();
    }

    private static final long serialVersionUID = 1L;

    /**
     * Create a new TupleDesc with typeAr.length fields with fields of the
     * specified types, with associated named fields.
     * 
     * @param typeAr
     *            array specifying the number of and types of fields in this
     *            TupleDesc. It must contain at least one entry.
     * @param fieldAr
     *            array specifying the names of the fields. Note that names may
     *            be null.
     */
    public TupleDesc(Type[] typeAr, String[] fieldAr) {
        items = new ArrayList<>();
        for (int i = 0; i < typeAr.length; i++) {
            items.add(new TDItem(typeAr[i],fieldAr[i]));
        }
    }

    /**
     * Constructor. Create a new tuple desc with typeAr.length fields with
     * fields of the specified types, with anonymous (unnamed) fields.
     * 
     * @param typeAr
     *            array specifying the number of and types of fields in this
     *            TupleDesc. It must contain at least one entry.
     */
    public TupleDesc(Type[] typeAr) {
        items = new ArrayList<>();
        for (Type type : typeAr) {
            items.add(new TDItem(type, ""));
        }
    }

    /**
     * @return the number of fields in this TupleDesc
     */
    public int numFields() {
        return items.size();
    }

    /**
     * Gets the (possibly null) field name of the ith field of this TupleDesc.
     * 
     * @param i
     *            index of the field name to return. It must be a valid index.
     * @return the name of the ith field
     * @throws NoSuchElementException
     *             if i is not a valid field reference.
     */
    public String getFieldName(int i) throws NoSuchElementException {
        if(i>=numFields()||i<0){
            throw new NoSuchElementException();
        }else{
            return items.get(i).fieldName;
        }
    }

    /**
     * Gets the type of the ith field of this TupleDesc.
     * 
     * @param i
     *            The index of the field to get the type of. It must be a valid
     *            index.
     * @return the type of the ith field
     * @throws NoSuchElementException
     *             if i is not a valid field reference.
     */
    public Type getFieldType(int i) throws NoSuchElementException {
        if(i>=numFields()||i<0){
            throw new NoSuchElementException();
        }else{
            return items.get(i).fieldType;
        }
    }

    /**
     * Find the index of the field with a given name.
     * 
     * @param name
     *            name of the field.
     * @return the index of the field that is first to have the given name.
     * @throws NoSuchElementException
     *             if no field with a matching name is found.
     */
    public int fieldNameToIndex(String name) throws NoSuchElementException {
        String[] tmp = name.split("\\.");
        name = tmp[tmp.length-1];
        // 上面这两行是为了执行catalog.txt... 否则会报错？？
        for (int i = 0; i < items.size(); i++) {
            if(items.get(i).fieldName.equals(name)){
                return i;
            }
        }
        throw new NoSuchElementException();
    }

    /**
     * @return The size (in bytes) of tuples corresponding to this TupleDesc.
     *         Note that tuples from a given TupleDesc are of a fixed size.
     */
    public int getSize() {
        int sz = 0;
        for (TDItem item:items) {
            sz+=item.fieldType.getLen();
        }
        return sz;
    }

    /**
     * Merge two TupleDescs into one, with td1.numFields + td2.numFields fields,
     * with the first td1.numFields coming from td1 and the remaining from td2.
     * 
     * @param td1
     *            The TupleDesc with the first fields of the new TupleDesc
     * @param td2
     *            The TupleDesc with the last fields of the TupleDesc
     * @return the new TupleDesc
     */
    public static TupleDesc merge(TupleDesc td1, TupleDesc td2) {
        ArrayList<Type> types = new ArrayList<>();
        ArrayList<String> names = new ArrayList<>();
        Iterator<TDItem> iterator = td1.iterator();
        while (iterator.hasNext()){
            TDItem tmp = iterator.next();
            types.add(tmp.fieldType);
            names.add(tmp.fieldName);
        }
        iterator = td2.iterator();
        while (iterator.hasNext()){
            TDItem tmp = iterator.next();
            types.add(tmp.fieldType);
            names.add(tmp.fieldName);
        }
        return new TupleDesc(types.toArray(new Type[0]),names.toArray(new String[0]));
    }

    /**
     * Compares the specified object with this TupleDesc for equality. Two
     * TupleDescs are considered equal if they have the same number of items
     * and if the i-th type in this TupleDesc is equal to the i-th type in o
     * for every i.
     * 
     * @param o
     *            the Object to be compared for equality with this TupleDesc.
     * @return true if the object is equal to this TupleDesc.
     */

    public boolean equals(Object o) {
//        same number of items
//        i-th type in this TupleDesc is equal to the i-th type in o for every i.
        boolean res = false;
        if (o instanceof TupleDesc){
            TupleDesc td = (TupleDesc)o;
            if(td.numFields()==numFields()){
                res = true;
                for (int i = 0; i < numFields(); i++) {
                    if(!getFieldType(i).equals(td.getFieldType(i))){
                        res = false;
                        break;
                    }
                }
            }
        }
        return res;
    }

    public int hashCode() {
        // If you want to use TupleDesc as keys for HashMap, implement this so
        // that equal objects have equals hashCode() results
        throw new UnsupportedOperationException("unimplemented");
    }

    /**
     * Returns a String describing this descriptor. It should be of the form
     * "fieldType[0](fieldName[0]), ..., fieldType[M](fieldName[M])", although
     * the exact format does not matter.
     * 
     * @return String describing this descriptor.
     */
    public String toString() {
        if(items.size()==0){
            return "";
        }else{
            StringBuffer sb = new StringBuffer(items.get(0).toString());
            for (int i = 1; i < items.size(); i++) {
                sb.append(",");
                sb.append(items.get(i).toString());
            }
            return sb.toString();
        }
    }
}
