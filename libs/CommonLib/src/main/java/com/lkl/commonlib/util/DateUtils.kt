package com.lkl.commonlib.util

import android.text.TextUtils
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

/**
 * 日期操作工具类
 *
 * @author likunlun
 * @since 2018/10/17
 */
object DateUtils {

    // 一小时的ms数
    private const val HOUR = (3600 * 1000).toLong()
    // 一天的ms数
    private const val DAY = HOUR * 24

    const val DATE = "yyyy-MM-dd"
    const val DATE_TIME = "yyyy-MM-dd HH:mm:ss"
    const val DATE_TIME_MS = "yyyy-MM-dd HH:mm:ss.SSS"
    const val DATE_TIME_MS_FN = "yyyyMMdd_HHmmss.SSS"
    private const val DATE_YEAR = "yyyy"
    private const val DATE_MONTH = "MM"
    private const val DATE_DAY = "dd"
    private const val DATE_HOUR = "HH"
    private const val DATE_MINUTE = "mm"
    private const val DATE_SECOND = "ss"
    private const val DATE_MILLISECOND = "ms"
    private const val TIME = "HH-mm-ss"

    /**
     * 获取当前系统日期
     *
     * @return
     */
    val nowDate: String
        get() {
            val df = SimpleDateFormat(DATE)
            return df.format(Date(System.currentTimeMillis()))
        }

    /**
     * 获取当前系统时间
     *
     * @return
     */
    @JvmStatic
    val nowTime: String
        get() {
            val df = SimpleDateFormat(DATE_TIME)
            return df.format(Date(System.currentTimeMillis()))
        }

    /**
     * 获取当前系统时间 - HH-mm-ss
     *
     * @return
     */
    @JvmStatic
    val nowHourMinuteSecond: String
        get() {
            val df = SimpleDateFormat(TIME)
            return df.format(Date(System.currentTimeMillis()))
        }

    /**
     * 将指定格式的时间转化为另外一种格式的时间串
     *
     * @param oldFormat 需要转化的时间格式
     * @param newFormat 转化后的时间格式
     * @param time      指定格式的时间字符串
     * @return 标准时间格式的字符串
     */
    fun changeTime(oldFormat: String, newFormat: String, time: String?): String? {
        try {
            if (null == time && "" == time) {
                return time
            }
            val oldDateFormat = SimpleDateFormat(oldFormat)
            val newDateFormat = SimpleDateFormat(newFormat)
            val date = oldDateFormat.parse(time)
            return newDateFormat.format(date)
        } catch (e: ParseException) {
            LogUtils.e(e)
        }
        return time
    }

    /**
     * This method generates a string representation of a date/time in the
     * format you specify on input
     *
     * @param aMask   the date pattern the string is in
     * @param strDate a string representation of a date
     * @return a converted Date object
     * @throws ParseException when String doesn't match the expected format
     * @see SimpleDateFormat
     */
    fun convertStringToDate(aMask: String, strDate: String): Date? {
        if (TextUtils.isEmpty(strDate)) {
            return null
        }
        val df = SimpleDateFormat(aMask)
        var date: Date? = null
        try {
            date = df.parse(strDate)
        } catch (pe: ParseException) {
            LogUtils.e(pe)
        }

        return date
    }

    /**
     * 将日期转换为字符串
     *
     * @param aMask 格式字符串
     * @param date  日期
     * @return
     * @throws ParseException
     */
    @JvmStatic
    fun convertDateToString(aMask: String, date: Date): String {
        val sdf = SimpleDateFormat(aMask)
        return sdf.format(date)
    }

    /**
     * 得到两个日期间隔时间
     *
     * @param date1 日期1
     * @param date2 日期2
     * @param type  时间间隔：毫秒、DATE_MILLISECOND 秒、DATE_SECOND  分钟、DATE_MINUTE 小时、DATE_HOUR 天、DATE_DAY
     * @return 两个日期之间间隔的时间（ms/s/m/H/d）
     */
    fun betweenDate(date1: Date, date2: Date, type: String): Long {
        var day = Math.abs(date1.time - date2.time)
        if (type == DATE_SECOND) {
            day /= 1000.toLong()
        } else if (type == DATE_MINUTE) {
            day /= (60 * 1000).toLong()
        } else if (type == DATE_HOUR) {
            day /= (60 * 60 * 1000).toLong()
        } else if (type == DATE_DAY) {
            day /= (24 * 60 * 60 * 1000).toLong()
        }
        return day
    }

    /**
     * 得到两个日期间隔时间
     *
     * @param aMask 时间格式字符串
     * @param Date1 指定格式的日期字符串1
     * @param Date2 指定格式的日期字符串2
     * @param type  时间间隔：毫秒、DATE_MILLISECOND 秒、DATE_SECOND  分钟、DATE_MINUTE 小时、DATE_HOUR 天、DATE_DAY
     * @return 两个日期之间间隔的时间（ms/s/m/H/d）
     * @throws ParseException
     */
    fun betweenDate(aMask: String, Date1: String, Date2: String, type: String): Long {
        return betweenDate(
            convertStringToDate(aMask, Date1)!!,
                convertStringToDate(aMask, Date2)!!, type)
    }

    /**
     * 日期增加或者减少秒，分钟，天，月，年
     *
     * @param date   源日期
     * @param type   类型
     * @param offset （整数）
     * @return 增加或者减少之后的日期
     */
    fun addDate(date: Date?, type: String, offset: Int): Date {
        val gc = GregorianCalendar()
        gc.time = date
        if (type == DATE_MILLISECOND) {
            gc.add(GregorianCalendar.MILLISECOND, offset)
        } else if (type == DATE_SECOND) {
            gc.add(GregorianCalendar.SECOND, offset)
        } else if (type == DATE_MINUTE) {
            gc.add(GregorianCalendar.MINUTE, offset)
        } else if (type == DATE_HOUR) {
            gc.add(GregorianCalendar.HOUR, offset)
        } else if (type == DATE_DAY) {
            gc.add(GregorianCalendar.DATE, offset)
        } else if (type == DATE_MONTH) {
            gc.add(GregorianCalendar.MONTH, offset)
        } else if (type == DATE_YEAR) {
            gc.add(GregorianCalendar.YEAR, offset)
        }
        return gc.time
    }

    /**
     * 日期增加或者减少秒，分钟，天，月，年
     *
     * @param aMask   格式字符串
     * @param srcDate 源时间字符串
     * @param type    日期修改类型
     * @param offset  偏移量
     * @return 增加或者减少之后的日期字符串
     * @throws ParseException
     */
    fun addDate(aMask: String, srcDate: String, type: String,
                offset: Int): String? {
        return if (TextUtils.isEmpty(srcDate)) {
            null
        } else convertDateToString(aMask,
                addDate(convertStringToDate(aMask, srcDate), type, offset)
        )
    }

    /**
     * 比较两个日期的大小 author:sdarmy
     *
     * @param date1
     * @param date2
     * @return date1 > date2:1  date1 < date2:-1  date1 = date2:0
     */
    fun compareDate(date1: Date, date2: Date): Int {
        return if (date1.time > date2.time) {
            1
        } else if (date1.time < date2.time) {
            -1
        } else {
            0
        }
    }

    /**
     * 比较两个日期的大小 author:sdarmy
     *
     * @param aMask
     * @param date1
     * @param date2
     * @return date1 > date2:1  date1 < date2:-1  date1 = date2:0
     */
    @JvmStatic
    fun compareDate(aMask: String, date1: String, date2: String): Int {
        if (TextUtils.isEmpty(date1) && TextUtils.isEmpty(date2)) {
            return 0
        } else if (TextUtils.isEmpty(date1)) {
            return -1
        } else if (TextUtils.isEmpty(date2)) {
            return 1
        }
        return if (convertStringToDate(aMask, date1)!!.time > convertStringToDate(aMask, date2)!!.time) {
            1
        } else if (convertStringToDate(aMask, date1)!!.time < convertStringToDate(aMask, date2)!!.time) {
            -1
        } else {
            0
        }
    }
}