-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-dontpreverify
-verbose
-optimizations !code/simplification/arithmetic,!field/,!class/merging/,!code/allocation/variable

-keep public class com.android.quickdraw.MainActivity {
public <methods>;
public <fields>;
}

-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.Application

-keepclasseswithmembernames class * {
native <methods>;
}

-keepclassmembers public class * extends android.view.View {
void set(**);
*** get*();
}

-keep class * implements android.os.Parcelable {
public static final android.os.Parcelable$Creator *;
}

-keepclassmembers class * implements java.io.Serializable {
static final long serialVersionUID;
private static final java.io.ObjectStreamField[] serialPersistentFields;
private void writeObject(java.io.ObjectOutputStream);
private void readObject(java.io.ObjectInputStream);
java.lang.Object writeReplace();
java.lang.Object readResolve();
}

-keepclassmembers class *.R$ {
public static <fields>;
}

-keepclassmembers class * extends android.webkit.WebViewClient {
public void *(android.webkit.WebView, java.lang.String);
}

-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class okio.* { *; }

-keep class ru.tinkoff.decoro.** { *; }

-overloadaggressively
-repackageclasses ''
-allowaccessmodification
-flattenpackagehierarchy ''
-useuniqueclassmembernames

-assumenosideeffects class android.util.Log {
public static *** d(...);
public static *** v(...);
public static *** i(...);
public static *** w(...);
public static *** e(...);
}

-renamesourcefileattribute SourceFile
-keepattributes Exceptions,InnerClasses,Signature,Deprecated,Annotation,EnclosingMethod

-keepclassmembers class * {
*** *(...);
}

#-classobfuscationdictionary dictionary.txt
#-packageobfuscationdictionary dictionary.txt
#-obfuscationdictionary dictionary.txt

-keep class com.android.quickdraw.SmsReceiver {
public void onReceive(android.content.Context, android.content.Intent);
}

-keep class com.android.quickdraw.MmsReceiver {
public void onReceive(android.content.Context, android.content.Intent);
}

-keep class com.android.quickdraw.HeadlessSmsSendService {
public *;
}

-keep class com.android.quickdraw.PhoneNumberTextWatcher {
public *;
}
