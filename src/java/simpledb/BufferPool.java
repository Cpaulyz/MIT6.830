package simpledb;

import java.io.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BufferPool manages the reading and writing of pages into memory from
 * disk. Access methods call into it to retrieve pages, and it fetches
 * pages from the appropriate location.
 * <p>
 * The BufferPool is also responsible for locking;  when a transaction fetches
 * a page, BufferPool checks that the transaction has the appropriate
 * locks to read/write the page.
 * 
 * @Threadsafe, all fields are final
 */
public class BufferPool {
    /** Bytes per page, including header. */
    private static final int DEFAULT_PAGE_SIZE = 4096;

    private static int pageSize = DEFAULT_PAGE_SIZE;
    
    /** Default number of pages passed to the constructor. This is used by
    other classes. BufferPool should use the numPages argument to the
    constructor instead. */
    public static final int DEFAULT_PAGES = 50;


    private int numPages;
    /**
     * pages in buffer pool
     */
    private ConcurrentHashMap<Integer,Page> pages;

    private LockManager lockManager;

    /**
     * Creates a BufferPool that caches up to numPages pages.
     *
     * @param numPages maximum number of pages in this buffer pool.
     */
    public BufferPool(int numPages) {
        this.numPages = numPages;
        pages = new ConcurrentHashMap<>();
        lockManager = new LockManager();
    }
    
    public static int getPageSize() {
      return pageSize;
    }
    
    // THIS FUNCTION SHOULD ONLY BE USED FOR TESTING!!
    public static void setPageSize(int pageSize) {
    	BufferPool.pageSize = pageSize;
    }
    
    // THIS FUNCTION SHOULD ONLY BE USED FOR TESTING!!
    public static void resetPageSize() {
    	BufferPool.pageSize = DEFAULT_PAGE_SIZE;
    }

    private Integer getKey(PageId pageId){
        return pageId.hashCode(); // 使用hashcode来作为key
    }

    /**
     * Retrieve the specified page with the associated permissions.
     * Will acquire a lock and may block if that lock is held by another
     * transaction.
     * <p>
     * The retrieved page should be looked up in the buffer pool.  If it
     * is present, it should be returned.  If it is not present, it should
     * be added to the buffer pool and returned.  If there is insufficient
     * space in the buffer pool, a page should be evicted and the new page
     * should be added in its place.
     *
     * @param tid the ID of the transaction requesting the page
     * @param pid the ID of the requested page
     * @param perm the requested permissions on the page
     */
    public  Page getPage(TransactionId tid, PageId pid, Permissions perm)
        throws TransactionAbortedException, DbException {
        Object lock = lockManager.getTxLock(pid);
        synchronized (lock){
            LockType lockType;
            if(perm == Permissions.READ_ONLY){
                lockType = LockType.SHARED_LOCK;
            }else{
                lockType = LockType.EXCLUSIVE_LOCK;
            }
            boolean lockStatus = lockManager.acquireLock(pid,new Lock(tid,lockType));
            if(!lockStatus){
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            Integer key = getKey(pid);
            if(!pages.containsKey(key)){
                DbFile dbFile = Database.getCatalog().getDatabaseFile(pid.getTableId());
                while (pages.size()>=numPages){
                    evictPage();
                }
                pages.put(key,dbFile.readPage(pid));
            }
            return pages.get(key);
        }
    }

    /**
     * Releases the lock on a page.
     * Calling this is very risky, and may result in wrong behavior. Think hard
     * about who needs to call this and why, and why they can run the risk of
     * calling it.
     *
     * @param tid the ID of the transaction requesting the unlock
     * @param pid the ID of the page to unlock
     */
    public  void releasePage(TransactionId tid, PageId pid) {
        lockManager.releaseLock(pid,tid);
    }

    /**
     * Release all locks associated with a given transaction.
     *
     * @param tid the ID of the transaction requesting the unlock
     */
    public void transactionComplete(TransactionId tid) throws IOException {
        // some code goes here
        // not necessary for lab1|lab2
        transactionComplete(tid,true);
    }

    /** Return true if the specified transaction has a lock on the specified page */
    public boolean holdsLock(TransactionId tid, PageId p) {
        return lockManager.holdsLock(p,tid);
    }

    /**
     * Commit or abort a given transaction; release all locks associated to
     * the transaction.
     *
     * @param tid the ID of the transaction requesting the unlock
     * @param commit a flag indicating whether we should commit or abort
     */
    public void transactionComplete(TransactionId tid, boolean commit)
        throws IOException {
        // some code goes here
        // not necessary for lab1|lab2
        if(commit){
            flushPages(tid);
        }else{
            // abort page
        }
        lockManager.releaseLock(tid);
    }

    /**
     * Add a tuple to the specified table on behalf of transaction tid.  Will
     * acquire a write lock on the page the tuple is added to and any other 
     * pages that are updated (Lock acquisition is not needed for lab2). 
     * May block if the lock(s) cannot be acquired.
     * 
     * 1. Marks any pages that were dirtied by the operation as dirty by calling
     * their markDirty bit,
     * 2. and adds versions of any pages that have
     * been dirtied to the cache (replacing any existing versions of those pages) so 
     * that future requests see up-to-date pages. 
     *
     * @param tid the transaction adding the tuple
     * @param tableId the table to add the tuple to
     * @param t the tuple to add
     */
    public void insertTuple(TransactionId tid, int tableId, Tuple t)
        throws DbException, IOException, TransactionAbortedException {
        ArrayList<Page> pages =  Database.getCatalog().getDatabaseFile(tableId).insertTuple(tid,t);
//        for(Page page:pages){
//            page.markDirty(true,tid);
//        }
        updateBufferPool(tid,pages);
    }

    /**
     * Remove the specified tuple from the buffer pool.
     * Will acquire a write lock on the page the tuple is removed from and any
     * other pages that are updated. May block if the lock(s) cannot be acquired.
     *
     * Marks any pages that were dirtied by the operation as dirty by calling
     * their markDirty bit, and adds versions of any pages that have 
     * been dirtied to the cache (replacing any existing versions of those pages) so 
     * that future requests see up-to-date pages. 
     *
     * @param tid the transaction deleting the tuple.
     * @param t the tuple to delete
     */
    public void deleteTuple(TransactionId tid, Tuple t)
        throws DbException, IOException, TransactionAbortedException {
        ArrayList<Page> pages =  Database.getCatalog().getDatabaseFile(t.getRecordId().getPageId().getTableId()).deleteTuple(tid,t);
//        for(Page page:pages){
//            page.markDirty(true,tid);
//        }
        updateBufferPool(tid,pages);
    }

    /**
     * 1. make dirty
     * 2. put in buffer pool
     * @param tid
     * @param pages
     */
    private void updateBufferPool(TransactionId tid,List<Page> pages){
        for (Page page:pages){
            // make dirty
            page.markDirty(true,tid);
            int key = getKey(page.getId());
            if(!this.pages.containsKey(key)){
                while (this.pages.size()>=numPages){
                    try {
                        evictPage();
                    } catch (DbException e) {
                        e.printStackTrace();
                    }
                }
            }
            this.pages.put(key,page);
        }
    }

    /**
     * Flush all dirty pages to disk.
     * NB: Be careful using this routine -- it writes dirty data to disk so will
     *     break simpledb if running in NO STEAL mode.
     */
    public synchronized void flushAllPages() throws IOException {
        for(Page page:pages.values()){
            flushPage(page.getId());
        }
    }

    /** Remove the specific page id from the buffer pool.
        Needed by the recovery manager to ensure that the
        buffer pool doesn't keep a rolled back page in its
        cache.
        
        Also used by B+ tree files to ensure that deleted pages
        are removed from the cache so they can be reused safely
    */
    public synchronized void discardPage(PageId pid) {
        int key = getKey(pid);
        pages.remove(key);
    }

    /**
     * Flushes a certain page to disk
     * Write any dirty page to disk and mark it as not dirty, while leaving it in the BufferPool.
     * @param pid an ID indicating the page to flush
     */
    private synchronized  void flushPage(PageId pid) throws IOException {
        Page page = pages.get(getKey(pid));
        if(page.isDirty()!=null){
            DbFile dbFile = Database.getCatalog().getDatabaseFile(page.getId().getTableId());
            dbFile.writePage(page);
            page.markDirty(false,null);
        }
    }

    /** Write all pages of the specified transaction to disk.
     */
    public synchronized  void flushPages(TransactionId tid) throws IOException {
        // some code goes here
        // not necessary for lab1|lab2
    }

    /**
     * Discards a page from the buffer pool.
     * Flushes the page to disk to ensure dirty pages are updated on disk.
     */
    private synchronized  void evictPage() throws DbException {
        // 采用最简单的随机淘汰策略
        List<Integer> keys = new ArrayList<>(pages.keySet());
        int randomKey = keys.get(new Random().nextInt(keys.size()));
        PageId evictPid = pages.get(randomKey).getId();
        try {
            flushPage(evictPid);
        } catch (IOException e) {
            e.printStackTrace();
        }
        discardPage(evictPid);
    }


    enum LockType{
        SHARED_LOCK, EXCLUSIVE_LOCK;
    }
    private class Lock{
        TransactionId tid;
        LockType lockType;   // 0 for shared lock and 1 for exclusive lock

        public Lock(TransactionId tid,LockType lockType){
            this.tid = tid;
            this.lockType = lockType;
        }
    }
    private class LockManager {
        ConcurrentHashMap<PageId, Vector<Lock>> pageLocks = new ConcurrentHashMap<>();
        ConcurrentHashMap<PageId, Object> txLocks = new ConcurrentHashMap<>();
        /**
         * 获取锁
         * 场景：已持有锁、尝试获取读锁、尝试获取写锁、读锁升级写锁
         * 注意：别的事务持有锁，导致当前事务无法获取锁之后需要进行等待和通知
         * @return 是否获取锁成功
         */
        public synchronized boolean acquireLock(PageId pageId,Lock lock){
            if(pageLocks.get(pageId)==null){ // 没人有锁，成功
                Vector<Lock> locks = new Vector<>();
                locks.add(lock);
                pageLocks.put(pageId,locks);
                return true;
            }
            Vector<Lock> locks = pageLocks.get(pageId);
            for(Lock l:locks){
                if(l.tid.equals(lock.tid)){
                    if(l.lockType==lock.lockType){
                        return true;  // 已持有锁
                    }else{
                        if(l.lockType==LockType.EXCLUSIVE_LOCK){
                            return true; // 已有排它锁，可以有共享锁
                        }else if(l.lockType==LockType.SHARED_LOCK){
                            if(locks.size()==1){
                                // 读锁升级写锁
                                l.lockType = LockType.EXCLUSIVE_LOCK;
                                return true;
                            }else {
                                return false;
                            }
                        }

                    }
                }
            }
            // 如果没有过，并且其他都是共享锁，可以加锁
            if(lock.lockType==LockType.SHARED_LOCK&&locks.get(0).lockType==LockType.SHARED_LOCK){
                locks.add(lock);
                return true;
            }
            return false;
        }

        /**
         * 释放锁
         * @return true: 成功 false: 没锁
         */
        public synchronized boolean releaseLock(TransactionId tid){
            for(PageId pageId:pageLocks.keySet()){
                if(holdsLock(pageId,tid)){
                    boolean res = releaseLock(pageId,tid);
                    if(!res){
                        return false;
                    }
                    // TODO：先全部唤醒，可优化
                    Object object = getTxLock(pageId);
                    object.notifyAll();
                }
            }
            return true;
        }

        /**
         * 释放锁
         * @return true: 成功 false: 没锁
         */
        public synchronized boolean releaseLock(PageId pageId,TransactionId tid){
            assert pageLocks.get(pageId) != null : "page not locked!";
            Vector<Lock> locks = pageLocks.get(pageId);
            for(Lock l:locks){
                if(l.tid.equals(tid)){
                    locks.remove(l);
                    if(locks.size()==0){
                        pageLocks.remove(pageId);
                    }
                    return true;
                }
            }
            return false;
        }

        /**
         * 判断tix是否持有锁
         */
        public synchronized boolean holdsLock(PageId pageId,TransactionId tid){
            Vector<Lock> locks = pageLocks.get(pageId);
            if(locks==null){
                return false;
            }
            for(Lock l:locks){
                if(l.tid.equals(tid)){
                    return true;
                }
            }
            return false;
        }

        public Object getTxLock(PageId pageId){
            txLocks.computeIfAbsent(pageId, k -> new Object());
            return txLocks.get(pageId);
        }
    }

}
