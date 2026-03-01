package com.example.yt2local.di;

import com.example.yt2local.data.DownloadStateHolder;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
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
public final class AppModule_ProvideDownloadStateHolderFactory implements Factory<DownloadStateHolder> {
  @Override
  public DownloadStateHolder get() {
    return provideDownloadStateHolder();
  }

  public static AppModule_ProvideDownloadStateHolderFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static DownloadStateHolder provideDownloadStateHolder() {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideDownloadStateHolder());
  }

  private static final class InstanceHolder {
    static final AppModule_ProvideDownloadStateHolderFactory INSTANCE = new AppModule_ProvideDownloadStateHolderFactory();
  }
}
