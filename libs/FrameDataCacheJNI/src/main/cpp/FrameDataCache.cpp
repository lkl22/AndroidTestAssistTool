#include "FrameDataCache.h"

#include <map>
#include <vector>
#include <algorithm>
#include <mutex>
#include <condition_variable>

typedef struct FrameIndex {
    FrameIndex(unsigned char *pBuf, int nLen, bool isKeyFrame, bool bRFlag = false)
            : _pBuf(pBuf), _nLen(nLen), _isKeyFrame(isKeyFrame), _isReadFlag(bRFlag) {
    }

    FrameIndex(const FrameIndex &item) {
        this->_pBuf = item._pBuf;
        this->_nLen = item._nLen;
        this->_isKeyFrame = item._isKeyFrame;
        this->_isReadFlag = item._isReadFlag;
    }

    /**
     * 数据存储在buffer中的地址
     */
    unsigned char *_pBuf;
    /**
     * 数据的长度
     */
    int _nLen;
    /**
     * 是否为关键帧
     */
    bool _isKeyFrame;
    /**
     * 是否正在读取
     */
    bool _isReadFlag;
} FrameIndex, *PFrameIndex;

typedef std::map <int64, std::shared_ptr<FrameIndex>> FRAME_INDEX_MAP;
// const_iterator可改自身但不可改所指的元素值
typedef FRAME_INDEX_MAP::const_iterator FRAME_INDEX_MAP_CONST_ITERATOR;
// iterator可改自身和所指的元素值
typedef FRAME_INDEX_MAP::iterator FRAME_INDEX_MAP_ITERATOR;

/**
 * 内存buffer指针
 */
static unsigned char *s_pMemBuf = nullptr;
static FRAME_INDEX_MAP sFrameIndexMap;

// 关键帧时间戳容器类型定义
typedef std::vector <int64> KEY_FRAME_TS_VECTOR;
// 不可改关键帧时间戳迭代器
typedef KEY_FRAME_TS_VECTOR::const_iterator KEY_FRAME_TS_VECTOR_CONST_ITERATOR;
// 可改关键帧时间戳迭代器
typedef KEY_FRAME_TS_VECTOR::iterator KEY_FRAME_TS_VECTOR_ITERATOR;
// 关键帧时间戳容器
static KEY_FRAME_TS_VECTOR sKeyFrameTSVec;

static unsigned char *s_pCurPos = nullptr;
static unsigned char *s_pFreePos = nullptr; //当前释放的位置

static bool printDebugLog = false;

// 默认分配大小
static long sMaxDataBuf = 1; // 30M

class WFirstRWLock {
public:
    WFirstRWLock() = default;

    ~WFirstRWLock() = default;

public:
    void lock_read() {
        std::unique_lock <std::mutex> ulk(counter_mutex);
        cond_r.wait(ulk, [=]() -> bool { return write_cnt == 0; });
        ++read_cnt;
    }

    void lock_write() {
        std::unique_lock <std::mutex> ulk(counter_mutex);
        ++write_cnt;
        cond_w.wait(ulk, [=]() -> bool { return read_cnt == 0 && !inWriteFlag; });
        inWriteFlag = true;
    }

    void release_read() {
        std::unique_lock <std::mutex> ulk(counter_mutex);
        if (--read_cnt == 0 && write_cnt > 0) {
            cond_w.notify_one();
        }
    }

    void release_write() {
        std::unique_lock <std::mutex> ulk(counter_mutex);
        if (--write_cnt == 0) {
            cond_r.notify_all();
        } else {
            cond_w.notify_one();
        }
        inWriteFlag = false;
    }

private:
    volatile size_t read_cnt{0};
    volatile size_t write_cnt{0};
    volatile bool inWriteFlag{false};
    std::mutex counter_mutex;
    std::condition_variable cond_w;
    std::condition_variable cond_r;
};

template<typename _RWLockable>
class unique_writeguard {
public:
    explicit unique_writeguard(_RWLockable &rw_lockable)
            : rw_lockable_(rw_lockable) {
        rw_lockable_.lock_write();
    }

    ~unique_writeguard() {
        rw_lockable_.release_write();
    }

private:
    unique_writeguard() = delete;

    unique_writeguard(const unique_writeguard &) = delete;

    unique_writeguard &operator=(const unique_writeguard &) = delete;

private:
    _RWLockable &rw_lockable_;
};

template<typename _RWLockable>
class unique_readguard {
public:
    explicit unique_readguard(_RWLockable &rw_lockable)
            : rw_lockable_(rw_lockable) {
        rw_lockable_.lock_read();
    }

    ~unique_readguard() {
        rw_lockable_.release_read();
    }

private:
    unique_readguard() = delete;

    unique_readguard(const unique_readguard &) = delete;

    unique_readguard &operator=(const unique_readguard &) = delete;

private:
    _RWLockable &rw_lockable_;
};

static WFirstRWLock s_Lock;

void init(int cacheSize, bool isDebug) {
    int finalSize = 30;
    if (cacheSize > 0 && cacheSize < 100) {
        // 限制缓存空间大小，不能超过100M
        finalSize = cacheSize;
    }
    sMaxDataBuf = finalSize * 1024 * 1024;
    if (s_pMemBuf == nullptr) {
        s_pMemBuf = new unsigned char[sMaxDataBuf];
    }
    printDebugLog = isDebug;
    s_pCurPos = s_pMemBuf;
    s_pFreePos = s_pMemBuf + sMaxDataBuf - 1;
    sFrameIndexMap.clear();
    sKeyFrameTSVec.clear();
    //s_mapBufTm.clear();
    LOGI("data cache size: %dM", cacheSize);
}

void UnInit() {
    sKeyFrameTSVec.clear();
    sFrameIndexMap.clear();

    s_pCurPos = nullptr;

    if (s_pMemBuf) {
        delete[] s_pMemBuf;
        s_pMemBuf = nullptr;
    }
}

void addFrame(int64 timestamp, bool isKeyFrame, unsigned char *puf, int nLen) {
    if (printDebugLog) {
        LOGI("data cache add frame start: timestamp -> %lld isKeyFrame -> %d  length -> %d", timestamp,
             isKeyFrame, nLen);
    }
    if (sFrameIndexMap.size() > 0 && sKeyFrameTSVec.size() > 0
        && sFrameIndexMap.begin()->first > *sKeyFrameTSVec.begin()) {
        LOGI("*****************************begin sFrameIndexMap.begin()->first > *sKeyFrameTSVec.begin()");
    }
    unique_writeguard<WFirstRWLock> writeLock(s_Lock);
    if ((s_pCurPos + nLen) > (s_pMemBuf + sMaxDataBuf)) {
        LOGI("buffer have full, start erase data ");
        //当前要存储的空间不足,从头开始
        FRAME_INDEX_MAP_ITERATOR iterFrame = sFrameIndexMap.begin(), _endFrame = sFrameIndexMap.end();
        for (; iterFrame != _endFrame;) {
            if (iterFrame->second->_pBuf >= s_pCurPos &&
                iterFrame->second->_pBuf < (s_pCurPos + nLen)) {
                if (iterFrame->second->_isKeyFrame) {
                    sKeyFrameTSVec.erase(std::remove(
                            sKeyFrameTSVec.begin(), sKeyFrameTSVec.end(), iterFrame->first),
                                         sKeyFrameTSVec.end());
                }
                iterFrame = sFrameIndexMap.erase(iterFrame);
            } else {
                break;
            }
        }
        s_pCurPos = s_pMemBuf;
        s_pFreePos = s_pCurPos;
    }
    if ((s_pCurPos + nLen) > s_pFreePos) {
        unsigned char *nFreePos = s_pFreePos;
        FRAME_INDEX_MAP_ITERATOR iterFrame = sFrameIndexMap.begin(), _endFrame = sFrameIndexMap.end();
        for (; iterFrame != _endFrame;) {
            if (iterFrame->second->_pBuf >= s_pCurPos &&
                iterFrame->second->_pBuf < (s_pCurPos + nLen)) {
                nFreePos += iterFrame->second->_nLen;
                if (iterFrame->second->_isKeyFrame) {
                    sKeyFrameTSVec.erase(
                            std::remove(sKeyFrameTSVec.begin(), sKeyFrameTSVec.end(),
                                        iterFrame->first), sKeyFrameTSVec.end());
                }
                iterFrame = sFrameIndexMap.erase(iterFrame);
            } else {
                break;
            }
        }
        s_pFreePos = nFreePos;
    }
    std::shared_ptr <FrameIndex> item(new FrameIndex(s_pCurPos, nLen, isKeyFrame, false));
    if (isKeyFrame) {
        sKeyFrameTSVec.push_back(timestamp);
    }
    memcpy(s_pCurPos, puf, nLen);
    sFrameIndexMap.insert(std::make_pair(timestamp, item));
    s_pCurPos += nLen;
}

int getFirstFrame(int64 timestamp, int64 &curTimestamp, unsigned char *&data, int &nLen) {
    unique_readguard<WFirstRWLock> readLock(s_Lock);

    KEY_FRAME_TS_VECTOR_CONST_ITERATOR iterKeyFrame = std::lower_bound(sKeyFrameTSVec.begin(),
                                                                       sKeyFrameTSVec.end(),
                                                                       timestamp);
    if (iterKeyFrame != sKeyFrameTSVec.end()) {
        LOGI("get First frame iterKeyFrame 1:%lld", *iterKeyFrame);

        FRAME_INDEX_MAP_CONST_ITERATOR iterFrame = sFrameIndexMap.find(*iterKeyFrame);
        if (iterFrame != sFrameIndexMap.end()) {
            LOGI("get First frame iterKeyFrame 2:%lld", iterFrame->first);
            nLen = iterFrame->second->_nLen;
            data = iterFrame->second->_pBuf;
            curTimestamp = *iterKeyFrame;
            return 0;
        }
    } else {
        if (sKeyFrameTSVec.size() > 0 && timestamp <= *sKeyFrameTSVec.begin()) {
            FRAME_INDEX_MAP_CONST_ITERATOR iterFrame = sFrameIndexMap.find(
                    *sKeyFrameTSVec.begin());
            if (iterFrame != sFrameIndexMap.end()) {
                nLen = iterFrame->second->_nLen;
                data = iterFrame->second->_pBuf;
                curTimestamp = *iterKeyFrame;
                return 0;
            }
        }
    }
    nLen = 0;
    data = nullptr;
    LOGI("get First frame time :%lld minTime:%lld maxTime:%lld", timestamp,
         sKeyFrameTSVec.begin() != sKeyFrameTSVec.end() ? *sKeyFrameTSVec.begin() : 0,
         sKeyFrameTSVec.rbegin() != sKeyFrameTSVec.rend() ? *sKeyFrameTSVec.rbegin() : 0);
    FRAME_INDEX_MAP_ITERATOR iterFrame = sFrameIndexMap.begin(), _endFrame = sFrameIndexMap.end();
    for (; iterFrame != _endFrame; ++iterFrame) {
        LOGI("get First frame iterKeyFrame :%lld", iterFrame->first);
    }
    return 1;
}

int getNextFrame(int64 preTimestamp, int64 &curTimestamp, unsigned char *&data,
                 int &len, bool &isKeyFrame) {
    unique_readguard<WFirstRWLock> readLock(s_Lock);
    FRAME_INDEX_MAP_CONST_ITERATOR iterFrame = sFrameIndexMap.find(preTimestamp);
    if (iterFrame != sFrameIndexMap.end()) {
        ++iterFrame;
        if (iterFrame != sFrameIndexMap.end()) {
            len = iterFrame->second->_nLen;
            isKeyFrame = iterFrame->second->_isKeyFrame;
            curTimestamp = iterFrame->first;
            data = iterFrame->second->_pBuf;
            if (data + len > s_pMemBuf + sMaxDataBuf) {
                LOGE("data + nLen > s_pMemBuf + sMaxDataBuf ");
            }
            return 0;
        }
        len = 0;
        data = nullptr;
        return 2;
    }
    len = 0;
    data = nullptr;
    return 1;
}
