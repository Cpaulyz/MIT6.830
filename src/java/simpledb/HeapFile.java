package simpledb;

import java.io.*;
import java.util.*;

/**
 * HeapFile is an implementation of a DbFile that stores a collection of tuples
 * in no particular order. Tuples are stored on pages, each of which is a fixed
 * size, and the file is simply a collection of those pages. HeapFile works
 * closely with HeapPage. The format of HeapPages is described in the HeapPage
 * constructor.
 * 
 * @see simpledb.HeapPage#HeapPage
 * @author Sam Madden
 */
public class HeapFile implements DbFile {
    /**
     * the file that stores the on-disk backing store for this heap file.
     */
    private File file;

    private TupleDesc tupleDesc;

    /**
     * Constructs a heap file backed by the specified file.
     * 
     * @param f
     *            the file that stores the on-disk backing store for this heap
     *            file.
     */
    public HeapFile(File f, TupleDesc td) {
        this.file = f;
        this.tupleDesc = td;
    }

    /**
     * Returns the File backing this HeapFile on disk.
     * 
     * @return the File backing this HeapFile on disk.
     */
    public File getFile() {
        return file;
    }

    /**
     * Returns an ID uniquely identifying this HeapFile. Implementation note:
     * you will need to generate this tableid somewhere to ensure that each
     * HeapFile has a "unique id," and that you always return the same value for
     * a particular HeapFile. We suggest hashing the absolute file name of the
     * file underlying the heapfile, i.e. f.getAbsoluteFile().hashCode().
     * 
     * @return an ID uniquely identifying this HeapFile.
     * used as tableid
     */
    public int getId() {
        return file.getAbsolutePath().hashCode();
    }

    /**
     * Returns the TupleDesc of the table stored in this DbFile.
     * 
     * @return TupleDesc of this DbFile.
     */
    public TupleDesc getTupleDesc() {
        return tupleDesc;
    }

    /**
     * see DbFile.java for javadocs
     * @throws IllegalArgumentException if the page does not exist in this file.
     */
    public Page readPage(PageId pid) {
        int tableId = pid.getTableId();
        int pgNo = pid.getPageNumber();
        RandomAccessFile f = null;
        try {
            f = new RandomAccessFile(file,"r");
            if((pgNo+1)*BufferPool.getPageSize()>f.length()){
                throw new IllegalArgumentException(String.format("table %d page %d is invalid", tableId, pgNo));
            }
            byte[] bytes = new byte[BufferPool.getPageSize()];
            f.seek(pgNo*BufferPool.getPageSize());
            int read = f.read(bytes,0,BufferPool.getPageSize());
            if(read!=BufferPool.getPageSize()){
                throw new IllegalArgumentException(String.format("table %d page %d is invalid", tableId, pgNo));
            }
            return new HeapPage((HeapPageId) pid,bytes);
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            try {
                f.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        throw new IllegalArgumentException(String.format("table %d page %d is invalid", tableId, pgNo));
    }

    // see DbFile.java for javadocs
    public void writePage(Page page) throws IOException {
        RandomAccessFile f = new RandomAccessFile(file,"rw");
        int pgNo = page.getId().getPageNumber();
        f.seek(pgNo*BufferPool.getPageSize());
        f.write(page.getPageData());
        f.close();
    }

    /**
     * Returns the number of pages in this HeapFile.
     */
    public int numPages() {
        return (int) Math.ceil(file.length()*1.0/BufferPool.getPageSize());
    }

    // see DbFile.java for javadocs
    public ArrayList<Page> insertTuple(TransactionId tid, Tuple t)
            throws DbException, IOException, TransactionAbortedException {
        //TODO: add and release lock?
        ArrayList<Page> modifyPages = new ArrayList<>();
        for (int i = 0; i < numPages(); i++) {
            HeapPage page = (HeapPage) Database.getBufferPool().getPage(tid,new HeapPageId(getId(),i),Permissions.READ_WRITE);
            if(page.getNumEmptySlots()>0){
                page.insertTuple(t);
                modifyPages.add(page);
                break;
            }
        }
        if(modifyPages.isEmpty()){
            int newPgNo = numPages();
            RandomAccessFile f = new RandomAccessFile(file,"rw");
            f.seek(f.length());
            byte[] data = HeapPage.createEmptyPageData();
            f.write(data);
            f.close();
            HeapPage page = (HeapPage) Database.getBufferPool().getPage(tid,new HeapPageId(getId(),newPgNo),Permissions.READ_WRITE);
            page.insertTuple(t);
            modifyPages.add(page);
        }
        return modifyPages;
    }

    // see DbFile.java for javadocs
    public ArrayList<Page> deleteTuple(TransactionId tid, Tuple t) throws DbException,
            TransactionAbortedException {
        ArrayList<Page> modifyPages = new ArrayList<Page>();
        HeapPage page = (HeapPage) Database.getBufferPool().getPage(tid, t.getRecordId().getPageId(),Permissions.READ_WRITE);
        page.deleteTuple(t);
        modifyPages.add(page);
        return modifyPages;
    }

    // see DbFile.java for javadocs
    public DbFileIterator iterator(TransactionId tid) {
        return new HeapFileIterator(this,tid);
    }

    private class HeapFileIterator implements DbFileIterator{
        private HeapFile heapFile;
        private TransactionId transactionId;
        private Iterator<Tuple> iterator;
        private int currPage;

        public HeapFileIterator(HeapFile heapFile,TransactionId tid){
            this.heapFile = heapFile;
            this.transactionId = tid;
        }

        private Iterator<Tuple> getIterator(int pageNumber) throws TransactionAbortedException, DbException {
            if(pageNumber>=0&&pageNumber<heapFile.numPages()){
                HeapPageId pageId = new HeapPageId(heapFile.getId(),pageNumber);
                HeapPage page = (HeapPage) Database.getBufferPool().getPage(transactionId,pageId,Permissions.READ_ONLY);
                return page.iterator();
            }else{
                throw new DbException(String.format("problems opening/accessing the database pageNo %d ", pageNumber));
            }
        }

        @Override
        public void open() throws DbException, TransactionAbortedException {
            currPage = 0;
            iterator = getIterator(currPage);
        }

        @Override
        public boolean hasNext() throws DbException, TransactionAbortedException {
            if(iterator==null){
                return false;
            }else if(iterator.hasNext()){
                return true;
            }else{
                // get next iterator
                currPage++;
                if(currPage>=heapFile.numPages()){
                    return false;
                }else{
                    iterator = getIterator(currPage);
                    return hasNext();
                }
            }
        }

        @Override
        public Tuple next() throws DbException, TransactionAbortedException, NoSuchElementException {
            if(iterator == null || !iterator.hasNext()){
                throw new NoSuchElementException();
            }
            return iterator.next();
        }

        @Override
        public void rewind() throws DbException, TransactionAbortedException {
            close();
            open();
        }

        @Override
        public void close() {
            iterator = null;
        }
    }
}

