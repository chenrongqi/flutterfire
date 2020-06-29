// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.firebase.crashlytics.firebasecrashlytics;

import android.content.Context;
import android.util.Log;

import com.google.firebase.crashlytics.FirebaseCrashlytics;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.Registrar;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** FirebaseCrashlyticsPlugin */
public class FirebaseCrashlyticsPlugin implements FlutterPlugin, MethodCallHandler {
  public static final String TAG = "CrashlyticsPlugin";
  private MethodChannel channel;

  @Override
  public void onAttachedToEngine(FlutterPluginBinding binding) {
    BinaryMessenger binaryMessenger = binding.getBinaryMessenger();
    channel = setup(binaryMessenger, binding.getApplicationContext());
  }

  @Override
  public void onDetachedFromEngine(FlutterPluginBinding binding) {
    if (channel != null) {
      channel.setMethodCallHandler(null);
      channel = null;
    }
  }

  private static MethodChannel setup(BinaryMessenger binaryMessenger, Context context) {
    final MethodChannel channel =
            new MethodChannel(binaryMessenger, "plugins.flutter.io/firebase_crashlytics");
    channel.setMethodCallHandler(new FirebaseCrashlyticsPlugin());
    
    return channel;
  }

  /** Plugin registration. */
  public static void registerWith(Registrar registrar) {
    setup(registrar.messenger(), registrar.context());
  }

  @Override
  public void onMethodCall(MethodCall call, Result result) {
    if (call.method.equals("Crashlytics#onError")) {
      // Add logs.
      List<String> logs = call.argument("logs");
      for (String log : logs) {
        FirebaseCrashlytics.getInstance().log(log);
      }

      // Set keys.
      List<Map<String, Object>> keys = call.argument("keys");
      for (Map<String, Object> key : keys) {
        switch ((String) key.get("type")) {
          case "int":
            FirebaseCrashlytics.getInstance().setCustomKey((String) key.get("key"), (int) key.get("value"));
            break;
          case "double":
            FirebaseCrashlytics.getInstance().setCustomKey((String) key.get("key"), (double) key.get("value"));
            break;
          case "string":
            FirebaseCrashlytics.getInstance().setCustomKey((String) key.get("key"), (String) key.get("value"));
            break;
          case "boolean":
            FirebaseCrashlytics.getInstance().setCustomKey((String) key.get("key"), (boolean) key.get("value"));
            break;
        }
      }

      // Report crash.
      String dartExceptionMessage = (String) call.argument("exception");
      Exception exception = new Exception(dartExceptionMessage);
      List<Map<String, String>> errorElements = call.argument("stackTraceElements");
      List<StackTraceElement> elements = new ArrayList<>();
      for (Map<String, String> errorElement : errorElements) {
        StackTraceElement stackTraceElement = generateStackTraceElement(errorElement);
        if (stackTraceElement != null) {
          elements.add(stackTraceElement);
        }
      }
      exception.setStackTrace(elements.toArray(new StackTraceElement[elements.size()]));

      FirebaseCrashlytics.getInstance().setCustomKey("exception", (String) call.argument("exception"));

      // Set a "reason" (to match iOS) to show where the exception was thrown.
      final String context = call.argument("context");
      if (context != null) FirebaseCrashlytics.getInstance().setCustomKey("reason", "thrown " + context);

      // Log information.
      final String information = call.argument("information");
      if (information != null && !information.isEmpty()) FirebaseCrashlytics.getInstance().log(information);

      FirebaseCrashlytics.getInstance().recordException(exception);
      result.success("Error reported to Crashlytics.");
    } else if (call.method.equals("Crashlytics#isDebuggable")) {
      result.success(false);
    } else if (call.method.equals("Crashlytics#getVersion")) {
      result.success("firebase");
    } else if (call.method.equals("Crashlytics#setUserEmail")) {
      FirebaseCrashlytics.getInstance().setCustomKey("email",(String) call.argument("email"));
      result.success(null);
    } else if (call.method.equals("Crashlytics#setUserIdentifier")) {
      FirebaseCrashlytics.getInstance().setUserId((String) call.argument("identifier"));
      result.success(null);
    } else if (call.method.equals("Crashlytics#setUserName")) {
      FirebaseCrashlytics.getInstance().setCustomKey("name",(String) call.argument("name"));
      result.success(null);
    } else {
      result.notImplemented();
    }
  }

  /**
   * Extract StackTraceElement from Dart stack trace element.
   *
   * @param errorElement Map representing the parts of a Dart error.
   * @return Stack trace element to be used as part of an Exception stack trace.
   */
  private StackTraceElement generateStackTraceElement(Map<String, String> errorElement) {
    try {
      String fileName = errorElement.get("file");
      String lineNumber = errorElement.get("line");
      String className = errorElement.get("class");
      String methodName = errorElement.get("method");

      return new StackTraceElement(
              className == null ? "" : className, methodName, fileName, Integer.parseInt(lineNumber));
    } catch (Exception e) {
      Log.e(TAG, "Unable to generate stack trace element from Dart side error.");
      return null;
    }
  }
}
