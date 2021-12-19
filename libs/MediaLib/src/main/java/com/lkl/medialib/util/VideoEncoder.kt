package com.lkl.medialib.util

import com.lkl.commonlib.util.LogUtils.d
import android.media.MediaCodec
import com.lkl.medialib.constant.VideoConfig
import android.media.MediaMuxer
import kotlin.Throws
import android.media.MediaFormat
import android.media.MediaCodecInfo
import com.lkl.framedatacachejni.FrameDataCacheUtils
import android.os.Build
import android.util.Log
import com.lkl.commonlib.util.LogUtils
import com.lkl.medialib.util.VideoEncoderCore
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.lang.Exception
import java.lang.RuntimeException
import java.nio.ByteBuffer

/**
 * Description:
 */
class VideoEncoder : Runnable {
    companion object {
        private const val TAG = "VideoEncoder"
    }

    private var mEncoder: MediaCodec? = null
    private var mime = VideoConfig.MIME //HEVC -> H265 AVC -> H264
    private var rate = VideoConfig.BIT_RATE
    private var frameRate = VideoConfig.FPS
    private var frameInterval = VideoConfig.FRAME_INTERVAL
    private val fpsTime: Int = 1000 / frameRate
    private var mThread: Thread? = null
    private var mStartFlag = false
    private var width = 0
    private var height = 0
    private var mHeadInfo: ByteArray? = null
    private var nowFeedData: ByteArray? = null
    private var nowTimeStep: Long = -1
    private val lastTimeStep: Long = 0
    private var hasNewData = false
    private var fos: FileOutputStream? = null
    private var mSavePath: String? = null
    var mMuxer: MediaMuxer? = null
    private val mTrackIndex = 0
    private val mMuxerStarted = false
    var mBufferInfo = MediaCodec.BufferInfo()

    fun setMime(mime: String) {
        this.mime = mime
    }

    fun setRate(rate: Int) {
        this.rate = rate
    }

    fun setFrameRate(frameRate: Int) {
        this.frameRate = frameRate
    }

    fun setFrameInterval(frameInterval: Int) {
        this.frameInterval = frameInterval
    }

    fun setSavePath(path: String?) {
        mSavePath = path
    }

    /**
     * 准备录制
     *
     * @param width  视频宽度
     * @param height 视频高度
     * @throws IOException
     */
    @Throws(IOException::class)
    fun prepare(width: Int, height: Int) {
        mHeadInfo = null
        this.width = width
        this.height = height
        val file = File(mSavePath)
        val folder = file.parentFile
        if (!folder.exists()) {
            val b = folder.mkdirs()
            Log.e("wuwang", "create " + folder.absolutePath + " " + b)
        }
        if (file.exists()) {
            val b = file.delete()
        }
        fos = FileOutputStream(mSavePath)
        val format = MediaFormat.createVideoFormat(mime, width, height)
        format.setInteger(MediaFormat.KEY_BIT_RATE, rate)
        format.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, frameInterval)
        format.setInteger(
            MediaFormat.KEY_COLOR_FORMAT,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar
        )
        mEncoder = MediaCodec.createEncoderByType(mime)
        mEncoder!!.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

//        MediaFormat mediaFormat = mEncoder.getOutputFormat();
        mMuxer = MediaMuxer("$mSavePath.mp4", MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        //        mTrackIndex = mMuxer.addTrack(mediaFormat);
        FrameDataCacheUtils.initCache(frameRate, width, height, 0)
    }

    /**
     * 开始录制
     *
     * @throws InterruptedException
     */
    @Throws(InterruptedException::class)
    fun start() {
        if (mThread != null && mThread!!.isAlive) {
            mStartFlag = false
            mThread!!.join()
        }
        mEncoder!!.start()
        //        mMuxer.start();
        mStartFlag = true
        mThread = Thread(this)
        mThread!!.start()
    }

    /**
     * 停止录制
     */
    fun stop() {
        try {
            mStartFlag = false
            mThread!!.join()
            mEncoder!!.flush()
            mEncoder!!.stop()
            mEncoder!!.release()
            mMuxer!!.stop()
            mMuxer!!.release()
            fos!!.flush()
            fos!!.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 由外部喂入一帧数据
     *
     * @param data     RGBA数据
     * @param timeStep camera附带时间戳
     */
    fun feedData(data: ByteArray?, timeStep: Long) {
        hasNewData = true
        nowFeedData = data
        if (nowTimeStep == -1L) {
            nowTimeStep = timeStep
        }
        //        nowTimeStep = timeStep;
    }

    private fun getInputBuffer(index: Int): ByteBuffer? {
        return mEncoder?.getInputBuffer(index)
    }

    private fun getOutputBuffer(index: Int): ByteBuffer? {
        return mEncoder?.getOutputBuffer(index)
    }

    //TODO 定时调用，如果没有新数据，就用上一个数据
    @Throws(IOException::class)
    private fun readOutputData(data: ByteArray, timeStep: Long) {
        val index = mEncoder!!.dequeueInputBuffer(-1)
        if (index >= 0) {
//            if (hasNewData) {
//                if (yuv == null) {
//                    yuv = new byte[width * height * 3 / 2];
//                }
//                YuvUtils.nv21ConvertToI420(data, width, height, yuv);
//            }
            val buffer = getInputBuffer(index)
            //            Log.d(TAG, "data length:" + data.length + " ByteBuffer:" + buffer.capacity() + " yuv data length:" + yuv.length);
            buffer?.clear()
            buffer?.put(data)
            mEncoder!!.queueInputBuffer(index, 0, data.size, timeStep * 1000, 0)
        }
        drainEncoder(false)
    }

    lateinit var yuv: ByteArray

    fun drainEncoder(endOfStream: Boolean) {
        val TIMEOUT_USEC = 10000L
        d(TAG, "drainEncoder($endOfStream)")
        if (endOfStream) {
            d(TAG, "sending EOS to encoder")
            //            mEncoder.signalEndOfInputStream();
            return
        }
        var encoderOutputBuffers = mEncoder!!.outputBuffers
        while (true) {
            val encoderStatus = mEncoder!!.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC)
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // no output available yet
                if (!endOfStream) {
                    break // out of while
                } else {
                    d(TAG, "no output available, spinning to await EOS")
                }
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                // not expected for an encoder
                encoderOutputBuffers = mEncoder!!.outputBuffers
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // should happen before receiving buffers, and should only happen once
                if (mMuxerStarted) {
                    throw RuntimeException("format changed twice")
                }
                //                MediaFormat newFormat = mEncoder.getOutputFormat();
                VideoEncoderCore.mMediaFormat = mEncoder!!.outputFormat
                Log.d(TAG, "encoder output format changed: " + VideoEncoderCore.mMediaFormat)

                // now that we have the Magic Goodies, start the muxer
//                mTrackIndex = mMuxer.addTrack(VideoEncoderCore.mMediaFormat);
//                mMuxer.start();
//                mMuxerStarted = true;
            } else if (encoderStatus < 0) {
                Log.w(
                    TAG, "unexpected result from encoder.dequeueOutputBuffer: " +
                            encoderStatus
                )
                // let's ignore it
            } else {
                val encodedData = encoderOutputBuffers[encoderStatus]
                    ?: throw RuntimeException(
                        "encoderOutputBuffer " + encoderStatus +
                                " was null"
                    )
                if (mBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                    // The codec config data was pulled out and fed to the muxer when we got
                    // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
                    d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG")
                    mBufferInfo.size = 0
                }
                if (mBufferInfo.size != 0) {
//                    if (!mMuxerStarted) {
//                        throw new RuntimeException("muxer hasn't started");
//                    }

                    // adjust the ByteBuffer values to match BufferInfo (not needed?)
                    encodedData.position(mBufferInfo.offset)
                    encodedData.limit(mBufferInfo.offset + mBufferInfo.size)
                    val data = ByteArray(mBufferInfo.size)
                    encodedData[data]
                    FrameDataCacheUtils.addFrameData(
                        mBufferInfo.presentationTimeUs / 1000,
                        mBufferInfo.flags == MediaCodec.BUFFER_FLAG_KEY_FRAME,
                        data, mBufferInfo.size
                    )
                    //                    mMuxer.writeSampleData(mTrackIndex, encodedData, mBufferInfo);
                    d(
                        TAG, "sent " + mBufferInfo.size + " bytes to muxer, ts=" +
                                mBufferInfo.presentationTimeUs + "  flags: " + mBufferInfo.flags
                    )
                }
                mEncoder!!.releaseOutputBuffer(encoderStatus, false)
                if (mBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    if (!endOfStream) {
                        d(TAG, "reached end of stream unexpectedly")
                    } else {
                        d(TAG, "end of stream reached")
                    }
                    break // out of while
                }
            }
        }
    }

    override fun run() {
        while (mStartFlag) {
            val time = System.currentTimeMillis()
            d(TAG, "encode start")
            if (nowFeedData != null) {
                try {
//                    if (nowTimeStep - lastTimeStep < fpsTime) {
                    nowTimeStep += 40
                    //                    }
                    readOutputData(nowFeedData!!, nowTimeStep)
                    //                    lastTimeStep = nowTimeStep;
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
            val lt = System.currentTimeMillis() - time
            d(TAG, "encode stop")
            if (fpsTime > lt) {
                try {
                    Thread.sleep(fpsTime - lt)
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }
        }
    }
}