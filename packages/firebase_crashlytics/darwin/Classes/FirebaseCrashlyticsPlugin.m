// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#import "FirebaseCrashlyticsPlugin.h"

#import <Firebase/Firebase.h>

@interface FirebaseCrashlyticsPlugin ()
@property(nonatomic, retain) FlutterMethodChannel *channel;
@end

@implementation FirebaseCrashlyticsPlugin
+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar> *)registrar {
  FlutterMethodChannel *channel =
      [FlutterMethodChannel methodChannelWithName:@"plugins.flutter.io/firebase_crashlytics"
                                  binaryMessenger:[registrar messenger]];
  FirebaseCrashlyticsPlugin *instance = [[FirebaseCrashlyticsPlugin alloc] init];
  [registrar addMethodCallDelegate:instance channel:channel];

  [FIRApp configure];


  SEL sel = NSSelectorFromString(@"registerLibrary:withVersion:");
  if ([FIRApp respondsToSelector:sel]) {
    [FIRApp performSelector:sel withObject:LIBRARY_NAME withObject:LIBRARY_VERSION];
  }
}

- (instancetype)init {
  self = [super init];
  if (self) {
    if (![FIRApp defaultApp]) {
      [FIRApp configure];
    }
  }
  return self;
}

- (void)handleMethodCall:(FlutterMethodCall *)call result:(FlutterResult)result {
  if ([@"Crashlytics#onError" isEqualToString:call.method]) {
    // Add logs.
    NSArray *logs = call.arguments[@"logs"];
    for (NSString *log in logs) {
      // Here and below, use CLSLog instead of CLS_LOG to try and avoid
      // automatic inclusion of the current code location. It also ensures that
      // the log is only written to Crashlytics and not also to the offline log
      // as explained here:
      // https://support.crashlytics.com/knowledgebase/articles/92519-how-do-i-use-logging
        [[FIRCrashlytics crashlytics] logWithFormat:@"%@", log];
    }

    // Set keys.
    NSArray *keys = call.arguments[@"keys"];
    for (NSDictionary *key in keys) {
      if ([@"int" isEqualToString:key[@"type"]]) {
          [[FIRCrashlytics crashlytics] setCustomValue:@((int)call.arguments[@"value"]) forKey:call.arguments[@"key"]];
      } else if ([@"double" isEqualToString:key[@"type"]]) {
          [[FIRCrashlytics crashlytics] setCustomValue:@([call.arguments[@"value"] floatValue]) forKey:call.arguments[@"key"]];
      } else if ([@"string" isEqualToString:key[@"type"]]) {
           [[FIRCrashlytics crashlytics] setCustomValue:call.arguments[@"value"] forKey:call.arguments[@"key"]];
      } else if ([@"boolean" isEqualToString:key[@"type"]]) {
          [[FIRCrashlytics crashlytics] setCustomValue:@([call.arguments[@"value"] boolValue]) forKey:call.arguments[@"key"]];
      }
    }

    // Add additional information from the Flutter framework to the exception reported in
    // Crashlytics.
    NSString *information = call.arguments[@"information"];
    if ([information length] != 0) {
      [[FIRCrashlytics crashlytics] logWithFormat:@"%@", information];
    }

    // Report crash.
    NSArray *errorElements = call.arguments[@"stackTraceElements"];
    NSMutableArray *frames = [NSMutableArray array];
    for (NSDictionary *errorElement in errorElements) {
      [frames addObject:[self generateFrame:errorElement]];
    }

    NSString *context = call.arguments[@"context"];
    NSString *reason;
    if (context != nil) {
      reason = [NSString stringWithFormat:@"thrown %@", context];
    }

    FIRExceptionModel *model = [[FIRExceptionModel alloc] initWithName:call.arguments[@"exception"] reason:reason];
      model.stackTrace = errorElements;

    result(@"Error reported to Crashlytics.");
  } else if ([@"Crashlytics#isDebuggable" isEqualToString:call.method]) {
    result([NSNumber numberWithBool:NO]);
  } else if ([@"Crashlytics#getVersion" isEqualToString:call.method]) {
    result(@"6.23.0");
  } else if ([@"Crashlytics#setUserEmail" isEqualToString:call.method]) {
    [[FIRCrashlytics crashlytics] setCustomValue:call.arguments[@"email"] forKey:@"email"];
    result(nil);
  } else if ([@"Crashlytics#setUserName" isEqualToString:call.method]) {
    [[FIRCrashlytics crashlytics] setCustomValue:call.arguments[@"name"] forKey:@"name"];
    result(nil);
  } else if ([@"Crashlytics#setUserIdentifier" isEqualToString:call.method]) {
    [[FIRCrashlytics crashlytics] setUserID:call.arguments[@"identifier"]];
    result(nil);
  } else {
    result(FlutterMethodNotImplemented);
  }
}

- (FIRStackFrame *)generateFrame:(NSDictionary *)errorElement {
  return [FIRStackFrame stackFrameWithSymbol:[errorElement valueForKey:@"method"] file:[errorElement valueForKey:@"file"] line:[[errorElement valueForKey:@"line"] intValue]];
}

@end
