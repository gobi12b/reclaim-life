# Glance pulls in WorkManager, which builds its Room database (WorkDatabase_Impl) by reflection on
# the class name. Without this, R8 renames or drops it and the release build crashes at startup in
# androidx.startup.InitializationProvider ("Failed to create an instance of WorkDatabase").
-keep class * extends androidx.room.RoomDatabase { <init>(); }
