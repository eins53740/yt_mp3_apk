package com.example.yt2local.di;

import com.example.yt2local.data.db.AppDatabase;
import com.example.yt2local.data.db.DownloadHistoryDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation",
    "nullness:initialization.field.uninitialized"
})
public final class AppModule_ProvideDownloadHistoryDaoFactory implements Factory<DownloadHistoryDao> {
  private final Provider<AppDatabase> dbProvider;

  private AppModule_ProvideDownloadHistoryDaoFactory(Provider<AppDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public DownloadHistoryDao get() {
    return provideDownloadHistoryDao(dbProvider.get());
  }

  public static AppModule_ProvideDownloadHistoryDaoFactory create(
      Provider<AppDatabase> dbProvider) {
    return new AppModule_ProvideDownloadHistoryDaoFactory(dbProvider);
  }

  public static DownloadHistoryDao provideDownloadHistoryDao(AppDatabase db) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideDownloadHistoryDao(db));
  }
}
