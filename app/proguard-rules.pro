# Add project specific ProGuard rules here.

# 保留注解与泛型签名，避免依赖库反射相关能力被裁剪
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# 编译期注解库在运行时不存在，忽略警告
-dontwarn javax.annotation.**
-dontwarn org.jetbrains.annotations.**
