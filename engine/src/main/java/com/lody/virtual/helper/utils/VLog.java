package com.lody.virtual.helper.utils;

import android.os.Bundle;
import android.util.Log;

import java.util.Set;

/**
 * @author Lody
 *
 */
public class VLog {

	public static boolean OPEN_LOG = true;

	/**
	 * PORT FIX: these used to call String.format unconditionally, so a message
	 * carrying a bare '%' -- a guest's exception text, a path, a stack trace --
	 * threw UnknownFormatConversionException out of the logging call and into
	 * whatever was trying to report a problem. A message with no arguments is
	 * now logged as it stands, and a bad format string loses its arguments
	 * instead of the whole line.
	 */
	private static String fmt(String msg, Object... args) {
		if (args == null || args.length == 0) {
			return msg;
		}
		try {
			return String.format(msg, args);
		} catch (Throwable e) {
			return msg;
		}
	}

	public static void i(String tag, String msg, Object... format) {
		if (OPEN_LOG) {
			Log.i(tag, fmt(msg, format));
		}
	}

	public static void d(String tag, String msg, Object... format) {
		if (OPEN_LOG) {
			Log.d(tag, fmt(msg, format));
		}
	}

	public static void w(String tag, String msg, Object... format) {
		if (OPEN_LOG) {
			Log.w(tag, fmt(msg, format));
		}
	}

	public static void e(String tag, String msg, Object... format) {
		if (OPEN_LOG) {
			Log.e(tag, fmt(msg, format));
		}
	}

	public static void v(String tag, String msg, Object... format) {
		if (OPEN_LOG) {
			Log.v(tag, fmt(msg, format));
		}
	}

	public static String toString(Bundle bundle){
		if(bundle==null)return null;
		if(Reflect.on(bundle).get("mParcelledData")!=null){
			Set<String> keys=bundle.keySet();
			StringBuilder stringBuilder=new StringBuilder("Bundle[");
			if(keys!=null) {
				for (String key : keys) {
					stringBuilder.append(key);
					stringBuilder.append("=");
					stringBuilder.append(bundle.get(key));
					stringBuilder.append(",");
				}
			}
			stringBuilder.append("]");
			return stringBuilder.toString();
		}
		return bundle.toString();
	}

	public static String getStackTraceString(Throwable tr) {
		return Log.getStackTraceString(tr);
	}

	public static void printStackTrace(String tag) {
		Log.e(tag, getStackTraceString(new Exception()));
	}

	public static void e(String tag, Throwable e) {
		Log.e(tag, getStackTraceString(e));
	}
}
